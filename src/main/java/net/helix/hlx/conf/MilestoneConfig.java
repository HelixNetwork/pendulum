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
    Set<Hash> getValidatorAddresses();
    /**
     * @return {@value Descriptions#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isDontValidateTestnetMilestoneSig();
    interface Descriptions {
        String VALIDATOR_ADDRESSES = "The addresses of nodes that are allowed to publish milestones";
        String DONT_VALIDATE_TESTNET_MILESTONE_SIG = "Disable coordinator validation on testnet";
    }
}
