package io.github.tt432.machinemax.common.registry;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.part.AbstractPart;
import io.github.tt432.machinemax.common.part.TestCubePart;
import io.github.tt432.machinemax.common.part.ae86.AE86BackSeatPart;
import io.github.tt432.machinemax.common.part.ae86.AE86ChassisPart;
import io.github.tt432.machinemax.common.part.ae86.AE86SeatPart;
import io.github.tt432.machinemax.util.PartTypeBuilder;
import lombok.Getter;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.RegistryBuilder;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Supplier;

public class PartType {
    //注册器与默认值
    public static final ResourceKey<Registry<PartType>> PART_REGISTRY_KEY = ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_type"));
    public static final Registry<PartType> PART_REGISTRY = new RegistryBuilder<>(PART_REGISTRY_KEY)
            .sync(true)
            .defaultKey(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "unknown_part_type"))
            .maxId(4096)
            .create();

    public static final DeferredRegister<PartType> PART_TYPE = DeferredRegister.create(PART_REGISTRY, MachineMax.MOD_ID);

    public static final Supplier<PartType> TEST_CUBE_PART = new PartTypeBuilder<>(MachineMax.MOD_ID, PART_TYPE)
            .id("test_cube_part")
            .bound(TestCubePart::new)
            .build();
    public static final Supplier<PartType> AE86_CHASSIS_PART = new PartTypeBuilder<>(MachineMax.MOD_ID, PART_TYPE)
            .id("ae86_chassis_part")
            .bound(AE86ChassisPart::new)
            .build();
    public static final Supplier<PartType> AE86_SEAT_PART = new PartTypeBuilder<>(MachineMax.MOD_ID, PART_TYPE)
            .id("ae86_seat_part")
            .bound(AE86SeatPart::new)
            .build();
    public static final Supplier<PartType> AE86_BACK_SEAT_PART = new PartTypeBuilder<>(MachineMax.MOD_ID, PART_TYPE)
            .id("ae86_back_seat_part")
            .bound(AE86BackSeatPart::new)
            .build();

    @Getter
    public final ResourceLocation registryKey;
    private final BiFunction<PartType, Level, ? extends AbstractPart> partFactory;

    public PartType(ResourceLocation registryKey, BiFunction<PartType, Level, ? extends AbstractPart> partFactory) {
        this.registryKey = registryKey;
        this.partFactory = partFactory;

    }

    public AbstractPart createPart(Level level) {
        return partFactory.apply(this, level);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PartType partType = (PartType) other;
        return Objects.equals(registryKey, partType.registryKey);
    }

    @Override
    public int hashCode() {
        return registryKey.hashCode();
    }

}
