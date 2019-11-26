package net.helix.pendulum.service.cache;

import net.helix.pendulum.Pendulum;
import net.helix.pendulum.controllers.BundleViewModel;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;
import net.helix.pendulum.model.persistables.Milestone;

import java.util.Collection;
import java.util.List;

/**
 * Cache service for quick access to the Tangle.
 *
 * The contract is that TangleCache never returns null. If a required key is missing,
 * it is loaded from the Tangle, similar to the way Guava Cache is implemented.
 *
 * Date: 2019-11-14
 * Author: zhelezov
 */
public interface TangleCache extends Pendulum.Initializable {
    /**
     * Cached <code>TransactionViewModel.fromHash(..)</code>
     *
     * @param hash transaction hash
     * @return TransactionViewModel
     */
    TransactionViewModel getTxVM(Hash hash);

    /**
     * Invalidate a hash entry (in case it is updated)
     *
     * @param hash tx hash to be updated
     */
    void invalidateTxHash(Hash hash);

    /**
     * Returns a list of hashes from
     * @param hash
     * @return
     */
    List<Hash> fromMerkleRoot(Hash hash);

    /**
     * Sorts the list and returns the Merkle root
     *
     *
     * @param hashes
     * @return
     */
    Hash toMerkleRoot(Collection<Hash> hashes);

    /**
     * Get a list of hashes a given tx belongs to
     *
     * @param txHash
     * @return
     */
    List<Hash> getBundle(Hash txHash);

    /**
     *
     * @param txHash
     * @return If a tx is a milestone, return the list of hashes in the milestone bundle,
     *         otherwise a empty list
     */
    List<Hash> milestoneBundle(Hash txHash);

    /**
     * If <code>txHash</code> is part of a milestone bundle, return first
     * the list
     *
     */
    List<Hash> approvees(Hash txHash);
}
