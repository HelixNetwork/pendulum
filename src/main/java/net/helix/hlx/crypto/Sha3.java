package net.helix.hlx.crypto;

import java.security.DigestException;

import net.helix.hlx.exception.IllegalHashLengthException;
import net.helix.hlx.exception.ThrowableDigestException;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.jcajce.provider.digest.SHA3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.helix.hlx.utils.FastByteComparisons;

// Sha3-256

public class Sha3 implements Sponge {

    public static final int HASH_LENGTH = 32;

    private final SHA3.Digest256 sha;
    private boolean only0 = true;

    protected Sha3() {
        this.sha = new SHA3.Digest256();
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
            sha.update(bytes, pos, HASH_LENGTH);
        }
        only0 = only0 && (FastByteComparisons.compareTo(bytes, offset, length, new byte[length], 0, length) == 0);
    }

    @Override
    public void squeeze(final byte[] bytes, final int offset, final int length) {
        if (bytes.length < length) {
            throw new IndexOutOfBoundsException();
        }
        if (length % HASH_LENGTH != 0) {
            throw new IllegalHashLengthException("Illegal length: " + length);
        }
        if (only0) {
            java.util.Arrays.fill(bytes, (byte)0);
            return;
        }
        try {
            for (int pos = offset; pos < offset + length; pos += HASH_LENGTH) {
                sha.digest(bytes, pos, HASH_LENGTH);
                sha.update(bytes, pos, HASH_LENGTH);
            }
        } catch (DigestException e) {
            throw new ThrowableDigestException(e);
        }
    }

    @Override
    public void reset() {
        this.sha.reset();
        only0 = true;
    }

    public static byte[] getStandardHash(byte[] message) {
        SHA3Digest digest = new SHA3Digest(256);
        byte[] hash = new byte[digest.getDigestSize()];
        if (message.length != 0) {
            if ((FastByteComparisons.compareTo(message, 0, message.length,
                    new byte[message.length], 0, message.length) == 0)) {
                return hash;
            }
            digest.update(message, 0, message.length);
            digest.doFinal(hash, 0);
        }
        return hash;
    }

}
