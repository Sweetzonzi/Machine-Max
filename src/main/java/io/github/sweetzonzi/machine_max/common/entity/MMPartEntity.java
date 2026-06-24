package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.EntityPatch;
import cn.solarmoon.spark_core.animation.IEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.BlackBoard;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.mixin_interface.IProjectileMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class MMPartEntity extends VehicleEntity implements IEntityAnimatable<MMPartEntity>, IEntityWithComplexSpawn, EntityPatch {

    public SubPart subPart;//实体所属的零件
    public UUID vehicleUUID;
    public UUID partUUID;
    public String subPartName;
    public AtomicReference<BoundingBox> boundingBox = new AtomicReference<>();
    public AtomicReference<Vector3f> bodyCenter = new AtomicReference<>();
    private PhysicsGhostObject testGhost;
    @Getter
    private final Map<String, Object> variables = HashMap.newHashMap(1);
    /**
     * 缓存1倍尺寸的AABB，供 getBoundingBoxForCulling 使用，避免每帧创建
     */
    private AABB cachedCullingAabb;

    /**
     * 不应被使用！
     *
     * @param entityType 实体类型
     * @param level      实体加入的世界
     */
    public MMPartEntity(EntityType<? extends Entity> entityType, Level level) {
        super(entityType, level);
        this.blocksBuilding = false;
    }

    public MMPartEntity(Level level, SubPart subPart) {
        this(MMEntities.PART_ENTITY.get(), level);
        this.setNoGravity(true);
        this.subPart = subPart;
        this.subPartName = subPart.name;
        this.partUUID = subPart.part.uuid;
        this.vehicleUUID = subPart.part.assembly.getAssemblyId();
        this.setPos(SparkMathKt.toVec3(subPart.getPosition()));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.@NotNull Builder builder) {
        super.defineSynchedData(builder);
    }

    @Override
    protected @NotNull Item getDropItem() {
        return ItemStack.EMPTY.getItem();
    }

    @Override
    public void tick() {
        if (tickCount == 2) {//移除SparkCore为实体添加的默认碰撞箱刚体
            var body = getPhysicsBody("body");
            if (body != null) {
                PhysicsBodyExtensionKt.removePhysicsBody(level(), body);
            }
        }
        super.tick();
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (this.subPart == null) {//如果实体没有所属的部件，则移除实体
            if (!this.isRemoved()) {
                if (tickCount % 20 == 0) updatePart();
                else if (tickCount > 100) {//等待100tick用于同步部件信息
                    MachineMax.LOGGER.warn("部件实体没有匹配的部件，已移除!");
                    this.remove(RemovalReason.DISCARDED);
                }
            }
        } else {
            //更新实体位置
            this.setPos(SparkMathKt.toVec3(subPart.getPosition()));
            Quaternionf q = SparkMathKt.toQuaternionf(subPart.getRotation());
            // 从四元数提取前向向量
            org.joml.Vector3f forward = new org.joml.Vector3f(0, 0, 1).rotate(q);
            // 计算yaw和pitch
            float yaw = -(float) Math.toDegrees(Math.atan2(forward.x, forward.z)) + 180;
            float pitch = (float) Math.toDegrees(Math.asin(forward.y));
            yaw = yaw % 360;
            if (yaw > 180) yaw -= 360;
            else if (yaw < -180) yaw += 360;
            // 设置实体旋转
            this.setRot(yaw, pitch);
            // 更新实体速度
            var vel = subPart.getLinearVelocity().mult(0.05f);
            this.setDeltaMovement(vel.x, vel.y, vel.z);
        }
    }

    @Override
    public boolean isAlwaysTicking() {
        return subPart != null && !subPart.isRemoved;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (this.subPart == null) return false;
        // === 投射物伤害：由 IProjectileMixin 提供精确的命中几何数据 ===
        if (source.getDirectEntity() instanceof Projectile projectile) {
            IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
            SubPart hitSubPart = mixinProjectile.machine_Max$getHitSubPart();
            if (hitSubPart == this.subPart) {//如果命中了部件
                Vector3f normal = mixinProjectile.machine_Max$getHitNormal();
                Vector3f contactPoint = mixinProjectile.machine_Max$getHitPoint();
                HitBox hitBox = mixinProjectile.machine_Max$getHitBox();
                // 计算相对速度（投射物速度 - 部件速度），用于角度修正和击退
                Vector3f relVel = PhysicsHelperKt.toBVector3f(projectile.getDeltaMovement().scale(20));
                relVel.subtractLocal(hitSubPart.body.getLinearVelocity(null));
                // 尝试取回外部 TB 协议上下文，有则补充缺失的 HIT_BOX，无则自己构造
                BFDamageContext ctx = BFDamageApi.getContextFor(this);
                if (ctx != null) {
                    // 外部 TB 伤害缺少碰撞箱信息，用我们自己的精确检测补上
                    if (ctx.extensions().get(MMDamageExtensions.HIT_BOX) == null) {
                        ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
                    }
                } else {
                    ctx = BFDamageContext.builder()
                            .source(source)
                            .baseDamage(amount)
                            .hitVelocity(SparkMathKt.toVec3(relVel))
                            .hitPoint(SparkMathKt.toVec3(contactPoint))
                            .hitNormal(SparkMathKt.toVec3(normal))
                            .penetration(angleCorrectedPenetration(relVel, normal, amount))
                            .build();
                    ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
                }
                return BFDamageApi.hurt(hitSubPart, ctx) > 0;
            } else return false;
        // === 有来源位置的实体/爆炸伤害：通过射线检测找到部件 ===
        } else if (source.getSourcePosition() != null && source.getDirectEntity() instanceof Entity entity) {
            PhysicsLevel physicsLevel = getPhysicsLevel();
            Vector3f start;
            Vector3f end;
            if (entity instanceof LivingEntity livingEntity && livingEntity.isAlive() && !livingEntity.isRemoved()) {
                start = PhysicsHelperKt.toBVector3f(livingEntity.getEyePosition());
                end = PhysicsHelperKt.toBVector3f(
                                livingEntity.getViewVector(1).normalize()
                                        .scale(5))
                        .add(start);
            } else {
                start = PhysicsHelperKt.toBVector3f(source.getSourcePosition());
                end = PhysicsHelperKt.toBVector3f(this.position());
            }
            if (end.subtract(start).length() > 0) {
                if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION)) {
                    // 爆炸/范围伤害：直接找最近的部件和最厚的装甲
                    SubPart nearest = null;
                    float nearestDistance = Float.MAX_VALUE;
                    Vector3f normal = new Vector3f();
                    Vector3f contactPoint = new Vector3f();
                    Vector3f delta = PhysicsBodyExtensionKt.stateOf(subPart.body).getTransform().getTranslation().subtract(start);
                    float d = delta.length();
                    if (d < nearestDistance) {
                        nearest = subPart;
                        contactPoint = PhysicsBodyExtensionKt.stateOf(subPart.body).getTransform().getTranslation();
                        normal = delta.multLocal(-1).normalize();
                    }
                    if (nearest != null) {
                        // 爆炸没有精确的碰撞箱，用最厚装甲作为命中部位
                        BFDamageContext ctx = BFDamageApi.getContextFor(this);
                        HitBox hitBox = subPart.findStrongestHitBox();
                        if (ctx != null) {
                            if (ctx.extensions().get(MMDamageExtensions.HIT_BOX) == null) {
                                ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
                            }
                        } else {
                            ctx = BFDamageContext.builder()
                                    .source(source)
                                    .baseDamage(amount)
                                    .hitVelocity(SparkMathKt.toVec3(normal.mult(-1)))
                                    .hitPoint(SparkMathKt.toVec3(contactPoint))
                                    .hitNormal(SparkMathKt.toVec3(normal))
                                    .penetration(amount)
                                    .build();
                            ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
                        }
                        return BFDamageApi.hurt(nearest, ctx) > 0;
                    } else throw new IllegalStateException("No subpart found for explosion damage.");
                // === 一般实体攻击/近战：射线检测精确命中 ===
                } else {
                    var results = physicsLevel.getWorld().getWorldSnapshot().rayTest(start, end);
                    for (var result : results) {
                        PhysicsRigidBody body = (PhysicsRigidBody) result.getCollisionObject();
                        if (PhysicsBodyExtensionKt.getOwner(body) instanceof SubPart someSubPart) {
                            // 跳过轮胎轮面
                            if (someSubPart.isWheel(result.triangleIndex()) && someSubPart.isWheelSurface(result.triangleIndex()))
                                continue;
                            HitBox hitBox = someSubPart.getHitBox(result.triangleIndex());
                            // 跳过未激活的碰撞箱
                            if (!hitBox.isActive()) continue;
                            Vector3f normal = result.getHitNormalLocal(null);
                            Vector3f contactPoint = start.add(end.subtract(start).mult(result.getHitFraction()));
                            // 射线检测已有精确的命中几何，同样先检查外部上下文
                            BFDamageContext ctx = BFDamageApi.getContextFor(this);
                            if (ctx != null) {
                                if (ctx.extensions().get(MMDamageExtensions.HIT_BOX) == null) {
                                    ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
                                }
                            } else {
                                Vector3f direction = end.subtract(start).normalize();
                                ctx = BFDamageContext.builder()
                                        .source(source)
                                        .baseDamage(amount)
                                        .hitVelocity(SparkMathKt.toVec3(direction))
                                        .hitPoint(SparkMathKt.toVec3(contactPoint))
                                        .hitNormal(SparkMathKt.toVec3(normal))
                                        .penetration(angleCorrectedPenetration(direction, normal, amount))
                                        .build();
                                ctx.extensions().set(MMDamageExtensions.HIT_BOX, hitBox);
                            }
                            return BFDamageApi.hurt(someSubPart, ctx) > 0;
                        }
                    }
                }
            } else {//射线长度有问题时的异常处理
                MachineMax.LOGGER.error("伤害来源 {} 距离实体过近，导致射线长度为0。", entity);
            }
            return false;//未能命中任何部件碰撞箱则不处理伤害
        } else return hurtWithoutRayTest(source, amount);
    }

    /**
     * 计算经过入射角效应修正后的穿深值。
     * <p>
     * 公式为 {@code penetration = amount * cosθ}，其中 θ 为速度方向与面法线的夹角。
     * 60° 入射时穿深为伤害的 50%；掠射时钳制到 {@code amount * 0.01f}。
     *
     * @param vel    命中速度方向矢量
     * @param normal 命中面法线
     * @param amount 原始伤害量
     * @return 角度修正后的穿深
     */
    private static float angleCorrectedPenetration(Vector3f vel, Vector3f normal, float amount) {
        float velLen = vel.length();
        float normalLen = normal.length();
        if (velLen < 1e-6f || normalLen < 1e-6f) return amount;
        float cosTheta = Math.abs(vel.dot(normal)) / (velLen * normalLen);
        return amount * Math.max(cosTheta, 0.01f);
    }

    /**
     * 无来源位置的伤害的处理（如/kill、虚空伤害、魔法伤害等）。
     * 先检查是否在外部 TB 协议管线内，若是则直接转发；
     * 否则委托 SubPart 生成基础上下文走协议管线。
     */
    public boolean hurtWithoutRayTest(@NotNull DamageSource source, float amount) {
        if (this.subPart == null) return false;
        BFDamageContext ctx = BFDamageApi.getContextFor(this);
        if (ctx == null) {
            ctx = this.subPart.createContextFromVanilla(source, amount);
            if (ctx == null) return false;
        }
        return BFDamageApi.hurt(this.subPart, ctx) > 0;
    }

    @Override
    public @NotNull Component getDisplayName() {
        if (subPart != null)
            return Component.translatable(subPart.part.name);
        return super.getDisplayName();
    }

    @Override
    public @NotNull Component getName() {
        if (subPart != null)
            return Component.translatable(subPart.part.name);
        return super.getName();
    }

    @Override
    public @NotNull AABB makeBoundingBox() {
        if (subPart != null && boundingBox != null) {
            BoundingBox bb = boundingBox.get();
            Vector3f bodyCenter = this.bodyCenter.get();
            if (bb != null && bodyCenter != null) {
                Vector3f boundingBoxCenter = bb.getCenter(null);
                Vec3 offset = SparkMathKt.toVec3(boundingBoxCenter.subtract(bodyCenter));
                Vec3 center = position().add(offset);

                // 缓存1倍尺寸的AABB用于渲染剔除，避免 getBoundingBoxForCulling 每帧创建
                double ex = bb.getXExtent();
                double ey = bb.getYExtent();
                double ez = bb.getZExtent();
                cachedCullingAabb = new AABB(
                        center.x - ex, center.y - ey, center.z - ez,
                        center.x + ex, center.y + ey, center.z + ez
                );

                // 交互碰撞箱使用0.7倍缩小，减少阻挡方块挖掘
                double scale = 0.7;
                return new AABB(
                        center.x - ex * scale, center.y - ey * scale, center.z - ez * scale,
                        center.x + ex * scale, center.y + ey * scale, center.z + ez * scale
                );
            }
        }
        return super.makeBoundingBox();
    }

    @Override
    protected void addPassenger(Entity passenger) {
        super.addPassenger(passenger);
        if (subPart != null) {
            Vector3f relPos = MMMath.worldPointLocalPos(PhysicsHelperKt.toBVector3f(passenger.position()), subPart.body);
        }
    }

    @Override
    protected @NotNull Vec3 getPassengerAttachmentPoint(@NotNull Entity entity, @NotNull EntityDimensions dimensions, float partialTick) {
        var subsystem = ((IEntityMixin) entity).machine_Max$getControllingSubsystem();
        if (entity instanceof LivingEntity && subsystem instanceof SeatSubsystem seat) {
            Vector3f rawRelPos = seat.getSeatPointLocalTransform().getTranslation();
            Matrix3f pose = seat.getSubPart().getRotation().toRotationMatrix();
            Vector3f relPos = pose.mult(rawRelPos, null);
            return SparkMathKt.toVec3(relPos);
        } else return new Vec3(0, 0, 0);
    }

    /**
     * 获取或创建测试用的幽灵刚体，并根据乘客姿势更新碰撞形状
     */
    private PhysicsGhostObject getOrCreateTestGhost(LivingEntity passenger, Pose pose) {
        if (testGhost == null) {
            // 懒加载创建幽灵刚体
            testGhost = new PhysicsGhostObject(new BoxCollisionShape(1, 1, 1)); // 临时形状，后面会更新
            testGhost.setCollisionGroup(CollisionGroups.PAWN);
            testGhost.setCollideWithGroups(CollisionGroups.PHYSICS_BODY); // 仅检测与车辆刚体的碰撞
        }
        // 根据乘客姿势更新碰撞形状
        AABB aabb = passenger.getLocalBoundsForPose(pose);
        float halfX = (float) (aabb.getXsize() * 0.5);
        float halfY = (float) (aabb.getYsize() * 0.5);
        float halfZ = (float) (aabb.getZsize() * 0.5);
        testGhost.setCollisionShape(new BoxCollisionShape(halfX, halfY, halfZ));
        return testGhost;
    }

    /**
     * 检查下车位置是否有效（地形可通过且不与车辆刚体碰撞）
     */
    private boolean isDismountLocationValid(Vec3 position, LivingEntity passenger, Pose pose) {
        // 1. 检查地形可通过性
        AABB aabb = passenger.getLocalBoundsForPose(pose);
        if (!DismountHelper.canDismountTo(this.level(), passenger, aabb.move(position))) {
            return false;
        }
        // 2. 检查刚体碰撞
        PhysicsGhostObject ghost = getOrCreateTestGhost(passenger, pose);
        ghost.setPhysicsLocation(PhysicsHelperKt.toBVector3f(position));
        int contactCount = getPhysicsLevel().getWorld().contactTest(ghost, null);
        return contactCount == 0;
    }

    /**
     * 生成候选下车位置列表（包括原始位置、邻位和刚体周围位置）
     * 每个位置的高度通过原版方法调整，避免陷入地面
     */
    private List<Vec3> generateCandidatePositions(Vec3 originalPos, LivingEntity passenger) {
        List<Vec3> candidates = new ArrayList<>();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        // 生成XZ位置的偏移列表
        List<Vec3> xzPositions = new ArrayList<>();
        xzPositions.add(new Vec3(originalPos.x, originalPos.y, originalPos.z)); // 原始XZ位置

        // 邻位偏移（XZ平面）
        double[] radius = {0.5, 1.0, 1.5, 2.0};
        int samples = 8;
        for (double r : radius) {
            for (int i = 0; i < samples; i++) {
                double angle = 2 * Math.PI * i / samples;
                double x = originalPos.x + r * Math.cos(angle);
                double z = originalPos.z + r * Math.sin(angle);
                xzPositions.add(new Vec3(x, originalPos.y, z));
            }
        }

        // 为每个XZ位置计算合适的高度
        for (Vec3 xzPos : xzPositions) {
            mutablePos.set(xzPos.x, xzPos.y, xzPos.z);
            double floorHeight = this.level().getBlockFloorHeight(mutablePos);
            if (DismountHelper.isBlockFloorValid(floorHeight)) {
                Vec3 adjustedPos = Vec3.upFromBottomCenterOf(mutablePos, floorHeight);
                candidates.add(adjustedPos);
            }
            // 如果地面高度无效，也可以尝试使用原始高度（防止车辆在空中时无法下车）
            else {
                // 使用原始位置的Y坐标作为备选
                candidates.add(new Vec3(xzPos.x, originalPos.y, xzPos.z));
            }
        }

        return candidates;
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        if (subPart != null) {
            Vec3 originalPos = passenger.getPosition(1);
            originalPos = originalPos.add(0, 0.1, 0); //防止陷地

            // 1. 首先尝试原始位置，所有姿势
            for (Pose pose : passenger.getDismountPoses()) {
                if (isDismountLocationValid(originalPos, passenger, pose)) {
                    return originalPos;
                }
            }

            // 2. 生成候选位置列表（包括邻位和刚体周围位置）
            java.util.List<Vec3> candidatePositions = generateCandidatePositions(originalPos, passenger);

            // 3. 对每个候选位置，尝试所有姿势
            for (Vec3 candidate : candidatePositions) {
                for (Pose pose : passenger.getDismountPoses()) {
                    if (isDismountLocationValid(candidate, passenger, pose)) {
                        return candidate;
                    }
                }
            }
        }
        return super.getDismountLocationForPassenger(passenger);//回退
    }

    @Override
    public void onPassengerTurned(@NotNull Entity entityToUpdate) {
        if (this.subPart != null && entityToUpdate instanceof LivingEntity livingEntity && ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem) {
            float rot = Mth.wrapDegrees(livingEntity.getYRot() - this.getYRot() + 180f);
            entityToUpdate.setYBodyRot(rot);
            livingEntity.yHeadRotO = rot;
            livingEntity.setYHeadRot(rot);
        }
    }

    @Override
    public void move(@NotNull MoverType type, @NotNull Vec3 pos) {
        //交由物理引擎处理
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
//        this.setPosRaw(x, y, z);
//        level().submitImmediateTask(PPhase.PRE, () -> {
//            updateBoundingBox();
//            return null;
//        });
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {

    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {

    }

    /**
     * 渲染剔除包围盒，直接使用 makeBoundingBox 时缓存的1倍AABB，避免每帧创建
     */
    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        if (cachedCullingAabb != null) {
            return cachedCullingAabb;
        }
        return super.getBoundingBoxForCulling();
    }

    @Override
    public boolean canCollideWith(@NotNull Entity entity) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return SparkLevel.getPhysicsLevel(level());
    }

    @Override
    public MMPartEntity getAnimatable() {
        return this;
    }

    @Override
    public @NotNull AnimController getAnimController() {
        if (subPart == null) return new AnimController(this);
        else return subPart.getAnimController();
    }

    private void updatePart() {
        VehicleCore vehicle = ObjectManager.clientAllVehicles.get(vehicleUUID);
        if (vehicle != null && vehicle.level == this.level()) {
            Part part = vehicle.partMap.get(partUUID);
            if (part != null) {
                this.subPart = part.getSubParts().get(subPartName);//设置实体对应的部件
                if (subPart != null) {
                    if (subPart.entity != null) subPart.entity.subPart = null;
                    subPart.entity = this;
                }
            }
        }
    }

    /**
     * 服务器创建实体时写入对应的部件UUID
     *
     * @param buffer 数据流
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        if (subPart != null) {
            buffer.writeBoolean(true);
            buffer.writeUUID(this.subPart.part.assembly.getAssemblyId());
            buffer.writeUUID(this.subPart.part.uuid);
            buffer.writeUtf(this.subPart.name);
        } else buffer.writeBoolean(false);
    }

    /**
     * 客户端接收实体创建包时读取匹配的部件UUID，
     * 寻找并并设置实体对应的部件
     *
     * @param additionalData 数据流
     */
    @Override
    public void readSpawnData(RegistryFriendlyByteBuf additionalData) {
        boolean hasPart = additionalData.readBoolean();
        if (hasPart) {
            vehicleUUID = additionalData.readUUID();
            partUUID = additionalData.readUUID();
            subPartName = additionalData.readUtf();
            updatePart();
        } else this.remove(RemovalReason.DISCARDED);
    }

    @NotNull
    @Override
    public Level getAnimLevel() {
        return this.level();
    }

    @NotNull
    @Override
    public BlackBoard getHurtData() {
        return new BlackBoard();
    }

    @NotNull
    @Override
    public ModelController getModelController() {
        if (subPart != null) return subPart.getModelController();
        else return new ModelController(this);
    }
}
