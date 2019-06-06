package net.helix.hlx.crypto;

import java.util.Arrays;
import java.util.Random;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import net.helix.hlx.controllers.TransactionViewModel;


public class WinternitzTest {

    private static final Logger log = LoggerFactory.getLogger(WinternitzTest.class);
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
        for (int security = 1; security <= 3; security++) {
            RND.nextBytes(seed);
            index = RND.nextInt(256);
            byte[] address = Winternitz.generateAddress(seed, index, security);
            Assert.assertNotNull(address);
            Assert.assertFalse(Arrays.equals(new byte[address.length], address));
        }
    }

    @Test
    public void signaturesResolveToAddressTest() throws Exception {
        int index = 10;
        int nof = 1;
        SpongeFactory.Mode[] modes = {SpongeFactory.Mode.S256, SpongeFactory.Mode.K256};
        byte[] seed = new byte[64];
        RND.nextBytes(seed);
        byte[] messageBytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(messageBytes);
        
        for (SpongeFactory.Mode mode: modes) {
            byte[] subseed = Winternitz.subseed(mode, seed, index);
            byte[] key = Winternitz.key(mode, subseed, nof);
            Sha3 sha3 = new Sha3();
            sha3.absorb(messageBytes, 0, messageBytes.length);
            byte[] messageHash = new byte[Sha3.HASH_LENGTH];
            sha3.squeeze(messageHash, 0, Sha3.HASH_LENGTH);
            byte[] normalizedFragment = Arrays.copyOf(Winternitz.normalizedBundle(messageHash),
                    Winternitz.NUMBER_OF_FRAGMENT_CHUNKS);
            byte[] signature = Winternitz.signatureFragment(mode, normalizedFragment, key);
            byte[] sigDigest = Winternitz.digest(mode, normalizedFragment, signature);
            byte[] signedAddress = Winternitz.address(mode, sigDigest);
            byte[] digest = Winternitz.digests(mode, key);
            byte[] address = Winternitz.address(mode, digest);
            Assert.assertTrue(Arrays.equals(address, signedAddress));
        }
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
            //log.debug(String.format("%s,%s,%s,%s,%s", seed, addresses[0],addresses[1],addresses[2],addresses[3]));
        }
    }

}
