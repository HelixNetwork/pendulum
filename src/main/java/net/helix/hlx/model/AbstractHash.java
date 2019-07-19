package net.helix.hlx.model;

import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.model.safe.ByteSafe;
import net.helix.hlx.storage.Indexable;
import org.bouncycastle.util.encoders.Hex;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public abstract class AbstractHash implements  Hash, Serializable {

    private final Object lock = new Object();
    private ByteSafe byteSafe;

    /**
     * AbstractHash constructor for byte array with offset.
     * @param source byte array
     * @param sourceOffset offset length
     * @param sourceSize length of byte array
     */
    public AbstractHash(byte[] source, int sourceOffset, int sourceSize) {
        byte[] dest = new byte[SIZE_IN_BYTES];
        System.arraycopy(source, sourceOffset, dest, 0, sourceSize - sourceOffset > source.length ? source.length - sourceOffset : sourceSize);
        this.byteSafe = new ByteSafe(dest);
    }

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
                byte[] dest = new byte[SIZE_IN_BYTES];
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
        int index = SIZE_IN_BYTES;
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
        while (index < SIZE_IN_BYTES && bytes[index] == 0) {
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
    public String toString() { return Hex.toHexString(bytes()); }

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
        long diff = bytesToLong(hash.bytes(), 0) - bytesToLong(bytes(), 0);
        if (Math.abs(diff) > Integer.MAX_VALUE) {
            return diff > 0L ? Integer.MAX_VALUE : Integer.MIN_VALUE + 1;
        }
        return (int) diff;
    }

    public static long bytesToLong(byte[] array, int offset) {
        return
                ((long)(array[offset]   & 0xff) << 56) |
                        ((long)(array[offset+1] & 0xff) << 48) |
                        ((long)(array[offset+2] & 0xff) << 40) |
                        ((long)(array[offset+3] & 0xff) << 32) |
                        ((long)(array[offset+4] & 0xff) << 24) |
                        ((long)(array[offset+5] & 0xff) << 16) |
                        ((long)(array[offset+6] & 0xff) << 8) |
                        ((long)(array[offset+7] & 0xff));
    }
}
