package io.github.tt432.machinemax.external;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class DynamicInject {
    private static final String PACK_PREFIX = MOD_ID+"_";
    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        switch (event.getPackType()) {
            case CLIENT_RESOURCES -> {
                DynamicResBus.EXTERNAL_RESOURCE.forEach((packName, resourcePack) -> {
                    Pack.ResourcesSupplier supplier = createSupplier(resourcePack);
                    event.addRepositorySource(buildSource(resourcePack, packName, supplier));
                });
            }
            case SERVER_DATA -> {
            }
        }
    }

    private static Pack.ResourcesSupplier createSupplier(PackResources pack) {
        return new Pack.ResourcesSupplier() {
            @Override public @NotNull PackResources openPrimary(@NotNull PackLocationInfo info) { return pack; }
            @Override public @NotNull PackResources openFull(@NotNull PackLocationInfo info, Pack.@NotNull Metadata meta) { return pack; }
        };
    }
   // 提取资源源构建逻辑
    private static RepositorySource buildSource(PackResources pack, ResourceLocation name, Pack.ResourcesSupplier supplier) {
        return consumer -> {
            Pack.Metadata meta = new Pack.Metadata(
                    Component.literal(PACK_PREFIX + name),
                    PackCompatibility.COMPATIBLE,
                    FeatureFlagSet.of(),
                    Collections.emptyList(),
                    false
            );
            consumer.accept(new Pack(pack.location(), supplier, meta,
                    new PackSelectionConfig(true, Pack.Position.TOP, true)));
        };
    }

}
