package io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.resources.ResourceLocation;

/**
 * 挂件的外观：单节模型、模型贴图与物品图标。
 *
 * <p>{@code model} 与 {@code texture} 必填。模型按 {@code ModelIndex("fitting", model)} 从内容包的
 * {@code models/fitting/} 加载，与零件模型共用同一套 {@code OModel}；贴图路径是该模型
 * 使用的完整贴图位置，不从模型路径推导。</p>
 *
 * <p>{@code icon} 是物品栏里的 2D 图标贴图，可省略。省略时该字段取 {@link #NO_ICON}，
 * 物品与创造栏改用 3D 单节模型渲染（见 {@code FittingItem}）。图标贴图只用于 GUI，
 * 不影响世界里履带节的渲染。</p>
 *
 * @param model   单节模型 id，如 {@code machine_max:track_link.geo}
 * @param texture 模型贴图的完整路径，如 {@code machine_max:textures/part/track_link.png}
 * @param icon    物品图标贴图的完整路径；{@link #NO_ICON} 表示没有图标
 */
public record FittingVisual(ResourceLocation model, ResourceLocation texture, ResourceLocation icon) {
    /**
     * 没有图标时的哨兵值。
     *
     * <p>用常量而不是 {@code Optional} 是为了让 JSON 缺省值、模型构造与渲染判断共用同一个值；
     * 该路径不会被当作贴图加载。</p>
     */
    public static final ResourceLocation NO_ICON =
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty");

    public static final Codec<FittingVisual> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("model").forGetter(FittingVisual::model),
            ResourceLocation.CODEC.fieldOf("texture").forGetter(FittingVisual::texture),
            ResourceLocation.CODEC.optionalFieldOf("icon", NO_ICON).forGetter(FittingVisual::icon)
    ).apply(instance, FittingVisual::new));

    /** 是否有可用于物品栏的 2D 图标。 */
    public boolean hasIcon() {
        return !NO_ICON.equals(icon);
    }
}
