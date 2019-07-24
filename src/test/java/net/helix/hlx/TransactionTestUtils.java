package net.helix.hlx;

import java.nio.ByteBuffer;
import java.util.Random;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.TransactionHash;

import net.helix.hlx.model.persistables.Transaction;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;


public class TransactionTestUtils {

    private static final Random RND = new Random(1);

    /**
     * Updates the transaction index in bytes.
     * 
     * @param tx The transaction to update
     * @param currentIndex The new index to set the transaction to
     */
    public static void setCurrentIndex(TransactionViewModel tx, long currentIndex) {
        setCurrentIndex(tx.getBytes(), currentIndex);
    }

    /**
     * Updates the transaction index bytes.
     * 
     * @param bytes The bytes to update
     * @param currentIndex The new index to set the transaction to
     */
    public static void setCurrentIndex(byte[] bytes, long currentIndex) {
        ByteBuffer.wrap(bytes).putLong(TransactionViewModel.CURRENT_INDEX_OFFSET, currentIndex);
    }

    /**
     * Updates the last transaction index in bytes.
     * 
     * @param tx The transaction to update
     * @param lastIndex The new last index to set the transaction to
     */
    public static void setLastIndex(TransactionViewModel tx, long lastIndex) {
        setLastIndex(tx.getBytes(), lastIndex);
    }

    /**
     * Updates the last transaction index bytes.
     * 
     * @param bytes The bytes to update
     * @param lastIndex The new last index to set the transaction to
     */
    public static void setLastIndex(byte[] bytes, long lastIndex) {
        ByteBuffer.wrap(bytes).putLong(TransactionViewModel.LAST_INDEX_OFFSET, lastIndex);
    }

    /**
     * Generates a transaction with a hash.
     * Transaction last and current index are set to the index provided.
     * 
     * @param index The index to set the transaction to
     * @return A transaction which is located on the end of its (nonexistent) bundle
     */
    public static TransactionViewModel createBundleHead(int index) {
        TransactionViewModel tx = new TransactionViewModel(getTransactionBytes(), getTransactionHash());
        setLastIndex(tx, index);
        setCurrentIndex(tx, index);
        return tx;
    }
    
    /**
     * Generates a transaction with the specified trunk and branch, and bundle hash from trunk.
     * This transaction indices are updated to match the trunk index.
     * 
     * @param trunkTx The trunk transaction
     * @param branchHash The branch transaction hash
     * @return A transaction in the same bundle as trunk, with its index 1 below trunk index
     */
    public static TransactionViewModel createTransactionWithTrunkBundleHash(TransactionViewModel trunkTx, Hash branchHash) {
        byte[] txBytes = getTransactionBytesWithTrunkAndBranch(trunkTx.getHash(), branchHash);
        setCurrentIndex(txBytes, trunkTx.getCurrentIndex() - 1);
        setLastIndex(txBytes, trunkTx.lastIndex());
        System.arraycopy(trunkTx.getBytes(), TransactionViewModel.BUNDLE_OFFSET, txBytes,
                TransactionViewModel.BUNDLE_OFFSET, TransactionViewModel.BUNDLE_SIZE);
        TransactionViewModel tx = new TransactionViewModel(txBytes, getTransactionHash());
        return tx;
    }

    /**
     * Generates a transaction with the provided hex.
     * If the hex is not enough to make a full transaction, 0s are appended.
     * Transaction hash is calculated and added.
     * 
     * @param hex The transaction hex to use
     * @return The transaction
     */
    public static TransactionViewModel createTransactionWithHex(String hex) {
        String expandedHex  = expandHex(hex);
        byte[] bytes = Hex.decode(expandedHex);
        return createTransactionFromBytes(bytes);
    }

    /**
     * Creates a {@link TransactionViewModel} from the supplied bytes.
     * Bytes are not checked for size and content.
     * 
     * @param bytes The transaction bytes
     * @return The transaction
     */
    public static TransactionViewModel createTransactionFromBytes(byte[] bytes) {
        return new TransactionViewModel(bytes, TransactionHash.calculate(SpongeFactory.Mode.S256, bytes));
    }

    /**
     * Generates a transaction with the provided hex, trunk and hash.
     * If the hex is not enough to make a full transaction, 0s are appended.
     * Transaction hash is calculated and added.
     * 
     * @param hex The transaction hex to use
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction
     */
    public static TransactionViewModel createTransactionWithTrunkAndBranch(String hex, Hash trunk, Hash branch) {
        byte[] bytes = createTransactionWithTrunkAndBranchBytes(hex, trunk, branch);
        return createTransactionFromBytes(bytes);
    }

    /**
     * Generates transaction bytes with the provided hex, trunk and hash.
     * If the hex is not enough to make a full transaction, 0s are appended.
     * 
     * @param hex The transaction hex to use
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction bytes
     */
    public static byte[] createTransactionWithTrunkAndBranchBytes(String hex, Hash trunk, Hash branch) {
        String expandedHex = expandHex(hex);
        byte[] bytes = Hex.decode(expandedHex);
        return getTransactionBytesWithTrunkAndBranchBytes(bytes, trunk, branch);
    }

    /**
     * Generates transaction bytes with the provided trunk and hash.
     * No validation is done on the resulting bytes, so fields are not valid except trunk and branch. 
     * 
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction bytes
     */
    public static byte[] getTransactionBytesWithTrunkAndBranch(Hash trunk, Hash branch) {
        byte[] bytes = getTransactionBytes();
        return getTransactionBytesWithTrunkAndBranchBytes(bytes, trunk, branch);
    }

    /**
     * Generates transaction bytes with the provided bytes, trunk and hash.
     * 
     * @param bytes The transaction bytes
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction bytes
     */
    public static byte[] getTransactionBytesWithTrunkAndBranchBytes(byte[] bytes, Hash trunk, Hash branch) {
        System.arraycopy(trunk.bytes(), 0, bytes, TransactionViewModel.TRUNK_TRANSACTION_OFFSET,
                TransactionViewModel.TRUNK_TRANSACTION_SIZE);
        System.arraycopy(branch.bytes(), 0, bytes, TransactionViewModel.BRANCH_TRANSACTION_OFFSET,
                TransactionViewModel.BRANCH_TRANSACTION_SIZE);
        return bytes;
    }

    /**
     * @param hex The hex to change.
     * @return The changed hex
     */
    public static String nextWord(String hex, int index) {
        return pad(Integer.toHexString(index+1));
    }

    private static String pad(String hex) {
        return StringUtils.rightPad(hex, TransactionViewModel.SIZE*2, '0');
    }

    /**
     * Generates a transaction.
     * 
     * @return The transaction
     */
    public static Transaction getTransaction() {
        byte[] bytes = getTransactionBytes();
        return buildTransaction(bytes);
    }

    /**
     * Generates bytes for a transaction.
     * 
     * @return The transaction bytes
     */
    public static byte[] getTransactionBytes() {
        return getBytes(TransactionViewModel.SIZE);
    }

    /**
     * Generates a transaction with only 0s.
     * 
     * @return The transaction
     */
    public static Transaction get0Transaction() {
        byte[] bytes = new byte[TransactionViewModel.SIZE];
        return buildTransaction(bytes);
    }

    /**
     * Generates a transaction with trunk and hash.
     * 
     * @param trunk The trunk transaction hash
     * @param branch The branch transaction hash
     * @return The transaction
     */
    public static Transaction createTransactionWithTrunkAndBranch(Hash trunk, Hash branch) {
        byte[] bytes = getBytes(TransactionViewModel.SIZE);
        getTransactionBytesWithTrunkAndBranchBytes(bytes, trunk, branch);
        return buildTransaction(bytes);
    }

    public static Hash getTransactionHash() {
        byte[] out = getBytes(Hash.SIZE_IN_BYTES);
        return net.helix.hlx.model.HashFactory.TRANSACTION.create(out);
    }

    /**
     * Builds a transaction from the bytes.
     * Make sure the bytes are in the correct order
     * 
     * @param bytes The bytes to build the transaction
     * @return The created transaction
     */
    public static Transaction buildTransaction(byte[] bytes) {  
        Transaction t = new Transaction();
        t.read(bytes);
        t.readMetadata(bytes);
        return t;
    }

    /**
     * Appends 0s to the supplied hex until the hex are of size {@link TransactionViewModel.SIZE}.
     * 
     * @param hex the hex to append to.
     * @return The expanded hex string
     */
    private static String expandHex(String hex) {
        return hex + StringUtils.repeat('0', TransactionViewModel.SIZE * 2 - hex.length());
    }

    /**
     * Generates 'random' bytes of specified size.
     * Not truly random as we always use the same seed.
     * 
     * @param size the amount of bytes to generate
     * @return The bytes
     */
    private static byte[] getBytes(int size) {
        byte[] out = new byte[size];
        RND.nextBytes(out);
        return out;
    }

}
