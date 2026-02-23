package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.mixin_interface.IProjectileMixin;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class PartHitHandler {
    @SubscribeEvent
    private static void onProjectileHit(ProjectileImpactEvent event) {
        if (event.getRayTraceResult() instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof MMPartEntity subPartEntity) {
            Projectile projectile = event.getProjectile();
            IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
            var start = PhysicsHelperKt.toBVector3f(projectile.getPosition(1).subtract(projectile.getDeltaMovement().scale(1.5)).subtract(projectile.getDeltaMovement().normalize().scale(projectile.getBoundingBox().getSize())));
            var end = PhysicsHelperKt.toBVector3f(projectile.getPosition(1).add(projectile.getDeltaMovement().scale(1.5).add(projectile.getDeltaMovement().normalize().scale(projectile.getBoundingBox().getSize()))));
            var results = SparkLevel.getPhysicsLevel(projectile.level()).getWorld().rayTest(start, end);
            for (PhysicsRayTestResult result : results) {//遍历射线检测结果
                if (PhysicsBodyExtensionKt.getOwner(result.getCollisionObject()) instanceof SubPart candidatedSubPart) {
                    if(candidatedSubPart.isWheel(result.triangleIndex()) && candidatedSubPart.isWheelSurface(result.triangleIndex())) continue; // 跳过轮子的球面部分
                    if (subPartEntity.subPart != null && candidatedSubPart == subPartEntity.subPart) {//若命中的是本零件
                        mixinProjectile.machine_Max$setHitPoint(start.add(end.subtract(start).mult(result.getHitFraction())));
                        mixinProjectile.machine_Max$setHitNormal(result.getHitNormalLocal(null));
                        mixinProjectile.machine_Max$setHitBox(subPartEntity.subPart.getHitBox(result.triangleIndex()));
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
}
