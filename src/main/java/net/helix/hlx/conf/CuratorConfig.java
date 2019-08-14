package net.helix.hlx.conf;

import net.helix.hlx.model.Hash;

/**
 * Configs that should be used for tracking candidate applications and publishing nominees
 */
public interface CuratorConfig extends Config {
    /**
     * @return {@value Descriptions#CURATOR_ENABLED}
     */
    boolean getCuratorEnabled();
    /**
     * @return Descriptions#CURATOR_ADDRESS
     */
    Hash getCuratorAddress();
    /**
     * @return {@value Descriptions#DONT_VALIDATE_TESTNET_CURATOR_SIG}
     */
    boolean isDontValidateTestnetCuratorSig();
    /**
     * @return {@value Descriptions#UPDATE_NOMINEE_DELAY}
     */
    int getUpdateNomineeDelay();
    /**
     * @return {@value Descriptions#START_ROUND_DELAY}
     */
    int getStartRoundDelay();
    /**
     * @return {@value Descriptions#CURATOR_KEYFILE}
     */
    String getCuratorKeyfile();
    /**
     * @return {@value Descriptions#CURATOR_KEY_DEPTH}
     */
    int getCuratorKeyDepth();
    /**
     * @return {@value Descriptions#CURATOR_SECURITY}
     */
    int getCuratorSecurity();


    interface Descriptions {
        String CURATOR_ENABLED = "Flag that determines if the node is a Curator.";
        String CURATOR_ADDRESS = "The address of the node that publishes nominees";
        String DONT_VALIDATE_TESTNET_CURATOR_SIG = "Disable curator validation on testnet";
        String UPDATE_NOMINEE_DELAY = "The desired delay for updating nominees in seconds.";
        String START_ROUND_DELAY = "The number of rounds between nominees are published and the round they start to operate.";
        String CURATOR_KEYFILE = "Filepath to curator keyfile";
        String CURATOR_KEY_DEPTH = "Depth of the merkle tree nominee transactions are signed with.";
        String CURATOR_SECURITY = "Security level of transactions sent from the curator";
    }
}

