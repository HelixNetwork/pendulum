package net.helix.pendulum.crypto;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;


public class WinternitzTest {

//    private static final Logger log = LoggerFactory.getLogger(WinternitzTest.class);
    private static final Random RND = new Random();


    @Test(expected=RuntimeException.class)
    public void subseedIndexRangeTest() {
        byte[] seed = new byte[Sha3.HASH_LENGTH * 2];
        int index = Integer.MAX_VALUE-254;
        Winternitz.subseed(SpongeFactory.Mode.S256, seed, index);
    }

    @Test
    public void generateWinternitzAddressTest() {
        byte[] seed = new byte[Sha3.HASH_LENGTH];
        int index;
        for (int security = 1; security <= 4; security++) {
            RND.nextBytes(seed);
            index = RND.nextInt(256);
            byte[] address = Winternitz.generateAddress(seed, index, security);
            Assert.assertNotNull(address);
            Assert.assertFalse(Arrays.equals(new byte[address.length], address));
        }
    }

    @Test
    public void signaturesResolveToAddressTest() throws Exception {
        int index = 0;
        SpongeFactory.Mode[] modes = {SpongeFactory.Mode.S256, SpongeFactory.Mode.K256};
        byte[] seed = new byte[64];
        RND.nextBytes(seed);
        byte[] messageBytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(messageBytes);
        
        for (SpongeFactory.Mode mode: modes) {
            for (int securityLevel = 1; securityLevel <= 4; securityLevel++) {
                byte[] subseed = Winternitz.subseed(mode, seed, index);
                byte[] key = Winternitz.key(mode, subseed, securityLevel);
                byte[] digest = Winternitz.digests(mode, key);
                byte[] address = Winternitz.address(mode, digest);
                byte[] messageHash = computeHash(messageBytes);
                byte[] signature = Winternitz.signatureFragments(mode, seed, index, securityLevel, messageHash);
                boolean isValid = Winternitz.validateSignature(mode, address, signature, messageHash);
                Assert.assertTrue("Winternitz signature is not valid!", isValid);
            }
        }
    }

    private byte[] computeHash(byte[] messageBytes) {
        Sha3 sha3 = new Sha3();
        sha3.absorb(messageBytes, 0, messageBytes.length);
        byte[] messageHash = new byte[Sha3.HASH_LENGTH];
        sha3.squeeze(messageHash, 0, Sha3.HASH_LENGTH);
        return messageHash;
    }

    @Test
    public void generateNAddressesForSeedTest() throws Exception {
        int nof = 2;
        //log.debug("seed,address_0,address_1,address_2,address_3");
        for (int i = 0; i < 50; i++) {
            byte[] b = new byte[Hash.SIZE_IN_BYTES];
            RND.nextBytes(b);
            Hash seed = HashFactory.TRANSACTION.create(b);
            SpongeFactory.Mode mode = SpongeFactory.Mode.S256;
            Hash[] addresses = new Hash[4];
            for (int j = 0; j < 4; j++) {
                byte[] subseed = Winternitz.subseed(mode, seed.bytes(), j);
                byte[] key = Winternitz.key(mode, subseed, nof);
                byte[] digest = Winternitz.digests(mode, key);
                byte[] address = Winternitz.address(mode, digest);
                addresses[j] = HashFactory.ADDRESS.create(address);
            }
           // log.debug(String.format("%s,%s,%s,%s,%s", seed, addresses[0],addresses[1],addresses[2],addresses[3]));
        }
    }
}
