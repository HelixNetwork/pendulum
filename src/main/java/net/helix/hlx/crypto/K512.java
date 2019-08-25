package net.helix.hlx.crypto;

import net.helix.hlx.exception.IllegalHashLengthException;
import net.helix.hlx.exception.ThrowableDigestException;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.DigestException;

public class K512 implements Sponge {

    public static final int HASH_LENGTH = 64;
    private final Keccak.Digest512 keccak;

    protected K512() {
        this.keccak = new Keccak.Digest512();
    }

    @Override
    public void absorb(final byte[] bytes, final int offset, final int length) {
        if (bytes.length < length) {
            throw new IndexOutOfBoundsException();
        }
        if (length % HASH_LENGTH != 0) {
            throw new IllegalHashLengthException("Illegal length: " + length);
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
            throw new IllegalHashLengthException("Illegal length: " + length);
        }
        try {
            for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
                keccak.digest(bytes, pos, HASH_LENGTH);
                keccak.update(bytes, pos, HASH_LENGTH);
            }
        } catch (DigestException e) {
            throw new ThrowableDigestException(e);
        }
    }

    @Override
    public void reset() {
        this.keccak.reset();
    }
}


