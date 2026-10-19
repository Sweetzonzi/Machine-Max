package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * AUI 载体元素的锚点：屏幕九个位置之一。
 * <p>
 * 锚点决定元素的哪一边贴住屏幕的对应边：{@code bottom_right} 让元素右下角对齐
 * 屏幕右下角，{@code center} 让元素中心对齐屏幕中心。模型载体不使用该字段。
 */
public enum HudAnchor implements StringRepresentable {

    TOP_LEFT("top_left", 0, 0),
    TOP_CENTER("top_center", 1, 0),
    TOP_RIGHT("top_right", 2, 0),
    CENTER_LEFT("center_left", 0, 1),
    CENTER("center", 1, 1),
    CENTER_RIGHT("center_right", 2, 1),
    BOTTOM_LEFT("bottom_left", 0, 2),
    BOTTOM_CENTER("bottom_center", 1, 2),
    BOTTOM_RIGHT("bottom_right", 2, 2);

    public static final Codec<HudAnchor> CODEC = StringRepresentable.fromEnum(HudAnchor::values);

    private final String name;
    /** 0 = 左/上，1 = 中，2 = 右/下。 */
    private final int horizontal;
    private final int vertical;

    HudAnchor(String name, int horizontal, int vertical) {
        this.name = name;
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name;
    }

    public int horizontal() {
        return horizontal;
    }

    public int vertical() {
        return vertical;
    }

    /** 全部枚举值的小写名，供诊断。 */
    public static String names() {
        StringBuilder builder = new StringBuilder();
        for (HudAnchor anchor : values()) {
            if (!builder.isEmpty()) builder.append(", ");
            builder.append(anchor.name.toLowerCase(Locale.ROOT));
        }
        return builder.toString();
    }
}
