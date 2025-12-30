package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr.AbstractSubsystemAttr;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class InteractBoxAttr {
    public final String boneName;
    public final Map<String, List<String>> signalTargets;
    public final String mode;
    public final Condition condition;

    public enum Condition {
        AND,
        OR,
        NAND,
        NOR,
        XOR,
        XNOR
    }

    public static final Codec<InteractBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("bone").forGetter(InteractBoxAttr::getBoneName),
            ConnectorAttr.SIGNAL_TARGETS_CODEC.fieldOf("signal_targets").forGetter(InteractBoxAttr::getSignalTargets),
            Codec.STRING.optionalFieldOf("interact_mode", "fast").forGetter(InteractBoxAttr::getMode),
            Codec.STRING.optionalFieldOf("condition", "NOR").forGetter(InteractBoxAttr::getCondition)
    ).apply(instance, InteractBoxAttr::new));

    public static final Codec<Map<String, InteractBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//交互区名称
            CODEC//形状属性，包含控制形状的骨骼名称、信号传输频道和目标、交互模式等
    );

    public InteractBoxAttr(String boneName, Map<String, List<String>> signalTargets, String mode, String condition) {
        this.boneName = boneName;
        this.signalTargets = signalTargets;
        this.mode = mode;
        this.condition = Condition.valueOf(condition.toUpperCase());
    }

    private String getCondition() {
        return condition.name();
    }
}
