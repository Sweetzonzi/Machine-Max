package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;

import java.util.List;
import java.util.Map;

public record InteractBoxAttr(
        String boneName,
        Map<String, List<String>> signalTargets,//信号传输频道和目标 Signal channels and targets
        String mode
) {
    public static final Codec<InteractBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("bone").forGetter(InteractBoxAttr::boneName),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.fieldOf("signal_targets").forGetter(InteractBoxAttr::signalTargets),
            Codec.STRING.optionalFieldOf("interact_mode", "fast").forGetter(InteractBoxAttr::mode)
    ).apply(instance, InteractBoxAttr::new));

    public static final Codec<Map<String, InteractBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//交互区名称
            CODEC//形状属性，包含控制形状的骨骼名称、信号传输频道和目标、交互模式等
    );
}
