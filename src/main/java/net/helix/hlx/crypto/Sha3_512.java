package net.helix.hlx.crypto;

import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.DigestException;

// Sha3-512

public class Sha3_512 implements Sponge {

    private static final Logger log = LoggerFactory.getLogger(Sha3_512.class);
    public static final int HASH_LENGTH = 64;

    private final SHA3.Digest512 sha;

    protected Sha3_512() {
        this.sha = new SHA3.Digest512();
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
            sha.update(bytes, pos, HASH_LENGTH);
        }
    }
    @Override
    public void squeeze(final byte[] bytes, final int offset, final int length) {
        if (bytes.length < length) {
            throw new IndexOutOfBoundsException();
        }
        if (length % HASH_LENGTH != 0) {
            throw new RuntimeException("Illegal length: " + length);
        }
        try {
            for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
                sha.digest(bytes, pos, HASH_LENGTH);
                sha.update(bytes, pos, HASH_LENGTH);
            }

        } catch (DigestException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void reset() {
        this.sha.reset();
    }

}
