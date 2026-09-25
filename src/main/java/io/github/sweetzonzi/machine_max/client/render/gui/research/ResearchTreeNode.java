package io.github.sweetzonzi.machine_max.client.render.gui.research;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 研究台界面的树节点模型。
 *
 * <p>渲染器只消费本模型的字段，不感知数据来源：研发标签页的节点来自研发索引与依赖层级，
 * 蓝图库标签页的节点来自本地蓝图扫描结果与内容包蓝图索引。</p>
 *
 * @param kind         节点类型
 * @param id           稳定标识（研发 id / 蓝图文件名 / 内容包蓝图 id）
 * @param label        显示文本，已在 Java 侧完成本地化
 * @param icon         图标纹理资源路径；无图标时为 {@code null}
 * @param note         行内次级标注（如「另需 1 项」）；无标注时为 {@code null}
 * @param statusText   状态文案；组头与无状态节点为 {@code null}
 * @param tone         状态色调（ok / ready / warn / locked / bad）；无状态时为 {@code null}
 * @param depth        组内依赖层级；组头为 -1
 * @param unreachable  是否为不可达成条目（行内标注不可研发）
 * @param progressDone 组头的已完成数
 * @param progressTotal 组头的条目总数
 * @param actions      行内动作
 * @param children     子节点
 */
public record ResearchTreeNode(
        Kind kind,
        String id,
        String label,
        @Nullable String icon,
        @Nullable String note,
        @Nullable String statusText,
        @Nullable String tone,
        int depth,
        boolean unreachable,
        int progressDone,
        int progressTotal,
        List<Action> actions,
        List<ResearchTreeNode> children
) {
    /** 节点类型 */
    public enum Kind {
        /** 组头 */
        GROUP,
        /** 研发条目 */
        RESEARCH,
        /** 蓝图条目 */
        BLUEPRINT
    }

    /**
     * 一个可执行动作。
     *
     * @param type    动作类型，同时决定按钮的图标 / 色调 / 文案与点击后分发的键
     * @param enabled 是否可点击；false 时渲染为禁用态
     * @param inRow   是否渲染到树行里；只在详情面板底部出现的动作置 false
     */
    public record Action(ResearchAction type, boolean enabled, boolean inRow) {
    }

    /** 组头节点 */
    public static ResearchTreeNode group(String id, String label, int progressDone, int progressTotal,
                                        List<ResearchTreeNode> children) {
        return new ResearchTreeNode(Kind.GROUP, id, label, null, null, null, null,
                -1, false, progressDone, progressTotal, List.of(), children);
    }

    /** 研发条目节点 */
    public static ResearchTreeNode research(String id, String label, @Nullable String icon, @Nullable String note,
                                           String statusText, String tone, int depth, boolean unreachable,
                                           List<Action> actions, List<ResearchTreeNode> children) {
        return new ResearchTreeNode(Kind.RESEARCH, id, label, icon, note, statusText, tone,
                depth, unreachable, 0, 0, actions, children);
    }

    /** 蓝图条目节点 */
    public static ResearchTreeNode blueprint(String id, String label, @Nullable String icon, @Nullable String note,
                                             String statusText, String tone, boolean unreachable,
                                             List<Action> actions) {
        return new ResearchTreeNode(Kind.BLUEPRINT, id, label, icon, note, statusText, tone,
                0, unreachable, 0, 0, actions, List.of());
    }
}
