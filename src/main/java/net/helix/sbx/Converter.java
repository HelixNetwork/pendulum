package net.helix.sbx;

import java.util.Arrays;
import java.util.Random;
import java.math.BigInteger;


public class Converter {

    public static void main(String[] args){

        Random seed = new Random();

        System.out.println("Bytes - Bits");
        System.out.println("------------------");

        // create random bytes
        byte[] bytes = new byte[32];
        seed.nextBytes(bytes);
        System.out.println("Bytes: " + Arrays.toString(bytes));

        String[] bs = bytesToBitStrings(bytes);
        System.out.println("Bytes bits: " + Arrays.toString(bs));

        byte[] b = bitsToBytes(bs);
        System.out.println("Bytes (from bits): " + Arrays.toString(bytes));

        System.out.println();


        System.out.println("Bytes - Long");
        System.out.println("------------------");

        // get long
        long l = bytesToLong(bytes, 0);
        System.out.println("Long: " + Long.toString(l));

        // get long bits
        String lb = Long.toString(l,2);
        System.out.println("Long bits: " + lb);

        System.out.println();


        System.out.println("Bytes - Long - Big Integer");
        System.out.println("-----------------------------");

        // get big int from bytes
        BigInteger bi = new BigInteger(bytes);
        System.out.println("Big Integer: " + bi);
        String s = bi.toString(2);
        System.out.println("Big Integer bits: " + s);

        // get big int from long
        BigInteger bl = BigInteger.valueOf(l);
        System.out.println("Big Integer from long: " + bl);

        // trits
        //int[] crypto = new int[243];
        //IntStream stream = Arrays.stream(crypto);
        //int[] trits = stream.map(i -> seed.nextInt(3)-1).toArray();
        //byte[] dest = new byte[49];
        //Converter.bytes(trits, dest);
        //Hash h = new Hash(dest);
        //System.out.println(Arrays.toString(trits));
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