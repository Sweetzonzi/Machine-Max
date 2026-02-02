package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;

public class MMTags {
    public static final TagKey<DamageType> HAS_PEN_DEPTH = TagKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "has_pen_depth")
    );

    public static final TagKey<Item> EMPTY_BLUEPRINT = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath("c", "empty_blueprint")
    );
}
