package io.github.sweetzonzi.machinemax.external.html;

import java.util.ArrayList;
import java.util.List;

public class TagHtNode extends HtNode {
    private String tagName;
    private List<HtNode> children = new ArrayList<>();

    public TagHtNode(String tagName) { this.tagName = tagName; }
    public String getTagName() { return tagName; }
    public List<HtNode> getChildren() { return children; }
    public void addChild(HtNode child) { children.add(child); }
}
