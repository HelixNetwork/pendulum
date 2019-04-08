package net.helix.sbx.crypto;

import java.math.BigInteger;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.sbx.utils.FastByteComparisons;
import net.helix.sbx.controllers.TransactionViewModel;

/** Next steps:
 *  - Assert that sha3() is equivalent to sha3Alternative()
 *  - Multithreading: add a numOfThreads parameter, define a search runnable, create a different entry nonce for each worker.
 *  - Find a difficulty value that corresponds to ~2^22 operations.
 *  - Replace divepearler with Miner in API
 */

public class Miner {

    private static final Logger log = LoggerFactory.getLogger(Miner.class);
    
    public boolean pow(byte[] txBytes, int difficulty) {
        return pow(txBytes, BigInteger.valueOf(difficulty));
    }

    public boolean pow(byte[] txBytes, byte[] difficulty) {
        return pow(txBytes, new BigInteger(1, difficulty));
    }

    private boolean pow(byte[] txBytes, BigInteger difficulty) {
        BigInteger max = BigInteger.valueOf(2).pow(256);
        byte[] target = BigIntegers.asUnsignedByteArray(Sha3.HASH_LENGTH, max.divide(difficulty));

        byte[] hash = sha3(txBytes);
        byte[] nonce = new byte[TransactionViewModel.NONCE_SIZE];
        byte[] concat;
        while(increment(nonce)) {
            concat = Arrays.concatenate(hash, nonce);
            byte[] result = sha3(concat);
            if(FastByteComparisons.compareTo(result, 0, Sha3.HASH_LENGTH, target, 0, Sha3.HASH_LENGTH) < 0) {
                //copy(nonce, 0, txBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                System.arraycopy(nonce, 0, txBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                log.debug("TX_HASH: " + Hex.toHexString(result));
                log.debug("NONCE  : " + Hex.toHexString(nonce));
                return true;
            }
        }
        return false; // couldn't find a valid nonce
    }    

    private static byte[] sha3(byte[] message) {
        SHA3Digest digest = new SHA3Digest(256);
        byte[] hash = new byte[digest.getDigestSize()];
        if (message.length != 0) {
            digest.update(message, 0, message.length);
        }
        digest.doFinal(hash, 0);
        return hash;
    }

    // Validation uses this method to hash the txBytes. output of sha3() should thus be equivalent to sha3Alternative in order to get the same tx_hash in pow, validation (and client library)
    private static byte[] sha3Alternative(byte[] message) {
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
        // we return false when all bytes are 0 again
        return (i >= startIndex || bytes[startIndex] != 0);
    }

    public void cancel() {
        // do something.
    }

}
