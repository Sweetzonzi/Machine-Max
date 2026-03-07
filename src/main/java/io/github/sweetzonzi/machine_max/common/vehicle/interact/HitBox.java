package io.github.sweetzonzi.machine_max.common.vehicle.interact;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;

@Getter
public class HitBox {
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final AbstractSubsystem subsystem;

    public HitBox(SubPart subPart, HitBoxAttr attr) {
        this.subPart = subPart;
        this.attr = attr;
        this.subsystem = subPart.subsystems.getOrDefault(attr.subsystem(), null);
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
        return attr.RHA() * (subPart.isDestroyed() ? 0.5f : 1.0f);
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

}
