package net.helix.pendulum.utils;

import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Date: 2019-10-16
 * Author: zhelezov
 */
public class PendulumUtilsTest {

    @Test
    public void logHashListTest() {
        List<Hash> hashes = new ArrayList<Hash>();
        byte[] first = Hex.decode("d6ea8f9a1f22e1298e5a9506bd066f23cc56001f5d36582344a628649df53ae8");
        byte[] second = Hex.decode("0000000000000000000000000000000000000000000000000000000000000000000000");
        hashes.add(HashFactory.TRANSACTION.create("d6ea8f9a1f22e1298e5a9506bd066f23cc56001f5d36582344a628649df53ae8"));
        hashes.add(HashFactory.TRANSACTION.create("0000000000000000000000000000000000000000000000000000000000000000000000"));

        String out = PendulumUtils.logHashList(hashes, 4);
        Assert.assertEquals(out, "3ae8, 0000");
    }
}
