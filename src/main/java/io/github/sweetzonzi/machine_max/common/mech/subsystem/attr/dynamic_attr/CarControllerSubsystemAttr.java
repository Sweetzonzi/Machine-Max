package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CarControllerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CarControllerSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 车辆控制器子系统动态属性。<br>
 * 控制输出（引擎/电动机/变速箱/车轮）统一通过control_outputs频道进行握手发现和信号发送。<br>
 * 运行状态输出（速度/油门/转向/刹车/手刹）保持独立频道，用于向HUD、脚本等广播。
 */
@Getter
public class CarControllerSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final CarControllerSubsystemStaticAttr staticAttribute;
    /** 统一控制输出频道 → 目标名称列表（同时用于握手发现下属子系统） */
    public final Map<String, List<String>> controlOutputTargets;
    public final Map<String, List<String>> speedOutputTargets;
    public final Map<String, List<String>> throttleOutputTargets;
    public final Map<String, List<String>> steeringOutputTargets;
    public final Map<String, List<String>> brakeOutputTargets;
    public final Map<String, List<String>> handbrakeOutputTargets;

    public static final MapCodec<CarControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.fieldOf("control_outputs").forGetter(CarControllerSubsystemAttr::getControlOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("speed_outputs", Map.of("vehicle_speed", List.of("local", "global"))).forGetter(CarControllerSubsystemAttr::getSpeedOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("throttle_outputs", Map.of("throttle", List.of("local", "global"))).forGetter(CarControllerSubsystemAttr::getThrottleOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("steering_outputs", Map.of("steering", List.of("local", "global"))).forGetter(CarControllerSubsystemAttr::getSteeringOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("brake_outputs", Map.of("brake", List.of("local", "global"))).forGetter(CarControllerSubsystemAttr::getBrakeOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("handbrake_outputs", Map.of("handbrake", List.of("local", "global"))).forGetter(CarControllerSubsystemAttr::getHandbrakeOutputTargets)
    ).apply(instance, CarControllerSubsystemAttr::new));

    public CarControllerSubsystemAttr(
            ResourceLocation modelName,
            Map<String, List<String>> controlOutputTargets,
            Map<String, List<String>> speedOutputTargets,
            Map<String, List<String>> throttleOutputTargets,
            Map<String, List<String>> steeringOutputTargets,
            Map<String, List<String>> brakeOutputTargets,
            Map<String, List<String>> handbrakeOutputTargets) {
        super(modelName);
        this.staticAttribute = (CarControllerSubsystemStaticAttr) getStaticAttr();
        this.controlOutputTargets = controlOutputTargets;
        this.speedOutputTargets = speedOutputTargets;
        this.throttleOutputTargets = throttleOutputTargets;
        this.steeringOutputTargets = steeringOutputTargets;
        this.brakeOutputTargets = brakeOutputTargets;
        this.handbrakeOutputTargets = handbrakeOutputTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAR_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CarControllerSubsystem(owner, name, this);
    }
}
