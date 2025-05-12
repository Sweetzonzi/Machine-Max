package io.github.tt432.machinemax.mixin_interface;

import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import net.minecraft.world.phys.HitResult;

public interface IProjectileMixin {
    void machine_Max$manualProjectileHit(HitResult hitResult);

    long machine_Max$getHitBoxId();

    void machine_Max$setHitBoxId(long index);

    Vector3f machine_Max$getHitPoint();

    void machine_Max$setHitPoint(Vector3f pos);

    Vector3f machine_Max$getHitNormal();

    void machine_Max$setHitNormal(Vector3f normal);

    SubPart machine_Max$getHitSubPart();

    void machine_Max$setHitSubPart(SubPart subPart);
}
