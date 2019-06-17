package net.helix.hlx;

import java.util.Random;

public class Converter {

    public static void main(String[] args) {

        Random seed = new Random();
    }

    public static String[] bytesToBitStrings(byte[] bytes) {
        String[] byteString = new String[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            String bits = String.format("%8s", Integer.toBinaryString(bytes[i] & 0xFF)).replace(' ', '0');
            byteString[i] = bits;
        }
        return byteString;
    }

    public static byte[] bitsToBytes(String[] bits){
        byte[] bytes = new byte[bits.length];
        for (int i = 0; i < bits.length; i++) {
            byte b = (byte)Integer.parseInt(bits[i]);
            bytes[i] = b;
        }
        return bytes;
    }
}
