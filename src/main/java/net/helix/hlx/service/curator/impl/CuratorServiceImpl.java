package net.helix.hlx.service.curator.impl;

import net.helix.hlx.BundleValidator;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.curator.CandidateValidity;
import net.helix.hlx.service.curator.CuratorException;
import net.helix.hlx.service.curator.CuratorService;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.SnapshotService;
import net.helix.hlx.storage.Tangle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.*;


import static net.helix.hlx.service.curator.CandidateValidity.*;

public class CuratorServiceImpl implements CuratorService {
    /**
     * Holds the logger of this class.
     */
    private final static Logger log = LoggerFactory.getLogger(CuratorServiceImpl.class);

    /**
     * Holds the tangle object which acts as a database interface.
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance of the snapshot package that allows us to roll back ledger states.
     */
    private SnapshotService snapshotService;

    /**
     * Configurations for milestone
     */
    private HelixConfig config;

    public CuratorServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, HelixConfig config) {

            this.tangle = tangle;
            this.snapshotProvider = snapshotProvider;
            this.snapshotService = snapshotService;
            this.config = config;

            return this;
    }

    @Override
    public CandidateValidity validateCandidate(TransactionViewModel transactionViewModel, SpongeFactory.Mode mode, int securityLevel) throws CuratorException {

        try {

            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                System.out.println("bundle empty");
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);
                    if (tail.getHash().equals(transactionViewModel.getHash())) {

                        //todo implement when sure how bundle structure has to look like
                        //if (isMilestoneBundleStructureValid(bundleTransactionViewModels, securityLevel)) {

                        Hash senderAddress = tail.getAddressHash();
                        boolean validSignature = Merkle.validateMerkleSignature(bundleTransactionViewModels, mode, senderAddress, securityLevel, config.getMilestoneKeyDepth());
                        System.out.println("valid signature (candidate): " + validSignature);

                        if ((config.isTestnet() && config.isDontValidateTestnetMilestoneSig()) || validSignature) {
                            return VALID;
                        } else {
                            return INVALID;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new CuratorException("error while checking candidate status of " + transactionViewModel.getHash(), e);
        }
        return INVALID;
    }

    /**
     * <p>
     * This method is a utility method that checks if the transactions belonging to the potential candidate bundle has
     * a valid structure (used during the validation of candidates).
     * </p>
     * <p>
     * It first checks if the bundle has enough transactions to conform to the given {@code securityLevel} and then
     * verifies that the {@code branchTransactionsHash}es are pointing to the {@code trunkTransactionHash} of the head
     * transactions.
     * </p>
     *
     * @param bundleTransactions all transactions belonging to the milestone
     * @param securityLevel the security level used for the signature
     * @return {@code true} if the basic structure is valid and {@code false} otherwise
     */
    private boolean isCandidateBundleStructureValid(List<TransactionViewModel> bundleTransactions, int securityLevel) {
        if (bundleTransactions.size() <= securityLevel) {
            return false;
        }

        Hash headTransactionHash = bundleTransactions.get(securityLevel).getTrunkTransactionHash();
        return bundleTransactions.stream()
                .limit(securityLevel)
                .map(TransactionViewModel::getBranchTransactionHash)
                .allMatch(branchTransactionHash -> branchTransactionHash.equals(headTransactionHash));
    }
    @Override
    public double getCandidateRelativeUptime(Hash address) {
        return 0;
    }

    @Override
    public double getCandidateNormalizedWeight(Hash candidateAddress, Set<Hash> seenCandidateTransactions, Double testWeight) {

        TransactionViewModel txvm;
        double powWeight = 0;
        //double uptimeWeight = getCandidateRelativeUptime(candidateAddress); (beta * uptimeWeight) // wt: + getCandidateRelativeUptime(h)
        double w = 0; // own weight
        double wt = 0; // total weight
        double alpha = 0.55; // weight factor for pow
        double beta = 0.02; // weight factor for relative uptime

        if(!seenCandidateTransactions.isEmpty()) {
            for (Hash h : seenCandidateTransactions) {
                try {
                    txvm = TransactionViewModel.fromHash(tangle, h);
                    if (txvm.getAddressHash().equals(candidateAddress)) {
                        powWeight += h.leadingZeros();
                    }
                } catch (Exception e) {
                    log.error("unexpected error while calculating own reputation", e);
                }
            }
            w = testWeight != null ? Math.exp(alpha * testWeight) : Math.exp(alpha * powWeight);
            wt = (seenCandidateTransactions.stream().mapToDouble(h -> Math.exp(alpha * (h.leadingZeros() ))).sum());
        }
        return wt != 0 ? w/wt : 0;
    }

    @Override
    public double getCandidateNormalizedWeight(Hash candidateAddress, Set<Hash> seenCandidateTransactions) {
        return getCandidateNormalizedWeight(candidateAddress, seenCandidateTransactions, null);
    }

    public void nominate(Map<Hash, Double> candidatesToNominate) {
    }

    @Override
    public boolean isCandidateConfirmed(TransactionViewModel transaction) {
        return true; // not yet implemented
    }
    @Override
    public boolean isCandidateConfirmed(TransactionViewModel transaction, int roundIndex) {
        return true; // not yet implemented
    }

}
