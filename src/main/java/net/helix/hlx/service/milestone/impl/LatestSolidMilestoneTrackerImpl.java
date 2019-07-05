package net.helix.hlx.service.milestone.impl;

import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.service.ledger.LedgerService;
import net.helix.hlx.service.milestone.LatestMilestoneTracker;
import net.helix.hlx.service.milestone.MilestoneException;
import net.helix.hlx.service.milestone.MilestoneService;
import net.helix.hlx.service.milestone.LatestSolidMilestoneTracker;
import net.helix.hlx.service.milestone.NomineeTracker;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.log.interval.IntervalLogger;
import net.helix.hlx.utils.thread.DedicatedScheduledExecutorService;
import net.helix.hlx.utils.thread.SilentScheduledExecutorService;

import java.util.HashSet;
import java.util.concurrent.TimeUnit;

/**
 * Creates a manager that keeps track of the latest solid milestones and that triggers the application of these
 * milestones and their corresponding balance changes to the latest {@link Snapshot} by incorporating a background
 * worker that periodically checks for new solid milestones.<br />
 * <br />
 * It extends this with a mechanisms to recover from database corruptions by using a backoff strategy that reverts the
 * changes introduced by previous milestones whenever an error is detected until the problem causing milestone was
 * found.<br />
 */
public class LatestSolidMilestoneTrackerImpl implements LatestSolidMilestoneTracker {
    /**
     * Holds the interval (in milliseconds) in which the {@link #trackLatestSolidMilestone()} method gets
     * called by the background worker.<br />
     */
    private static final int RESCAN_INTERVAL = 1000;

    /**
     * Holds the logger of this class (a rate limited logger than doesn't spam the CLI output).<br />
     */
    private static final IntervalLogger log = new IntervalLogger(LatestSolidMilestoneTrackerImpl.class);

    /**
     * Holds the Tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * The snapshot provider which gives us access to the relevant snapshots that the node uses (for the ledger
     * state).<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Holds a reference to the service instance containing the business logic of the milestone package.<br />
     */
    private MilestoneService milestoneService;

    /**
     * Holds a reference to the manager that keeps track of the latest milestone.<br />
     */
    private LatestMilestoneTracker latestMilestoneTracker;

    /**
     * Holds a reference to the service that contains the logic for applying milestones to the ledger state.<br />
     */
    private LedgerService ledgerService;

    private NomineeTracker validatorTracker;

    /**
     * Holds a reference to the manager of the background worker.<br />
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Latest Solid Milestone Tracker", log.delegate());

    /**
     * Boolean flag that is used to identify the first iteration of the background worker.<br />
     */
    private boolean firstRun = true;

    /**
     * Holds the milestone index of the milestone that caused the repair logic to get started.<br />
     */
    private int errorCausingRoundIndex = Integer.MAX_VALUE;

    /**
     * Counter for the backoff repair strategy (see {@link #repairCorruptedRound(RoundViewModel)}.<br />
     */
    private int repairBackoffCounter = 0;

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
     *       {@code latestSolidMilestoneTracker = new LatestSolidMilestoneTrackerImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider manager for the snapshots that allows us to retrieve the relevant snapshots of this node
     * @param milestoneService contains the important business logic when dealing with milestones
     * @param ledgerService the manager for
     * @param latestMilestoneTracker the manager that keeps track of the latest milestone
     * @return the initialized instance itself to allow chaining
     */
    public LatestSolidMilestoneTrackerImpl init(Tangle tangle, SnapshotProvider snapshotProvider,
                                                MilestoneService milestoneService, LedgerService ledgerService,
                                                LatestMilestoneTracker latestMilestoneTracker, NomineeTracker validatorTracker) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
        this.ledgerService = ledgerService;
        this.latestMilestoneTracker = latestMilestoneTracker;
        this.validatorTracker = validatorTracker;

        return this;
    }

    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::latestSolidMilestoneTrackerThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }

    /**
     * {@inheritDoc}
     * <br />
     * In addition to applying the found milestones to the ledger state it also issues log messages and keeps the
     * {@link LatestMilestoneTracker} in sync (if we happen to process a new latest milestone faster).<br />
     */
    @Override
    public void trackLatestSolidMilestone() throws MilestoneException {
        try {
            int currentSolidRoundIndex = snapshotProvider.getLatestSnapshot().getIndex();
            RoundViewModel nextRound;
            while (!Thread.currentThread().isInterrupted() && (currentSolidRoundIndex < latestMilestoneTracker.getCurrentRoundIndex() - 1)) {

                nextRound = RoundViewModel.get(tangle, currentSolidRoundIndex + 1);

                if (nextRound == null) {
                    // round has finished without milestones
                    RoundViewModel latest = RoundViewModel.latest(tangle);
                    if (latest != null && latest.index() > currentSolidRoundIndex + 1) {
                        nextRound = new RoundViewModel(currentSolidRoundIndex + 1, new HashSet<>());
                        nextRound.store(tangle);
                    }
                    // round hasn't finished yet
                    else {
                        break;
                    }
                }


                // check solidity of milestones
                // TODO: How do we handle non solid milestones? Should we only store a milestone if its solid or should we only do snapshot from solid ones?
                // TODO: This solution is definitly wrong, we should continue even there are non solid milestones
                boolean allSolid = true;
                for (Hash milestoneHash : nextRound.getHashes()) {
                    if (!TransactionViewModel.fromHash(tangle, milestoneHash).isSolid()) {
                        allSolid = false;
                    }
                }
                if (allSolid) {
                    //syncValidatorTracker();
                    //syncLatestMilestoneTracker(nextRound.index());
                    applySolidMilestoneToLedger(nextRound);
                    logChange(currentSolidRoundIndex);
                    currentSolidRoundIndex = snapshotProvider.getLatestSnapshot().getIndex();
                }
            }
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while checking for new latest solid milestones", e);
        }
    }

    /**
     * Contains the logic for the background worker.<br />
     * <br />
     * It simply calls {@link #trackLatestSolidMilestone()} and wraps with a log handler that prevents the {@link
     * MilestoneException} to crash the worker.<br />
     */
    private void latestSolidMilestoneTrackerThread() {
        try {
            if (firstRun) {
                firstRun = false;

                ledgerService.restoreLedgerState();
                //latestMilestoneTracker.bootstrapCurrentRoundIndex();
                logChange(snapshotProvider.getInitialSnapshot().getIndex());
            }

            trackLatestSolidMilestone();
        } catch (Exception e) {
            log.error("error while updating the solid milestone", e);
        }
    }

    /**
     * Applies the given milestone to the ledger.<br />
     * <br />
     * If the application of the milestone fails, we start a repair routine which will revert the milestones preceding
     * our current milestone and consequently try to reapply them in the next iteration of the {@link
     * #trackLatestSolidMilestone()} method (until the problem is solved).<br />
     *
     * @param round the milestone that shall be applied to the ledger state
     * @throws Exception if anything unexpected goes wrong while applying the milestone to the ledger
     */
    private void applySolidMilestoneToLedger(RoundViewModel round) throws Exception {
        if (ledgerService.applyMilestoneToLedger(round)) {
            if (isRepairRunning() && isRepairSuccessful(round)) {
                stopRepair();
            }
        } else {
            repairCorruptedRound(round);
        }
    }

    /**
     * Checks if we are currently trying to repair a milestone.<br />
     * <br />
     * We simply use the {@link #repairBackoffCounter} as an indicator if a repair routine is running.<br />
     *
     * @return {@code true} if we are trying to repair a milestone and {@code false} otherwise
     */
    private boolean isRepairRunning() {
        return repairBackoffCounter != 0;
    }

    /**
     * Checks if we successfully repaired the corrupted milestone.<br />
     * <br />
     * To determine if the repair routine was successful we check if the processed milestone has a higher index than the
     * one that initially could not get applied to the ledger.<br />
     *
     * @param processedRound the currently processed milestone
     * @return {@code true} if we advanced to a milestone following the corrupted one and {@code false} otherwise
     */
    private boolean isRepairSuccessful(RoundViewModel processedRound) {
        return processedRound.index() > errorCausingRoundIndex;
    }

    /**
     * Resets the internal variables that are used to keep track of the repair process.<br />
     * <br />
     * It gets called whenever we advance to a milestone that has a higher milestone index than the milestone that
     * initially caused the repair routine to kick in (see {@link #repairCorruptedRound(RoundViewModel)}.<br />
     */
    private void stopRepair() {
        repairBackoffCounter = 0;
        errorCausingRoundIndex = Integer.MAX_VALUE;
    }

    /**
     * Keeps the {@link LatestMilestoneTracker} in sync with our current progress.<br />
     * <br />
     * Since the {@link LatestMilestoneTracker} scans all old milestones during its startup (which can take a while to
     * finish) it can happen that we see a newer latest milestone faster than this manager.<br />
     * <br />
     * Note: This method ensures that the latest milestone index is always bigger or equals the latest solid milestone
     *       index.
     *
     * @param roundIndex milestone index
     */
    private void syncLatestMilestoneTracker(int roundIndex) {
        if(roundIndex > latestMilestoneTracker.getCurrentRoundIndex()) {
            latestMilestoneTracker.setCurrentRoundIndex(roundIndex);
        }
    }

    private void syncNomineeTracker() {
        latestMilestoneTracker.setCurrentNominees(validatorTracker.getLatestNominees());
    }

    /**
     * Emits a log message whenever the latest solid milestone changes.<br />
     * <br />
     * It simply compares the current latest milestone index against the previous milestone index and emits the log
     * messages using the {@link #log} and the {@link #messageQ} instances if it differs.<br />
     *
     * @param prevSolidRoundIndex the milestone index before the change
     */
    private void logChange(int prevSolidRoundIndex) {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
        int latestRoundIndex = latestSnapshot.getIndex();
        Hash latestMilestoneHash = latestSnapshot.getHash();

        if (prevSolidRoundIndex != latestRoundIndex) {
            log.info("Round #" + latestRoundIndex + " is SOLID");

            tangle.publish("lmsi %d %d", prevSolidRoundIndex, latestRoundIndex);
            tangle.publish("lmhs %s", latestMilestoneHash.hexString());
        }
    }

    /**
     * Tries to actively repair the ledger by reverting the milestones preceding the given milestone.<br />
     * <br />
     * It gets called when a milestone could not be applied to the ledger state because of problems like "inconsistent
     * balances". While this should theoretically never happen (because milestones are by definition "consistent"), it
     * can still happen because the node crashed or got stopped in the middle of applying a milestone or if a milestone
     * was processed in the wrong order.<br />
     * <br />
     * Every time we call this method the internal {@link #repairBackoffCounter} is incremented which causes the next
     * call of this method to repair an additional milestone. This means that whenever we face an error we first try to
     * reset only the last milestone, then the two last milestones, then the three last milestones (and so on ...) until
     * the problem was fixed.<br />
     * <br />
     * To be able to tell when the problem is fixed and the {@link #repairBackoffCounter} can be reset, we store the
     * milestone index that caused the problem the first time we call this method.<br />
     *
     * @param errorCausingRound the milestone that failed to be applied
     * @throws MilestoneException if we failed to reset the corrupted milestone
     */
    private void repairCorruptedRound(RoundViewModel errorCausingRound) throws MilestoneException {
        if(repairBackoffCounter++ == 0) {
            errorCausingRoundIndex = errorCausingRound.index();
        }
        for (int i = errorCausingRound.index(); i > errorCausingRound.index() - repairBackoffCounter; i--) {
            milestoneService.resetCorruptedRound(i);
        }
    }
}
