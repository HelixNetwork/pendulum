package net.helix.pendulum.conf;

public interface SpamConfig {
    /**
     * @return {@value Descriptions#GET_SPAM_DELAY}
     */
    int getSpamDelay();

    interface Descriptions {
        String GET_SPAM_DELAY = "Delay of spam.";
    }
}
