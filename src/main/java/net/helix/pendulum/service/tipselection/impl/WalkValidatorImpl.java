package net.helix.pendulum.service.tipselection.impl;

import net.helix.pendulum.conf.TipSelConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.ledger.LedgerService;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.tipselection.WalkValidator;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Implementation of {@link WalkValidator} that checks consistency of the ledger as part of validity checks.
 *
 *     A transaction is only valid if:
 *      <ol>
 *      <li>it is a tail
 *      <li>all the history of the transaction is present (is solid)
 *      <li>it does not reference an old unconfirmed transaction (not belowMaxDepth)
 *      <li>the ledger is still consistent if the transaction is added
 *          (balances of all addresses are correct and all signatures are valid)
 *      </ol>
 */
public class WalkValidatorImpl implements WalkValidator {

    private final Tangle tangle;
    private final Logger log = LoggerFactory.getLogger(WalkValidator.class);
    private final SnapshotProvider snapshotProvider;
    private final LedgerService ledgerService;
    private final TipSelConfig config;

    private Set<Hash> maxDepthOkMemoization;
    private Map<Hash, Long> myDiff;
    private Set<Hash> myApprovedHashes;

    /**
     * Constructor of Walk Validator
     * @param tangle Tangle object which acts as a database interface.
     * @param snapshotProvider grants access to snapshots od the ledger state.
     * @param ledgerService allows to perform ledger related logic.
     * @param config configurations to set internal parameters.
     */
    public WalkValidatorImpl(Tangle tangle, SnapshotProvider snapshotProvider, LedgerService ledgerService,
                             TipSelConfig config) {
        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.ledgerService = ledgerService;
        this.config = config;

        maxDepthOkMemoization = new HashSet<>();
        myDiff = new HashMap<>();
        myApprovedHashes = new HashSet<>();
    }

    @Override
    public boolean isValid(Hash transactionHash) throws Exception {

        TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, transactionHash);
        if(Hash.NULL_HASH.equals(transactionHash)){
            return true;
        }
        if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
            log.debug("transactionViewModel: {} ", transactionViewModel.getBytes());
            log.debug("transactionViewModel.Type: {} ", transactionViewModel.getType());
            log.debug("Validation failed: {} is missing in db", transactionHash.toString());
            return false;
        } else if (transactionViewModel.getCurrentIndex() != 0) {
            log.debug("Validation failed: {} not a tail", transactionHash.toString());
            return false;
        } else if (!transactionViewModel.isSolid()) {
            log.debug("Validation failed: {} is not solid", transactionHash.toString());
            return false;
        }
        // todo do we need this?
        /*else if (belowMaxDepth(transactionViewModel.getHash(),
                snapshotProvider.getLatestSnapshot().getIndex() - config.getMaxDepth())) {
            log.debug("Validation failed: {} is below max depth", transactionHash.toString());
            return false;
        }*/
            else if (!ledgerService.updateDiff(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
            log.debug("Validation failed: {} is not consistent", transactionHash.toString());
            return false;
        }
        else if (!ledgerService.isBalanceDiffConsistent(myApprovedHashes, myDiff, transactionViewModel.getHash())) {
            log.debug("Validation failed: {} balance is not consistent", transactionHash.toString());
            return false;
        }
        return true;
    }

    private boolean belowMaxDepth(Hash tip, int lowerAllowedSnapshotIndex) throws Exception {
        //if tip is confirmed stop
        if (TransactionViewModel.fromHash(tangle, tip).snapshotIndex() >= lowerAllowedSnapshotIndex) {
            return false;
        }
        //if tip unconfirmed, check if any referenced tx is confirmed below maxDepth
        Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(Collections.singleton(tip));
        Set<Hash> analyzedTransactions = new HashSet<>();
        Hash hash;
        final int maxAnalyzedTransactions = config.getBelowMaxDepthTransactionLimit();
        while ((hash = nonAnalyzedTransactions.poll()) != null) {
            if (analyzedTransactions.size() == maxAnalyzedTransactions) {
                log.debug("failed below max depth because of exceeding max threshold of {} analyzed transactions",
                        maxAnalyzedTransactions);
                return true;
            }

            if (analyzedTransactions.add(hash)) {
                TransactionViewModel transaction = TransactionViewModel.fromHash(tangle, hash);
                if ((transaction.snapshotIndex() != 0 || snapshotProvider.getInitialSnapshot().hasSolidEntryPoint(transaction.getHash()))
                        && transaction.snapshotIndex() < lowerAllowedSnapshotIndex) {
                    log.debug("failed below max depth because of reaching a tx below the allowed snapshot index {}",
                            lowerAllowedSnapshotIndex);
                    return true;
                }
                if (transaction.snapshotIndex() == 0) {
                    if (!maxDepthOkMemoization.contains(hash)) {
                        nonAnalyzedTransactions.offer(transaction.getTrunkTransactionHash());
                        nonAnalyzedTransactions.offer(transaction.getBranchTransactionHash());
                    }
                }
            }
        }
        maxDepthOkMemoization.add(tip);
        return false;
    }
}
