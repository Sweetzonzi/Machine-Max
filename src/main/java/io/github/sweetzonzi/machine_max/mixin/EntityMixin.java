package io.github.sweetzonzi.machine_max.mixin;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.PhysicsSweepTestResult;
import com.jme3.bullet.collision.shapes.CapsuleCollisionShape;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
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
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

@Mixin(Entity.class)
abstract public class EntityMixin extends AttachmentHolder implements IEntityMixin {
    @Shadow
    public abstract Level level();

    @Shadow
    private boolean onGround;
    @Shadow
    public boolean verticalCollision;
    @Shadow
    public boolean verticalCollisionBelow;

    @Shadow
    protected abstract void checkSupportingBlock(boolean onGround, @Nullable Vec3 movement);

    @Shadow
    public abstract void resetFallDistance();

    @Shadow
    public abstract void setDeltaMovement(Vec3 deltaMovement);

    @Unique
    private AbstractControllableSubsystem machine_Max$controllingSubSystem;
    @Unique
    private CapsuleCollisionShape machine_Max$collideTestShape = null;
    @Unique
    private final Transform machine_Max$sweepTestStart = new Transform();
    @Unique
    private final Transform machine_Max$sweepTestEnd = new Transform();
    @Unique
    private final Vec3 AXIS_YP = new Vec3(0, 1, 0);
    @Unique
    private boolean machine_Max$groundedByPhysicsBody = false;
    @Unique
    private Vec3 machine_Max$physicsGroundNormal = null;

    /**
     * 在调用 maybeBackOffFromEdge 之前修改 pos 变量
     */
    @ModifyArg(
            method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;maybeBackOffFromEdge(Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/entity/MoverType;)Lnet/minecraft/world/phys/Vec3;"
            ),
            index = 0
    )
    private Vec3 modifyMovePosBeforeEdgeBackoff(Vec3 originalPos, MoverType moverType) {
        // 获取原版碰撞结果
        if (originalPos.lengthSqr() < 1e-6f) {
            return originalPos; // 无运动时直接返回
        }
        Entity entity = (Entity) (Object) this;
        AABB aabb = entity.getBoundingBox();
        // 跳过非必要的检测对象
        if (entity instanceof BlockAttachedEntity
                || entity instanceof MMPartEntity
                || (aabb.maxX - aabb.minX) * (aabb.maxY - aabb.minY) * (aabb.maxZ - aabb.minZ) < 0.001
                || (entity instanceof Player player && !player.isAffectedByFluids()) // 飞行模式无碰撞
                || ((IEntityMixin) entity).machine_Max$getControllingSubsystem() != null) {
            return originalPos;
        }
        // 调用物理引擎进行碰撞检测
        if (machine_Max$collideTestShape == null) {
            double x = (aabb.maxX - aabb.minX) / 2;
            double z = (aabb.maxZ - aabb.minZ) / 2;
            float radius = (float) Math.sqrt(x * x + z * z);
            float height = (float) (aabb.maxY - aabb.minY - radius * 2);
            machine_Max$collideTestShape = new CapsuleCollisionShape(radius, height > 0 ? height : 0.01f);
        }
        List<PhysicsSweepTestResult> results = new ArrayList<>();
        Vec3 delta = new Vec3(originalPos.x, originalPos.y, originalPos.z);
        Vec3 center = aabb.getCenter();
        double len = delta.length();
        if (len > 1e-6 && len < 0.5)
            delta = delta.normalize();
        else if (len < 1e-6) { // 无运动直接返回
            return originalPos;
        }
        machine_Max$sweepTestStart.setTranslation(PhysicsHelperKt.toBVector3f(center));
        machine_Max$sweepTestEnd.setTranslation(PhysicsHelperKt.toBVector3f(center.add(delta)));
        entity.level().getPhysicsLevel().getWorld().sweepTest(
                machine_Max$collideTestShape,
                machine_Max$sweepTestStart,
                machine_Max$sweepTestEnd, results, 0.05f);
        if (results.isEmpty()) {
            return originalPos;// 无碰撞结果时直接返回
        }
        Vec3 normal = new Vec3(0, 1, 0);
        Vec3 movement = new Vec3(0, 0, 0);
        float hitFraction = Float.MAX_VALUE;
        HitBox hitBox = null;
        for (PhysicsSweepTestResult result : results) {
            PhysicsCollisionObject pco = result.getCollisionObject();
            int group = pco.getCollisionGroup();
            if (group == CollisionGroups.PHYSICS_BODY) {
                if (PhysicsBodyExtensionKt.getOwner(pco) instanceof SubPart subPart) {
                    if (subPart.part.getAssemblingProgress() <= 0) continue; // 未组装时不检测碰撞
                    if (result.getHitFraction() < hitFraction) {
                        normal = SparkMathKt.toVec3(result.getHitNormalLocal(null)).normalize();
                        hitFraction = result.getHitFraction();
                        movement = SparkMathKt.toVec3(MMMath.worldPointWorldVel(PhysicsHelperKt.toBVector3f(center), subPart.body));
                        hitBox = subPart.getHitBox(result.triangleIndex());
                    }
                }
            }
        }
        if (hitBox == null || hitFraction > 500 || originalPos.dot(normal) > 0) {
            return originalPos;// 运动方向与法线方向相同时或未检测到匹配的碰撞体时直接返回
        }
        if (machine_Max$sweepTestStart.getTranslation().subtract(machine_Max$sweepTestEnd.getTranslation()).lengthSquared() * hitFraction * hitFraction * 0.8 > originalPos.lengthSqr())
            return originalPos; // 扫掠命中但不足移动长度时直接返回，避免浮空
        // 计算原始向量在法线方向的投影
        Vec3 finalVec;
        double dotProduct = originalPos.dot(normal);
        Vec3 normalComponent = normal.scale(dotProduct);
        // 减去法线方向投影，得到垂直法线方向的向量
        finalVec = originalPos.subtract(normalComponent);
        // 计算与水平方向的夹角
        double angle = Math.acos(Math.clamp(normal.normalize().dot(AXIS_YP), -1, 1)) * 180 / Math.PI;
        if (angle < 45.0) { // 爬坡角度小于45°时
            this.machine_Max$groundedByPhysicsBody = true;
            this.machine_Max$physicsGroundNormal = normal;
            if (originalPos.horizontalDistanceSqr() > 1e-6f) {
                //水平方向有运动时，令水平方向速度保持原输入
                double originalLen = originalPos.horizontalDistance();
                double finalLen = finalVec.horizontalDistance();
                finalVec = finalVec.scale(originalLen / finalLen);
            } else {
                //水平方向无运动时，保持静止
                finalVec = new Vec3(0, 0, 0);
            }
        }
        // 叠加刚体的运动
        movement = movement.scale(0.05);//速度转为单tick移动量
        Vec3 deltaWithPart = finalVec.subtract(movement);
//        finalVec = finalVec.subtract(deltaWithPart.scale(machine_Max$groundedByPhysicsBody ? 0.2 : 0.1)); // 摩擦使得双方接近同速
        MachineMax.LOGGER.debug("angle:{}", angle);
        entity.setDeltaMovement(finalVec); // 修改速度，否则速度会无限积累
        return finalVec;
    }


    /**
     * <p>补充 setOnGroundWithMovement 的地面语义判定，使物理刚体斜面能够被识别为“地面”</p>
     * <p>Supplement ground semantics for physics bodies, allowing entities to be considered on-ground
     * when standing on physics-driven surfaces.</p>
     */
    @Inject(
            method = "setOnGroundWithMovement",
            at = @At("HEAD"),
            cancellable = true
    )
    private void machine_Max$setOnGroundWithMovementByPhysics(
            boolean onGround,
            Vec3 movement,
            CallbackInfo ci
    ) {
        Entity entity = (Entity) (Object) this;

        // === 原版已经判定为地面时，完全交由原逻辑处理 ===
        if (onGround) {
            return;
        }

        // 跳过非必要的检测对象
        if (entity instanceof BlockAttachedEntity
                || entity instanceof MMPartEntity
                || (entity instanceof Player player && !player.isAffectedByFluids()) // 飞行模式无碰撞
                || ((IEntityMixin) entity).machine_Max$getControllingSubsystem() != null) {
            return;
        }

        // === 本 tick 未记录物理刚体地面接触，直接回退原版 ===
        if (!this.machine_Max$groundedByPhysicsBody || this.machine_Max$physicsGroundNormal == null) {
            return;
        }

        /*
         * - 即使几何解算中未产生 verticalCollisionBelow
         * - 只要 collide 阶段确认是“可站立斜面”
         * - 即补充 onGround = true
         */
        this.onGround = true;
        this.verticalCollision = true;
        this.verticalCollisionBelow = true;

        // 调用原版支撑方块检测逻辑
        this.checkSupportingBlock(true, movement);

        // 接触物理地面时，视为安全着地，重置跌落距离
        this.resetFallDistance();

        // === 本 tick 状态用完即清 ===
        this.machine_Max$groundedByPhysicsBody = false;
        this.machine_Max$physicsGroundNormal = null;

        // 阻止原版逻辑再次覆盖 onGround
        ci.cancel();
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
