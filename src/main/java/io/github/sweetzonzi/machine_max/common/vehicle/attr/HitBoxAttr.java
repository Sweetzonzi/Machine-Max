package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.DamageModifier;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;


@Getter
public class HitBoxAttr {
    /**
     * 碰撞箱id, 同id的碰撞箱不会被同一次攻击重复命中
     */
    public final String id;
    /**
     * 形状类型，目前支持cube、cylinder、sphere, capsule, wheel
     */
    public final String shapeType;
    /**
     * 子系统名称，受到伤害时会将伤害等量地传递给子系统
     */
    public final String subsystem;
    /**
     * 材质注册id，用于获取摩擦，减伤等属性
     */
    public final ResourceLocation material;
    /**
     * 等效护甲厚度，用于穿甲判定
     */
    public final float thickness;
    /**
     * 碰撞箱启用条件，Molang语句
     */
    public final String condition;
    /**
     * 材质属性覆写
     */
    @Nullable
    public final OverwriteAttr overwrite;
    /**
     * 最终材质属性，考虑用户自定义覆写
     */
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
            Codec.STRING,//形状骨骼名称
            CODEC//形状属性，包含形状类型、材质名称、及厚度
    );

    /**
     * @param friction                摩擦系数
     * @param slipAdaptation          滑移适应系数
     * @param rollingFriction         滚动摩擦系数
     * @param spinningFriction        旋转摩擦系数
     * @param restitution             弹性系数
     * @param blockDamageFactor       方块伤害系数
     * @param rha                     材质相对轧制均质装甲(RHA)抗穿系数
     * @param angleEffect             角度效应
     * @param impactModifiers         冲击伤害修正
     * @param piercingModifiers       穿甲伤害修正
     * @param damageModifiers         普通伤害修正
     * @param unPenetrateDamageFactor 未穿透伤害系数
     * @param sounds                  材质音效属性
     */
    public record OverwriteAttr(
            Optional<Vec3> friction,
            Optional<Float> slipAdaptation,
            Optional<Float> rollingFriction,
            Optional<Float> spinningFriction,
            Optional<Float> restitution,
            Optional<Float> blockDamageFactor,
            Optional<Float> rha,
            Optional<Boolean> angleEffect,
            Optional<DamageModifier> impactModifiers,
            Optional<DamageModifier> piercingModifiers,
            Optional<DamageModifier> damageModifiers,
            Optional<Float> unPenetrateDamageFactor,
            Optional<MaterialAttr.MaterialSoundAttr> sounds
    ) {
        public static final Codec<OverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Vec3.CODEC.optionalFieldOf("friction").forGetter(OverwriteAttr::friction),
                Codec.FLOAT.optionalFieldOf("slip_adaptation").forGetter(OverwriteAttr::slipAdaptation),
                Codec.FLOAT.optionalFieldOf("rolling_friction").forGetter(OverwriteAttr::rollingFriction),
                Codec.FLOAT.optionalFieldOf("spinning_friction").forGetter(OverwriteAttr::spinningFriction),
                Codec.FLOAT.optionalFieldOf("restitution").forGetter(OverwriteAttr::restitution),
                Codec.FLOAT.optionalFieldOf("block_damage_factor").forGetter(OverwriteAttr::blockDamageFactor),
                Codec.FLOAT.optionalFieldOf("rha").forGetter(OverwriteAttr::rha),
                Codec.BOOL.optionalFieldOf("angle_effect").forGetter(OverwriteAttr::angleEffect),
                DamageModifier.CODEC.optionalFieldOf("impact_modifiers").forGetter(OverwriteAttr::impactModifiers),
                DamageModifier.CODEC.optionalFieldOf("piercing_modifiers").forGetter(OverwriteAttr::piercingModifiers),
                DamageModifier.CODEC.optionalFieldOf("damage_modifiers").forGetter(OverwriteAttr::damageModifiers),
                Codec.FLOAT.optionalFieldOf("un_penetrate_damage_factor").forGetter(OverwriteAttr::unPenetrateDamageFactor),
                MaterialAttr.MaterialSoundAttr.CODEC.optionalFieldOf("sounds").forGetter(OverwriteAttr::sounds)
        ).apply(instance, OverwriteAttr::new));
    }

    /**
     * 获取有效的材质属性，合并基础材质和覆写属性
     *
     * @return 合并后的材质属性
     */
    public MaterialAttr getEffectiveMaterial() {
        MaterialAttr baseMaterial = getMaterial(material);
        if (overwrite == null) {
            return baseMaterial;
        }
        return new MaterialAttr(
                overwrite.friction().isPresent() ? overwrite.friction().get() : baseMaterial.friction(),
                overwrite.slipAdaptation().isPresent() ? overwrite.slipAdaptation().get() : baseMaterial.slipAdaptation(),
                overwrite.rollingFriction().isPresent() ? overwrite.rollingFriction().get() : baseMaterial.rollingFriction(),
                overwrite.spinningFriction().isPresent() ? overwrite.spinningFriction().get() : baseMaterial.spinningFriction(),
                overwrite.restitution().isPresent() ? overwrite.restitution().get() : baseMaterial.restitution(),
                overwrite.blockDamageFactor().isPresent() ? overwrite.blockDamageFactor().get() : baseMaterial.blockDamageFactor(),
                overwrite.rha().isPresent() ? overwrite.rha().get() : baseMaterial.rha(),
                overwrite.angleEffect().isPresent() ? overwrite.angleEffect().get() : baseMaterial.angleEffect(),
                overwrite.impactModifiers().isPresent() ? overwrite.impactModifiers().get() : baseMaterial.impactModifiers(),
                overwrite.piercingModifiers().isPresent() ? overwrite.piercingModifiers().get() : baseMaterial.piercingModifiers(),
                overwrite.damageModifiers().isPresent() ? overwrite.damageModifiers().get() : baseMaterial.damageModifiers(),
                overwrite.unPenetrateDamageFactor().isPresent() ? overwrite.unPenetrateDamageFactor().get() : baseMaterial.unPenetrateDamageFactor(),
                overwrite.sounds().isPresent() ? overwrite.sounds().get() : baseMaterial.sounds()
        );
    }

    public static MaterialAttr getMaterial(ResourceLocation materialId) {
        return MMDynamicRes.MATERIALS.getOrDefault(materialId, MaterialAttr.DEFAULT);
    }

    public Vec3 friction() {
        return effectiveMaterial.friction();
    }

    public float slipAdaptation() {
        return effectiveMaterial.slipAdaptation();
    }

    public float rollingFriction() {
        return effectiveMaterial.rollingFriction();
    }

    public float spinningFriction() {
        return effectiveMaterial.spinningFriction();
    }

    public float restitution() {
        return effectiveMaterial.restitution();
    }

    public float blockDamageFactor() {
        return effectiveMaterial.blockDamageFactor();
    }

    public float rha() {
        return effectiveMaterial.rha();
    }

    public boolean angleEffect() {
        return effectiveMaterial.angleEffect();
    }

    public DamageModifier impactModifiers() {
        return effectiveMaterial.impactModifiers();
    }

    public DamageModifier piercingModifiers() {
        return effectiveMaterial.piercingModifiers();
    }

    public DamageModifier damageModifiers() {
        return effectiveMaterial.damageModifiers();
    }

    public float unPenetrateDamageFactor() {
        return effectiveMaterial.unPenetrateDamageFactor();
    }

    public MaterialAttr.MaterialSoundAttr sounds() {
        return effectiveMaterial.sounds();
    }

}
