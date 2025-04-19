package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

public record ShapeAttr(
        String name,
        String shapeType,
        String materialName,
        float thickness
) {

    public static final Codec<ShapeAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name","default").forGetter(ShapeAttr::name),
            Codec.STRING.fieldOf("type").forGetter(ShapeAttr::shapeType),
            Codec.STRING.optionalFieldOf("material","default").forGetter(ShapeAttr::materialName),
            Codec.FLOAT.optionalFieldOf("thickness",1.0f).forGetter(ShapeAttr::thickness)
    ).apply(instance, ShapeAttr::new));

    public static final Codec<Map<String, ShapeAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );
}
