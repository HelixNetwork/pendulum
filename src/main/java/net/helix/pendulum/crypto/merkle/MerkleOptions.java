package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.Hash;

public class MerkleOptions {

    private SpongeFactory.Mode mode;
    private int securityLevel;
    private int depth;
    private Hash address;

    public MerkleOptions(SpongeFactory.Mode mode) {
        this.mode = mode;
    }

    /**
     * Merkle option parameters
     *
     * @param mode
     * @param securityLevel
     * @param depth
     */
    public MerkleOptions(SpongeFactory.Mode mode, int securityLevel, int depth) {
        this.mode = mode;
        this.securityLevel = securityLevel;
        this.depth = depth;
    }

    public MerkleOptions(SpongeFactory.Mode mode, int securityLevel, int depth, Hash address) {
        this.mode = mode;
        this.securityLevel = securityLevel;
        this.depth = depth;
        this.address = address;
    }

    public static MerkleOptions getDefault() {
        return new MerkleOptions(SpongeFactory.Mode.S256, 2, 0, Hash.NULL_HASH);
    }

    public SpongeFactory.Mode getMode() {
        return mode;
    }

    public void setMode(SpongeFactory.Mode mode) {
        this.mode = mode;
    }

    public int getSecurityLevel() {
        return securityLevel;
    }

    public void setSecurityLevel(int securityLevel) {
        this.securityLevel = securityLevel;
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }

    public Hash getAddress() {
        return address;
    }

    public void setAddress(Hash address) {
        this.address = address;
    }
}
