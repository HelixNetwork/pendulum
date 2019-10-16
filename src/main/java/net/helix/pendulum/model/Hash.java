package net.helix.pendulum.model;

import net.helix.pendulum.crypto.Sha3;
import net.helix.pendulum.crypto.merkle.MerkleNode;
import net.helix.pendulum.storage.Indexable;


/**
* Hash is an implementation of the Serializable, Indexable and HashID interface.
* The model class contains a hash <code> Hash </code>, the size of the hash, lock
* and the inner classes <code> ByteSafe </code>
*/
public interface Hash extends Indexable, HashId, MerkleNode {
    
    /**
     * Creates a null transaction hash with from a byte array of length {@value Sha3#HASH_LENGTH}.
     * This is used as a reference hash for the genesis transaction.
     */
    Hash NULL_HASH = HashFactory.TRANSACTION.create(new byte[Sha3.HASH_LENGTH]);

    /**
     * The size of a hash stored in a byte[] when the data structure is bytes
     */
    int SIZE_IN_BYTES = 32;

    /**
     * The data of this hash in bytes
     * @return the bytes
     */
    byte[] bytes();

    /**
     * The amount of zeros this hash has on the end.
     * Defines the weightMagnitude for a transaction.
     * @return the trailing zeros
     */
    int trailingZeros();

    /**
     * The amount of leading zeros on this hash.
     * Defines the weightMagnitude for a transaction.
     * @return the trailing zeros
     */
    int leadingZeros();

}
