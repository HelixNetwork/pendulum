package net.helix.pendulum.conf;


/**
 * Configs that should be used for tracking milestones
 */
public interface MilestoneConfig extends Config {

    /**
     * @return {@value Descriptions#MILESTONE_KEY_DEPTH}
     */
    int getMilestoneKeyDepth();

    /**
     * @return {@value Descriptions#MILESTONE_START_INDEX}
     */
    int getMilestoneStartIndex();


    interface Descriptions {
        String MILESTONE_KEY_DEPTH = "Depth of the merkle tree the milestones are signed with.";
        String MILESTONE_START_INDEX = "The start index of the milestones. This index is encoded in each milestone " +
                "transaction by the coordinator.";
        String NUMBER_OF_KEYS_IN_A_MILESTONE = "The depth of the Merkle tree which in turn determines the number of" +
                "leaves (private keys) that the coordinator can use to sign a message.";

    }
}
