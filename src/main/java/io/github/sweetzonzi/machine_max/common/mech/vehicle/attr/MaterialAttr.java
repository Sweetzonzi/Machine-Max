package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.DamageModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

public record MaterialAttr(
        Vec3 friction,
        float slipAdaptation,
        SlipCurveAttr slipCurve,
        float rollingFriction,
        float spinningFriction,
        float restitution,
        float blockDamageFactor,
        float rha,
        boolean angleEffect,
        DamageModifier impactModifiers,
        DamageModifier piercingModifiers,
        DamageModifier damageModifiers,
        float decay_rha,
        float decay_power,
        float unpen_power,
        MaterialSoundAttr sounds
) {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "default");

    /** 耐久归零时 RHA 系数的默认值（满耐久 rha=1.0 的 30%） */
    public static final float DEFAULT_DECAY_RHA = 0.3f;
    /** 装甲退化曲线指数的默认值（>1 则先硬后脆） */
    public static final float DEFAULT_DECAY_POWER = 1.5f;

    public static final MaterialAttr DEFAULT = new MaterialAttr(
            new Vec3(0.5, 0.5, 0.5),
            0.5f,
            SlipCurveAttr.DEFAULT,
            0.2f,
            0f,
            0.1f,
            1.0f,
            1.0f,
            true,
            DamageModifier.DEFAULT_DAMAGE_MODIFIERS,
            DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS,
            DamageModifier.DEFAULT_DAMAGE_MODIFIERS,
            DEFAULT_DECAY_RHA,
            DEFAULT_DECAY_POWER,
            0.0f,
            MaterialSoundAttr.DEFAULT
    );

    public static final Codec<MaterialAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.optionalFieldOf("friction", DEFAULT.friction()).forGetter(MaterialAttr::friction),
            Codec.FLOAT.optionalFieldOf("slip_adaptation", DEFAULT.slipAdaptation()).forGetter(MaterialAttr::slipAdaptation),
            SlipCurveAttr.CODEC.optionalFieldOf("slip_curve", DEFAULT.slipCurve()).forGetter(MaterialAttr::slipCurve),
            Codec.FLOAT.optionalFieldOf("rolling_friction", DEFAULT.rollingFriction()).forGetter(MaterialAttr::rollingFriction),
            Codec.FLOAT.optionalFieldOf("spinning_friction", DEFAULT.spinningFriction()).forGetter(MaterialAttr::spinningFriction),
            Codec.FLOAT.optionalFieldOf("restitution", DEFAULT.restitution()).forGetter(MaterialAttr::restitution),
            Codec.FLOAT.optionalFieldOf("block_damage_factor", DEFAULT.blockDamageFactor()).forGetter(MaterialAttr::blockDamageFactor),
            Codec.FLOAT.optionalFieldOf("rha", DEFAULT.rha()).forGetter(MaterialAttr::rha),
            Codec.BOOL.optionalFieldOf("angle_effect", DEFAULT.angleEffect()).forGetter(MaterialAttr::angleEffect),
            DamageModifier.CODEC.optionalFieldOf("impact_modifiers", DEFAULT.impactModifiers()).forGetter(MaterialAttr::impactModifiers),
            DamageModifier.CODEC.optionalFieldOf("penetration_modifiers", DEFAULT.piercingModifiers()).forGetter(MaterialAttr::piercingModifiers),
            DamageModifier.CODEC.optionalFieldOf("damage_modifiers", DEFAULT.damageModifiers()).forGetter(MaterialAttr::damageModifiers),
            Codec.FLOAT.optionalFieldOf("decay_rha", DEFAULT.decay_rha()).forGetter(MaterialAttr::decay_rha),
            Codec.FLOAT.optionalFieldOf("decay_power", DEFAULT.decay_power()).forGetter(MaterialAttr::decay_power),
            Codec.FLOAT.optionalFieldOf("unpen_power", DEFAULT.unpen_power()).forGetter(MaterialAttr::unpen_power),
            MaterialSoundAttr.CODEC.optionalFieldOf("sounds", DEFAULT.sounds()).forGetter(MaterialAttr::sounds)
    ).apply(instance, MaterialAttr::new));

    public record SlipCurveAttr(
            LongitudinalSlipCurveAttr longitudinal,
            LateralSlipCurveAttr lateral
    ) {
        public static final SlipCurveAttr DEFAULT = new SlipCurveAttr(
                LongitudinalSlipCurveAttr.DEFAULT,
                LateralSlipCurveAttr.DEFAULT
        );

        public static final Codec<SlipCurveAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LongitudinalSlipCurveAttr.CODEC.optionalFieldOf("longitudinal", LongitudinalSlipCurveAttr.DEFAULT).forGetter(SlipCurveAttr::longitudinal),
                LateralSlipCurveAttr.CODEC.optionalFieldOf("lateral", LateralSlipCurveAttr.DEFAULT).forGetter(SlipCurveAttr::lateral)
        ).apply(instance, SlipCurveAttr::new));
    }

    public record LongitudinalSlipCurveAttr(
            float peakSlipRatio,
            float baseScale,
            float peakScale,
            float kineticScale
    ) {
        public static final LongitudinalSlipCurveAttr DEFAULT = new LongitudinalSlipCurveAttr(0.20f, 1.0f, 1.4f, 0.9f);

        public static final Codec<LongitudinalSlipCurveAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("peak_slip_ratio", DEFAULT.peakSlipRatio).forGetter(LongitudinalSlipCurveAttr::peakSlipRatio),
                Codec.FLOAT.optionalFieldOf("base_scale", DEFAULT.baseScale).forGetter(LongitudinalSlipCurveAttr::baseScale),
                Codec.FLOAT.optionalFieldOf("peak_scale", DEFAULT.peakScale).forGetter(LongitudinalSlipCurveAttr::peakScale),
                Codec.FLOAT.optionalFieldOf("kinetic_scale", DEFAULT.kineticScale).forGetter(LongitudinalSlipCurveAttr::kineticScale)
        ).apply(instance, LongitudinalSlipCurveAttr::new));
    }

    public record LateralSlipCurveAttr(
            float peakAngleDeg,
            float kineticAngleDeg,
            float baseScale,
            float peakScale,
            float kineticScale
    ) {
        public static final LateralSlipCurveAttr DEFAULT = new LateralSlipCurveAttr(12.0f, 90.0f, 1.0f, 1.2f, 0.7f);

        public static final Codec<LateralSlipCurveAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("peak_angle_deg", DEFAULT.peakAngleDeg).forGetter(LateralSlipCurveAttr::peakAngleDeg),
                Codec.FLOAT.optionalFieldOf("kinetic_angle_deg", DEFAULT.kineticAngleDeg).forGetter(LateralSlipCurveAttr::kineticAngleDeg),
                Codec.FLOAT.optionalFieldOf("base_scale", DEFAULT.baseScale).forGetter(LateralSlipCurveAttr::baseScale),
                Codec.FLOAT.optionalFieldOf("peak_scale", DEFAULT.peakScale).forGetter(LateralSlipCurveAttr::peakScale),
                Codec.FLOAT.optionalFieldOf("kinetic_scale", DEFAULT.kineticScale).forGetter(LateralSlipCurveAttr::kineticScale)
        ).apply(instance, LateralSlipCurveAttr::new));
    }

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
