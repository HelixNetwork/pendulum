package net.helix.pendulum.model;

/**
 * Represents an ID of a transaction, address or bundle
 */
public interface HashId {

    /**
     *
     * @return the bytes of the Hash Id
     */
    byte[] bytes();
}
