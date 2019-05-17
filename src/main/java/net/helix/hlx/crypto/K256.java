package net.helix.hlx.crypto;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.DigestException;

public final class K256 implements Sponge {

    private static final Logger log = LoggerFactory.getLogger(K256.class);
    public static final int HASH_LENGTH = 32;
    private final Keccak.Digest256 keccak;

    protected K256() {
        this.keccak = new Keccak.Digest256();
    }

    @Override
    public void absorb(final byte[] bytes, final int offset, final int length) {
        if (bytes.length < length) {
            throw new IndexOutOfBoundsException();
        }
        if (length % HASH_LENGTH != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }
        for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
            keccak.update(bytes, pos, HASH_LENGTH);
        }
    }

    @Override
    public void squeeze(final byte[] bytes, final int offset, final int length){
        if (bytes.length < length) {
            throw new IndexOutOfBoundsException();
        }
        if (length % HASH_LENGTH != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }
        try {
            for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
                keccak.digest(bytes, pos, HASH_LENGTH);
                keccak.update(bytes, pos, HASH_LENGTH);
            }
        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reset() {
        this.keccak.reset();
    }

}
