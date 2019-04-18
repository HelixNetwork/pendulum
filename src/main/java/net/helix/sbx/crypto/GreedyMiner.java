package net.helix.sbx.crypto;

import java.math.BigInteger;
import java.util.ArrayList;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.sbx.controllers.TransactionViewModel;
import net.helix.sbx.utils.FastByteComparisons;
import static net.helix.sbx.crypto.GreedyMiner.State.*;

/** Next steps:
 *  - (done) Assert that sha3() is equivalent to sha3Alternative()
 *  - (done: 'difficulty' is a power of 2) Find a difficulty value that corresponds to ~2^22 operations.
 *  - (done) Multithreading: add a numOfThreads parameter, define a search runnable, create a different entry nonce for each worker.
 *  - Replace divepearler with Miner in API
 */

public class GreedyMiner {

    private static final Logger log = LoggerFactory.getLogger(GreedyMiner.class);

    protected enum State {
        RUNNING,
        CANCELLED,
        COMPLETED
    }

    private volatile State state;
    private final Object syncObj = new Object();

    public synchronized boolean mine(byte[] txBytes, int difficulty, int threadCount) {
        if (difficulty < 1 || difficulty > 255) {
            throw new IllegalArgumentException("Illegal difficulty: " + difficulty);
        }
        if (threadCount < 1 || threadCount > 16) {
            threadCount = Math.max(1, Math.floorDiv(
                    Runtime.getRuntime().availableProcessors() * 8, 10));
        }
        state = RUNNING;
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
        log.info("MINER_STATE: " + state);
        return state == COMPLETED;
    }    

    public void cancel() {
        synchronized (syncObj) {
            state = CANCELLED;
        }
    }

    private Runnable getMiner(byte[] txBytes, byte[] target, int offset, int step) {
        return () -> {
            byte[] result = txBytes.clone();
            byte[] nonce = new byte[TransactionViewModel.NONCE_SIZE];
            increment(nonce, offset);
            if (state == RUNNING) {
                do {
                    System.arraycopy(nonce, 0, result, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                    byte[] hash = sha3(result);
                    if(FastByteComparisons.compareTo(hash, 0, Sha3.HASH_LENGTH, target, 0, Sha3.HASH_LENGTH) < 0) {
                        synchronized (syncObj) {
                            if (state == RUNNING) {
                                state = COMPLETED;
                                System.arraycopy(nonce, 0, txBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                                log.debug("TX_HASH: " + Hex.toHexString(hash));
                                log.debug("NONCE  : " + Hex.toHexString(nonce));
                            }
                        }
                    }
                } while (state == RUNNING && increment(nonce, step));
            }
        };
    }

    private static byte[] sha3(byte[] message) {
        Sha3 sha3 = new Sha3();
        byte[] txHash = new byte[Sha3.HASH_LENGTH];
        sha3.reset();
        sha3.absorb(message, 0, message.length);
        sha3.squeeze(txHash, 0, Sha3.HASH_LENGTH);
        return txHash;
    }

    private static boolean increment(byte[] bytes, int v) {
        int i;
        for (i = bytes.length - 1; i >= 0; i--) {
            v = (bytes[i] & 0xFF) + v;
            bytes[i] = (byte)v;
            v = v >> 8;
            if (v == 0) {
                return true;
            }
        }
        return false;
    }

}
