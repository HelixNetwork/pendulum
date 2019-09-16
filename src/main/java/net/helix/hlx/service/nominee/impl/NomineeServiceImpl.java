package net.helix.hlx.service.nominee.impl;

import net.helix.hlx.BundleValidator;
import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.Merkle;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.nominee.NomineeValidity;
import net.helix.hlx.service.nominee.NomineeException;
import net.helix.hlx.service.nominee.NomineeService;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.service.snapshot.SnapshotService;
import net.helix.hlx.storage.Tangle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;


import static net.helix.hlx.service.nominee.NomineeValidity.*;

public class NomineeServiceImpl implements NomineeService {

    private final static Logger log = LoggerFactory.getLogger(NomineeServiceImpl.class);

    private Tangle tangle;

    private SnapshotProvider snapshotProvider;

    private SnapshotService snapshotService;

    private HelixConfig config;

    public NomineeServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService, HelixConfig config) {

            this.tangle = tangle;
            this.snapshotProvider = snapshotProvider;
            this.snapshotService = snapshotService;
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
