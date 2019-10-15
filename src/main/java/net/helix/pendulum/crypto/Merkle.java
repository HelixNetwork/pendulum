package net.helix.pendulum.crypto;

import net.helix.pendulum.controllers.RoundViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import net.helix.pendulum.utils.bundle.BundleUtils;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Merkle {

    private static final Logger log = LoggerFactory.getLogger(Merkle.class);

    public static byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] hash, byte[] bytes, int offset, final int indexIn, int size) {
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
            return Hash.NULL_HASH.bytes();
        }
        return hash;
    }

    public static List<Hash> getMerklePath(List<List<Hash>> merkleTree, int keyIndex) {
        List<Hash> merklePath = new ArrayList<>((merkleTree.size() - 1) * 32);
        for (int i = 0; i < merkleTree.size() - 1; i++) {
            Hash subkey = merkleTree.get(i).get(keyIndex ^ 1);
            merklePath.add(subkey == null ? Hash.NULL_HASH : subkey);
            keyIndex /= 2;
        }
        return merklePath;
    }

    public static List<List<Hash>> buildMerkleKeyTree(String seed, int pubkeyDepth, int firstIndex, int pubkeyCount, int security) {
        List<Hash> keys = new ArrayList<>(1 << pubkeyDepth);
        for (int i = 0; i < pubkeyCount; i++) {
            int idx = firstIndex + i;
            keys.add(HashFactory.ADDRESS.create(Winternitz.generateAddress(Hex.decode(seed), idx, security)));
        }
        return buildMerkleTree(keys);
    }


    public static List<byte[]> buildMerkleTransactionTree(List<Hash> leaves, Hash milestoneHash, Hash address) {
        if (leaves.isEmpty()) {
            leaves.add(Hash.NULL_HASH);
        }
        byte[] buffer;
        Sponge sha3 = SpongeFactory.create(SpongeFactory.Mode.S256);
        int row = 1;
        List<byte[]> virtualTransactionList = new ArrayList<byte[]>();
        int depth = getTreeDepth(leaves.size());
        while (leaves.size() > 1) {
            List<Hash> nextKeys = Arrays.asList(new Hash[(leaves.size() % 2 == 0 ? (leaves.size() / 2) : (leaves.size() / 2 + 1))]);
            for (int i = 0; i < nextKeys.size(); i++) {
                if (areLeavesNull(leaves, i)) continue;
                sha3.reset();
                Hash k1 = getLeaves(leaves, i * 2);
                Hash k2 = getLeaves(leaves, i * 2 + 1);
                buffer = computeParentHash(sha3, k1, k2);
                Hash parentHash = HashFactory.TRANSACTION.create(buffer);
                nextKeys.set(i, parentHash);
                virtualTransactionList.add(createVirtualTransaction(k1, k2, getParentMerkleIndex(row, depth, i * 2), milestoneHash, address, parentHash));
            }
            leaves = nextKeys;
            row++;
        }
        return virtualTransactionList;
    }

    private static Hash getLeaves(List<Hash> leaves, int index) {
        return index < leaves.size() ? leaves.get(index) : Hash.NULL_HASH;
    }

    private static long getParentMerkleIndex(int row, int depth, int i) {
        if (row == depth) {
            return 0; // root
        }
        long index = depth - row;
        return (long) Math.pow(2, index) + i / 2 - 1;
    }

    private static byte[] computeParentHash(Sponge sha3, Hash k1, Hash k2) {
        byte[] buffer;
        buffer = copyHash(k1);
        sha3.absorb(buffer, 0, buffer.length);
        buffer = copyHash(k2);
        sha3.absorb(buffer, 0, buffer.length);
        sha3.squeeze(buffer, 0, buffer.length);
        return buffer;
    }

    public static List<List<Hash>> buildMerkleTree(List<Hash> leaves) {
        if (leaves.isEmpty()) {
            leaves.add(Hash.NULL_HASH);
        }
        byte[] buffer;
        Sponge sha3 = SpongeFactory.create(SpongeFactory.Mode.S256);
        int depth = getTreeDepth(leaves.size());
        List<List<Hash>> merkleTree = new ArrayList<>(depth + 1);
        merkleTree.add(0, leaves);
        int row = 1;
        // hash two following keys together until only one is left -> merkle tree
        while (leaves.size() > 1) {
            // Take two following keys (i=0: (k0,k1), i=1: (k2,k3), ...) and get one crypto of them
            List<Hash> nextKeys = Arrays.asList(new Hash[(leaves.size() / 2)]);
            for (int i = 0; i < nextKeys.size(); i++) {
                if (areLeavesNull(leaves, i)) continue;
                sha3.reset();
                Hash k1 = leaves.get(i * 2);
                Hash k2 = leaves.get(i * 2 + 1);
                buffer = computeParentHash(sha3, k1, k2);
                nextKeys.set(i, HashFactory.TRANSACTION.create(buffer));
            }
            leaves = nextKeys;
            merkleTree.add(row++, leaves);
        }
        return merkleTree;
    }

    private static boolean areLeavesNull(List<Hash> leaves, int i) {
        if (leaves.get(i * 2) == null && leaves.get(i * 2 + 1) == null) {
            return true;
        }
        return false;
    }

    private static int getTreeDepth(int leavesNumber) {
        return (int) Math.ceil((float) (Math.log(leavesNumber) / Math.log(2)));
    }

    private static byte[] createVirtualTransaction(Hash branchHash, Hash trunkHash, long merkleIndex, Hash milestoneHash, Hash address, Hash transactionHash) {
        log.debug("New virtual transaction + " + transactionHash + " for milestone: " + milestoneHash + " with merkle index: " + merkleIndex + " [" + branchHash + ", " + trunkHash + " ]");
        return BundleUtils.createVirtualTransaction(branchHash, trunkHash, merkleIndex, milestoneHash.bytes(), address);
    }

    private static byte[] copyHash(Hash k2) {
        return Arrays.copyOfRange(k2 == null ? Hex.decode(Hash.NULL_HASH.bytes()) : k2.bytes(), 0, Hash.SIZE_IN_BYTES);
    }

    public static boolean validateMerkleSignature(List<TransactionViewModel> bundleTransactionViewModels, SpongeFactory.Mode mode, Hash validationAddress, int securityLevel, int depth) {

        final TransactionViewModel merkleTx = bundleTransactionViewModels.get(securityLevel);
        int keyIndex = RoundViewModel.getRoundIndex(merkleTx); // get keyindex

        //milestones sign the normalized hash of the sibling transaction. (why not bundle hash?)
        //TODO: check if its okay here to use bundle hash instead of tx hash
        byte[] bundleHash = Winternitz.normalizedBundle(merkleTx.getBundleHash().bytes());

        //validate leaf signature
        ByteBuffer bb = ByteBuffer.allocate(Sha3.HASH_LENGTH * securityLevel);

        for (int i = 0; i < securityLevel; i++) {
            byte[] bundleHashFragment = Arrays.copyOfRange(bundleHash, Winternitz.NORMALIZED_FRAGMENT_LENGTH * i, Winternitz.NORMALIZED_FRAGMENT_LENGTH * (i + 1));
            byte[] digest = Winternitz.digest(mode, bundleHashFragment, bundleTransactionViewModels.get(i).getSignature());
            bb.put(digest);
        }

        byte[] digests = bb.array();
        byte[] address = Winternitz.address(mode, digests);

        //validate Merkle path
        byte[] merkleRoot = Merkle.getMerkleRoot(mode, address,
                merkleTx.getSignature(), 0, keyIndex, depth);
        return HashFactory.ADDRESS.create(merkleRoot).equals(validationAddress);
    }

    public static List<List<Hash>> readKeyfile(File keyfile) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
            String[] fields = br.readLine().split(" ");
            int depth = Integer.parseInt(fields[0]);
            List<List<Hash>> result = new ArrayList<>(depth + 1);
            for (int i = 0; i <= depth; i++) {
                fields = br.readLine().split(" ");
                int leadingNulls = Integer.parseInt(fields[0]);
                List<Hash> row = new ArrayList<>();
                for (int j = 0; j < leadingNulls; j++) {
                    row.add(Hash.NULL_HASH);
                }
                for (int j = 0; j < fields[1].length() / 64; j++) {
                    row.add(HashFactory.TRANSACTION.create(fields[1].substring(j * 64, (j + 1) * 64)));
                }
                result.add(row);
            }
            return result;
        }
    }

    public static String getSeed(File keyfile) throws IOException {
        StringBuilder seedBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
            String[] fields = br.readLine().split(" ");
            seedBuilder.append(fields[1]);
        }
        return seedBuilder.toString();
    }


    public static void createKeyfile(List<List<Hash>> merkleTree, byte[] seed, int pubkeyDepth, int keyIndex, int keyfileIndex, String filename) throws IOException {
        // fill buffer
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            // write pubkey depth and seed into buffer
            bw.write(pubkeyDepth + " " + Hex.toHexString(seed) + " " + keyfileIndex + " " + keyIndex);
            bw.newLine();
            writeKeys(bw, merkleTree.get(0));
            for (int i = 1; i < merkleTree.size(); i++) {
                writeKeys(bw, merkleTree.get(i));
            }
        }
    }

    private static void writeKeys(BufferedWriter bw, List<Hash> keys) throws IOException {
        int leadingNulls = 0;
        while (keys.get(leadingNulls) == null) {
            leadingNulls++;
        }
        bw.write(leadingNulls + " ");
        for (int i = leadingNulls; i < keys.size(); i++) {
            if (keys.get(i) == null) {
                break;
            }
            bw.write(keys.get(i).toString());
        }
        bw.newLine();
    }

    public static byte[] padding(byte[] input, int length) {
        if (input.length < length) {
            byte[] output = new byte[length];
            System.arraycopy(input, 0, output, length - input.length, input.length);
            return output;
        } else {
            if (input.length > length) {
                return Arrays.copyOfRange(input, 0, length);
            } else {
                return Arrays.copyOfRange(input, 0, input.length);
            }

        }
    }
}
