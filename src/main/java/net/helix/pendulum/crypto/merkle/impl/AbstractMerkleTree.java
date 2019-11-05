package net.helix.pendulum.crypto.merkle.impl;

import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Sha3;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.crypto.merkle.MerkleNode;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.crypto.merkle.MerkleTree;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public abstract class AbstractMerkleTree implements MerkleTree {

    protected abstract MerkleNode createMerkleNode(Hash hashParent, Hash h1, Hash h2, int row, long index, MerkleOptions options);

    protected abstract List<MerkleNode> createMerkleNodes(List<Hash> leaves, MerkleOptions options);

    @Override
    public byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] hash, byte[] bytes, int offset, final int indexIn, int size) {
        int index = indexIn;
        final Sponge sha3 = SpongeFactory.create(mode);
        for (int i = 0; i < size; i++) {
            sha3.reset();
            if ((index & 1) == 0) {
                sha3.absorb(hash, 0, hash.length);
                sha3.absorb(bytes, offset + i * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
            } else {
                sha3.absorb(bytes, offset + i * Sha3.HASH_LENGTH, Sha3.HASH_LENGTH);
                sha3.absorb(hash, 0, hash.length);
            }
            sha3.squeeze(hash, 0, hash.length);
            index >>= 1;
        }
        if (index != 0) {
            return getDefaultMerkleHash().bytes();
        }
        return hash;
    }

    @Override
    public List<MerkleNode> getMerklePath(List<List<MerkleNode>> merkleTree, int keyIndex) {
        List<MerkleNode> merklePath = new ArrayList<>((merkleTree.size() - 1) * Hash.SIZE_IN_BYTES);
        for (int i = 0; i < merkleTree.size() - 1; i++) {
            MerkleNode subkey = merkleTree.get(i).get(keyIndex ^ 1);
            merklePath.add(subkey == null ? getDefaultMerkleHash() : subkey);
            keyIndex /= 2;
        }
        return merklePath;
    }

    @Override
    public byte[] getMerkleRoot(List<Hash> leaves, MerkleOptions options) {
        List<List<MerkleNode>> merkleTree = buildMerkleTree(leaves, options);
        return (merkleTree.get(merkleTree.size() - 1).get(0)).bytes();
    }

    @Override
    public List<MerkleNode> buildMerkle(List<Hash> leaves, MerkleOptions options) {
        if (leaves.isEmpty()) {
            leaves.add(getDefaultMerkleHash());
        }
        byte[] buffer;
        Sponge sha3 = SpongeFactory.create(SpongeFactory.Mode.S256);
        int row = 1;
        addPaddingLeaves(leaves);
        sortLeaves(leaves);
        List<MerkleNode> merkleNodes = new ArrayList<>();
        int depth = getTreeDepth(leaves.size());
        while (leaves.size() > 1) {
            List<Hash> nextKeys = Arrays.asList(new Hash[getParentNodesSize(leaves)]);
            for (int i = 0; i < nextKeys.size(); i++) {
                if (areLeavesNull(leaves, i)) continue;
                sha3.reset();
                Hash k1 = getLeaves(leaves, i * 2);
                Hash k2 = getLeaves(leaves, i * 2 + 1);
                buffer = computeParentHash(sha3, k1, k2);
                Hash parentHash = HashFactory.TRANSACTION.create(buffer);
                nextKeys.set(i, parentHash);
                merkleNodes.add(createMerkleNode(parentHash, k1, k2, row, getParentMerkleIndex(row, depth, i * 2), options));
            }
            leaves = nextKeys;
            row++;
        }
        return merkleNodes;
    }

    private void sortLeaves(List<Hash> leaves) {
        leaves.sort(Comparator.comparing((Hash m) -> m.toString()));
    }

    @Override
    public List<List<MerkleNode>> buildMerkleTree(List<Hash> leaves, MerkleOptions options) {
        if (leaves.isEmpty()) {
            leaves.add(getDefaultMerkleHash());
        }
        byte[] buffer;
        addPaddingLeaves(leaves);
        sortLeaves(leaves);
        Sponge sha3 = SpongeFactory.create(options.getMode());
        List<List<MerkleNode>> merkleTree = new ArrayList<>();

        if (options.isIncludeLeavesInTree()) {
            merkleTree.add(0, createMerkleNodes(leaves, options));
        }
        int row = 1;
        while (leaves.size() > 1) {
            List<Hash> nextKeys = Arrays.asList(new Hash[getParentNodesSize(leaves)]);
            for (int i = 0; i < nextKeys.size(); i++) {
                if (areLeavesNull(leaves, i)) continue;
                sha3.reset();
                Hash k1 = getLeaves(leaves, i * 2);
                Hash k2 = getLeaves(leaves, i * 2 + 1);
                buffer = computeParentHash(sha3, k1, k2);
                nextKeys.set(i, HashFactory.TRANSACTION.create(buffer));
            }
            leaves = nextKeys;
            merkleTree.add(row++, createMerkleNodes(leaves, options));
        }
        return merkleTree;
    }

    public boolean validateMerkleSignature(List<TransactionViewModel> bundleTransactionViewModels, MerkleOptions options) {

        final TransactionViewModel merkleTx = bundleTransactionViewModels.get(options.getSecurityLevel());
        int keyIndex = RoundViewModel.getRoundIndex(merkleTx); // get keyindex

        //milestones sign the normalized hash of the sibling transaction. (why not bundle hash?)
        //TODO: check if its okay here to use bundle hash instead of tx hash
        byte[] bundleHash = Winternitz.normalizedBundle(merkleTx.getBundleHash().bytes());

        //validate leaf signature
        ByteBuffer bb = ByteBuffer.allocate(Sha3.HASH_LENGTH * options.getSecurityLevel());

        for (int i = 0; i < options.getSecurityLevel(); i++) {
            byte[] bundleHashFragment = Arrays.copyOfRange(bundleHash, Winternitz.NORMALIZED_FRAGMENT_LENGTH * i, Winternitz.NORMALIZED_FRAGMENT_LENGTH * (i + 1));
            byte[] digest = Winternitz.digest(options.getMode(), bundleHashFragment, bundleTransactionViewModels.get(i).getSignature());
            bb.put(digest);
        }

        byte[] digests = bb.array();
        byte[] address = Winternitz.address(options.getMode(), digests);

        return validateMerklePath(merkleTx.getSignature(), keyIndex, address, options);
    }

    private boolean validateMerklePath(byte[] path, int keyIndex, byte[] address, MerkleOptions options) {
        byte[] merkleRoot = getMerkleRoot(options.getMode(), address,
                path, 0, keyIndex, options.getDepth());
        return HashFactory.ADDRESS.create(merkleRoot).equals(options.getAddress());
    }

    protected void addPaddingLeaves(List<Hash> leaves){
        int closestPow = (int) Math.pow(2.0, getClosestPow(leaves.size()));
        int leaveSize = leaves.size();
        while(leaveSize < closestPow){
            leaves.add(leaveSize++, Hash.NULL_HASH);
        }
    }

    protected int getTreeDepth(int leavesNumber) {
        return getClosestPow(leavesNumber);
    }

    protected int getClosestPow(int i) {
        int j = 1;
        int power = 0;
        while (j < i) {
            j = j << 1;
            power++;
        }
        return power;
    }

    protected Hash getLeaves(List<Hash> leaves, int index) {
        return index < leaves.size() ? leaves.get(index) : getDefaultMerkleHash();
    }

    protected static long getParentMerkleIndex(int row, int depth, int i) {
        if (row == depth) {
            return 0;
        }
        long index = depth - row;
        return (long) Math.pow(2, index) + i / 2 - 1;
    }

    protected int getParentNodesSize(List<Hash> leaves) {
        return leaves.size() % 2 == 0 ? (leaves.size() / 2) : (leaves.size() / 2 + 1);
    }

    private boolean areLeavesNull(List<Hash> leaves, int i) {
        if (leaves.get(i * 2) == null && leaves.get(i * 2 + 1) == null) {
            return true;
        }
        return false;
    }

    private byte[] computeParentHash(Sponge sha3, byte[] k1, byte[] k2) {
        byte[] buffer = new byte[Hash.SIZE_IN_BYTES];
        sha3.absorb(k1, 0, k1.length);
        sha3.absorb(k2, 0, k2.length);
        sha3.squeeze(buffer, 0, buffer.length);
        return buffer;
    }

    private byte[] computeParentHash(Sponge sha3, Hash k1, Hash k2) {
        return computeParentHash(sha3, copyHash(k1), copyHash(k2));
    }

    private byte[] copyHash(Hash k2) {
        return Arrays.copyOfRange(k2 == null ? getDefaultMerkleHash().bytes() : k2.bytes(), 0, Hash.SIZE_IN_BYTES);
    }

    private Hash getDefaultMerkleHash() {
        return Hash.NULL_HASH;
    }
}
