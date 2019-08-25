package net.helix.hlx.service.stats;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.dag.DAGHelper;
import net.helix.hlx.utils.dag.TraversalException;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;

/**
 * Utility class to count the number of distinct approvees that have an arrival time within the given time window.
 */
class TimeWindowedApproveeCounter {

    private final Tangle tangle;
    private final long minTransactionAgeSeconds;
    private final long maxTransactionAgeSeconds;

    TimeWindowedApproveeCounter(Tangle tangle, Duration minTransactionAge, Duration maxTransactionAge) {
        this.tangle = tangle;
        this.minTransactionAgeSeconds = minTransactionAge.getSeconds();
        this.maxTransactionAgeSeconds = maxTransactionAge.getSeconds();
    }

    private boolean isOlderThanMaxAge(Instant now, TransactionViewModel transaction) {
        // stop the traversing as soon as the transaction is older than the max
        final long age = now.getEpochSecond() - transaction.getArrivalTime();
        return age <= maxTransactionAgeSeconds;
    }

    private boolean isConfirmed(TransactionViewModel transaction) {
        return transaction.snapshotIndex() > 0;
    }

    /**
     * Checks whether the transaction is in the validity time window.
     *
     * @param now         current time epoch in seconds
     * @param transaction transaction to check
     * @return true, if the arrival time is in the time window, false otherwise
     */
    protected boolean isInTimeWindow(Instant now, TransactionViewModel transaction) {

        final long age = now.getEpochSecond() - transaction.getArrivalTime();
        return (age >= minTransactionAgeSeconds && age <= maxTransactionAgeSeconds);
    }

    /**
     * Counts the number of distinct approvees of the starting transaction, that are within the validity time window.
     *
     * The approvee traversal is not continued for transactions that are older than the maximum age.
     *
     * @param now                     current time epoch in seconds
     * @param startingTransactionHash the starting point of the traversal
     * @param processedTransactions   a set of hashes that is considered as "processed" and will consequently be ignored
     *                                in the traversal. This set is updated during the traversal to include all the
     *                                processed hashes.
     * @throws TraversalException if anything goes wrong while traversing the graph and processing the transactions
     */
    protected long getCount(Instant now, Hash startingTransactionHash, HashSet<Hash> processedTransactions, boolean onlyConfirmed)
            throws TraversalException {

        DAGHelper helper = DAGHelper.get(tangle);

        ValidTransactionCounter transactionCounter = new ValidTransactionCounter(now, onlyConfirmed);
        helper.traverseApprovees(startingTransactionHash, t -> isOlderThanMaxAge(now, t), transactionCounter::count,
                    processedTransactions);

        return transactionCounter.getCount();
    }

    /**
     * Counts the consumed transactions that arrived in the valid interval.
     *
     * It is assumed that the same transaction is not consumed more than once.
     */
    private final class ValidTransactionCounter {

        private final Instant now;
        private boolean confirmed;

        private long count = 0;

        private ValidTransactionCounter(Instant now, boolean onlyConfirmed) {
            this.now = now;
            this.confirmed = onlyConfirmed;
        }

        private void count(TransactionViewModel transactionViewModel) {
            if (isInTimeWindow(now, transactionViewModel)) {
                if (confirmed) {
                    if (isConfirmed(transactionViewModel)){
                        count++;
                    }
                } else {
                    count++;
                }
            }
        }

        private long getCount() {
            return count;
        }
    }
}