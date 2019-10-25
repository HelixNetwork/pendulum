package net.helix.pendulum.service.validatormanager;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

import java.util.Set;

public interface CandidateTracker {

    int getStartRound();

    /**
     * validatomanager address.<br />
     */
    //Hash validatorManagerAddress = HashFactory.ADDRESS.create("c2eb2d5297f4e70f3e40e3d7aa3f5c1d7405264aeb72232d06776605d8b61211");

    /**
     * Analyzes the given transaction to determine if it is a valid candidate application.<br />
     * <br />
     * If the transaction that was analyzed represents a candidate application, we check... <br />
     *
     * @param transaction the transaction that shall be examined
     * @return {@code true} if the milestone could be processed and {@code false} if the bundle is not complete, yet
     * @throws ValidatorManagerException if anything unexpected happens while trying to analyze the milestone candidate
     */
    boolean processCandidate(TransactionViewModel transaction) throws ValidatorManagerException;

    /**
     * Does the same as {@link #processCandidate(TransactionViewModel)} but automatically retrieves the
     * transaction belonging to the passed in hash.<br />
     *
     * @param transactionHash the hash of the transaction that shall be examined
     * @return {@code true} if the milestone could be processed and {@code false} if the bundle is not complete, yet
     * @throws ValidatorManagerException if anything unexpected happens while trying to analyze the milestone candidate
     */
    boolean processCandidate(Hash transactionHash) throws ValidatorManagerException;


    Set<Hash> getValidators();

    Set<Hash> getValidatorsOfRound(int roundIndex);

    /**
     * This method starts the background worker that...
     */
    void start();

    /**
     * This method stops the background worker that....<br />
     */
    void shutdown();
}
