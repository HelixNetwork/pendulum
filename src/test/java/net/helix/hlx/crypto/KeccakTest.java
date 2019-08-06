package net.helix.hlx.crypto;

import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.bouncycastle.util.encoders.Hex;
import java.security.SecureRandom;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class KeccakTest {
    private static final Logger log = LoggerFactory.getLogger(KeccakTest.class);
    private String txHex = "bf443967e93cf9e5ec3a5b1fa029527deb45da2627e80050d45fc9f0003679055f1b6ca9f25ac70f44f09c4aacb155a2340cbff3043070e382ddb62da72aa650f76b0f8b494203204954053afa20aec27417ba3419b68236cdc538091c6a6cf1854ea1147ee2f39128a5b8cdbf6c30b0e9553f07499ee74dcf88b53ae08b11fdcc376b14c76bfcd90122c2deccbfbba1dcf46041f0d34c88f15f96c25ce902ff7d25e7ab4cb50f29304c2adf498de522973bf123f865a84c4386e886fc370377aafd1f5f45f4b95b21fa2dc6629f2e879ec80ccf658c4a128d335f83fd4e0746313a91f31cadcd0384c973401bd5d1008d8e3446fa906f6fa849f6cad209725d8fa076845d65787c0a51f9f97be5046c89f7c0c4b1805375e6b9140d2851dfbcb51b29842ea135cc9f853062f72c19b3f4a68c2363b3e8ff64086706383540f8a4f2155dbf9170d7cb1cec664d8d3421ea749d963852b867ed080633568aa7a502e693253b5d31fe2c6969ca589e4ba224b118b9362416a7dcb81343b77038792ce5f786a1b53d06c9e40c3be09e6268d9af6ed3d1b755f5304e489a8c229de68c4e47bd0cf9e47ad5ad42471bbf5dd2eaad857b7a515f28f450f119a35cfcb24217067d6252d9775925888b80e2a5be4cdabdc615cbe2aa561c68f0d8e5866c0201cfa66bd231ade44b197d7345ec5dc8f63c93cbaa2604ae4d5efe69eafcb0ce88b9c3fefb96b3245e44275013b58f95046e9af97c458064c47543fa028ef62475ae6c64d8e4cc36d9ea77ff4587618770f71911b4c7c4d955138446830087fe0ca66fc87a8dc8500334e7f9cbc67f28d844db7ad8a400140e16f2afbd6b64e22fb619e5c493af0a694b50c6b2dd651abaf812a516da7f74a3e8832e3ae14311ced1b0a5988e9751e904b890463a00d28098a2c2bd5b927b93a2d6da7623debf8499174638625dbb4f8ead72a346ba36212f20b8d09cf63b28bc8fc6116cf78ab349b0c8cdab6fc0e22abc7e1647b9f583343bf4873aebbf4d95898db3bc9fac7c3b5f6a16bfdfdeb3e11dfd4b8640c523afcfa6f4c8118746e41950e829da7bce0f244626d0036f3e2139f839f1d77a454de5bd0d72a8db671f45ee994c02e1edc3228201f4174d1ee059f7a7daeb18999a583574b8c426b30dfc85730e746fc8af5875a08f35a0b91199fe8b9f9706c469c9785e1d5ff20592bf4296b66d2ff7d0f38a8bf098714d62a460cc3b7a0a09a6883500941f31cdc6f3ba9d4322021e306a40f96e8d7a8f720fb94e724c3cdd5e2a982d848e608305846a5ae5a550f4caf27c45299c2435c31705cdf76071d3982f5ff66c348fe2026f90ab1452a13d7860fd32ec42e2e14bba0dc30ab0ece27a878b3209df1f58d261def6eb064edf8363369851fd1fe5f238255125369cfebe8c4b081c3dd4a0b8b1f5206e63f9073f88c5f5af47ef4797206c5c39d7413682d09f5a08a965b84a6f2fa8e87a4c76bd2f3e21ee60f2c8992d9ccfd19775fce735b7f45491fc7e2387de8e96234497f8d2f31c44f803a52fbb0aa94b0da9b30b441b39926e93f7f184d13e22f433073a022c6167c5ce3ecbba91982dec6ce9b3a03c605e5fd7480f8cfdfe2546068d67c0135a6a834f54d2ae66faa1b0faab32e2367849c2b70e207aed53608b6aac94cfe3786a79a8bf4d9051e3b7404a5e66758b2c8ee8e361a1e443dc06ee8e16c0fc4af13cb8467864b70458ca9390d512b694bd8f306526852f1602fc03";
    private final String hexEncodedS3Hash = "b180cf12c98626b1386553a96fb66a27690d979da441c8f0089ea04c810fbde0";
    private final String hexEncodedKHash = "1bacd73cea4b9fd2fbddf1a453d1253ab12cc2d928800641e1283cf944401177";

    @Test
    public void sha3Test(){
        byte[] txBytes = Hex.decode(txHex);
        byte[] hexEncodedBytes = Hex.decode(hexEncodedS3Hash);
        byte[] outBytes = new byte[Sha3.HASH_LENGTH];
        Sponge sha3;
        sha3 = SpongeFactory.create(SpongeFactory.Mode.S256);
        sha3.absorb(txBytes,0, txBytes.length);
        sha3.squeeze(outBytes, 0, Sha3.HASH_LENGTH);
        Assert.assertArrayEquals(outBytes, hexEncodedBytes);
    }

    @Test
    public void keccakTest(){
        byte[] txBytes = Hex.decode(txHex);
        byte[] hexEncodedBytes = Hex.decode(hexEncodedKHash);
        byte[] outBytes = new byte[K256.HASH_LENGTH];
        Sponge keccak;
        keccak = SpongeFactory.create(SpongeFactory.Mode.K256);
        keccak.absorb(txBytes,0, txBytes.length);
        keccak.squeeze(outBytes, 0, K256.HASH_LENGTH);
        Assert.assertArrayEquals(outBytes, hexEncodedBytes);
    }

    @Test
    public void sha3SpongeTest() {

        // Test with .getBytes()
        SHA3.Digest256 sha3 = new SHA3.Digest256();
        byte[] testBytes = txHex.getBytes();
        byte[] testBytes2 = txHex.getBytes();
        byte[] testBytes2Out = new byte [Sha3.HASH_LENGTH];

        sha3.update(testBytes);
        testBytes = sha3.digest();
        Sponge s3 = SpongeFactory.create(SpongeFactory.Mode.S256);

        s3.absorb(testBytes2, 0, txHex.length());
        s3.squeeze(testBytes2Out, 0, Sha3.HASH_LENGTH);

        log.info("Expected-Hash-Str-Encoded: " + Hex.toHexString(testBytes));
        log.info("S256-Hash-Str-Encoded    : " + Hex.toHexString(testBytes2Out));

        Assert.assertArrayEquals(testBytes, testBytes2Out);

        // Test Hex.decode()
        SHA3.Digest256 _sha3 = new SHA3.Digest256();
        byte[] encodedBytes = Hex.decode(txHex);
        byte[] encodedBytes2 = encodedBytes.clone();
        byte[] encodedBytes2Out = new byte[Sha3.HASH_LENGTH];

        _sha3.update(encodedBytes);
        encodedBytes = _sha3.digest();
        Sponge _s3 = SpongeFactory.create(SpongeFactory.Mode.S256);

        _s3.absorb(encodedBytes2, 0, encodedBytes2.length);
        _s3.squeeze(encodedBytes2Out, 0, Sha3.HASH_LENGTH);

        log.info("Expected-Hash-Hex-Encoded: " + Hex.toHexString(encodedBytes));
        log.info("S256-Hash-Hex-Encoded    : " + Hex.toHexString(encodedBytes2Out));

        Assert.assertArrayEquals(encodedBytes, encodedBytes2Out);
    }

    @Test
    public void keccakSpongeTest() {

        // Test with .getBytes()
        Keccak.Digest256 keccak = new Keccak.Digest256();
        byte[] testBytes = txHex.getBytes();
        byte[] testBytes2 = txHex.getBytes();
        byte[] testBytes2Out = new byte [K256.HASH_LENGTH];

        keccak.update(testBytes);
        testBytes = keccak.digest();
        Sponge k = SpongeFactory.create(SpongeFactory.Mode.K256);

        k.absorb(testBytes2, 0, txHex.length());
        k.squeeze(testBytes2Out, 0, K256.HASH_LENGTH);

        log.info("Expected-Hash-Str-Encoded: " + Hex.toHexString(testBytes));
        log.info("K256-Hash-Str-Encoded    : " + Hex.toHexString(testBytes2Out));
        Assert.assertArrayEquals(testBytes, testBytes2Out);

        // Test Hex.decode()
        Keccak.Digest256 _keccak = new Keccak.Digest256();
        byte[] encodedBytes = Hex.decode(txHex);
        byte[] encodedBytes2 = encodedBytes.clone();
        byte[] encodedBytes2Out = new byte[K256.HASH_LENGTH];

        _keccak.update(encodedBytes);
        encodedBytes = _keccak.digest();
        Sponge _k = SpongeFactory.create(SpongeFactory.Mode.K256);

        _k.absorb(encodedBytes2, 0, encodedBytes2.length);
        _k.squeeze(encodedBytes2Out, 0, K256.HASH_LENGTH);

        log.info("Expected-Hash-Hex-Encoded: " + Hex.toHexString(encodedBytes));
        log.info("K256-Hash-Hex-Encoded    : " + Hex.toHexString(encodedBytes2Out));

        Assert.assertArrayEquals(encodedBytes, encodedBytes2Out);
    }

}
