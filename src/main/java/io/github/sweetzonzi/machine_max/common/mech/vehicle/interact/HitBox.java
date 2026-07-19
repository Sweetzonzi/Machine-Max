package io.github.sweetzonzi.machine_max.common.mech.vehicle.interact;

import cn.solarmoon.spark_core.molang.MolangContextRegistry;
import cn.solarmoon.spark_core.molang.runtime.MolangExpression;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

@Getter
public class HitBox {
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final AbstractSubsystem subsystem;
    private boolean active = true;
    private final boolean alwaysActive;
    /**
     * 预编译的 HitBox condition MoLang 表达式
     */
    private final MolangExpression hitBoxCondition;

    public HitBox(SubPart subPart, HitBoxAttr attr) {
        this.subPart = subPart;
        this.attr = attr;
        this.subsystem = subPart.subsystems.getOrDefault(attr.subsystem, null);
        alwaysActive = attr.condition.isEmpty() || attr.condition.equalsIgnoreCase("true");
        this.hitBoxCondition = alwaysActive ? null : MolangContextRegistry.compile(attr.condition, subPart.getSparkMolangContext());
    }

    /**
     * 根据condition脚本更新激活状态
     * 应在每物理刻（prePhysicsTick）调用
     */
    public void updateActive() {
        if (alwaysActive) {
            active = true;
            return;
        }
        try {
            active = hitBoxCondition.evaluate(subPart.getSparkMolangContext()) > 0;
        } catch (Exception e) {
            io.github.sweetzonzi.machine_max.MachineMax.LOGGER.warn(
                    "Failed to evaluate condition for HitBox '{}' in part '{}-{}': {}",
                    attr.id, subPart.part.name, subPart.name, e.getMessage()
            );
        }
    }

    public float modifyImpact(DamageSource source, float amount) {
        return attr.impactModifiers().apply(source, amount);
    }

    public float modifyPiercing(DamageSource source, float amount) {
        return attr.piercingModifiers().apply(source, amount);
    }

    /**
     * 完整伤害修正管线：条件化修正 → 线性减伤 → 百分比乘算。
     *
     * @param source 伤害来源
     * @param amount 原始伤害量
     * @return 修正后的伤害量
     */
    public float modifyDamage(DamageSource source, float amount) {
        // ① 条件化伤害修正（类型/实体特定调整）
        float dmg = attr.damageModifiers().apply(source, amount);
        // ② 全局线性减伤（材料硬度阈值）
        dmg = Math.max(0, dmg - getEffectiveDmgResist(subPart));
        // ③ 全局百分比减伤（材料能量耗散效率）
        dmg *= getEffectiveDmgMult(subPart);
        return dmg;
    }

    /**
     * 计算等效护甲厚度（RHA 毫米数），根据零部件当前耐久度动态衰减。
     * <p>
     * 公式：thickness × [decay_rha + (rha - decay_rha) × (durabilityRatio)^decay_power]
     * </p>
     *
     * @param subPart 所属零部件
     * @return 等效护甲厚度
     */
    public float getRHA(SubPart subPart) {
        float durabilityRatio = subPart.getDurability() / subPart.getMaxDurability();
        float rhaCoeff = attr.decay_rha() + (attr.rha() - attr.decay_rha()) * (float) Math.pow(durabilityRatio, attr.decay_power());
        return attr.thickness * rhaCoeff;
    }

    /**
     * 计算考虑耐久衰减后的有效线性减伤值。
     * <p>
     * 公式：decay_dmg_resist + (dmg_resist - decay_dmg_resist) × durability^decay_power
     * </p>
     *
     * @param subPart 所属零部件
     * @return 有效线性减伤值
     */
    public float getEffectiveDmgResist(SubPart subPart) {
        float durabilityRatio = subPart.getDurability() / subPart.getMaxDurability();
        return attr.decay_dmg_resist() + (attr.dmg_resist() - attr.decay_dmg_resist()) * (float) Math.pow(durabilityRatio, attr.decay_power());
    }

    /**
     * 计算考虑耐久衰减后的有效伤害乘数。
     * <p>
     * 公式：decay_dmg_mult + (dmg_mult - decay_dmg_mult) × durability^decay_power
     * </p>
     *
     * @param subPart 所属零部件
     * @return 有效伤害乘数（1.0=无变化，0.5=一半伤害）
     */
    public float getEffectiveDmgMult(SubPart subPart) {
        float durabilityRatio = subPart.getDurability() / subPart.getMaxDurability();
        return attr.decay_dmg_mult() + (attr.dmg_mult() - attr.decay_dmg_mult()) * (float) Math.pow(durabilityRatio, attr.decay_power());
    }

    public boolean hasAngleEffect() {
        return attr.angleEffect();
    }

    /**
     * 是否有未穿透钝伤效果
     *
     * @return true 表示未穿透时仍可造成部分伤害
     */
    public boolean hasUnpenDamage() {
        return attr.unpen_power() > 0.0f;
    }

    /**
     * 获取未穿透伤害指数（幂次）
     *
     * @return 未穿透伤害指数
     */
    public float getUnpenPower() {
        return attr.unpen_power();
    }

    /**
     * 获取碰撞箱的前向摩擦系数
     *
     * @return 前向摩擦系数
     */
    public double getMuFront() {
        return attr.friction().y();
    }

    /**
     * 获取碰撞箱的侧向摩擦系数
     *
     * @return 侧向摩擦系数
     */
    public double getMuSide() {
        return attr.friction().x();
    }

    public SoundEvent getHitPenSound() {
        return attr.sounds().onHitPen();
    }

    public SoundEvent getHitUnPenSound() {
        return attr.sounds().onHitUnPen();
    }

}
