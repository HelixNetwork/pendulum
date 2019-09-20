package net.helix.pendulum.conf;

public class MainnetConfig extends BaseHelixConfig {
    @Override
    public boolean isTestnet() {
        return false;
    }
}