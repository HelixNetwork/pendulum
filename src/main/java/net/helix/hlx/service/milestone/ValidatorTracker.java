package net.helix.hlx.service.milestone;

import net.helix.hlx.model.Hash;

public interface ValidatorTracker {

    boolean processTrusteeTransaction(Hash transactionHash) throws Exception;

    void updateValidatorAddresses(Hash transaction, int roundIndex) throws Exception;

    void analyzeTrusteeTransactions() throws Exception;

    void collectNewTrusteeTransactions() throws Exception;

    void start();

    void shutdown();

}
