package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.DamageModifier;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;


@Getter
public class HitBoxAttr {
    /** 碰撞箱id，同id的碰撞箱不会被同一次攻击重复命中 */
    public final String id;
    /** 形状类型，目前支持cube、cylinder、sphere, capsule, wheel */
    public final String shapeType;
    /** 子系统名称，受到伤害时会将伤害等量地传递给子系统 */
    public final String subsystem;
    /** 材质注册id，用于获取摩擦，减伤等属性 */
    public final ResourceLocation material;
    /** 等效护甲厚度，用于穿甲判定 */
    public final float thickness;
    /** 碰撞箱启用条件，Molang语句 */
    public final String condition;
    /** 材质属性覆写 */
    @Nullable
    public final OverwriteAttr overwrite;
    /** 最终材质属性，考虑用户自定义覆写 */
    private final MaterialAttr effectiveMaterial;

    public HitBoxAttr(String id, String shapeType, String subsystem, ResourceLocation material, float thickness, String condition, @Nullable OverwriteAttr overwrite) {
        this.id = id;
        this.shapeType = shapeType;
        this.subsystem = subsystem;
        this.material = material;
        this.thickness = thickness;
        this.condition = condition;
        this.overwrite = overwrite;
        this.effectiveMaterial = getEffectiveMaterial();
    }

    public static final Codec<HitBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(HitBoxAttr::getId),
            Codec.STRING.fieldOf("type").forGetter(HitBoxAttr::getShapeType),
            Codec.STRING.optionalFieldOf("subsystem", "").forGetter(HitBoxAttr::getSubsystem),
            ResourceLocation.CODEC.optionalFieldOf("material", MaterialAttr.DEFAULT_ID).forGetter(HitBoxAttr::getMaterial),
            Codec.FLOAT.optionalFieldOf("thickness", 1.0f).forGetter(HitBoxAttr::getThickness),
            Codec.STRING.optionalFieldOf("condition", "true").forGetter(HitBoxAttr::getCondition),
            OverwriteAttr.CODEC.optionalFieldOf("overwrite").forGetter(attr -> Optional.ofNullable(attr.getOverwrite()))
    ).apply(instance, (
            id,
            shapeType,
            subsystem,
            material,
            thickness,
            condition,
            overwrite
    ) -> new HitBoxAttr(id, shapeType, subsystem, material, thickness, condition, overwrite.orElse(null))));

    public static final Codec<Map<String, HitBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,   // 形状骨骼名称
            CODEC            // 形状属性，包含形状类型、材质名称、及厚度
    );

    // ==================== 覆写属性 ====================

    /**
     * HitBox 材质属性覆写，按 physics / armor / sounds 三组分别可选覆写。
     * <p>
     * physics 和 armor 均为字段级 Optional 覆写（未提供则沿用基础材质值），
     * sounds 为整体覆写。
     * </p>
     */
    public record OverwriteAttr(
            Optional<PhysicsOverwriteAttr> physics,
            Optional<ArmorOverwriteAttr> armor,
            Optional<MaterialAttr.MaterialSoundAttr> sounds
    ) {
        public static final Codec<OverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PhysicsOverwriteAttr.CODEC.optionalFieldOf("physics").forGetter(OverwriteAttr::physics),
                ArmorOverwriteAttr.CODEC.optionalFieldOf("armor").forGetter(OverwriteAttr::armor),
                MaterialAttr.MaterialSoundAttr.CODEC.optionalFieldOf("sounds").forGetter(OverwriteAttr::sounds)
        ).apply(instance, OverwriteAttr::new));
    }

    /**
     * 物理属性覆写，字段级 Optional。
     */
    public record PhysicsOverwriteAttr(
            Optional<Vec3> friction,
            Optional<Float> slipAdaptation,
            Optional<SlipCurveOverwriteAttr> slipCurve,
            Optional<Float> rollingFriction,
            Optional<Float> spinningFriction,
            Optional<Float> restitution,
            Optional<Float> blockDamageFactor
    ) {
        public static final Codec<PhysicsOverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Vec3.CODEC.optionalFieldOf("friction").forGetter(PhysicsOverwriteAttr::friction),
                Codec.FLOAT.optionalFieldOf("slip_adaptation").forGetter(PhysicsOverwriteAttr::slipAdaptation),
                SlipCurveOverwriteAttr.CODEC.optionalFieldOf("slip_curve").forGetter(PhysicsOverwriteAttr::slipCurve),
                Codec.FLOAT.optionalFieldOf("rolling_friction").forGetter(PhysicsOverwriteAttr::rollingFriction),
                Codec.FLOAT.optionalFieldOf("spinning_friction").forGetter(PhysicsOverwriteAttr::spinningFriction),
                Codec.FLOAT.optionalFieldOf("restitution").forGetter(PhysicsOverwriteAttr::restitution),
                Codec.FLOAT.optionalFieldOf("block_damage_factor").forGetter(PhysicsOverwriteAttr::blockDamageFactor)
        ).apply(instance, PhysicsOverwriteAttr::new));
    }

    /**
     * 装甲属性覆写，字段级 Optional。
     */
    public record ArmorOverwriteAttr(
            Optional<Float> rha,
            Optional<Boolean> angleEffect,
            Optional<Float> dmg_resist,
            Optional<Float> dmg_mult,
            Optional<Float> decay_rha,
            Optional<Float> decay_dmg_resist,
            Optional<Float> decay_dmg_mult,
            Optional<Float> decay_power,
            Optional<Float> unpen_power,
            Optional<DamageModifier> impactModifiers,
            Optional<DamageModifier> piercingModifiers,
            Optional<DamageModifier> damageModifiers
    ) {
        public static final Codec<ArmorOverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("rha").forGetter(ArmorOverwriteAttr::rha),
                Codec.BOOL.optionalFieldOf("angle_effect").forGetter(ArmorOverwriteAttr::angleEffect),
                Codec.FLOAT.optionalFieldOf("dmg_resist").forGetter(ArmorOverwriteAttr::dmg_resist),
                Codec.FLOAT.optionalFieldOf("dmg_mult").forGetter(ArmorOverwriteAttr::dmg_mult),
                Codec.FLOAT.optionalFieldOf("decay_rha").forGetter(ArmorOverwriteAttr::decay_rha),
                Codec.FLOAT.optionalFieldOf("decay_dmg_resist").forGetter(ArmorOverwriteAttr::decay_dmg_resist),
                Codec.FLOAT.optionalFieldOf("decay_dmg_mult").forGetter(ArmorOverwriteAttr::decay_dmg_mult),
                Codec.FLOAT.optionalFieldOf("decay_power").forGetter(ArmorOverwriteAttr::decay_power),
                Codec.FLOAT.optionalFieldOf("unpen_power").forGetter(ArmorOverwriteAttr::unpen_power),
                DamageModifier.CODEC.optionalFieldOf("impact_modifiers").forGetter(ArmorOverwriteAttr::impactModifiers),
                DamageModifier.CODEC.optionalFieldOf("penetration_modifiers").forGetter(ArmorOverwriteAttr::piercingModifiers),
                DamageModifier.CODEC.optionalFieldOf("damage_modifiers").forGetter(ArmorOverwriteAttr::damageModifiers)
        ).apply(instance, ArmorOverwriteAttr::new));
    }

    public record SlipCurveOverwriteAttr(
            Optional<LongitudinalSlipCurveOverwriteAttr> longitudinal,
            Optional<LateralSlipCurveOverwriteAttr> lateral
    ) {
        public static final Codec<SlipCurveOverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                LongitudinalSlipCurveOverwriteAttr.CODEC.optionalFieldOf("longitudinal").forGetter(SlipCurveOverwriteAttr::longitudinal),
                LateralSlipCurveOverwriteAttr.CODEC.optionalFieldOf("lateral").forGetter(SlipCurveOverwriteAttr::lateral)
        ).apply(instance, SlipCurveOverwriteAttr::new));
    }

    public record LongitudinalSlipCurveOverwriteAttr(
            Optional<Float> peakSlipRatio,
            Optional<Float> baseScale,
            Optional<Float> peakScale,
            Optional<Float> kineticScale
    ) {
        public static final Codec<LongitudinalSlipCurveOverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("peak_slip_ratio").forGetter(LongitudinalSlipCurveOverwriteAttr::peakSlipRatio),
                Codec.FLOAT.optionalFieldOf("base_scale").forGetter(LongitudinalSlipCurveOverwriteAttr::baseScale),
                Codec.FLOAT.optionalFieldOf("peak_scale").forGetter(LongitudinalSlipCurveOverwriteAttr::peakScale),
                Codec.FLOAT.optionalFieldOf("kinetic_scale").forGetter(LongitudinalSlipCurveOverwriteAttr::kineticScale)
        ).apply(instance, LongitudinalSlipCurveOverwriteAttr::new));
    }

    public record LateralSlipCurveOverwriteAttr(
            Optional<Float> peakAngleDeg,
            Optional<Float> kineticAngleDeg,
            Optional<Float> baseScale,
            Optional<Float> peakScale,
            Optional<Float> kineticScale
    ) {
        public static final Codec<LateralSlipCurveOverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("peak_angle_deg").forGetter(LateralSlipCurveOverwriteAttr::peakAngleDeg),
                Codec.FLOAT.optionalFieldOf("kinetic_angle_deg").forGetter(LateralSlipCurveOverwriteAttr::kineticAngleDeg),
                Codec.FLOAT.optionalFieldOf("base_scale").forGetter(LateralSlipCurveOverwriteAttr::baseScale),
                Codec.FLOAT.optionalFieldOf("peak_scale").forGetter(LateralSlipCurveOverwriteAttr::peakScale),
                Codec.FLOAT.optionalFieldOf("kinetic_scale").forGetter(LateralSlipCurveOverwriteAttr::kineticScale)
        ).apply(instance, LateralSlipCurveOverwriteAttr::new));
    }

    // ==================== 有效材质合并 ====================

    /**
     * 获取有效的材质属性，合并基础材质和覆写属性。
     *
     * @return 合并后的材质属性
     */
    public MaterialAttr getEffectiveMaterial() {
        MaterialAttr baseMaterial = getMaterial(material);
        if (overwrite == null) {
            return baseMaterial;
        }
        return new MaterialAttr(
                mergePhysics(baseMaterial.physics(), overwrite.physics().orElse(null)),
                mergeArmor(baseMaterial.armor(), overwrite.armor().orElse(null)),
                overwrite.sounds().orElse(baseMaterial.sounds())
        );
    }

    private static MaterialAttr.PhysicsAttr mergePhysics(
            MaterialAttr.PhysicsAttr base,
            @Nullable PhysicsOverwriteAttr ow
    ) {
        if (ow == null) return base;
        MaterialAttr.PhysicsAttr.SlipCurveAttr effectiveSlipCurve = mergeSlipCurve(base.slipCurve(), ow.slipCurve().orElse(null));
        return new MaterialAttr.PhysicsAttr(
                ow.friction().orElse(base.friction()),
                ow.slipAdaptation().orElse(base.slipAdaptation()),
                effectiveSlipCurve,
                ow.rollingFriction().orElse(base.rollingFriction()),
                ow.spinningFriction().orElse(base.spinningFriction()),
                ow.restitution().orElse(base.restitution()),
                ow.blockDamageFactor().orElse(base.blockDamageFactor())
        );
    }

    private static MaterialAttr.ArmorAttr mergeArmor(
            MaterialAttr.ArmorAttr base,
            @Nullable ArmorOverwriteAttr ow
    ) {
        if (ow == null) return base;
        return new MaterialAttr.ArmorAttr(
                ow.rha().orElse(base.rha()),
                ow.angleEffect().orElse(base.angleEffect()),
                ow.dmg_resist().orElse(base.dmg_resist()),
                ow.dmg_mult().orElse(base.dmg_mult()),
                ow.decay_rha().orElse(base.decay_rha()),
                ow.decay_dmg_resist().orElse(base.decay_dmg_resist()),
                ow.decay_dmg_mult().orElse(base.decay_dmg_mult()),
                ow.decay_power().orElse(base.decay_power()),
                ow.unpen_power().orElse(base.unpen_power()),
                ow.impactModifiers().orElse(base.impactModifiers()),
                ow.piercingModifiers().orElse(base.piercingModifiers()),
                ow.damageModifiers().orElse(base.damageModifiers())
        );
    }

    private static MaterialAttr.PhysicsAttr.SlipCurveAttr mergeSlipCurve(
            MaterialAttr.PhysicsAttr.SlipCurveAttr base,
            @Nullable SlipCurveOverwriteAttr ow
    ) {
        if (ow == null) return base;
        MaterialAttr.PhysicsAttr.LongitudinalSlipCurveAttr baseLongitudinal = base.longitudinal();
        MaterialAttr.PhysicsAttr.LateralSlipCurveAttr baseLateral = base.lateral();

        MaterialAttr.PhysicsAttr.LongitudinalSlipCurveAttr longitudinal = ow.longitudinal()
                .map(o -> new MaterialAttr.PhysicsAttr.LongitudinalSlipCurveAttr(
                        o.peakSlipRatio().orElse(baseLongitudinal.peakSlipRatio()),
                        o.baseScale().orElse(baseLongitudinal.baseScale()),
                        o.peakScale().orElse(baseLongitudinal.peakScale()),
                        o.kineticScale().orElse(baseLongitudinal.kineticScale())
                ))
                .orElse(baseLongitudinal);

        MaterialAttr.PhysicsAttr.LateralSlipCurveAttr lateral = ow.lateral()
                .map(o -> new MaterialAttr.PhysicsAttr.LateralSlipCurveAttr(
                        o.peakAngleDeg().orElse(baseLateral.peakAngleDeg()),
                        o.kineticAngleDeg().orElse(baseLateral.kineticAngleDeg()),
                        o.baseScale().orElse(baseLateral.baseScale()),
                        o.peakScale().orElse(baseLateral.peakScale()),
                        o.kineticScale().orElse(baseLateral.kineticScale())
                ))
                .orElse(baseLateral);

        return new MaterialAttr.PhysicsAttr.SlipCurveAttr(longitudinal, lateral);
    }

    // ==================== 委托方法 ====================

    public static MaterialAttr getMaterial(ResourceLocation materialId) {
        return MMDynamicRes.MATERIALS.getOrDefault(materialId, MaterialAttr.DEFAULT);
    }

    // -- PhysicsAttr 委托 --

    public Vec3 friction() { return effectiveMaterial.physics().friction(); }
    public float slipAdaptation() { return effectiveMaterial.physics().slipAdaptation(); }
    public float rollingFriction() { return effectiveMaterial.physics().rollingFriction(); }
    public float spinningFriction() { return effectiveMaterial.physics().spinningFriction(); }
    public float restitution() { return effectiveMaterial.physics().restitution(); }
    public float blockDamageFactor() { return effectiveMaterial.physics().blockDamageFactor(); }

    // -- ArmorAttr 委托 --

    public float rha() { return effectiveMaterial.armor().rha(); }
    public boolean angleEffect() { return effectiveMaterial.armor().angleEffect(); }
    public float dmg_resist() { return effectiveMaterial.armor().dmg_resist(); }
    public float dmg_mult() { return effectiveMaterial.armor().dmg_mult(); }
    public float decay_rha() { return effectiveMaterial.armor().decay_rha(); }
    public float decay_dmg_resist() { return effectiveMaterial.armor().decay_dmg_resist(); }
    public float decay_dmg_mult() { return effectiveMaterial.armor().decay_dmg_mult(); }
    public float decay_power() { return effectiveMaterial.armor().decay_power(); }
    public float unpen_power() { return effectiveMaterial.armor().unpen_power(); }
    public DamageModifier impactModifiers() { return effectiveMaterial.armor().impactModifiers(); }
    public DamageModifier piercingModifiers() { return effectiveMaterial.armor().piercingModifiers(); }
    public DamageModifier damageModifiers() { return effectiveMaterial.armor().damageModifiers(); }

    // -- MaterialSoundAttr 委托 --

    public MaterialAttr.MaterialSoundAttr sounds() { return effectiveMaterial.sounds(); }
}
