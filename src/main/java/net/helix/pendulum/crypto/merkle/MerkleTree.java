package net.helix.pendulum.crypto.merkle;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

import java.util.Arrays;
import java.util.List;

public interface MerkleTree {

    /**
     * Gets merkle path from the specified key index (Used in merkle transaction message
     *
     * @param merkleTree
     * @param keyIndex
     * @return
     */
    List<Hash> getMerklePath(List<List<Hash>> merkleTree, int keyIndex);

    /**
     * Build merkle tree as lists of lists
     *
     * @param leaves
     * @return
     */
    List<List<Hash>> buildMerkleTree(List<Hash> leaves);

    /**
     * Gets merkle tree root for given leaves
     *
     * @param leaves
     * @return
     */
    byte[] getMerkleRoot(List<Hash> leaves);

    /**
     * Get merkle tree root, based on merkle path
     *
     * @param mode
     * @param hash
     * @param bytes   merkle path bytes
     * @param offset
     * @param indexIn
     * @param size    of elements in path
     * @return
     */
    byte[] getMerkleRoot(byte[] hash, byte[] bytes, int offset, int indexIn, int size);

    /**
     * Validate bundles which contains merkle transaction
     * First it validates sender transactions signature
     *
     * @param bundleTransactionViewModels
     * @return
     */
    boolean validateMerkleSignature(List<TransactionViewModel> bundleTransactionViewModels);

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
