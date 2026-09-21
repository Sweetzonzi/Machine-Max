package io.github.sweetzonzi.machine_max.common.entity;

import cn.solarmoon.spark_core.EntityPatch;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.BlackBoard;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.PhysicsCollisionListener;
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

public class MMPartEntity extends VehicleEntity implements IEntityWithComplexSpawn, EntityPatch {

    public SubPart subPart;//实体所属的零件
    public UUID vehicleUUID;
    public UUID partUUID;
    public String subPartName;
    public AtomicReference<BoundingBox> boundingBox = new AtomicReference<>();
    public AtomicReference<Vector3f> bodyCenter = new AtomicReference<>();
    /** 下车候选校验用的幽灵刚体，常驻物理快照空间 */
    private PhysicsGhostObject testGhost;
    /** 上一个被拒绝的下车候选的拒绝原因，仅用于观测日志 */
    private String lastDismountRejectReason;
    /** 第 ①② 层允许的穿透深度上限（格），超过该深度才判为与载具刚体相撞 */
    private static final float PENETRATION_EPSILON = 0.02f;
    /** 候选去重的最小间距（格） */
    private static final double CANDIDATE_DEDUP_DISTANCE = 0.25;
    /** 抬升重试的格数上限，沿世界 +Y */
    private static final int MAX_LIFT = 2;
    /** 水平采样环的方向数 */
    private static final int RING_DIRECTIONS = 8;
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
    public boolean shouldCreateDefaultPhysicsBody() {
        return false;
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
    }

    /** 第 ① 层（locator）的水平采样环：零偏移 + 半径 0.5 的 8 个方向 */
    private static final List<Vec3> RING_NARROW = buildRingOffsets(0.5);
    /** 第 ② 层（上车点缓存）的水平采样环：零偏移 + 半径 {0.5, 1.0} 的 8 个方向 */
    private static final List<Vec3> RING_MEDIUM = buildRingOffsets(0.5, 1.0);
    /** 第 ③ 层（座位点盲撒）的水平采样环：零偏移 + 半径 {0.5, 1.0, 1.5, 2.0} 的 8 个方向 */
    private static final List<Vec3> RING_WIDE = buildRingOffsets(0.5, 1.0, 1.5, 2.0);

    /**
     * 构造水平采样环：零偏移（通道中心点本身）在前，随后是每个半径上的 8 个方向，
     * 方位自世界坐标 +X 轴起算。
     *
     * @param radii 环半径列表
     * @return 不可变的偏移列表
     */
    private static List<Vec3> buildRingOffsets(double... radii) {
        List<Vec3> offsets = new ArrayList<>();
        offsets.add(Vec3.ZERO);
        for (double radius : radii) {
            for (int i = 0; i < RING_DIRECTIONS; i++) {
                double angle = 2 * Math.PI * i / RING_DIRECTIONS;
                offsets.add(new Vec3(radius * Math.cos(angle), 0, radius * Math.sin(angle)));
            }
        }
        return List.copyOf(offsets);
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
     * 一条下车候选搜索通道，由「中心点 + 水平环 + Y 取值方式 + 是否容忍穿透」四要素确定。
     *
     * @param name             通道名（P1~P4），用于观测日志
     * @param locator          关联的 locator 名（仅 P1/P2 非空），用于观测日志
     * @param center           通道中心（世界坐标）
     * @param ring             相对中心的水平偏移列表，首项为零偏移
     * @param useFloorY        为 true 时候选 Y 取该列地板高度，否则取通道中心的世界 Y
     * @param tolerant         为 true 时允许 ε 内的浅穿透（P1/P2/P3 使用）
     * @param allowNoFloor     为 true 时，该列没有地板也沿用通道中心的 Y（P4 使用）
     * @param snapToBlockCenterXZ 为 true 时候选吸附到所在方块的中心，否则保留精确 XZ（P4 使用）
     */
    private record SearchPass(String name, String locator, Vec3 center, List<Vec3> ring,
                              boolean useFloorY, boolean tolerant, boolean allowNoFloor,
                              boolean snapToBlockCenterXZ) {
    }

    /**
     * 获取或创建测试用的幽灵刚体，并根据乘客姿势更新碰撞形状。
     * <p>幽灵体只在首次创建时加入物理快照空间，之后只改写形状与位置；这样查询全程停留在
     * 主线程只读的快照空间上，不触碰实时物理世界。</p>
     */
    private PhysicsGhostObject getOrCreateTestGhost(LivingEntity passenger, Pose pose) {
        if (testGhost == null) {
            // 懒加载创建幽灵刚体
            testGhost = new PhysicsGhostObject(new BoxCollisionShape(1, 1, 1)); // 临时形状，后面会更新
            testGhost.setCollisionGroup(CollisionGroups.PAWN);
            testGhost.setCollideWithGroups(CollisionGroups.PHYSICS_BODY); // 仅检测与车辆刚体的碰撞
            getPhysicsLevel().getWorld().getWorldSnapshot().addCollisionObject(testGhost);
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
     * 检查下车位置是否有效：地形可通过，且不与载具刚体深度重叠。
     *
     * @param position  候选落点（乘客脚底所在位置）
     * @param passenger 乘客
     * @param pose      用于校验的姿势
     * @param tolerant  为 true 时允许 ε 内的浅穿透（站在车顶等场景脚底与车体顶面相切）
     * @return 通过校验返回 true
     */
    private boolean isDismountLocationValid(Vec3 position, LivingEntity passenger, Pose pose, boolean tolerant) {
        // 1. 检查地形可通过性
        AABB aabb = passenger.getLocalBoundsForPose(pose);
        if (!DismountHelper.canDismountTo(this.level(), passenger, aabb.move(position))) {
            lastDismountRejectReason = "terrain_blocked";
            return false;
        }
        // 2. 检查刚体碰撞：幽灵盒中心与乘客包围盒中心对齐
        PhysicsGhostObject ghost = getOrCreateTestGhost(passenger, pose);
        Vec3 center = position.add(0, aabb.getYsize() * 0.5, 0);
        ghost.setPhysicsLocation(PhysicsHelperKt.toBVector3f(center));
        boolean blocked = tolerant
                ? hasDeeperThan(ghost, PENETRATION_EPSILON)
                : snapshotContactTest(ghost, null) != 0;
        if (blocked) lastDismountRejectReason = "body_contact";
        return !blocked;
    }

    /**
     * 在物理快照空间上对幽灵体做接触查询。
     * <p>物理引擎只报告已接触（距离 ≤ 0）的接触点，因此这里得到的是「相交」而非「贴近」。</p>
     *
     * @param ghost    已加入快照空间的幽灵体
     * @param listener 接触回调，可为 null
     * @return 接触次数
     */
    private int snapshotContactTest(PhysicsGhostObject ghost, PhysicsCollisionListener listener) {
        return getPhysicsLevel().getWorld().getWorldSnapshot().contactTest(ghost, listener);
    }

    /**
     * 判断幽灵体与载具刚体之间是否存在深度超过阈值的穿透。
     *
     * @param ghost     已加入快照空间的幽灵体
     * @param threshold 穿透深度阈值（格）
     * @return 存在更深穿透时返回 true
     */
    private boolean hasDeeperThan(PhysicsGhostObject ghost, float threshold) {
        boolean[] deep = {false};
        snapshotContactTest(ghost, event -> {
            if (event.getDistance1() < -threshold) deep[0] = true;
        });
        return deep[0];
    }

    /**
     * 按优先级构造下车候选的搜索通道：locator（P1/P2）→ 上车点缓存（P3）→ 座位点盲撒（P4）。
     *
     * @param passenger 正在离座的乘客
     * @param seat      按身份匹配到的座位，可能为 null（此时只构造第 ③ 层）
     * @return 有序的通道列表
     */
    private List<SearchPass> buildPasses(LivingEntity passenger, SeatSubsystem seat) {
        List<SearchPass> passes = new ArrayList<>();
        if (seat != null) {
            // 第 ① 层：作者为该座位声明的下车 locator
            for (String locatorName : seat.getAttr().getDismountLocators()) {
                if (!subPart.hasLocator(locatorName)) {
                    MachineMax.LOGGER.warn("零件 {} 的座位 {} 声明了下车 locator {}，但模型中不存在该 locator，已跳过",
                            subPart.name, seat.getName(), locatorName);
                    continue;
                }
                Vec3 center = SparkMathKt.toVec3(subPart.getLocatorWorldPos(locatorName));
                passes.add(new SearchPass("P1", locatorName, center, RING_NARROW, false, true, false, false));
                passes.add(new SearchPass("P2", locatorName, center, RING_NARROW, true, true, false, false));
            }
            // 第 ② 层：该座位的上车点缓存还原出的世界位置
            Vector3f boardingOffset = seat.getBoardingLocalOffset();
            if (boardingOffset != null && subPart.body != null) {
                Vec3 center = SparkMathKt.toVec3(MMMath.relPointWorldPos(boardingOffset, subPart.body));
                passes.add(new SearchPass("P3", null, center, RING_MEDIUM, false, true, false, false));
            }
        }
        // 第 ③ 层：座位点周边盲撒，中心为乘客当前位置
        passes.add(new SearchPass("P4", null, passenger.getPosition(1), RING_WIDE, true, false, true, true));
        return passes;
    }

    /**
     * 把一个「水平偏移 + 抬升」解析为具体候选落点。
     *
     * @param pass       所属通道
     * @param ringOffset 相对通道中心的水平偏移
     * @param lift       沿世界 +Y 的抬升格数
     * @return 候选落点；该组合无法构造出候选点（该列没有地板）时返回 null
     */
    private Vec3 resolvePassPoint(SearchPass pass, Vec3 ringOffset, int lift) {
        double x = pass.center().x + ringOffset.x;
        double z = pass.center().z + ringOffset.z;
        if (!pass.useFloorY()) {
            return new Vec3(x, pass.center().y + lift, z);
        }
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos(x, pass.center().y, z);
        double floorHeight = this.level().getBlockFloorHeight(mutablePos);
        if (!DismountHelper.isBlockFloorValid(floorHeight)) {
            // 该列没有地板：兜底通道沿用通道中心的 Y，使载具悬空时也可下车
            return pass.allowNoFloor() ? new Vec3(x, pass.center().y + lift, z) : null;
        }
        if (pass.snapToBlockCenterXZ()) {
            return Vec3.upFromBottomCenterOf(mutablePos, floorHeight).add(0, lift, 0);
        }
        // 精确 XZ + 该列地板面高度：locator 与上车点都依赖精确水平位置
        return new Vec3(x, mutablePos.getY() + floorHeight + lift, z);
    }

    /**
     * 判断候选落点是否与已尝试过的候选过近。
     * <p>locator 与座位点重合时会产生同一批坐标，去重可避免重复发起物理接触查询。</p>
     *
     * @param tried     已尝试的候选落点
     * @param candidate 待判定的候选落点
     * @return 距离小于 {@link #CANDIDATE_DEDUP_DISTANCE} 格时返回 true
     */
    private boolean isDuplicateCandidate(List<Vec3> tried, Vec3 candidate) {
        double thresholdSqr = CANDIDATE_DEDUP_DISTANCE * CANDIDATE_DEDUP_DISTANCE;
        for (Vec3 triedPos : tried) {
            if (triedPos.distanceToSqr(candidate) < thresholdSqr) return true;
        }
        return false;
    }

    /**
     * 按乘客身份定位其正在离开的座位。
     * <p>下车位置查询由 {@code SeatSubsystem.removePassenger()} 内的 {@code stopRiding()} 触发，
     * 调用时控制子系统已被置空，而座位的 {@code passenger} 字段尚未清空，
     * 因此身份匹配是查询期间唯一可靠的定位依据。</p>
     *
     * @param passenger 正在离座的乘客
     * @return 匹配到的座位；无匹配返回 null
     */
    private SeatSubsystem findOccupiedSeat(LivingEntity passenger) {
        if (subPart == null) return null;
        for (var subsystem : subPart.getSubsystems().values()) {
            if (subsystem instanceof SeatSubsystem seat && seat.passenger == passenger) return seat;
        }
        return null;
    }

    @Override
    public @NotNull Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        if (subPart == null) return super.getDismountLocationForPassenger(passenger);
        SeatSubsystem seat = findOccupiedSeat(passenger);
        List<Pose> poses = passenger.getDismountPoses();
        // 已尝试候选集合：各通道共享，不设校验次数上限
        List<Vec3> tried = new ArrayList<>();
        for (SearchPass pass : buildPasses(passenger, seat)) {
            // 抬升语义是「踩上障碍物」，只向上，最多 2 格
            for (int lift = 0; lift <= MAX_LIFT; lift++) {
                String liftReason = null;
                for (Vec3 ringOffset : pass.ring()) {
                    Vec3 candidate = resolvePassPoint(pass, ringOffset, lift);
                    if (candidate == null) {
                        liftReason = "no_floor";
                        continue;
                    }
                    if (isDuplicateCandidate(tried, candidate)) continue;
                    tried.add(candidate);
                    for (Pose pose : poses) {
                        if (isDismountLocationValid(candidate, passenger, pose, pass.tolerant())) {
                            return candidate; // 首个通过即采用，后续通道被跳过
                        }
                    }
                    liftReason = lastDismountRejectReason;
                }
                if (liftReason != null) {
                    MachineMax.LOGGER.debug("dismount pass={} locator={} lift={} reason={}",
                            pass.name(), pass.locator() == null ? "" : pass.locator(), lift, liftReason);
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
    public void remove(@NotNull RemovalReason reason) {
        super.remove(reason);
        // 幽灵体常驻物理快照空间，实体销毁时一并摘除，避免快照空间长期引用已失效的实体
        if (testGhost != null) {
            getPhysicsLevel().getWorld().getWorldSnapshot().removeCollisionObject(testGhost);
            testGhost = null;
        }
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
    public BlackBoard getHurtData() {
        return new BlackBoard();
    }
}
