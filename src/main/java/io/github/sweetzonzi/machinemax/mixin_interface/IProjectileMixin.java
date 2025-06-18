package io.github.sweetzonzi.machinemax.mixin_interface;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.vehicle.HitBox;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import net.minecraft.world.phys.HitResult;

public interface IProjectileMixin {
    void machine_Max$manualProjectileHit(HitResult hitResult);

    HitBox machine_Max$getHitBox();

    void machine_Max$setHitBox(HitBox hitBox);

    Vector3f machine_Max$getHitPoint();

    void machine_Max$setHitPoint(Vector3f pos);

    Vector3f machine_Max$getHitNormal();

    void machine_Max$setHitNormal(Vector3f normal);

    SubPart machine_Max$getHitSubPart();

    void machine_Max$setHitSubPart(SubPart subPart);
}
