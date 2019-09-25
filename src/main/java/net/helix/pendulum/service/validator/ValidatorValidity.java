package net.helix.pendulum.service.validator;

/**
 * Validity states of candidate transactions that are used to express their "relevance" for the validatomanager.
 */
public enum ValidatorValidity {
    VALID,
    INVALID,
    INCOMPLETE
}
