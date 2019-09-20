package net.helix.pendulum.service.nominee;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;

public interface NomineeService {

    /**
     * <p>
     * Analyzes the given transaction to determine if it is a valid nominee transaction.
     * </p>
     * </p>
     * It first checks if all transactions that belong to the nominee bundle are known already
     *
     *
     * (and only then verifies
     * the signature to analyze if the given nominee transaction was really issued by a valid nominee) <- might not be needed.
     * </p>
     *
     * @param transactionViewModel transaction that shall be analyzed
     * @param mode hash mode
     * @param securityLevel
     * @return validity status of the transaction
     * @throws NomineeException if anything unexpected goes wrong while validating the nominee transaction
     */
    NomineeValidity validateNominees(TransactionViewModel transactionViewModel, int roundIndex,
                                     SpongeFactory.Mode mode, int securityLevel) throws NomineeException;


}
