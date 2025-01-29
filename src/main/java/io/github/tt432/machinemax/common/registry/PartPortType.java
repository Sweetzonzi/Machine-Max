package io.github.tt432.machinemax.common.registry;

import com.mojang.datafixers.util.Pair;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.port.AbstractPortPort;
import io.github.tt432.machinemax.common.part.port.AttachPointPortPort;
import io.github.tt432.machinemax.common.part.port.FixedPartPort;
import io.github.tt432.machinemax.common.sloarphys.body.AbstractPartBody;
import io.github.tt432.machinemax.util.PartPortTypeBuilder;
import io.github.tt432.machinemax.util.data.PosRot;
import lombok.Getter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PartPortType {
    //注册器与默认值
    public static final ResourceKey<Registry<PartPortType>> PART_PORT_REGISTRY_KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "body_slot_type"));
    public static final Registry<PartPortType> PART_PORT_REGISTRY = new RegistryBuilder<>(PART_PORT_REGISTRY_KEY)
            .sync(true)
            .defaultKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "unknown_body_slot_type"))
            .maxId(4096)
            .create();

    public static final DeferredRegister<PartPortType> PART_PORT_TYPE = DeferredRegister.create(PART_PORT_REGISTRY, MachineMax.MOD_ID);

    public static final Supplier<PartPortType> ATTACH_POINT_PART_PORT = new PartPortTypeBuilder<>(MachineMax.MOD_ID, PART_PORT_TYPE)
            .id("attach_point")
            .bound(AttachPointPortPort::new)
            .build();
    public static final Supplier<PartPortType> FIXED_PART_PORT = new PartPortTypeBuilder<>(MachineMax.MOD_ID, PART_PORT_TYPE)
            .id("fixed")
            .bound(FixedPartPort::new)
            .build();
    @Getter
    public final ResourceLocation registryKey;
    private final BiFunction<Pair<PartPortType, AbstractPartBody>, Pair<String , PosRot>, ? extends AbstractPortPort> bodySlotFactory;

    public PartPortType(ResourceLocation registryKey, BiFunction<Pair<PartPortType, AbstractPartBody>, Pair<String , PosRot>, ? extends AbstractPortPort> bodySlotFactory) {
        this.registryKey = registryKey;
        this.bodySlotFactory = bodySlotFactory;

    }

    public AbstractPortPort createBodySlot(String name, AbstractPartBody slotOwnerBody, PosRot childBodyAttachPoint) {
        return bodySlotFactory.apply(Pair.of(this, slotOwnerBody), Pair.of(name, childBodyAttachPoint));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PartPortType type = (PartPortType) other;
        return Objects.equals(registryKey, type.registryKey);
    }

    @Override
    public int hashCode() {
        return registryKey.hashCode();
    }

}
