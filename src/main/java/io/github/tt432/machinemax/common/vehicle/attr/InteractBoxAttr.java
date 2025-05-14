package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record InteractBoxAttr(
        String interactBoxName,
        String shapeType,
        String mode
) {
    public static final Codec<InteractBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("hit_box").forGetter(InteractBoxAttr::interactBoxName),
            Codec.STRING.fieldOf("type").forGetter(InteractBoxAttr::shapeType),
            Codec.STRING.optionalFieldOf("interactMode", "fast").forGetter(InteractBoxAttr::mode)
    ).apply(instance, InteractBoxAttr::new));

    public static final Codec<Map<String, InteractBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、交互模式等
    );
}
