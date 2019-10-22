package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.Hash;

public class MerkleOptions {

    private boolean includeLeavesInTree;
    private Hash milestoneHash;
    private Hash address;
    private SpongeFactory.Mode mode;
    private int securityLevel;
    private int depth;

    /**
     * Parameters needed fro validation
     * @param mode
     * @param address
     * @param securityLevel
     * @param depth
     */
    public MerkleOptions(SpongeFactory.Mode mode, Hash address, int securityLevel, int depth) {
        this.address = address;
        this.mode = mode;
        this.securityLevel = securityLevel;
        this.depth = depth;
        this.includeLeavesInTree = true;
    }

    public MerkleOptions() {
    }

    public static MerkleOptions getDefault() {
        MerkleOptions op = new MerkleOptions();
        op.includeLeavesInTree = true;
        op.mode = SpongeFactory.Mode.S256;
        return op;
    }

    public boolean isIncludeLeavesInTree() {
        return includeLeavesInTree;
    }

    public void setIncludeLeavesInTree(boolean includeLeavesInTree) {
        this.includeLeavesInTree = includeLeavesInTree;
    }

    public Hash getMilestoneHash() {
        return milestoneHash;
    }

    public void setMilestoneHash(Hash milestoneHash) {
        this.milestoneHash = milestoneHash;
    }

    public Hash getAddress() {
        return address;
    }

    public void setAddress(Hash address) {
        this.address = address;
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
}
