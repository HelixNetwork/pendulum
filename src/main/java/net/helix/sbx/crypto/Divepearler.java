package net.helix.sbx.crypto;

import java.util.ArrayList;
import java.util.Arrays;

import net.helix.sbx.controllers.TransactionViewModel;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.List;

import static net.helix.sbx.crypto.Divepearler.State.*;

/**
 * This class only serves as a preliminary pow simulator, so long as the actual pow engine is in R&D.
**/

public class Divepearler {
    private static final Logger log = LoggerFactory.getLogger(Divepearler.class);
    enum State {
        RUNNING,
        CANCELLED,
        COMPLETED
    }

    private volatile State state;
    private final Object syncObj = new Object();

    private static final int TRANSACTION_LENGTH = 768;
    private static final int HASH_LENGTH = 32;
    SecureRandom rand = new SecureRandom();

    public synchronized boolean dive(final byte[] txBytes, int minWeightMagnitude, int numOfThreads) {
        synchronized (syncObj) {
            state = RUNNING;
        }
        if (numOfThreads <= 0) {
            int available = Runtime.getRuntime().availableProcessors();
            numOfThreads = Math.max(1, Math.floorDiv(available * 8, 10));
        }
        log.debug("MWM=" + minWeightMagnitude + " -- #ofThreads=" + numOfThreads);
        log.debug("POW_STATE: " + state);
        List<Thread> divers = new ArrayList<>(numOfThreads);
        while (numOfThreads-- > 0) {
            Runnable runnable = getRunnable(numOfThreads, txBytes, minWeightMagnitude);
            Thread diver = new Thread(runnable);
            divers.add(diver);
            diver.setName(":diver#" + numOfThreads);
            diver.setDaemon(true);
            diver.start();
        }
        for (Thread diver : divers) {
            try {
                diver.join();
            } catch (InterruptedException e) {
                synchronized (syncObj) {
                    state = CANCELLED;
                }
            }
        }
        log.info("POW_STATE: " + state);
        return state == COMPLETED;
    }

    private Runnable getRunnable(final int threadIndex, final byte[] txBytes, final int minWeightMagnitude) {
        return () -> {

            byte[] initialStates = new byte[minWeightMagnitude];
            rand.nextBytes(initialStates);
            byte[] targetZeroes = new byte[minWeightMagnitude];
            byte[] leadingZeroes = initialStates;
            byte[] nonce = new byte[TransactionViewModel.NONCE_SIZE];

            // with a newly generated nonce allocated at the according offset in each attempt.
            byte[] clonedTxBytes = txBytes.clone();
            byte[] txHash;

            while (state == RUNNING) {
                rand.nextBytes(nonce);
                copy(nonce, 0, clonedTxBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE); // add nonce to txbytes
                txHash = nextTry(clonedTxBytes);
                leadingZeroes = copyOfRange(txHash, 0, minWeightMagnitude);

                // H ( txBytes | nonce ).leadingZeroes()  ==  minWeightMagnitude * '0'
                // only temp.
                if (equals(leadingZeroes, targetZeroes)) {
                    synchronized (syncObj) {
                        if (state == RUNNING) {
                            state = COMPLETED;
                            copy(nonce, 0, txBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                        }
                    }
                    log.debug("TX_HASH: " + Hex.toHexString(txHash));
                    log.debug("NONCE  : " + Hex.toHexString(nonce));

                }
            }
        };
    }

    public void copy(byte[] source, int pos, byte[] destination, int destpos, int length) {
        System.arraycopy(source, pos, destination, destpos, length);
    }

    private byte[] copyOfRange(byte[] a, int from, int to) {
        return Arrays.copyOfRange(a, from, to);
    }

    public boolean equals(byte[] a, byte[] b) {
        return Arrays.equals(a, b);
    }

    private byte[] nextTry(byte[] clonedTxBytes) {
        Sha3 sha3 = new Sha3();
        byte[] txHash = new byte[Sha3.HASH_LENGTH];
        sha3.reset();
        sha3.absorb(clonedTxBytes, 0, clonedTxBytes.length);
        sha3.squeeze(txHash, 0, Sha3.HASH_LENGTH);
        return txHash;
    }

    public void cancel() {
        synchronized (syncObj) {
            state = CANCELLED;
        }
    }
}

