package io.github.tt432.machinemax.external;

import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;

public class DynamicResBus {
    public static HashMap<ResourceLocation, DynamicPack> EXTERNAL_RESOURCE = new HashMap<>(); //所有当下读取的外部资源
}
