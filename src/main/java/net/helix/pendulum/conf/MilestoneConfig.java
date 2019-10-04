package net.helix.pendulum.conf;

import net.helix.pendulum.model.Hash;

import java.util.Set;

/**
 * Configs that should be used for tracking milestones
 */
public interface MilestoneConfig extends Config {

    /**
     * @return {@value Descriptions#VALIDATOR}
     */
    boolean isValidator();
    /**
     * @return {@value Descriptions#VALIDATOR_PATH}
     */
    String getValidatorPath();
    /**
     * @return {@value Descriptions#VALIDATOR_SEED_PATH}
     */
    String getValidatorSeedfile();
    /**
     *  Returns is validator is enabled, is dependent on isValidator and ValidatorPath
     * @return {@value Descriptions#VALIDATOR}
     */
    boolean isValidatorEnabled();
    /**
     * @return Descriptions#INITIAL_VALIDATORS
     */
    Set<Hash> getInitialValidators();
    /**
     * @return {@value Descriptions#DONT_VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isDontValidateTestnetMilestoneSig();
    /**
     * @return {@value Descriptions#GENESIS_TIME}
     */
    long getGenesisTime();
    /**
     * @return {@value Descriptions#GENESIS_TIME}
     */
    long getGenesisTimeTestnet();
    /**
     * @return {@value Descriptions#ROUND_DURATION}
     */
    int getRoundDuration();
    /**
     * @return {@value Descriptions#ROUND_PAUSE}
     */
    int getRoundPause();
    /**
     * @return {@value Descriptions#VALIDATOR_KEYFILE}
     */
    String getValidatorKeyfile();
    /**
     * @return {@value Descriptions#MILESTONE_KEY_DEPTH}
     */
    int getMilestoneKeyDepth();
    /**
     * @return {@value Descriptions#VALIDATOR_SECURITY}
     */
    int getValidatorSecurity();

    interface Descriptions {
        String VALIDATOR = "Flag that enables applying as a validator in the network.";
        String VALIDATOR_PATH = "A path to a file containing the seed / keyfile has to be passed.";
        String VALIDATOR_SEED_PATH = "A path to a file containing the seed has to be passed.";
        String INITIAL_VALIDATORS = "The addresses of validators the network starts with";
        String DONT_VALIDATE_TESTNET_MILESTONE_SIG = "Disable validator validation on testnet";
        String GENESIS_TIME = "Time when the ledger started.";
        String ROUND_DURATION = "Duration of a round in milli secounds.";
        String ROUND_PAUSE = "Duration of time to finalize the round in milli secounds.";
        String VALIDATOR_KEYFILE = "Filepath to validator keyfile";
        String MILESTONE_KEY_DEPTH = "Depth of the merkle tree the milestones are signed with.";
        String VALIDATOR_SECURITY = "Security level of transactions sent from a validator (milestones, registrations)";
    }
}
