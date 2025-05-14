package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

//TODO:等效厚度、减伤和抗性改为针对不同伤害类型有不同数值
public record HitBoxAttr(
        String hitBoxName,
        String shapeType,
        float RHA,
        float damageReduction,
        float damageMultiplier
) {

    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("hit_box", "part").forGetter(HitBoxAttr::hitBoxName),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::shapeType),
            Codec.FLOAT.optionalFieldOf("rha", 1.0f).forGetter(HitBoxAttr::RHA),
            Codec.FLOAT.optionalFieldOf("damage_reduction", 0.0f).forGetter(HitBoxAttr::damageReduction),
            Codec.FLOAT.optionalFieldOf("damage_multiplier", 1.0f).forGetter(HitBoxAttr::damageMultiplier)
    ).apply(instance, HitBoxAttr::new));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );
}
