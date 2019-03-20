package net.helix.sbx.conf;

/**
 * Configurations for HXI modules
 */

public interface HXIConfig extends Config{
    String HXI_DIR = "hxi";
    /**
     * @return Descriptions#HXI_DIR
     */
    String getHxiDir();
    interface Descriptions {
        String HXI_DIR = "The folder where hxi modules should be added for automatic discovery by SBX.";
    }
}
