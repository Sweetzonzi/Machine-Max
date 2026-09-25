package io.github.sweetzonzi.machine_max.client.render.gui.research;

import com.sighs.apricityui.init.Element;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 研究台界面的 HTML 片段工具：文本转义与动作按钮。
 *
 * <p>界面内容全部由 Java 侧拼成 HTML 片段后交给 AUI 解析，节点文本与属性值都来自内容包数据，
 * 因此进入片段前一律转义。</p>
 */
public final class ResearchHtml {
    private ResearchHtml() {
    }

    /** 转义 HTML 文本与属性值中的保留字符 */
    public static String escape(@Nullable String raw) {
        if (raw == null) return "";
        StringBuilder builder = new StringBuilder(raw.length() + 16);
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            switch (c) {
                case '&' -> builder.append("&amp;");
                case '<' -> builder.append("&lt;");
                case '>' -> builder.append("&gt;");
                case '"' -> builder.append("&quot;");
                case '\'' -> builder.append("&#39;");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    /** 动作的显示文案（已本地化） */
    public static String actionLabel(ResearchAction action) {
        return Component.translatable(action.labelKey()).getString();
    }

    /**
     * 生成一个动作按钮片段。
     *
     * @param action   动作类型，决定图标 / 色调 / 文案与写入 {@code data-act} 的键
     * @param enabled  是否可点击
     * @param iconOnly true 生成行内的等宽方形图标按钮，false 生成详情面板底部的文字按钮
     * @return 按钮的 HTML 片段
     */
    public static String actionButton(ResearchAction action, boolean enabled, boolean iconOnly) {
        String icon = iconOnly ? action.icon() : null;
        StringBuilder classes = new StringBuilder(icon == null ? "btn-sm" : "btn-icon");
        if (action.primary()) classes.append(" primary");
        if (action.danger()) classes.append(" danger");
        if (!enabled) classes.append(" disabled");
        String text = icon == null ? escape(actionLabel(action)) : icon;
        String title = icon == null ? "" : " title=\"" + escape(actionLabel(action)) + "\"";
        return "<div class=\"" + classes + "\" data-act=\"" + escape(action.key()) + "\"" + title + ">" + text + "</div>";
    }

    /** 拼接一个区块：标题 + 正文片段 */
    public static String section(String title, String body) {
        return "<div class=\"sect\"><div class=\"sect-head\">" + escape(title) + "</div>" + body + "</div>";
    }

    /** 元素的 class 列表里是否含有指定类名 */
    public static boolean hasClass(Element element, String token) {
        String classes = element.getAttribute("class");
        if (classes == null || classes.isBlank()) return false;
        for (String current : classes.trim().split("\\s+")) {
            if (token.equals(current)) return true;
        }
        return false;
    }
}
