package net.helix.pendulum.conf;

public interface GraphConfig extends Config{
    boolean isGraphEnabled();

    interface Descriptions {
        String GRAPH_ENABLED = "Enabling Graphstream";
    }
}