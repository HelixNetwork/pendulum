package net.helix.hlx.service.dto;

import net.helix.hlx.conf.HelixConfig;

/**
 * Contains information about the result of a successful {@link net.helix.hlx.service.API#getNodeAPIConfigurationStatement()} API call.
 * See {@link net.helix.hlx.service.API#getNodeAPIConfigurationStatement()} for how this response is created.
 */
public class GetNodeAPIConfigurationResponse extends AbstractResponse {
    private int maxFindTransactions;
    private int maxRequestsList;
    private int maxBytes;
    private int maxBodyLength;
    private boolean testNet;
    private int milestoneStartIndex;

    /**
     * Use factory method {@link GetNodeAPIConfigurationResponse#create(HelixConfig) to create response.}
     */
    private GetNodeAPIConfigurationResponse() {
    }

    /**
     * Creates a new {@link GetNodeAPIConfigurationResponse} with configuration options that should be returned.
     * <b>Make sure that you do not return secret informations (e.g. passwords, secrets...).</b>
     *
     * @param configuration {@link HelixConfig} used to create response.
     * @return an {@link GetNodeAPIConfigurationResponse} filled with actual config options.
     */
    public static AbstractResponse create(HelixConfig configuration) {
        if(configuration == null) {
            throw new IllegalStateException("configuration must not be null!");
        }

        final GetNodeAPIConfigurationResponse res = new GetNodeAPIConfigurationResponse();

        res.maxFindTransactions = configuration.getMaxFindTransactions();
        res.maxRequestsList = configuration.getMaxRequestsList();
        res.maxBytes = configuration.getMaxBytes();
        res.maxBodyLength = configuration.getMaxBodyLength();
        res.testNet = configuration.isTestnet();
        res.milestoneStartIndex = configuration.getMilestoneStartIndex();

        return res;
    }

    /** {@link HelixConfig#getMaxFindTransactions()} */
    public int getMaxFindTransactions() {
        return maxFindTransactions;
    }

    /** {@link HelixConfig#getMaxRequestsList()} */
    public int getMaxRequestsList() {
        return maxRequestsList;
    }

    /** {@link HelixConfig#getMaxBytes()} */
    public int getMaxBytes() {
        return maxBytes;
    }

    /** {@link HelixConfig#getMaxBodyLength()} */
    public int getMaxBodyLength() {
        return maxBodyLength;
    }

    /** {@link HelixConfig#isTestnet()} */
    public boolean isTestNet() {
        return testNet;
    }

    /** {@link HelixConfig#getMilestoneStartIndex()} */
    public int getMilestoneStartIndex() {
        return milestoneStartIndex;
    }
}
