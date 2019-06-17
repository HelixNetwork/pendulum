package net.helix.hlx.model;

import net.helix.hlx.model.persistables.*;
import net.helix.hlx.storage.Persistable;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum HashFactory {
    TRANSACTION(Transaction.class),
    ADDRESS(Address.class),
    BUNDLE(Bundle.class),
    TAG(Tag.class),
    BUNDLENONCE(BundleNonce.class),

    /**
     * Creates from generic class, should be passed in the create() function.
     * Will return NULL_HASH if other functions are used
     */
    GENERIC;

    private static final Logger log = LoggerFactory.getLogger(HashFactory.class);

    private Class<? extends Persistable> clazz;

    HashFactory(Class<? extends Persistable> clazz) {
        this.clazz = clazz;
    }

    HashFactory() {

    }

    /**
     * Creates a Hash using the provided hex-string
     * @param hex hex-string, the source data
     * @return the hash
     */
    public Hash create(String hex) {
        byte[] bytes = Hex.decode(hex);
        return create(clazz, bytes, 0, Hash.SIZE_IN_BYTES);
    }

    /**
     * Creates a Hash using the provided bytes
     * @param source the source data
     * @param sourceOffset the offset we start reading from
     * @param sourceSize the size this hash is in bytes, starting from offset
     * @return the hash
     */
    public Hash create(byte[] source, int sourceOffset, int sourceSize) {
        return create(clazz, source, sourceOffset, sourceSize);
    }

    /**
     * Creates a Hash using the provided bytes
     * @param bytes the source data
     * @param sourceOffset the offset we start reading from
     * @return the hash
     */
    public Hash create(byte[] bytes, int sourceOffset) {
        return create(clazz, bytes, sourceOffset, Hash.SIZE_IN_BYTES);
    }

    /**
     * Creates a Hash using the provided source.
     * Starts from the beginning, source size is based on source length
     * @param source the source data
     * @return the hash
     */
    public Hash create(byte[] source) {
        return create(clazz, source, 0,  Hash.SIZE_IN_BYTES );
    }

    /**
     *
     * @param modelClass The model this Hash represents
     * @param source the source data or bytes. Based on the length of source data
     * @return the hash of the correct type
     */
    public Hash create(Class<?> modelClass, byte[] source) {
        return create(modelClass, source, 0, AbstractHash.SIZE_IN_BYTES);
    }

    /**
     *
     * @param modelClass The model this Hash represents
     * @param source the source data, bytes / hex decoded bytes
     * @param sourceOffset the offset in the source
     * @param sourceSize the size this hash is in bytes, starting from offset
     * @return the hash of the correct type
     */
    public Hash create(Class<?> modelClass, byte[] source, int sourceOffset, int sourceSize) {
        //Transaction is first since its the most used
        if (modelClass.equals(Transaction.class) || modelClass.equals(Approvee.class)) {
            return new TransactionHash(source, sourceOffset, sourceSize);

        } else if (modelClass.equals(Address.class)) {
            return new AddressHash(source, sourceOffset, sourceSize);

        } else if (modelClass.equals(Bundle.class)) {
            return new BundleHash(source, sourceOffset, sourceSize);

        } else if (modelClass.equals(Tag.class)) {
            return new TagHash(source, sourceOffset, sourceSize);

        } else if (modelClass.equals(BundleNonce.class)) {
            return new BundleNonceHash(source, sourceOffset, sourceSize);

        } else {
            log.warn("Tried to construct hash from unknown class " + modelClass);
            return new TransactionHash(source, sourceOffset, sourceSize);
        }
    }
}
