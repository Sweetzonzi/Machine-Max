package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.api.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.*;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.ballistics_framework.api.ArmorLevel;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableRigidObject;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.IModularSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.HydrodynamicAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionHandler;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.MMDamageExtensions;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageApi;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageExtensions;
import io.github.sweetzonzi.ballistics_framework.api.PenetrationResult;
import net.minecraft.world.phys.Vec3;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBoxes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.ShapeHelper;
import io.github.sweetzonzi.machine_max.util.mechanic.DynamicUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import io.github.sweetzonzi.machine_max.util.terrain.LocalHeightField;
import jme3utilities.math.MyMath;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber
@Getter
public class SubPart extends DestroyableRigidObject implements ISubsystemHost {
    //渲染与交互
    @Nullable
    public MMPartEntity entity;//用于渲染模型以及和原版内容进行交互的的实体对象
    //游戏机制
    public final Part part;
    public String name;
    public final SubPartAttr attr;
    public final ConcurrentHashMap<String, HitBox> hitBoxes = new ConcurrentHashMap<>();
    public final InteractBoxes interactBoxes;//交互判定
    public final HashMap<String, AbstractSubsystem> subsystems = HashMap.newHashMap(1);
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    /** 子系统额外质量映射表（子系统名称 → 额外质量值 kg），由 ISubsystemHost.updateExtraMass() 管理 */
    private final ConcurrentHashMap<String, Float> extraMassMap = new ConcurrentHashMap<>();
    //物理
    public final boolean GROUND_COLLISION_ONLY;//是否仅和零件之下的地面方块碰撞
    public final float stepHeight;
    public Vec3 projectedArea = null;
    public float bodyMinY = -99999;
    public final HashSet<BlockPos> climbableBlocks = new HashSet<>();
    public final LocalHeightField heightField = new LocalHeightField(3);//爬坡辅助用高度场
    public final CollisionHandler collisionHandler;

    // === 性能优化缓存：getShapeMinY ===
    /** 缓存的相对偏移量：刚体中心Y - 碰撞形状最低点Y（仅随旋转变化） */
    private float cachedRelativeMinYOffset = Float.NaN;
    /** 上次计算 relativeMinYOffset 时的刚体旋转，用于判断旋转是否变化 */
    private final Quaternion cachedRotationForMinY = new Quaternion();

    // === 性能优化缓存：LocalHeightField.rebuild ===
    /** 上次重建高度场时的中心方块X坐标 */
    private int lastHFBlockX = Integer.MIN_VALUE;
    /** 上次重建高度场时的中心方块Z坐标 */
    private int lastHFBlockZ = Integer.MIN_VALUE;

    public SubPart(String name, Part part, SubPartAttr attr) {
        super(part.level, attr.getCollisionShape(part.variant), attr.mass);
        this.part = part;
        this.name = name;
        this.attr = attr;
        if (!attr.interactBoxes.isEmpty()) {
            this.interactBoxes = new InteractBoxes(this, attr.interactBoxes, attr.getInteractBoxShape(part.variant));
        } else this.interactBoxes = null;
        PhysicsBodyExtensionKt.setOwner(this.body, this);
        this.body.setSleepingThresholds(0.1f, 0.1f);
        this.body.setProtectGravity(true);
        if (part.getLevel().isClientSide()) {
            this.body.setKinematic(true);
        }
        Vector3f inverseInertia = new Vector3f();
        this.body.getInverseInertiaLocal(inverseInertia);
        this.body.setFriction(1.0f);
        this.body.setCollisionGroup(CollisionGroups.PHYSICS_BODY);
        this.body.setCollideWithGroups(CollisionGroups.PHYSICS_BODY);
        this.body.addCollideWithGroup(CollisionGroups.PAWN);
        if (attr.blockCollision == SubPartAttr.BlockCollisionType.TRUE) {
            GROUND_COLLISION_ONLY = false;
            this.body.addCollideWithGroup(CollisionGroups.TERRAIN);
        } else if (attr.blockCollision == SubPartAttr.BlockCollisionType.GROUND) {
            GROUND_COLLISION_ONLY = true;
            this.body.addCollideWithGroup(CollisionGroups.TERRAIN);
        } else {
            GROUND_COLLISION_ONLY = false;
        }
        this.stepHeight = attr.stepHeight;
        this.projectedArea = attr.projectedArea;
        this.collisionHandler = new CollisionHandler(this);
        //各类回调
        PhysicsBodyExtensionKt.onCollidePre(this.body, event -> this.collisionHandler.onPreContact(
                event.getO1(), event.getO2(),
                event.getO1Point(), event.getO2Point(),
                event.getO1Point().getId()));
        PhysicsBodyExtensionKt.onCollideProcessed(this.body, event -> {
            this.collisionHandler.onContactProcessed(
                    event.getO1(), event.getO2(),
                    event.getO1Point(), event.getO2Point(),
                    event.getO1Point().getId());
            return null;
        });
    }

    @Override
    public void addToLevel() {
        super.addToLevel();
        for (AbstractConnector connector : connectors.values()) {
            if (connector.hasPart())
                connector.addToLevel();
        }
    }

    @Override
    public void destroy() {
        collisionHandler.effectManager.stopAll();
        super.destroy();
        subsystems.forEach((name, subsystem) -> subsystem.onDetach());
        subsystems.clear();
        for (AbstractConnector connector : connectors.values()) {
            connector.destroy();
        }
        if (interactBoxes != null) {
            for (InteractBox interactBox : interactBoxes.values()) interactBox.destroy();
            interactBoxes.destroy();
        }
        if (this.entity != null) {
            this.entity.subPart = null;
            this.entity.remove(Entity.RemovalReason.DISCARDED);
            this.entity = null;
        }
    }

    public void refreshPartEntity() {
        this.entity = new MMPartEntity(getLevel(), this);
        getLevel().addFreshEntity(this.entity);
    }

    @SubscribeEvent
    public static void onPreCollision(NeedsCollisionEvent event) {
        var ownerA = PhysicsBodyExtensionKt.getOwner(event.getPcoA());
        var ownerB = PhysicsBodyExtensionKt.getOwner(event.getPcoB());
        if (ownerA == null || ownerB == null) return;
        if (ownerA instanceof SubPart && ownerB instanceof MMPartEntity) {
            event.setShouldCollide(false);
            return;
        } else if (ownerB instanceof SubPart && ownerA instanceof MMPartEntity) {
            event.setShouldCollide(false);
            return;
        }
        if (ownerA.getPhysicsLevel().getMcLevel().isClientSide()) {
            if (ownerA instanceof SubPart subPart && !subPart.isActive()) {
                event.setShouldCollide(false);
                return;
            } else if (ownerB instanceof SubPart subPart && !subPart.isActive()) {
                event.setShouldCollide(false);
                return;
            }
        }
        //同装配体零件不发生碰撞（VehicleCore 或 MechUnit 共用）
        if (ownerA instanceof SubPart subPartA && ownerB instanceof SubPart subPartB) {
            if (subPartA.part.assembly != null && subPartA.part.assembly == subPartB.part.assembly) {
                event.setShouldCollide(false);
                return;
            }
        }
        //装配体不与乘客发生碰撞
        AbstractControllableSubsystem sub;
        if (ownerA instanceof SubPart subPart && ownerB instanceof LivingEntity livingEntity) {
            sub = ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem();
            if (sub != null && sub.getOwner().getSubPart().getPart().getAssembly() == subPart.part.assembly) {
                event.setShouldCollide(false);
            }
        } else if (ownerB instanceof SubPart subPart && ownerA instanceof LivingEntity livingEntity) {
            sub = ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem();
            if (sub != null && sub.getOwner().getSubPart().getPart().getAssembly() == subPart.part.assembly) {
                event.setShouldCollide(false);
            }
        }
    }

    @Override
    public void preTick() {
        super.preTick();
        if (part.assemblingProgress >= 1f && tickCount == 10) {
            body.setGravity(SparkLevel.getPhysicsLevel(level).getWorld().getGravity(null));
        }
    }

    @Override
    public void postTick() {
        super.postTick();
        for (AbstractConnector connector : this.connectors.values()) connector.mcTick();
        if (!isRemoved()) {
            if (this.entity == null || this.entity.isRemoved()) {
                if (!getLevel().isClientSide()) refreshPartEntity();
            }
            if (this.entity != null && !this.entity.isRemoved()) {
                BoundingBox box = PhysicsBodyExtensionKt.stateOf(body).getCachedBoundingBox();
                Vector3f center = PhysicsBodyExtensionKt.stateOf(body).getTransform().getTranslation();
                entity.boundingBox.set(box);
                entity.bodyCenter.set(center);
            }
        }
        collisionHandler.effectManager.tick();
    }

    // ===== 临时流体动力计算向量缓存，避免每 tick 分配 =====
    private final Vector3f tmpRigidVel = new Vector3f();
    private final Vector3f tmpLocalVel = new Vector3f();
    private final Vector3f tmpWorldVel = new Vector3f();
    private final Vector3f tmpRayDir = new Vector3f();
    private final Vector3f tmpTarget = new Vector3f();
    private final Vector3f tmpLocalAeroForce = new Vector3f();
    private final Vector3f tmpRigidAeroForce = new Vector3f();

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        collisionHandler.effectManager.resetLatestStates();
        // 更新所有HitBox的生效状态
        if (isActive() || (physicsTickCount % getPhysicsLevel().getTps() * 10 == 0)) {
            for (HitBox hitBox : hitBoxes.values()) {
                hitBox.updateActive();
            }
        }
        if (!isActive()) return;
        for (AbstractConnector connector : this.connectors.values()) connector.prePhysicsTick();
        this.body.getLinearVelocity(tmpWorldVel);
        // 仅在服务端且有速度时应用流体动力
        if (!level.isClientSide() && tmpWorldVel.lengthSquared() > 0.01f) {
            for (Map.Entry<String, HydrodynamicAttr> entry : attr.hydrodynamics.entrySet()) {
                String name = entry.getKey();
                HydrodynamicAttr hydrodynamicAttr = entry.getValue();

                for (String locatorName : attr.hydrodynamicLocators.get(name)) {
                    Transform hydroCenterTransform = getLocatorLocalTransform(locatorName);
                    Vector3f hydroCenterPos = hydroCenterTransform.getTranslation();
                    Quaternion hydroCenterRot = hydroCenterTransform.getRotation();

                    // === 计算气动点局部速度 ===
                    MMMath.relPointLocalVel(hydroCenterPos, this.body, tmpRigidVel);
                    MyQuaternion.rotate(hydroCenterRot, tmpRigidVel, tmpLocalVel);

                    if (tmpLocalVel.lengthSquared() < 0.01f) continue;

                    // === 遮挡检测（仅一次，沿来流方向） ===
                    float occlusion = 1f;
                    double range = hydrodynamicAttr.effectiveRange();
                    if (range > 0) {
                        // 世界坐标下的来流方向
                        MyQuaternion.rotate(hydroCenterRot, tmpLocalVel, tmpRayDir);
                        tmpRayDir.negateLocal().normalize();

                        Vector3f pos = getLocatorWorldPos(locatorName);
                        tmpTarget.set(tmpRayDir).multLocal((float) range).addLocal(pos);

                        List<PhysicsRayTestResult> results =
                                getPhysicsLevel().getWorld().rayTest(pos, tmpTarget);

                        for (PhysicsRayTestResult ray : results) {
                            var hit = ray.getCollisionObject();
                            if (hit == this.body) continue;

                            var owner = PhysicsBodyExtensionKt.getOwner(hit);
                            if (owner instanceof SubPart sp &&
                                    sp.part.assembly == this.part.assembly &&
                                    sp.attr.hydroPriority >= attr.hydroPriority) {

                                occlusion = Math.min(occlusion, ray.getHitFraction());
                                break;
                            }
                        }
                    }

                    if (occlusion <= 0f) continue;

                    // === 气动力计算（气动点局部坐标） ===
                    tmpLocalAeroForce.set(
                            DynamicUtil.aeroDynamicForce(
                                    1.29f,        // kg/m^3 空气密度
                                    1.8e-5f,      // Pa·s 动力粘度
                                    this.projectedArea,
                                    hydrodynamicAttr,
                                    tmpLocalVel
                            )
                    ).multLocal(occlusion);

                    // === 转回刚体局部坐标 ===
                    MyQuaternion.rotateInverse(hydroCenterRot, tmpLocalAeroForce, tmpRigidAeroForce);

                    // === 应用到世界 ===
                    this.body.applyForce(
                            MMMath.localVectorToWorldVector(tmpRigidAeroForce, this.body),
                            MMMath.localVectorToWorldVector(hydroCenterPos, this.body)
                    );
                }
            }
        }
        //攀爬辅助处理
        climbableBlocks.clear();
        if (!level.isClientSide() && attr.blockCollision == SubPartAttr.BlockCollisionType.GROUND) {
            // 获取刚体旋转，用于判断是否需要重新计算 getShapeMinY
            Quaternion currentRot = body.getPhysicsRotation(null);
            // 旋转变化时重新计算相对偏移量（相对偏移量仅受旋转影响，平动不影响）
            if (Float.isNaN(cachedRelativeMinYOffset)
                    || MyQuaternion.dot(cachedRotationForMinY, currentRot) < 0.9999f) {
                float absoluteMinY = ShapeHelper.getShapeMinY(this.body, 0.1f);
                cachedRelativeMinYOffset = body.getPhysicsLocation(null).y - absoluteMinY;
                cachedRotationForMinY.set(currentRot);
            }
            // 用缓存的相对偏移量快速计算当前世界坐标下的 bodyMinY
            bodyMinY = body.getPhysicsLocation(null).y - cachedRelativeMinYOffset;

            Vector3f pos = body.getPhysicsLocation(null);
            int blockX = (int) pos.x;
            int blockZ = (int) pos.z;
            // 仅在中心方块发生变化时重建高度场 (定期重建以反映方块更新)
            if (blockX != lastHFBlockX || blockZ != lastHFBlockZ || physicsTickCount % 100 == 0) {
                heightField.rebuild(getPhysicsLevel(), blockX, blockZ, bodyMinY);
                lastHFBlockX = blockX;
                lastHFBlockZ = blockZ;
            }
            //遍历范围内的方块
            AABB aabb = SparkMathKt.toAABB(PhysicsBodyExtensionKt.stateOf(this.body).getCachedBoundingBox())
                    .expandTowards(new Vec3(tmpWorldVel.x, 0, tmpWorldVel.z).scale(0.1f));
            int minX = (int) Math.floor(aabb.minX);
            int minZ = (int) Math.floor(aabb.minZ);
            int maxX = (int) Math.ceil(aabb.maxX);
            int maxZ = (int) Math.ceil(aabb.maxZ);
            float y0 = (float) Math.floor(bodyMinY) - 0.1f;
            Set<BlockPos> noCollisionBlocks = new HashSet<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos currentPos = new BlockPos(x, (int) Math.floor(y0), z);
                    SectionSnapshot.BlockSnapshot blockSnapshot = getPhysicsLevel().terrainManager.getBlockSnapshotAt(currentPos);

                    if (blockSnapshot != null) {
                        BlockState blockState = blockSnapshot.getState();
                        float blockHeight = blockState.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)
                                ? 1.0f
                                : (float) blockState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Direction.Axis.Y);
                        float terrainHeight = blockHeight + currentPos.getY();

                        if (terrainHeight > y0 && terrainHeight < bodyMinY + attr.stepHeight) {
                            float height = terrainHeight - bodyMinY; // 部件最低点与地形的高度差
                            noCollisionBlocks.clear();
                            // 向上遍历检查连续方块
                            BlockPos highestBlockPos = currentPos;
                            while (height <= stepHeight) {
                                noCollisionBlocks.add(highestBlockPos);
                                SectionSnapshot.BlockSnapshot higherSnapshot = getPhysicsLevel().terrainManager.getBlockSnapshotAt(highestBlockPos);
                                if (higherSnapshot == null) break;

                                BlockState higherState = higherSnapshot.getState();
                                float higherBlockHeight = (float) higherState.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).max(Direction.Axis.Y);
                                float higherTerrainHeight = higherBlockHeight + highestBlockPos.getY();
                                height = higherTerrainHeight - y0;
                                highestBlockPos = highestBlockPos.above();
                            }
                            // 根据高度判断是否可攀爬
                            if (height <= stepHeight) {
                                climbableBlocks.addAll(noCollisionBlocks);
                            }
                        }
                    }
                }
            }
        }

    }

    @Override
    public void postPhysicsTick() {
        super.postPhysicsTick();
        for (AbstractConnector connector : this.connectors.values()) connector.postPhysicsTick();
    }

    // ==================== BallisticsFramework 协议穿甲管线 ====================

    @Override
    public float getRHA(BFDamageContext ctx) {
        return findHitBox(ctx).getRHA(this);
    }

    @Override
    public ArmorLevel getArmorLevel(BFDamageContext ctx) {
        return ArmorLevel.fromRha(getRHA(ctx));
    }

    @Override
    public float modifyPenetration(BFDamageContext ctx) {
        HitBox hitBox = findHitBox(ctx);
        return hitBox.modifyPiercing(ctx.source(), ctx.penetration());
    }

    @Override
    public boolean isArmorPenetrated(BFDamageContext ctx) {
        return modifyPenetration(ctx) > getRHA(ctx);
    }

    /**
     * 穿甲结果判定：跳弹 → 击穿 → 未击穿。
     * <p>
     * 默认实现仅区分击穿/未击穿，此处覆写增加跳弹判定：
     * 根据入射角、弹丸口径、穿深和装甲RHA综合计算跳弹概率。
     * 碾压时（口径或穿深远超装甲）免疫跳弹。
     *
     * @param ctx 命中上下文
     * @return PENETRATED / BLOCKED / RICOCHET
     */
    @Override
    public PenetrationResult resolvePenetration(BFDamageContext ctx) {
        // 计算入射角（与法线夹角，度）
        Vec3 normal = ctx.hitNormal();
        Vec3 velocity = ctx.hitVelocity();
        double cosTheta = Math.abs(normal.normalize().dot(velocity.normalize()));
        float impactAngleDeg = (float) Math.toDegrees(Math.acos(cosTheta));

        // 从扩展容器读取口径（mm），若未写入则回退到0（无碾压保护）
        float caliber = ctx.extensions().get(BFDamageExtensions.CALIBER);
        float effectivePen = modifyPenetration(ctx);
        float armorRha = getRHA(ctx);

        // 跳弹判定
        if (ArmorUtil.shouldRicochet(impactAngleDeg, caliber, effectivePen, armorRha)) {
            return PenetrationResult.RICOCHET;
        }

        return isArmorPenetrated(ctx) ? PenetrationResult.PENETRATED : PenetrationResult.BLOCKED;
    }

    /**
     * 根据穿甲结果计算最终伤害量（毫米级精度）。
     * <p>
     * PENETRATED：击穿，伤害由碰撞箱的 {@code modifyDamage} 处理（考虑装甲后效衰减）；
     * BLOCKED / RICOCHET：未击穿，若碰撞箱支持钝伤（{@code hasUnpenDamage}），
     * 按穿深/装甲厚度比计算钝伤比例（幂函数衰减），否则为 0。
     *
     * @param ctx    命中上下文
     * @param result 由 {@link #resolvePenetration} 返回的穿甲结果
     * @return 最终伤害量
     */
    @Override
    public float calculateFinalDamage(BFDamageContext ctx, PenetrationResult result) {
        HitBox hitBox = findHitBox(ctx);
        if (result == PenetrationResult.PENETRATED) {
            return hitBox.modifyDamage(ctx.source(), ctx.baseDamage());
        }
        if (hitBox.hasUnpenDamage()) {
            float rha = getRHA(ctx);
            if (rha > 0) {
                float ratio = Math.clamp(ctx.penetration() / rha, 0f, 1f);
                float unpenDmg = ctx.baseDamage() * (float) Math.pow(ratio, hitBox.getUnpenPower());
                return hitBox.modifyDamage(ctx.source(), unpenDmg);
            }
        }
        return 0f;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level.isClientSide()) return true;
        // 通过 TB 上下文栈取回完整的命中上下文
        BFDamageContext ctx = BFDamageApi.getContextFor(this);
        if (ctx == null) {
            // 不在协议管线内，尝试用低信息量上下文模拟
            ctx = createContextFromVanilla(source, amount);
        }
        if (ctx == null) {
            // 仍不在协议管线内，丢弃伤害
            return false;
        }
        // 触发伤害事件
        SubPartDamageEvent.Pre event = NeoForge.EVENT_BUS.post(new SubPartDamageEvent.Pre(this, ctx, amount));
        if (event.isCanceled()) return false;
        ctx = event.getCtx();
        amount = event.getDamageAmount(); // 以事件最终结果为准
        Vec3 sourcePos = ctx.hitPoint();
        HitBox hitBox = findHitBox(ctx);
        // 击退动量 — 捕获 ctx，延迟到物理任务中决定用 IMPULSE 还是回退公式
        final BFDamageContext capCtx = ctx;
        final DamageSource capSource = source;
        final float capAmount = amount;
        SparkLevel.getPhysicsLevel(level).submitImmediateTask(PPhase.PRE, () -> {
            float knockBack;
            if (capCtx.extensions().contains(BFDamageExtensions.IMPULSE)) {
                // 弹道框架显式提供了冲量（N·s），直接取用（可能为 0 表示明确不击退）
                knockBack = capCtx.extensions().get(BFDamageExtensions.IMPULSE);
            } else if (!capSource.is(MMDamageTypes.PART_COLLISION)) {
                // 未提供 IMPULSE：回退到伤害基公式
                Vector3f contactSpeed = PhysicsHelperKt.toBVector3f(capCtx.hitVelocity());
                if (contactSpeed.lengthSquared() <= 1e-6f) return null;
                knockBack = (float) (Math.log10(Math.max(1.01, 10 * Math.sqrt(capAmount / getMaxDurability()))) * 250f);
                if (capSource.getDirectEntity() != null && capSource.getWeaponItem() != null) {
                    knockBack *= EnchantmentHelper.modifyKnockback((ServerLevel) level, capSource.getWeaponItem(), capSource.getDirectEntity(), capSource, 1.0f);
                }
                if (capSource.is(DamageTypeTags.IS_EXPLOSION)) knockBack *= 10.0f;
            } else { // 一般碰撞伤害不需要额外冲量，物理引擎负责处理
                return null;
            }
            if (knockBack <= 1e-6f) return null;
            Vector3f contactSpeed = PhysicsHelperKt.toBVector3f(capCtx.hitVelocity());
            part.assembly.activatePhysics();
            Vector3f contactPoint = PhysicsHelperKt.toBVector3f(capCtx.hitPoint());
            this.body.applyImpulse(contactSpeed.normalize().mult(knockBack),
                    contactPoint.subtract(this.body.getPhysicsLocation(null)));
            return null;
        });
        // 连接点冲击分配
        float impactDamage = hitBox.modifyImpact(source, amount);
        if (impactDamage > 0) {
            distributeDamageImpactToConnectors(impactDamage, PhysicsHelperKt.toBVector3f(sourcePos));
        }
        // 累积伤害
        accumulateDamage(amount, ctx);
        // 音效与粒子
        Vector3f normal = PhysicsHelperKt.toBVector3f(ctx.hitNormal());
        BFDamageContext finalCtx = ctx;
        SparkLevel.submitImmediateTask(level, PPhase.POST, () -> {
            SoundEvent sound = isArmorPenetrated(finalCtx) ? hitBox.getHitPenSound() : hitBox.getHitUnPenSound();
            level.playSound(null, sourcePos.x, sourcePos.y, sourcePos.z, sound, SoundSource.NEUTRAL, 0.5f, 1);
            if (!isArmorPenetrated(finalCtx)) {
                for (int i = 0; i < 3; i++) {
                    var dir = normal.mult(0.3f).add(new Vector3f(
                            (float) (Math.random() - 0.5f),
                            (float) (Math.random() - 0.5f),
                            (float) (Math.random() - 0.5f)).mult(0.1f));
                    level.addParticle(ParticleTypes.FIREWORK, sourcePos.x, sourcePos.y, sourcePos.z, dir.x, dir.y, dir.z);
                }
            }
        });
        return true;
    }

    @Override
    @Nullable
    public BFDamageContext createContextFromVanilla(DamageSource source, float amount) {
        // 原版伤害缺少弹道信息，返回仅含 source+baseDamage 的低信息量上下文
        return BFDamageContext.builder()
                .source(source)
                .baseDamage(amount)
                .penetration(amount)
                .build();
    }

    /**
     * 根据上下文查找命中的碰撞箱
     */
    @NotNull
    private HitBox findHitBox(@NotNull BFDamageContext ctx) {
        HitBox hitBox = ctx.extensions().get(MMDamageExtensions.HIT_BOX);
        if (hitBox != null) return hitBox;
        // 回退：找装甲最厚的
        return findStrongestHitBox();
    }

    /**
     * 获取护甲水平最强的碰撞箱
     */
    @NotNull
    public HitBox findStrongestHitBox() {
        HitBox best = null;
        float maxThickness = -1;
        for (HitBox box : hitBoxes.values()) {
            float t = box.getRHA(this);
            if (t > maxThickness) {
                maxThickness = t;
                best = box;
            }
        }
        return best;
    }

    /**
     * <p>根据伤害和各连接点的距离，施加冲击力至各个连接点</p>
     * <p>Distributes the impact to each connector based on the damage and distance to each connector</p>
     *
     * @param impact      冲击
     * @param impactPoint 冲击点
     */
    protected void distributeDamageImpactToConnectors(float impact, Vector3f impactPoint) {
        // 累积冲击效果用于削减连接点结构完整性
        Map<AbstractConnector, Float> impactWeights = new HashMap<>();
        float totalImpactWeight = 0;
        // 可调节参数
        final float DISTANCE_EXPONENT = 2.0f; // 距离指数：1=反比，2=平方反比
        final float MIN_DISTANCE = 0.1f; // 最小距离，防止除零和过大的权重
        for (AbstractConnector connector : this.connectors.values()) {
            if (!connector.hasPart() || connector.isInternal() || connector.attr.getImpactMultiplier() <= 0)
                continue; // 仅有连接且可破坏的连接点参与分配
            Vector3f connectorPos = MMMath.relPointWorldPos(connector.offsetFromMassCenter.getTranslation(), this.body);
            float distance = Math.max(connectorPos.distance(impactPoint), MIN_DISTANCE);
            // 权重是距离的指数反比，距离越远权重越小
            float weight = 1.0f / (float) Math.pow(distance, DISTANCE_EXPONENT);
            impactWeights.put(connector, weight);
            totalImpactWeight += weight;
        }
        // 分配冲击伤害
        for (Map.Entry<AbstractConnector, Float> entry : impactWeights.entrySet()) {
            float impactFraction = entry.getValue() / totalImpactWeight;
            entry.getKey().accumulateImpact(impact * impactFraction);
        }
    }

    /**
     * <p>维修零件和子系统并加固连接点</p>
     * <p>Repairs the sub-part and subsystems and strengthen the connectors</p>
     *
     * @param amount          零件维修量 (>0) sub-part repair damageAmount (>0)
     * @param subSystemAmount 子系统维修量 (>0) subsystem repair damageAmount (>0)
     * @param connectorAmount 连接点结构完整性加固量 (>0) connector integrity strengthen damageAmount (>0)
     * @return 是否成功修理
     */
    public boolean repair(float amount, float subSystemAmount, float connectorAmount) {
        if (!level.isClientSide) {
            part.setRenderWireframe(true); // 尝试维修时重置为线框模式
            // 修理零件
            float repairAmount = 0f;
            float requestedRepairAmount = Math.max(amount, 0f);
            float maxDurability = getMaxDurability();
            float currentDurability = getDurability();
            if (requestedRepairAmount > 0f && maxDurability > 0f && currentDurability < maxDurability) {
                float maxRepairRatio = 1f;
                FabricatingRecipe recipe = part.getRecipe();
                if (recipe != null && recipe.isManualAssemblablePart()) {
                    int totalMaterials = recipe.getManualAssembleIngredientList().size();
                    if (totalMaterials > 0) {
                        maxRepairRatio = Math.clamp((float) part.getMaterialProgress() / totalMaterials, 0f, 1f);
                    }
                }
                float currentRatio = Math.clamp(currentDurability / maxDurability, 0f, 1f);
                float repairDeltaRatio = requestedRepairAmount / maxDurability;
                float targetRatio = Math.min(currentRatio + repairDeltaRatio, maxRepairRatio);
                float targetDurability = targetRatio * maxDurability;
                repairAmount = Math.min(
                        Math.max(0f, targetDurability - currentDurability),
                        maxDurability - currentDurability
                );
            }
            float subsystemsRepairAmount = Math.max(subSystemAmount, 0);
            float connectorsRepairAmount = Math.max(connectorAmount, 0);
            if (repairAmount > 0f) {
                setDurability(currentDurability + repairAmount);
            }
            // 修理子系统（若实现 IModularSubsystem 则路由到模块，否则直接设置耐久度）
            for (AbstractSubsystem subsystem : subsystems.values()) {
                if (subsystemsRepairAmount <= 0) break;
                if (subsystem.getDurability() < subsystem.getMaxDurability()) {
                    if (subsystem instanceof IModularSubsystem modular) {
                        // 模块化子系统：通过 routeRepair 按受损比例分摊到各模块
                        modular.routeRepair(null, subsystemsRepairAmount);
                        subsystemsRepairAmount -= subsystem.getMaxDurability() - subsystem.getDurability();
                    } else {
                        float subSystemRepairAmount = Math.min(subsystemsRepairAmount, subsystem.getMaxDurability() - subsystem.getDurability());
                        subsystem.setDurability(subsystem.getDurability() + subSystemRepairAmount);
                        subsystemsRepairAmount -= subSystemRepairAmount;
                    }
                }
            }
            // 加固连接点
            for (AbstractConnector connector : connectors.values()) {
                if (connectorsRepairAmount <= 0) break;
                if (connector.getIntegrity() < connector.getBasicIntegrity()) {
                    float connectorRepairAmount = Math.min(connectorsRepairAmount, connector.getBasicIntegrity() - connector.getIntegrity());
                    connector.addIntegrity(connectorRepairAmount);
                    connectorsRepairAmount -= connectorRepairAmount;
                }
                if (connector.hasPart() && connector.attachedConnector.getIntegrity() < connector.attachedConnector.getBasicIntegrity()) {
                    float connectorRepairAmount = Math.min(connectorsRepairAmount,
                            connector.attachedConnector.getBasicIntegrity() - connector.attachedConnector.getIntegrity());
                    connector.attachedConnector.addIntegrity(connectorRepairAmount);
                    connectorsRepairAmount -= connectorRepairAmount;
                }
            }
            part.recomputeAssemblyFromDurability();
            syncToClient();
            return !(repairAmount == 0 && subSystemAmount == subsystemsRepairAmount && connectorAmount == connectorsRepairAmount);
        } else return false;
    }

    /**
     * <p>处理各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link DestroyableObject#preTick()}</p>
     * <p>Handles the damage caused by each thread and applies it to the subsystem, which will be handled in the main thread, see {@link DestroyableObject#preTick()}</p>
     */
    protected void handleAccumulatedDamage() {
        if (!level.isClientSide() && !accumulatedDamage.isEmpty()) {
            float totalDamage = 0;
            Vec3 soundPos = Vec3.ZERO;
            while (!accumulatedDamage.isEmpty()) {
                Pair<Float, BFDamageContext> pair = accumulatedDamage.poll();
                float damage = pair.getFirst();
                BFDamageContext ctx = pair.getSecond();
                SubPartDamageEvent.Pre event = new SubPartDamageEvent.Pre(this, ctx, damage);
                //向子系统发送伤害事件，对子系统造成伤害
                HitBox hitBox = findHitBox(ctx);
                if (hitBox != null && hitBox.getSubsystem() != null) {
                    hitBox.getSubsystem().onHurt(event);
                }
                if (!event.isCanceled()) { // 若伤害未被子系统取消
                    // 广播事件
                    NeoForge.EVENT_BUS.post(new SubPartDamageEvent.Post(this, ctx, damage));
                    totalDamage += damage;
                    soundPos = ctx.hitPoint();
                }
            }
            if (totalDamage > 0) {
                setDurability(Math.clamp(getDurability() - totalDamage, 0, getMaxDurability()));
                part.recomputeAssemblyFromDurability();
                if (part.assembly != null) {
                    float rate = isDestroyed() ? part.type.vehicleDamageRateDestroyed : part.type.vehicleDamageRate;
                    part.assembly.onPartDamage(part, Math.max(0f, totalDamage * rate));
                }
                if (isDestroyed() && getDestroyTime() > 20) { // 仅剩最后1秒销毁倒计时时不再额外缩减
                    int extraAdvance = Math.round(totalDamage * MMServerConfig.getSubPartDestroyAdvanceTicksPerDamage());
                    if (extraAdvance > 0) {
                        tickDestroyTimer(Math.min(extraAdvance, getDestroyTime() - 20));
                    }
                }
                //发包同步部件状态
                syncToClient();
            }
        }
    }

    protected void setDestroyed() {
        for (AbstractSubsystem subsystem : subsystems.values()) {
            subsystem.setActive(false);
        }
        for (AbstractConnector connector : connectors.values()) {
            //TODO:随机锁定/解锁某个关节的自由度？
            if (connector.attr.getImpactMultiplier() > 0) {

            }
        }
        if (level.isClientSide && !isDestroyed()) {
            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.destroyed"), 64f);
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(getPosition()), Vec3.ZERO,
                    (float) (1f + 0.2f * (Math.random() - 0.5f)),
                    1f);
        }
        super.setDestroyed();
    }

    public float getEquivalentMass() {
        return MassUtil.getEquivalentMass(this);
    }

    @NotNull
    public HitBox getHitBox(int contactPointIndex) {
        try {
            ChildCollisionShape[] children = this.collisionShape.listChildren();
            if (children.length >= contactPointIndex)
                return getHitBox(children[contactPointIndex].getShape().nativeId());
            else throw new IndexOutOfBoundsException();
        } catch (IndexOutOfBoundsException e) {
            MachineMax.LOGGER.error("No matching child shape of sub-part {}-{} found for contact point id: {}", part.name, name, contactPointIndex);
            return hitBoxes.values().iterator().next();
        }
    }

    @NotNull
    public HitBox getHitBox(long childShapeId) {
        try {
            return hitBoxes.get(attr.getHitBoxNames().get(childShapeId));
        } catch (NullPointerException e) {
            MachineMax.LOGGER.error("No hit box of sub-part {}-{} found for child shape id: {}", part.name, name, childShapeId);
            return hitBoxes.values().iterator().next();
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * 将三角形索引（碰撞检测结果）映射到对应的 HitBox id。
     * 不同 HitBox 具有独立穿透判定——击穿外装甲后仍可命中内部引擎等。
     */
    @Override
    @org.jetbrains.annotations.Nullable
    public String getPenetrationZoneId(com.jme3.bullet.collision.PhysicsCollisionObject body, int triangleIndex) {
        HitBox hitBox = getHitBox(triangleIndex);
        return hitBox != null ? hitBox.getAttr().getId() : null;
    }

    /**
     * 查询子形状是否为轮胎碰撞形状
     *
     * @param contactPointIndex 子形状ID
     * @return 是否为轮胎碰撞形状
     */
    public boolean isWheel(int contactPointIndex) {
        try {
            ChildCollisionShape[] children = this.collisionShape.listChildren();
            if (children.length >= contactPointIndex)
                return isWheel(children[contactPointIndex].getShape().nativeId());
            else throw new IndexOutOfBoundsException();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * 查询子形状是否为轮胎碰撞形状
     *
     * @param childShapeId 子形状ID
     * @return 是否为轮胎碰撞形状
     */
    public boolean isWheel(long childShapeId) {
        return attr.getIsWheelSurface().containsKey(childShapeId);
    }

    /**
     * 查询子形状是否为轮胎面
     *
     * @param contactPointIndex 子形状ID
     * @return 是否为轮胎面
     */
    public boolean isWheelSurface(int contactPointIndex) {
        try {
            ChildCollisionShape[] children = this.collisionShape.listChildren();
            if (children.length >= contactPointIndex)
                return attr.getWheelHalfWidths().containsKey(children[contactPointIndex].getShape().nativeId());
            else throw new IndexOutOfBoundsException();
        } catch (IndexOutOfBoundsException e) {
            return false;
        }
    }

    /**
     * 查询子形状是否为轮胎面
     *
     * @param childShapeId 子形状ID
     * @return 是否为轮胎面
     */
    public boolean isWheelSurface(long childShapeId) {
        return attr.getWheelHalfWidths().containsKey(childShapeId);
    }

    /**
     * 获取轮胎半径
     *
     * @param contactPointIndex 接触点序号
     * @return 轮胎半径
     */
    public float getWheelRadius(int contactPointIndex) {
        try {
            ChildCollisionShape[] children = this.collisionShape.listChildren();
            if (children.length >= contactPointIndex)
                return getWheelRadius(children[contactPointIndex].getShape().nativeId());
            else throw new IndexOutOfBoundsException();
        } catch (IndexOutOfBoundsException e) {
            return 0f;
        }
    }

    public float getWheelRadius(long childShapeId) {
        return attr.getWheelRadius().getOrDefault(childShapeId, 0f);
    }

    /**
     * 碰撞是否发生在轮胎碰撞体的有效范围内
     *
     * @param contactPointIndex 接触点序号
     * @param contactPoint      接触点
     * @return true则有效，false则无效
     */
    public boolean isEffectiveContact(int contactPointIndex, Vector3f contactPoint, boolean worldContact) {
        ChildCollisionShape[] children = this.collisionShape.listChildren();
        ChildCollisionShape child = children[contactPointIndex];
        long shapeId = child.getShape().nativeId();
        // 检查HitBox是否激活
        HitBox hitBox = getHitBox(shapeId);
        if (hitBox != null && !hitBox.isActive()) {
            return false;
        }
        if (isWheel(shapeId) && isWheelSurface(shapeId)) {
            return true;
//            float halfWidth = attr.getWheelHalfWidths().getOrDefault(shapeId, 0f);
//            if (worldContact) {
//                // 子形状 -> 刚体
//                Transform wheelToBody = child.copyTransform(null);
//                // 刚体 -> 世界
//                Transform bodyToWorld = body.getTransform(null);
//                // 子形状 -> 世界
//                Transform wheelToWorld = MyMath.combine(wheelToBody, bodyToWorld, null);
//                // 世界 -> 子形状
//                Transform worldToWheel = wheelToWorld.invert();
//                // 世界接触点 -> 子形状局部坐标
//                var localContactPoint =
//                        SparkMathKt.toVector3f(contactPoint).mulPosition(SparkMathKt.toMatrix4f(worldToWheel.toTransformMatrix()));
//                // X 轴即轮胎宽度方向
//                return Math.abs(localContactPoint.x) <= halfWidth;
//            } else {
//                // 刚体 -> 局部
//                Transform bodyToWheel = child.copyTransform(null).invert();
//                // 刚体接触点 -> 子形状局部坐标
//                var localContactPoint =
//                        SparkMathKt.toVector3f(contactPoint).mulPosition(SparkMathKt.toMatrix4f(bodyToWheel.toTransformMatrix()));
//                // X 轴即轮胎宽度方向
//                return Math.abs(localContactPoint.x) <= halfWidth;
//            }
        } else return true;
    }

    public Transform getLerpedLocatorWorldTransform(String locatorName, float partialTick) {
        return getLerpedLocatorWorldTransform(locatorName, new Transform(), partialTick);
    }

    public Transform getLerpedLocatorWorldTransform(String locatorName, Transform offset, float partialTick) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            Transform localTransform = MyMath.combine(offset, attr.getLocatorTransforms().get(locatorName), null);
            Transform pose = MyMath.combine(localTransform, PhysicsBodyExtensionKt.stateOf(body).getTransform(), null);
            Transform oldPose = MyMath.combine(localTransform, PhysicsBodyExtensionKt.stateOf(body).getLastTransform(), null);
            return SparkMathKt.lerp(oldPose, pose, partialTick);
        } catch (Exception e) {
            return SparkMathKt.lerp(PhysicsBodyExtensionKt.stateOf(body).getLastTransform(), PhysicsBodyExtensionKt.stateOf(body).getTransform(), partialTick);
        }
    }

    public Transform getLocatorWorldTransform(String locatorName) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            Transform localTransform = attr.getLocatorTransforms().getOrDefault(locatorName, new Transform());
            return MyMath.combine(localTransform, body.getTransform(null), null);
        } catch (Exception e) {
            return body.getTransform(null);
        }
    }

    public Transform getLocatorLocalTransform(String locatorName) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            return attr.getLocatorTransforms().getOrDefault(locatorName, new Transform());
        } catch (Exception e) {
            return new Transform();
        }
    }

    public Vector3f getLocatorWorldPos(String locatorName) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            return getLocatorWorldTransform(locatorName).getTranslation();
        } catch (Exception e) {
            return body.getTransform(null).getTranslation();
        }
    }

    public Vector3f getLocatorLocalPos(String locatorName) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            return getLocatorLocalTransform(locatorName).getTranslation();
        } catch (Exception e) {
            return new Vector3f();
        }
    }

    @Override
    public void setPosition(Vector3f position) {
        if (entity != null && !entity.isRemoved() && !updateLock) {
            entity.setPos(position.x, position.y, position.z);
        }
        super.setPosition(position);
    }

    /**
     * <p>获取零件的耐久度，如果部件内零件共享耐久度，则返回共享耐久度；否则返回自身耐久度。</p>
     * <p>This method returns the durability of the sub-part, taking into account whether the part shares durability with its sub-parts.</p>
     *
     * @return 耐久度 The durability of the sub-part.
     */
    @Override
    public float getDurability() {
        return (part.type.shareDurability ? part.getSharedDurability() : super.getDurability());
    }

    @Override
    public void setDurability(float durability) {
        float durabilityCap = this.getMaxDurability() * Math.max(part.getAssemblingProgress(), 0.05f);
        this.syncedData.set(DATA_DURABILITY_ID, Math.clamp(durability, 0.0F, durabilityCap));
    }

    /**
     * <p>获取零件的最大耐久度，如果部件内零件共享耐久度，则返回共享最大耐久度；否则返回自身最大耐久度。</p>
     * <p>This method returns the maximum durability of the sub-part, taking into account whether the part shares durability with its sub-parts.</p>
     *
     * @return 最大耐久度 The maximum durability of the sub-part.
     */
    @Override
    public float getMaxDurability() {
        return (part.type.shareDurability ? part.getSharedMaxDurability() : attr.durability);
    }

    /**
     * <p>获取本零件为部件共享耐久度的贡献。</p>
     * <p>This method returns the contribution of the part to the shared durability of its sub-parts.</p>
     *
     * @return 本零件为部件共享耐久度的贡献 The contribution of the part to the shared durability of its sub-parts.
     */
    public float getSharedMaxDurability() {
        return attr.durability;
    }

    @Override
    public SubPart getSubPart() {
        return this;
    }

    @Override
    public EnergyGrid getEnergyGrid() {
        return part.assembly != null
                ? getSubsystemController().getEnergyGrid()
                : null;
    }

    @NotNull
    @Override
    public SubsystemController getSubsystemController() {
        return part.assembly.getSubsystemController();
    }

    @NotNull
    @Override
    public Map<String, Float> getExtraMass() {
        return extraMassMap;
    }

    @Override
    public void onMassChange(float totalExtraMass) {
        // 在物理线程安全地更新刚体质量
        var physLevel = getPhysicsLevel();
        float newMass = getAttr().getMass() + totalExtraMass;
        if (physLevel != null) {
            physLevel.submitImmediateTask(PPhase.ALL, () -> {
                body.setMass(newMass);
                return null;
            });
        }
    }

    public Map<String, OBone> getBones() {
        return attr.getBones(part.variant);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (interactBoxes != null) {
            interactBoxes.updatePose();//同步刚体与交互判定区位置
        }
    }

    public Transform getLocalMassCenterTransform() {
        return getAttr().getMassCenterTransform();
    }

    /**
     * 轻量世界坐标查询——直接取 transform 的翻译分量，零矩阵分配。
     * 用于 LOD 系统的距离判断，避免构建完整 Matrix4f。
     */
    public Vec3 getRenderPosition(Number partialTicks) {
        var t = transform.getTranslation(); // JME Vector3f，字段直读，零分配
        return new Vec3(t.x, t.y, t.z);
    }

    /**
     * 获取模型坐标原点在世界坐标系下的位姿变换
     *
     * @param number 插值系数，0-1
     * @return 模型坐标原点在世界坐标系下的位姿变换，常用于渲染
     */
    public Matrix4f getRenderWorldPositionMatrix(@NotNull Number number) {
        return getWorldPositionMatrix(number).mul(SparkMathKt.toMatrix4f(getLocalMassCenterTransform().invert().toTransformMatrix()));
    }
}

