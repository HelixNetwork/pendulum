package net.helix.hlx.conf;

public class MainnetConfig extends BaseHelixConfig {
    @Override
    public boolean isTestnet() {
        return false;
    }
}