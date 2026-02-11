package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

public class MMDamageTypes {
    public static final ResourceKey<DamageType> PART_COLLISION =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_collision"));
}
