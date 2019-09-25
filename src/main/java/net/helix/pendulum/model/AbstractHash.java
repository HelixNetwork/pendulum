package net.helix.pendulum.model;

import net.helix.pendulum.model.persistables.Transaction;
import net.helix.pendulum.storage.Indexable;
import net.helix.pendulum.utils.Converter;
import org.bouncycastle.util.encoders.Hex;

import java.io.Serializable;
import java.util.Arrays;


/**
 * Base implementation of a hash object.
 */
@SuppressWarnings("ALL")
public abstract class AbstractHash implements Hash, Serializable {

    private byte[] data;
    private Integer hashCode;
    private final Object lock = new Object();

    /**
     * Empty Constructor for a placeholder hash identifier object. Creates a hash identifier object with no properties.
     */
    public AbstractHash() {
    }

    /**
     * Constructor for a hash object using a byte source array.
     *
     * @param source A byte array containing the source information in byte format
     * @param sourceOffset The offset defining the start point for the hash object in the source
     * @param sourceSize The size of the source information in the byte array
     */
    public AbstractHash(byte[] source, int sourceOffset, int sourceSize) {
        read(source, sourceOffset, sourceSize);
    }

    protected abstract int getByteSize();

    /**
     * Assigns the input byte data to the hash object. Each hash object can only be initialized with data
     * once. If the byte array is not null, an <tt>IllegalStateException</tt> is thrown.
     *
     * @param source A byte array containing the source bytes
     * @throws IllegalStateException in case bytes are already initialized
     */
    @Override
    public void read(byte[] source) {
        if (source != null) {
            synchronized (lock) {
                if (data != null) {
                    throw new IllegalStateException("I cannot be initialized with data twice.");
                }
                read(source, 0, source.length);
            }
        }
    }

    /**
     * Private method for reading in the byte array.
     * Source data is cut if it's too long; zeros are added if source data is too short.
     * 
     * @param source byte array
     * @param offset The offset defining the start point for the hash object in the source
     * @param length The size of the source information in the byte array
     */
    private void read(byte[] source, int offset, int length) {
        data = new byte[getByteSize()];
        System.arraycopy(source, offset, data, 0, Math.min(data.length, Math.min(source.length, length)));
        hashCode = Arrays.hashCode(data);
    }

    /**
     * Checks if the hash object is storing a byte array. If there
     * is no byte array present, a <tt>NullPointerException</tt> will be thrown.
     *
     * @return The stored byte array containing the hash values
     */
    @Override
    public byte[] bytes() {
        if (data == null) {
            throw new NullPointerException("No bytes initialized, please use read(byte[]) to read in the byte array");
        }
        return data;
    }

    /**
     * Counting number of zeros in a byte array.
     * 
     * @return zeros <code> int </code>
     */
    @Override
    public int trailingZeros() {
        final byte[] bytes = bytes();
        int index = getByteSize();
        int zeros = 0;
        while (index-- > 0 && bytes[index] == 0) {
            zeros++;
        }
        return zeros;
    }

    /**
     * Counting number of zeros in a byte array.
     * 
     * @return zeros <code> int </code>
     */
    @Override
    public int leadingZeros() {
        final byte[] bytes = bytes();
        int index = 0;
        int zeros = 0;
        while (index < getByteSize() && bytes[index] == 0) {
            zeros++;
            index++;
        }
        return zeros;
    }


    /**
     * Check equality of hash and <code> object </code> o.
     * 
     * @param o the reference object with which to compare
     * @return <code> boolean </code> equality
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Hash hash = (Hash) o;
        return Arrays.equals(bytes(), hash.bytes());
    }

    /**
     * Get hash code of bytes.
     * 
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        bytes();
        return hashCode;
    }

    /**
     * Convert to hex string.
     * 
     * @return <code> string </code> string in hex representation
     */
    @Override
    public String toString() {
        return Hex.toHexString(bytes());
    }

    @Override
    public Indexable incremented() {
        return null;
    }

    @Override
    public Indexable decremented() {
        return null;
    }

    /**
     * Get difference between Hash and <code> Indexable </code>. Returns 0 if they are equal.
     * 
     * @param indexable
     * @return <code> int </code> difference
     */
    @Override
    public int compareTo(Indexable indexable) {
        Hash hash = (indexable instanceof Hash) ? (Hash) indexable : HashFactory.GENERIC.create(Transaction.class, indexable.bytes());
        if (this.equals(hash)) {
            return 0;
        }
        long diff = Converter.bytesToLong(hash.bytes(), 0) - Converter.bytesToLong(bytes(), 0);
        if (Math.abs(diff) > Integer.MAX_VALUE) {
            return diff > 0L ? Integer.MAX_VALUE : Integer.MIN_VALUE + 1;
        }
        return (int) diff;
    }
}
