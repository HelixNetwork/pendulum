package net.helix.hlx.crypto;

import java.util.Random;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Test;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.hlx.controllers.TransactionViewModel;


public class GreedyMinerTest {
    
    private static final Logger log = LoggerFactory.getLogger(GreedyMinerTest.class);
    private static final Random RND = new Random();


    @Test(expected=IllegalArgumentException.class)
    public void invalidDifficulty0Test() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        int difficulty = 0;
        GreedyMiner miner = new GreedyMiner();
        miner.mine(txBytes, difficulty, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidDifficulty32Test() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        int difficulty = 32;
        GreedyMiner miner = new GreedyMiner();
        miner.mine(txBytes, difficulty, 1);
    }

    @Test(expected=IllegalArgumentException.class)
    public void invalidByteNullTest() {
        byte[] txBytes = null;
        int difficulty = 1;
        GreedyMiner miner = new GreedyMiner();
        miner.mine(txBytes, difficulty, 1);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void invalidByteLengthTest() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE - RND.nextInt(TransactionViewModel.SIZE) + 1];
        int difficulty = 1;
        log.debug("invalidByteLengthTest: txBytes.length=" + txBytes.length);
        GreedyMiner miner = new GreedyMiner();
        miner.mine(txBytes, difficulty, 1);
    }
    
    @Test
    public void getHashForRandomBytesTest() {
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(txBytes);
        int difficulty = 2;
        int threadCount = 4;
        GreedyMiner miner = new GreedyMiner();
        boolean result = miner.mine(txBytes, difficulty, threadCount);
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
            log.debug("getHashForRandomBytesTest: difficulty=" + difficulty + " hash=" + Hex.toHexString(hash));
            Assert.assertTrue("expectedZeros=" + difficulty + " zeros=" + zeros, difficulty <= zeros);
        }
    }

    @Test
    public void noRandomFailTest() {
        boolean[] result = {true};
        byte[] txBytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(txBytes);
        int difficulty = 16;
        int threadCount = 1;
        GreedyMiner miner = new GreedyMiner();
        Thread minerThread = new Thread(() -> {
            result[0] = miner.mine(txBytes, difficulty, threadCount);
        });
        minerThread.setName("miner#noRandomFailTest");
        minerThread.setDaemon(true);
        minerThread.start();
        try {
            Thread.sleep(1000);
            miner.cancel();
            minerThread.join(1000);
        } catch (InterruptedException ex) {
        }
        Assert.assertFalse(result[0]);
    }
    

    
}
