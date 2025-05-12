package io.github.tt432.machinemax.external.htlike;

import java.util.ArrayList;
import java.util.List;

public class TextHtNode implements HtNode {
    private final String text;
    private final List<String> enclosingTags;

    public TextHtNode(String text, List<String> enclosingTags) {
        this.text = text.trim();
        this.enclosingTags = new ArrayList<>(enclosingTags); // 复制不可变列表
    }

    @Override
    public boolean isText() {
        return true;
    }

    @Override
    public String getText() {
        return text;
    }

    @Override
    public List<HtNode> getChildren() {
        return List.of();
    }

    @Override
    public List<String> getEnclosingTags() {
        return enclosingTags; // 返回标签路径
    }
}
