package net.helix.sbx.service;

import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Graphstream {
    public Graph graph;
    public boolean LABEL = false;
    private static final Logger log = LoggerFactory.getLogger(Graphstream.class);
    public Graphstream() {
        this.graph = new SingleGraph("tangle");
        this.graph.setStrict(false);
        this.graph.setAutoCreate(true);
        this.graph.display();
    }
    public void setLabel(boolean toLabel) {
        this.LABEL = toLabel;
    }

    public void addNode(String hash, String branch, String trunk) {
        this.graph.addEdge(hash+branch, hash, branch); // h -> b
        this.graph.addEdge(hash+trunk, hash, trunk);   // h -> t
        if (this.LABEL) {
            Node graphNode = graph.getNode(hash);
            graphNode.addAttribute("ui.label", hash);
        }
    }
}
