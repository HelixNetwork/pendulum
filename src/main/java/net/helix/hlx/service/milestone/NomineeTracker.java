package net.helix.hlx.service.milestone;

import net.helix.hlx.model.Hash;

import java.util.Set;

public interface NomineeTracker {

    public Set<Hash> getLatestValidators();

    Set<Hash> getValidatorsOfRound(int roundIndex) throws Exception;

    boolean processTrusteeTransaction(Hash transactionHash) throws Exception;

    Set<Hash> getValidatorAddresses(Hash transaction) throws Exception;

    void analyzeTrusteeTransactions() throws Exception;

    void collectNewTrusteeTransactions() throws Exception;

    void start();

    void shutdown();

}
