package net.helix.pendulum.crypto.merkle.impl;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.merkle.MerkleNode;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.utils.bundle.BundleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class TransactionMerkleTreeImpl extends AbstractMerkleTree {

    private static final Logger log = LoggerFactory.getLogger(TransactionMerkleTreeImpl.class);

    @Override
    protected MerkleNode createMerkleNode(Hash parentHash, Hash h1, Hash h2, int row, long index, MerkleOptions options) {
        log.debug("New virtual transaction + " + parentHash + " for milestone: " + options.getMilestoneHash() + " with merkle index: " + index + " [" + h1 + ", " + h2 + " ]");
        byte[] virtualTransaction = BundleUtils.createVirtualTransaction(h1, h2, index, options.getMilestoneHash().bytes(), options.getAddress());
        return new TransactionViewModel(virtualTransaction, options.getMode());
    }

    @Override
    protected List<MerkleNode> createMerkleNodes(List<Hash> leaves, MerkleOptions options) {

        int depth = getTreeDepth(leaves.size());
        List<MerkleNode> result = new ArrayList<>();
        for (int i = 0; i < leaves.size(); i++) {
            result.add(createMerkleNode(leaves.get(i), Hash.NULL_HASH, Hash.NULL_HASH, 0, getParentMerkleIndex(0, depth, i), options));
        }
        return result;
    }
}
