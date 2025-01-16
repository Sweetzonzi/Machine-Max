package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.PartType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * 部件类型注册器
 * @author Sweetzonzi
 */
public class PartRegistry {
    public static final ResourceKey<Registry<PartType>> PART_REGISTRY_KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_type"));
    public static final Registry<PartType> PART_REGISTRY = new RegistryBuilder<>(PART_REGISTRY_KEY)
            .sync(true)
            .defaultKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "unknown_part_type"))
            .maxId(4096)
            .create();
    }
