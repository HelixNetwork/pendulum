package net.helix.hlx.crypto;

import org.bouncycastle.util.encoders.Hex;

import java.util.Arrays;


public class Winternitz {

    public static final byte HASH_LENGTH = Sha3.HASH_LENGTH;
    public static final int w = 4;
    public static final int rounds = (int) Math.pow(2, w) - 1;
    public static final int NUMBER_OF_FRAGMENT_CHUNKS = 16;
    public static final int FRAGMENT_LENGTH = HASH_LENGTH * NUMBER_OF_FRAGMENT_CHUNKS;
    public static final int SIGNATURE_MESSAGE_FRAGMENT_SIZE = 512;

    private static final int NUMBER_OF_SECURITY_LEVELS = 4;
    public static final int NORMALIZED_FRAGMENT_LENGTH = (HASH_LENGTH / NUMBER_OF_SECURITY_LEVELS) * 8 / w;
    private static final byte MIN_VALUE = -8;
    private static final byte MAX_VALUE = 7;


    /**
     * Generate subseed by adding seed and index.
     *
     * @param mode  hashing mode
     * @param seed  privately generated random
     * @param index subseed index has to be in [1..(Integer.MAX_VALUE-255)]
     * @return <code> byte[] </code> subseed
     */
    public static byte[] subseed(SpongeFactory.Mode mode, final byte[] seed, int index) {
        if (index < 0 || index > Integer.MAX_VALUE - 255) {
            throw new RuntimeException("Invalid subseed index: " + index);
        }
        final Sponge hash = SpongeFactory.create(mode);
        if (seed.length % hash.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid seed length: " + seed.length);
        }
        final byte[] subseedPreimage = seed.clone();
        for (int i = subseedPreimage.length - 1; i >= 0; i--) {
            index += (subseedPreimage[i] & 0xFF);
            subseedPreimage[i] = (byte) index;
            index >>= 8;
            if (index == 0) {
                break;
            }
        }
        final byte[] subseed = new byte[hash.HASH_LENGTH];
        hash.absorb(subseedPreimage, 0, subseedPreimage.length);
        hash.squeeze(subseed, 0, subseed.length);
        return subseed;
    }

    /**
     * Generate private key.
     *
     * @param mode              hashing mode
     * @param subseed           subseed
     * @param numberOfFragments security level (1,2 or 3)
     * @return <code> byte[] </code> private key
     */
    public static byte[] key(SpongeFactory.Mode mode, final byte[] subseed, final int numberOfFragments) {
        final Sponge hash = SpongeFactory.create(mode);
        if (subseed.length != hash.HASH_LENGTH) {
            throw new RuntimeException("Invalid subseed length: " + subseed.length);
        }
        if (numberOfFragments <= 0) {
            throw new RuntimeException("Invalid number of key fragments: " + numberOfFragments);
        }
        final byte[] key = new byte[FRAGMENT_LENGTH * numberOfFragments];
        hash.absorb(subseed, 0, subseed.length);
        hash.squeeze(key, 0, key.length);
        return key;
    }

    /**
     * Generate public key.
     *
     * @param mode hashing mode
     * @param key  private key
     * @return <code> byte[] </code> public key
     */
    public static byte[] digests(SpongeFactory.Mode mode, final byte[] key) {
        if (key.length == 0 || key.length % FRAGMENT_LENGTH != 0) {
            throw new RuntimeException("Invalid private key length: " + key.length);
        }

        final Sponge hash = SpongeFactory.create(mode);
        final byte[] digests = new byte[key.length / FRAGMENT_LENGTH * hash.HASH_LENGTH];


        for (int i = 0; i < key.length / FRAGMENT_LENGTH; i++) {

            final byte[] buffer = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);

            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

                for (int k = rounds; k-- > 0; ) {
                    hash.reset();
                    hash.absorb(buffer, j *  hash.HASH_LENGTH,  hash.HASH_LENGTH);
                    hash.squeeze(buffer, j *  hash.HASH_LENGTH,  hash.HASH_LENGTH);
                }

            }
            hash.reset();
            hash.absorb(buffer, 0, buffer.length);
            hash.squeeze(digests, i *  hash.HASH_LENGTH,  hash.HASH_LENGTH);
        }
        return digests;
    }

    /**
     * Generate address.
     *
     * @param mode    hashing mode
     * @param digests public key
     * @return <code> byte[] </code> address
     */
    public static byte[] address(SpongeFactory.Mode mode, final byte[] digests) {
        final Sponge hash = SpongeFactory.create(mode);
        if (digests.length == 0 || digests.length %  hash.HASH_LENGTH != 0) {
            throw new RuntimeException("Invalid public key length: " + digests.length);
        }
        final byte[] address = new byte[ hash.HASH_LENGTH];
        hash.absorb(digests, 0, digests.length);
        hash.squeeze(address, 0, address.length);
        return address;
    }

    /**
     * Sign bundle.
     *
     * @param bundleFragment bundle fragment
     * @param keyFragment    private key fragment
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
                hash.absorb(signatureFragment, j * hash.HASH_LENGTH, hash.HASH_LENGTH);
                hash.squeeze(signatureFragment, j * hash.HASH_LENGTH, hash.HASH_LENGTH);
            }
        }
        return signatureFragment;
    }

    /**
     * Sign bundle.
     *
     * @param mode              Sponge mode
     * @param seed              Seed
     * @param index             address index
     * @param numberOfFragments number of fragments or security levels
     * @param bundleHash        bundle hash
     * @return
     */
    public static byte[] signatureFragments(SpongeFactory.Mode mode, final byte[] seed, final int index, final int numberOfFragments, final byte[] bundleHash) {

        if (bundleHash.length != SpongeFactory.create(mode).HASH_LENGTH) {
            throw new RuntimeException("Invalid bundle fragment length: " + bundleHash.length);
        }
        if (seed.length == 0) {
            throw new RuntimeException("Invalid key fragment length: " + seed.length);
        }

        byte[] normBundleHash = normalizedBundle(bundleHash);
        byte[] subseed = subseed(mode, seed, (int) index);
        final byte[] key = key(mode, subseed, numberOfFragments);
        byte[] signatureFragment = new byte[0];

        for (int i = 0; i < numberOfFragments; i++) {
            byte[] bundleFragment = Arrays.copyOfRange(normBundleHash, i * NORMALIZED_FRAGMENT_LENGTH, (i + 1) * NORMALIZED_FRAGMENT_LENGTH);
            byte[] keyFragment = Arrays.copyOfRange(key, i * FRAGMENT_LENGTH, (i + 1) * FRAGMENT_LENGTH);
            signatureFragment = concat(signatureFragment, signatureFragment(mode, bundleFragment, keyFragment));
        }
        return signatureFragment;
    }

    /**
     * Verify bundle.
     *
     * @param bundleFragment    bundle fragment
     * @param signatureFragment signature fragement
     * @return <code> byte[] </code> public key
     */
    public static byte[] digest(SpongeFactory.Mode mode, final byte[] bundleFragment, final byte[] signatureFragment) {
        if (bundleFragment.length < NORMALIZED_FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid bundle fragment length: " + bundleFragment.length);
        }
        if (signatureFragment.length < FRAGMENT_LENGTH) {
            throw new RuntimeException("Invalid signature fragment length: " + signatureFragment.length);
        }

        final Sponge hash = SpongeFactory.create(mode);
        final byte[] digest = new byte[hash.HASH_LENGTH];
        final byte[] buffer = Arrays.copyOfRange(signatureFragment, 0, FRAGMENT_LENGTH);
            for (int j = 0; j < NUMBER_OF_FRAGMENT_CHUNKS; j++) {

            for (int k = (bundleFragment[j] < 0) ? bundleFragment[j] + 256 : bundleFragment[j]; k-- > 0; ) {
                hash.reset();
                hash.absorb(buffer, j * hash.HASH_LENGTH, hash.HASH_LENGTH);
                hash.squeeze(buffer, j * hash.HASH_LENGTH, hash.HASH_LENGTH);
            }
        }
        hash.reset();
        hash.absorb(buffer, 0, buffer.length);
        hash.squeeze(digest, 0, digest.length);
        return digest;
    }

    public static boolean validateSignature(SpongeFactory.Mode mode, byte[] expectedAddress, byte[] signatureFragments, byte[] bundleHash) {

        byte[] normBundleHash = normalizedBundle(bundleHash);
        int numberOfFragments = signatureFragments.length / FRAGMENT_LENGTH;
        byte[] sigDigest = new byte[0];
        for (int i = 0; i < numberOfFragments; i++) {
            byte[] bundleFragment = Arrays.copyOfRange(normBundleHash, i * NORMALIZED_FRAGMENT_LENGTH, (i + 1) * NORMALIZED_FRAGMENT_LENGTH);
            byte[] signature = Arrays.copyOfRange(signatureFragments, i * SIGNATURE_MESSAGE_FRAGMENT_SIZE, (i + 1) * SIGNATURE_MESSAGE_FRAGMENT_SIZE);

            sigDigest = concat(sigDigest, digest(mode, bundleFragment, signature));
        }
        byte[] address = address(mode, sigDigest);
        return Arrays.equals(address, expectedAddress);

    }

    /**
     * Deterministically Normalize the bundle hash. <br>
     * <ol>
     * <li>map each byte in {@code bundle} to base 16 {@code [-13 , 13]} </li>
     * <li>sum all mapped bytes together</li>
     * <li>if sum != 0, start inc/dec each byte till sum equals 0</li>
     * </ol>
     *
     * @param bundle hash to be normalized
     * @return normalized hash
     */
    public static byte[] normalizedBundle(final byte[] bundle) {


        byte bitsInBytes = 8;
        byte[] normalizedBundle;
        if (w < bitsInBytes) {
            int splitFactor = bitsInBytes / w;

            normalizedBundle = new byte[splitFactor * HASH_LENGTH];
            for (int k = 0, i = 0; i < bundle.length; i++) {
                byte value = bundle[i];
                byte highByte4 = (byte) ((value >> w) & 0x0f);
                byte lowByte4 = (byte) (value & 0x0f);
                normalizedBundle[k++] = (byte) ((highByte4 & 0x08) == 0x08 ? highByte4 | 0xf0 : highByte4);
                normalizedBundle[k++] = (byte) ((lowByte4 & 0x08) == 0x08 ? lowByte4 | 0xf0 : lowByte4);
            }
        } else {
            normalizedBundle = Arrays.copyOf(bundle, bundle.length);
        }
        if (bundle.length != HASH_LENGTH) {
            throw new RuntimeException("Invalid bundleValidator length: " + bundle.length);
        }
        normalizedBundleFragments(normalizedBundle);

        if (w < bitsInBytes) {
            for (int i = 0; i < normalizedBundle.length; i++) {
                normalizedBundle[i] = (byte) (normalizedBundle[i] & 0x0f);
            }
        }
        return normalizedBundle;
    }

    /**
     * Normalize Bundle
     *
     * @param normalizedBundle normalized bundle
     */
    private static void normalizedBundleFragments(byte[] normalizedBundle) {
        if (normalizedBundle.length != HASH_LENGTH * 8 / w) {
            throw new RuntimeException("Invalid bundleValidator length: " + normalizedBundle.length);
        }

        for (int i = 0; i < NUMBER_OF_SECURITY_LEVELS; i++) {
            int sum = 0;
            for (int j = i * NORMALIZED_FRAGMENT_LENGTH; j < (i + 1) * NORMALIZED_FRAGMENT_LENGTH; j++) {
                normalizedBundle[j] = normalizedBundle[j];
                sum += normalizedBundle[j];
            }

            if (sum > 0) {
                while (sum-- > 0) {

                    ;
                    for (int j = i * NORMALIZED_FRAGMENT_LENGTH; j < (i + 1) * NORMALIZED_FRAGMENT_LENGTH; j++) {

                        if (normalizedBundle[j] > MIN_VALUE) {
                            normalizedBundle[j]--;
                            break;
                        }
                    }
                }
            } else {
                while (sum++ < 0) {
                    for (int j = i * NORMALIZED_FRAGMENT_LENGTH; j < (i + 1) * NORMALIZED_FRAGMENT_LENGTH; j++) {

                        if (normalizedBundle[j] < MAX_VALUE) {
                            normalizedBundle[j]++;
                            break;
                        }
                    }
                }
            }
        }
    }

    public static byte[] generateAddress(byte[] seed, int index, int security) {
        byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, seed, index);
        byte[] key = Winternitz.key(SpongeFactory.Mode.S256, subseed, security);
        byte[] digests = Winternitz.digests(SpongeFactory.Mode.S256, key);
        return Winternitz.address(SpongeFactory.Mode.S256, digests);
    }

    private static byte[] concat(byte[] first, byte[] second) {
        byte[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }
}
