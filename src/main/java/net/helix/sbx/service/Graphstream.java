package net.helix.sbx.service;

import org.graphstream.graph.*;
import org.graphstream.graph.implementations.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Graphstream {
    public Graph graph;
    public boolean LABEL = true;
    private static final Logger log = LoggerFactory.getLogger(Graphstream.class);
    public Graphstream() {
        System.setProperty("org.graphstream.ui.renderer", "org.graphstream.ui.j2dviewer.J2DGraphRenderer");
        this.graph = new SingleGraph("tangle");
        this.graph.setStrict(false);
        this.graph.setAutoCreate(true);
        this.graph.display();
    }
    public void setLabel(boolean toLabel) {
        this.LABEL = toLabel;
    }

    public void setValidity(String hash, Integer validity) {
        Node graphNode = graph.getNode(hash);
        if (validity.equals(-1)){
            graphNode.addAttribute("ui.style", "fill-color: rgb(255,0,0);");
        }
        else {
            if (validity.equals(0)) {
                graphNode.addAttribute("ui.style", "fill-color: rgb(255,165,0);");
            } else {
                graphNode.addAttribute("ui.style", "fill-color: rgb(0,255,0);");
            }
        }
    }

    public void setMilestone(String hash) {
        Node graphNode = graph.getNode(hash);
        graphNode.addAttribute("ui.style", "size: 20px; stroke-mode: plain;");
    }

    public void addNode(String hash, String branch, String trunk) {
        this.graph.addEdge(hash+branch, hash, branch); // h -> b
        this.graph.addEdge(hash+trunk, hash, trunk);   // h -> t
        if (this.LABEL) {
            Node graphNode = graph.getNode(hash);
            graphNode.addAttribute("ui.label", hash.substring(0,10));
            graphNode.addAttribute("ui.style", "fill-color: rgb(255,165,0); stroke-color: rgb(30,144,255); stroke-width: 2px;");
        }
    }
}
