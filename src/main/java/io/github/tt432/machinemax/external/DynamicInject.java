package io.github.tt432.machinemax.external;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.*;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraft.world.flag.FeatureFlagSet;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD)
public class DynamicInject {

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) { //防止 F3+T不再识别外部包
        // 注册自定义重载监听器
        event.registerReloadListener((ResourceManagerReloadListener) manager -> {
            MMDynamicRes.EXTERNAL_RESOURCE.forEach((packName, resourcePack) -> {
                PackLocationInfo location = createLocationInfo(String.valueOf(packName));
                Pack.ResourcesSupplier supplier = createSupplier(resourcePack);
                // 重新添加自定义资源包
                Minecraft.getInstance().getResourcePackRepository().addPackFinder(buildSource(location, supplier));
            });

        });
    }

    @SubscribeEvent
    public static void addPackFinders(AddPackFindersEvent event) {
        switch (event.getPackType()) {
            case CLIENT_RESOURCES -> {
                MMDynamicRes.EXTERNAL_RESOURCE.forEach((packName, resourcePack) -> {
                    PackLocationInfo location = createLocationInfo(String.valueOf(packName));
                    Pack.ResourcesSupplier supplier = createSupplier(resourcePack);
                    event.addRepositorySource(buildSource(location, supplier));
                });
            }
            case SERVER_DATA -> {
            }
        }
    }
    // 用于描述资源包的位置和元数据信息
    private static PackLocationInfo createLocationInfo(String packName) {
        return new PackLocationInfo(
                createPackId(packName),// 资源包唯一ID
                createPackTitle(packName),// 显示名称组件
                PackSource.BUILT_IN,// 来源类型
                resolveKnownPackInfo(packName)// 已知包信息
        );
    }

    // 生成标准化的pack ID
    private static String createPackId(String name) {
        return "custom_pack_" + name.toLowerCase(Locale.ROOT)
                .replace(' ', '_')
                .replaceAll("[^a-z0-9_-]", "");
    }

    // 创建带格式的标题组件
    private static Component createPackTitle(String name) {
        return Component.translatable("pack.title." + name)
                .withStyle(ChatFormatting.RED);
    }

    // 解析已知包信息（根据实际需求实现）
    private static Optional<KnownPack> resolveKnownPackInfo(String packName) {
        // 实现建议：
        // 1. 从配置文件加载
        // 2. 从资源包元数据解析
        // 3. 使用默认空值
        return Optional.empty();
    }


    private static Pack.ResourcesSupplier createSupplier(PackResources pack) {
        return new Pack.ResourcesSupplier() {
            @Override public @NotNull PackResources openPrimary(@NotNull PackLocationInfo info) { return pack; }
            @Override public @NotNull PackResources openFull(@NotNull PackLocationInfo info, Pack.@NotNull Metadata meta) { return pack; }
        };
    }
   // 提取资源源构建逻辑
   private static RepositorySource buildSource(PackLocationInfo location,
                                               Pack.ResourcesSupplier supplier) {
       return consumer -> {
           Pack.Metadata meta = new Pack.Metadata(
                   location.title().copy().withStyle(ChatFormatting.ITALIC), // 复用location中的标题
                   PackCompatibility.COMPATIBLE,
                   FeatureFlagSet.of(),
                   Collections.emptyList(),
                   false
           );

           consumer.accept(new Pack(
                   location,       // 使用完整构造的位置信息
                   supplier,
                   meta,
                   new PackSelectionConfig(true, Pack.Position.TOP, true)
           ));
       };
   }

}
