package io.github.sweetzonzi.machinemax.common.vehicle;

import io.github.sweetzonzi.machinemax.common.vehicle.attr.HitBoxAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import lombok.Getter;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Getter
public class HitBox {
    public final String name;
    public final SubPart subPart;
    public final HitBoxAttr attr;
    public final ConcurrentMap<String, AbstractSubsystem> subsystems = new ConcurrentHashMap<>();

    public HitBox(SubPart subPart,HitBoxAttr attr) {
        this.subPart = subPart;
        this.name = attr.hitBoxName();
        this.attr = attr;
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

    public float getRHA(Part part) {
        return attr.RHA() * (part.destroyed? 0.5f : 1.0f);
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
