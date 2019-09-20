package net.helix.pendulum.service.nominee;

import net.helix.pendulum.model.Hash;

import java.util.Set;

public interface NomineeTracker {

    Set<Hash> getLatestNominees();

    int getStartRound();

    Set<Hash> getNomineesOfRound(int roundIndex) throws Exception;

    boolean processNominees(Hash transactionHash) throws Exception;

    Set<Hash> getNomineeAddresses(Hash transaction) throws Exception;

    Hash getLatestNomineeHash();

    void analyzeCuratorTransactions() throws Exception;

    void collectNewCuratorTransactions() throws Exception;

    void start();

    void shutdown();

}