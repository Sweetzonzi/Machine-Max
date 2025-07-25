package io.github.sweetzonzi.machinemax.mixin;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.vehicle.HitBox;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import io.github.sweetzonzi.machinemax.mixin_interface.IProjectileMixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Projectile.class)
abstract public class ProjectileMixin extends Entity implements IProjectileMixin {
    @Unique
    private HitBox machine_Max$hitBox = null;
    @Unique
    private Vector3f machine_Max$normal = null;
    @Unique
    private Vector3f machine_Max$hitPoint = null;
    @Unique
    private SubPart machine_Max$hitSubPart = null;

    @Shadow
    abstract protected void onHit(HitResult hitResult);

    public ProjectileMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Unique
    public void machine_Max$manualProjectileHit(HitResult hitResult) {
        onHit(hitResult);
    }

    public HitBox machine_Max$getHitBox() {
        return machine_Max$hitBox;
    }

    public void machine_Max$setHitBox(HitBox hitBox) {
        machine_Max$hitBox = hitBox;
    }

    public Vector3f machine_Max$getHitPoint() {
        return machine_Max$hitPoint;
    }

    public void machine_Max$setHitPoint(Vector3f pos) {
        machine_Max$hitPoint = pos;
    }

    public Vector3f machine_Max$getHitNormal() {
        return machine_Max$normal;
    }

    public void machine_Max$setHitNormal(Vector3f normal) {
        machine_Max$normal = normal;
    }

    public SubPart machine_Max$getHitSubPart() {
        return machine_Max$hitSubPart;
    }

    public void machine_Max$setHitSubPart(SubPart subPart) {
        machine_Max$hitSubPart = subPart;
    }
}
