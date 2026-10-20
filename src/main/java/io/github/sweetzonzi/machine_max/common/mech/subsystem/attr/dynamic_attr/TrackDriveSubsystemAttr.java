package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.TrackDriveSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.TrackDriveSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 履带驱动动态属性。{@code connector} 指向车体侧、连接主动轮的连接点。
 */
@Getter
public class TrackDriveSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final TrackDriveSubsystemStaticAttr staticAttribute;
    public final String controlledConnector;
    public final Map<String, List<String>> speedOutputs;
    public final Map<String, List<String>> forceOutputs;

    public static final MapCodec<TrackDriveSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("connector").forGetter(TrackDriveSubsystemAttr::getControlledConnector),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("speed_outputs",
                            Map.of("track_speed", List.of("local", "global")))
                    .forGetter(TrackDriveSubsystemAttr::getSpeedOutputs),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("force_outputs",
                            Map.of("track_force", List.of("local", "global")))
                    .forGetter(TrackDriveSubsystemAttr::getForceOutputs)
    ).apply(instance, TrackDriveSubsystemAttr::new));

    public TrackDriveSubsystemAttr(ResourceLocation modelName,
                                   String controlledConnector,
                                   Map<String, List<String>> speedOutputs,
                                   Map<String, List<String>> forceOutputs) {
        super(modelName);
        this.staticAttribute = (TrackDriveSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
        this.speedOutputs = speedOutputs;
        this.forceOutputs = forceOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TRACK_DRIVE;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new TrackDriveSubsystem(owner, name, this);
    }
}
