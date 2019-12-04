package net.helix.pendulum.conf;

public class MainnetConfig extends BasePendulumConfig {

    @Override
    public boolean isTestnet() {
        return false;
    }
}