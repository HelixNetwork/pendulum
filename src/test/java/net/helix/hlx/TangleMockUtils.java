package net.helix.hlx;

import net.helix.hlx.controllers.TransactionViewModel;
import net.helix.hlx.model.Hash;
import net.helix.hlx.model.persistables.Transaction;
import net.helix.hlx.storage.Tangle;
import net.helix.hlx.utils.Pair;

import org.mockito.Mockito;


public class TangleMockUtils {

    /**
     * Creates an empty transaction, which is marked filled and parsed.
     * This transaction is returned when the hash is asked to load in the tangle object
     * 
     * @param tangle mocked tangle object that shall retrieve a milestone object when being queried for it
     * @param hash transaction hash
     * @return The newly created (empty) transaction
     */
    public static Transaction mockTransaction(Tangle tangle, Hash hash) {
        Transaction transaction = new Transaction();
        transaction.bytes = new byte[0];
        transaction.type = TransactionViewModel.FILLED_SLOT;
        transaction.parsed = true;

        return mockTransaction(tangle, hash, transaction);
    }

    /**
     * Mocks the tangle object by checking for the hash and returning the transaction.
     * 
     * @param tangle mocked tangle object that shall retrieve a milestone object when being queried for it
     * @param hash transaction hash
     * @param transaction the transaction we send back
     * @return The transaction
     */
    public static Transaction mockTransaction(Tangle tangle, Hash hash, Transaction transaction) {
        try {
            Mockito.when(tangle.load(Transaction.class, hash)).thenReturn(transaction);
            Mockito.when(tangle.getLatest(Transaction.class, Hash.class)).thenReturn(new Pair<>(hash, transaction));
        } catch (Exception e) {
            // the exception can not be raised since we mock
        }

        return transaction;
    }

}
