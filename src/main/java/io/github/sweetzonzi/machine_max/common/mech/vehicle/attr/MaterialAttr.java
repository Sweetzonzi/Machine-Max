package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.DamageModifier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.phys.Vec3;

/**
 * 材料属性，按功能分为物理、装甲、音效三组。
 * <p>
 * 伤害管线：条件化 damageModifiers → 线性减伤(dmg_resist) → 百分比乘算(dmg_mult)
 * </p>
 */
public record MaterialAttr(
        PhysicsAttr physics,
        ArmorAttr armor,
        MaterialSoundAttr sounds
) {
    public static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "default");

    public static final MaterialAttr DEFAULT = new MaterialAttr(
            PhysicsAttr.DEFAULT,
            ArmorAttr.DEFAULT,
            MaterialSoundAttr.DEFAULT
    );

    public static final Codec<MaterialAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            PhysicsAttr.CODEC.optionalFieldOf("physics", PhysicsAttr.DEFAULT).forGetter(MaterialAttr::physics),
            ArmorAttr.CODEC.optionalFieldOf("armor", ArmorAttr.DEFAULT).forGetter(MaterialAttr::armor),
            MaterialSoundAttr.CODEC.optionalFieldOf("sounds", MaterialSoundAttr.DEFAULT).forGetter(MaterialAttr::sounds)
    ).apply(instance, MaterialAttr::new));

    // ==================== 物理属性 ====================

    /**
     * 材料物理属性：摩擦、滑移、弹性等有物理模拟意义的参数。
     */
    public record PhysicsAttr(
            Vec3 friction,
            float slipAdaptation,
            SlipCurveAttr slipCurve,
            float rollingFriction,
            float spinningFriction,
            float restitution,
            float blockDamageFactor
    ) {
        public static final PhysicsAttr DEFAULT = new PhysicsAttr(
                new Vec3(0.5, 0.5, 0.5),
                0.5f,
                SlipCurveAttr.DEFAULT,
                0.2f,
                0f,
                0.1f,
                1.0f
        );

        public static final Codec<PhysicsAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Vec3.CODEC.optionalFieldOf("friction", DEFAULT.friction()).forGetter(PhysicsAttr::friction),
                Codec.FLOAT.optionalFieldOf("slip_adaptation", DEFAULT.slipAdaptation()).forGetter(PhysicsAttr::slipAdaptation),
                SlipCurveAttr.CODEC.optionalFieldOf("slip_curve", DEFAULT.slipCurve()).forGetter(PhysicsAttr::slipCurve),
                Codec.FLOAT.optionalFieldOf("rolling_friction", DEFAULT.rollingFriction()).forGetter(PhysicsAttr::rollingFriction),
                Codec.FLOAT.optionalFieldOf("spinning_friction", DEFAULT.spinningFriction()).forGetter(PhysicsAttr::spinningFriction),
                Codec.FLOAT.optionalFieldOf("restitution", DEFAULT.restitution()).forGetter(PhysicsAttr::restitution),
                Codec.FLOAT.optionalFieldOf("block_damage_factor", DEFAULT.blockDamageFactor()).forGetter(PhysicsAttr::blockDamageFactor)
        ).apply(instance, PhysicsAttr::new));

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
    }

    // ==================== 装甲属性 ====================

    /**
     * 材料装甲属性：抗穿、线性减伤、百分比减伤、耐久衰减参数及条件化修改器。
     * <p>
     * 伤害管线：{@code damageModifiers}.apply() → (dmg - effectiveResist) * effectiveMult
     * </p>
     */
    public record ArmorAttr(
            /** 材质相对轧制均质装甲(RHA)抗穿系数，1.0=标准钢 */
            float rha,
            /** 等效护甲厚度是否受入射角影响（倾斜装甲效果） */
            boolean angleEffect,
            /** 全局线性减伤值，默认 1.0 表示减免 1 点伤害 */
            float dmg_resist,
            /** 全局伤害乘数，默认 0.95 表示承受 95% 伤害 */
            float dmg_mult,
            /** 耐久归零时 RHA 系数的残余值 */
            float decay_rha,
            /** 耐久归零时线性减伤的残余值，默认 0.0 表示完全归零 */
            float decay_dmg_resist,
            /** 耐久归零时伤害乘数的残余值，默认 1.0 表示完全失效 */
            float decay_dmg_mult,
            /** 装甲退化曲线指数（>1 则先硬后脆） */
            float decay_power,
            /** 未穿透伤害指数（幂次），0.0 表示未击穿无伤害 */
            float unpen_power,
            /** 条件驱动的冲击修正器 */
            DamageModifier impactModifiers,
            /** 条件驱动的穿深修正器 */
            DamageModifier piercingModifiers,
            /** 条件驱动的伤害修正器（类型/实体特定调节） */
            DamageModifier damageModifiers
    ) {

        public static final ArmorAttr DEFAULT = new ArmorAttr(
                1.0f,       // rha
                true,       // angleEffect
                1.0f,       // dmg_resist
                0.95f,      // dmg_mult
                0.3f,
                0.0f,       // decay_dmg_resist
                1.0f,       // decay_dmg_mult
                1.5f,
                0.0f,       // unpen_power
                DamageModifier.DEFAULT_DAMAGE_MODIFIERS,
                DamageModifier.DEFAULT_PEN_DEPTH_MODIFIERS,
                DamageModifier.DEFAULT_DAMAGE_MODIFIERS
        );

        public static final Codec<ArmorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("rha", DEFAULT.rha()).forGetter(ArmorAttr::rha),
                Codec.BOOL.optionalFieldOf("angle_effect", DEFAULT.angleEffect()).forGetter(ArmorAttr::angleEffect),
                Codec.FLOAT.optionalFieldOf("dmg_resist", DEFAULT.dmg_resist()).forGetter(ArmorAttr::dmg_resist),
                Codec.FLOAT.optionalFieldOf("dmg_mult", DEFAULT.dmg_mult()).forGetter(ArmorAttr::dmg_mult),
                Codec.FLOAT.optionalFieldOf("decay_rha", DEFAULT.decay_rha()).forGetter(ArmorAttr::decay_rha),
                Codec.FLOAT.optionalFieldOf("decay_dmg_resist", DEFAULT.decay_dmg_resist()).forGetter(ArmorAttr::decay_dmg_resist),
                Codec.FLOAT.optionalFieldOf("decay_dmg_mult", DEFAULT.decay_dmg_mult()).forGetter(ArmorAttr::decay_dmg_mult),
                Codec.FLOAT.optionalFieldOf("decay_power", DEFAULT.decay_power()).forGetter(ArmorAttr::decay_power),
                Codec.FLOAT.optionalFieldOf("unpen_power", DEFAULT.unpen_power()).forGetter(ArmorAttr::unpen_power),
                DamageModifier.CODEC.optionalFieldOf("impact_modifiers", DEFAULT.impactModifiers()).forGetter(ArmorAttr::impactModifiers),
                DamageModifier.CODEC.optionalFieldOf("penetration_modifiers", DEFAULT.piercingModifiers()).forGetter(ArmorAttr::piercingModifiers),
                DamageModifier.CODEC.optionalFieldOf("damage_modifiers", DEFAULT.damageModifiers()).forGetter(ArmorAttr::damageModifiers)
        ).apply(instance, ArmorAttr::new));
    }

    // ==================== 音效属性 ====================

    /**
     * 材料对应的命中音效。
     */
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
