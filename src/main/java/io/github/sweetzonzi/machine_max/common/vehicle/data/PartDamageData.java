package io.github.sweetzonzi.machine_max.common.vehicle.data;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.vehicle.IPhysicsProjectile;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import net.minecraft.world.damagesource.DamageSource;

public record PartDamageData(
        DamageSource source,
        IPhysicsProjectile projectileSource,
        Vector3f normal,
        Vector3f worldContactSpeed,
        Vector3f worldContactPoint,
        HitBox hitBox
) {
}