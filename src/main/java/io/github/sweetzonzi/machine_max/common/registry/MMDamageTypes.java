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
     * <p>不得复用原版 {@code minecraft:explosion}——那会落进 {@code MMPartEntity.hurt()} 的爆炸分支
     * （语义为"最近 SubPart + 最厚装甲"，与本设计的逐 HitBox 判定不符）。</p>
     */
    public static final ResourceKey<DamageType> BLAST =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blast"));
}
