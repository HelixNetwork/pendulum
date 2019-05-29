package net.helix.hlx.conf;

import net.helix.hlx.model.Hash;

import java.util.Set;

/**
 * Configs that should be used for tracking milestones
 */
public interface MilestoneConfig extends Config {
    /**
     * @return Descriptions#VALIDATOR_ADDRESSES
     */
    Hash getTrusteeAddress();
    /**
     * @return {@value Descriptions#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isDontValidateTestnetMilestoneSig();
    /**
     * @return {@value Descriptions#MS_DELAY}
     */
    int getMsDelay();
    /**
     * @return {@value Descriptions#MS_MIN_DELAY}
     */
    int getMinDelay();

    interface Descriptions {
        String VALIDATOR_ADDRESSES = "The addresses of nodes that are allowed to publish milestones";
        String DONT_VALIDATE_TESTNET_MILESTONE_SIG = "Disable coordinator validation on testnet";
        String MS_DELAY = "The desired milestone delay in seconds.";
        String MS_MIN_DELAY = "The minimum delay between publishing milestones.";
    }
}
