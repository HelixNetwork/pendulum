package net.helix.hlx.crypto;

import java.util.Arrays;
import java.util.Random;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.hlx.controllers.TransactionViewModel;


public class MinerTest {

    private static final Logger log = LoggerFactory.getLogger(MinerTest.class);
    private static final Random RND = new Random();


    @Test(expected=IllegalArgumentException.class)
    public void invalidDifficulty0Test() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        int difficulty = 0;
        Miner miner = new Miner();
        miner.mine(txBytes, difficulty);
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidDifficulty256Test() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        int difficulty = 256;
        Miner miner = new Miner();
        miner.mine(txBytes, difficulty);
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidByteNullTest() {
        byte[] txBytes = null;
        int difficulty = 1;
        Miner miner = new Miner();
        miner.mine(txBytes, difficulty);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void invalidByteLengthTest() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE - RND.nextInt(TransactionViewModel.SIZE) + 1];
        int difficulty = 1;
        log.debug("invalidByteLengthTest: txBytes.length=" + txBytes.length);
        Miner miner = new Miner();
        miner.mine(txBytes, difficulty);
    }
    
    @Test
    public void getHashForRandomBytesTest() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(txBytes);
        int difficulty = 16;
        Miner miner = new Miner();
        boolean result = miner.mine(txBytes, difficulty);
        if (result) {
            Sha3 sha3 = new Sha3();
            byte[] hash = new byte[Sha3.HASH_LENGTH];
            sha3.reset();
            sha3.absorb(txBytes, 0, txBytes.length);
            sha3.squeeze(hash, 0, Sha3.HASH_LENGTH);
            int zeros = 0;
            while (zeros < hash.length && hash[zeros] == 0) {
                zeros++;
            }
            log.debug("getHashForRandomBytesTest: difficulty=" + difficulty
                    + " hash=" + Hex.toHexString(hash));
            Assert.assertTrue("expectedZeros=" + difficulty / Byte.SIZE + " zeros=" + zeros,
                    difficulty / Byte.SIZE <= zeros);
        }
    }

    @Test
    public void noRandomFailTest() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(txBytes);
        int difficulty = 16;
        byte[] nonce = new byte[TransactionViewModel.NONCE_SIZE];
        Arrays.fill(nonce, (byte)255);
        Miner miner = new Miner();
        boolean result = miner.mine(txBytes, difficulty, nonce);
        Assert.assertFalse(result);
    }
    
}
