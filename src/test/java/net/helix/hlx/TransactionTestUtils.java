package net.helix.hlx;

import java.util.Random;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.crypto.SpongeFactory;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.TransactionHash;

import net.helix.hlx.model.persistables.Transaction;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;

public class TransactionTestUtils {

    private static final Random RND = new Random();

    /**
     * Generates a transaction with the provided hex string.
     * Transaction hash is calculated and added.
     *
     * @param hex The transaction hex to use
     * @return The transaction
     */
    public static TransactionViewModel createTransactionWithHex(String hex) {
        byte[] hbytes = Hex.decode(hex);
        return new TransactionViewModel(hbytes, TransactionHash.calculate(SpongeFactory.Mode.S256, hbytes));
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

    public static Hash getTransactionHash() {
        byte[] bytes = new byte[Hash.SIZE_IN_BYTES];
        RND.nextBytes(bytes);
        return net.helix.hlx.model.HashFactory.TRANSACTION.create(bytes);
    }

    /**
     * Generates a transaction.
     * 
     * @return The transaction
     */
    public static Transaction getTransaction() {
        byte[] bytes = new byte[TransactionViewModel.SIZE];
        RND.nextBytes(bytes);
        return buildTransaction(bytes);
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

}
