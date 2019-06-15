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
        Transaction tx = new Transaction();
        tx.read(bytes);
        return tx;
    }

    /**
     * Generates a transaction with only 0s.
     * 
     * @return The transaction
     */
    public static Transaction get0Transaction() {
        byte[] bytes = new byte[TransactionViewModel.SIZE];
        Transaction tx = new Transaction();
        tx.read(bytes);
        return tx;
    }

}
