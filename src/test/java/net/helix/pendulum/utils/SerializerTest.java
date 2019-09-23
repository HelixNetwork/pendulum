package net.helix.pendulum.utils;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;


public class SerializerTest {

    @Test
    public void serializerEndianTest() {
        final int[] itestvec = {0, 1, Integer.MAX_VALUE, 123456789};
        final long[] ltestvec = {0L, 1L, Long.MAX_VALUE, 123456789L};

        for(int i : itestvec) {
            Assert.assertArrayEquals(Serializer.serialize(i), bbSerialize(i));
        }

        for(long l : ltestvec) {
            Assert.assertArrayEquals(Serializer.serialize(l), bbSerialize(l));
        }

    }
	
    public static byte[] bbSerialize(Long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        return buffer.array();
    }

    public static byte[] bbSerialize(int integer) {
        ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
        buffer.putInt(integer);
        return buffer.array();
    }

}
