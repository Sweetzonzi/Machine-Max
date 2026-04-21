package io.github.sweetzonzi.machine_max.common.vehicle.interact;

import cn.solarmoon.spark_core.js.molang.JSMolangValueKt;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;

@Getter
public class HitBox {
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final AbstractSubsystem subsystem;
    private boolean active = true;

    public HitBox(SubPart subPart, HitBoxAttr attr) {
        this.subPart = subPart;
        this.attr = attr;
        this.subsystem = subPart.subsystems.getOrDefault(attr.subsystem, null);
    }

    /**
     * 根据condition脚本更新激活状态
     * 应在每物理刻（prePhysicsTick）调用
     */
    public void updateActive() {
        String condition = attr.condition;
        if (condition == null || condition.isEmpty()) {
            active = true;
            return;
        }
        try {
            active = JSMolangValueKt.evalAsBoolean(condition, subPart);
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

    public float modifyDamage(DamageSource source, float amount) {
        return attr.damageModifiers().apply(source, amount);
    }

    public float getRHA(SubPart subPart) {
        return attr.thickness * attr.rha() * (subPart.isDestroyed() ? 0.5f : 1.0f);
    }

    public boolean hasAngleEffect() {
        return attr.angleEffect();
    }

    public boolean hasUnPenetrateDamage() {
        return attr.unPenetrateDamageFactor() > 0.0f;
    }

    public float getUnPenetrateDamageFactor() {
        return attr.unPenetrateDamageFactor();
    }

    /**
     * 获取碰撞箱的前向摩擦系数
     * @return 前向摩擦系数
     */
    public double getMuFront() {
        return attr.friction().y();
    }
    /**
     * 获取碰撞箱的侧向摩擦系数
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
