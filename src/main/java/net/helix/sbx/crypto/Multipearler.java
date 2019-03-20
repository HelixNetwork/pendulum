package net.helix.sbx.crypto;

import java.util.ArrayList;
import java.util.Arrays;

import net.helix.sbx.controllers.TransactionViewModel;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.SecureRandom;
import java.util.List;

import static net.helix.sbx.crypto.Multipearler.State.*;


public class Multipearler {
    private static final Logger log = LoggerFactory.getLogger(Multipearler.class);
    enum State {
        RUNNING,
        CANCELLED,
        COMPLETED
    }

    private volatile State state;
    private final Object syncObj = new Object();

    private static final int TRANSACTION_LENGTH = 1248;
    private static final int HASH_LENGTH = 32;
    SecureRandom rand = new SecureRandom();
    Sha3 sha3 = new Sha3();

    public synchronized boolean dive(final byte[] txBytes, int minWeightMagnitude, int numOfThreads) {
        synchronized (syncObj) {
            state = RUNNING;
        }
        if (numOfThreads <= 0) {
            int available = Runtime.getRuntime().availableProcessors();
            numOfThreads = Math.max(1, Math.floorDiv(available * 8, 10));
        }

        log.info("MWM set to: " + minWeightMagnitude);

        byte[] initialStates  = new byte[minWeightMagnitude];
        rand.nextBytes(initialStates);
        log.info("Random initial states: " + Hex.toHexString(initialStates));
        //Sha3 sha3 = new Sha3();

        List<Thread> workers = new ArrayList<>(numOfThreads);
        while (numOfThreads-- > 0) {
            byte[] initialStatesCopy = initialStates.clone();
            Runnable runnable = getRunnable(numOfThreads, txBytes, minWeightMagnitude, initialStatesCopy);
            Thread worker = new Thread(runnable);
            workers.add(worker);
            worker.setName(this + ":worker-" + numOfThreads);
            worker.setDaemon(true);
            worker.start();
        }
        return state == COMPLETED;

    }
    private Runnable getRunnable(final int threadIndex, final byte[] txBytes, final int minWeightMagnitude, byte[] initialStates) {
        return () -> {
            for (int i = 0; i < threadIndex; i++) {
                this.newInitialState(initialStates);
            }
            byte[] targetZeroes = new byte[minWeightMagnitude];
            byte[] leadingZeroes;
            byte[] nonce = new byte[8];
            byte[] pearl = txBytes.clone();
            byte[] txHash = new byte[Sha3.HASH_LENGTH];

            //sha3.reset();

            log.info("Diving...");
            while (state == RUNNING) {
                rand.nextBytes(nonce);
                System.arraycopy(nonce, 0, pearl, TransactionViewModel.NONCE_OFFSET,8);

                //sha3.absorb(pearl, 0, pearl.length);
                //sha3.squeeze(txHash, 0, Sha3.HASH_LENGTH);

                //for ()

                leadingZeroes = Arrays.copyOfRange(txHash, 0, minWeightMagnitude);
                log.info("txHash:  " + Hex.toHexString(txHash));
                log.info("nonce:   " + Hex.toHexString(nonce));

                //sha3.reset();

                // H ( txBytes | nonce )  ==  0000....
                if (Arrays.equals(leadingZeroes, targetZeroes)) {
                    log.info("Found a pearl (o)! The nonce is: " + Hex.toHexString(nonce));
                    synchronized (syncObj) {
                        if (state == RUNNING) {
                            System.arraycopy(nonce,0, txBytes, TransactionViewModel.NONCE_OFFSET,8);
                            state = COMPLETED;
                            log.info("Found pearl in thread: " + threadIndex);
                        }
                    }
                }
            }

        };
    }
    private void newInitialState(byte[] initialStates) { rand.nextBytes(initialStates); }

    public void cancel() {
        synchronized (syncObj) {
            state = CANCELLED;
        }
    }
}


