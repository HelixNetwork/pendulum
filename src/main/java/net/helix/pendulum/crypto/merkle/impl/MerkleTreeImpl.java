package net.helix.pendulum.crypto.merkle.impl;

import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.Sha3;
import net.helix.pendulum.crypto.Sponge;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.crypto.merkle.MerkleTree;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MerkleTreeImpl implements MerkleTree {

    private MerkleOptions options;

    public MerkleTreeImpl(MerkleOptions options) {
        this.options = options;
    }

    protected List<Hash> createHashs(List<Hash> leaves) {
        List<Hash> result = new ArrayList<>();
        result.addAll(leaves);
        return result;
    }

    protected Hash createHash(Hash hashParent, Hash h1, Hash h2, int row, long index) {
        return hashParent;
    }

    @Override
    public byte[] getMerkleRoot(byte[] hash, byte[] bytes, int offset, final int indexIn, int size) {
        int index = indexIn;
        final Sponge sha3 = SpongeFactory.create(options.getMode());
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
    public List<Hash> getMerklePath(List<List<Hash>> merkleTree, int keyIndex) {
        List<Hash> merklePath = new ArrayList<>((merkleTree.size() - 1) * Hash.SIZE_IN_BYTES);
        for (int i = 0; i < merkleTree.size() - 1; i++) {
            Hash subkey = merkleTree.get(i).get(keyIndex ^ 1);
            merklePath.add(subkey == null ? getDefaultMerkleHash() : subkey);
            keyIndex /= 2;
        }
        return merklePath;
    }

    @Override
    public byte[] getMerkleRoot(List<Hash> leaves) {
        List<List<Hash>> merkleTree = buildMerkleTree(leaves);
        return (merkleTree.get(merkleTree.size() - 1).get(0)).bytes();
    }


    private void sortLeaves(List<Hash> leaves) {
        leaves.sort(Comparator.comparing((Hash m) -> m.toString()));
    }

    @Override
    public List<List<Hash>> buildMerkleTree(List<Hash> leaves) {
        if (leaves.isEmpty()) {
            leaves.add(getDefaultMerkleHash());
        }
        byte[] buffer;
        addPaddingLeaves(leaves);
        sortLeaves(leaves);
        Sponge sha3 = SpongeFactory.create(options.getMode());
        List<List<Hash>> merkleTree = new ArrayList<>();
        merkleTree.add(0, leaves);
        int row = 1;
        while (leaves.size() > 1) {
            List<Hash> nextKeys = Arrays.asList(new Hash[getParentNodesSize(leaves)]);
            for (int i = 0; i < nextKeys.size(); i++) {
                if (areLeavesNull(leaves, i)) {
                    continue;
                }
                sha3.reset();
                Hash k1 = getLeaves(leaves, i * 2);
                Hash k2 = getLeaves(leaves, i * 2 + 1);
                buffer = computeParentHash(sha3, k1, k2);
                nextKeys.set(i, HashFactory.TRANSACTION.create(buffer));
            }
            leaves = nextKeys;
            merkleTree.add(row++, createHashs(leaves));
        }
        return merkleTree;
    }

    public boolean validateMerkleSignature(List<TransactionViewModel> bundleTransactionViewModels) {

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

        return validateMerklePath(merkleTx.getSignature(), keyIndex, address);
    }

    private boolean validateMerklePath(byte[] path, int keyIndex, byte[] address) {
        byte[] merkleRoot = getMerkleRoot(address,
                path, 0, keyIndex, options.getDepth());
        return HashFactory.ADDRESS.create(merkleRoot).equals(options.getAddress());
    }

    protected void addPaddingLeaves(List<Hash> leaves) {
        int closestPow = (int) Math.pow(2.0, getClosestPow(leaves.size()));
        int leaveSize = leaves.size();
        while (leaveSize < closestPow) {
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
