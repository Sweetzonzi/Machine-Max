package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.DamageModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

public record MaterialAttr(
        Vec3 friction,
        float slipAdaptation,
        float rollingFriction,
        float spinningFriction,
        float restitution,
        float blockDamageFactor,
        float rha,
        boolean angleEffect,
        DamageModifier impactModifiers,
        DamageModifier piercingModifiers,
        DamageModifier damageModifiers,
        float unPenetrateDamageFactor,
        MaterialSoundAttr sounds
) {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "default");

    public static final MaterialAttr DEFAULT = new MaterialAttr(
            new Vec3(0.5, 0.5, 0.5),
            0.5f,
            0.2f,
            0f,
            0.1f,
            1.0f,
            1.0f,
            true,
            DamageModifier.DEFAULT_DAMAGE_MODIFIERS,
            DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS,
            DamageModifier.DEFAULT_DAMAGE_MODIFIERS,
            0.0f,
            MaterialSoundAttr.DEFAULT
    );

    public static final Codec<MaterialAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.optionalFieldOf("friction", DEFAULT.friction()).forGetter(MaterialAttr::friction),
            Codec.FLOAT.optionalFieldOf("slip_adaptation", DEFAULT.slipAdaptation()).forGetter(MaterialAttr::slipAdaptation),
            Codec.FLOAT.optionalFieldOf("rolling_friction", DEFAULT.rollingFriction()).forGetter(MaterialAttr::rollingFriction),
            Codec.FLOAT.optionalFieldOf("spinning_friction", DEFAULT.spinningFriction()).forGetter(MaterialAttr::spinningFriction),
            Codec.FLOAT.optionalFieldOf("restitution", DEFAULT.restitution()).forGetter(MaterialAttr::restitution),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", DEFAULT.blockDamageFactor()).forGetter(MaterialAttr::blockDamageFactor),
            Codec.FLOAT.optionalFieldOf("rha", DEFAULT.rha()).forGetter(MaterialAttr::rha),
            Codec.BOOL.optionalFieldOf("angle_effect", DEFAULT.angleEffect()).forGetter(MaterialAttr::angleEffect),
            DamageModifier.CODEC.optionalFieldOf("impact_modifiers", DEFAULT.impactModifiers()).forGetter(MaterialAttr::impactModifiers),
            DamageModifier.CODEC.optionalFieldOf("penetration_modifiers", DEFAULT.piercingModifiers()).forGetter(MaterialAttr::piercingModifiers),
            DamageModifier.CODEC.optionalFieldOf("damage_modifiers", DEFAULT.damageModifiers()).forGetter(MaterialAttr::damageModifiers),
            Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor", DEFAULT.unPenetrateDamageFactor()).forGetter(MaterialAttr::unPenetrateDamageFactor),
            MaterialSoundAttr.CODEC.optionalFieldOf("sounds", DEFAULT.sounds()).forGetter(MaterialAttr::sounds)
    ).apply(instance, MaterialAttr::new));

    public record MaterialSoundAttr(
            SoundEvent onHitUnPen,
            SoundEvent onHitPen
    ) {
        public static final MaterialSoundAttr DEFAULT = new MaterialSoundAttr(
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "hit_box.hit.up_pen.metal"), 64f),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "hit_box.hit.pen.metal"), 64f)
        );

        public static final Codec<MaterialSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SoundEvent.DIRECT_CODEC.optionalFieldOf("hit_un_pen", DEFAULT.onHitUnPen).forGetter(MaterialSoundAttr::onHitUnPen),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("hit_pen", DEFAULT.onHitPen).forGetter(MaterialSoundAttr::onHitPen)
        ).apply(instance, MaterialSoundAttr::new));
    }
}
