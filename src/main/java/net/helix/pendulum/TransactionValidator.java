package net.helix.pendulum;

import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TipsViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.event.*;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.helix.pendulum.controllers.TransactionViewModel.*;

public class TransactionValidator implements PendulumEventListener {
    private static final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private static final int  TESTNET_MWM_CAP = 1;
    public static final int SOLID_SLEEP_TIME = 500;

    private  Tangle tangle;
    private  SnapshotProvider snapshotProvider;
    private  TipsViewModel tipsViewModel;
    private Node.RequestQueue requestQueue;

    private int minWeightMagnitude = 1;
    private PendulumConfig config;
    private static final long MAX_TIMESTAMP_FUTURE = 2L * 60L * 60L;
    private static final long MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1_000L;

    /////////////////////////////////fields for solidification thread//////////////////////////////////////

    private Thread newSolidThread;

    /**
     * If true use {@link #newSolidTransactionsOne} while solidifying. Else use {@link #newSolidTransactionsTwo}.
     */
    private final AtomicBoolean useFirst = new AtomicBoolean(true);
    /**
     * Is {@link #newSolidThread} shutting down
     */
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    /**
     * mutex for solidification
     */
    private final Object cascadeSync = new Object();
    private final Set<Hash> newSolidTransactionsOne = new LinkedHashSet<>();
    private final Set<Hash> newSolidTransactionsTwo = new LinkedHashSet<>();

    /**
     * Constructor for Tangle Validator
     *
     *
     */
    public TransactionValidator() {
        Pendulum.ServiceRegistry.get().register(TransactionValidator.class, this);
    }

    /**
     * Does two things:
     * <ol>
     *     <li>Sets the minimum weight magnitude (MWM). POW on a transaction is validated by counting a certain
     *     number of consecutive 0s in the end of the transaction hash. The number of 0s is the MWM.</li>
     *     <li>Starts the transaction solidification thread.</li>
     * </ol>
     *
     *
     * @see #spawnSolidTransactionsPropagation()
     */
    public void init() {

        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);
        this.snapshotProvider = Pendulum.ServiceRegistry.get().resolve(SnapshotProvider.class);
        this.tipsViewModel = Pendulum.ServiceRegistry.get().resolve(TipsViewModel.class);
        this.requestQueue = Pendulum.ServiceRegistry.get().resolve(Node.RequestQueue.class);
        this.config = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);

        boolean testnet = this.config.isTestnet();
        int mwm = this.config.getMwm();

        setMwm(testnet, mwm);

        newSolidThread = new Thread(spawnSolidTransactionsPropagation(), "Solid TX cascader");
        newSolidThread.start();

        EventManager.get().subscribe(EventType.TX_STORED,  this);
        EventManager.get().subscribe(EventType.TX_SOLIDIFIED,  this);
    }



    //Package Private For Testing
    protected void setMwm(boolean testnet, int mwm) {
        minWeightMagnitude = mwm;

        //lowest allowed MWM encoded in 46 bytes.
        if (!testnet){
            minWeightMagnitude = Math.max(minWeightMagnitude, TESTNET_MWM_CAP);
        }
    }

    /**
     * Shutdown roots to tip solidification thread
     * @throws InterruptedException
     * @see #spawnSolidTransactionsPropagation()
     */
    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        newSolidThread.join();
    }

    /**
     * @return the minimal number of trailing 0s that have to be present at the end of the transaction hash
     * in order to validate that sufficient proof of work has been done
     */
    public int getMinWeightMagnitude() {
        return minWeightMagnitude;
    }

    /**
     * Checks that the timestamp of the transaction is below the last global snapshot time
     * or more than {@value #MAX_TIMESTAMP_FUTURE} seconds in the future, and thus invalid.
     *
     * <p>
     *     First the attachment timestamp (set after performing POW) is checked, and if not available
     *     the regular timestamp is checked. Genesis transaction will always be valid.
     * </p>
     * @param transactionViewModel transaction under test
     * @return <tt>true</tt> if timestamp is not in valid bounds and {@code transactionViewModel} is not genesis.
     * Else returns <tt>false</tt>.
     */
    private boolean hasInvalidTimestamp(TransactionViewModel transactionViewModel) {
        // ignore invalid timestamps for transactions that were requested by our node while solidifying a milestone
        if(requestQueue.isTransactionRequested(transactionViewModel.getHash(), true)) {
            return false;
        }
        log.trace("tx_hash, tx_att_ts, tx_ts, snap_ts, snap_solid_ep = {} {} {} {} {}",
                transactionViewModel.getHash().toString(),
                transactionViewModel.getAttachmentTimestamp(),
                transactionViewModel.getTimestamp(),
                snapshotProvider.getInitialSnapshot().getTimestamp(),
                snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(transactionViewModel.getHash()));

        if (transactionViewModel.getAttachmentTimestamp() == 0) {
            return transactionViewModel.getTimestamp() < snapshotProvider.getInitialSnapshot().getTimestamp() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(transactionViewModel.getHash())
                    || transactionViewModel.getTimestamp() > (System.currentTimeMillis() / 1000) + MAX_TIMESTAMP_FUTURE;
        }
        return transactionViewModel.getAttachmentTimestamp() < (snapshotProvider.getInitialSnapshot().getTimestamp())
                || transactionViewModel.getAttachmentTimestamp() > System.currentTimeMillis() + MAX_TIMESTAMP_FUTURE_MS;
    }

    /**
     * Runs the following validation checks on a transaction:
     * <ol>
     *     <li>{@link #hasInvalidTimestamp} check.</li>
     *     <li>Check that no value bytes are set beyond the usable index, otherwise we will have values larger
     *     than max supply.</li>
     *     <li>Check that sufficient POW was performed.</li>
     *
     * </ol>
     *Exception is thrown upon failure.
     *
     * @param transactionViewModel transaction that should be validated
     * @param minWeightMagnitude the minimal number of trailing 0s at the end of the transaction hash
     * @throws StaleTimestampException if timestamp check fails
     * @throws IllegalStateException if any of the other checks fail
     */
    public void runValidation(TransactionViewModel transactionViewModel, final int minWeightMagnitude) {
        transactionViewModel.setMetadata();
        transactionViewModel.setAttachmentData();
        if(hasInvalidTimestamp(transactionViewModel)) {

            EventContext ctx = new EventContext();
            ctx.put(Key.key("TX", TransactionViewModel.class), transactionViewModel);
            EventManager.get().fire(EventType.STALE_TX, ctx);

            log.debug("Invalid timestamp for txHash/addressHash: {} {}", transactionViewModel.getHash().toString(), transactionViewModel.getAddressHash().toString());
            throw new StaleTimestampException("Invalid transaction timestamp.");
        }
        for (int i = VALUE_OFFSET + VALUE_USABLE_SIZE; i < VALUE_OFFSET + VALUE_SIZE; i++) { // todo always false.
            if (transactionViewModel.getBytes()[i] != 0) {
                throw new IllegalStateException("Invalid transaction value");
            }
        }

        int weightMagnitude = transactionViewModel.weightMagnitude;
        if((weightMagnitude < minWeightMagnitude)) {
            throw new IllegalStateException("Invalid transaction hash");
        }

        /* todo validation
        if (transactionViewModel.value() != 0 && transactionViewModel.getAddressHash().bytes()[Sha3.HASH_LENGTH - 1] != 0) {
            throw new IllegalStateException("Invalid transaction address");
        }*/
    }

    /**
     * Creates a new transaction from  {@code bytes} and validates it with {@link #runValidation}.
     *
     * @param bytes raw transaction bytes
     * @param minWeightMagnitude minimal number of leading 0s in transaction for POW validation
     * @return the transaction resulting from the raw bytes if valid.
     * @throws RuntimeException if validation fails
     */
    public TransactionViewModel validateBytes(final byte[] bytes, int minWeightMagnitude) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(bytes, 0, bytes.length, SpongeFactory.create(SpongeFactory.Mode.S256)));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }


    public boolean validateMvm(Hash hash) {
        return (hash.leadingZeros() >= this.minWeightMagnitude);
    }

    /**
     * Creates a new transaction from {@code bytes} and validates it with {@link #runValidation}.
     *
     * @param bytes raw transaction bytes
     * @param minWeightMagnitude minimal number of leading 0s in transaction for POW validation
     * @return the transaction resulting from the raw bytes if valid
     * @throws RuntimeException if validation fails
     */
    public TransactionViewModel validateBytes(final byte[] bytes, int minWeightMagnitude, Sponge sha3) {
        TransactionViewModel transactionViewModel = new TransactionViewModel(bytes, TransactionHash.calculate(bytes, SIZE, sha3));
        runValidation(transactionViewModel, minWeightMagnitude);
        return transactionViewModel;
    }

    /**
     * This method does the same as {@link #checkSolidity(Hash, boolean, int)} but defaults to an unlimited amount
     * of transactions that are allowed to be traversed.
     *
     * @param hash hash of the transactions that shall get checked
     * @param milestone true if the solidity check was issued while trying to solidify a milestone and false otherwise
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
    public boolean checkSolidity(Hash hash, boolean milestone) throws Exception {
        return checkSolidity(hash, milestone, Integer.MAX_VALUE);
    }

    /**
     * This method checks transactions for solidity and marks them accordingly if they are found to be solid.
     *
     * It iterates through all approved transactions until it finds one that is missing in the database or until it
     * reached solid transactions on all traversed subtangles. In case of a missing transactions it issues a transaction
     * request and returns false. If no missing transaction is found, it marks the processed transactions as solid in
     * the database and returns true.
     *
     * Since this operation can potentially take a long time to terminate if it would have to traverse big parts of the
     * tangle, it is possible to limit the amount of transactions that are allowed to be processed, while looking for
     * unsolid / missing approvees. This can be useful when trying to "interrupt" the solidification of one transaction
     * (if it takes too many steps) to give another one the chance to be solidified instead (i.e. prevent blocks in the
     * solidification threads).
     *
     * @param hash hash of the transactions that shall get checked
     * @param milestone true if the solidity check was issued while trying to solidify a milestone and false otherwise
     * @param maxProcessedTransactions the maximum amount of transactions that are allowed to be traversed
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */
    public boolean checkSolidity(Hash hash, boolean milestone, int maxProcessedTransactions) throws Exception {
        TransactionViewModel txvm = fromHash(tangle, hash);
        quickSetSolid(txvm, true);
        return txvm.isSolid();

 //       if(fromHash(tangle, hash).isSolid()) {
 //           return true;
 //       }
//        Set<Hash> analyzedHashes = new HashSet<>(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().keySet());
//        if(maxProcessedTransactions != Integer.MAX_VALUE) {
//            maxProcessedTransactions += analyzedHashes.size();
//        }
//        boolean solid = true;
//        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(hash));
//        Hash hashPointer;
//        while ((hashPointer = nonAnalyzedTransactions.poll()) != null) {
//            if (analyzedHashes.add(hashPointer)) {
//                if(analyzedHashes.size() >= maxProcessedTransactions) {
//                    return false;
//                }
//
//                final TransactionViewModel transaction = fromHash(tangle, hashPointer);
//                if(!transaction.isSolid() && !snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hashPointer)) {
//                    if (transaction.getType() == PREFILLED_SLOT) {
//                        solid = false;
//
//                        if (!requestQueue.isTransactionRequested(hashPointer, milestone)) {
//                            requestQueue.enqueueTransaction(hashPointer, milestone);
//                            break;
//                        }
//                    } else {
//                        // transaction of milestone bundle
//                        TransactionViewModel milestoneTx;
//                        if ((milestoneTx = transaction.isMilestoneBundle(tangle)) != null){
//                            Set<Hash> parents = RoundViewModel.getMilestoneTrunk(tangle, transaction, milestoneTx);
//                            parents.addAll(RoundViewModel.getMilestoneBranch(tangle, transaction, milestoneTx, config.getValidatorSecurity()));
//                            for (Hash parent : parents){
//                                nonAnalyzedTransactions.offer(parent);
//                            }
//                        }
//                        // normal transaction
//                        else {
//                            nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
//                            nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
//                        }
//                    }
//                }
//            }
//        }
//        if (solid) {
//            updateSolidTransactions(tangle, snapshotProvider.getInitialSnapshot(), analyzedHashes);
//        }
//        analyzedHashes.clear();
//        return solid;
    }

    public void addSolidTransaction(Hash hash) {
        synchronized (cascadeSync) {
            if (useFirst.get()) {
                newSolidTransactionsOne.add(hash);
            } else {
                newSolidTransactionsTwo.add(hash);
            }
        }
    }

    /**
     * Creates a runnable that runs {@link #propagateSolidTransactions()} in a loop every {@value #SOLID_SLEEP_TIME} ms
     * @return runnable that is not started
     */
    private Runnable spawnSolidTransactionsPropagation() {
        return () -> {
            while(!shuttingDown.get()) {
                propagateSolidTransactions();
                try {
                    Thread.sleep(SOLID_SLEEP_TIME);
                } catch (InterruptedException e) {
                    // Ignoring InterruptedException. Do not use Thread.currentThread().interrupt() here.
                    log.error("Thread was interrupted: ", e);
                }
            }
        };
    }

    /**
     * Iterates over all currently known solid transactions. For each solid transaction, we find
     * its children (approvers) and try to quickly solidify them with {@link #quickSetSolid}.
     * If we manage to solidify the transactions, we add them to the solidification queue for a traversal by a later run.
     */
    //Package private for testing
    protected void propagateSolidTransactions() {
        Set<Hash> newSolidHashes = new LinkedHashSet<>();
        useFirst.set(!useFirst.get());
        //synchronized to make sure no one is changing the newSolidTransactions collections during addAll
        synchronized (cascadeSync) {
            //We are using a collection that doesn't get updated by other threads
            if (useFirst.get()) {
                newSolidHashes.addAll(newSolidTransactionsTwo);
                newSolidTransactionsTwo.clear();
            } else {
                newSolidHashes.addAll(newSolidTransactionsOne);
                newSolidTransactionsOne.clear();
            }
            // sweep from the entry points as well
            newSolidHashes.addAll(snapshotProvider.getLatestSnapshot().getSolidEntryPoints().keySet());
        }

        LinkedList<Hash> solidifictionQueue = new LinkedList<>(newSolidHashes);

        Hash hash;
        while((hash = solidifictionQueue.poll()) != null) {
            try {
                TransactionViewModel transaction = fromHash(tangle, hash);
                Set<Hash> approvers = transaction.getApprovers(tangle).getHashes();
                for(Hash h: approvers) {
                    TransactionViewModel tx = fromHash(tangle, h);
                    if (tx.isSolid() && !newSolidHashes.contains(h)) {
                        newSolidHashes.add(h);
                        solidifictionQueue.add(h);
                    } else {
                        quickSetSolid(tx, true);
                    }
                }
            } catch (Exception e) {
                log.error("Error while propagating solidity upwards", e);
            }
        }
    }


    /**
     * Updates a transaction after it was stored in the tangle. Tells the node to not request the transaction anymore,
     * to update the live tips accordingly, and attempts to quickly solidify the transaction.
     *
     * <p/>
     * Performs the following operations:
     *
     * <ol>
     *     <li>Attempts to quickly solidify {@code transactionViewModel} by checking whether its direct parents
     *     are solid. If solid we add it to the queue transaction solidification thread to help it propagate the
     *     solidification to the approving child transactions.</li>
     *     <li>Requests missing direct parent (trunk & branch) transactions that are needed to solidify
     *     {@code transactionViewModel}.</li>
     * </ol>
     * @param transactionViewModel received transaction that is being updated
     * @throws Exception if an error occurred while trying to solidify
     * @see TipsViewModel
     */
    //Not part of the validation process. This should be moved to a component in charge of
    //what transaction we gossip.
    //public void updateSolidityStatus(TransactionViewModel transactionViewModel) throws Exception {
        // handled by events


     //   if(quickSetSolid(transactionViewModel)) {
            //transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
            //tipsViewModel.setSolid(transactionViewModel.getHash());
            //addSolidTransaction(transactionViewModel.getHash());
     //   }
    //}

    /**
     * Perform a {@link #quickSetSolid} while capturing and logging errors
     * @param transactionViewModel transaction we try to solidify.
     * @return <tt>true</tt> if we managed to solidify, else <tt>false</tt>.
     */
    //private boolean quietQuickSetSolid(TransactionViewModel transactionViewModel) {
    //    try {
    //        return quickSetSolid(transactionViewModel);
    //    } catch (Exception e) {
    //        log.error(e.getMessage(), e);
    //        return false;
    //    }
    //}

    /**
     * Tries to solidify the transactions quickly by performing {@link #checkApproovee} on both parents (trunk and
     * branch). If the parents are solid, mark the transactions as solid.
     * @param transactionViewModel transaction to solidify
     * @param requestParents <tt>true</tt> is request missing parents
     * @return <tt>true</tt> if we made the transaction solid, else <tt>false</tt>.
     * @throws Exception
     */
    private boolean quickSetSolid(final TransactionViewModel transactionViewModel, boolean requestParents) throws Exception {
        if(transactionViewModel.isSolid()) {
            return false;
        }


        boolean solid = true;
        TransactionViewModel milestoneTx;
        if ((milestoneTx = transactionViewModel.isMilestoneBundle(tangle)) != null){
            log.trace("Milestone solidification: {}", milestoneTx.getHash().toString());
            Set<Hash> parents = RoundViewModel.getMilestoneTrunk(tangle, transactionViewModel, milestoneTx);
            parents.addAll(RoundViewModel.getMilestoneBranch(tangle, transactionViewModel, milestoneTx, config.getValidatorSecurity()));
            for (Hash parent : parents){
                // milestones are solidified separately
                if (!checkApproovee(fromHash(tangle, parent), false)) {
                    solid = false;
                }
            }
        } else {
            if (!checkApproovee(transactionViewModel.getTrunkTransaction(tangle), requestParents)) {
                solid = false;
            }
            if (!checkApproovee(transactionViewModel.getBranchTransaction(tangle), requestParents)) {
                solid = false;
            }
        }

        if(solid) {
            log.trace("Quickly solidified: {}", transactionViewModel.getHash());
            // ugly...
            transactionViewModel.updateSolid(true);
            transactionViewModel.updateHeights(tangle, snapshotProvider.getInitialSnapshot());
            transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");

            EventManager.get().fire(EventType.TX_SOLIDIFIED, EventUtils.fromTx(transactionViewModel));
            return true;
        }

        return false;
    }

    /**
     * If the the {@code approvee} is missing, request it from a neighbor.
     * @param approovee transaction we check.
     * @return true if {@code approvee} is solid.
     * @throws Exception if we encounter an error while requesting a transaction
     */
    private boolean checkApproovee(TransactionViewModel approovee, boolean request) throws Exception {
        if(snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(approovee.getHash())) {
            return true;
        }


        if(request && approovee.getType() == PREFILLED_SLOT) {
            requestQueue.enqueueTransaction(approovee.getHash(), false);
        }

        return approovee.isSolid() && (approovee.getType() == FILLED_SLOT);
    }

    //Package Private For Testing
    protected boolean isNewSolidTxSetsEmpty () {
        return newSolidTransactionsOne.isEmpty() && newSolidTransactionsTwo.isEmpty();
    }

    @Override
    public void handle(EventType type, EventContext ctx) {
        switch (type) {
            case TX_STORED:
                try {
                    quickSetSolid(EventUtils.getTx(ctx), true);
                } catch (Exception e) {
                    log.error("Failed to solidify", e);
                }
                break;

            case TX_SOLIDIFIED:
                TransactionViewModel tvm = EventUtils.getTx(ctx);
                addSolidTransaction(tvm.getHash());
                break;

            default:
        }
    }

    /**
     * Thrown if transaction fails {@link #hasInvalidTimestamp} check.
     */
    public static class StaleTimestampException extends RuntimeException {
        StaleTimestampException (String message) {
            super(message);
        }
    }
}
