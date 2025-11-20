package io.github.sweetzonzi.machine_max.mixin;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsSweepTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
abstract public class EntityMixin extends AttachmentHolder implements IEntityMixin {
    @Shadow public abstract Level level();

    @Unique
    private AbstractControllableSubsystem machine_Max$controllingSubSystem;
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
                || (entity instanceof Player player && !player.isAffectedByFluids())
                || ((IEntityMixin) entity).machine_Max$getControllingSubsystem() != null) return;
        // 调用物理引擎进行碰撞检测
        if (machine_Max$collideTestShape == null) {
            double x = (aabb.maxX - aabb.minX) / 2;
            double z = (aabb.maxZ - aabb.minZ) / 2;
            float radius = (float) Math.sqrt(x * x + z * z) * 0.7f;
            float height = (float) (aabb.maxY - aabb.minY - radius * 2);
            machine_Max$collideTestShape = new CapsuleCollisionShape(radius, height > 0 ? height : 0.01f);
        }
        List<PhysicsSweepTestResult> results = new ArrayList<>();
        Vec3 delta = new Vec3(originalVec.x, originalVec.y, originalVec.z);
        Vec3 center = aabb.getCenter();
        if (delta.length() < 0.5f) delta = delta.normalize().scale(0.5f);
        if(delta.length() < 0.498f) {
            //TODO:有时扫掠测试会报错，检查原因 会是因为delta某些情况下等于0吗？
            return; // 无运动时直接返回，也许能修复此问题
        }
        machine_Max$sweepTestStart.setTranslation(PhysicsHelperKt.toBVector3f(center));
        machine_Max$sweepTestEnd.setTranslation(PhysicsHelperKt.toBVector3f(center.add(delta)));
        entity.level().getPhysicsLevel().getWorld().sweepTest(
                machine_Max$collideTestShape,
                machine_Max$sweepTestStart,
                machine_Max$sweepTestEnd, results, 0.1f);
        if (results.isEmpty()) return;// 无碰撞结果时直接返回
        Vec3 normal = new Vec3(0, 1, 0);
        Vec3 movement = new Vec3(0, 0, 0);
        float hitFraction = Float.MAX_VALUE;
        for (PhysicsSweepTestResult result : results) {
            PhysicsCollisionObject pco = result.getCollisionObject();
            int group = pco.getCollisionGroup();
            if (group == CollisionGroups.PHYSICS_BODY) {
                if (PhysicsBodyExtensionKt.getOwner(pco) instanceof SubPart subPart) {
                    if (result.getHitFraction() < hitFraction) {
                        normal = SparkMathKt.toVec3(result.getHitNormalLocal(null).normalize());
                        hitFraction = result.getHitFraction();
                        movement = SparkMathKt.toVec3(MMMath.worldPointWorldVel(PhysicsHelperKt.toBVector3f(center), subPart.body));
                    }
                }
            }
        }
        if (hitFraction > 1) return;// 无碰撞结果时直接返回
        if (originalVec.dot(normal) > 0) return;// 运动方向与法线方向相同时不会碰撞，直接返回
        // 计算原始向量在法线方向的投影
        Vec3 finalVec;
        double dotProduct = originalVec.dot(normal);
        Vec3 normalComponent = normal.scale(dotProduct);
        // 减去法线方向投影，得到垂直法线方向的向量
        finalVec = originalVec.subtract(normalComponent);
        // 计算运动方向与水平方向的夹角
        float angle = (float) Math.acos(Math.clamp(finalVec.normalize().dot(new Vec3(finalVec.x, 0, finalVec.z).normalize()), -1, 1));
//        if (entity instanceof Player)
//            MachineMax.LOGGER.debug("angle: {}, normal: {}, originalVec: {} ,finalVec: {}", angle, normal, originalVec, finalVec);
        if (angle * 180 / (float) Math.PI < 45f) {
            //TODO: 配置文件控制是否全量碰撞或水平方向无碰撞
            boolean noHorizontalCollision = false;
            if (noHorizontalCollision || normal.dot(new Vec3(0, 1, 0)) > 0.7071f) {
                //爬坡角度小于45°时
                if (originalVec.horizontalDistanceSqr() > 0.0001f)
                    //水平方向有运动时，取原始向量的长度，方便爬坡
                    finalVec = new Vec3(originalVec.x, finalVec.horizontalDistance() * (float) Math.tan(angle), originalVec.z);
                else
                    //水平方向无运动时，保持静止
                    finalVec = new Vec3(0, 0, 0);
            }
        }
        // 叠加刚体的运动
        movement = movement.scale(0.05);//速度转为单tick移动量
        finalVec = finalVec.add(movement.x, movement.y > 0 ? movement.y : 0, movement.z);
        // 若存在方块碰撞导致的向量变化，则返回合并后的向量
        if (!originalVec.equals(vec)) {
            double x = finalVec.x * originalVec.x < 0 ? 0 :
                    finalVec.x > 0 ? Math.min(finalVec.x, originalVec.x) : Math.max(finalVec.x, originalVec.x);
            double y = finalVec.y * originalVec.y < 0 ? 0 :
                    finalVec.y > 0 ? Math.min(finalVec.y, originalVec.y) : Math.max(finalVec.y, originalVec.y);
            double z = finalVec.z * originalVec.z < 0 ? 0 :
                    finalVec.z > 0 ? Math.min(finalVec.z, originalVec.z) : Math.max(finalVec.z, originalVec.z);
            finalVec = new Vec3(x, y, z);
        }
        cir.setReturnValue(finalVec);
    }

    @Nullable
    @Override
    public AbstractControllableSubsystem machine_Max$getControllingSubsystem() {
        return machine_Max$controllingSubSystem;
    }

    @Override
    public void machine_Max$setControllingSubsystem(AbstractControllableSubsystem subSystem) {
        this.machine_Max$controllingSubSystem = subSystem;
    }

}
