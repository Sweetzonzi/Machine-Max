package io.github.tt432.machinemax.util;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.common.vehicle.port.AbstractPortPort;
import io.github.tt432.machinemax.common.registry.PartPortType;
import io.github.tt432.machinemax.common.phys.body.AbstractPartBody;
import io.github.tt432.machinemax.util.data.PosRot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PartPortTypeBuilder<T extends AbstractPortPort> {
    private final String modId;
    private final DeferredRegister<PartPortType> partPortTypeRegister;
    private String id = "";
    private BiFunction<Pair<PartPortType, AbstractPartBody>, Pair<String , PosRot>, T> partPortFactory = null;

    public PartPortTypeBuilder(String modId, DeferredRegister<PartPortType> partPortTypeRegister) {
        this.modId = modId;
        this.partPortTypeRegister = partPortTypeRegister;
    }

    public PartPortTypeBuilder<T> id(String id) {
        this.id = id;
        return this;
    }

    public PartPortTypeBuilder<T> bound(BiFunction<Pair<PartPortType, AbstractPartBody>, Pair<String , PosRot>, T> partPortFactory) {
        this.partPortFactory = partPortFactory;
        return this;
    }

    public Supplier<PartPortType> build() {
        if (partPortFactory == null) {
            throw new IllegalStateException("BodySlot factory must be set before building PartPortType");
        }
        Supplier<PartPortType> supplier = partPortTypeRegister.register(id, () -> new PartPortType(ResourceLocation.fromNamespaceAndPath(modId, id), partPortFactory));
        return supplier;
    }
}
