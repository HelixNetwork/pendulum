package net.helix.pendulum.conf;

/**
 * Configurations for handling global snapshot data
 */
public interface SpentAddressesConfig extends Config {

    /**
     * @return {@value Descriptions#SPENT_ADDRESSES_DB_PATH}
     */
    String getSpentAddressesDbPath();

    /**
     * @return {@value Descriptions#SPENT_ADDRESSES_DB_LOG_PATH}
     */
    String getSpentAddressesDbLogPath();

    /**
     * @return {@value Descriptions#PREVIOUS_EPOCH_SPENT_ADDRESSES_FILE}
     */
    String getPreviousEpochSpentAddressesFiles();

    /**
     * @return {@value Descriptions#PREVIOUS_EPOCH_SPENT_ADDRESSES_SIG_FILE}
     */
    String getPreviousEpochSpentAddressesSigFile();

    interface Descriptions {

        String SPENT_ADDRESSES_DB_PATH = "The path where the spent addresses db is stored";
        String SPENT_ADDRESSES_DB_LOG_PATH = "The path where the spent addresses db log is stored";
        String PREVIOUS_EPOCH_SPENT_ADDRESSES_FILE = "The file that contains the list of all used addresses from previous epochs";
        String PREVIOUS_EPOCH_SPENT_ADDRESSES_SIG_FILE = "The file that contains signature of the previous epochs spent addresses file";
    }
}
