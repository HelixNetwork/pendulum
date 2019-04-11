package net.helix.sbx.crypto;

import java.math.BigInteger;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.sbx.controllers.TransactionViewModel;
import net.helix.sbx.utils.FastByteComparisons;

/** Next steps:
 *  - (done) Assert that sha3() is equivalent to sha3Alternative()
 *  - (done: 'difficulty' is a power of 2) Find a difficulty value that corresponds to ~2^22 operations.
 *  - Multithreading: add a numOfThreads parameter, define a search runnable, create a different entry nonce for each worker.
 *  - Replace divepearler with Miner in API
 */

public class Miner {

    private static final Logger log = LoggerFactory.getLogger(Miner.class);
    
    public boolean mine(byte[] txBytes, int difficulty) {
        if (difficulty < 1 || difficulty > 255) {
            throw new IllegalArgumentException("Illegal difficulty: " + difficulty);
        }
        byte[] target = BigIntegers.asUnsignedByteArray(Sha3.HASH_LENGTH,
                BigInteger.valueOf(2).pow(256 - difficulty));

        byte[] result = txBytes.clone();
        byte[] nonce = new byte[TransactionViewModel.NONCE_SIZE];
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
    
    private static byte[] sha3(byte[] message) {
        Sha3 sha3 = new Sha3();
        byte[] txHash = new byte[Sha3.HASH_LENGTH];
        sha3.reset();
        sha3.absorb(message, 0, message.length);
        sha3.squeeze(txHash, 0, Sha3.HASH_LENGTH);
        return txHash;
    }

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

    public void cancel() {
        // do something.
    }

}
