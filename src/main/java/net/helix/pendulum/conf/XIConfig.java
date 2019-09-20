package net.helix.pendulum.conf;

/**
 * Configurations for XI modules
 */

public interface XIConfig extends Config{
    String XI_DIR = "modules";
    /**
     * @return Descriptions#XI_DIR
     */
    String getXiDir();
    interface Descriptions {
        String XI_DIR = "The folder where XI modules should be added for automatic discovery.";
    }
}
