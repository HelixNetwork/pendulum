package net.helix.hlx.crypto;

import java.util.Arrays;
import java.util.Random;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Hex;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class WinternitzTest {

    private static final Logger log = LoggerFactory.getLogger(WinternitzTest.class);
    private static final Random RND = new Random();

    @Test
    public void generateWinternitzAddressTest() {
        byte[] seed = new byte[Sha3.HASH_LENGTH];
        int index;
        for (int security = 1; security <= 3; security++) {
            RND.nextBytes(seed);
            index = RND.nextInt(256);
            byte[] address = Winternitz.generateAddress(seed, index, security);
            Assert.assertNotNull(address);
            Assert.assertFalse(Arrays.equals(new byte[address.length], address));
        }
    }

    // The following test methods were defined to show specific case(s), where the old subseed implementation produced unexpected results.
    // These methods now basically use the same algorithm as the Winternitz class and will thus be removed in the next updates.
    @Test
    public void subseedTest() {
        byte[] seed = new byte[Sha3.HASH_LENGTH * 2];
        RND.nextBytes(seed);
        int index = RND.nextInt(255);

        byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, seed, index);
        log.debug("Generated subseed: " + Hex.toHexString(subseed));

        byte[] seed2 = seed.clone();
        for (int i = seed2.length - 1; i >= 0; i--) {
            index = (seed2[i] & 0xFF) + index;
            seed2[i] = (byte)index;
            index = index >> 8;
            if (index == 0) {
                break;
            }
        }
        SHA3.Digest256 sha3 = new SHA3.Digest256();
        sha3.update(seed2);
        byte[] subseed2 = sha3.digest();
        log.debug("Expected  subseed: " + Hex.toHexString(subseed2));

        Assert.assertArrayEquals(subseed, subseed2);
    }

    @Test
    public void subseed0Test() {
        byte[] seed = new byte[Sha3.HASH_LENGTH * 2];
        RND.nextBytes(seed);
        int index = RND.nextInt(255);
        seed[0] = 0;
        seed[1] = 0;
        log.debug("Seed: " + Hex.toHexString(seed));

        byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, seed, index);
        log.debug("Generated subseed: " + Hex.toHexString(subseed));

        byte[] seed2 = seed.clone();
        for (int i = seed2.length - 1; i >= 0; i--) {
            index = (seed2[i] & 0xFF) + index;
            seed2[i] = (byte)index;
            index = index >> 8;
            if (index == 0) {
                break;
            }
        }
        SHA3.Digest256 sha3 = new SHA3.Digest256();
        sha3.update(seed2);
        byte[] subseed2 = sha3.digest();
        log.debug("Expected  subseed: " + Hex.toHexString(subseed2));

        Assert.assertArrayEquals(subseed, subseed2);
    }

    @Test
    public void subseed255Test() {
        byte[] seed = new byte[Sha3.HASH_LENGTH * 2];
        RND.nextBytes(seed);
        int index = RND.nextInt(255);
        seed[0] = (byte)255;
        seed[1] = (byte)255;
        log.debug("Seed: " + Hex.toHexString(seed));

        byte[] subseed = Winternitz.subseed(SpongeFactory.Mode.S256, seed, index);
        log.debug("Generated subseed: " + Hex.toHexString(subseed));

        byte[] seed2 = seed.clone();
        for (int i = seed2.length - 1; i >= 0; i--) {
            index = (seed2[i] & 0xFF) + index;
            seed2[i] = (byte)index;
            index = index >> 8;
            if (index == 0) {
                break;
            }
        }
        SHA3.Digest256 sha3 = new SHA3.Digest256();
        sha3.update(seed2);
        byte[] subseed2 = sha3.digest();
        log.debug("Expected  subseed: " + Hex.toHexString(subseed2));

        Assert.assertArrayEquals(subseed, subseed2);
    }
    
}
