package net.helix.hlx.service.milestone.impl;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.AddressViewModel;
import net.helix.hlx.controllers.BundleViewModel;
import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.IntegerIndex;
import net.helix.hlx.model.persistables.Round;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.milestone.MilestoneService;
import net.helix.hlx.service.milestone.MilestoneSolidifier;
import net.helix.hlx.service.milestone.ValidatorTracker;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.log.interval.IntervalLogger;
import net.helix.hlx.utils.thread.DedicatedScheduledExecutorService;
import net.helix.hlx.utils.thread.SilentScheduledExecutorService;

import java.util.*;
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
    private SnapshotProvider snapshotProvider;
    private MilestoneService milestoneService;
    private MilestoneSolidifier milestoneSolidifier;
    private Set<Hash> latestValidators = new HashSet<>();
    private final Set<Hash> seenTrusteeTransactions = new HashSet<>();
    private final Deque<Hash> trusteeTransactionsToAnalyze = new ArrayDeque<>();

    public ValidatorTrackerImpl init(Tangle tangle, LatestMilestoneTracker latestMilestoneTracker, SnapshotProvider snapshotProvider, MilestoneService milestoneService, MilestoneSolidifier milestoneSolidifier, HelixConfig config) {

        this.tangle = tangle;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.Trustee_Address = config.getTrusteeAddress();
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
        this.milestoneSolidifier = milestoneSolidifier;
        //bootstrapLatestValidators();

        return this;
    }

    @Override
    public Set<Hash> getLatestValidators() {
        return latestValidators;
    }


    @Override
    public Set<Hash> getValidatorsOfRound(int roundIndex) throws Exception{
        try {
            Set<Hash> validators = new HashSet<>();
            for (Hash hash : AddressViewModel.load(tangle, Trustee_Address).getHashes()) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if (milestoneService.getRoundIndex(transaction) == roundIndex) {
                    validators = getValidatorAddresses(hash);
                }
            }
            return validators;
        }catch (Exception e) {
            throw new Exception("unexpected error while getting Validators of round #{}" + roundIndex, e);
        }
    }

    @Override
    public boolean processTrusteeTransaction(Hash transactionHash) throws Exception {
        TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, transactionHash);
        try {
            if (Trustee_Address.equals(transaction.getAddressHash())) {

                int roundIndex = milestoneService.getRoundIndex(transaction);

                // if the trustee transaction is older than our ledger start point: we already processed it in the past
                if (roundIndex <= snapshotProvider.getInitialSnapshot().getIndex()) {
                    return true;
                }

                // we use milestone validation because its the same process (TODO: Rename function and implement more general)
                Set<Hash> validation = new HashSet<>();
                validation.add(Trustee_Address);
                switch (milestoneService.validateMilestone(transaction, roundIndex, SpongeFactory.Mode.S256, 1, validation)) {
                    case VALID:
                        if (roundIndex > latestMilestoneTracker.getCurrentRoundIndex()) {
                            latestValidators = getValidatorAddresses(transaction.getHash());
                        }

                        if (!transaction.isSolid()) {
                            milestoneSolidifier.add(transaction.getHash(), roundIndex);
                        }

                        break;

                    case INCOMPLETE:
                        milestoneSolidifier.add(transaction.getHash(), roundIndex);

                        return false;

                    default:
                }
            }else {
                return false;
            }
            return true;
        } catch (Exception e) {
            throw new Exception("unexpected error while processing trustee transaction " + transaction, e);
        }
    }

    @Override
    public Set<Hash> getValidatorAddresses(Hash transaction) throws Exception{
        TransactionViewModel tail = TransactionViewModel.fromHash(tangle, transaction);
        BundleViewModel bundle = BundleViewModel.load(tangle, tail.getBundleHash());
        int security = 1;
        Set<Hash> validators = new HashSet<>();

        for (Hash txHash : bundle.getHashes()) {
            TransactionViewModel tx = TransactionViewModel.fromHash(tangle, txHash);
            // get transactions with validator addresses in signatureMessageFragment
            // -> we have to count from last index
            // n-security-1 to n: tx with signature
            // n-security-1: tx with merkle path
            // 0 to n-security: tx with validator addresses
            if ((tx.getCurrentIndex() <= 0) && (tx.getCurrentIndex() < bundle.size() - security - 1)) {
                for (int i = 0; i < TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE / Hash.SIZE_IN_BYTES; i++) {
                    Hash address = HashFactory.ADDRESS.create(Arrays.copyOfRange(tx.getSignature(), i * Hash.SIZE_IN_BYTES, (i+1) * Hash.SIZE_IN_BYTES));
                    // TODO: Do we send all validators or only adding and removing ones
                    if (address.equals(Hash.NULL_HASH)){
                        break;
                    }
                    validators.add(address);
                }
            }
        }
        return validators;
    }

    @Override
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

    @Override
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
