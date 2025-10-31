package io.github.sweetzonzi.machine_max.common.vehicle.interact;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;

@Getter
public class HitBox {
    public final String name;
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final AbstractSubsystem subsystem;

    public HitBox(SubPart subPart,HitBoxAttr attr) {
        this.subPart = subPart;
        this.name = attr.hitBoxName();
        this.attr = attr;
        this.subsystem = subPart.subsystems.getOrDefault(attr.subsystem(), null);
    }

    public float getDamageReduction() {
        return attr.damageReduction();
    }

    public float getCollisionDamageReduction() {
        return attr.collisionDamageReduction();
    }

    public float getDamageMultiplier() {
        return attr.damageMultiplier();
    }

    public float getRHA(SubPart subPart) {
        return attr.RHA() * (subPart.part.destroyed? 0.5f : 1.0f);
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

}
