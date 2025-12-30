package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.DamageModifier;
import lombok.AccessLevel;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
@Getter
public class HitBoxAttr {
    public final String hitBoxName;
    public final String shapeType;
    public final String subsystem;
    public final Vec3 friction;
    public final float slipAdaptation;
    public final float rollingFriction;
    public final float spinningFriction;
    public final float restitution;
    public final float blockDamageFactor;
    public final boolean angleEffect;
    public final float RHA;
    @Getter(AccessLevel.PRIVATE)
    private final List<DamageModifier.ModifierEntry> impactModifiers;
    @Getter(AccessLevel.PRIVATE)
    private final List<DamageModifier.ModifierEntry> piercingModifiers;
    @Getter(AccessLevel.PRIVATE)
    private final List<DamageModifier.ModifierEntry> damageModifiers;
    public final float unPenetrateDamageFactor;

    public final DamageModifier impactModifier;
    public final DamageModifier piercingModifier;
    public final DamageModifier damageModifier;

    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "part").forGetter(HitBoxAttr::getHitBoxName),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::getShapeType),
            Codec.STRING.optionalFieldOf("subsystem", "").forGetter(HitBoxAttr::getSubsystem),
            Vec3.CODEC.optionalFieldOf("friction", new Vec3(0.5, 0.5, 0.5)).forGetter(HitBoxAttr::getFriction),
            Codec.FLOAT.optionalFieldOf("slip_adaptation", 0.5f).forGetter(HitBoxAttr::getSlipAdaptation),
            Codec.FLOAT.optionalFieldOf("rolling_friction", 0.2f).forGetter(HitBoxAttr::getRollingFriction),
            Codec.FLOAT.optionalFieldOf("spinning_friction", 0f).forGetter(HitBoxAttr::getSpinningFriction),
            Codec.FLOAT.optionalFieldOf("restitution", 0.1f).forGetter(HitBoxAttr::getRestitution),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", 1.0f).forGetter(HitBoxAttr::getBlockDamageFactor),
            Codec.BOOL.optionalFieldOf("angle_effect", true).forGetter(HitBoxAttr::isAngleEffect),
            Codec.FLOAT.optionalFieldOf("rha", 1.0f).forGetter(HitBoxAttr::getRHA),
            DamageModifier.ENTRY_CODEC.listOf().optionalFieldOf("impact_modifiers", List.of()).forGetter(HitBoxAttr::getImpactModifiers),
            DamageModifier.ENTRY_CODEC.listOf().optionalFieldOf("penetration_modifiers", DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS).forGetter(HitBoxAttr::getPiercingModifiers),
            DamageModifier.ENTRY_CODEC.listOf().optionalFieldOf("damage_modifiers", DamageModifier.DEFAULT_DAMAGE_MODIFIERS).forGetter(HitBoxAttr::getDamageModifiers),
            Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor", 0.0f).forGetter(HitBoxAttr::getUnPenetrateDamageFactor)
    ).apply(instance, HitBoxAttr::new));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );

    public HitBoxAttr(
            String hitBoxName,
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
            List<DamageModifier.ModifierEntry> impactModifiers,
            List<DamageModifier.ModifierEntry> piercingModifiers,
            List<DamageModifier.ModifierEntry> damageModifiers,
            float unPenetrateDamageFactor) {
            this.hitBoxName = hitBoxName;
            this.shapeType = shapeType;
            this.subsystem = subsystem;
            this.friction = friction;
            this.slipAdaptation = slipAdaptation;
            this.rollingFriction = rollingFriction;
            this.spinningFriction = spinningFriction;
            this.restitution = restitution;
            this.blockDamageFactor = blockDamageFactor;
            this.angleEffect = angleEffect;
            this.RHA = RHA;
            this.impactModifiers = impactModifiers;
            this.piercingModifiers = piercingModifiers;
            this.damageModifiers = damageModifiers;
            this.unPenetrateDamageFactor = unPenetrateDamageFactor;
            this.impactModifier = new DamageModifier(impactModifiers);
            this.piercingModifier = new DamageModifier(piercingModifiers);
            this.damageModifier = new DamageModifier(damageModifiers);
    }
}
