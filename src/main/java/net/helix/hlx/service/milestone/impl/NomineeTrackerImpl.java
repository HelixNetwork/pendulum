package net.helix.hlx.service.milestone.impl;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.BundleValidator;
import net.helix.hlx.controllers.AddressViewModel;
import net.helix.hlx.controllers.BundleViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.service.milestone.*;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.log.interval.IntervalLogger;
import net.helix.hlx.utils.thread.DedicatedScheduledExecutorService;
import net.helix.hlx.utils.thread.SilentScheduledExecutorService;

import static net.helix.hlx.service.milestone.MilestoneValidity.INCOMPLETE;
import static net.helix.hlx.service.milestone.MilestoneValidity.INVALID;
import static net.helix.hlx.service.milestone.MilestoneValidity.VALID;


import java.util.*;
import java.util.concurrent.TimeUnit;

public class NomineeTrackerImpl implements NomineeTracker {

    private static final int MAX_CANDIDATES_TO_ANALYZE = 5000;
    private static final int RESCAN_INTERVAL = 1000;
    private Hash Curator_Address;

    private static final IntervalLogger log = new IntervalLogger(NomineeTrackerImpl.class);
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Nominee Tracker", log.delegate());

    private Tangle tangle;
    private HelixConfig config;
    private SnapshotProvider snapshotProvider;
    private MilestoneService milestoneService;
    private MilestoneSolidifier milestoneSolidifier;
    private Set<Hash> latestNominees;
    private int startRound;
    private final Set<Hash> seenCuratorTransactions = new HashSet<>();
    private final Deque<Hash> curatorTransactionsToAnalyze = new ArrayDeque<>();

    public NomineeTrackerImpl init(Tangle tangle, SnapshotProvider snapshotProvider, MilestoneService milestoneService, MilestoneSolidifier milestoneSolidifier, HelixConfig config) {

        this.tangle = tangle;
        this.config = config;
        this.Curator_Address = config.getTrusteeAddress();
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
        this.milestoneSolidifier = milestoneSolidifier;

        latestNominees = new HashSet<>();
        latestNominees.add(HashFactory.ADDRESS.create("6a8413edc634e948e3446806afde11b17e0e188faf80a59a8b1147a0600cc5db"));
        latestNominees.add(HashFactory.ADDRESS.create("cc439e031810f847e4399477e46fd12de2468f12cd0ba85447404148bee2a033"));

        startRound = 1;
        //bootstrapLatestNominees();

        return this;
    }

    @Override
    public Set<Hash> getLatestNominees() {
        return latestNominees;
    }

    @Override
    public int getStartRound() {return startRound; }


    @Override
    public Set<Hash> getNomineesOfRound(int roundIndex) throws Exception{
        try {
            Set<Hash> validators = new HashSet<>();
            for (Hash hash : AddressViewModel.load(tangle, Curator_Address).getHashes()) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if (RoundViewModel.getRoundIndex(transaction) == roundIndex) {
                    validators = getNomineeAddresses(hash);
                }
            }
            return validators;
        }catch (Exception e) {
            throw new Exception("unexpected error while getting Validators of round #{}" + roundIndex, e);
        }
    }


    public MilestoneValidity validateNominees(TransactionViewModel transactionViewModel, int roundIndex,
                                              SpongeFactory.Mode mode, int securityLevel) throws Exception {

        if (roundIndex < 0 || roundIndex >= 0x200000) {
            return INVALID;
        }

        try {

            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);   // transaction with signature
                    if (tail.getHash().equals(transactionViewModel.getHash())) {

                        // validate signature
                        Hash senderAddress = tail.getAddressHash();
                        boolean validSignature = Merkle.validateMerkleSignature(bundleTransactionViewModels, mode, senderAddress, roundIndex, securityLevel, config.getNumberOfKeysInMilestone());

                        if (Curator_Address.equals(senderAddress) && validSignature) {
                            return VALID;
                        } else {
                            return INVALID;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new MilestoneException("error while validating nominees for round #" + roundIndex, e);
        }

        return INVALID;
    }

    @Override
    public boolean processNominees(Hash transactionHash) throws Exception {
        TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, transactionHash);
        try {
            if (Curator_Address.equals(transaction.getAddressHash())) {

                int roundIndex = RoundViewModel.getRoundIndex(transaction);

                // if the trustee transaction is older than our ledger start point: we already processed it in the past
                if (roundIndex <= snapshotProvider.getInitialSnapshot().getIndex()) {
                    return true;
                }

                // validate
                switch (validateNominees(transaction, roundIndex, SpongeFactory.Mode.S256, 1)) {
                    case VALID:
                        if (roundIndex > startRound) {
                            latestNominees = getNomineeAddresses(transaction.getHash());
                            startRound = roundIndex;
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
    public Set<Hash> getNomineeAddresses(Hash transaction) throws Exception{
        TransactionViewModel tail = TransactionViewModel.fromHash(tangle, transaction);
        BundleViewModel bundle = BundleViewModel.load(tangle, tail.getBundleHash());
        int security = 1;
        Set<Hash> validators = new HashSet<>();

        for (Hash txHash : bundle.getHashes()) {
            TransactionViewModel tx = TransactionViewModel.fromHash(tangle, txHash);
            // get transactions with validator addresses in signatureMessageFragment
            // 0 - security: tx with signature
            // security: tx with merkle path
            // security+1 - n: tx with validator addresses
            if ((tx.getCurrentIndex() > security)) {

                for (int i = 0; i < TransactionViewModel.SIGNATURE_MESSAGE_FRAGMENT_SIZE / Hash.SIZE_IN_BYTES; i++) {
                    Hash address = HashFactory.ADDRESS.create(tx.getSignature(), i * Hash.SIZE_IN_BYTES, Hash.SIZE_IN_BYTES);
                    // TODO: Do we send all validators or only adding and removing ones ?
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
    public void analyzeCuratorTransactions() throws Exception {
        int transactionsToAnalyze = Math.min(curatorTransactionsToAnalyze.size(), MAX_CANDIDATES_TO_ANALYZE);
        for (int i = 0; i < transactionsToAnalyze; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            Hash trusteeTransactionHash = curatorTransactionsToAnalyze.pollFirst();
            if(!processNominees(trusteeTransactionHash)) {
                seenCuratorTransactions.remove(trusteeTransactionHash);
            }
        }
    }

    @Override
    public void collectNewCuratorTransactions() throws Exception {
        try {
            for (Hash hash : AddressViewModel.load(tangle, Curator_Address).getHashes()) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
                if (seenCuratorTransactions.add(hash)) {
                    curatorTransactionsToAnalyze.addFirst(hash);
                }
            }
        } catch (Exception e) {
            throw new Exception("failed to collect the new trustee transactions", e);
        }
    }

    private void validatorTrackerThread() {
        try {
            collectNewCuratorTransactions();
            analyzeCuratorTransactions();
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
