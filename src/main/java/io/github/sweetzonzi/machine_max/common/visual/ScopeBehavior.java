package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * 炮镜模式下 HUD 元素的跟随行为。
 * <p>
 * 只在布局被相机/瞄具的 {@code scope_hud_layout} 引用时生效；
 * 被座椅视角引用时忽略。
 */
public enum ScopeBehavior implements StringRepresentable {
    /** 屏幕固定：原点=屏幕中心，无旋转。 */
    SCREEN_FIXED,
    /** 跟随位置：原点=炮镜投影中心，不旋转（默认）。 */
    FOLLOW_POSITION,
    /** 跟随姿态：原点=炮镜投影中心，叠加炮镜 pitch/yaw 偏移旋转。 */
    FOLLOW_TRANSFORM;

    public static final Codec<ScopeBehavior> CODEC = StringRepresentable.fromEnum(ScopeBehavior::values);

    @Override
    public @NotNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
