package net.helix.pendulum.crypto;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.utils.FastByteComparisons;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;

/* Next steps:
 *  - (done) Assert that sha3() is equivalent to sha3Alternative()
 *  - (done: 'difficulty' is a power of 2) Find a difficulty value that corresponds to ~2^22 operations.
 *  - (done: GreedyMiner.java) Multithreading: add a numOfThreads parameter, define a search runnable, create a different entry nonce for each worker.
 *  - Replace divepearler with Miner in API
 */

/**
 * The Miner (Ethereum-style) performs the proof-of-work needed for a valid block.
 */
public class Miner {

    private static final Logger log = LoggerFactory.getLogger(Miner.class);
    
    /**
     * Finds a correct nonce for the given byte block.
     * @param txBytes byte block.
     * @param difficulty the mining difficulty. The difficulty is a number of leading zero bytes and it has to be in [1..31].
     * @return {@code true} if a valid nonce has been added into the byte block, {@code false} otherwise.
     * @throws IllegalArgumentException if txBytes is null or txBytes.length != TransactionViewModel.SIZE
     * @throws IllegalArgumentException if difficulty is not in [1..31]
     * @see TransactionViewModel#SIZE
     */
    public boolean mine(byte[] txBytes, int difficulty) {
        return mine(txBytes, difficulty, new byte[TransactionViewModel.NONCE_SIZE]);
    }
    
    /**
     * Finds a correct nonce for the given byte block.
     * @param txBytes byte block.
     * @param difficulty the mining difficulty. The difficulty is a number of leading zero bytes and it has to be in [1..31].
     * @param nonce the initial nonce.
     * @return {@code true} if a valid nonce has been added into the byte block, {@code false} otherwise.
     * @throws IllegalArgumentException if txBytes is null or txBytes.length != TransactionViewModel.SIZE
     * @throws IllegalArgumentException if difficulty is not in [1..31]
     * @see TransactionViewModel#SIZE
     */
    protected boolean mine(byte[] txBytes, int difficulty, byte[] nonce) {
        if (txBytes == null || txBytes.length != TransactionViewModel.SIZE) {
            throw new IllegalArgumentException("Illegal txBytes length: "
                    + (txBytes == null ? null : txBytes.length));
        }
        difficulty *= 8;
        if (difficulty < 1 || difficulty > 255) {
            throw new IllegalArgumentException("Illegal difficulty: " + difficulty);
        }
        byte[] target = BigIntegers.asUnsignedByteArray(Sha3.HASH_LENGTH,
                BigInteger.valueOf(2).pow(256 - difficulty));
        byte[] result = txBytes.clone();
        while(increment(nonce)) {
            System.arraycopy(nonce, 0, result, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
            byte[] hash = sha3(result);
            if(FastByteComparisons.compareTo(hash, 0, Sha3.HASH_LENGTH, target, 0, Sha3.HASH_LENGTH) < 0) {
                System.arraycopy(nonce, 0, txBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                log.debug("TX_HASH: " + Hex.toHexString(hash));
                log.debug("NONCE  : " + Hex.toHexString(nonce));
                return true;
            }
        }
        return false; // A valid nonce is not found
    }

    /**
     * Hashes the byte array.
     * @param message the bytes to be hashed
     * @return the hash
     */
    private static byte[] sha3(byte[] message) {
        Sha3 sha3 = new Sha3();
        byte[] txHash = new byte[Sha3.HASH_LENGTH];
        sha3.reset();
        sha3.absorb(message, 0, message.length);
        sha3.squeeze(txHash, 0, Sha3.HASH_LENGTH);
        return txHash;
    }

    /**
     * Increments a value represented by the byte array.
     * @param bytes - a value represented by the byte array
     * @return {@code true} if increment is done, {@code false} if overflow occurred.
     */
    private static boolean increment(byte[] bytes) {
        final int startIndex = 0;
        int i;
        for (i = bytes.length-1; i >= startIndex; i--) {
            bytes[i]++;
            if (bytes[i] != 0) {
                break;
            }
        }
        // Returns false when all bytes are 0 again
        return (i >= startIndex || bytes[startIndex] != 0);
    }

    /**
     * Does nothing.
     */
    public void cancel() {
        // Does nothing
    }

}
