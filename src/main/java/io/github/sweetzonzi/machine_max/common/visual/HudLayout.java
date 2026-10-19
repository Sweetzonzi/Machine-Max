package io.github.sweetzonzi.machine_max.common.visual;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * HUD 布局：一份有序的 {@link HudElement} 数组。
 * <p>
 * 布局是命名资源，由内容包 {@code hud_layouts/*.json} 定义，读入
 * {@link io.github.sweetzonzi.machine_max.external.MMDynamicRes#HUD_LAYOUTS}；
 * 文件内容就是裸数组，键为 {@code <命名空间>:<路径>}。
 * <p>
 * 布局不含视角信息：同一份布局可以被多个宿主、多个视角引用，视角由宿主的字段名决定。
 */
public record HudLayout(List<HudElement> elements) {

    /**
     * “该视角没有 HUD”的哨兵键。
     * <p>
     * 宿主的视角字段缺省时取这个值，而不是 {@code null}：DFU 的 codec 不允许产出
     * {@code null}，用显式空键可以保持解码路径全程非空。该键保留，内容包不应注册
     * 名为 {@code <命名空间>:empty} 的布局。
     */
    public static final ResourceLocation EMPTY = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty");

    /** 空布局对象；解析到 {@link #EMPTY} 或布局缺失时使用。 */
    public static final HudLayout EMPTY_LAYOUT = new HudLayout(List.of());

    public static final Codec<HudLayout> CODEC = HudElement.CODEC.listOf()
            .xmap(HudLayout::new, HudLayout::elements);
}
