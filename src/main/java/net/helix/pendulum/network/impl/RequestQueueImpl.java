package net.helix.pendulum.network.impl;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.TransactionValidator;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.network.Node;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.storage.Tangle;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Created by paul on 3/27/17.
 */
public class RequestQueueImpl implements Node.RequestQueue {

    private static final Logger log = LoggerFactory.getLogger(RequestQueueImpl.class);
    private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();

    public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private final SecureRandom random = new SecureRandom();

    private final Object syncObj = new Object();
    private Tangle tangle;
    private SnapshotProvider snapshotProvider;
    private TransactionValidator validator;


    private PendulumConfig config;

    public RequestQueueImpl() {
        Pendulum.ServiceRegistry.get().register(Node.RequestQueue.class, this);
    }

    public Pendulum.Initializable init() {
        this.tangle = Pendulum.ServiceRegistry.get().resolve(Tangle.class);
        this.snapshotProvider = Pendulum.ServiceRegistry.get().resolve(SnapshotProvider.class);
        this.config = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);
        this.validator = Pendulum.ServiceRegistry.get().resolve(TransactionValidator.class);
        double pRemoveRequest = config.getpRemoveRequest();

        if(!initialized) {
            initialized = true;
            P_REMOVE_REQUEST = pRemoveRequest;
        }

        return this;
    }

    @Override
    public Hash[] getRequestedTransactions() {
        synchronized (syncObj) {
            return ArrayUtils.addAll(transactionsToRequest.stream().toArray(Hash[]::new),
                    milestoneTransactionsToRequest.stream().toArray(Hash[]::new));
        }
    }

    @Override
    public int size() {
        synchronized (syncObj) {
            return transactionsToRequest.size() + milestoneTransactionsToRequest.size();
        }
    }

    @Override
    public boolean clearTransactionRequest(Hash hash) {
        synchronized (syncObj) {
            boolean milestone = milestoneTransactionsToRequest.remove(hash);
            boolean normal = transactionsToRequest.remove(hash);
            return normal || milestone;
        }
    }

    @Override
    public void enqueueTransaction(Hash hash, boolean milestone) {
        if (shouldRequest(hash)) {
            synchronized (syncObj) {
                if(milestone) {
                    transactionsToRequest.remove(hash);
                    milestoneTransactionsToRequest.add(hash);
                } else {
                    if(!milestoneTransactionsToRequest.contains(hash)) {
                        if (transactionsToRequestIsFull()) {
                            popEldestTransactionToRequest();
                        }
                        transactionsToRequest.add(hash);
                    }
                }
            }
        }
    }

    /**
     * Checks that the hash
     *  - is not null or null_hash
     *  - has required min weight maginitude
     *  - the required hash is not already stored
     *  - the requested hash in not a solid entry point
     * @param hash hash to check
     * @return <code>true</code> if eligibile for requesting
     */
    private boolean shouldRequest(Hash hash) {
        if (hash == null || Hash.NULL_HASH.equals(hash)) {
            return false;
        }

        if (!validator.validateMvm(hash)) {
            log.trace("Mvm check failed: {}", hash.toString());
            return false;
        }

        if (snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hash)) {
            log.trace("{} is a solid entry point", hash.toString());
            return false;
        }

        boolean exists = false;
        try {
            exists = TransactionViewModel.exists(tangle, hash);
        } catch (Exception e) {
            log.error("Error looking up a tx hash", e);
        }
        if (exists) {
            log.trace("{} already exists", hash.toString());
        }
        return !exists;
    }

    /**
     * This method removes the oldest transaction in the transactionsToRequest Set.
     *
     * It used when the queue capacity is reached, and new transactions would be dropped as a result.
     */
    //@VisibleForTesting
    public void popEldestTransactionToRequest() {
        Iterator<Hash> iterator = transactionsToRequest.iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    /**
     * This method allows to check if a transaction was requested by the TransactionRequester.
     *
     * It can for example be used to determine if a transaction that was received by the node was actively requested
     * while i.e. solidifying transactions or if a transaction arrived due to the gossip protocol.
     *
     * @param transactionHash hash of the transaction to check
     * @param milestoneRequest flag that indicates if the hash was requested by a milestone request
     * @return true if the transaction is in the set of transactions to be requested and false otherwise
     */
    @Override
    public boolean isTransactionRequested(Hash transactionHash, boolean milestoneRequest) {
        synchronized (syncObj) {
            return (milestoneRequest && milestoneTransactionsToRequest.contains(transactionHash))
                    || (!milestoneRequest && milestoneTransactionsToRequest.contains(transactionHash) ||
                    transactionsToRequest.contains(transactionHash));
        }
    }

    private boolean transactionsToRequestIsFull() {
        return transactionsToRequest.size() >= RequestQueueImpl.MAX_TX_REQ_QUEUE_SIZE;
    }


    @Override
    public Hash popTransaction() {
        boolean milestone = random.nextDouble() < config.getpSelectMilestoneChild();
        synchronized (syncObj) {
            Hash hash = null;
            // determine which set of transactions to operate on
            // determine the first hash in our set that needs to be processed

            Set<Hash> primarySet = milestone ? milestoneTransactionsToRequest : transactionsToRequest;
            Set<Hash> alternativeSet = milestone ? transactionsToRequest : milestoneTransactionsToRequest;
            Set<Hash> requestSet = primarySet.size() == 0 ? alternativeSet : primarySet;

            // repeat while we have transactions that shall be requested
            while (requestSet.size() != 0) {
                // remove the first item in our set for further examination
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();
                iterator.remove();

                boolean exists = false;
                try {
                    exists = TransactionViewModel.exists(tangle, hash);
                } catch (Exception e) {
                    log.error(e.toString());
                }
                // if we have received the transaction in the mean time ....
                if (exists) {
                    // ... dump a log message ...
                    log.info("Removed existing tx from request list: " + hash.toString());
                    tangle.publish("rtl %s", hash.toString());

                    // ... and continue to the next element in the set
                    continue;
                }

                // ... otherwise -> re-add it at the end of the set ...
                //
                // Note: we always have enough space since we removed the element before
                requestSet.add(hash);

                // ... and abort our loop to continue processing with the element we found
                break;
            }
            // randomly drop "non-milestone" transactions so we don't keep on asking for non-existent transactions forever
            if(random.nextDouble() < P_REMOVE_REQUEST && !requestSet.equals(milestoneTransactionsToRequest)) {
                log.trace("remove {} for tx to request", hash);
                transactionsToRequest.remove(hash);
            }
            return hash;
        }



    }

}
