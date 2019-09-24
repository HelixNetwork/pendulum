package net.helix.pendulum.service.nominee.impl;

import net.helix.pendulum.BundleValidator;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Merkle;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.nominee.NomineeException;
import net.helix.pendulum.service.nominee.NomineeService;
import net.helix.pendulum.service.nominee.NomineeValidity;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.SnapshotService;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static net.helix.pendulum.service.nominee.NomineeValidity.*;

public class NomineeServiceImpl implements NomineeService {

    private Tangle tangle;

    private SnapshotProvider snapshotProvider;

    private PendulumConfig config;

    public NomineeServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, PendulumConfig config) {

            this.tangle = tangle;
            this.snapshotProvider = snapshotProvider;
            this.config = config;

            return this;
    }

    @Override
    public NomineeValidity validateNominees(TransactionViewModel transactionViewModel, int roundIndex,
                                              SpongeFactory.Mode mode, int securityLevel) throws NomineeException {

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

                        if (isNomineeBundleStructureValid(bundleTransactionViewModels, securityLevel)) {

                            // validate signature
                            Hash senderAddress = tail.getAddressHash();
                            boolean validSignature = Merkle.validateMerkleSignature(bundleTransactionViewModels, mode, senderAddress, securityLevel, config.getCuratorKeyDepth());
                            //System.out.println("valid signature (nominee): " + validSignature);

                            if ((config.isTestnet() && config.isDontValidateTestnetMilestoneSig()) || (config.getCuratorAddress().equals(senderAddress) && validSignature)) {
                                return VALID;
                            } else {
                                return INVALID;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new NomineeException("error while validating nominees for round #" + roundIndex, e);
        }

        return INVALID;
    }


    private boolean isNomineeBundleStructureValid(List<TransactionViewModel> bundleTransactions, int securityLevel) {
        // todo maybe variable if there can be more than 16 nominees
        int lastIdx = securityLevel + 1;

        if (bundleTransactions.size() <= lastIdx) {
            return false;
        }

        Hash headTransactionHash = bundleTransactions.get(lastIdx).getTrunkTransactionHash();
        return bundleTransactions.stream()
                .limit(lastIdx)
                .map(TransactionViewModel::getBranchTransactionHash)
                .allMatch(branchTransactionHash -> branchTransactionHash.equals(headTransactionHash));
    }
}
