package net.helix.pendulum.service.validatomanager;

/**
 * Validity states of candidate transactions that are used to express their "relevance" for the validatomanager.
 */
public enum CandidateValidity {
    VALID,
    INVALID,
    INCOMPLETE
}
