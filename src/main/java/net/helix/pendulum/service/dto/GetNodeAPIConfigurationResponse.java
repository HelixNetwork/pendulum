package net.helix.pendulum.service.dto;

import net.helix.pendulum.conf.PendulumConfig;

/**
 * Contains information about the result of a successful {@link net.helix.pendulum.service.API#getNodeAPIConfigurationStatement()} API call.
 * See {@link net.helix.pendulum.service.API#getNodeAPIConfigurationStatement()} for how this response is created.
 */
public class GetNodeAPIConfigurationResponse extends AbstractResponse {
    private int maxFindTransactions;
    private int maxRequestsList;
    private int maxTransactionStrings;
    private int maxBodyLength;
    private boolean testNet;
    private int milestoneStartIndex;

    /**
     * Use factory method {@link GetNodeAPIConfigurationResponse#create(PendulumConfig) to create response.}
     */
    private GetNodeAPIConfigurationResponse() {
    }

    /**
     * Creates a new {@link GetNodeAPIConfigurationResponse} with configuration options that should be returned.
     * <b>Make sure that you do not return secret informations (e.g. passwords, secrets...).</b>
     *
     * @param configuration {@link PendulumConfig} used to create response.
     * @return an {@link GetNodeAPIConfigurationResponse} filled with actual config options.
     */
    public static AbstractResponse create(PendulumConfig configuration) {
        if(configuration == null) {
            throw new IllegalStateException("configuration must not be null!");
        }

        final GetNodeAPIConfigurationResponse res = new GetNodeAPIConfigurationResponse();

        res.maxFindTransactions = configuration.getMaxFindTransactions();
        res.maxRequestsList = configuration.getMaxRequestsList();
        res.maxTransactionStrings = configuration.getMaxTransactionStrings();
        res.maxBodyLength = configuration.getMaxBodyLength();
        res.testNet = configuration.isTestnet();
        res.milestoneStartIndex = configuration.getMilestoneStartIndex();

        return res;
    }

    /** {@link PendulumConfig#getMaxFindTransactions()} */
    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    /** {@link PendulumConfig#getMaxRequestsList()} */
    public int getMaxRequestsList() {
        return maxRequestsList;
    }

    /** {@link PendulumConfig#getMaxTransactionStrings()} */
    public int getMaxTransactionStrings() {
        return maxTransactionStrings;
    }

    /** {@link PendulumConfig#getMaxBodyLength()} */
    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    /** {@link PendulumConfig#isTestnet()} */
    public boolean isTestNet() {
        return testNet;
    }

    /** {@link PendulumConfig#getMilestoneStartIndex()} */
    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }
}
