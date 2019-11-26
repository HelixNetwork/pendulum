package net.helix.pendulum.service.milestone;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

import java.util.Set;
import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.service.snapshot.SnapshotProvider;
import net.helix.pendulum.service.validatormanager.CandidateTracker;
import net.helix.pendulum.storage.Tangle;

/**
 * The manager that keeps track of the latest milestone by incorporating a background worker that periodically checks if
 * new milestones have arrived.<br />
 * <br />
 * Knowing about the latest milestone and being able to compare it to the latest solid milestone allows us to determine
 * if our node is "in sync".<br />
 */
public interface MilestoneTracker extends Pendulum.Initializable  {

    void addMilestoneToRoundLog(Hash milestoneHash, int roundIndex, int numberOfMilestones, int numberOfValidators);


    /**
     * Returns the index of the latest milestone that was seen by this tracker.<br />
     * <br />
     * It simply returns the internal property that is used to store the latest milestone index.<br />
     *
     * @return the index of the latest milestone that was seen by this tracker
     */
    int getCurrentRoundIndex();

    int getRound(long time);

    boolean isRoundActive(long time);

    /**
     * Returns the transaction hash of the latest milestone that was seen by this tracker.<br />
     * <br />
     * It simply returns the internal property that is used to store the latest milestone index.<br />
     *
     * @return the transaction hash of the latest milestone that was seen by this tracker
     */
    Set<Hash> getMilestonesOfCurrentRound() throws Exception;

    void setCurrentValidators(Set<Hash> validatorAddresses);

    /**
     * Analyzes the given transaction to determine if it is a valid milestone.<br />
     * <br />
     * If the transaction that was analyzed represents a milestone, we check if it is younger than the current latest
     * milestone and update the internal properties accordingly.<br />
     *
     * @param transaction the transaction that shall be examined
     * @return {@code true} if the milestone could be processed and {@code false} if the bundle is not complete, yet
     * @throws MilestoneException if anything unexpected happens while trying to analyze the milestone candidate
     */
    boolean processMilestoneCandidate(TransactionViewModel transaction) throws MilestoneException;

    /**
     * Does the same as {@link #processMilestoneCandidate(TransactionViewModel)} but automatically retrieves the
     * transaction belonging to the passed in hash.<br />
     *
     * @param transactionHash the hash of the transaction that shall be examined
     * @return {@code true} if the milestone could be processed and {@code false} if the bundle is not complete, yet
     * @throws MilestoneException if anything unexpected happens while trying to analyze the milestone candidate
     */
    boolean processMilestoneCandidate(Hash transactionHash) throws MilestoneException;

    /**
     * Since the {@link MilestoneTracker} scans all milestone candidates whenever the node restarts, this flag gives us
     * the ability to determine if this initialization process has finished.<br />
     * <br />
     *
     * @return {@code true} if the initial scan of milestones has finished and {@code false} otherwise
     */
    boolean isInitialScanComplete();

    /**
     * This method starts the background worker that automatically calls {@link #processMilestoneCandidate(Hash)} on all
     * newly found milestone candidates to update the latest milestone.<br />
     */
    void start();

    /**
     * This method stops the background worker that updates the latest milestones.<br />
     */
    void shutdown();
    
    MilestoneTracker init(Tangle tangle, SnapshotProvider snapshotProvider,
            MilestoneService milestoneService, MilestoneSolidifier milestoneSolidifier, CandidateTracker candidateTracker, PendulumConfig config);

}
