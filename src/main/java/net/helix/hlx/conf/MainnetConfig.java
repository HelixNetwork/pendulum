package net.helix.hlx.conf;

public class MainnetConfig extends BaseHelixConfig {
    public MainnetConfig() {
        //All the configs are defined in the super class
        super();
    }
    @Override
    public boolean isTestnet() {
        return false;
    }
}