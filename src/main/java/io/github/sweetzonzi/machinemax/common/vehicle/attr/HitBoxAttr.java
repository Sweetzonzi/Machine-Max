package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Map;

//TODO:等效厚度、减伤和抗性改为针对不同伤害类型有不同数值
public record HitBoxAttr(
        String hitBoxName,
        String shapeType,
        boolean angleEffect,
        float RHA,
        float damageReduction,
        float damageMultiplier,
        boolean unPenetrateDamage,
        float unPenetrateDamageFactor
) {

    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "part").forGetter(HitBoxAttr::hitBoxName),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::shapeType),
            Codec.BOOL.optionalFieldOf("angle_effect", true).forGetter(HitBoxAttr::angleEffect),
            Codec.FLOAT.optionalFieldOf("rha", 1.0f).forGetter(HitBoxAttr::RHA),
            Codec.FLOAT.optionalFieldOf("damage_reduction", 0.0f).forGetter(HitBoxAttr::damageReduction),
            Codec.FLOAT.optionalFieldOf("damage_multiplier", 1.0f).forGetter(HitBoxAttr::damageMultiplier),
            Codec.BOOL.optionalFieldOf("un_penetrate_damage", false).forGetter(HitBoxAttr::unPenetrateDamage),
            Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor", 1.0f).forGetter(HitBoxAttr::unPenetrateDamageFactor)
    ).apply(instance, HitBoxAttr::new));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );
}
