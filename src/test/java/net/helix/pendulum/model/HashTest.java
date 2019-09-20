package net.helix.pendulum.model;

import java.util.Arrays;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import static net.helix.pendulum.TransactionTestUtils.getTransactionBytes;

public class HashTest {
    
    @Test
    public void calculateTest() throws Exception {
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, getTransactionBytes());
        Assert.assertNotEquals(0, hash.hashCode());
        Assert.assertNotEquals(null, hash.bytes());
    }

    @Test
    public void calculateTest1() throws Exception {
        Hash hash = TransactionHash.calculate(getTransactionBytes(), 0, 128, SpongeFactory.create(SpongeFactory.Mode.S256));
        Assert.assertNotEquals(null, hash.bytes());
        Assert.assertNotEquals(0, hash.hashCode());
    }

    @Test
    public void calculateTest2() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionHash hash = TransactionHash.calculate(bytes, 0, TransactionViewModel.ADDRESS_SIZE, SpongeFactory.create(SpongeFactory.Mode.S256));
        Assert.assertNotEquals(0, hash.hashCode());
        Assert.assertNotEquals(null, hash.bytes());
    }

    @Test
    public void calculateAllZerosTest() throws Exception {
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, new byte[TransactionViewModel.SIZE]);
        Assert.assertEquals(TransactionHash.NULL_HASH, hash);
    }

    @Test
    public void trailingZerosTest() throws Exception {
        Hash hash = TransactionHash.NULL_HASH;
        Assert.assertEquals(TransactionHash.SIZE_IN_BYTES, hash.trailingZeros());
    }
    @Test
    public void leadingZerosTest() throws Exception {
        Hash hash = TransactionHash.NULL_HASH;
        Assert.assertEquals(Hash.SIZE_IN_BYTES, hash.leadingZeros());
    }

    @Test
    public void bytesTest() throws Exception {
        TransactionHash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, getTransactionBytes());
        Assert.assertFalse(Arrays.equals(new byte[Hash.SIZE_IN_BYTES], hash.bytes()));
    }

    @Test
    public void equalsTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionHash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, bytes);
        TransactionHash hash1 = TransactionHash.calculate(SpongeFactory.Mode.S256, bytes);
        Assert.assertTrue(hash.equals(hash1));
        Assert.assertFalse(hash.equals(Hash.NULL_HASH));
        Assert.assertFalse(hash.equals(TransactionHash.calculate(SpongeFactory.Mode.S256, getTransactionBytes())));
    }

    @Test
    public void hashCodeTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionHash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, bytes);
        Assert.assertNotEquals(hash.hashCode(), 0);
        
        Assert.assertNotEquals(Hash.NULL_HASH.hashCode(), 0);
    }

    @Test
    public void toHexStringTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionHash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, bytes);
        Assert.assertEquals(Hex.toHexString(Hash.NULL_HASH.bytes()), "0000000000000000000000000000000000000000000000000000000000000000");
        Assert.assertNotEquals(Hex.toHexString(hash.bytes()), "0000000000000000000000000000000000000000000000000000000000000000");
        Assert.assertNotEquals(Hex.toHexString(hash.bytes()).length(), 0);
    }

    @Test
    public void toStringTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        Hash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, bytes);
        Assert.assertEquals(Hash.NULL_HASH.toString(), "0000000000000000000000000000000000000000000000000000000000000000");
        Assert.assertNotEquals(hash.toString(), "0000000000000000000000000000000000000000000000000000000000000000");
        Assert.assertNotEquals(hash.toString().length(), 0);
    }

    @Test
    public void txBytesTest() throws Exception {
        byte[] bytes = getTransactionBytes();
        TransactionHash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, bytes);
        Assert.assertTrue(Arrays.equals(new byte[Hash.SIZE_IN_BYTES], Hash.NULL_HASH.bytes()));
        Assert.assertFalse(Arrays.equals(new byte[Hash.SIZE_IN_BYTES], hash.bytes()));
        Assert.assertNotEquals(0, hash.bytes().length);
    }

    @Test
    public void compareToTest() throws Exception {
        byte[] randomTransactionBytes = getTransactionBytes();
        TransactionHash hash = TransactionHash.calculate(SpongeFactory.Mode.S256, randomTransactionBytes);
        Assert.assertEquals(hash.compareTo(Hash.NULL_HASH), -Hash.NULL_HASH.compareTo(hash));
    }

}
