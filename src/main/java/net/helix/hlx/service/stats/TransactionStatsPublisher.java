package net.helix.hlx.service.stats;

import net.helix.hlx.controllers.TipsViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.tipselection.TipSelector;
import net.helix.hlx.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publishes the number of total transactions and confirmed transactions to the ZeroMQ.
 *
 * Only transactions are counted that have an arrival time between 5 minutes and 2 hours in the past.
 *
 * For the confirmed transactions, the normal tip selection is performed to determine a supertip. The number of
 * transactions in its past set is then published to ZMQ.
 */
public class TransactionStatsPublisher {

    private static final long PUBLISH_INTERVAL = Duration.ofSeconds(10).toMillis();

    private static final String CONFIRMED_TRANSACTIONS_TOPIC = "ct5s2m";
    private static final String TOTAL_TRANSACTIONS_TOPIC = "t5s2m";

    private static final Duration MIN_TRANSACTION_AGE_THRESHOLD = Duration.ofSeconds(5);
    private static final Duration MAX_TRANSACTION_AGE_THRESHOLD = Duration.ofMinutes(2);

    private final Logger log = LoggerFactory.getLogger(TransactionStatsPublisher.class);

    private final Tangle tangle;
    private final TipsViewModel tipsViewModel;
    private final TipSelector tipsSelector;
    private final TimeWindowedApproveeCounter approveeCounter;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private Thread thread;


    public TransactionStatsPublisher(Tangle tangle, TipsViewModel tipsViewModel, TipSelector tipsSelector) {

        this.tangle = tangle;
        this.tipsViewModel = tipsViewModel;
        this.tipsSelector = tipsSelector;
        this.approveeCounter = new TimeWindowedApproveeCounter(tangle, MIN_TRANSACTION_AGE_THRESHOLD,
                MAX_TRANSACTION_AGE_THRESHOLD);
    }

    /**
     * Starts the publisher.
     */
    public void init() {
        thread = new Thread(getRunnable(), "Transaction Stats Publisher");
        thread.start();
    }

    private Runnable getRunnable() {
        return () -> {
            while (!shuttingDown.get()) {
                try {
                    final Instant now = Instant.now();

                    final long numConfirmed = getConfirmedTransactionsCount(now);
                    final long numTransactions = getAllTransactionsCount(now);

                    tangle.publish(CONFIRMED_TRANSACTIONS_TOPIC + " %d", numConfirmed);
                    tangle.publish(TOTAL_TRANSACTIONS_TOPIC + " %d", numTransactions);

                } catch (Exception e) {
                    log.error("Error while getting transaction counts : {}", e);
                }
                try {
                    Thread.sleep(PUBLISH_INTERVAL);
                } catch (InterruptedException e) {
                    log.debug("Transaction count interrupted.");
                }
            }
        };
    }

    private Hash getSuperTip() throws Exception {
        // call the usual tip selection and return the first tip
        List<Hash> tips = tipsSelector.getTransactionsToApprove(3, Optional.empty());

        return tips.get(0);
    }

    private long getConfirmedTransactionsCount(Instant now) throws Exception {
<<<<<<< HEAD
        return approveeCounter.getCount(now, getSuperTip(), new HashSet<>());
=======

        return approveeCounter.getCount(now, getSuperTip(), new HashSet<>(), true);
>>>>>>> upstream/dev
    }

    private long getAllTransactionsCount(Instant now) throws Exception {
        // count all transactions in a scalable way, by counting the parents of all the tips
        HashSet<Hash> processedTransactions = new HashSet<>();
        long count = 0;
        for (Hash tip : tipsViewModel.getTips()) {
            // count the tip, if it is the valid time window
            log.debug("DZMQ: {}", tip.hexString());
            if (approveeCounter.isInTimeWindow(now, TransactionViewModel.fromHash(tangle, tip))) {
                count += 1 + approveeCounter.getCount(now, tip, processedTransactions, false);
            } else {
<<<<<<< HEAD
                // even if the tip is not in the time window, count parents that might be older
                count += approveeCounter.getCount(now, tip, processedTransactions);
=======
                // even if the tip is not in the time window, count approvees that might be older
                count += approveeCounter.getCount(now, tip, processedTransactions, false);
>>>>>>> upstream/dev
            }
        }

        return count;
    }

    /**
     * Stops the publisher.
     */
    public void shutdown() {
        log.info("Shutting down TransactionStatsPublisher...");
        shuttingDown.set(true);
        // todo: remove this if we want the the count to be finished.
        try {
            if (thread != null && thread.isAlive()) {
                thread.interrupt(); // we will interrupt tx count for now, so shutdown hook doesn't have to wait for count to complete.
                //thread.join();
            }
        } catch (Exception e) {
            log.error("Error in shutdown", e);
        }
    }
}
