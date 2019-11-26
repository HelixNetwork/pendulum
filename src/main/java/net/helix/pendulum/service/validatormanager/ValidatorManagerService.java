package net.helix.pendulum.service.validatormanager;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.Hash;

import java.util.Set;

public interface ValidatorManagerService extends Pendulum.Initializable {

    /**
     * <p>
     * Analyzes the given transaction to determine if it is a valid candidate transaction.
     * </p>
     * </p>
     * It first checks if all transactions that belong to the candidate bundle are known already
     *
     *
     * (and only then verifies
     * the signature to analyze if the given candidate transaction was really issued by a valid candidate) <- might not be needed.
     * </p>
     *
     * @param transactionViewModel transaction that shall be analyzed
     * @param mode hash mode
     * @param securityLevel
     * @return validity status of the transaction regarding its role as a validator application
     * @throws ValidatorManagerException if anything unexpected goes wrong while validating the candidate transaction
     */
    CandidateValidity validateCandidate(TransactionViewModel transactionViewModel, SpongeFactory.Mode mode, int securityLevel, Set<Hash> validators) throws ValidatorManagerException;

    /**
     * <p>
     * Checks if the given transaction was confirmed by the candidate with the given index (or any of its
     * predecessors).
     * </p>
     * </p>
     * We determine if the transaction was confirmed by examining its {@code snapshotIndex} value. For this method to
     * work we require that the previous candidates have been processed already (which is enforced by the {@link
     * net.helix.pendulum.service.validatormanager.CandidateTracker} which applies the candidates in the order that they
     * are issued by the coordinator).
     * </p>
     *
     * @param transaction the transaction that shall be examined
     * @param roundIndex the round index that we want to check against
     * @return {@code true} if the candidate has been nominated and {@code false} otherwise
     */
    boolean isCandidateConfirmed(TransactionViewModel transaction, int roundIndex);

    /**
     * Does the same as {@link #isCandidateConfirmed(TransactionViewModel, int)} but defaults to the latest solid
     * round index for the {@code roundIndex} which means that the candidate has been nominated and is eligible to participate for as full node.
     *
     * @param transaction the transaction that shall be examined
     * @return {@code true} if the transaction belongs to the candidate and {@code false} otherwise
     */
    boolean isCandidateConfirmed(TransactionViewModel transaction);

    /**
     *      interval = initial_candidate_tx[timestamp] + now
     *      uptime = (interval – downtime ) / interval
     *
     * @param address address of the candidate whom's uptime since application is computed
     * @return relative uptime (since application or since in {@code nominationCandidates}
     */
    double getCandidateRelativeUptime(Hash address);

    /**
     * @param candidateAddress address of the candidate whom's reputation is computed
     * @param seenCandidateTransactions all transactions of seen candidates
     * @param testRep testing reputation
     * @return relative reputation of a candidate (since application or since in {@code nominationCandidates}
     */
    double getCandidateNormalizedWeight(Hash candidateAddress, Set<Hash> seenCandidateTransactions, Double testRep);
    double getCandidateNormalizedWeight(Hash candidateAddress, Set<Hash> seenCandidateTransactions);
}
