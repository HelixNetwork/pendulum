package net.helix.pendulum.conf;

public interface RoundConfig extends Config {

    /**
     * @return {@value Descriptions#GENESIS_TIME}
     */
    long getGenesisTime();
    /**
     * @return {@value Descriptions#ROUND_DURATION}
     */
    int getRoundDuration();
    /**
     * @return {@value Descriptions#ROUND_PAUSE}
     */
    int getRoundPause();

    interface Descriptions {
        String GENESIS_TIME = "Time when the ledger started.";
        String ROUND_DURATION = "Duration of a round in milli secounds.";
        String ROUND_PAUSE = "Duration of time to finalize the round in milli secounds.";
    }
}
