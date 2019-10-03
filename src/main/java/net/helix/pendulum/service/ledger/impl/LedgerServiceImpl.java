package net.helix.pendulum.service.ledger.impl;

import net.helix.pendulum.BundleValidator;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.StateDiffViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.ledger.LedgerException;
import net.helix.pendulum.service.ledger.LedgerService;
import net.helix.pendulum.service.milestone.MilestoneService;
import net.helix.pendulum.service.snapshot.SnapshotException;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.snapshot.SnapshotService;
import net.helix.pendulum.service.snapshot.impl.SnapshotStateDiffImpl;
import net.helix.pendulum.service.spentaddresses.impl.SpentAddressesServiceImpl;
import net.helix.pendulum.storage.Tangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Creates a service instance that allows us to perform ledger state specific operations.<br />
 * <br />
 * This class is stateless and does not hold any domain specific models.<br />
 */
public class LedgerServiceImpl implements LedgerService {
    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);
    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    private PendulumConfig config;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance containing the business logic of the snapshot package.<br />
     */
    private SnapshotService snapshotService;

    /**
     * Holds a reference to the service instance containing the business logic of the milestone package.<br />
     */
    private MilestoneService milestoneService;


    /**
     * Initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code ledgerService = new LedgerServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider snapshot provider which gives us access to the relevant snapshots
     * @param snapshotService service instance of the snapshot package that gives us access to packages' business logic
     * @param milestoneService contains the important business logic when dealing with milestones
     * @return the initialized instance itself to allow chaining
     */
    public LedgerServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider, SnapshotService snapshotService,
                                  MilestoneService milestoneService, PendulumConfig config) {

        this.tangle = tangle;
        this.config = config;
        this.snapshotProvider = snapshotProvider;
        this.snapshotService = snapshotService;
        this.milestoneService = milestoneService;

        return this;
    }


    @Override
    public boolean updateDiff(Set<Hash> approvedHashes, final Map<Hash, Long> diff, Hash tip) {
        return true;
    }

    @Override
    public void restoreLedgerState() throws LedgerException {
        try {
            Optional<RoundViewModel> milestone = milestoneService.findLatestProcessedSolidRoundInDatabase();
            //System.out.println(milestone);
            if (milestone.isPresent()) {
                //System.out.println(milestone.get().index());
                snapshotService.replayMilestones(snapshotProvider.getLatestSnapshot(), milestone.get().index());
            }
        } catch (Exception e) {
            throw new LedgerException("unexpected error while restoring the ledger state", e);
        }
    }

    @Override
    public boolean applyRoundToLedger(RoundViewModel round) throws LedgerException {
        if(generateStateDiff(round)) {
            try {
                snapshotService.replayMilestones(snapshotProvider.getLatestSnapshot(), round.index());
                log.debug("Replayed milestones +1");
                //snapshotProvider.getLatestSnapshot().getBalances().forEach((address, balance) -> log.debug(("Address: " + address.toString() + ", " + balance)));
            } catch (SnapshotException e) {
                throw new LedgerException("failed to apply the balance changes to the ledger state", e);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean tipsConsistent(List<Hash> tips) throws LedgerException {
        Set<Hash> visitedHashes = new HashSet<>();
        Map<Hash, Long> diff = new HashMap<>();
        for (Hash tip : tips) {
            if (!isBalanceDiffConsistent(visitedHashes, diff, tip)) {
                return false;
            }
        }

        return true;
    }
    // TODO: remove debug verbosity
    @Override
    public boolean isBalanceDiffConsistent(Set<Hash> approvedHashes, Map<Hash, Long> diff, Hash tip) throws
            LedgerException {

        try {
            if (!TransactionViewModel.fromHash(tangle, tip).isSolid()) {
                return false;
            }
        } catch (Exception e) {
            throw new LedgerException("failed to check the consistency of the balance changes", e);
        }

        if (approvedHashes.contains(tip)) {
            return true;
        }
        Set<Hash> visitedHashes = new HashSet<>(approvedHashes);
        Set<Hash> startHashes = new HashSet<>(Collections.singleton(tip));
        Map<Hash, Long> currentState = generateBalanceDiff(visitedHashes, startHashes,
                snapshotProvider.getLatestSnapshot().getIndex());
        if (currentState == null) {
            return false;
        }
        diff.forEach((key, value) -> {
            if (currentState.computeIfPresent(key, ((hash, aLong) -> value + aLong)) == null) {
                currentState.putIfAbsent(key, value);
            }
        });
        boolean isConsistent = snapshotProvider.getLatestSnapshot().patchedState(new SnapshotStateDiffImpl(
                currentState)).isConsistent();
        if (isConsistent) {
            diff.putAll(currentState);
            approvedHashes.addAll(visitedHashes);
        }
        return isConsistent;
    }

    @Override
    public Map<Hash, Long> generateBalanceDiff(Set<Hash> visitedTransactions, Set<Hash> startTransactions, int milestoneIndex)
            throws LedgerException {

        //System.out.println("Generate balance diff for round " + milestoneIndex);

        Map<Hash, Long> state = new HashMap<>();
        Set<Hash> countedTx = new HashSet<>();

        snapshotProvider.getInitialSnapshot().getSolidEntryPoints().keySet().forEach(solidEntryPointHash -> {
            visitedTransactions.add(solidEntryPointHash);
            countedTx.add(solidEntryPointHash);
        });

        final Queue<Hash> nonAnalyzedTransactions = new LinkedList<>(startTransactions);
        Hash transactionPointer;
        while ((transactionPointer = nonAnalyzedTransactions.poll()) != null) {
            if (visitedTransactions.add(transactionPointer)) {
                try {
                    final TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle,
                            transactionPointer);
                    // only take transactions into account that have not been confirmed by the referenced milestone, yet
                    //System.out.println("Transaction " + transactionPointer.toString() + " is confirmed: " + milestoneService.isTransactionConfirmed(transactionViewModel, milestoneIndex));
                    if (!milestoneService.isTransactionConfirmed(transactionViewModel, milestoneIndex)) {
                        if (transactionViewModel.getType() == TransactionViewModel.PREFILLED_SLOT) {
                            return null;
                        } else {
                            if (transactionViewModel.getCurrentIndex() == 0) {
                                boolean validBundle = false;

                                final List<List<TransactionViewModel>> bundleTransactions = BundleValidator.validate(
                                        tangle, snapshotProvider.getInitialSnapshot(), transactionViewModel.getHash());

                                for (final List<TransactionViewModel> bundleTransactionViewModels : bundleTransactions) {

                                    if (BundleValidator.isInconsistent(bundleTransactionViewModels)) {
                                        break;
                                    }
                                    if (bundleTransactionViewModels.get(0).getHash().equals(transactionViewModel.getHash())) {
                                        validBundle = true;

                                        for (final TransactionViewModel bundleTransactionViewModel : bundleTransactionViewModels) {

                                            if (bundleTransactionViewModel.value() != 0 && countedTx.add(bundleTransactionViewModel.getHash())) {

                                                final Hash address = bundleTransactionViewModel.getAddressHash();
                                                final Long value = state.get(address);
                                                state.put(address, value == null ? bundleTransactionViewModel.value()
                                                        : Math.addExact(value, bundleTransactionViewModel.value()));
                                            }
                                        }

                                        break;
                                    }
                                }
                                if (!validBundle) {
                                    return null;
                                }
                            }
                            if (!visitedTransactions.contains(transactionViewModel.getTrunkTransactionHash())) {
                                nonAnalyzedTransactions.offer(transactionViewModel.getTrunkTransactionHash());
                            }
                            if (!visitedTransactions.contains(transactionViewModel.getBranchTransactionHash())) {
                                nonAnalyzedTransactions.offer(transactionViewModel.getBranchTransactionHash());
                            }
                        }
                    }
                } catch (Exception e) {
                    throw new LedgerException("unexpected error while generating the balance diff", e);
                }
            }
        }

        return state;
    }

    /**
     * Generates the {@link net.helix.pendulum.model.StateDiff} that belongs to the given milestone in the database and marks
     * all transactions that have been approved by the milestone accordingly by setting their {@code snapshotIndex}
     * value.<br />
     * <br />
     * It first checks if the {@code snapshotIndex} of the transaction belonging to the milestone was correctly set
     * already (to determine if this milestone was processed already) and proceeds to generate the {@link
     * net.helix.pendulum.model.StateDiff} if that is not the case. To do so, it calculates the balance changes, checks if
     * they are consistent and only then writes them to the database.<br />
     * <br />
     * If inconsistencies in the {@code snapshotIndex} are found it issues a reset of the corresponding milestone to
     * recover from this problem.<br />
     *
     * @param round the milestone that shall have its {@link net.helix.pendulum.model.StateDiff} generated
     * @return {@code true} if the {@link net.helix.pendulum.model.StateDiff} could be generated and {@code false} otherwise
     * @throws LedgerException if anything unexpected happens while generating the {@link net.helix.pendulum.model.StateDiff}
     */
    private boolean generateStateDiff(RoundViewModel round) throws LedgerException {
        try {
            /*TransactionViewModel transactionViewModel = TransactionViewModel.fromHash(tangle, round.getHash());

            if (!transactionViewModel.isSolid()) {
                return false;
            }

            final int transactionSnapshotIndex = transactionViewModel.snapshotIndex();
            boolean successfullyProcessed = transactionSnapshotIndex == round.index();
            if (!successfullyProcessed) {
                // if the snapshotIndex of our transaction was set already, we have processed our milestones in
                // the wrong order (i.e. while rescanning the db)
                if (transactionSnapshotIndex != 0) {
                    milestoneService.resetCorruptedRound(round.index());
                }*/

            snapshotProvider.getLatestSnapshot().lockRead();
            boolean successfullyProcessed;
            try {
                    Set<Hash> confirmedTips = milestoneService.getConfirmedTips(round.index());
                    //System.out.println("Confirmed Tips:");
                    //confirmedTips.forEach(tip -> System.out.println(tip.toString()));
                    Map<Hash, Long> balanceChanges = generateBalanceDiff(new HashSet<>(), confirmedTips == null? new HashSet<>() : confirmedTips,
                            snapshotProvider.getLatestSnapshot().getIndex() + 1);
                    successfullyProcessed = balanceChanges != null;
                    if (successfullyProcessed) {
                        successfullyProcessed = snapshotProvider.getLatestSnapshot().patchedState(
                                new SnapshotStateDiffImpl(balanceChanges)).isConsistent();
                        if (successfullyProcessed) {
                            milestoneService.updateRoundIndexOfMilestoneTransactions(round.index());

                            if (!balanceChanges.isEmpty()) {
                                new StateDiffViewModel(balanceChanges, round.index()).store(tangle);
                            }
                        }
                    }
                } finally {
                    snapshotProvider.getLatestSnapshot().unlockRead();
                }

            return successfullyProcessed;
        } catch (Exception e) {
            throw new LedgerException("unexpected error while generating the StateDiff for Round" + round.index(), e);
        }
    }
}
