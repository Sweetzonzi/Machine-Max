package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.TrackControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TrackControllerSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 履带控制器动态属性。实例只声明控制/速度信号的接线，参数在静态属性里。
 */
@Getter
public class TrackControllerSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final TrackControllerSubsystemStaticAttr staticAttribute;
    public final Map<String, List<String>> controlOutputTargets;
    public final Map<String, List<String>> speedOutputTargets;

    public static final MapCodec<TrackControllerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.fieldOf("control_outputs")
                    .forGetter(TrackControllerSubsystemAttr::getControlOutputTargets),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("speed_outputs",
                            Map.of("vehicle_speed", List.of("local", "global")))
                    .forGetter(TrackControllerSubsystemAttr::getSpeedOutputTargets)
    ).apply(instance, TrackControllerSubsystemAttr::new));

    public TrackControllerSubsystemAttr(ResourceLocation modelName,
                                        Map<String, List<String>> controlOutputTargets,
                                        Map<String, List<String>> speedOutputTargets) {
        super(modelName);
        this.staticAttribute = (TrackControllerSubsystemStaticAttr) getStaticAttr();
        this.controlOutputTargets = controlOutputTargets;
        this.speedOutputTargets = speedOutputTargets;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TRACK_CTRL;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new TrackControllerSubsystem(owner, name, this);
    }
}
