package net.helix.hlx.model.persistables;

import java.nio.ByteBuffer;
import net.helix.hlx.TransactionTestUtils;
import net.helix.hlx.model.Hash;
import org.junit.Assert;
import org.junit.Test;
import static org.hamcrest.Matchers.hasItem;


public class RoundTest {

    @Test
    public void writeAndReadTest() {
        int hashCount = 3;
        int index = Integer.MAX_VALUE;
        Hash[] hashes = new Hash[hashCount];

        byte[] dataIN = new byte[Integer.BYTES + Hash.SIZE_IN_BYTES * hashCount + hashCount - 1];
        ByteBuffer b = ByteBuffer.wrap(dataIN);
        b.putInt(index);
        for (int i = 0; i < hashCount; i++) {
            if (i > 0) {
                b.put(Hashes.delimiter);
            }
            hashes[i] = TransactionTestUtils.getTransactionHash();
            b.put(hashes[i].bytes());
        }
        
        Round round = new Round();
        round.read(dataIN);
        Assert.assertNotNull(round.index);
        Assert.assertEquals(index, round.index.getValue());
        Assert.assertEquals(hashCount, round.set.size());
        for (int i = 0; i < hashCount; i++) {
            Assert.assertThat(round.set, hasItem(hashes[i]));
        }

        byte[] dataOUT = round.bytes();
        Assert.assertArrayEquals(dataIN, dataOUT);
    }

}
