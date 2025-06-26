package io.github.sweetzonzi.machinemax.common.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record FabricatingResult(
        ResourceLocation id,
        int count
) {
    public static final Codec<FabricatingResult> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(FabricatingResult::id),
            Codec.INT.optionalFieldOf("count",1).forGetter(FabricatingResult::count)
    ).apply(instance, FabricatingResult::new));
}
