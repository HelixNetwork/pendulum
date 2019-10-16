package net.helix.pendulum.crypto.merkle.impl;

import net.helix.pendulum.crypto.merkle.MerkleNode;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.model.Hash;

import java.util.ArrayList;
import java.util.List;

public class MerkleTreeImpl extends AbstractMerkleTree {

    @Override
    protected MerkleNode createMerkleNode(Hash hashParent, Hash h1, Hash h2, int row, long index, MerkleOptions options) {
        return hashParent;
    }

    @Override
    protected List<MerkleNode> createMerkleNodes(List<Hash> leaves, MerkleOptions options) {
        List<MerkleNode> result = new ArrayList<>();
        result.addAll(leaves);
        return result;
    }
}
