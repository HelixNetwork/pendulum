package net.helix.pendulum.crypto;

public interface Sponge {
    int HASH_LENGTH = 32;

    void absorb(final byte[] bytes, final int offset, final int length);
    void squeeze(final byte[] bytes, final int offset, final int length);
    void reset();
}
