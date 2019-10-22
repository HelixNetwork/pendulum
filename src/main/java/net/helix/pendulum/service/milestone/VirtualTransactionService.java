package net.helix.pendulum.service.milestone;

import net.helix.pendulum.controllers.TransactionViewModel;


public interface VirtualTransactionService {

    /**
     * It tries to rebuild the virtual parent transaction for given trasaction model, parent transaction could be build if the sibling transaction already exists,
     * then it will try to rebuild the next parent transaction an so on.
     * @param transactionViewModel
     * @return number of the built transactions.
     */
    int rebuildVirtualTransactionsIfPossible(TransactionViewModel transactionViewModel);
}
