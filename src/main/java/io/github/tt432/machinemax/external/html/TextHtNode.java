package io.github.tt432.machinemax.external.html;

import java.util.ArrayList;
import java.util.List;

public class TextHtNode extends HtNode {
    private String text;
    private List<String> enclosingTags;

    public TextHtNode(String text, List<String> enclosingTags) {
        this.text = text;
        this.enclosingTags = new ArrayList<>(enclosingTags);
    }
    @Override
    public boolean isText() { return true; }
    public String getText() { return text; }
    public List<String> getEnclosingTags() { return enclosingTags; }
}
