package io.github.sweetzonzi.machine_max.client.render.gui.research;

import org.jetbrains.annotations.Nullable;

/**
 * 研究台界面的动作类型。
 *
 * <p>本枚举是动作的唯一事实来源：动作键（写入 DOM 的 {@code data-act} 属性，也是点击后从属性读回的协议值）、
 * 行内图标、按钮色调与文案语言键都收在常量上。新增动作只需在此补一个常量，
 * 各处的 {@code switch} 对枚举的穷尽性检查会把漏改的分支报出来。</p>
 */
public enum ResearchAction {
    /** 研发：消耗材料完成该研发项目 */
    RESEARCH("research", "▶", true, false, "gui.machine_max.research.action.research"),
    /** 取出：把蓝图拿到手里；研发条目取出的是该零件的制造蓝图，蓝图库取出的是蓝图文件或内容包蓝图 */
    TAKE("take", "↓", true, false, "gui.machine_max.research.action.take"),
    /** 展开全部：作用于组自身 */
    EXPAND("expand", null, false, false, "gui.machine_max.research.expand_all"),
    /** 折叠全部：作用于组自身 */
    COLLAPSE("collapse", null, false, false, "gui.machine_max.research.collapse_all"),
    /** 改名：蓝图库条目动作，只在详情面板底部出现 */
    RENAME("rename", null, false, false, "gui.machine_max.research.action.rename"),
    /** 删除：蓝图库条目动作，只在详情面板底部出现，用危险色调 */
    DELETE("delete", null, false, true, "gui.machine_max.research.action.delete");

    /** 动作键：写入 DOM 的 {@code data-act}，点击时按此值反查 */
    private final String key;
    /** 行内图标；为 {@code null} 的动作在行内退化为文字按钮 */
    @Nullable
    private final String icon;
    /** 是否使用主按钮色调 */
    private final boolean primary;
    /** 是否使用危险色调 */
    private final boolean danger;
    /** 文案语言键 */
    private final String labelKey;

    ResearchAction(String key, @Nullable String icon, boolean primary, boolean danger, String labelKey) {
        this.key = key;
        this.icon = icon;
        this.primary = primary;
        this.danger = danger;
        this.labelKey = labelKey;
    }

    /** 动作键 */
    public String key() {
        return key;
    }

    /** 行内图标；无图标时返回 {@code null} */
    @Nullable
    public String icon() {
        return icon;
    }

    /** 是否使用主按钮色调 */
    public boolean primary() {
        return primary;
    }

    /** 是否使用危险色调 */
    public boolean danger() {
        return danger;
    }

    /** 文案语言键 */
    public String labelKey() {
        return labelKey;
    }

    /**
     * 按 DOM 里的动作键反查动作类型。
     *
     * @param key {@code data-act} 属性的值
     * @return 对应的动作类型；键为 {@code null} 或不在枚举内时返回 {@code null}
     */
    @Nullable
    public static ResearchAction byKey(@Nullable String key) {
        if (key == null) return null;
        for (ResearchAction action : values()) {
            if (action.key.equals(key)) return action;
        }
        return null;
    }
}
