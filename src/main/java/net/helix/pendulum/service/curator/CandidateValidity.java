package net.helix.pendulum.service.curator;

/**
 * Validity states of candidate transactions that are used to express their "relevance" for the curator.
 */
public enum CandidateValidity {
    VALID,
    INVALID,
    INCOMPLETE
}
