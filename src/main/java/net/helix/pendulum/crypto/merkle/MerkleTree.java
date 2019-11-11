package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;
import net.helix.pendulum.model.Hash;

import java.util.Arrays;
import java.util.List;

public interface MerkleTree {

    /**
     * Gets merkle path from the specified key index (Used in merkle transaction message
     * @param merkleTree
     * @param keyIndex
     * @return
     */
    List<MerkleNode> getMerklePath(List<List<MerkleNode>> merkleTree, int keyIndex);

    /**
     * Build flat merkle tree
     * @param leaves hash leaves
     * @param options
     * @return
     */
    List<MerkleNode> buildMerkle(List<Hash> leaves, MerkleOptions options);

    /**
     * Build merkle tree as lists of lists
     * @param leaves
     * @param options
     * @return
     */
    List<List<MerkleNode>> buildMerkleTree(List<Hash> leaves, MerkleOptions options);

    /**
     * Gets merkle tree root for given leaves
     * @param leaves
     * @param options
     * @return
     */
    byte[] getMerkleRoot(List<Hash> leaves, MerkleOptions options);

    /**
     * Get merkle tree root, based on merkle path
     * @param mode
     * @param hash
     * @param bytes merkle path bytes
     * @param offset
     * @param indexIn
     * @param size of elements in path
     * @return
     */
    byte[] getMerkleRoot(SpongeFactory.Mode mode, byte[] hash, byte[] bytes, int offset, int indexIn, int size);

    /**
     * Validate bundles which contains merkle transaction
     * First it validates sender transactions signature
     * @param bundleTransactionViewModels
     * @param options
     * @return
     */
    boolean validateMerkleSignature(List<TransactionViewModel> bundleTransactionViewModels, MerkleOptions options);

    static byte[] padding(byte[] input, int length) {
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
