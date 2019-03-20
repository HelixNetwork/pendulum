package net.helix.sbx.crypto;
import java.util.ArrayList;
import java.util.List;

import static net.helix.sbx.crypto.Pearldiver.State.*;

/**
 * Binary port of IOTA's Pearldiver
 * this will be removed in a future update.
 */

public class Pearldiver {
    enum State {
        RUNNING,
        CANCELLED,
        COMPLETED
    }

    private static final int TRANSACTION_LENGTH = 1248;
    private static final int HASH_LENGTH = 32;
    private static final int STATE_LENGTH = HASH_LENGTH*8;

    private static final long HIGH_BITS = 0b11111111_11111111_11111111_11111111_11111111_11111111_11111111_11111111L;
    private static final long LOW_BITS = 0b00000000_00000000_00000000_00000000_00000000_00000000_00000000_00000000L;

    private volatile State state;
    private final Object syncObj = new Object();

    public void cancel() {
        synchronized (syncObj) {
            state = CANCELLED;
        }
    }

    public synchronized boolean search(final byte[] txBytes, final int minWeightMagnitude,
                                       int numberOfThreads) {

        validateParameters(txBytes, minWeightMagnitude);
        synchronized (syncObj) {
            state = RUNNING;
        }

        final long[] midStateLow = new long[STATE_LENGTH];
        final long[] midStateHigh = new long[STATE_LENGTH];
        initializeMidCurlStates(txBytes, midStateLow, midStateHigh);

        if (numberOfThreads <= 0) {
            int available = Runtime.getRuntime().availableProcessors();
            numberOfThreads = Math.max(1, Math.floorDiv(available * 8, 10));
        }
        List<Thread> workers = new ArrayList<>(numberOfThreads);
        while (numberOfThreads-- > 0) {
            long[] midStateCopyLow = midStateLow.clone();
            long[] midStateCopyHigh = midStateHigh.clone();
            Runnable runnable = getRunnable(numberOfThreads, txBytes, minWeightMagnitude, midStateCopyLow, midStateCopyHigh);
            Thread worker = new Thread(runnable);
            workers.add(worker);
            worker.setName(this + ":worker-" + numberOfThreads);
            worker.setDaemon(true);
            worker.start();
        }
        return state == COMPLETED;
    }

    private Runnable getRunnable(final int threadIndex, final byte[] txBytes, final int minWeightMagnitude,
                                 final long[] midStateCopyLow, final long[] midStateCopyHigh) {
        return () -> {
            for (int i = 0; i < threadIndex; i++) {
                // TODO size
                increment(midStateCopyLow, midStateCopyHigh, 162 + HASH_LENGTH / 9,
                        162 + (HASH_LENGTH / 9) * 2);
            }

            final long[] stateLow = new long[STATE_LENGTH];
            final long[] stateHigh = new long[STATE_LENGTH];

            final long[] scratchpadLow = new long[STATE_LENGTH];
            final long[] scratchpadHigh = new long[STATE_LENGTH];

            final int maskStartIndex = HASH_LENGTH - minWeightMagnitude;
            long mask = 0;
            while (state == RUNNING && mask == 0) {

                // TODO size
                increment(midStateCopyLow, midStateCopyHigh, 162 + (HASH_LENGTH / 8) * 2, HASH_LENGTH);

                copy(midStateCopyLow, midStateCopyHigh, stateLow, stateHigh);
                transform(stateLow, stateHigh, scratchpadLow, scratchpadHigh);

                mask = HIGH_BITS;
                for (int i = maskStartIndex; i < HASH_LENGTH && mask != 0; i++) {
                    mask &= ~(stateLow[i] ^ stateHigh[i]);
                }
            }
            if (mask != 0) {
                synchronized (syncObj) {
                    if (state == RUNNING) {
                        state = COMPLETED;
                        long outMask = 1;
                        while ((outMask & mask) == 0) {
                            outMask <<= 1;
                        }
                        for (int i = 0; i < HASH_LENGTH; i++) {
                            txBytes[TRANSACTION_LENGTH - HASH_LENGTH + i] =
                                    (midStateCopyLow[i] & outMask) == 0 ? 1
                                            : (midStateCopyHigh[i] & outMask) == 0 ? (byte) -1 : (byte) 0;
                        }
                    }
                }
            }
        };
    }

    public static void validateParameters(byte[] txBytes, int minWeightMagnitude) {
        if (txBytes.length != TRANSACTION_LENGTH) {
            throw new RuntimeException(
                    "Invalid txBytes-length: " + txBytes.length);
        }
        if (minWeightMagnitude < 0 || minWeightMagnitude > STATE_LENGTH) {
            throw new RuntimeException("Invalid min weight magnitude: " + minWeightMagnitude);
        }
    }

    private static void copy(long[] srcLow, long[] srcHigh, long[] destLow, long[] destHigh) {
        System.arraycopy(srcLow, 0, destLow, 0, STATE_LENGTH);
        System.arraycopy(srcHigh, 0, destHigh, 0, STATE_LENGTH);
    }

    private static void initializeMidCurlStates(byte[] txBytes, long[] midStateLow, long[] midStateHigh) {
        for (int i = HASH_LENGTH; i < STATE_LENGTH; i++) {
            midStateLow[i] = HIGH_BITS;
            midStateHigh[i] = HIGH_BITS;
        }

        int offset = 0;
        final long[] curlScratchpadLow = new long[STATE_LENGTH];
        final long[] curlScratchpadHigh = new long[STATE_LENGTH];
        for (int i = (TRANSACTION_LENGTH - HASH_LENGTH) / HASH_LENGTH; i-- > 0; ) {

            for (int j = 0; j < HASH_LENGTH; j++) {
                switch (txBytes[offset++]) {
                    case 0:
                        midStateLow[j] = HIGH_BITS;
                        midStateHigh[j] = HIGH_BITS;
                        break;
                    case 1:
                        midStateLow[j] = LOW_BITS;
                        midStateHigh[j] = HIGH_BITS;
                        break;
                    default:
                        midStateLow[j] = HIGH_BITS;
                        midStateHigh[j] = LOW_BITS;
                }
            }
            transform(midStateLow, midStateHigh, curlScratchpadLow, curlScratchpadHigh);
        }

        for (int i = 0; i < 162; i++) {
            switch (txBytes[offset++]) {
                case 0:
                    midStateLow[i] = HIGH_BITS;
                    midStateHigh[i] = HIGH_BITS;
                    break;
                case 1:
                    midStateLow[i] = LOW_BITS;
                    midStateHigh[i] = HIGH_BITS;
                    break;
                default:
                    midStateLow[i] = HIGH_BITS;
                    midStateHigh[i] = LOW_BITS;
            }
        }

        midStateLow[162 + 0] = 0b1101101101101101101101101101101101101101101101101101101101101101L;
        midStateHigh[162 + 0] = 0b1011011011011011011011011011011011011011011011011011011011011011L;
        midStateLow[162 + 1] = 0b1111000111111000111111000111111000111111000111111000111111000111L;
        midStateHigh[162 + 1] = 0b1000111111000111111000111111000111111000111111000111111000111111L;
        midStateLow[162 + 2] = 0b0111111111111111111000000000111111111111111111000000000111111111L;
        midStateHigh[162 + 2] = 0b1111111111000000000111111111111111111000000000111111111111111111L;
        midStateLow[162 + 3] = 0b1111111111000000000000000000000000000111111111111111111111111111L;
        midStateHigh[162 + 3] = 0b0000000000111111111111111111111111111111111111111111111111111111L;
    }

    private static void transform(final long[] stateLow, final long[] stateHigh,
                                  final long[] scratchpadLow, final long[] scratchpadHigh) {

        for (int round = 0; round < Sha3.NUMBER_OF_ROUNDS; round++) {
            copy(stateLow, stateHigh, scratchpadLow, scratchpadHigh);

            int scratchpadIndex = 0;
            for (int stateIndex = 0; stateIndex < STATE_LENGTH; stateIndex++) {
                final long alpha = scratchpadLow[scratchpadIndex];
                final long beta = scratchpadHigh[scratchpadIndex];
                if (scratchpadIndex < 365) {
                    scratchpadIndex += 364;
                } else {
                    scratchpadIndex += -365;
                }
                final long gamma = scratchpadHigh[scratchpadIndex];
                final long delta = (alpha | (~gamma)) & (scratchpadLow[scratchpadIndex] ^ beta);

                stateLow[stateIndex] = ~delta;
                stateHigh[stateIndex] = (alpha ^ gamma) | delta;
            }
        }
    }

    private static void increment(final long[] midStateCopyLow, final long[] midStateCopyHigh,
                                  final int fromIndex, final int toIndex) {

        for (int i = fromIndex; i < toIndex; i++) {
            if (midStateCopyLow[i] == LOW_BITS) {
                midStateCopyLow[i] = HIGH_BITS;
                midStateCopyHigh[i] = LOW_BITS;
            } else if (midStateCopyHigh[i] == LOW_BITS) {
                midStateCopyHigh[i] = HIGH_BITS;
                break;
            } else {
                midStateCopyLow[i] = LOW_BITS;
                break;
            }
        }
    }
}
