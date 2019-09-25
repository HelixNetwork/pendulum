package net.helix.pendulum.conf;

import net.helix.pendulum.model.Hash;

/**
 * Configs that should be used for tracking candidate applications and publishing validators
 */
public interface ValidatorManagerConfig extends Config {
    /**
     * @return {@value Descriptions#VALIDATOR_MANAGER_ENABLED}
     */
    boolean getValidatorManagerEnabled();
    /**
     * @return Descriptions#VALIDATOR_MANAGER_ADDRESS
     */
    Hash getValidatorManagerAddress();
    /**
     * @return {@value Descriptions#DONT_VALIDATE_TESTNET_VALIDATOR_MANAGER_SIG}
     */
    boolean isDontValidateTestnetValidatorManagerSig();
    /**
     * @return {@value Descriptions#UPDATE_VALIDATOR_DELAY}
     */
    int getUpdateValidatorDelay();
    /**
     * @return {@value Descriptions#START_ROUND_DELAY}
     */
    int getStartRoundDelay();
    /**
     * @return {@value Descriptions#VALIDATOR_MANAGER_KEYFILE}
     */
    String getValidatorManagerKeyfile();
    /**
     * @return {@value Descriptions#VALIDATOR_MANAGER_KEY_DEPTH}
     */
    int getValidatorManagerKeyDepth();
    /**
     * @return {@value Descriptions#VALIDATOR_MANAGER_SECURITY}
     */
    int getValidatorManagerSecurity();


    interface Descriptions {
        String VALIDATOR_MANAGER_ENABLED = "Flag that determines if the node is a validator manager.";
        String VALIDATOR_MANAGER_ADDRESS = "The address of the node that publishes validators";
        String DONT_VALIDATE_TESTNET_VALIDATOR_MANAGER_SIG = "Disable validatomanager validation on testnet";
        String UPDATE_VALIDATOR_DELAY = "The desired delay for updating validators in seconds.";
        String START_ROUND_DELAY = "The number of rounds between validators are published and the round they start to operate.";
        String VALIDATOR_MANAGER_KEYFILE = "Filepath to validatomanager keyfile";
        String VALIDATOR_MANAGER_KEY_DEPTH = "Depth of the merkle tree validator transactions are signed with.";
        String VALIDATOR_MANAGER_SECURITY = "Security level of transactions sent from the validatomanager";
    }
}

