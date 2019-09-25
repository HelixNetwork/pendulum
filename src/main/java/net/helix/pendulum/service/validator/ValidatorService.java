package net.helix.pendulum.service.validator;

import net.helix.pendulum.controllers.TransactionViewModel;
import net.helix.pendulum.crypto.SpongeFactory;

public interface ValidatorService {

    /**
     * <p>
     * Analyzes the given transaction to determine if it is a valid validator transaction.
     * </p>
     * </p>
     * It first checks if all transactions that belong to the validator bundle are known already
     *
     *
     * (and only then verifies
     * the signature to analyze if the given validator transaction was really issued by a valid validator) <- might not be needed.
     * </p>
     *
     * @param transactionViewModel transaction that shall be analyzed
     * @param mode hash mode
     * @param securityLevel
     * @return validity status of the transaction
     * @throws ValidatorException if anything unexpected goes wrong while validating the validator transaction
     */
    ValidatorValidity validateValidators(TransactionViewModel transactionViewModel, int roundIndex,
                                       SpongeFactory.Mode mode, int securityLevel) throws ValidatorException;


}
