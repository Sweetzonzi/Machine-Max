package io.github.sweetzonzi.machine_max.mixin;

import io.github.sweetzonzi.machine_max.client.render.gui.creative.CreativePartBrowser;
import io.github.sweetzonzi.machine_max.common.mech.part.PartDefinitions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPartType;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 部件定义或客户端配方索引变化时让原版创造目录缓存失效。
 *
 * <p>检测放在 {@code tryRebuildTabContents} 的 HEAD：这是静态入口，屏幕构造时与开屏后的
 * {@code containerTick} 都会经过，闭屏期间发布的新快照因此也能在下次打开时生效。</p>
 *
 * <p>未就绪（{@code PartDefinitions.isReady(true)} 为假）时不置空缓存、不记录快照、不消费配方
 * 脏标记：装载开始时发布的是暂空表，若据此强制重建，屏幕会把完整候选为空的当前页切到默认建筑页。</p>
 *
 * <p>只比较 {@link PartDefinitions#regular(boolean)} 每次返回的发布快照引用，不比较长期保持同一引用
 * 的视图。</p>
 *
 * <p>线程：主线程（20tps）。</p>
 */
@Mixin(CreativeModeTabs.class)
public abstract class CreativeModeTabsMixin {

    @Shadow
    @Nullable
    private static CreativeModeTab.ItemDisplayParameters CACHED_PARAMETERS;

    /** 上一次参与比较的发布快照引用。 */
    @Unique
    private static Map<ResourceLocation, RegularPartType> machineMax$lastSnapshot;

    @Inject(method = "tryRebuildTabContents", at = @At("HEAD"))
    private static void machineMax$invalidateOnContentChange(FeatureFlagSet enabledFeatures, boolean hasPermissions,
                                                             HolderLookup.Provider registries,
                                                             CallbackInfoReturnable<Boolean> cir) {
        if (!PartDefinitions.isReady(true)) return;
        Map<ResourceLocation, RegularPartType> snapshot = PartDefinitions.regular(true);
        boolean dirty = CreativePartBrowser.consumeContentsDirty();
        if (snapshot == machineMax$lastSnapshot && !dirty) return;
        machineMax$lastSnapshot = snapshot;
        CACHED_PARAMETERS = null;
        CreativePartBrowser.bumpContentsGeneration();
    }
}
