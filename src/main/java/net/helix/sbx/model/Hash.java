package net.helix.sbx.model;

import net.helix.sbx.crypto.Sha3;
import net.helix.sbx.storage.Indexable;
//import net.helix.sbx.utils.Converter;


/**
* Hash is an implementation of the Serializable, Indexable and HashID interface.
* The model class contains a crypto <code> Hash </code>, the size of the crypto, lock
* and the inner classes <code> ByteSafe </code> and <code> TritSafe </code>.
*/
public interface Hash extends Indexable, HashId {
    Hash NULL_HASH = HashFactory.TRANSACTION.create(new byte[Sha3.HASH_LENGTH]);

    /**
     * The size of a crypto stored in a byte[] when the data structure is bytes
     */
    int SIZE_IN_BYTES = 32;

    /**
     * The data of this hash in bytes
     * @return the bytes
     */
    public byte[] bytes();

    /**
     * The data of this hash as hexString
     * @return the hexString
     */
    public String hexString();

    /**
     * The amount of zeros this hash has on the end.
     * Defines the weightMagnitude for a transaction.
     * @return the trailing zeros
     */
    public int trailingZeros();

    /**
     * The amount of leading zeros on this hash.
     * Defines the weightMagnitude for a transaction.
     * @return the trailing zeros
     */
    public int leadingZeros();

}
