package net.helix.pendulum.network;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.model.Hash;

/**
 * This interface encapsulates the queue of transactions used
 * by the requester thread. The clients should use
 * <code>enqueueTransaction()</code> in order to place the required
 * transaction <code>Hash</code> into the queue.
 *
 * Access to the service is thread-safe.
 *
 * Date: 2019-11-05
 * Author: zhelezov
 */
public interface RequestQueue extends Pendulum.Initializable {
    Hash[] getRequestedTransactions();

    int size();

    boolean clearTransactionRequest(Hash hash);

    void enqueueTransaction(Hash hash, boolean milestone) throws Exception;

    boolean isTransactionRequested(Hash transactionHash, boolean milestoneRequest);

    Hash popTransaction(boolean milestone) throws Exception;
}
