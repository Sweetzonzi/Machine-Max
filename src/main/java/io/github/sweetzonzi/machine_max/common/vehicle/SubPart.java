package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OBone;
import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.CollisionObjectEntity;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.AfMode;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.registry.MMDamageTypes;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HydrodynamicAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import io.github.sweetzonzi.machine_max.common.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBoxes;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractControllableSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartPaintPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.ShapeHelper;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DynamicUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import jme3utilities.math.MyMath;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.github.sweetzonzi.machine_max.util.mechanic.DynamicUtil.calculateSlipScale;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
@Getter
public class SubPart extends DestroyableRigidObject implements IAnimatable<SubPart>, ISubsystemHost, ISignalReceiver {
    //模型、动画与渲染
    public final ModelController modelController;
    public final AnimController animController;
    public String textureName;//当前使用的纹理的索引(用于切换纹理)
    @Nullable
    public MMPartEntity entity;//用于渲染模型以及和原版内容进行交互的的实体对象
    //游戏机制
    public final Part part;
    public String name;
    public final SubPartAttr attr;
    public Transform massCenterTransform = new Transform();
    public final ConcurrentHashMap<String, HitBox> hitBoxes = new ConcurrentHashMap<>();
    public final InteractBoxes interactBoxes;//交互判定
    public final HashMap<String, AbstractSubsystem> subsystems = HashMap.newHashMap(1);
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public static final ConcurrentMap<String, SignalChannel> signalInputChannels = new ConcurrentHashMap<>();
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();//部件内供Molang查询的信号
    public final ConcurrentMap<String, Object> variables = new ConcurrentHashMap<>();//部件存储的molang值
    //物理
    public final boolean GROUND_COLLISION_ONLY;//是否仅和零件之下的地面方块碰撞
    public final float stepHeight;
    public Vec3 projectedArea = null;
    public float bodyMinY = -99999;
    public HashSet<BlockPos> climbableBlocks = new HashSet<>();

    public SubPart(String name, Part part, SubPartAttr attr) {
        super(part.level, attr.getCollisionShape(part.variant), attr.mass);
        this.part = part;
        this.name = name;
        this.attr = attr;
        Map.Entry<String, ResourceLocation> texture = part.variant.getTextures().entrySet().iterator().next();
        this.textureName = texture.getKey();
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
        this.getModelController().setModel(new ModelIndex("part", part.variant.getModel()));
        this.getModelController().setTextureLocation(part.variant.getTexture(textureName));
        if (!attr.interactBoxes.isEmpty()) {
            this.interactBoxes = new InteractBoxes(this, attr.interactBoxes, attr.getInteractBoxShape(part.variant, "default"));
        } else this.interactBoxes = null;
        PhysicsBodyExtensionKt.setOwner(this.body, this);
        this.body.setSleepingThresholds(0.1f, 0.1f);
        this.body.setProtectGravity(true);
//        this.body.setGravity(getPhysicsLevel().getWorld().getGravity(null));
        if (part.getLevel().isClientSide()) this.body.setKinematic(true);
        Vector3f inverseInertia = new Vector3f();
        this.body.getInverseInertiaLocal(inverseInertia);
        if (inverseInertia.length() > 5) {
            MachineMax.LOGGER.error("{} ({})转动惯量异常: {}", name, part.variantName, body.getInverseInertiaLocal(null));
        }
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
        //各类回调
        PhysicsBodyExtensionKt.onCollideProcessed(this.body, event -> {
            var o1 = event.getO1();
            var o2 = event.getO2();
            var point1 = event.getO1Point();
            var point2 = event.getO2Point();
            long manifoldPointId = point1.getId();
            this.onContactProcessed(o1, o2, point1, point2, manifoldPointId);
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

    /**
     * 按给定的纹理名切换部件纹理
     * 可用于为拥有多个纹理的部件选择外观
     *
     * @param name 纹理名
     */
    public void switchTexture(String name) {
        if (part.variant.getTextures().size() == 1) return;
        this.textureName = name;
        this.getModelController().setTextureLocation(part.variant.getTexture(name));
        //同步客户端
        if (!getLevel().isClientSide() && part.vehicle != null)
            PacketDistributor.sendToPlayersInDimension((ServerLevel) getLevel(),
                    new PartPaintPayload(part.vehicle.uuid, part.uuid, this.name, this.textureName));
    }

    public void refreshPartEntity() {
        this.entity = new MMPartEntity(getLevel(), this);
        getLevel().addFreshEntity(this.entity);
    }

    public void onContactProcessed(PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, ManifoldPoint point1, ManifoldPoint point2, long manifoldPointId) {
        if (level.isClientSide() && !isActive()) return; // 忽略非激活客户端刚体
        PhysicsRigidBody other = (PhysicsRigidBody) o2;
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        Vector3f normal = new Vector3f();
        int hitBoxIndex, otherHitBoxIndex;
        Vector3f worldContactPoint = new Vector3f();
        Vector3f localContactPoint = new Vector3f(), otherLocalContactPoint = new Vector3f();
        hitBoxIndex = point1.getTriangleIndex();
        otherHitBoxIndex = point2.getTriangleIndex();
        point1.getPositionWorld(worldContactPoint);
        point1.getLocalPoint(localContactPoint);
        point2.getLocalPoint(otherLocalContactPoint);
        if (!isEffectiveContact(hitBoxIndex, worldContactPoint, true)) {
            ManifoldPoints.setAppliedImpulse(manifoldPointId, 0);
            ManifoldPoints.setDistance1(manifoldPointId, 500);
            return; // 忽略无效碰撞
        }
        //获取世界坐标下的碰撞点法线，由另一物体指向自身
        point2.getNormalWorld(normal);
        //计算相对接触速度
        Vector3f contactVel = MMMath.relPointWorldVel(
                localContactPoint,
                body.getPhysicsRotation(null),
                body.getLinearVelocity(null),
                body.getAngularVelocity(null));
        contactVel.subtractLocal((o2 instanceof PhysicsRigidBody) ? MMMath.relPointWorldVel(otherLocalContactPoint, other) : new Vector3f());
        //计算碰撞角度（法线与速度方向的夹角）
        float impactAngle = (float) Math.toDegrees(Math.acos(normal.dot(contactVel.normalize())));
        if (Float.isNaN(impactAngle)) impactAngle = 0; // 处理NaN情况
        //获取参与碰撞的碰撞箱
        HitBox hitBox = this.getHitBox(hitBoxIndex);
        //根据实际接触部位重设摩擦系数
        Vector3f friction = PhysicsHelperKt.toBVector3f(hitBox.attr.friction());
        if (!friction.equals(body.getAnisotropicFriction(null)) && !isWheel(hitBoxIndex))
            body.setAnisotropicFriction(friction, AfMode.basic); // 非轮子部件重设摩擦系数，轮胎另行处理逻辑
        if (hitBox.attr.rollingFriction() != body.getRollingFriction())
            body.setRollingFriction(hitBox.attr.rollingFriction());
        if (hitBox.attr.rollingFriction() != body.getSpinningFriction())
            body.setSpinningFriction(hitBox.attr.spinningFriction());
        if (hitBox.attr.restitution() != body.getRestitution())
            body.setRestitution(hitBox.attr.restitution());
        if (other.getCollisionGroup() == CollisionGroups.TERRAIN) {
            //与方块碰撞时
            this.onCollideWithTerrain(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, point1, point2, manifoldPointId);
        } else if (other.getCollisionGroup() == CollisionGroups.PHYSICS_BODY) {
            //与另一刚体碰撞时
            this.onCollideWithRigid(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
        } else if (other.getCollisionGroup() == CollisionGroups.PAWN) {
            //与实体碰撞时
            if (otherOwner instanceof Entity contactEntity && !(contactEntity instanceof CollisionObjectEntity)) {
                onCollideWithEntity(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
            }
        }
    }

    Vector3f tmpFront = new Vector3f();
    Vector3f tmpSide = new Vector3f();

    @Override
    protected void onCollideWithTerrain(PhysicsRigidBody other, Vector3f normal, Vector3f worldContactPoint, Vector3f localContactPoint, Vector3f otherLocalContactPoint, Vector3f contactVel, int hitBoxIndex, int otherHitBoxIndex, float impactAngle, ManifoldPoint point1, ManifoldPoint point2, long manifoldPointId) {
        super.onCollideWithTerrain(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, point1, point2, manifoldPointId);
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        if (otherOwner instanceof PhysicsChunkSection terrain) {
            other.shouldShowDebugBoxWhenNonColldeWith = true;
            //基本信息获取
            var hitBox = this.getHitBox(hitBoxIndex);
            var vel = this.getLinearVelocity();
            BlockPos blockPos = terrain.getBlockPosFromContactPoint(worldContactPoint, normal, ManifoldPoints.getDistance1(manifoldPointId));
            BlockPos relBlockPos = blockPos.subtract(terrain.getSectionPos().origin());
            if (relBlockPos.getX() < 0 || relBlockPos.getY() < 0 || relBlockPos.getZ() < 0 ||
                    relBlockPos.getX() > 15 || relBlockPos.getY() > 15 || relBlockPos.getZ() > 15) {
                ManifoldPoints.setDistance1(manifoldPointId, 500);//阻止接触约束计算
                return;//若方块不属于本区块，则不处理碰撞
            }
            SectionSnapshot.BlockSnapshot block = terrain.getBlockSnapshot(blockPos);
            if (block == null || terrain.isRemoved(blockPos)) {
                ManifoldPoints.setDistance1(manifoldPointId, 500);//阻止接触约束计算
                return;//若方块已被移除，则不处理碰撞
            }
            BlockState blockState = block.getState();
            float blockFriction = block.getFriction();
            float blockRollingFriction = block.getRollingFriction();
            float blockRestitution = block.getRestitution();
            float blockSlip = block.getSlip(); // 湿滑系数，0~1，0表示完全干燥，1表示完全湿滑
            //等效质量计算，考虑连接部件的影响
            float partMass = this.getEquivalentMass();
            //摩擦力修正相关计算
            float normalContactVel = contactVel.dot(normal); // 法线方向上的接触速度
            Vector3f slipVel = contactVel.subtract(normal.mult(normalContactVel)); // 滑移速度
            Vector3f wheelVel = MMMath.relPointExtraVelFromAngularVel(localContactPoint, body.getPhysicsRotation(null), body.getAngularVelocity(null));
            //获取前向和侧向
            normal.cross(getRightVector(), tmpFront);
            tmpFront.cross(normal, tmpSide);
            float slipAngle = (float) Math.atan2(tmpSide.dot(slipVel), tmpFront.dot(slipVel)); // 滑移角(rad)
            float moveVelLen = body.getLinearVelocity(null).length();
            float wheelVelLen = Math.abs(wheelVel.dot(tmpFront));
            float slipRatio = Math.abs(moveVelLen - wheelVelLen) / (Math.max(moveVelLen, wheelVelLen) + 0.1f); // 滑移率
            if (!level.isClientSide()) { // 服务端处理摩擦力修正
                float effectiveSlip = blockSlip * (1f - hitBox.attr.slipAdaptation()); // 有效湿滑强度
                float wetFactor = (1f - effectiveSlip) * (1f - effectiveSlip * slipRatio * 0.7f); // 湿滑衰减
                if (isWheel(hitBoxIndex) && isWheelSurface(hitBoxIndex)) { // 轮胎特殊处理
                    double cos2 = Math.cos(slipAngle) * Math.cos(slipAngle);
                    double sin2 = Math.sin(slipAngle) * Math.sin(slipAngle);
                    float angleDeg = (float) Math.toDegrees(Math.abs(slipAngle));
                    float s_angle = angleDeg / 90.0f; // 归一化到 [0, 1]
                    double muFront = hitBox.getMuFront() // 滑移率15%时摩擦系数达到峰值
                            * calculateSlipScale(slipRatio, 0.15f, 1.0f, 1.3f, 0.9f);
                    double muSide = hitBox.getMuSide() // 设定侧向在 12度达到峰值，且动摩擦衰减更剧烈(0.5f)
                            * calculateSlipScale(s_angle, 0.133f, 1.0f, 1.1f, 0.5f);
                    // 根据摩擦方向调整摩擦系数，越接近某个方向，实际摩擦系数越接近对应方向的摩擦系数
                    float scale = (float) (muFront * muSide / Math.sqrt(muFront * muFront * sin2 + muSide * muSide * cos2));
                    // 重设摩擦方向
                    Vector3f slipVelNorm = slipVel.normalize();
                    ManifoldPoints.setLateralFrictionDir1(manifoldPointId, slipVelNorm);
                    ManifoldPoints.setLateralFrictionDir2(manifoldPointId, normal.cross(slipVelNorm));
                    // 重设摩擦系数
                    ManifoldPoints.setCombinedFriction(manifoldPointId, Math.max(0.001f, body.getFriction() * scale * blockFriction * wetFactor));
                } else { // 一般物体直接重设摩擦系数
                    ManifoldPoints.setCombinedFriction(manifoldPointId, Math.max(0.001f, body.getFriction() * blockFriction * wetFactor));
                }
                ManifoldPoints.setCombinedRollingFriction(manifoldPointId, Math.max(0f, body.getRollingFriction() * blockRollingFriction));
            }
            //若是需要攀爬辅助处理的方块
            if (climbableBlocks.contains(blockPos)) {
                //重设碰撞法线方向
                normal = point1.getIndex() == 0 ? Vector3f.UNIT_Y : Vector3f.UNIT_Y.negate();
                ManifoldPoints.setNormalWorldOnB(manifoldPointId, normal);
                ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                ManifoldPoints.setAppliedImpulseLateral1(manifoldPointId, 0f);
                ManifoldPoints.setAppliedImpulseLateral2(manifoldPointId, 0f);
                if (attr.isClimbAssist()) {
                    // 人工设置一个小的侵入深度，法线向上，若侵入深度为0.05则期望每帧使部件上浮0.05*ERP的高度
//                    ManifoldPoints.setDistance1(manifoldPointId, -0.05f);
                    ManifoldPoints.setDistance1(manifoldPointId, Math.clamp(worldContactPoint.y - blockPos.getY() + 1, -0.03f, -0.006f));
                } else { //穿透深度设为正值代表分离，让物理引擎忽视该接触点的处理
                    ManifoldPoints.setDistance1(manifoldPointId, 500f);
                }
                return; //爬坡辅助的方块不参与后续碰撞处理
            }
            //调用子系统碰撞回调
            if (hitBox.subsystem != null) {
                hitBox.subsystem.onCollideWithBlock(
                        this.body, other, blockPos, blockState, contactVel, normal, worldContactPoint, impactAngle, hitBox, manifoldPointId
                );
                if (terrain.isRemoved(blockPos)) {
                    ManifoldPoints.setDistance1(manifoldPointId, 500);//阻止接触约束计算
                    return;//若方块已被移除，则不处理碰撞
                }
            }
            //根据碰撞速度、碰撞角、方块硬度和爆炸抗性，摧毁碰撞的方块，同时对自身造成伤害
            //TODO:配置文件开关冲撞可破坏方块
            //碰撞的方块可破坏时
            if (hitBox.attr.blockDamageFactor() > 0 && blockState.getDestroySpeed(part.level, blockPos) >= 0) {
                //计算碰撞法线方向上的速度(考虑冲量影响)
                float blockArmor = ArmorUtil.getBlockArmor(part.level, blockState, blockPos);
                float subPartArmor = hitBox.getRHA(this);
                double contactNormalSpeed = Math.abs(contactVel.dot(normal)) + ManifoldPoints.getAppliedImpulse(manifoldPointId) / body.getMass();
                float restitution = Math.clamp(body.getRestitution() * blockRestitution, 0f, 1f);//TODO:考虑二者护甲差距调整此系数，决定相加还是相乘
                ManifoldPoints.setCombinedRestitution(manifoldPointId, restitution);
                double contactEnergy = 0.5 * partMass * contactNormalSpeed * contactNormalSpeed * (1 - restitution);//此次碰撞损失的能量
                //TODO:根据硬度差距调整能量释放速度
                double blockDurability = DamageUtil.getMaxBlockDurability(EmptyBlockGetter.INSTANCE, blockState, BlockPos.ZERO);
                //方块有支撑时将强化其耐久度
                Vec3i supportBlockPos = MMMath.getClosestAxisAlignedVector(SparkMathKt.toVec3(normal.mult(-1)));
                SectionSnapshot.BlockSnapshot supportBlock = getPhysicsLevel().getTerrainManager().getBlockSnapshotAt(blockPos.offset(supportBlockPos));
                if (supportBlock != null) {
                    blockDurability += 0.5 * DamageUtil.getMaxBlockDurability(EmptyBlockGetter.INSTANCE, supportBlock.getState(), BlockPos.ZERO);
                }
                double blockEnergy = contactEnergy * subPartArmor / (subPartArmor + blockArmor);//方块吸收的碰撞能量
                double partEnergy = contactEnergy - blockEnergy;//部件吸收的碰撞能量
                if (hitBox.attr.blockDamageFactor() * blockEnergy > 250 * blockDurability) {
                    //能量能够一次摧毁则摧毁,计算额外冲量使部件减速
                    terrain.markRemoved(blockPos);
                    //被摧毁的方块掉落为物品的概率，方块吸收的碰撞能量恰好与耐久度相同时必定掉落，掉落率随能量增加而递减
                    double blockDropRate = Math.exp(1 - (hitBox.attr.blockDamageFactor() * blockEnergy / (250 * blockDurability)));
                    if (!level.isClientSide) {
                        SparkLevel.submitDeduplicatedTask(level, blockPos.toShortString(), PPhase.PRE, () -> level.destroyBlock(blockPos, Math.random() < blockDropRate));
                    }
                    //根据方块被破坏实际消耗的能量调整部件吸收的能量，但不全额作用为反冲量以提升操控流畅性
                    double actualPartEnergy = 0.2 * partEnergy * ((250 * blockDurability) / blockEnergy);
                    if (actualPartEnergy < 0 || Double.isNaN(actualPartEnergy)) actualPartEnergy = 0f;
                    double finalActualPartEnergy = actualPartEnergy;
                    //部件减速
                    ManifoldPoints.setDistance1(manifoldPointId, 500f);//阻止接触约束计算
                    ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);//重置默认冲量，采用计算结果
                    Vector3f aVel = body.getAngularVelocity(null);
                    Vector3f impulse = normal.mult((float) (Math.sqrt(2 * finalActualPartEnergy * partMass)));
                    Vector3f offset = worldContactPoint.subtract(body.getPhysicsLocation(null));
                    Matrix3f inertia = body.getInverseInertiaWorld(null);
                    body.setLinearVelocity(vel.add(impulse.mult(1f / body.getMass())));
                    Vector3f deltaOmega = inertia.mult(offset.cross(impulse), null);
                    body.setAngularVelocity(aVel.add(deltaOmega));
                    //对部件造成伤害
                    float partDamage = (float) (finalActualPartEnergy / 250);
                    DamageSource source = level.damageSources().flyIntoWall();
                    if (hitBox.modifyDamage(source, partDamage) > 1) {
                        PartDamageData data = new PartDamageData(source, null, normal, contactVel, worldContactPoint, hitBox);
                        onHurt(data, partDamage);
                    }
                    return;
                } else { //否则以三分之一的能量计算伤害，冲量交给物理引擎处理
                    // 与一个物体发生碰撞时会创建3个(4个?)碰撞点，因此在单点处理计算时只取部分能量用于计算伤害
                    //TODO:对方块累积伤害
                    //对部件造成伤害
                    float partDamage = (float) (0.2 * 0.33 * partEnergy / 250);
                    DamageSource source = level.damageSources().flyIntoWall();
                    if (hitBox.modifyDamage(source, partDamage) > 1) {
                        PartDamageData data = new PartDamageData(source, null, normal, contactVel, worldContactPoint, hitBox);
                        hitBox.modifyDamage(source, partDamage);
                        onHurt(data, partDamage);
                    }
                }
            }
            //通常粒子效果
            if (level.isClientSide()) {
                float speed = vel.length();
                Vector3f finalNormal = normal;
                SparkLevel.submitImmediateTask(level, PPhase.PRE, () -> {
                    if (speed > 10 || Math.random() < 1 - Math.exp(-0.5 * speed)) {
                        //飞溅草石
                        if (blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SAND) || blockState.is(BlockTags.SNOW)) {
                            if (Math.random() < Math.max(1f, 0.05f * speed))
                                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                                        worldContactPoint.x, worldContactPoint.y + 0.01f, worldContactPoint.z,
                                        contactVel.x * (1f + 0.2f * (Math.random() - 0.5f)),
                                        contactVel.y * (1f + 0.2f * (Math.random() - 0.5f)),
                                        contactVel.z * (1f + 0.2f * (Math.random() - 0.5f)));
                        }
                    }
                    if (speed > 2 && slipRatio > 0.3 && !climbableBlocks.contains(blockPos) && finalNormal.y > 0.97f && worldContactPoint.y - blockPos.getY() > -0.1f) {
                        // 漂移烟雾与音效
                        if (Math.random() < 0.5 * slipRatio)
                            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                                    worldContactPoint.x, worldContactPoint.y + 0.01f, worldContactPoint.z,
                                    contactVel.x * (0.03f + 0.02f * (Math.random() - 0.5f)),
                                    contactVel.y * (0.03f + 0.02f * (Math.random() - 0.5f)) + 0.01f,
                                    contactVel.z * (0.03f + 0.02f * (Math.random() - 0.5f)));
                        SparkLevel.submitDeduplicatedTask(level, part.uuid + "_" + name + "_slide_sound", PPhase.PRE, () -> {
                            level.playLocalSound(worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                                    blockState.getSoundType(part.level, blockPos, null).getStepSound(), SoundSource.BLOCKS,
                                    (float) (0.3f * (1f - Math.exp(-0.1 * (vel.length() - 2)))), 0.75f, false);
                        });
                    }
                });
            }
        }
    }

    @Override
    protected void onCollideWithRigid(PhysicsRigidBody other, Vector3f normal, Vector3f worldContactPoint, Vector3f
                                              localContactPoint, Vector3f otherLocalContactPoint, Vector3f contactVel, int hitBoxIndex, int otherHitBoxIndex,
                                      float impactAngle, long manifoldPointId) {
        super.onCollideWithRigid(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        if (otherOwner instanceof SubPart otherSubPart) {
            var hitBox = this.getHitBox(hitBoxIndex);
            //与零件碰撞时
            HitBox otherHitBox = otherSubPart.getHitBox(otherHitBoxIndex);
            //调用子系统碰撞回调
            if (hitBox.subsystem != null) {
                hitBox.subsystem.onCollideWithPart(
                        this.body, other, contactVel, normal, worldContactPoint, impactAngle, hitBox, otherHitBox, manifoldPointId
                );
            }
            //不处理速度过小的碰撞
            if (contactVel.length() < 2f) {
                return;
            }
            // 计算碰撞法线方向上的相对速度
            float contactNormalVel = contactVel.dot(normal);
            // 基于双方材质属性重设碰撞恢复系数
            float restitution = (float) Math.sqrt(body.getRestitution() * other.getRestitution());
            ManifoldPoints.setCombinedRestitution(manifoldPointId, restitution);
            // 计算给对方施加的速度变化
            float deltaVel = (1 + restitution) * contactNormalVel * this.getEquivalentMass() / (this.getEquivalentMass() + otherSubPart.getEquivalentMass());
            //基于能量对对方部件造成伤害
            float partDamage = 0.0005f * deltaVel * deltaVel * body.getMass();
            DamageSource source = level.damageSources().source(MMDamageTypes.PART_COLLISION);
            if (otherHitBox.modifyDamage(source, partDamage) > 1) {
                PartDamageData data = new PartDamageData(level.damageSources().source(MMDamageTypes.PART_COLLISION), null, normal, contactVel, worldContactPoint, hitBox);
                otherSubPart.onHurt(data, partDamage);
            }
        }
    }

    @Override
    protected void onCollideWithEntity(PhysicsRigidBody other, Vector3f normal, Vector3f
                                               worldContactPoint, Vector3f localContactPoint, Vector3f otherLocalContactPoint, Vector3f contactVel,
                                       int hitBoxIndex, int otherHitBoxIndex, float impactAngle, long manifoldPointId) {
        super.onCollideWithEntity(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
        var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
        if (otherOwner instanceof LivingEntity livingEntity && !livingEntity.isRemoved() && !livingEntity.isDeadOrDying() && !livingEntity.hasImpulse && !(livingEntity.getVehicle() instanceof MMPartEntity)) {//不处理相对速度不足的碰撞
            var hitBox = this.getHitBox(hitBoxIndex);
            var vel = this.getLinearVelocity();
            //调用子系统碰撞回调
            if (hitBox.subsystem != null) {
                hitBox.subsystem.onCollideWithEntity(
                        this.body, other, contactVel, normal, worldContactPoint, impactAngle, hitBox, manifoldPointId
                );
            }
            //不处理速度过小的碰撞
            if (contactVel.subtract(PhysicsHelperKt.toBVector3f(livingEntity.getDeltaMovement().scale(20))).length() < 2f) {
                return;
            }
            float contactNormalSpeed = vel.dot(normal);//直接取接触点碰撞速度似乎不准确
            //计算并分配碰撞能量
            double entityMass = MassUtil.getEntityMass(livingEntity);
            double partMass = body.getMass();
            for (AbstractConnector connector : this.connectors.values()) {
                if (connector.hasPart())
                    partMass += 0.3 * connector.attachedConnector.subPart.body.getMass();
            }
            partMass += 0.05 * (part.vehicle.totalMass - body.getMass());
            float restitution = (float) Math.sqrt(body.getRestitution());
            double miu = (entityMass * partMass / (partMass + entityMass));
            double contactEnergy = 0.5 * miu * contactNormalSpeed * contactNormalSpeed * (1 - restitution * restitution);
            float impulse = (float) miu * (1 + restitution) * contactNormalSpeed;
            Vector3f impulseVec = normal.mult(impulse);
            //部件伤害
            float partDamage = (float) (0.2 * contactEnergy * miu / (250 * partMass));
            DamageSource source = level.damageSources().flyIntoWall();
            if (hitBox.modifyDamage(source, partDamage) > 1) {
                PartDamageData data = new PartDamageData(level.damageSources().source(DamageTypes.FLY_INTO_WALL, livingEntity),
                        null, normal, vel, worldContactPoint, hitBox);
                onHurt(data, partDamage);
            }
            //部件减速
            getPhysicsLevel().submitDeduplicatedTask(part.uuid + "_" + name + "_entity_impulse", PPhase.PRE, () -> {
                body.applyImpulse(impulseVec.mult(-0.3f), worldContactPoint.subtract(body.getPhysicsLocation(null)));
                return null;
            });
            //实体击退与伤害
            other.setLinearVelocity(other.getLinearVelocity(null).add(impulseVec.mult((float) (1f / entityMass))));
            SparkLevel.submitImmediateTask(level, PPhase.PRE, () -> {
                float damage = (float) (contactEnergy * miu / (250 * entityMass));
                if (damage > 1) {
                    if (!level.isClientSide) {
                        livingEntity.hurt(level.damageSources().source(DamageTypes.FLY_INTO_WALL, this.getEntity()), damage);
                    }
                    level.playSound(null, worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                            SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.AMBIENT, 1f, 1f);
                }
                livingEntity.setDeltaMovement(SparkMathKt.toVec3(impulseVec.mult((float) (0.05 / entityMass)).add(0, 0.1f, 0)));
            });
        }
    }

    @SubscribeEvent
    public static void onPreCollision(NeedsCollisionEvent event) {
        var ownerA = PhysicsBodyExtensionKt.getOwner(event.getPcoA());
        var ownerB = PhysicsBodyExtensionKt.getOwner(event.getPcoB());
        if (ownerA == null || ownerB == null) return;
        if (ownerA.getPhysicsLevel().getMcLevel().isClientSide()) {
            if (ownerA instanceof SubPart subPart && !subPart.isActive()) {
                event.setShouldCollide(false);
                return;
            } else if (ownerB instanceof SubPart subPart && !subPart.isActive()) {
                event.setShouldCollide(false);
                return;
            }
        }
        //同载具部件不发生碰撞
        if (ownerA instanceof SubPart subPartA && ownerB instanceof SubPart subPartB) {
            if (subPartA.part.vehicle instanceof VehicleCore vehicleA && subPartB.part.vehicle instanceof VehicleCore vehicleB) {
                if (vehicleA == vehicleB) {
                    event.setShouldCollide(false);
                    return;
                }
            }
        }
        //载具不与乘客发生碰撞
        AbstractControllableSubsystem sub;
        if (ownerA instanceof SubPart subPart && ownerB instanceof LivingEntity livingEntity) {
            sub = ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem();
            if (sub != null && sub.getOwner().getSubPart().getPart().getVehicle() == subPart.part.vehicle) {
                event.setShouldCollide(false);
            }
        } else if (ownerB instanceof SubPart subPart && ownerA instanceof LivingEntity livingEntity) {
            sub = ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem();
            if (sub != null && sub.getOwner().getSubPart().getPart().getVehicle() == subPart.part.vehicle) {
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
            var animSet = OAnimationSet.getORIGINS().get(new ModelIndex("part", part.variant.getAnimations()));
            if (!animController.isPlayingAnim() && animSet != null && !animSet.getAnimations().isEmpty()) {
                for (Map.Entry<String, OAnimation> entry : animSet.getAnimations().entrySet()) {
                    String name = entry.getKey();
                    var animInstance = new AnimInstance(this, new AnimIndex(new ModelIndex("part", part.variant.getAnimations()), name));
                    animInstance.enter();
                }
            }
            animController.tick();
        }
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
                                    sp.part.vehicle == this.part.vehicle &&
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
        if (!level.isClientSide() && body.isActive() && attr.blockCollision == SubPartAttr.BlockCollisionType.GROUND) {
            bodyMinY = ShapeHelper.getShapeMinY(this.body, 0.1f);
            //遍历范围内的方块
            AABB aabb = SparkMathKt.toAABB(PhysicsBodyExtensionKt.stateOf(this.body).getCachedBoundingBox())
                    .expandTowards(new Vec3(tmpWorldVel.x, 0, tmpWorldVel.z).scale(0.1f));
            int minX = (int) Math.floor(aabb.minX);
            int minZ = (int) Math.floor(aabb.minZ);
            int maxX = (int) Math.ceil(aabb.maxX);
            int maxZ = (int) Math.ceil(aabb.maxZ);
            float y0 = bodyMinY + 0.05f;
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos currentPos = new BlockPos(x, (int) Math.floor(y0), z);
                    SectionSnapshot.BlockSnapshot blockSnapshot = getPhysicsLevel().terrainManager.getBlockSnapshotAt(currentPos);

                    if (blockSnapshot != null) {
                        BlockState blockState = blockSnapshot.getState();
                        float blockHeight = getPhysicsLevel().getBlockShapeManager().getCollisionShape(blockState)
                                .boundingBoxWithoutRecalculate(Vector3f.ZERO, Matrix3f.IDENTITY, null).getYExtent() * 2;
                        float terrainHeight = blockHeight + currentPos.getY();

                        if (terrainHeight > y0 && terrainHeight < bodyMinY + attr.stepHeight) {
                            float height = terrainHeight - bodyMinY; // 部件最低点与地形的高度差
                            Set<BlockPos> noCollisionBlocks = new HashSet<>();
                            // 向上遍历检查连续方块
                            BlockPos highestBlockPos = currentPos;
                            while (height <= stepHeight) {
                                noCollisionBlocks.add(highestBlockPos);
                                SectionSnapshot.BlockSnapshot higherSnapshot = getPhysicsLevel().terrainManager.getBlockSnapshotAt(highestBlockPos);
                                if (higherSnapshot == null) break;

                                BlockState higherState = higherSnapshot.getState();
                                float higherBlockHeight = getPhysicsLevel().getBlockShapeManager().getCollisionShape(higherState)
                                        .boundingBoxWithoutRecalculate(Vector3f.ZERO, Matrix3f.IDENTITY, null).getYExtent() * 2;
                                float higherTerrainHeight = higherBlockHeight + highestBlockPos.getY();
                                height = higherTerrainHeight - y0;
                                highestBlockPos = highestBlockPos.above();
                            }

                            // 根据高度判断是否可攀爬
                            if (height <= stepHeight) {
                                climbableBlocks.addAll(noCollisionBlocks);
                                Vector3f pos = body.getPhysicsLocation(null);
                                int partX = (int) Math.floor(pos.x);
                                int partZ = (int) Math.floor(pos.z);
                                if (attr.climbAssist && partX == x && partZ == z) {
                                    // 在部件重心所处方块柱施加额外攀爬辅助力
                                    if (height > 0 && height <= stepHeight) {
                                        var horizonVel = Math.sqrt(tmpWorldVel.x * tmpWorldVel.x + tmpWorldVel.z * tmpWorldVel.z);
                                        var speedFactor = 0.7 * Math.exp(-0.25 * horizonVel) + 0.3;
                                        var ang = Math.max(0, Math.atan2(tmpWorldVel.y, horizonVel));
                                        var tgtAng = speedFactor * Math.atan2(height, 1) + (1 - speedFactor) * ang;
                                        float extraVel = (float) Math.max(-5, Math.max(Math.sin(tgtAng) * tmpWorldVel.length(), 2f * speedFactor) - tmpWorldVel.y);

                                        if (!(extraVel <= 0 && tmpWorldVel.y < 0)) {
                                            float horizontalVelScale = (float) Math.max(0, (Math.cos(ang) - Math.cos(tgtAng)));
//                                            body.applyCentralImpulse(new Vector3f(
//                                                    -horizontalVelScale * tmpWorldVel.x,
//                                                    extraVel,
//                                                    -horizontalVelScale * tmpWorldVel.z).mult(0.5f * body.getMass()));
                                        }
                                    }
                                }
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

    /**
     * 实际处理伤害
     *
     * @param data
     * @param damage
     * @return 伤害是否被正常处理
     */
    public boolean onHurt(PartDamageData data, float damage) {
        SubPartDamageEvent.Pre event = new SubPartDamageEvent.Pre(this, data, damage);
        if (!NeoForge.EVENT_BUS.post(event).isCanceled()) {
            DamageSource source = event.getData().source();
            damage = event.getDamageAmount();
            HitBox hitBox = data.hitBox();
            Vector3f normal = data.normal();
            Vector3f worldContactPoint = data.worldContactPoint();
            Vector3f worldContactSpeed = data.worldContactSpeed();
            Vec3 sourcePos = source.getSourcePosition();
            if (sourcePos == null)
                sourcePos = SparkMathKt.toVec3(PhysicsBodyExtensionKt.stateOf(body).getTransform().getTranslation());
            Vec3 finalSourcePos = sourcePos;
            float armor = hitBox.getRHA(this);
            float armorPenetration;
            //击退处理与特殊逻辑
            if (!source.is(MMTags.HAS_PEN_DEPTH)) {//原版伤害处理
                //冲击效果 (部件间的冲击交由物理引擎处理)
                if (!level.isClientSide() && !source.is(MMDamageTypes.PART_COLLISION)) {
                    float knockBack = (float) (Math.log10(Math.max(1.01, 10 * Math.sqrt(damage / getMaxDurability()))) * 250f);//伤害转化为动量，使用log函数以使冲量与部件耐久匹配
                    if (source.getDirectEntity() != null && source.getWeaponItem() != null) {//应用附魔等效果调整击退力度
                        knockBack *= EnchantmentHelper.modifyKnockback((ServerLevel) level, source.getWeaponItem(), source.getDirectEntity(), source, 1.0f);
                    }
                    if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION))
                        knockBack *= 15.0f;
                    float finalKnockBack = knockBack;
                    SparkLevel.getPhysicsLevel(level).submitImmediateTask(PPhase.PRE, () -> {//施加动量
                        part.vehicle.activate();
                        this.body.applyImpulse(worldContactSpeed.normalize().mult(finalKnockBack), worldContactPoint.subtract(this.body.getPhysicsLocation(null)));
                        return null;
                    });
                }
                //换算穿深
                armorPenetration = hitBox.modifyPiercing(source, damage);
            } else {//甲弹对抗处理
                //获取穿深
                try {
                    armorPenetration = hitBox.modifyPiercing(source, damage);// TODO: 研究一下Key是怎么用的,换成来自投射物的穿深数据
//                armorPenetration = (float) source.getExtraData().getBlackBoard().getStorage().getOrDefault(new Key<>("armor_pierce", Float.class), 0f);
                } catch (Exception e) {
                    armorPenetration = damage / 2f;
                    MachineMax.LOGGER.warn("{}受到的伤害不包含穿甲值信息", this.part.name);
                }
            }
            //计算冲击对连接点结构完整性的伤害
            float impactDamage = hitBox.modifyImpact(source, damage);
            //分配冲击至连接点
            distributeDamageImpactToConnectors(impactDamage, worldContactPoint);
            //甲弹对抗相关处理
            if (hitBox.hasAngleEffect()) armorPenetration *= -normal.dot(worldContactSpeed.normalize());//按照设置考虑入射角影响
            //击穿判定
            if (armorPenetration > armor || hitBox.hasUnPenetrateDamage()) {
                float subPartDamage = hitBox.modifyDamage(source, damage);
                if (armorPenetration < armor)//未击穿且有未击穿伤害时按照设置造成部分伤害
                    subPartDamage *= (float) Math.pow(armorPenetration / armor, hitBox.getUnPenetrateDamageFactor());
                //对部件造成伤害
                if (!level.isClientSide()) accumulateDamage(subPartDamage, data);
                else {
                    SparkLevel.submitImmediateTask(level, PPhase.ALL, () -> {
                        //TODO:播放击穿音效特效
                    });
                }
            } else if (level.isClientSide()) {
                float finalDamage = damage;
                SparkLevel.submitImmediateTask(level, PPhase.ALL, () -> {
                    //播放命中未击穿音效
                    SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.no_pen"), 64f);
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, finalSourcePos, Vec3.ZERO,
                            (float) ((2 - Math.min(7f, finalDamage) / 7f) * (1f + 0.2f * (Math.random() - 0.5f))),
                            0.1f + 0.4f * Math.min(7f, finalDamage) / 7f);
                    //添加粒子
                    var pos = SparkMathKt.toVec3(worldContactPoint);
//                for (int i = 0; i < 3; i++) {
//                    var dir = normal.mult(0.3f).add(new Vector3f(
//                            (float) (Math.random() - 0.5f),
//                            (float) (Math.random() - 0.5f),
//                            (float) (Math.random() - 0.5f)).mult(0.1f));
//                    level.addParticle(ParticleTypes.FIREWORK, pos.x, pos.y, pos.z, dir.x, dir.y, dir.z);
//                }
                });
            }
            return true; //返回true表示命中，且伤害已被处理
        }
        return false; //返回false表示伤害已被取消
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
            if (!connector.hasPart() || connector.getImpactMultiplier() == 0) continue; // 仅有连接且可破坏的连接点参与分配
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
            // 修理零件
            //TODO: 传递修复至载具
            float repairAmount = Math.min(Math.max(amount, 0), getMaxDurability() - getDurability());
            float subsystemsRepairAmount = Math.max(subSystemAmount, 0);
            float connectorsRepairAmount = Math.max(connectorAmount, 0);
            setDurability(getDurability() + repairAmount);
            // 修理子系统
            for (AbstractSubsystem subsystem : subsystems.values()) {
                if (subsystemsRepairAmount <= 0) break;
                if (subsystem.getDurability() < subsystem.getMaxDurability()) {
                    float subSystemRepairAmount = Math.min(subsystemsRepairAmount, subsystem.getMaxDurability() - subsystem.getDurability());
                    subsystem.setDurability(subsystem.getDurability() + subSystemRepairAmount);
                    subsystemsRepairAmount -= subSystemRepairAmount;
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
                Pair<Float, PartDamageData> pair = accumulatedDamage.poll();
                float damage = pair.getFirst();
                PartDamageData data = pair.getSecond();
                SubPartDamageEvent.Pre event = new SubPartDamageEvent.Pre(this, data, damage);
                //向子系统发送伤害事件，对子系统造成伤害
                if (data.hitBox().getSubsystem() != null) {
                    data.hitBox().getSubsystem().onHurt(event);
                }
                if (!event.isCanceled()) { // 若伤害未被子系统取消
                    // 广播事件
                    NeoForge.EVENT_BUS.post(new SubPartDamageEvent.Post(this, data, damage));
                    totalDamage += damage;
                    soundPos = SparkMathKt.toVec3(data.worldContactPoint());
                }
            }
            setDurability(Math.clamp(getDurability() - totalDamage, 0, getMaxDurability()));
            //TODO:对载具造成伤害
            //发包同步部件状态
            syncToClient();
            //播放音效
            if (totalDamage > 0) {
                SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.penetrate"), 64f);
                SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, soundPos, Vec3.ZERO,
                        (float) ((2 - 2 * Math.min(0.5f * getMaxDurability(), totalDamage) / getMaxDurability()) * (1f + 0.2f * (Math.random() - 0.5f))),
                        0.2f + 0.8f * 2 * Math.min(0.5f * getMaxDurability(), totalDamage) / getMaxDurability());
            }
        }
    }

    protected void onDestroyed() {
        super.onDestroyed();
        for (AbstractSubsystem subsystem : subsystems.values()) {
            subsystem.setActive(false);
        }
        for (AbstractConnector connector : connectors.values()) {
            //TODO:随机锁定/解锁某个关节的自由度？
            if (connector.attr.impactMultiplier() > 0) {

            }
        }
        if (level.isClientSide) {
            SoundEvent sound = SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.destroyed"), 64f);
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(getTransform().getTranslation()), Vec3.ZERO,
                    (float) (1f + 0.2f * (Math.random() - 0.5f)),
                    1f);
        }
    }

    public float getEquivalentMass() {
        float partMass = body.getMass();
        for (AbstractConnector connector : this.connectors.values()) {
            if (connector.hasPart())
                partMass += (0.3f * connector.attachedConnector.subPart.body.getMass());
        }
        partMass += 0.05f * (part.vehicle.totalMass - body.getMass());
        return partMass;
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
        if (isWheel(shapeId) && isWheelSurface(shapeId)) {
            float halfWidth = attr.getWheelHalfWidths().getOrDefault(shapeId, 0f);
            if (worldContact) {
                // 子形状 -> 刚体
                Transform wheelToBody = child.copyTransform(null);
                // 刚体 -> 世界
                Transform bodyToWorld = body.getTransform(null);
                // 子形状 -> 世界
                Transform wheelToWorld = MyMath.combine(wheelToBody, bodyToWorld, null);
                // 世界 -> 子形状
                Transform worldToWheel = wheelToWorld.invert();
                // 世界接触点 -> 子形状局部坐标
                var localContactPoint =
                        SparkMathKt.toVector3f(contactPoint).mulPosition(SparkMathKt.toMatrix4f(worldToWheel.toTransformMatrix()));
                // X 轴即轮胎宽度方向
                return Math.abs(localContactPoint.x) <= halfWidth;
            } else {
                // 刚体 -> 局部
                Transform bodyToWheel = child.copyTransform(null).invert();
                // 刚体接触点 -> 子形状局部坐标
                var localContactPoint =
                        SparkMathKt.toVector3f(contactPoint).mulPosition(SparkMathKt.toMatrix4f(bodyToWheel.toTransformMatrix()));
                // X 轴即轮胎宽度方向
                return Math.abs(localContactPoint.x) <= halfWidth;
            }
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
        if (entity != null && !entity.isRemoved()) entity.setPos(position.x, position.y, position.z);
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
        return getDurabilityRaw() * (0.05f + 0.95f * part.getAssemblingProgress());
    }

    public float getDurabilityRaw() {
        return (part.type.shareDurability ? part.getSharedDurability() : super.getDurability());
    }

    @Override
    public void setDurability(float durability) {
        this.syncedData.set(DATA_DURABILITY_ID, Math.clamp(durability, 0.0F, this.getMaxDurability()));
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
    public ConcurrentMap<String, SignalChannel> getSignalInputChannels() {
        return signalInputChannels;
    }

    @Override
    public SubPart getAnimatable() {
        return this;
    }

    @Nullable
    @Override
    public Level getAnimLevel() {
        return getLevel();
    }

    @Override
    public SubPart getSubPart() {
        return this;
    }

    @NotNull
    @Override
    public ModelIndex getDefaultModelIndex() {
        return new ModelIndex("part", part.variant.getModel());
    }

    public Map<String, OBone> getBonesToRender() {
        return attr.getBonesToRender(part.variant);
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (interactBoxes != null) {
            interactBoxes.updatePose();//同步刚体与交互判定区位置
        }
    }

}
