package net.helix.sbx.crypto;

import net.helix.sbx.model.Hash;
import net.helix.sbx.utils.Serializer;
import java.math.BigInteger;
import java.util.Arrays;


public class Winternitz {

    public static final int w = 8;
    public static final int rounds = (int) Math.pow(2,w) -1;
    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 16;
    public static final int FRAGMENT_LENGTH = Sha3.HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    private static final int NUMBER_OF_SECURITY_LEVELS = 2;
    public static final int NORMALIZED_FRAGMENT_LENGTH = Sha3.HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS;
    private static final byte MIN_BYTE_VALUE = -128, MAX_BYTE_VALUE = 127;

    /**
     * Generate subseed by adding seed and index.
     * @param mode hashing mode
     * @param seed privately generated random
     * @param index subseed index
     * @return <code> byte[] </code> subseed
     */
    public static byte[] subseed(SpongeFactory.Mode mode, final byte[] seed, int index) {
        if (index < 0) {
            throw new RuntimeException("Invalid subseed index: " + index);
        }
        byte[] indexInBytes = Serializer.serialize(index);
        final byte[] subseedPreimage = new BigInteger(seed).add(new BigInteger(indexInBytes)).toByteArray();
        final byte[] subseed = new byte[Sha3.HASH_LENGTH];
        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(subseedPreimage, 0, subseedPreimage.length);
        hash.squeeze(subseed, 0, subseed.length);
        return subseed;
    }

    /**
     * Generate private key.
     * @param mode hashing mode
     * @param subseed subseed
     * @param numberOfFragments security level (1,2 or 3)
     * @return <code> byte[] </code> private key
     */
    public static byte[] key(SpongeFactory.Mode mode, final byte[] subseed, final int numberOfFragments) {
        if (subseed.length != Sha3.HASH_LENGTH) {
            throw new RuntimeException("Invalid subseed length: " + subseed.length);
        }
        if (numberOfFragments <= 0) {
            throw new RuntimeException("Invalid number of key fragments: " + numberOfFragments);
        }
        final byte[] key = new byte[FRAGMENT_LENGTH * numberOfFragments];
        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(key, 0, key.length);
        return key;
    }

    /**
     * Generate public key.
     * @param mode hashing mode
     * @param key private key
     * @return <code> byte[] </code> public key
     */
    public static byte[] digests(SpongeFactory.Mode mode, final byte[] key) {
        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {
            throw new RuntimeException("Invalid private key length: " + key.length);
        }
        final byte[] digests = new byte[key.length / FRAGMENT_LENGTH * Sha3.HASH_LENGTH];
        final Sponge hash = SpongeFactory.create(mode);

        for (int i = 0; i < key.length / FRAGMENT_LENGTH; i++) {

            final byte[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

                for (int k = rounds; k-- > 0; ) {
                    hash.reset();
                    hash.absorb(buffer, j * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
                    hash.squeeze(buffer, j * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
                }
            }
            hash.reset();
            hash.absorb(buffer, 0, buffer.length);
            hash.squeeze(digests, i * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
        }
        return digests;
    }

    /**
     * Generate address.
     * @param mode hashing mode
     * @param digests public key
     * @return <code> byte[] </code> address
     */
    public static byte[] address(SpongeFactory.Mode mode, final byte[] digests){
        if (digests.length == 0 || digests.length % Sha3.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid public key length: " + digests.length);
        }
        final byte[] address = new byte[Sha3.HASH_LENGTH];
        final Sponge hash = SpongeFactory.create(mode);
        hash.absorb(digests, 0, digests.length);
        hash.squeeze(address, 0, address.length);
        return address;
    }

    /**
     * Sign bundle.
     * @param  bundleFragment bundle fragment
     * @param keyFragment private key fragment
     * @return <code> byte[] </code> signature fragment
     */
    public static byte[] signatureFragment(SpongeFactory.Mode mode, final byte[] bundleFragment, final byte[] keyFragment) {
        if (bundleFragment.length != NORMALIZED_FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid bundle fragment length: " + bundleFragment.length);
        }
        if (keyFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid key fragment length: " + keyFragment.length);
        }
        final byte[] signatureFragment = Arrays.copyOf(keyFragment, keyFragment.length);
        final Sponge hash = SpongeFactory.create(mode);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {
            for (int k = (bundleFragment[j] < 0) ? rounds - (bundleFragment[j] + 256) : rounds - bundleFragment[j]; k-- > 0; ) {
                hash.reset();
                hash.absorb(signatureFragment, j * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
                hash.squeeze(signatureFragment, j * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
            }
        }
        return signatureFragment;
    }

    /**
     * Verify bundle.
     * @param  bundleFragment bundle fragment
     * @param signatureFragment signature fragement
     * @return <code> byte[] </code> public key
     */
    public static byte[] digest(SpongeFactory.Mode mode, final byte[] bundleFragment, final byte[] signatureFragment) {
        if (bundleFragment.length != NORMALIZED_FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid bundle fragment length: " + bundleFragment.length);
        }
        if (signatureFragment.length != FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid signature fragment length: " + signatureFragment.length);
        }
        final byte[] digest = new byte[Sha3.HASH_LENGTH];
        final byte[] buffer = Arrays.copyOfRange(signatureFragment, 0, FRAGMENT_LENGTH);
        final Sponge hash = SpongeFactory.create(mode);

        for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = (bundleFragment[j] < 0) ? bundleFragment[j] + 256 : bundleFragment[j]; k-- > 0; ) {
                hash.reset();
                hash.absorb(buffer, j * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
                hash.squeeze(buffer, j * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
            }
        }
        hash.reset();
        hash.absorb(buffer, 0, buffer.length);
        hash.squeeze(digest, 0, digest.length);
        return digest;
    }

    /**
     * Deterministically Normalize the bundle hash. <br>
     *     <ol>
     *         <li>map each byte in {@code bundle} to base 16 {@code [-13 , 13]} </li>
     *         <li>sum all mapped hbytes together</li>
     *         <li>if sum != 0, start inc/dec each byte till sum equals 0</li>
     *     </ol>
     *
     * @param bundle hash to be normalized
     * @return normalized hash
     */
    public static byte[] normalizedBundle(final byte[] bundle) {

        if (bundle.length != Sha3.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }

        final byte[] normalizedBundle = new byte[Sha3.HASH_LENGTH / w];

        normalizedBundle(bundle, normalizedBundle);
        return normalizedBundle;
    }

    /**
     * Normalize Bundle.
     * @param  bundle bundle hash
     * @param normalizedBundle normalized bundle
     */
    public static void normalizedBundle(final byte[] bundle, byte[] normalizedBundle) {
        if (bundle.length != Sha3.HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }

        for (int i = 0; i < NUMBER_OF_SECURITY_LEVELS; i++) {
            int sum = 0;
            for (int j = i * (Sha3.HASH_LENGTH  / NUMBER_OF_SECURITY_LEVELS); j < (i + 1) * (Sha3.HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS); j++) {
                normalizedBundle[j] = bundle[j];
                sum += normalizedBundle[j];
            }

            if (sum > 0) {
                while (sum-- > 0) {

;                    for (int j = i * (Sha3.HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS); j < (i + 1) * (Sha3.HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                        if (normalizedBundle[j] > MIN_BYTE_VALUE) {
                            normalizedBundle[j]--;
                            break;
                        }
                    }
                }
            } else {
                while (sum++ < 0) {
                    for (int j = i * (Sha3.HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS); j < (i + 1) * (Sha3.HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS); j++) {

                        if (normalizedBundle[j] < MAX_BYTE_VALUE) {
                            normalizedBundle[j]++;
                            break;
                        }
                    }
                }
            }
        }
    }

    // todo: result or input is wrong, understand and test
    public static byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] hash, byte[] bytes, int offset, final int indexIn, int size) {
        int index = indexIn;
        final Sponge sha3 = SpongeFactory.create(mode);
        for (int i = 0; i < size; i++) {
            // todo verbosity cleanup
            //System.out.println("(" + (index & 1) + ") " + Hex.toHexString(crypto) + " + " + Hex.toHexString(Arrays.copyOfRange(bytes, offset + i*32, (i+1)*32)));
            sha3.reset();
            if ((index & 1) == 0) {
                sha3.absorb(hash, 0, hash.length);
                sha3.absorb(bytes, offset + i * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
            } else {
                sha3.absorb(bytes, offset + i * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
                sha3.absorb(hash, 0, hash.length);
            }
            sha3.squeeze(hash, 0, hash.length);
            index >>= 1;
        }
        if(index != 0) {
            return Hash.NULL_HASH.bytes();
        }
        return hash;
    }
}
