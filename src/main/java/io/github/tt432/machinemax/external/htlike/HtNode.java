package io.github.tt432.machinemax.external.htlike;

import java.util.List;

public interface HtNode {
    boolean isText();

    String getText();

    List<HtNode> getChildren();

    List<String> getEnclosingTags(); // 新增方法
}
