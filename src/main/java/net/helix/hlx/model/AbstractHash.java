package net.helix.hlx.model;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

import org.bouncycastle.util.encoders.Hex;

import net.helix.hlx.utils.Converter;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.model.safe.ByteSafe;
import net.helix.hlx.storage.Indexable;

public abstract class AbstractHash implements  Hash, Serializable {

    private final Object lock = new Object();
    private ByteSafe byteSafe;

    /**
     * Empty Constructor for a placeholder hash identifier object. Creates a hash identifier object with no properties.
     */
    public AbstractHash() {
    }

    /**
     * AbstractHash constructor for byte array with offset.
     * @param source byte array
     * @param sourceOffset offset length
     * @param sourceSize length of byte array
     */
    public AbstractHash(byte[] source, int sourceOffset, int sourceSize) {
        super();
        byte[] dest = new byte[getByteSize()];
        System.arraycopy(source, sourceOffset, dest, 0, sourceSize - sourceOffset > source.length ? source.length - sourceOffset : sourceSize);
        this.byteSafe = new ByteSafe(dest);
    }

    protected abstract int getByteSize();

    /**
     * Private method for reading in the byte array.
     * @param src byte array
     * @throws IllegalStateException in case bytes are already initialized.
     */
    private void fullRead(byte[] src) {
        if (src != null) {
            synchronized (lock) {
                if (byteSafe != null) {
                    throw new IllegalStateException("I cannot be initialized with data twice.");
                }
                byte[] dest = new byte[getByteSize()];
                System.arraycopy(src, 0, dest, 0, Math.min(dest.length, src.length));
                byteSafe = new ByteSafe(dest);
            }
        }
    }

    /**
     * Counting number of zeros in a byte array
     * @return zeros <code> int </code>
     */
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
     * Counting number of zeros in a byte array
     * @return zeros <code> int </code>
     */
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
     * Check Equality of hash and <code> object </code> o
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
     * Get hash code of bytes from byteSafe.
     * @return <code> boolean </code> equality
     */
    @Override
    public int hashCode() {
        bytes();
        return byteSafe.getHashcode();
    }

    /**
     * Get bytes.
     * @return <code> byte[] </code> bytes
     */
    public byte[] bytes() {
        ByteSafe safe = byteSafe;
        if (safe == null) {
            Objects.requireNonNull(byteSafe, "No bytes initialized, Please use fullRead(byte[]) to read in the byte array");
        }
        return safe.getData();
    }

    /**
     * Convert to hex string
     * @return <code> string </code> string in hex representation
     */
    public String toString() {
        return Hex.toHexString(bytes());
    }

    /**
     * Reading byte array. @see #fullRead(byte[])
     * @param src byte array
     */
    @Override
    public void read(byte[] src) {
        fullRead(src);
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
