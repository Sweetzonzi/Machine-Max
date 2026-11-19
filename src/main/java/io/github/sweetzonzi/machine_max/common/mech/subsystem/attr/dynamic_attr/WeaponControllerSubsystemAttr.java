package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WeaponControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.visual.HudLayout;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 武器控制器子系统动态属性。<br>
 * 定义控制信号输出频道、按视角的 HUD 布局等运行时配置。<br>
 * 输入频道（target_inputs, fire_inputs）在静态属性中定义。
 */
@Getter
public class WeaponControllerSubsystemAttr extends BasicSubsystemDynamicAttr {

    public final WeaponControllerSubsystemStaticAttr staticAttribute;

    /** 控制信号输出频道 → 目标名称列表（同时用于握手发现下属子系统） */
    public final Map<String, List<String>> controlOutputTargets;

    /**
     * 本火控负责的视点发现表：频道 → 目标名称列表。<br>
     * 只读发现，与 {@link #controlOutputTargets} 分开：那张表是命令输出，混入发现会让
     * “输出”和“发现”共用一张表。缺省空表，表示本火控不声明任何视点。
     */
    public final Map<String, List<String>> cameraDiscoveryTargets;

    /** 第一人称视角使用的 HUD 布局；{@link HudLayout#EMPTY} 表示该视角不显示 HUD */
    public final ResourceLocation firstHudLayout;

    /** 第三人称视角使用的 HUD 布局；{@link HudLayout#EMPTY} 表示该视角不显示 HUD */
    public final ResourceLocation thirdHudLayout;

    /** 炮镜视角使用的 HUD 布局；{@link HudLayout#EMPTY} 表示该视角不显示 HUD */
    public final ResourceLocation scopeHudLayout;

    public static final MapCodec<WeaponControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("control_outputs", Map.of()).forGetter(WeaponControllerSubsystemAttr::getControlOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("camera_discovery_targets", Map.of()).forGetter(WeaponControllerSubsystemAttr::getCameraDiscoveryTargets),
            ResourceLocation.CODEC.optionalFieldOf("first_hud_layout", HudLayout.EMPTY)
                    .forGetter(WeaponControllerSubsystemAttr::getFirstHudLayout),
            ResourceLocation.CODEC.optionalFieldOf("third_hud_layout", HudLayout.EMPTY)
                    .forGetter(WeaponControllerSubsystemAttr::getThirdHudLayout),
            ResourceLocation.CODEC.optionalFieldOf("scope_hud_layout", HudLayout.EMPTY)
                    .forGetter(WeaponControllerSubsystemAttr::getScopeHudLayout)
    ).apply(instance, WeaponControllerSubsystemAttr::new));

    public WeaponControllerSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> controlOutputTargets,
            Map<String, List<String>> cameraDiscoveryTargets,
            ResourceLocation firstHudLayout,
            ResourceLocation thirdHudLayout,
            ResourceLocation scopeHudLayout) {
        super(modelName);
        this.staticAttribute = (WeaponControllerSubsystemStaticAttr) getStaticAttr();
        this.controlOutputTargets = controlOutputTargets;
        this.cameraDiscoveryTargets = cameraDiscoveryTargets;
        this.firstHudLayout = firstHudLayout;
        this.thirdHudLayout = thirdHudLayout;
        this.scopeHudLayout = scopeHudLayout;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new WeaponControllerSubsystem(owner, name, this);
    }
}
