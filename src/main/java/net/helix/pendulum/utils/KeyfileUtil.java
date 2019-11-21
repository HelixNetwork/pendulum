package net.helix.pendulum.utils;

import net.helix.pendulum.crypto.Winternitz;
import net.helix.pendulum.crypto.merkle.MerkleFactory;
import net.helix.pendulum.crypto.merkle.MerkleOptions;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.HashFactory;
import org.bouncycastle.util.encoders.Hex;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * It contains the required methods to manage key files (read, write, build)
 */
public class KeyfileUtil {

    private static int ADDRESS_STRING_LENGTH = Hash.SIZE_IN_BYTES * 2;

    public String getSeed(File keyfile) throws IOException {
        StringBuilder seedBuilder = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(keyfile))) {
            String[] fields = br.readLine().split(" ");
            seedBuilder.append(fields[1]);
        }
        return seedBuilder.toString();
    }

    public List<List<Hash>> buildMerkleKeyTree(String seed, int pubkeyDepth, int firstIndex, int pubkeyCount, int security) {
        List<Hash> keys = new ArrayList<>(1 << pubkeyDepth);
        for (int i = 0; i < pubkeyCount; i++) {
            int idx = firstIndex + i;
            keys.add(createKeyHash(seed, security, idx));
        }
        return MerkleFactory.create(MerkleFactory.MerkleTree, MerkleOptions.getDefault()).buildMerkleTree(keys);
    }

    public void writeKeys(BufferedWriter bw, List<Hash> keys) throws IOException {
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

    public void createKeyfile(List<List<Hash>> merkleTree, byte[] seed, int pubkeyDepth, int keyIndex, int keyfileIndex, String filename) throws IOException {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filename))) {
            bw.write(pubkeyDepth + " " + Hex.toHexString(seed) + " " + keyfileIndex + " " + keyIndex);
            bw.newLine();
            writeKeys(bw, merkleTree.get(0));
            for (int i = 1; i < merkleTree.size(); i++) {
                writeKeys(bw, merkleTree.get(i));
            }
        }
    }

    public List<List<Hash>> readKeyfile(File keyfile) throws IOException {
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
                for (int j = 0; j < fields[1].length() / ADDRESS_STRING_LENGTH; j++) {
                    row.add(HashFactory.ADDRESS.create(fields[1].substring(j * ADDRESS_STRING_LENGTH, (j + 1) * ADDRESS_STRING_LENGTH)));
                }
                result.add(row);
            }
            return result;
        }
    }

    private Hash createKeyHash(String seed, int security, int idx) {
        return HashFactory.ADDRESS.create(Winternitz.generateAddress(Hex.decode(seed), idx, security));
    }

    private String writeMerkleNode(Hash key) {
        return key.toString();
    }
}
