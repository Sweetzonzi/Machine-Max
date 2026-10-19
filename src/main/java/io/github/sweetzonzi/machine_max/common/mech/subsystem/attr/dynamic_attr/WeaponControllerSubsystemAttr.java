package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
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

    /** 第一人称视角使用的 HUD 布局；{@link HudLayout#EMPTY} 表示该视角不显示 HUD */
    public final ResourceLocation firstHudLayout;

    /** 第三人称视角使用的 HUD 布局；{@link HudLayout#EMPTY} 表示该视角不显示 HUD */
    public final ResourceLocation thirdHudLayout;

    /** 炮镜视角使用的 HUD 布局；{@link HudLayout#EMPTY} 表示该视角不显示 HUD */
    public final ResourceLocation scopeHudLayout;

    public static final MapCodec<WeaponControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("control_outputs", Map.of()).forGetter(WeaponControllerSubsystemAttr::getControlOutputTargets),
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
            ResourceLocation firstHudLayout,
            ResourceLocation thirdHudLayout,
            ResourceLocation scopeHudLayout) {
        super(modelName);
        this.staticAttribute = (WeaponControllerSubsystemStaticAttr) getStaticAttr();
        this.controlOutputTargets = controlOutputTargets;
        this.firstHudLayout = firstHudLayout;
        this.thirdHudLayout = thirdHudLayout;
        this.scopeHudLayout = scopeHudLayout;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.WEAPON_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new WeaponControllerSubsystem(owner, name, this);
    }
}
