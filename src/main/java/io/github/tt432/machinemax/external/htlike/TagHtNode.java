package io.github.tt432.machinemax.external.htlike;

import java.util.ArrayList;
import java.util.List;

public class TagHtNode implements HtNode {
    private final String tagName;
    private final List<HtNode> children = new ArrayList<>();

    public TagHtNode(String tagName) {
        this.tagName = tagName;
    }

    public void addChild(HtNode htNode) {
        children.add(htNode);
    }

    @Override
    public boolean isText() {
        return false;
    }

    @Override
    public String getText() {
        return null;
    }

    @Override
    public List<HtNode> getChildren() {
        return children;
    }

    public String getTagName() {
        return tagName;
    }

    @Override
    public List<String> getEnclosingTags() {
        return List.of(); // 标签节点返回空列表
    }
}
