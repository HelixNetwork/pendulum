package net.helix.pendulum.utils;

import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.crypto.merkle.MerkleNode;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.crypto.merkle.MerkleTree;
import net.helix.pendulum.crypto.merkle.impl.MerkleTreeImpl;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 *  It contains the required methods to manage key files (read, write, build)
 */
public class KeyfileUtil {

    public static String getSeed(File keyfile) throws IOException {
        StringBuilder seedBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
            String[] fields = br.readLine().split(" ");
            seedBuilder.append(fields[1]);
        }
        return seedBuilder.toString();
    }

    public static byte[] getKeyTreeRoot(String seed, int pubkeyDepth, int firstIndex, int pubkeyCount, int security) {
        List<List<MerkleNode>> tree = buildMerkleKeyTree(seed, pubkeyDepth,firstIndex,pubkeyCount,security);
        return tree.get(tree.size()-1).get(0).bytes();

    }
    public static List<List<MerkleNode>> buildMerkleKeyTree(String seed, int pubkeyDepth, int firstIndex, int pubkeyCount, int security) {
        List<Hash> keys = new ArrayList<>(1 << pubkeyDepth);
        for (int i = 0; i < pubkeyCount; i++) {
            int idx = firstIndex + i;
            keys.add(createKeyHash(seed, security, idx));
        }
        return (new MerkleTreeImpl()).buildMerkleTree(keys, MerkleOptions.getDefault());
    }

    public static void writeKeys(BufferedWriter bw, List<MerkleNode> keys) throws IOException {
        int leadingNulls = 0;
        while (keys.get(leadingNulls) == null) {
            leadingNulls++;
        }
        bw.write(leadingNulls + " ");
        for (int i = leadingNulls; i < keys.size(); i++) {
            if (keys.get(i) == null) {
                break;
            }
            bw.write(writeMerkleNode(keys.get(i)));
        }
        bw.newLine();
    }

    public static void createKeyfile(List<List<MerkleNode>> merkleTree, byte[] seed, int pubkeyDepth, int keyIndex, int keyfileIndex, String filename) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            bw.write(pubkeyDepth + " " + Hex.toHexString(seed) + " " + keyfileIndex + " " + keyIndex);
            bw.newLine();
            writeKeys(bw, merkleTree.get(0));
            for (int i = 1; i < merkleTree.size(); i++) {
                writeKeys(bw, merkleTree.get(i));
            }
        }
    }

    public static List<List<MerkleNode>> readKeyfile(File keyfile) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
            String[] fields = br.readLine().split(" ");
            int depth = Integer.parseInt(fields[0]);
            List<List<MerkleNode>> result = new ArrayList<>(depth + 1);
            for (int i = 0; i <= depth; i++) {
                fields = br.readLine().split(" ");
                int leadingNulls = Integer.parseInt(fields[0]);
                List<MerkleNode> row = new ArrayList<>();
                for (int j = 0; j < leadingNulls; j++) {
                    row.add(Hash.NULL_HASH);
                }
                for (int j = 0; j < fields[1].length() / 64; j++) {
                    row.add(HashFactory.ADDRESS.create(fields[1].substring(j * 64, (j + 1) * 64)));
                }
                result.add(row);
            }
            return result;
        }
    }

    private static Hash createKeyHash(String seed, int security, int idx) {
        return HashFactory.ADDRESS.create(Winternitz.generateAddress(Hex.decode(seed), idx, security));
    }

    private static String writeMerkleNode(MerkleNode key) {
        return key.toString();
    }
}
