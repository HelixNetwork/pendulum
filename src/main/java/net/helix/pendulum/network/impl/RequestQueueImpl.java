package net.helix.pendulum.network.impl;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.network.RequestQueue;
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
public class RequestQueueImpl implements RequestQueue {

    private static final Logger log = LoggerFactory.getLogger(RequestQueueImpl.class);
    private final Set<Hash> milestoneTransactionsToRequest = new LinkedHashSet<>();
    private final Set<Hash> transactionsToRequest = new LinkedHashSet<>();

    public static final int MAX_TX_REQ_QUEUE_SIZE = 10000;

    private static double P_REMOVE_REQUEST;
    private static boolean initialized = false;
    private final SecureRandom random = new SecureRandom();

    private final Object syncObj = new Object();
    private final Tangle tangle;
    private final SnapshotProvider snapshotProvider;

    public RequestQueueImpl(Tangle tangle, SnapshotProvider snapshotProvider) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
    }

    public Pendulum.Initializable init() {
        PendulumConfig config = Pendulum.ServiceRegistry.get().resolve(PendulumConfig.class);
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
    public void enqueueTransaction(Hash hash, boolean milestone) throws Exception {
        if (!snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(hash) && !TransactionViewModel.exists(tangle, hash)) {
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
    public Hash popTransaction(boolean milestone) throws Exception {
        // determine which set of transactions to operate on
        Set<Hash> primarySet = milestone ? milestoneTransactionsToRequest : transactionsToRequest;
        Set<Hash> alternativeSet = milestone ? transactionsToRequest : milestoneTransactionsToRequest;
        Set<Hash> requestSet = primarySet.size() == 0 ? alternativeSet : primarySet;

        // determine the first hash in our set that needs to be processed
        Hash hash = null;
        synchronized (syncObj) {
            // repeat while we have transactions that shall be requested
            while (requestSet.size() != 0) {
                // remove the first item in our set for further examination
                Iterator<Hash> iterator = requestSet.iterator();
                hash = iterator.next();
                iterator.remove();

                // if we have received the transaction in the mean time ....
                if (TransactionViewModel.exists(tangle, hash)) {
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
        }

        // randomly drop "non-milestone" transactions so we don't keep on asking for non-existent transactions forever
        if(random.nextDouble() < P_REMOVE_REQUEST && !requestSet.equals(milestoneTransactionsToRequest)) {
            synchronized (syncObj) {
                transactionsToRequest.remove(hash);
            }
        }

        // return our result
        return hash;
    }

}
