package io.github.tt432.machinemax.datagen;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.PartType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

//@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, modid = MachineMax.MOD_ID)
public class MMDataGenerator {
//    @SubscribeEvent
    public static void generateData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
//        //部件数据
        generator.addProvider(
                event.includeServer(),
                (DataProvider.Factory<DatapackBuiltinEntriesProvider>) out -> new DatapackBuiltinEntriesProvider(
                        out,
                        lookupProvider,
                        new RegistrySetBuilder()
                                .add(PartType.PART_REGISTRY_KEY, bootstrap -> {
                                    bootstrap.register(
                                            ResourceKey.create(PartType.PART_REGISTRY_KEY,
                                                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "test_cube")),
                                            new PartType(
                                                    "test_cube",
                                                    Map.of("default", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "geo/model/part/test_cube.geo.json")),
                                                    List.of(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/part/test_cube.png")),
                                                    ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"),
                                                    10.0f,
                                                    Map.of()
                                            ));

                                }),
                        Set.of(MachineMax.MOD_ID)
                )
        );
    }
}
