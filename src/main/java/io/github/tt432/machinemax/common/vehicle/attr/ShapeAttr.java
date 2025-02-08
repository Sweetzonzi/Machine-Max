package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record ShapeAttr(
        String type,
        String material,
        float thickness
) {
    public static final Codec<ShapeAttr> CODEC = RecordCodecBuilder.create(instance->instance.group(
            Codec.STRING.fieldOf("type").forGetter(ShapeAttr::type),
            Codec.STRING.fieldOf("material").forGetter(ShapeAttr::material),
            Codec.FLOAT.fieldOf("thickness").forGetter(ShapeAttr::thickness)
    ).apply(instance, ShapeAttr::new));

    public static final Codec<Map<String, ShapeAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//键：包含子部件形状材料相关信息的骨骼组名称
            ShapeAttr.CODEC//值：骨骼组的形状材料相关信息
    );
}
