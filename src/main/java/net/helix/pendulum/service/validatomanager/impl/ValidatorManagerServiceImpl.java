package net.helix.pendulum.service.validatomanager.impl;

import net.helix.pendulum.BundleValidator;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.crypto.merkle.impl.MerkleTreeImpl;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.SnapshotService;
import net.helix.pendulum.service.validatomanager.CandidateValidity;
import net.helix.pendulum.service.validatomanager.ValidatorManagerException;
import net.helix.pendulum.service.validatomanager.ValidatorManagerService;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static net.helix.pendulum.service.validatomanager.CandidateValidity.*;


public class ValidatorManagerServiceImpl implements ValidatorManagerService {
    /**
     * Holds the logger of this class.
     */
    private final static Logger log = LoggerFactory.getLogger(ValidatorManagerServiceImpl.class);

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
    private PendulumConfig config;

    public ValidatorManagerServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, PendulumConfig config) {

            this.tangle = tangle;
            this.snapshotProvider = snapshotProvider;
            this.snapshotService = snapshotService;
            this.config = config;

            return this;
    }

    @Override
    public CandidateValidity validateCandidate(TransactionViewModel transactionViewModel, SpongeFactory.Mode mode, int securityLevel, Set<Hash> validator) throws ValidatorManagerException {

        try {

            final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(tangle,
                    snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

            if (bundleTransactions.isEmpty()) {
                return INCOMPLETE;
            } else {
                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {
                    final TransactionViewModel tail = bundleTransactionViewModels.get(0);
                    if (tail.getHash().equals(transactionViewModel.getHash()) && isCandidateBundleStructureValid(bundleTransactionViewModels, securityLevel)) {

                        Hash senderAddress = tail.getAddressHash();

                        boolean validSignature = new MerkleTreeImpl().validateMerkleSignature(bundleTransactionViewModels,
                                new MerkleOptions(mode, senderAddress, securityLevel, config.getMilestoneKeyDepth()));

                        if ((config.isTestnet() && config.isDontValidateTestnetMilestoneSig()) || (validator.contains(senderAddress)) && validSignature) {
                            return VALID;
                        } else {
                            return INVALID;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new ValidatorManagerException("error while checking candidate status of " + transactionViewModel.getHash(), e);
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
        int lastIdx = securityLevel + 1;
        if (bundleTransactions.size() <= lastIdx) {
            return false;
        }

        // todo head trunk and branch of the rest of the tx are different
        Hash headTransactionHash = bundleTransactions.get(lastIdx).getTrunkTransactionHash();
        List<Hash> branch = bundleTransactions.stream()
                .map(TransactionViewModel::getBranchTransactionHash).collect(Collectors.toList());

        return bundleTransactions.stream()
                .limit(lastIdx)
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

    @Override
    public boolean isCandidateConfirmed(TransactionViewModel transaction) {
        return true; // not yet implemented
    }
    @Override
    public boolean isCandidateConfirmed(TransactionViewModel transaction, int roundIndex) {
        return true; // not yet implemented
    }

}
