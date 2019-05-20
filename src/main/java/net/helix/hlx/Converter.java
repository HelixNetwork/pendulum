package net.helix.hlx;

import java.util.Random;

public class Converter {

    public static void main(String[] args) {

        Random seed = new Random();
    }

    public static String[] bytesToBitStrings(byte[] bytes) {
        String[] bytestring = new String[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            String bits = String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replace(' ', '0');
            bytestring[i] = bits;
        }
        return bytestring;
    }

    public static byte[] bitsToBytes(String[] bits){
        byte[] bytes = new byte[bits.length];
        for (int i = 0; i < bits.length; i++) {
            byte b = (byte)Integer.parseInt(bits[i]);
            bytes[i] = b;
        }
        return bytes;
    }

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
}
