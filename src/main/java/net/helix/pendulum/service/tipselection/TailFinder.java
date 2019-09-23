package net.helix.pendulum.service.tipselection;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

import java.util.Optional;

/**
 * Finds the tail of a bundle
 */

public interface TailFinder {
    /**
     * Method for finding a tail (current_index=0) of a bundle given any transaction in the associated bundle.
     *
     * @param hash The transaction hash of any transaction in the bundle.
     * @return Hash of the tail transaction, or {@code Empty} if the tail is not found.
     * @throws Exception If DB fails to retrieve transactions
     */
    Optional<Hash> findTail(Hash hash) throws Exception;

    /**
     * Method for finding a tail (current_index=0) of a bundle given any transaction in the associated bundle.
     *
     * @param tx any transaction in the bundle.
     * @return Hash of the tail transaction, or {@code Empty} if the tail is not found.
     * @throws Exception If DB fails to retrieve transactions
     */
    Optional<Hash> findTailFromTx(TransactionViewModel tx) throws Exception;
}
