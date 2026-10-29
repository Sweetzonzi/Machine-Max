package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

public class MMTags {
    public static final TagKey<DamageType> HAS_PEN_DEPTH = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "has_pen_depth")
    );

    public static final TagKey<DamageType> IS_IMPACT = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath("c", "is_impact")
    );

    public static final TagKey<Item> EMPTY_BLUEPRINT = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "empty_blueprint")
    );

    // ==================== 零件定义标签 ====================
    //
    // 以下取值来自内容包 parts/*.json 的 variants[].tags。它们是零件定义上的字符串标签，
    // 不注册进 Registries.ITEM，所以类型是 ResourceLocation 而不是 TagKey。

    /**
     * 系列标签的路径前缀；完整标签形如 {@code machine_max:series/k17}。
     *
     * <p>系列名由内容包作者自取，判定按完整标签 ID 进行，因此两个包的同名后缀是两个系列。</p>
     */
    public static final String PART_SERIES_PREFIX = "series/";

    /** 结构类。 */
    public static final ResourceLocation PART_CATEGORY_STRUCTURAL = partTag("category/structural");
    /** 机动类。 */
    public static final ResourceLocation PART_CATEGORY_MOBILITY = partTag("category/mobility");
    /** 武器类。 */
    public static final ResourceLocation PART_CATEGORY_WEAPON = partTag("category/weapon");
    /** 装饰类。 */
    public static final ResourceLocation PART_CATEGORY_DECORATION = partTag("category/decoration");
    /** 杂项类。 */
    public static final ResourceLocation PART_CATEGORY_MISC = partTag("category/misc");

    private static ResourceLocation partTag(String path) {
        return ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, path);
    }
}
