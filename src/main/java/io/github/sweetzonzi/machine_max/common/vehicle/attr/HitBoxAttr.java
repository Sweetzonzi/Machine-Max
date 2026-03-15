package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.DamageModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;


public record HitBoxAttr(
        String id,
        String shapeType,
        String subsystem,

        PhysicsAttr physicsAttr,

        float blockDamageFactor,
        boolean angleEffect,
        float RHA,
        DamageModifier impactModifiers,
        DamageModifier piercingModifiers,
        DamageModifier damageModifiers,
        float unPenetrateDamageFactor,
        String condition,
        HitBoxSoundAttr sounds
) {

    private record PhysicsAttr(
            Vec3 friction,
            float slipAdaptation,
            float rollingFriction,
            float spinningFriction,
            float restitution
    ) {
        public static final MapCodec<PhysicsAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Vec3.CODEC.optionalFieldOf("friction", new Vec3(0.5, 0.5, 0.5)).forGetter(PhysicsAttr::friction),
                Codec.FLOAT.optionalFieldOf("slip_adaptation", 0.5f).forGetter(PhysicsAttr::slipAdaptation),
                Codec.FLOAT.optionalFieldOf("rolling_friction", 0.2f).forGetter(PhysicsAttr::rollingFriction),
                Codec.FLOAT.optionalFieldOf("spinning_friction", 0f).forGetter(PhysicsAttr::spinningFriction),
                Codec.FLOAT.optionalFieldOf("restitution", 0.1f).forGetter(PhysicsAttr::restitution)
        ).apply(instance, PhysicsAttr::new));
    }

    public record HitBoxSoundAttr(
            SoundEvent onHitUnPen,
            SoundEvent onHitPen
    ){
        public static final HitBoxSoundAttr DEFAULT = new HitBoxSoundAttr(
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "hit_box.hit.up_pen.metal"), 64f),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "hit_box.hit.pen.metal"), 64f)
        );

        public static final Codec<HitBoxSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SoundEvent.DIRECT_CODEC.optionalFieldOf("hit_un_pen", DEFAULT.onHitUnPen).forGetter(HitBoxSoundAttr::onHitUnPen),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("hit_pen", DEFAULT.onHitPen).forGetter(HitBoxSoundAttr::onHitPen)
        ).apply(instance, HitBoxSoundAttr::new));
    }

    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(HitBoxAttr::id),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::shapeType),
            Codec.STRING.optionalFieldOf("subsystem", "").forGetter(HitBoxAttr::subsystem),
            PhysicsAttr.CODEC.forGetter(HitBoxAttr::physicsAttr),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", 1.0f).forGetter(HitBoxAttr::blockDamageFactor),
            Codec.BOOL.optionalFieldOf("angle_effect", true).forGetter(HitBoxAttr::angleEffect),
            Codec.FLOAT.optionalFieldOf("rha", 1.0f).forGetter(HitBoxAttr::RHA),
            DamageModifier.CODEC.optionalFieldOf("impact_modifiers", new DamageModifier(List.of())).forGetter(HitBoxAttr::impactModifiers),
            DamageModifier.CODEC.optionalFieldOf("penetration_modifiers", DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS).forGetter(HitBoxAttr::piercingModifiers),
            DamageModifier.CODEC.optionalFieldOf("damage_modifiers", DamageModifier.DEFAULT_DAMAGE_MODIFIERS).forGetter(HitBoxAttr::damageModifiers),
            Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor", 0.0f).forGetter(HitBoxAttr::unPenetrateDamageFactor),
            Codec.STRING.optionalFieldOf("condition", "true").forGetter(HitBoxAttr::condition),
            HitBoxSoundAttr.CODEC.optionalFieldOf("sounds", HitBoxSoundAttr.DEFAULT).forGetter(HitBoxAttr::sounds)
    ).apply(instance, HitBoxAttr::new));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );

    public Vec3 friction() {
        return physicsAttr.friction;
    }

    public float slipAdaptation() {
        return physicsAttr.slipAdaptation;
    }

    public float rollingFriction() {
        return physicsAttr.rollingFriction;
    }

    public float spinningFriction() {
        return physicsAttr.spinningFriction;
    }

    public float restitution() {
        return physicsAttr.restitution;
    }

}
