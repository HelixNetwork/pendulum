package net.helix.pendulum.model.persistables;

import java.nio.ByteBuffer;
import net.helix.pendulum.TransactionTestUtils;
import net.helix.pendulum.model.Hash;
import org.junit.Assert;
import org.junit.Test;
import static org.hamcrest.Matchers.hasItem;


public class HashesTest {

    @Test
    public void writeAndReadTest() {
        int hashCount = 5;
        Hash[] hashes = new Hash[hashCount];
        byte[] dataIN = new byte[Hash.SIZE_IN_BYTES * hashCount + hashCount - 1];
        ByteBuffer b = ByteBuffer.wrap(dataIN);
        for (int i = 0; i < hashCount; i++) {
            if (i > 0) {
                b.put(Hashes.delimiter);
            }
            hashes[i] = TransactionTestUtils.getTransactionHash();
            b.put(hashes[i].bytes());
        }
        
        Hashes h = new Hashes();
        h.read(dataIN);

        Assert.assertEquals(hashCount, h.set.size());
        for (int i = 0; i < hashCount; i++) {
            Assert.assertThat(h.set, hasItem(hashes[i]));
        }

        byte[] dataOUT = h.bytes();
        Assert.assertArrayEquals(dataIN, dataOUT);
    }

}
