package net.helix.hlx;

import java.util.Random;
import net.helix.hlx.model.Hash;
import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.persistables.Transaction;


public class TransactionTestUtils {

    private static final Random RND = new Random();

    
    /**
     * Generates a transaction hash.
     * 
     * @return The transaction hash
     */
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
