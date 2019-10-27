package net.helix.pendulum.service.spentaddresses;

import net.helix.pendulum.conf.PendulumConfig;
import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.model.Hash;

import java.util.Collection;

/**
 *
 * Check and calculate spent addresses
 *
 */
public interface SpentAddressesService {

    /**
     *
     * @param addressHash
     * @return <code>true</code> if it was, else <code>false</code>
     * @throws SpentAddressesException
     */
    boolean wasAddressSpentFrom(Hash addressHash) throws SpentAddressesException;

    /**
     * Persist all the verifiable spent from a given list of transactions
     * @param transactions transactions to obtain spends from
     * @throws SpentAddressesException
     */
    void persistSpentAddresses(Collection<TransactionViewModel> transactions) throws SpentAddressesException;
}
