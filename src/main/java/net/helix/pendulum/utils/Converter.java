package net.helix.pendulum.utils;

public class Converter {

	public static final int RADIX = 2;

    public static long bytesToLong(byte[] array, int offset) {
        return
                ((long)(array[offset]   & 0xff) << 56) |
                ((long)(array[offset+1] & 0xff) << 48) |
                ((long)(array[offset+2] & 0xff) << 40) |
                ((long)(array[offset+3] & 0xff) << 32) |
                ((long)(array[offset+4] & 0xff) << 24) |
                ((long)(array[offset+5] & 0xff) << 16) |
                ((long)(array[offset+6] & 0xff) << 8) |
                ((long)(array[offset+7] & 0xff));
    }

    public static long longValue(final int[] bytes, final int srcPos, final int size) {

        long value = 0;
        for (int i = size; i-- > 0; ) {
            value = value * RADIX + bytes[srcPos + i];
        }
        return value;
    }
}
