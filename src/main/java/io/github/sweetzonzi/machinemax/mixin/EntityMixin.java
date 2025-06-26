package io.github.sweetzonzi.machinemax.mixin;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsSweepTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleManager;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
abstract public class EntityMixin extends AttachmentHolder implements IEntityMixin {
    @Unique
    private SeatSubsystem machine_Max$subSystem;
    @Unique
    private CapsuleCollisionShape machine_Max$collideTestShape = null;
    @Unique
    private Transform machine_Max$sweepTestStart = new Transform();
    @Unique
    private Transform machine_Max$sweepTestEnd = new Transform();

    /**
     * <p>额外处理原版碰撞检测逻辑，使实体能够与物理体发生碰撞</p>
     * <p>Handle the original collision detection logic, allowing entities to collide with physical bodies.</p>
     */
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void onCollide(Vec3 vec, CallbackInfoReturnable<Vec3> cir) {
        // 获取原版碰撞结果
        Vec3 originalVec = cir.getReturnValue();
        Entity entity = (Entity) (Object) this;
        AABB aabb = entity.getBoundingBox();
        // 跳过非必要的检测对象
        if (entity instanceof BlockAttachedEntity
                || entity instanceof MMPartEntity
                || (aabb.maxX - aabb.minX) * (aabb.maxY - aabb.minY) * (aabb.maxZ - aabb.minZ) < 0.001
                || ((IEntityMixin) entity).machine_Max$getRidingSubsystem() != null) return;
        // 调用物理引擎进行碰撞检测
        if (machine_Max$collideTestShape == null) {
            double x = (aabb.maxX - aabb.minX) / 2;
            double z = (aabb.maxZ - aabb.minZ) / 2;
            float radius = (float) Math.sqrt(x * x + z * z);
            float height = (float) (aabb.maxY - aabb.minY - radius * 2);
            machine_Max$collideTestShape = new CapsuleCollisionShape(radius, height > 0 ? height : 0.01f);
        }
        List<PhysicsSweepTestResult> results = new ArrayList<>();
        Vec3 delta = new Vec3(vec.x, vec.y, vec.z);
        Vec3 center = aabb.getCenter();
        if (delta.length() < 0.5f) delta = delta.normalize().scale(0.5f);
        machine_Max$sweepTestStart.setTranslation(PhysicsHelperKt.toBVector3f(center));
        machine_Max$sweepTestEnd.setTranslation(PhysicsHelperKt.toBVector3f(center.add(delta)));
        entity.level().getPhysicsLevel().getWorld().sweepTest(
                machine_Max$collideTestShape,
                machine_Max$sweepTestStart,
                machine_Max$sweepTestEnd, results, 0.1f);
        if (results.isEmpty()) return;// 无碰撞结果时直接返回
        Vec3 normal = new Vec3(0, 1, 0);
        float hitFraction = Float.MAX_VALUE;
        for (PhysicsSweepTestResult result : results) {
            PhysicsCollisionObject pco = result.getCollisionObject();
            int group = pco.getCollisionGroup();
            if (group == VehicleManager.COLLISION_GROUP_PART) {
                if (pco.getOwner() instanceof SubPart) {
                    if (result.getHitFraction() < hitFraction) {
                        normal = SparkMathKt.toVec3(result.getHitNormalLocal(null).normalize());
                        hitFraction = result.getHitFraction();
                    }
                }
            }
        }
        if (hitFraction > 1) return;// 无碰撞结果时直接返回
        // 计算原始向量在法线方向的投影
        Vec3 finalVec;
        double dotProduct = vec.dot(normal);
        Vec3 normalComponent = normal.scale(dotProduct);
        // 减去法线方向投影，得到垂直法线方向的向量
        finalVec = vec.subtract(normalComponent);
        if (vec.normalize().dot(normal) > -0.5f) {
            //原始向量与法线夹角小于30°时，取原始向量的长度，方便爬坡
            finalVec = finalVec.normalize().scale(vec.length());
        }
        // 返回合并后的向量
        cir.setReturnValue(finalVec);
    }

    @Nullable
    @Override
    public SeatSubsystem machine_Max$getRidingSubsystem() {
        return machine_Max$subSystem;
    }

    @Override
    public void machine_Max$setRidingSubsystem(SeatSubsystem subSystem) {
        this.machine_Max$subSystem = subSystem;
    }

}
