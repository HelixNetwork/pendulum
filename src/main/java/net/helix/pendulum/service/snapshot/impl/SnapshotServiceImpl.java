package net.helix.pendulum.service.snapshot.impl;

import net.helix.pendulum.conf.BasePendulumConfig;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.ApproveeViewModel;
import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.StateDiffViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.service.milestone.MilestoneTracker;
import net.helix.pendulum.service.snapshot.*;
import net.helix.pendulum.service.spentaddresses.SpentAddressesProvider;
import net.helix.pendulum.service.spentaddresses.SpentAddressesService;
import net.helix.pendulum.service.transactionpruning.TransactionPruner;
import net.helix.pendulum.service.transactionpruning.TransactionPruningException;
import net.helix.pendulum.service.transactionpruning.jobs.MilestonePrunerJob;
import net.helix.pendulum.service.transactionpruning.jobs.UnconfirmedSubtanglePrunerJob;
import net.helix.pendulum.service.utils.RoundIndexUtil;
import net.helix.pendulum.storage.Tangle;
import net.helix.pendulum.utils.dag.DAGHelper;
import net.helix.pendulum.utils.dag.TraversalException;
import net.helix.pendulum.utils.log.ProgressLogger;
import net.helix.pendulum.utils.log.interval.IntervalProgressLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Creates a service instance that allows us to access the business logic for {@link Snapshot}s.<br />
 * <br />
 * The service instance is stateless and can be shared by multiple other consumers.<br />
 */
public class SnapshotServiceImpl implements SnapshotService {
    /**
     * Logger for this class allowing us to dump debug and status messages.
     */
    private static final Logger log = LoggerFactory.getLogger(SnapshotServiceImpl.class);

    /**
     * Holds a limit for the amount of milestones we go back in time when generating the solid entry points (to speed up
     * the snapshot creation).<br />
     * <br />
     * Note: Since the snapshot creation is a "continuous" process where we build upon the information gathered during
     *       the creation of previous snapshots, we do not need to analyze all previous milestones but can rely on
     *       slowly gathering the missing information over time. While this might lead to a situation where the very
     *       first snapshots taken by a node might generate snapshot files that can not reliably be used by other nodes
     *       to sync it is still a reasonable trade-off to reduce the load on the nodes. We just assume that anybody who
     *       wants to share his snapshots with the community as a way to bootstrap new nodes will run his snapshot
     *       enabled node for a few hours before sharing his files (this is a problem in very rare edge cases when
     *       having back-referencing transactions anyway).<br />
     */
    private static final int OUTER_SHELL_SIZE = 100;

    /**
     * Maximum age in milestones since creation of solid entry points.
     *
     * Since it is possible to artificially keep old solid entry points alive by periodically attaching new transactions
     * to them, we limit the life time of solid entry points and ignore them whenever they become too old. This is a
     * measure against a potential attack vector where somebody might try to blow up the meta data of local snapshots.
     */
    private static final int SOLID_ENTRY_POINT_LIFETIME = 1000;

    /**
     * Holds the tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * Holds the snapshot provider which gives us access to the relevant snapshots.<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds the config with important snapshot specific settings.<br />
     */
    private PendulumConfig config;

    private SpentAddressesService spentAddressesService;

    private SpentAddressesProvider spentAddressesProvider;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code snapshotService = new SnapshotServiceImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the snapshots that are relevant for the node
     * @param config important snapshot related configuration parameters
     * @return the initialized instance itself to allow chaining
     */
    public SnapshotServiceImpl init(Tangle tangle, SnapshotProvider snapshotProvider,
                                    SpentAddressesService spentAddressesService, SpentAddressesProvider spentAddressesProvider,
                                    PendulumConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.spentAddressesService = spentAddressesService;
        this.spentAddressesProvider = spentAddressesProvider;
        this.config = config;

        return this;
    }

    /**
     * {@inheritDoc}
     * <br />
     * To increase the performance of this operation, we do not apply every single milestone separately but first
     * accumulate all the necessary changes and then apply it to the snapshot in a single run. This allows us to
     * modify its values without having to create a "copy" of the initial state to possibly roll back the changes if
     * anything unexpected happens (creating a backup of the state requires a lot of memory).<br />
     */
    @Override
    public void replayMilestones(Snapshot snapshot, int targetRoundIndex) throws SnapshotException {
        Map<Hash, Long> balanceChanges = new HashMap<>();
        Set<Integer> skippedMilestones = new HashSet<>();
        RoundViewModel lastAppliedRound = null;

        try {
            for (int currentRoundIndex = snapshot.getIndex() + 1; currentRoundIndex <= targetRoundIndex;
                 currentRoundIndex++) {

                RoundViewModel currentRound = RoundViewModel.get(tangle, currentRoundIndex);
                if (currentRound != null) {
                    StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, currentRoundIndex);
                    if(!stateDiffViewModel.isEmpty()) {
                        stateDiffViewModel.getDiff().forEach((address, change) -> {
                            balanceChanges.compute(address, (k, balance) -> (balance == null ? 0 : balance) + change);
                        });
                    }

                    lastAppliedRound = currentRound;
                } else {
                    skippedMilestones.add(currentRoundIndex);
                }
            }

            if (lastAppliedRound != null) {
                try {
                    snapshot.lockWrite();

                    snapshot.applyStateDiff(new SnapshotStateDiffImpl(balanceChanges));

                    snapshot.setIndex(lastAppliedRound.index());

                    //store merkle root
                    snapshot.setHash(lastAppliedRound.getMerkleRoot());

                    // todo: this is only a temporary fix to circumvent empty rounds serving as solidification end-points (#184). Empty round's snapshot hashes should be unique and this should be handled in the merkle-root generation.
                    if (snapshot.getHash().equals(Hash.NULL_HASH)) {
                        snapshot.setHash(BasePendulumConfig.Defaults.EMPTY_ROUND_HASH);
                    }

                    // only log when applying new rounds
                    if(!(lastAppliedRound.index() + 1 < getRound(System.currentTimeMillis()))) {
                        log.debug("Applying round #{}, snapshot hash: {} to ledger", lastAppliedRound.index(), snapshot.getHash());
                    }

                    // start time of round
                    snapshot.setTimestamp(config.getGenesisTime() + (lastAppliedRound.index() * config.getRoundDuration()));


                    for (int skippedMilestoneIndex : skippedMilestones) {
                        snapshot.addSkippedMilestone(skippedMilestoneIndex);
                    }
                } finally {
                    snapshot.unlockWrite();
                }
            }
        } catch (Exception e) {
            throw new SnapshotException("failed to replay the state of the ledger", e);
        }
    }

    // todo: unfortunately we need to have getRound in milestoneTracker and here, as RoundIndexUtils is static and we need to check isTestnet.
    public int getRound(long time) {
        return config.isTestnet() ?
                RoundIndexUtil.getRound(time, BasePendulumConfig.Defaults.GENESIS_TIME_TESTNET, BasePendulumConfig.Defaults.ROUND_DURATION) :
                RoundIndexUtil.getRound(time, BasePendulumConfig.Defaults.GENESIS_TIME, BasePendulumConfig.Defaults.ROUND_DURATION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void rollBackMilestones(Snapshot snapshot, int targetMilestoneIndex) throws SnapshotException {
        if(targetMilestoneIndex <= snapshot.getInitialIndex() || targetMilestoneIndex > snapshot.getIndex()) {
            throw new SnapshotException("invalid milestone index");
        }

        snapshot.lockWrite();

        Snapshot snapshotBeforeChanges = snapshot.clone();

        try {
            boolean rollbackSuccessful = true;
            while (targetMilestoneIndex <= snapshot.getIndex() && rollbackSuccessful) {
                rollbackSuccessful = rollbackLastMilestone(tangle, snapshot);
            }

            if(targetMilestoneIndex < snapshot.getIndex()) {
                throw new SnapshotException("failed to reach the target milestone index when rolling back milestones");
            }
        } catch(SnapshotException e) {
            snapshot.update(snapshotBeforeChanges);

            throw e;
        } finally {
            snapshot.unlockWrite();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void takeLocalSnapshot(MilestoneTracker milestoneTracker, TransactionPruner transactionPruner)
            throws SnapshotException {

        RoundViewModel targetMilestone = determineMilestoneForLocalSnapshot(tangle, snapshotProvider, config);

        Snapshot newSnapshot = generateSnapshot(milestoneTracker, targetMilestone);

        if (transactionPruner != null) {
            cleanupExpiredSolidEntryPoints(tangle, snapshotProvider.getInitialSnapshot().getSolidEntryPoints(),
                    newSnapshot.getSolidEntryPoints(), transactionPruner);

            cleanupOldData(config, transactionPruner, targetMilestone);
        }

        persistLocalSnapshot(snapshotProvider, newSnapshot, config);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Snapshot generateSnapshot(MilestoneTracker milestoneTracker, RoundViewModel targetRound)
            throws SnapshotException {

        if (targetRound == null) {
            throw new SnapshotException("the target milestone must not be null");
        } else if (targetRound.index() > snapshotProvider.getLatestSnapshot().getIndex()) {
            throw new SnapshotException("the snapshot target " + targetRound + " was not solidified yet");
        } else if (targetRound.index() < snapshotProvider.getInitialSnapshot().getIndex()) {
            throw new SnapshotException("the snapshot target " + targetRound + " is too old");
        }

        snapshotProvider.getInitialSnapshot().lockRead();
        snapshotProvider.getLatestSnapshot().lockRead();

        Snapshot snapshot;
        try {
            int distanceFromInitialSnapshot = Math.abs(snapshotProvider.getInitialSnapshot().getIndex() -
                    targetRound.index());
            int distanceFromLatestSnapshot = Math.abs(snapshotProvider.getLatestSnapshot().getIndex() -
                    targetRound.index());

            if (distanceFromInitialSnapshot <= distanceFromLatestSnapshot) {
                snapshot = snapshotProvider.getInitialSnapshot().clone();

                replayMilestones(snapshot, targetRound.index());
            } else {
                snapshot = snapshotProvider.getLatestSnapshot().clone();

                rollBackMilestones(snapshot, targetRound.index() + 1);
            }
        } finally {
            snapshotProvider.getInitialSnapshot().unlockRead();
            snapshotProvider.getLatestSnapshot().unlockRead();
        }

        snapshot.setSolidEntryPoints(generateSolidEntryPoints(targetRound));
        snapshot.setSeenRounds(generateSeenRounds(milestoneTracker, targetRound));

        return snapshot;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Hash, Integer> generateSolidEntryPoints(RoundViewModel targetMilestone) throws SnapshotException {
        Map<Hash, Integer> solidEntryPoints = new HashMap<>();
        solidEntryPoints.put(Hash.NULL_HASH, targetMilestone.index());

        processOldSolidEntryPoints(tangle, snapshotProvider, targetMilestone, solidEntryPoints);
        processNewSolidEntryPoints(tangle, snapshotProvider, targetMilestone, solidEntryPoints);

        return solidEntryPoints;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Map<Integer, Hash> generateSeenRounds(MilestoneTracker milestoneTracker,
                                                     RoundViewModel targetRound) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [processing seen milestones]", log)
                .start(config.getLocalSnapshotsDepth());

        Map<Integer, Hash> seenRounds = new HashMap<>();
        try {
            RoundViewModel seenRound = targetRound;
            while ((seenRound = RoundViewModel.findClosestNextRound(tangle, seenRound.index(),
                    milestoneTracker.getCurrentRoundIndex())) != null) {

                seenRounds.put(seenRound.index(), seenRound.getMerkleRoot());

                progressLogger.progress();
            }
        } catch (Exception e) {
            progressLogger.abort(e);

            throw new SnapshotException("could not generate the set of seen milestones", e);
        }

        progressLogger.finish();

        return seenRounds;
    }

    /**
     * This method reverts the changes caused by the last milestone that was applied to this snapshot.
     *
     * It first checks if we didn't arrive at the initial index yet and then reverts the balance changes that were
     * caused by the last milestone. Then it checks if any milestones were skipped while applying the last milestone and
     * determines the {@link SnapshotMetaData} that this Snapshot had before and restores it.
     *
     * @param tangle Tangle object which acts as a database interface
     * @return true if the snapshot was rolled back or false otherwise
     * @throws SnapshotException if anything goes wrong while accessing the database
     */
    private boolean rollbackLastMilestone(Tangle tangle, Snapshot snapshot) throws SnapshotException {
        if (snapshot.getIndex() == snapshot.getInitialIndex()) {
            return false;
        }

        snapshot.lockWrite();

        try {
            // revert the last balance changes
            StateDiffViewModel stateDiffViewModel = StateDiffViewModel.load(tangle, snapshot.getIndex());
            if (!stateDiffViewModel.isEmpty()) {
                SnapshotStateDiffImpl snapshotStateDiff = new SnapshotStateDiffImpl(
                        stateDiffViewModel.getDiff().entrySet().stream().map(
                                hashLongEntry -> new HashMap.SimpleEntry<>(
                                        hashLongEntry.getKey(), -1 * hashLongEntry.getValue()
                                )
                        ).collect(
                                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)
                        )
                );

                if (!snapshotStateDiff.isConsistent()) {
                    throw new SnapshotException("the StateDiff belonging to milestone #" + snapshot.getIndex() +
                            " (" + snapshot.getHash() + ") is inconsistent");
                } else if (!snapshot.patchedState(snapshotStateDiff).isConsistent()) {
                    throw new SnapshotException("failed to apply patch belonging to milestone #" + snapshot.getIndex() +
                            " (" + snapshot.getHash() + ")");
                }

                snapshot.applyStateDiff(snapshotStateDiff);
            }

            // jump skipped milestones
            int currentIndex = snapshot.getIndex() - 1;
            while (snapshot.removeSkippedMilestone(currentIndex)) {
                currentIndex--;
            }

            // check if we arrived at the start
            if (currentIndex <= snapshot.getInitialIndex()) {
                snapshot.setIndex(snapshot.getInitialIndex());
                snapshot.setHash(snapshot.getInitialHash());
                snapshot.setTimestamp(snapshot.getInitialTimestamp());

                return true;
            }

            // otherwise set metadata of the previous milestone
            RoundViewModel currentMilestone = RoundViewModel.get(tangle, currentIndex);
            snapshot.setIndex(currentMilestone.index());

            //TODO: see above
            //snapshot.setHash(currentMilestone.getHash());
            //snapshot.setTimestamp(TransactionViewModel.fromHash(tangle, currentMilestone.getHash()).getTimestamp());

            return true;
        } catch (Exception e) {
            throw new SnapshotException("failed to rollback last milestone", e);
        } finally {
            snapshot.unlockWrite();
        }
    }

    /**
     * This method determines the milestone that shall be used for the local snapshot.
     *
     * It determines the milestone by subtracting the {@link PendulumConfig#getLocalSnapshotsDepth()} from the latest
     * solid milestone index and retrieving the next milestone before this point.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param config important snapshot related configuration parameters
     * @return the target milestone for the local snapshot
     * @throws SnapshotException if anything goes wrong while determining the target milestone for the local snapshot
     */
    private RoundViewModel determineMilestoneForLocalSnapshot(Tangle tangle, SnapshotProvider snapshotProvider,
                                                              PendulumConfig config) throws SnapshotException {

        int targetMilestoneIndex = snapshotProvider.getLatestSnapshot().getIndex() - config.getLocalSnapshotsDepth();

        RoundViewModel targetMilestone;
        try {
            targetMilestone = RoundViewModel.findClosestPrevRound(tangle, targetMilestoneIndex,
                    snapshotProvider.getInitialSnapshot().getIndex());
        } catch (Exception e) {
            throw new SnapshotException("could not load the target milestone", e);
        }
        if (targetMilestone == null) {
            throw new SnapshotException("missing milestone with an index of " + targetMilestoneIndex + " or lower");
        }

        return targetMilestone;
    }

    /**
     * This method creates {@link net.helix.pendulum.service.transactionpruning.TransactionPrunerJob}s for the expired solid
     * entry points, which removes the unconfirmed subtangles branching off of these transactions.
     *
     * We only clean up these subtangles if the transaction that they are branching off has been cleaned up already by a
     * {@link MilestonePrunerJob}. If the corresponding milestone has not been processed we leave them in the database
     * so we give the node a little bit more time to "use" these transaction for references from future milestones. This
     * is used to correctly reflect the {@link PendulumConfig#getLocalSnapshotsPruningDelay()}, where we keep old data
     * prior to a snapshot.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param oldSolidEntryPoints solid entry points of the current initial {@link Snapshot}
     * @param newSolidEntryPoints solid entry points of the new initial {@link Snapshot}
     * @param transactionPruner manager for the pruning jobs that takes care of cleaning up the old data that
     */
    private void cleanupExpiredSolidEntryPoints(Tangle tangle, Map<Hash, Integer> oldSolidEntryPoints,
                                                Map<Hash, Integer> newSolidEntryPoints, TransactionPruner transactionPruner) {

        oldSolidEntryPoints.forEach((transactionHash, milestoneIndex) -> {
            if (!newSolidEntryPoints.containsKey(transactionHash)) {
                try {
                    // only clean up if the corresponding milestone transaction was cleaned up already -> otherwise
                    // let the MilestonePrunerJob do this
                    if (TransactionViewModel.fromHash(tangle, transactionHash).getType() ==
                            TransactionViewModel.PREFILLED_SLOT) {

                        transactionPruner.addJob(new UnconfirmedSubtanglePrunerJob(transactionHash));
                    }
                } catch (Exception e) {
                    log.error("failed to add cleanup job to the transaction pruner", e);
                }
            }
        });
    }

    /**
     * This method creates the {@link net.helix.pendulum.service.transactionpruning.TransactionPrunerJob}s that are
     * responsible for removing the old data.
     *
     * It first calculates the range of milestones that shall be deleted and then issues a {@link MilestonePrunerJob}
     * for this range (if it is not empty).
     *
     * @param config important snapshot related configuration parameters
     * @param transactionPruner  manager for the pruning jobs that takes care of cleaning up the old data that
     * @param targetMilestone milestone that was used as a reference point for the local snapshot
     * @throws SnapshotException if anything goes wrong while issuing the cleanup jobs
     */
    private void cleanupOldData(PendulumConfig config, TransactionPruner transactionPruner,
                                RoundViewModel targetMilestone) throws SnapshotException {

        int targetIndex = targetMilestone.index() - config.getLocalSnapshotsPruningDelay();
        int startingIndex = config.getMilestoneStartIndex() + 1;

        try {
            if (targetIndex >= startingIndex) {
                transactionPruner.addJob(new MilestonePrunerJob(startingIndex, targetIndex));
            }
        } catch (TransactionPruningException e) {
            throw new SnapshotException("could not add the cleanup job to the transaction pruner", e);
        }
    }

    /**
     * This method persists the local snapshot on the disk and updates the instances used by the
     * {@link SnapshotProvider}.
     *
     * It first writes the files to the disk and then updates the two {@link Snapshot}s accordingly.
     *
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param newSnapshot Snapshot that shall be persisted
     * @param config important snapshot related configuration parameters
     * @throws SnapshotException if anything goes wrong while persisting the snapshot
     */
    private void persistLocalSnapshot(SnapshotProvider snapshotProvider, Snapshot newSnapshot, PendulumConfig config)
            throws SnapshotException {

        try {
            spentAddressesService.persistSpentAddresses(snapshotProvider.getInitialSnapshot().getIndex(),
                    newSnapshot.getIndex());

        } catch (Exception e) {
            throw new SnapshotException(e);
        }

        snapshotProvider.writeSnapshotToDisk(newSnapshot, config.getLocalSnapshotsBasePath());

        snapshotProvider.getLatestSnapshot().lockWrite();
        snapshotProvider.getLatestSnapshot().setInitialHash(newSnapshot.getHash());
        snapshotProvider.getLatestSnapshot().setInitialIndex(newSnapshot.getIndex());
        snapshotProvider.getLatestSnapshot().setInitialTimestamp(newSnapshot.getTimestamp());
        snapshotProvider.getLatestSnapshot().unlockWrite();

        snapshotProvider.getInitialSnapshot().update(newSnapshot);
    }

    /**
     * This method determines if a transaction is orphaned.
     *
     * Since there is no hard definition for when a transaction can be considered to be orphaned, we define orphaned in
     * relation to a referenceTransaction. If the transaction or any of its direct or indirect approvers saw a
     * transaction being attached to it, that arrived after our reference transaction, we consider it "not orphaned".
     *
     * Since we currently use milestones as reference transactions that are sufficiently old, this definition in fact is
     * a relatively safe way to determine if a subtangle "above" a transaction got orphaned.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param transaction transaction that shall be checked
     * @param referenceTransaction transaction that acts as a judge to the other transaction
     * @param processedTransactions transactions that were visited already while trying to determine the orphaned status
     * @return true if the transaction got orphaned and false otherwise
     * @throws SnapshotException if anything goes wrong while determining the orphaned status
     */
    private boolean isOrphaned(Tangle tangle, TransactionViewModel transaction,
                               TransactionViewModel referenceTransaction, Set<Hash> processedTransactions) throws SnapshotException {

        long arrivalTime = transaction.getArrivalTime() / 1000L;
        if (arrivalTime > referenceTransaction.getTimestamp()) {
            return false;
        }

        AtomicBoolean nonOrphanedTransactionFound = new AtomicBoolean(false);
        try {
            DAGHelper.get(tangle).traverseApprovers(
                    transaction.getHash(),
                    currentTransaction -> !nonOrphanedTransactionFound.get(),
                    currentTransaction -> {
                        if (arrivalTime > referenceTransaction.getTimestamp()) {
                            nonOrphanedTransactionFound.set(true);
                        }
                    },
                    processedTransactions
            );
        } catch (TraversalException e) {
            throw new SnapshotException("failed to determine orphaned status of " + transaction, e);
        }

        return !nonOrphanedTransactionFound.get();
    }

    /**
     * This method checks if a transaction is a solid entry point for the targetMilestone.
     *
     * A transaction is considered a solid entry point if it has non-orphaned approvers.
     *
     * To check if the transaction has non-orphaned approvers we first check if any of its approvers got confirmed by a
     * future milestone, since this is very cheap. If none of them got confirmed by another milestone we do the more
     * expensive check from {@link #isOrphaned(Tangle, TransactionViewModel, TransactionViewModel, Set)}.
     *
     * Since solid entry points have a limited life time and to prevent potential problems due to temporary errors in
     * the database, we assume that the checked transaction is a solid entry point if any error occurs while determining
     * its status. This is a storage <=> reliability trade off, since the only bad effect of having too many solid entry
     * points) is a bigger snapshot file.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param transactionHash hash of the transaction that shall be checked
     * @param targetRound milestone that is used as an anchor for our checks
     * @return true if the transaction is a solid entry point and false otherwise
     */
    private boolean isSolidEntryPoint(Tangle tangle, Hash transactionHash, RoundViewModel targetRound) {
        Set<TransactionViewModel> unconfirmedApprovers = new HashSet<>();

        try {
            for (Hash approverHash : ApproveeViewModel.load(tangle, transactionHash).getHashes()) {
                TransactionViewModel approver = TransactionViewModel.fromHash(tangle, approverHash);

                if (approver.snapshotIndex() > targetRound.index()) {
                    return true;
                } else if (approver.snapshotIndex() == 0) {
                    unconfirmedApprovers.add(approver);
                }
            }

            Set<Hash> processedTransactions = new HashSet<>();
            for (TransactionViewModel unconfirmedApprover : unconfirmedApprovers) {
                // if one of the unconfirmed approvers isn't orphaned from the perspective of one of the confirmed tips, the transaction is a solid entry point
                for (Hash milestoneHash : targetRound.getConfirmedTips(tangle, config.getValidatorSecurity())) {
                    TransactionViewModel milestoneTransaction = TransactionViewModel.fromHash(tangle, milestoneHash);
                    if (!isOrphaned(tangle, unconfirmedApprover, milestoneTransaction, processedTransactions)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            log.error("failed to determine the solid entry point status for transaction " + transactionHash, e);

            return true;
        }

        return false;
    }

    /**
     * This method analyzes the old solid entry points and determines if they are still not orphaned.
     *
     * It simply iterates through the old solid entry points and checks them one by one. If an old solid entry point
     * is found to still be relevant it is added to the passed in map.
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param targetMilestone milestone that is used to generate the solid entry points
     * @param solidEntryPoints map that is used to collect the solid entry points
     */
    private void processOldSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
                                            RoundViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [analyzing old solid entry points]", log)
                .start(snapshotProvider.getInitialSnapshot().getSolidEntryPoints().size());

        Snapshot initialSnapshot = snapshotProvider.getInitialSnapshot();
        initialSnapshot.getSolidEntryPoints().forEach((hash, milestoneIndex) -> {
            if (!Hash.NULL_HASH.equals(hash) && targetMilestone.index() - milestoneIndex <= SOLID_ENTRY_POINT_LIFETIME
                    && isSolidEntryPoint(tangle, hash, targetMilestone)) {

                solidEntryPoints.put(hash, milestoneIndex);
            }

            progressLogger.progress();
        });

        progressLogger.finish();
    }

    /**
     * This method retrieves the new solid entry points of the snapshot reference given by the target milestone.
     *
     * It iterates over all unprocessed milestones and analyzes their directly and indirectly approved transactions.
     * Every transaction is checked for being a solid entry point and added to the passed in map (if it was found to be
     * one).
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider data provider for the {@link Snapshot}s that are relevant for the node
     * @param targetMilestone milestone that is used to generate the solid entry points
     * @param solidEntryPoints map that is used to collect the solid entry points
     * @throws SnapshotException if anything goes wrong while determining the solid entry points
     */
    private void processNewSolidEntryPoints(Tangle tangle, SnapshotProvider snapshotProvider,
                                            RoundViewModel targetMilestone, Map<Hash, Integer> solidEntryPoints) throws SnapshotException {

        ProgressLogger progressLogger = new IntervalProgressLogger(
                "Taking local snapshot [generating solid entry points]", log);

        try {
            progressLogger.start(Math.min(targetMilestone.index() - snapshotProvider.getInitialSnapshot().getIndex(),
                    OUTER_SHELL_SIZE));

            RoundViewModel nextMilestone = targetMilestone;
            while (nextMilestone != null && nextMilestone.index() > snapshotProvider.getInitialSnapshot().getIndex() &&
                    progressLogger.getCurrentStep() < progressLogger.getStepCount()) {

                RoundViewModel currentMilestone = nextMilestone;
                for (Hash confirmedTip : currentMilestone.getConfirmedTips(tangle, config.getValidatorSecurity())) {
                    DAGHelper.get(tangle).traverseApprovees(
                            confirmedTip,
                            currentTransaction -> currentTransaction.snapshotIndex() >= currentMilestone.index(),
                            currentTransaction -> {
                                if (isSolidEntryPoint(tangle, currentTransaction.getHash(), targetMilestone)) {
                                    solidEntryPoints.put(currentTransaction.getHash(), targetMilestone.index());
                                }
                            }
                    );
                    solidEntryPoints.put(confirmedTip, targetMilestone.index());
                }

                nextMilestone = RoundViewModel.findClosestPrevRound(tangle, currentMilestone.index(),
                        snapshotProvider.getInitialSnapshot().getIndex());

                progressLogger.progress();
            }

            progressLogger.finish();
        } catch (Exception e) {
            progressLogger.abort(e);

            throw new SnapshotException("could not generate the solid entry points for " + targetMilestone, e);
        }
    }
}
