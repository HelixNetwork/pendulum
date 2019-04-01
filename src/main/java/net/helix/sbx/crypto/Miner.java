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


public class Miner {

    private static final Logger log = LoggerFactory.getLogger(Miner.class);
    
    public boolean pow(byte[] block, int difficulty) {
        return pow(block, BigInteger.valueOf(difficulty));
    }

    public boolean pow(byte[] block, byte[] difficulty) {
        return pow(block, new BigInteger(1, difficulty));
    }

    private boolean pow(byte[] block, BigInteger difficulty) {
        BigInteger max = BigInteger.valueOf(2).pow(256);
        byte[] target = BigIntegers.asUnsignedByteArray(32, max.divide(difficulty));

        byte[] hash = sha3(block);
        byte[] nonce = new byte[32];
        byte[] concat;
        while(increment(nonce)) {
            concat = Arrays.concatenate(hash, nonce);
            byte[] result = sha3(concat);
            if(FastByteComparisons.compareTo(result, 0, 32, target, 0, 32) < 0) {
                //copy(nonce, 0, txBytes, TransactionViewModel.NONCE_OFFSET, TransactionViewModel.NONCE_SIZE);
                System.arraycopy(nonce, 0, block, TransactionViewModel.NONCE_OFFSET, 32);
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

}
