package net.helix.pendulum.model;

import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import org.bouncycastle.util.encoders.Hex;

public class TransactionHash extends AbstractHash {

    public TransactionHash() {
    }

    protected TransactionHash(byte[] source, int offset, int sourceSize) {
        super(source, offset, sourceSize);
    }

    /**
     * Calculates a transaction hash from an array of bytes.
     * @param mode The mode(sha3 or keccak) we absorb the bytes with
     * @param bytes array of bytes we calculate the hash with
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(SpongeFactory.Mode mode, byte[] bytes) {
        return calculate(bytes, 0, bytes.length, SpongeFactory.create(mode));
    }

    @Override
    protected int getByteSize() {
        return Hash.SIZE_IN_BYTES;
    }
    /**
     * Calculates a transaction hash from an array of bytes.
     * Temporarily returns the NULL_HASH for nullByte transactionViewModels.
     * @param sha3 sponge
     * @param bytes array of bytes we calculate the hash with
     * @param length
     * @param offset
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(final byte[] bytes, int offset, int length, final Sponge sha3) {
        byte[] hash = new byte[SIZE_IN_BYTES];
        sha3.reset();
        sha3.absorb(bytes, offset, length);
        sha3.squeeze(hash, 0, SIZE_IN_BYTES);
        return (TransactionHash) HashFactory.TRANSACTION.create(hash, 0, SIZE_IN_BYTES);
    }

    /**
     * Calculates a transaction hash from an array of bytes
     * @param bytes The bytes that contain the transactionHash
     * @param length length of byte[]
     * @param sponge Mode to absorb
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(byte[] bytes, int length, Sponge sponge) {
        return calculate(bytes, 0, length, sponge);
    }

    /**
     * Calculates a transaction hash from an array of bytes.
     * @param sha3 sponge
     * @param hex transaction bytes in hex-string format
     * @param length
     * @param offset
     * @return The {@link TransactionHash}
     */
    public static TransactionHash calculate(final String hex, int offset, int length, final Sponge sha3) {
        byte[] bytes = Hex.decode(hex);
        TransactionHash hash = calculate(bytes, offset, length, sha3);
        return hash;
    }

}
