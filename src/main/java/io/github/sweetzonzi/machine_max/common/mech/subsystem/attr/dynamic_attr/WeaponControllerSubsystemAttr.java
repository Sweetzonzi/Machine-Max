package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.WeaponControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.WeaponControllerSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 武器控制器子系统动态属性。<br>
 * 定义控制信号输出频道等运行时配置。<br>
 * 输入频道（target_inputs, fire_inputs）在静态属性中定义。
 */
@Getter
public class WeaponControllerSubsystemAttr extends BasicSubsystemDynamicAttr {

    public final WeaponControllerSubsystemStaticAttr staticAttribute;

    /** 控制信号输出频道 → 目标名称列表（同时用于握手发现下属子系统） */
    public final Map<String, List<String>> controlOutputTargets;

    public static final MapCodec<WeaponControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("control_outputs", Map.of()).forGetter(WeaponControllerSubsystemAttr::getControlOutputTargets)
    ).apply(instance, WeaponControllerSubsystemAttr::new));

    public WeaponControllerSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> controlOutputTargets) {
        super(modelName);
        this.staticAttribute = (WeaponControllerSubsystemStaticAttr) getStaticAttr();
        this.controlOutputTargets = controlOutputTargets;
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
