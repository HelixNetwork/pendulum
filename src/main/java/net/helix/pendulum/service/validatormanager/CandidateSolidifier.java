package net.helix.pendulum.service.validatormanager;

import net.helix.pendulum.model.Hash;

/**
 * This interface defines the contract for a manager that tries to solidify unsolid candidates by incorporating a
 * background worker that periodically checks the solidity of the candidates and issues transaction requests for the
 * missing transactions until the candidates become solid.
 */
public interface CandidateSolidifier {
    /**
     * This method allows us to add new candidates to the solidifier that will consequently be solidified.
     *
     * @param candidateHash Hash of the candidate that shall be solidified
     * @param roundIndex index corresponding to the round that the candidate submit the application
     */
    void add(Hash candidateHash, int roundIndex);

    /**
     * This method starts the background worker that asynchronously solidifies the candidates.
     */
    void start();

    /**
     * This method shuts down the background worker that asynchronously solidifies the candidates.
     */
    void shutdown();

}


