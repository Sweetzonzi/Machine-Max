package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class MMDamageTypes {
    public static final ResourceKey<DamageType> PART_COLLISION =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_collision"));

    /**
     * 爆炸伤害类型。
     * <p>本系统爆炸的默认归属：{@code ExplosionParams.damage_type} 缺省即指向它。
     * 它决定该次爆炸伤害按哪些类型条件分派——材料侧 {@code damage_modifiers} 的
     * {@code damage_type} / {@code damage_tag} 条件、实体的原版免疫与无敌帧等。
     * 爆炸伤害本身经 {@code BFDamageApi} 逐 HitBox 施加，不依赖该类型选路。</p>
     * <p>内容包可在 {@code warheads} 块的 {@code damage_type} 中改配为任意类型
     * （含原版爆炸类型），该次爆炸随后按所选类型的条件分派。</p>
     */
    public static final ResourceKey<DamageType> BLAST =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blast"));

    /**
     * 投射物动能伤害类型。
     * <p>弹种 {@code terminal.damage_type} 的缺省归属：投射物直接命中的伤害（零件侧逐 HitBox、
     * 实体侧交原版减伤链）都取它。内容包可按弹种改配为任意类型，该弹种随后按所选类型的条件分派。</p>
     * <p>该类型不加入任何伤害类型标签（如 {@code is_impact}），按标签条件的默认修正链对其
     * 既不加也不减；材料侧 {@code damage_modifiers} 仍可按 {@code damage_type} 条件针对它单独生效。</p>
     */
    public static final ResourceKey<DamageType> PROJECTILE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectile"));

    /**
     * 弹药殉爆伤害类型。
     * <p>库存、膛内与战斗部被诱爆时合成的爆炸归属：起爆点通常位于自身几何内部，与外来的
     * 高爆弹在语义上不同——前者炸的是自己的结构，后者炸的是外部装甲。</p>
     * <p>它只改变两件事：冲击结算多乘一次
     * {@link io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox#COOKOFF_IMPACT_MULTIPLIER}
     * （内部起爆不按装甲的外向折减结算），以及内容包可以用 {@code damage_type} 条件单独给殉爆写伤害规则。
     * 爆炸的几何、射线条数、穿深与冲量仍由
     * {@link io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffComposition}
     * 合成，与普通爆炸同一条管线。</p>
     */
    public static final ResourceKey<DamageType> COOKOFF =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "cookoff"));
}
