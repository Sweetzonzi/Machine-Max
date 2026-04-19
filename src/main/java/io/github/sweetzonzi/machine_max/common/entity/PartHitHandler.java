package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.mixin_interface.IProjectileMixin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.List;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class PartHitHandler {
    @SubscribeEvent
    private static void onProjectileHit(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof MMPartEntity subPartEntity) {
            Projectile projectile = event.getProjectile();
            IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
            var start = PhysicsHelperKt.toBVector3f(projectile.getPosition(1).subtract(projectile.getDeltaMovement().scale(1.5)).subtract(projectile.getDeltaMovement().normalize().scale(projectile.getBoundingBox().getSize())));
            var end = PhysicsHelperKt.toBVector3f(projectile.getPosition(1).add(projectile.getDeltaMovement().scale(1.5).add(projectile.getDeltaMovement().normalize().scale(projectile.getBoundingBox().getSize()))));
            var results = SparkLevel.getPhysicsLevel(projectile.level()).getWorld().getWorldSnapshot().rayTest(start, end);
            for (PhysicsRayTestResult result : results) {//遍历射线检测结果
                if (PhysicsBodyExtensionKt.getOwner(result.getCollisionObject()) instanceof SubPart candidatedSubPart) {
                    if(candidatedSubPart.isWheel(result.triangleIndex()) && candidatedSubPart.isWheelSurface(result.triangleIndex())) continue; // 跳过轮子的球面部分
                    var hitBox = candidatedSubPart.getHitBox(result.triangleIndex());
                    if (!hitBox.isActive()) continue; // 跳过未激活的碰撞箱
                    if (subPartEntity.subPart != null && candidatedSubPart == subPartEntity.subPart) {//若命中的是本零件
                        mixinProjectile.machine_Max$setHitPoint(start.add(end.subtract(start).mult(result.getHitFraction())));
                        mixinProjectile.machine_Max$setHitNormal(result.getHitNormalLocal(null));
                        mixinProjectile.machine_Max$setHitBox(hitBox);
                        mixinProjectile.machine_Max$setHitSubPart(candidatedSubPart);
                    } else {
                        // 第一个命中部件刚体与部件实体不匹配，说明投射物被挡住，取消事件
                        event.setCanceled(true);
//                        MachineMax.LOGGER.debug("Projectile hit a sub part but not the same sub part, cancel event");
                    }
                    return;
                }
            }
            // 射线检测结果为空，说明没有命中任何部件，取消事件
            event.setCanceled(true);
//            MachineMax.LOGGER.debug("Projectile hit nothing, cancel event");
        }
    }

    @SubscribeEvent
    public static void onProjectileTick(EntityTickEvent.Pre event) {
        if (event.getEntity() instanceof Projectile projectile) {
            if (projectile.isRemoved() || projectile.getDeltaMovement().lengthSqr() < 0.16) return;
            // 上一 tick 位置
            Vec3 prevPos = projectile.position().subtract(projectile.getDeltaMovement());
            // 当前 tick 位置
            Vec3 currentPos = projectile.position();

            Vector3f start = PhysicsHelperKt.toBVector3f(prevPos);
            Vector3f end = PhysicsHelperKt.toBVector3f(currentPos);

            var physicsLevel = SparkLevel.getPhysicsLevel(projectile.level());
            var snapshot = physicsLevel.getWorld().getWorldSnapshot();

            List<PhysicsRayTestResult> results = snapshot.rayTest(start, end);

            if (results.isEmpty()) return;

            SubPart hitSubPart = null;
            PhysicsRayTestResult hitResult = null;

            for (PhysicsRayTestResult result : results) {
                if (!(PhysicsBodyExtensionKt.getOwner(result.getCollisionObject()) instanceof SubPart subPart))
                    continue;
                if (subPart.isWheel(result.triangleIndex()) && subPart.isWheelSurface(result.triangleIndex()))
                    continue;
                var hitBox = subPart.getHitBox(result.triangleIndex());
                if (!hitBox.isActive()) continue; // 跳过未激活的碰撞箱
                hitSubPart = subPart;
                hitResult = result;
                break;
            }

            if (hitSubPart == null) return;

            Vec3 hitPos = new Vec3(
                    start.x + (end.x - start.x) * hitResult.getHitFraction(),
                    start.y + (end.y - start.y) * hitResult.getHitFraction(),
                    start.z + (end.z - start.z) * hitResult.getHitFraction()
            );

            MMPartEntity partEntity = hitSubPart.getEntity();

            if (partEntity == null) return;

            HitResult vallinaHitResult = new EntityHitResult(
                    partEntity,
                    hitPos
            );

            IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;

            mixinProjectile.machine_Max$setHitPoint(start.add(end.subtract(start).mult(hitResult.getHitFraction())));
            mixinProjectile.machine_Max$setHitNormal(hitResult.getHitNormalLocal(null));
            mixinProjectile.machine_Max$setHitBox(hitSubPart.getHitBox(hitResult.triangleIndex()));
            mixinProjectile.machine_Max$setHitSubPart(hitSubPart);

            mixinProjectile.machine_Max$manualProjectileHit(vallinaHitResult);
        }
    }
}
