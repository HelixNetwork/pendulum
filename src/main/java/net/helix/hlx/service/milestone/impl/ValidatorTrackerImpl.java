package net.helix.hlx.service.milestone.impl;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.AddressViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.milestone.ValidatorTracker;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.log.interval.IntervalLogger;
import net.helix.hlx.utils.thread.DedicatedScheduledExecutorService;
import net.helix.hlx.utils.thread.SilentScheduledExecutorService;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class ValidatorTrackerImpl implements ValidatorTracker {

    private static final int MAX_CANDIDATES_TO_ANALYZE = 5000;
    private static final int RESCAN_INTERVAL = 1000;
    private Hash Trustee_Address;

    private static final IntervalLogger log = new IntervalLogger(LatestMilestoneTrackerImpl.class);
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Validator Tracker", log.delegate());

    private Tangle tangle;
    private LatestMilestoneTracker latestMilestoneTracker;
    private Set<Hash> validatorAddresses;
    private final Set<Hash> seenTrusteeTransactions = new HashSet<>();
    private final Deque<Hash> trusteeTransactionsToAnalyze = new ArrayDeque<>();

    private boolean firstRun = true;

    public ValidatorTrackerImpl init(Tangle tangle, LatestMilestoneTracker latestMilestoneTracker, HelixConfig config) {

        this.tangle = tangle;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.Trustee_Address = config.getTrusteeAddress();

        return this;
    }

    public boolean processTrusteeTransaction(Hash transactionHash) throws Exception {
        TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, transactionHash);
        try {
            if (Trustee_Address.equals(transaction.getAddressHash()) && transaction.getCurrentIndex() == 0) {
                return true;
            }else {
                return false;
            }
        } catch (Exception e) {
            throw new Exception("unexpected error while processing trustee transaction " + transaction, e);
        }
    }

    public void updateValidatorAddresses() throws Exception{

    }

    public void analyzeTrusteeTransactions() throws Exception {
        int transactionsToAnalyze = Math.min(trusteeTransactionsToAnalyze.size(), MAX_CANDIDATES_TO_ANALYZE);
        for (int i = 0; i < transactionsToAnalyze; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            Hash trusteeTransactionHash = trusteeTransactionsToAnalyze.pollFirst();
            if(!processTrusteeTransaction(trusteeTransactionHash)) {
                seenTrusteeTransactions.remove(trusteeTransactionHash);
            }
        }
    }

    public void collectNewTrusteeTransactions() throws Exception {
        try {
            for (Hash hash : AddressViewModel.load(tangle, Trustee_Address).getHashes()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (seenTrusteeTransactions.add(hash)) {
                    trusteeTransactionsToAnalyze.addFirst(hash);
                }
            }
        } catch (Exception e) {
            throw new Exception("failed to collect the new trustee transactions", e);
        }
    }

    private void validatorTrackerThread() {
        try {
            collectNewTrusteeTransactions();
            analyzeTrusteeTransactions();
        } catch (Exception e) {
            log.error("error while scaning for trustee transactions", e);
        }
    }

    public void start(){
        executorService.silentScheduleWithFixedDelay(this::validatorTrackerThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    public void shutdown(){
        executorService.shutdownNow();
    }
}
