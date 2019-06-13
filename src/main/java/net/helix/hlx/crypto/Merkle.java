package net.helix.hlx.crypto;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.HashFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class Merkle {

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
        if(index != 0) {
            return Hash.NULL_HASH.bytes();
        }
        return hash;
    }

    public static byte[] getMerklePath(byte[][][] merkleTree, int keyIndex){
        byte[] merklePath = new byte[(merkleTree.length-1) * 32];
        for (int i = 0; i < merkleTree.length-1; i++) {
            byte[] subkey = merkleTree[i][keyIndex ^ 1];
            System.arraycopy((subkey == null ? Hash.NULL_HASH.bytes() : subkey), 0, merklePath, i * 32, 32);
            keyIndex /= 2;
        }
        return merklePath;
    }

    public static byte[][][] buildMerkleTree(String seed, int pubkeyDepth, int firstIndex, int pubkeyCount){
        byte[][] keys = new byte[1 << pubkeyDepth][32];
        for (int i = 0; i < pubkeyCount; i++) {
            int idx = firstIndex + i;
            keys[idx] = Winternitz.generateAddress(Hex.decode(seed), idx, 1);
        }
        byte[] buffer;
        Sponge sha3 = SpongeFactory.create(SpongeFactory.Mode.S256);
        byte[][][] merkleTree = new byte[pubkeyDepth+1][][];
        merkleTree[0] = keys;
        int row = 1;
        // hash two following keys together until only one is left -> merkle tree
        while (keys.length > 1) {
            // Take two following keys (i=0: (k0,k1), i=1: (k2,k3), ...) and get one crypto of them
            byte[][] nextKeys = new byte[keys.length / 2][32];
            for (int i = 0; i < nextKeys.length; i++) {
                if (keys[i * 2] == null && keys[i * 2 + 1] == null) {
                    // leave the combined key null as well
                    continue;
                }
                sha3.reset();
                byte[] k1 = keys[i * 2], k2 = keys[i * 2 + 1];
                buffer = Arrays.copyOfRange(k1 == null ? Hex.decode("0000000000000000000000000000000000000000000000000000000000000000") : k1, 0, 32);
                sha3.absorb(buffer, 0, buffer.length);
                buffer = Arrays.copyOfRange(k2 == null ? Hex.decode("0000000000000000000000000000000000000000000000000000000000000000") : k2, 0, 32);
                sha3.absorb(buffer, 0, buffer.length);
                sha3.squeeze(buffer, 0, buffer.length);
                nextKeys[i] = buffer;
            }
            keys = nextKeys;
            merkleTree[row++] = keys;
        }
        return merkleTree;
    }

    public static boolean validateMerkleSignature(List<TransactionViewModel> bundleTransactionViewModels, SpongeFactory.Mode mode, Hash validationAddress, int index, int securityLevel, int depth) {

        final TransactionViewModel merkleTx = bundleTransactionViewModels.get(securityLevel);

        //milestones sign the normalized hash of the sibling transaction. (why not bundle hash?)
        //TODO: check if its okay here to use bundle hash instead of tx hash
        byte[] bundleHash = new byte[Sha3.HASH_LENGTH];
        Winternitz.normalizedBundle(merkleTx.getBundleHash().bytes(), bundleHash);

        //validate leaf signature
        ByteBuffer bb = ByteBuffer.allocate(Sha3.HASH_LENGTH * securityLevel);

        for (int i = 0; i < securityLevel; i++) {
            byte[] bundleHashFragment = Arrays.copyOfRange(bundleHash, Winternitz.NORMALIZED_FRAGMENT_LENGTH * i, Winternitz.NORMALIZED_FRAGMENT_LENGTH * (i+1));
            byte[] digest = Winternitz.digest(mode, bundleHashFragment, bundleTransactionViewModels.get(i).getSignature());
            bb.put(digest);
        }

        byte[] digests = bb.array();
        byte[] address = Winternitz.address(mode, digests);

        //validate Merkle path
        byte[] merkleRoot = Merkle.getMerkleRoot(mode, address,
                merkleTx.getSignature(), 0, index, depth);

        return HashFactory.ADDRESS.create(merkleRoot).equals(validationAddress);
    }

    public static byte[][][] readKeyfile(File keyfile, StringBuilder seedBuilder) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
            String[] fields = br.readLine().split(" ");
            int depth = Integer.parseInt(fields[0]);
            seedBuilder.append(fields[1]);
            byte[][][] result = new byte[depth + 1][][];
            for (int i = 0; i <= depth; i++) {
                int col = 1 << (depth - i);
                result[i] = new byte[1 << (depth - i)][32];
                fields = br.readLine().split(" ");
                int leadingNulls = Integer.parseInt(fields[0]);
                for (int j = 0; j < fields[1].length() / 64; j++) {
                    result[i][j + leadingNulls] = Hex.decode(fields[1].substring(j * 64, (j+1) * 64));
                }
            }
            return result;
        }
    }

    public static void createKeyfile(byte[][][] merkleTree, byte[] seed, int pubkeyDepth, String filename) throws IOException {
        // fill buffer
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            // write pubkey depth and seed into buffer
            bw.write(pubkeyDepth + " " + Hex.toHexString(seed));
            bw.newLine();
            writeKeys(bw, merkleTree[0]);
            for (int i = 1; i < merkleTree.length; i++) {
                writeKeys(bw, merkleTree[i]);
            }
        }
    }

    private static void writeKeys(BufferedWriter bw, byte[][] keys) throws IOException {
        int leadingNulls = 0;
        while (keys[leadingNulls] == null) {
            leadingNulls++;
        }
        bw.write(leadingNulls + " ");
        for (int i = leadingNulls; i < keys.length; i++) {
            if (keys[i] == null) {
                break;
            }
            bw.write(Hex.toHexString(keys[i]));
        }
        bw.newLine();
    }

    public static byte[] padding(byte[] input, int length){
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
