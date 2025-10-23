package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlockEntity;
import io.github.sweetzonzi.machine_max.common.block.road.RoadBaseBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MMBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MachineMax.MOD_ID);

    public static final Supplier<BlockEntityType<FabricatorBlockEntity>> FABRICATOR_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "fabricator",
            () -> BlockEntityType.Builder.of(
                    FabricatorBlockEntity::new,
                    MMBlocks.getFABRICATOR_BLOCK().get()
            ).build(null)
    );

    public static final Supplier<BlockEntityType<RoadBaseBlockEntity>> ROAD_BASE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register(
            "road_base",
            () -> BlockEntityType.Builder.of(
                    RoadBaseBlockEntity::new,
                    MMBlocks.getROAD_BASE_BLOCK().get()
            ).build(null)
    );

    public static void register(IEventBus bus) {
        BLOCK_ENTITY_TYPES.register(bus);
    }
}
