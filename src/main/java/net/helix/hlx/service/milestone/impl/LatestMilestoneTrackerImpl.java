package net.helix.hlx.service.milestone.impl;

import net.helix.hlx.conf.HelixConfig;
import net.helix.hlx.controllers.AddressViewModel;
import net.helix.hlx.controllers.RoundViewModel;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.model.persistables.Round;
import net.helix.hlx.service.milestone.*;
import net.helix.hlx.service.snapshot.Snapshot;
import net.helix.hlx.service.snapshot.SnapshotProvider;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.log.interval.IntervalLogger;
import net.helix.hlx.utils.thread.DedicatedScheduledExecutorService;
import net.helix.hlx.utils.thread.SilentScheduledExecutorService;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * Creates a tracker that automatically detects new milestones by incorporating a background worker that periodically
 * checks all transactions that are originating from the coordinator address and that exposes the found latest milestone
 * via getters.<br />
 * <br />
 * It can be used to determine the sync-status of the node by comparing these values against the latest solid
 * milestone.<br />
 */
public class LatestMilestoneTrackerImpl implements LatestMilestoneTracker {
    /**
     * Holds the amount of milestone candidates that will be analyzed per iteration of the background worker.<br />
     */
    private static final int MAX_CANDIDATES_TO_ANALYZE = 5000;

    /**
     * Holds the time (in milliseconds) between iterations of the background worker.<br />
     */
    private static final int RESCAN_INTERVAL = 1000;


    /**
     * Holds the logger of this class (a rate limited logger that doesn't spam the CLI output).<br />
     */
    private static final IntervalLogger log = new IntervalLogger(LatestMilestoneTrackerImpl.class);

    /**
     * Holds the Tangle object which acts as a database interface.<br />
     */
    private Tangle tangle;

    /**
     * The snapshot provider which gives us access to the relevant snapshots that the node uses (for faster
     * bootstrapping).<br />
     */
    private SnapshotProvider snapshotProvider;

    /**
     * Service class containing the business logic of the milestone package.<br />
     */
    private MilestoneService milestoneService;

    /**
     * Holds a reference to the manager that takes care of solidifying milestones.<br />
     */
    private MilestoneSolidifier milestoneSolidifier;

    /**
     * Holds a reference to the manager that tracks nominees.<br />
     */
    private NomineeTracker nomineeTracker;

    /**
     * Holds the addresses which are used to filter possible milestone candidates.<br />
     */
    private Set<Hash> currentNominees;

    private Set<Hash> allNominees;

    /**
     * Holds a reference to the manager of the background worker.<br />
     */
    private final SilentScheduledExecutorService executorService = new DedicatedScheduledExecutorService(
            "Latest Milestone Tracker", log.delegate());


    private long genesisTime;

    private int roundDuration;

    /**
     * Holds the round index of the latest round that we have seen / processed.<br />
     */
    private int currentRoundIndex;

    /**
     * Holds the transaction hashes of the latest round that we have seen / processed.<br />
     */
    private Set<Hash> latestRoundHashes = new HashSet<>();

    /**
     * A set that allows us to keep track of the candidates that have been seen and added to the {@link
     * #milestoneCandidatesToAnalyze} already.<br />
     */
    private final Set<Hash> seenMilestoneCandidates = new HashSet<>();

    /**
     * A list of milestones that still have to be analyzed.<br />
     */
    private final Deque<Hash> milestoneCandidatesToAnalyze = new ArrayDeque<>();

    /**
     * A flag that allows us to detect if the background worker is in its first iteration (for different log
     * handling).<br />
     */
    private boolean firstRun = true;

    /**
     * Flag which indicates if this tracker has finished its initial scan of all old milestone candidates.<br />
     */
    private boolean initialized = false;

    /**
     * This method initializes the instance and registers its dependencies.<br />
     * <br />
     * It simply stores the passed in values in their corresponding private properties and bootstraps the latest
     * milestone with values for the latest milestone that can be found quickly.<br />
     * <br />
     * Note: Instead of handing over the dependencies in the constructor, we register them lazy. This allows us to have
     *       circular dependencies because the instantiation is separated from the dependency injection. To reduce the
     *       amount of code that is necessary to correctly instantiate this class, we return the instance itself which
     *       allows us to still instantiate, initialize and assign in one line - see Example:<br />
     *       <br />
     *       {@code latestMilestoneTracker = new LatestMilestoneTrackerImpl().init(...);}
     *
     * @param tangle Tangle object which acts as a database interface
     * @param snapshotProvider manager for the snapshots that allows us to retrieve the relevant snapshots of this node
     * @param milestoneService contains the important business logic when dealing with milestones
     * @param milestoneSolidifier manager that takes care of solidifying milestones
     * @param config configuration object which allows us to determine the important config parameters of the node
     * @return the initialized instance itself to allow chaining
     */
    public LatestMilestoneTrackerImpl init(Tangle tangle, SnapshotProvider snapshotProvider,
                                           MilestoneService milestoneService, MilestoneSolidifier milestoneSolidifier, NomineeTracker nomineeTracker, HelixConfig config) {

        this.tangle = tangle;
        this.snapshotProvider = snapshotProvider;
        this.milestoneService = milestoneService;
        this.milestoneSolidifier = milestoneSolidifier;
        this.nomineeTracker = nomineeTracker;

        allNominees = new HashSet<>();
        allNominees.addAll(nomineeTracker.getLatestNominees());

        System.out.println("Current Time: " + System.currentTimeMillis());
        System.out.println("Genesis Time: " + config.getGenesisTime());
        System.out.println("Round Duration: " + config.getRoundDuration());
        genesisTime = config.getGenesisTime();
        roundDuration = config.getRoundDuration();

        setCurrentNominees(nomineeTracker.getLatestNominees());

        //bootstrapLatestRoundIndex();

        return this;
    }

    /**
     * {@inheritDoc}
     * <br />
     * In addition to setting the internal properties, we also issue a log message and publish the change to the ZeroMQ
     * message processor so external receivers get informed about this change.<br />
     */
    @Override
    public void addMilestoneToRoundLog(Hash milestoneHash, int roundIndex, int numberOfMilestones, int numberOfNominees) {
        tangle.publish("lmi %s %d", milestoneHash.hexString(), roundIndex);
        log.delegate().info("New milestone {} ({}/{}) added to round #{}", milestoneHash.hexString(), numberOfMilestones, numberOfNominees, roundIndex);

    }
    @Override
    public void setCurrentRoundIndex(int roundIndex) {
        tangle.publish("lmi %d %d", this.currentRoundIndex, roundIndex);
        log.delegate().info("Latest round has changed from #{} to #{}", this.currentRoundIndex, roundIndex);
        this.currentRoundIndex = roundIndex;
    }

    @Override
    public void setCurrentNominees(Set<Hash> nominees) {
        //tangle.publish("lv %d %d", getCurrentRoundIndex(), nominees);
        log.delegate().info("Validators of round #{}: {}", getCurrentRoundIndex(), nominees);
        this.currentNominees = nominees;
    }

    @Override
    public int getCurrentRoundIndex() {
        return getRound(System.currentTimeMillis());
    }

    public int getRound(long time) {
        return (int) (time - genesisTime) / roundDuration;
    }

    @Override
    public Set<Hash> getLatestRoundHashes() throws Exception{
        int index = getCurrentRoundIndex();
        try {
            RoundViewModel currentRound = RoundViewModel.get(tangle, index);
            if (currentRound == null) {
                return null;
            } else {
                return currentRound.getHashes();
            }
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while getting latest milestones (#" + index + ")", e);
        }
    }

    @Override
    public void clearLatestRoundHashes() {this.latestRoundHashes.clear();}

    @Override
    public boolean processMilestoneCandidate(Hash transactionHash) throws MilestoneException {
        try {
            return processMilestoneCandidate(TransactionViewModel.fromHash(tangle, transactionHash));
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while analyzing the transaction " + transactionHash, e);
        }
    }

    /**
     * {@inheritDoc}
     * <br />
     * If we detect a milestone that is either {@code INCOMPLETE} or not solid, yet we hand it over to the
     * {@link MilestoneSolidifier} that takes care of requesting the missing parts of the milestone bundle.<br />
     */
    @Override
    public boolean processMilestoneCandidate(TransactionViewModel transaction) throws MilestoneException {
        try {
            //System.out.println("Process Milestone");
            //System.out.println("Hash: " + transaction.getHash().hexString() + ", round: " + milestoneService.getRoundIndex(transaction));
            //System.out.println("Current Round: " + getCurrentRoundIndex());

            int roundIndex = RoundViewModel.getRoundIndex(transaction);
            int currentRound = getCurrentRoundIndex();

            // todo what happens if this method isn't called for that round and it passes the start round (do we need <= here?)
            if (nomineeTracker.getStartRound() == currentRound) {
                setCurrentNominees(nomineeTracker.getLatestNominees());
                allNominees.addAll(nomineeTracker.getLatestNominees());
            }

            Set<Hash> nominees = currentNominees;
            if (roundIndex != currentRound) {
                nominees = nomineeTracker.getNomineesOfRound(roundIndex);
            }

            if (nominees.contains(transaction.getAddressHash()) && transaction.getCurrentIndex() == 0) {

                // if the milestone is older than our ledger start point: we already processed it in the past
                if (roundIndex <= snapshotProvider.getInitialSnapshot().getIndex()) {
                    return true;
                }

                switch (milestoneService.validateMilestone(transaction, roundIndex, SpongeFactory.Mode.S256, 1, nominees)) {
                    case VALID:
                        System.out.println("VALID");
                        // TODO: 1.review conditions, 2.is okay to store here?
                        // before a milestone can be added to a round the following conditions have to be checked:
                        // - index is bigger than initial snapshot (above)
                        // - VALID:
                        //      - senderAddress must be part of validator addresses
                        //      - signature belongs to senderAddress
                        //      - index is bigger than snapshot index
                        // - attachment timestamp is in correct time window for the index
                        // - there doesn't already exist a milestone with the same address for that round
                        System.out.println("attachment timestamp round: " + getRound(transaction.getAttachmentTimestamp()));
                        if (roundIndex == getRound(transaction.getAttachmentTimestamp())) {

                            RoundViewModel currentRoundViewModel;

                            // there already arrived a milestone for that round, just update
                            if ((currentRoundViewModel = RoundViewModel.get(tangle, roundIndex)) != null) {
                                // check if there is already a milestone with the same address
                                if (RoundViewModel.getMilestone(tangle, roundIndex, transaction.getAddressHash()) == null) {
                                    currentRoundViewModel.addMilestone(transaction.getHash());
                                    currentRoundViewModel.update(tangle);
                                    //log.info("Milestone " + transaction.getHash().hexString() + " is stored in round #" + roundIndex + " (" + currentRoundViewModel.size() + ")");
                                } else {
                                    //log.error("Round #" + roundIndex + " has already received a milestone from " + transaction.getAddressHash().hexString());
                                }
                            }
                            // this is the first milestone for that round, make new database entry
                            else {
                                Set<Hash> milestones = new HashSet<>();
                                milestones.add(transaction.getHash());
                                currentRoundViewModel = new RoundViewModel(roundIndex, milestones);
                                currentRoundViewModel.store(tangle);
                                //log.info("Milestone " + transaction.getHash().hexString() + " is stored in round #" + roundIndex);
                            }

                            addMilestoneToRoundLog(transaction.getHash(), roundIndex, currentRoundViewModel.size(), nominees.size());
                        }

                        if (!transaction.isSolid()) {
                            milestoneSolidifier.add(transaction.getHash(), roundIndex);
                        }

                        transaction.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);
                        break;

                    case INCOMPLETE:
                        System.out.println("INCOMPLETE");
                        milestoneSolidifier.add(transaction.getHash(), roundIndex);

                        transaction.isMilestone(tangle, snapshotProvider.getInitialSnapshot(), true);

                        return false;

                    default:
                        // we can consider the milestone candidate processed and move on w/o farther action
                }
            }

            return true;
        } catch (Exception e) {
            throw new MilestoneException("unexpected error while analyzing the " + transaction, e);
        }
    }

    @Override
    public boolean isInitialScanComplete() {
        return initialized;
    }

    /**
     * {@inheritDoc}
     * <br />
     * We repeatedly call {@link #latestMilestoneTrackerThread()} to actively look for new milestones in our database.
     * This is a bit inefficient and should at some point maybe be replaced with a check on transaction arrival, but
     * this would required adjustments in the whole way IRI handles transactions and is therefore postponed for
     * now.<br />
     */
    @Override
    public void start() {
        executorService.silentScheduleWithFixedDelay(this::latestMilestoneTrackerThread, 0, RESCAN_INTERVAL,
                TimeUnit.MILLISECONDS);
    }


    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }


    /**
     * This method bootstraps this tracker with the latest milestone values that can easily be retrieved without
     * analyzing any transactions (for faster startup).<br />
     * <br />
     * It first sets the latest milestone to the values found in the latest snapshot and then check if there is a younger
     * milestone at the end of our database. While this last entry in the database doesn't necessarily have to be the
     * latest one we know it at least gives a reasonable value most of the times.<br />
     */
    @Override
    public void bootstrapCurrentRoundIndex() {
        Snapshot latestSnapshot = snapshotProvider.getLatestSnapshot();
        setCurrentRoundIndex(latestSnapshot.getIndex() + 1);

        try {
            RoundViewModel lastMilestoneInDatabase = RoundViewModel.latest(tangle);
            if (lastMilestoneInDatabase != null && lastMilestoneInDatabase.index() > getCurrentRoundIndex()) {
                setCurrentRoundIndex(lastMilestoneInDatabase.index());
            }
        } catch (Exception e) {
            log.error("unexpectedly failed to retrieve the latest milestone from the database", e);
        }
    }


    /**
     * This method contains the logic for scanning for new latest milestones that gets executed in a background
     * worker.<br />
     * <br />
     * It first collects all new milestone candidates that need to be analyzed, then analyzes them and finally checks if
     * the initialization is complete. In addition to this scanning logic it also issues regular log messages about the
     * progress of the scanning.<br />
     */
    private void latestMilestoneTrackerThread() {
        try {
            logProgress();
            collectNewMilestoneCandidates();

            // additional log message on the first run to indicate how many milestone candidates we have in total
            if (firstRun) {
                firstRun = false;

                logProgress();
            }

            analyzeMilestoneCandidates();
            checkIfInitializationComplete();
        } catch (MilestoneException e) {
            log.error("error while analyzing the milestone candidates", e);
        }
    }

    /**
     * This method emits a log message about the scanning progress.<br />
     * <br />
     * It only emits a log message if we have more than one {@link #milestoneCandidatesToAnalyze}, which means that the
     * very first call to this method in the "first run" on {@link #latestMilestoneTrackerThread()} will not produce any
     * output (which is the reason why we call this method a second time after we have collected all the
     * candidates in the "first run").<br />
     */
    private void logProgress() {
        if (milestoneCandidatesToAnalyze.size() > 1) {
            log.info("Processing milestone candidates (" + milestoneCandidatesToAnalyze.size() + " remaining) ...");
        }
    }

    /**
     * This method collects the new milestones that have not been "seen" before, by collecting them in the {@link
     * #milestoneCandidatesToAnalyze} queue.<br />
     * <br />
     * We simply request all transaction that are originating from the coordinator address and treat them as potential
     * milestone candidates.<br />
     *
     * @throws MilestoneException if anything unexpected happens while collecting the new milestone candidates
     */
    private void collectNewMilestoneCandidates() throws MilestoneException {
        try {
            for (Hash address : allNominees) {
                for (Hash hash : AddressViewModel.load(tangle, address).getHashes()) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }

                    if (seenMilestoneCandidates.add(hash)) {
                        milestoneCandidatesToAnalyze.addFirst(hash);
                    }
                }
            }
        } catch (Exception e) {
            throw new MilestoneException("failed to collect the new milestone candidates", e);
        }
    }

    /**
     * This method analyzes the milestone candidates by working through the {@link #milestoneCandidatesToAnalyze}
     * queue.<br />
     * <br />
     * We only process {@link #MAX_CANDIDATES_TO_ANALYZE} at a time, to give the caller the option to terminate early
     * and pick up new milestones as fast as possible without being stuck with analyzing the old ones for too
     * long.<br />
     *
     * @throws MilestoneException if anything unexpected happens while analyzing the milestone candidates
     */
    private void analyzeMilestoneCandidates() throws MilestoneException {
        int candidatesToAnalyze = Math.min(milestoneCandidatesToAnalyze.size(), MAX_CANDIDATES_TO_ANALYZE);
        for (int i = 0; i < candidatesToAnalyze; i++) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            Hash candidateTransactionHash = milestoneCandidatesToAnalyze.pollFirst();
            if(!processMilestoneCandidate(candidateTransactionHash)) {
                seenMilestoneCandidates.remove(candidateTransactionHash);
            }
        }
    }

    /**
     * This method checks if the initialization is complete.<br />
     * <br />
     * It simply checks if the {@link #initialized} flag is not set yet and there are no more {@link
     * #milestoneCandidatesToAnalyze}. If the initialization was complete, we issue a log message and set the
     * corresponding flag to {@code true}.<br />
     */
    private void checkIfInitializationComplete() {
        if (!initialized && milestoneCandidatesToAnalyze.size() == 0) {
            initialized = true;

            log.info("Processing milestone candidates ... [DONE]").triggerOutput(true);
        }
    }
}
