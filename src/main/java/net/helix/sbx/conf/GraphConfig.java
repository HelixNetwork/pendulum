package net.helix.sbx.conf;

public interface GraphConfig extends Config{
    boolean isGraphEnabled();

    interface Descriptions {
        String GRAPH_ENABLED = "Enabling Graphstream";
    }
}