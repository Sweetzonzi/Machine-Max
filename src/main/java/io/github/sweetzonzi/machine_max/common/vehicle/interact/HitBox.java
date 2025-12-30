package io.github.sweetzonzi.machine_max.common.vehicle.interact;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;

@Getter
public class HitBox {
    public final String name;
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final AbstractSubsystem subsystem;

    public HitBox(SubPart subPart,HitBoxAttr attr) {
        this.subPart = subPart;
        this.name = attr.getHitBoxName();
        this.attr = attr;
        this.subsystem = subPart.subsystems.getOrDefault(attr.getSubsystem(), null);
    }

    public float modifyImpact(DamageSource source, float amount) {
        return attr.getImpactModifier().apply(source, amount);
    }

    public float modifyPiercing(DamageSource source, float amount) {
        return attr.getPiercingModifier().apply(source, amount);
    }

    public float modifyDamage(DamageSource source, float amount) {
        return attr.getDamageModifier().apply(source, amount);
    }

    public float getRHA(SubPart subPart) {
        return attr.getRHA() * (subPart.destroyed? 0.5f : 1.0f);
    }

    public boolean hasAngleEffect() {
        return attr.isAngleEffect();
    }

    public boolean hasUnPenetrateDamage() {
        return attr.getUnPenetrateDamageFactor() > 0.0f;
    }

    public float getUnPenetrateDamageFactor() {
        return attr.getUnPenetrateDamageFactor();
    }

}
