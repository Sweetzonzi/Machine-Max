package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.DamageModifier;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;


public record HitBoxAttr(
        String id,
        String shapeType,
        String subsystem,

        Vec3 friction,
        float slipAdaptation,
        float rollingFriction,
        float spinningFriction,
        float restitution,

        float blockDamageFactor,
        boolean angleEffect,
        float RHA,
        DamageModifier impactModifiers,
        DamageModifier piercingModifiers,
        DamageModifier damageModifiers,
        float unPenetrateDamageFactor) {
    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(HitBoxAttr::id),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::shapeType),
            Codec.STRING.optionalFieldOf("subsystem", "").forGetter(HitBoxAttr::subsystem),
            Vec3.CODEC.optionalFieldOf("friction", new Vec3(0.5, 0.5, 0.5)).forGetter(HitBoxAttr::friction),
            Codec.FLOAT.optionalFieldOf("slip_adaptation", 0.5f).forGetter(HitBoxAttr::slipAdaptation),
            Codec.FLOAT.optionalFieldOf("rolling_friction", 0.2f).forGetter(HitBoxAttr::rollingFriction),
            Codec.FLOAT.optionalFieldOf("spinning_friction", 0f).forGetter(HitBoxAttr::spinningFriction),
            Codec.FLOAT.optionalFieldOf("restitution", 0.1f).forGetter(HitBoxAttr::restitution),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", 1.0f).forGetter(HitBoxAttr::blockDamageFactor),
            Codec.BOOL.optionalFieldOf("angle_effect", true).forGetter(HitBoxAttr::angleEffect),
            Codec.FLOAT.optionalFieldOf("rha", 1.0f).forGetter(HitBoxAttr::RHA),
            DamageModifier.CODEC.optionalFieldOf("impact_modifiers", new DamageModifier(List.of())).forGetter(HitBoxAttr::impactModifiers),
            DamageModifier.CODEC.optionalFieldOf("penetration_modifiers", DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS).forGetter(HitBoxAttr::piercingModifiers),
            DamageModifier.CODEC.optionalFieldOf("damage_modifiers", DamageModifier.DEFAULT_DAMAGE_MODIFIERS).forGetter(HitBoxAttr::damageModifiers),
            Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor", 0.0f).forGetter(HitBoxAttr::unPenetrateDamageFactor)
    ).apply(instance, HitBoxAttr::new));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );

}
