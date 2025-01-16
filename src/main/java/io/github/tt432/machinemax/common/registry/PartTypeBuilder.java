package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.part.PartType;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PartTypeBuilder<T extends AbstractPart> {

    private final String modId;
    private final DeferredRegister<PartType> partTypeRegister;
    private String id = "";
    private BiFunction<PartType, Level, T> partFactory = null;

    public PartTypeBuilder(String modId, DeferredRegister<PartType> partTypeRegister) {
        this.modId = modId;
        this.partTypeRegister = partTypeRegister;
    }

    public PartTypeBuilder<T> id(String id) {
        this.id = id;
        return this;
    }

    public PartTypeBuilder<T> bound(BiFunction<PartType, Level, T> partFactory) {
        this.partFactory = partFactory;
        return this;
    }

    public Supplier<PartType> build() {
        if (partFactory == null) {
            throw new IllegalStateException("Part factory must be set before building PartType");
        }
        Supplier<PartType> supplier = partTypeRegister.register(id, () -> new PartType(ResourceLocation.fromNamespaceAndPath(modId, id), partFactory));
        return supplier;
    }
}
