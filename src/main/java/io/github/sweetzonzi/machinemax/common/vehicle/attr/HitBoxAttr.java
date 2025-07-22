package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

//TODO:等效厚度、减伤和抗性改为针对不同伤害类型有不同数值
public record HitBoxAttr(
        String hitBoxName,
        String shapeType,
        Vec3 friction,
        float slipAdaptation,
        float rollingFriction,
        float spinningFriction,
        float restitution,
        float blockDamageFactor,
        boolean angleEffect,
        float RHA,
        float damageReduction,
        float damageMultiplier,
        float unPenetrateDamageFactor
) {

    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "part").forGetter(HitBoxAttr::hitBoxName),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::shapeType),
            Vec3.CODEC.optionalFieldOf("friction", new Vec3(0.5, 0.5, 0.5)).forGetter(HitBoxAttr::friction),
            Codec.FLOAT.optionalFieldOf("slip_adaptation", 0.5f).forGetter(HitBoxAttr::slipAdaptation),
            Codec.FLOAT.optionalFieldOf("rolling_friction", 0.2f).forGetter(HitBoxAttr::rollingFriction),
            Codec.FLOAT.optionalFieldOf("spinning_friction", 0f).forGetter(HitBoxAttr::spinningFriction),
            Codec.FLOAT.optionalFieldOf("restitution", 0.1f).forGetter(HitBoxAttr::restitution),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", 1.0f).forGetter(HitBoxAttr::blockDamageFactor),
            Codec.BOOL.optionalFieldOf("angle_effect", true).forGetter(HitBoxAttr::angleEffect),
            Codec.FLOAT.optionalFieldOf("rha", 1.0f).forGetter(HitBoxAttr::RHA),
            Codec.FLOAT.optionalFieldOf("damage_reduction", 0.0f).forGetter(HitBoxAttr::damageReduction),
            Codec.FLOAT.optionalFieldOf("damage_multiplier", 1.0f).forGetter(HitBoxAttr::damageMultiplier),
            Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor", 0.0f).forGetter(HitBoxAttr::unPenetrateDamageFactor)
    ).apply(instance, HitBoxAttr::new));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );
}
