package net.helix.pendulum.crypto;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.utils.FastByteComparisons;
import org.bouncycastle.util.BigIntegers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static net.helix.pendulum.crypto.GreedyMiner.State.*;

/**
 * The Miner performs the proof-of-work needed for a valid block.
 */
public class GreedyMiner {

    private static final Logger log = LoggerFactory.getLogger(GreedyMiner.class);

    /**
     * States of miner.
     */
    protected enum State {
        RUNNING,
        CANCELLED,
        COMPLETED
    }

    private volatile AtomicReference state;

    /**
     * Creates miners to find a correct nonce for the given byte block.
     * @param txBytes byte block.
     * @param difficulty the mining difficulty. The difficulty is a number of leading zero bytes and it has to be in [1..31].
     * @param threadCount miner count. If the count is not in [1..16], it is set automatically.
     * @return {@code true} if a valid nonce has been added into the byte block, {@code false} otherwise.
     * @throws IllegalArgumentException if TransactionViewModel.NONCE_SIZE < Long.BYTES
     * @throws IllegalArgumentException if txBytes is null or txBytes.length != TransactionViewModel.SIZE
     * @throws IllegalArgumentException if difficulty is not in [1..31]
     * @see TransactionViewModel#SIZE
     * @see TransactionViewModel#NONCE_SIZE
     */
    public synchronized boolean mine(byte[] txBytes, int difficulty, int threadCount) {
        if (TransactionViewModel.NONCE_SIZE < Long.BYTES) {
            throw new IllegalArgumentException("Illegal NONCE_SIZE: " + TransactionViewModel.NONCE_SIZE);
        }
        if (txBytes == null || txBytes.length != TransactionViewModel.SIZE) {
            throw new IllegalArgumentException("Illegal txBytes length: "
                    + (txBytes == null ? null : txBytes.length));
        }
        difficulty *= 8;
        if (difficulty < 1 || difficulty > 255) {
            throw new IllegalArgumentException("Illegal difficulty: " + difficulty);
        }
        if (threadCount < 1 || threadCount > 16) {
            threadCount = Math.max(1, Math.floorDiv(Runtime.getRuntime().availableProcessors() * 8, 10));
        }
        state = new AtomicReference(RUNNING);
        byte[] target = BigIntegers.asUnsignedByteArray(Sha3.HASH_LENGTH,
                BigInteger.valueOf(2).pow(256 - difficulty));

        ArrayList<Thread> miners = new ArrayList<>(threadCount);
        for (int i = 1; i <= threadCount; i++) {
            Runnable runnable = getMiner(txBytes, target, i, threadCount);
            Thread miner = new Thread(runnable);
            miner.setName("miner#" + i);
            miner.setDaemon(true);
            miners.add(miner);
            miner.start();
        }
        for (Thread miner : miners) {
            try {
                miner.join();
            } catch (InterruptedException ex) {
                cancel();
            }
        }
        //log.debug("MINER_STATE: {}", state);
        return state.get() == COMPLETED;
    }

    /**
     * Cancels miners working.
     */
    public void cancel() {
        if (state != null) {
            state.set(CANCELLED);
        }
    }

    /**
     * The miner finds a correct nonce and adds it into the byte block.
     * @param txBytes the byte block.
     * @param target the pattern to compare.
     * @param offset the initial nonce.
     * @param step the step added to the nonce every iteration.
     * @return the miner.
     */
    private Runnable getMiner(byte[] txBytes, byte[] target, int offset, int step) {
        return () -> {
            byte[] result = txBytes.clone();
            ByteBuffer nonceWrapper = ByteBuffer.wrap(result, TransactionViewModel.NONCE_OFFSET,
                    TransactionViewModel.NONCE_SIZE).slice();
            for (long nonce = offset; state.get() == RUNNING && nonce > 0; nonce += step) {
                nonceWrapper.putLong(0, nonce);
                byte[] hash = sha3(result);
                if(FastByteComparisons.compareTo(hash, 0, Sha3.HASH_LENGTH, target, 0, Sha3.HASH_LENGTH) < 0) {
                    if (state.compareAndSet(RUNNING, COMPLETED)) {
                        System.arraycopy(result, TransactionViewModel.NONCE_OFFSET, txBytes,
                                TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                        //log.debug("TX_HASH: {}", Hex.toHexString(hash));
                        //log.debug("NONCE  : {}", Hex.toHexString(ByteBuffer.allocate(Long.BYTES).putLong(nonce).array()));
                    }
                }
            }
        };
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

}
