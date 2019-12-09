package net.helix.pendulum.conf;

import net.helix.pendulum.model.Hash;

import java.util.Set;

public interface ValidatorConfig extends Config  {

    /**
     * @return {@value Descriptions#VALIDATOR_SECURITY}
     */
    int getValidatorSecurity();
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
     * @return {@value Descriptions#VALIDATE_TESTNET_MILESTONE_SIG}
     */
    boolean isValidateTestnetMilestoneSig();

    /**
     * @return {@value Descriptions#VALIDATOR_KEYFILE}
     */
    String getValidatorKeyfile();

    interface Descriptions {
        String INITIAL_VALIDATORS = "The addresses of validators the network starts with";
        String VALIDATOR = "Flag that enables applying as a validator in the network.";
        String VALIDATOR_PATH = "A path to a file containing the seed / keyfile has to be passed.";
        String VALIDATOR_SEED_PATH = "A path to a file containing the seed has to be passed.";
        String VALIDATE_TESTNET_MILESTONE_SIG = "Disable validator validation on testnet";
        String VALIDATOR_KEYFILE = "Filepath to validator keyfile";
        String VALIDATOR_SECURITY = "Security level of transactions sent from a validator (milestones, registrations)";

    }

}
