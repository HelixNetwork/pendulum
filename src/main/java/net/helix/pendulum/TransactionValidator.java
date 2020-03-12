package net.helix.pendulum;

import com.google.common.cache.Cache;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.BundleViewModel;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.event.*;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.TransactionHash;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.service.cache.TangleCache;
import net.helix.pendulum.service.snapshot.Snapshot;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.PendulumUtils;
import net.helix.pendulum.utils.collections.impl.BoundedLinkedListImpl;
import net.helix.pendulum.utils.collections.interfaces.BoundedLinkedSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static net.helix.pendulum.controllers.TransactionViewModel.*;

public class TransactionValidator implements PendulumEventListener {
    private static final Logger log = LoggerFactory.getLogger(TransactionValidator.class);
    private static final int  TESTNET_MWM_CAP = 1;

    private  Tangle tangle;
    private  SnapshotProvider snapshotProvider;
    private Node.RequestQueue requestQueue;

    private int minWeightMagnitude = 1;
    private PendulumConfig config;
    private static final long MAX_TIMESTAMP_FUTURE = 2L * 60L * 60L;
    private static final long MAX_TIMESTAMP_FUTURE_MS = MAX_TIMESTAMP_FUTURE * 1_000L;

    private ReentrantLock lock = new ReentrantLock(true);
    /////////////////////////////////fields for solidification thread//////////////////////////////////////

    private ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    //private Thread newSolidThread;

    private BoundedLinkedSet<Hash> forwardSolidificationQueue;
    private BoundedLinkedSet<Hash> backwardsSolidificationQueue;

    private Function<Hash, Void> depthCallback;
    private Function<Hash, Void> breadthCallback;

    private TangleCache tangleCache;

    private static final int BACKWARDS_SOLIDIFICATION_DELAY_MS = 400;
    private static final int FORWARD_SOLIDIFICATION_DELAY_MS = 200;
    private static final int MAX_TX_PER_SCAN = 1000;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final AtomicBoolean forwardSolidificationDone = new AtomicBoolean(true);

    private Cache<Hash, Integer> solidificationCache;

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
     */
    public void init() {

        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);
        this.snapshotProvider = Pendulum.ServiceRegistry.get().resolve(SnapshotProvider.class);
        this.requestQueue = Pendulum.ServiceRegistry.get().resolve(Node.RequestQueue.class);
        this.config = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);
        this.tangleCache = Pendulum.ServiceRegistry.get().resolve(TangleCache.class);

        boolean testnet = this.config.isTestnet();
        int mwm = this.config.getMwm();

        setMwm(testnet, mwm);

        int solidificationQueueCap = config.solidificationQueueCap();
        forwardSolidificationQueue = new BoundedLinkedListImpl<>(solidificationQueueCap);
        backwardsSolidificationQueue = new BoundedLinkedListImpl<>(solidificationQueueCap);

        breadthCallback = parentHash -> {
            TransactionViewModel parent = tangleCache.getTxVM(parentHash);

            if(parent.getType() == PREFILLED_SLOT) {
                if(requestQueue.enqueueTransaction(parent.getHash(), false)) {
                    log.trace("Missing parent, requesting {}", parent.getHash());
                }
            }

            if (parent.getType() == FILLED_SLOT && !parent.isSolid()) {
                if (forwardSolidificationDone.get()) {
                    forwardSolidificationDone.set(false);
                    if (forwardSolidificationQueue.add(parent.getHash())) {
                        log.trace("Non-solid but filled parent {}", parent.getHash());
                    }
                }
            }
            // not important
            return null;
        };

        depthCallback = parentHash -> {
            TransactionViewModel parent = tangleCache.getTxVM(parentHash);
            if(parent.getType() == PREFILLED_SLOT) {
                if(requestQueue.enqueueTransaction(parent.getHash(), false)) {
                    log.trace("Missing parent, requesting {}", parent.getHash());
                }
            }

            if (parent.getType() == FILLED_SLOT && !parent.isSolid()) {
                if (forwardSolidificationQueue.push(parent.getHash())) {
                    log.trace("Non-solid but filled parent {}", parent.getHash());
                }
                forwardSolidificationDone.set(false);
            }
            // not important
            return null;
        };



        EventManager.get().subscribe(EventType.TX_STORED,  this);
    }


    public void start() {
        scheduler.scheduleAtFixedRate(this::solidifyBackwards,
                0, BACKWARDS_SOLIDIFICATION_DELAY_MS, TimeUnit.MILLISECONDS);

        scheduler.scheduleAtFixedRate(this::solidifyForward,
                10, FORWARD_SOLIDIFICATION_DELAY_MS, TimeUnit.MILLISECONDS);
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
     */
    public void shutdown() throws InterruptedException {
        shuttingDown.set(true);
        scheduler.shutdown();
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
    public boolean hasInvalidTimestamp(TransactionViewModel transactionViewModel) {
        // ignore invalid timestamps for transactions that were requested by our node while solidifying a milestone
        if(requestQueue.isTransactionRequested(transactionViewModel.getHash(), true)) {
            return false;
        }

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

            log.trace("tx_hash, tx_att_ts, tx_ts, snap_ts, snap_solid_ep = {} {} {} {} {}",
                transactionViewModel.getHash().toString(),
                transactionViewModel.getAttachmentTimestamp(),
                transactionViewModel.getTimestamp(),
                snapshotProvider.getInitialSnapshot().getTimestamp(),
                snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(transactionViewModel.getHash()));

            EventContext ctx = new EventContext();
            ctx.put(Key.key("TX", TransactionViewModel.class), transactionViewModel);
            EventManager.get().fire(EventType.STALE_TX, ctx);
            tangle.publish("invalid_tx_timestamp += 1");
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
            tangle.publish("invalid_tx_hash += 1");
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
     * This methods tries its best to solidify a given hash, by requesting the missing parents if needed
     *
     * @param hash hash of the transactions that shall get checked
     * @return true if the transaction is solid and false otherwise
     * @throws Exception if anything goes wrong while trying to solidify the transaction
     */

    public boolean checkSolidity(Hash hash) throws Exception {
        if (fromHash(tangle, hash).isSolid()) {
            return true;
        }
        return quickSetSolid(hash, breadthCallback);
    }



    /**
     * Run a forward sweep so that filled but not yet solid transactions are updated
     */
    public void solidifyForward() {
        if (shuttingDown.get()) {
            return;
        }

        Thread.currentThread().setName("forward solidification");
        Hash txHash;
        int txCount = 0;
        while ((txHash = forwardSolidificationQueue.poll()) != null) {
            try {
                if (txCount > MAX_TX_PER_SCAN) {
                    return;
                }
                txCount++;
                quickSetSolid(txHash, depthCallback);
            } catch (Exception e) {
                log.error("Failed to solidify", e);
            }
        }
        forwardSolidificationDone.set(true);
    }

    /**
     * For each new solid transaction, we find
     * its children (approvers) and try to quickly solidify them with {@link #quickSetSolid}.
     * The solidifaction is then cascaded backwards
     */
    //Package private for testing
    public void solidifyBackwards() {
        if (shuttingDown.get()) {
            return;
        }

        Thread.currentThread().setName("backward solidification");
        Hash txHash;
        int txCount = 0;

        if (backwardsSolidificationQueue.isEmpty()) {
            for (Hash solidEntry : snapshotProvider.getLatestSnapshot().getSolidEntryPoints().keySet()) {
                try {
                    backwardsSolidificationQueue.add(solidEntry);
                } catch (Exception e) {
                    log.error("error:", e);
                }
            }
        }

        while((txHash = backwardsSolidificationQueue.poll()) != null) {
            try {
                if (txCount > MAX_TX_PER_SCAN) {
                    return;
                }
                txCount++;
                TransactionViewModel transaction = tangleCache.getTxVM(txHash);
                Set<Hash> approvers = transaction.getApprovers(tangle).getHashes();
                for(Hash h: approvers) {
                    if (h.leadingZeros() < getMinWeightMagnitude()) {
                        log.trace("Skipping {}. All aprovers: {}", h, PendulumUtils.logHashList(approvers, 4));
                        continue;
                    }

                    TransactionViewModel approver = fromHash(tangle, h);
                    if (approver.isSolid()) {
                        backwardsSolidificationQueue.add(h);
                    } else {
                        quickSetSolid(h, breadthCallback);
                    }
                }
            } catch (Exception e) {
                log.error("Error while propagating solidity upwards", e);
            }
        }
    }

    protected void addSolidTransaction(Hash hash) throws Exception {
        backwardsSolidificationQueue.add(hash);
    }


    /**
     * Tries to solidify the transactions quickly by performing {@link #checkApproovee} on both parents (trunk and
     * branch). If the parents are solid, mark the transactions as solid.
     * @param tvmHash hash of the transaction to solidify
     * @return <tt>true</tt> if we made the transaction solid, else <tt>false</tt>.
     * @throws Exception
     */
    private boolean quickSetSolid(Hash tvmHash, Function<Hash, Void> parentCallback) throws Exception {
        TransactionViewModel transactionViewModel = fromHash(tangle, tvmHash);

        if(transactionViewModel.isSolid()) {
            return false;
        }


        boolean solid = true;
        TransactionViewModel milestoneTx;
        if ((milestoneTx = transactionViewModel.isMilestoneBundle(tangle)) != null) {
            log.trace("Milestone solidification: {} {}", milestoneTx.getHash().toString(),
                    transactionViewModel.getHash());
            Set<Hash> parents = RoundViewModel.getMilestoneTrunk(tangle, transactionViewModel, milestoneTx);
            if (transactionViewModel.getCurrentIndex() == transactionViewModel.lastIndex()) {
                parents.addAll(RoundViewModel.getMilestoneBranch(tangle, transactionViewModel, milestoneTx, config.getValidatorSecurity()));
                log.trace("Tail milestone tx, adding referenced parents: {}", PendulumUtils.logHashList(parents, 4));
            }

            for (Hash parent : parents){
                if (!checkApproovee(parent, parentCallback)) {
                    solid = false;
                }
            }
        } else {
            Hash[] parents = new Hash[]{
                    transactionViewModel.getTrunkTransactionHash(),
                    transactionViewModel.getBranchTransactionHash()
            };

//            if (checkParentsTxs(transactionViewModel, parents)) {
//                log.warn("The tx and the bundle has been deleted");
//                return false;
//            }

            for (Hash parent: parents) {
                if (!checkApproovee(parent, parentCallback)) {
                    solid = false;
                }
            }
        }

        if(solid) {
            lock.lock();
            try {
                log.trace("Quickly solidified: {}", transactionViewModel.getHash());
                // ugly...
                transactionViewModel.updateSolid(true);
                transactionViewModel.update(tangle, snapshotProvider.getInitialSnapshot(), "solid|height");
            } finally {
                lock.unlock();
            }

            EventManager.get().fire(EventType.TX_SOLIDIFIED, EventUtils.fromTxHash(transactionViewModel.getHash()));
            backwardsSolidificationQueue.add(transactionViewModel.getHash());
            // we don't use heights atm
            //tvm.updateHeights(tangle, snapshotProvider.getInitialSnapshot());
            return true;
        }

        return false;
    }


    private boolean checkParentsTxs(TransactionViewModel transactionViewModel, Hash[] parents) {

        for (Hash parentHash: parents) {
            if (parentHash.leadingZeros() < getMinWeightMagnitude()) {

                log.trace("Invalid parent: {}\n tx: {}\n Deleting", parentHash, transactionViewModel);
                try {
                    lock.lock();
                    for (Hash bundleTx : BundleViewModel.load(tangle, transactionViewModel.getBundleHash()).getHashes()) {
                        log.trace("Deleting: {}", bundleTx);
                        fromHash(tangle, bundleTx).delete(tangle);
                    }
                    transactionViewModel.delete(tangle);
                    fromHash(tangle, parentHash).delete(tangle);
                    return false;
                } catch (Exception e) {
                    log.error("Fatal: ", e);
                } finally {
                    lock.unlock();
                }
            }
        }
        return true;
    }

    /**
     * If the the {@code approvee} is missing, request it from a neighbor.
     * @param hApprovee transaction hash we check.
     * @return true if {@code approvee} is solid.
     * @throws Exception if we encounter an error while requesting a transaction
     */
    private boolean checkApproovee(Hash hApprovee, Function<Hash, Void> approveeCallback) throws Exception {
        TransactionViewModel approovee = fromHash(tangle, hApprovee);
        Snapshot s = snapshotProvider.getInitialSnapshot();
        if (s == null) {
            log.warn("Initial snapshot is NULL");
        } else if (s.hasSolidEntryPoint(approovee.getHash())) {
            return true;
        }

        if (approovee.getHash().leadingZeros() >= minWeightMagnitude) {
            approveeCallback.apply(approovee.getHash());
        } else {
            log.trace("Invalid mvm: {}", approovee.getHash());
        }
        return approovee.getType() == FILLED_SLOT && approovee.isSolid();
    }

    //Package Private For Testing
    protected boolean isNewSolidTxSetsEmpty () {
        return backwardsSolidificationQueue.isEmpty() && forwardSolidificationQueue.isEmpty();
    }

    @Override
    public void handle(EventType type, EventContext ctx) {
        switch (type) {
            case TX_STORED:
                try {
                    checkSolidity(EventUtils.getTxHash(ctx));
                } catch (Exception e) {
                    log.error("Failed to solidify", e);
                }
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
