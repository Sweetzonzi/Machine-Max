package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimation;
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionObjectEntity;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.sound.SpreadingSoundHelper;
import cn.solarmoon.spark_core.util.*;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.AfMode;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HydrodynamicAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBoxes;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.SubPartDataSyncPayload;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartPaintPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import io.github.sweetzonzi.machine_max.util.ShapeHelper;
import io.github.sweetzonzi.machine_max.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.DynamicUtil;
import io.github.sweetzonzi.machine_max.util.mechanic.MassUtil;
import jme3utilities.math.MyMath;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
@Getter
public class SubPart extends DestroyableRigidObject implements IAnimatable<SubPart>, ISubsystemHost, ISignalReceiver {
    //模型、动画与渲染
    public final ModelController modelController;
    public final AnimController animController;
    public int textureIndex;//当前使用的纹理的索引(用于切换纹理)
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
    public final ConcurrentMap<String, SignalChannel> signalChannels = new ConcurrentHashMap<>();//部件内共享的信号
    public final ConcurrentMap<String, Object> signalStorage = new ConcurrentHashMap<>();//部件内供Molang查询的信号
    //物理
    public final boolean GROUND_COLLISION_ONLY;//是否仅和零件之下的地面方块碰撞
    public final float stepHeight;
    public Vec3 projectedArea = null;
    public float bodyMinY = -99999;
    public HashSet<BlockPos> climbableBlocks = new HashSet<>();

    public SubPart(String name, Part part, SubPartAttr attr) {
        super(part.level, attr.getCollisionShape("default"), attr.mass);
        this.part = part;
        this.name = name;
        this.attr = attr;
        this.setDurability(getMaxDurability());
        this.modelController = new ModelController(this);
        this.animController = new AnimController(this);
        this.getModelController().setModel(new ModelIndex("part", attr.getModel("default")));
        this.getModelController().setTextureLocation(attr.getTextures("default").get(textureIndex % attr.getTextures("default").size()));
        if (!attr.interactBoxes.isEmpty()) {
            this.interactBoxes = new InteractBoxes(this, attr.interactBoxes, attr.getInteractBoxShape("default"));
        } else this.interactBoxes = null;
        PhysicsBodyExtensionKt.setOwner(this.body, this);
        this.body.setSleepingThresholds(0.1f, 0.1f);
        this.body.setProtectGravity(true);
        this.body.setGravity(getPhysicsLevel().getWorld().getGravity(null));
        if (part.getLevel().isClientSide()) this.body.setKinematic(true);
        Vector3f inverseInertia = new Vector3f();
        this.body.getInverseInertiaLocal(inverseInertia);
        //TODO:检查为什么从保存的文件加载时有概率获得一个不正确的转动惯量
        if (inverseInertia.length() > 5) {
            MachineMax.LOGGER.error("{} ({})转动惯量异常: {}", name, part.variant, body.getInverseInertiaLocal(null));
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
        PhysicsBodyExtensionKt.onPrePhysicsTick(this.body, event -> {
            this.prePhysicsTick();
            return null;
        });
        PhysicsBodyExtensionKt.onTick(this.body, event -> {
            this.tick();
            return null;
        });
        PhysicsBodyExtensionKt.onPostPhysicsTick(this.body, event -> {
            this.postPhysicsTick();
            return null;
        });
    }

    public void addToLevel() {
        getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
            if (body.isInWorld()) return null;
            getPhysicsLevel().getWorld().addCollisionObject(body);
            return null;
        });
        for (AbstractConnector connector : connectors.values()) {
            if (connector.hasPart())
                connector.addToLevel();
        }
    }

    public void destroy() {
        super.destroy();
        subsystems.forEach((name, subsystem) -> subsystem.onDetach());
        subsystems.clear();
        for (AbstractConnector connector : connectors.values()) {
            connector.destroy();
        }
        if (body.isInWorld()) {
            PhysicsBodyExtensionKt.setOwner(body, null);
            PhysicsBodyExtensionKt.removePhysicsBody(getLevel(), this.body);
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
     * 按给定的纹理索引切换部件纹理
     * 可用于为拥有多个纹理的部件选择外观
     *
     * @param index 纹理索引
     */
    public void switchTexture(int index) {
        if (attr.getTextures("default").size() == 1) return;
        this.textureIndex = index % attr.getTextures("default").size();
        this.getModelController().setTextureLocation(attr.getTextures("default").get(textureIndex));
        //同步客户端
        if (!getLevel().isClientSide() && part.vehicle != null)
            PacketDistributor.sendToPlayersInDimension((ServerLevel) getLevel(),
                    new PartPaintPayload(part.vehicle.uuid, part.uuid, name, this.textureIndex));
    }

    public void refreshPartEntity() {
        this.entity = new MMPartEntity(getLevel(), this);
        getLevel().addFreshEntity(this.entity);
    }

    public void onContactProcessed(PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, ManifoldPoint point1, ManifoldPoint point2, long manifoldPointId) {
        //TODO:拆分为多个简单方法以方便子类修改并提升可读性
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
        //获取世界坐标下的碰撞点法线，由另一物体指向自身
        point2.getNormalWorld(normal);
        //计算相对接触速度
        Vector3f contactVel = MMMath.relPointWorldVel(localContactPoint, body.getPhysicsRotation(null), getLinearVelocity(), getAngularVelocity());
        contactVel.subtractLocal((o2 instanceof PhysicsRigidBody) ? MMMath.relPointWorldVel(otherLocalContactPoint, other) : new Vector3f());
        //计算碰撞角度（法线与速度方向的夹角）
        float impactAngle = (float) Math.toDegrees(Math.acos(normal.dot(contactVel.normalize())));
        if (Float.isNaN(impactAngle)) impactAngle = 0; // 处理NaN情况
        //获取参与碰撞的碰撞箱
        HitBox hitBox = this.getHitBox(hitBoxIndex);
        //根据实际接触部位重设摩擦系数
        Vector3f friction = PhysicsHelperKt.toBVector3f(hitBox.attr.friction());
        if (!friction.equals(body.getAnisotropicFriction(null)))
            body.setAnisotropicFriction(friction, AfMode.basic);
        if (hitBox.attr.rollingFriction() != body.getRollingFriction())
            body.setRollingFriction(hitBox.attr.rollingFriction());
        if (hitBox.attr.rollingFriction() != body.getSpinningFriction())
            body.setSpinningFriction(hitBox.attr.spinningFriction());
        if (hitBox.attr.restitution() != body.getRestitution())
            body.setRestitution(hitBox.attr.restitution());
        if (other.getCollisionGroup() == CollisionGroups.TERRAIN) {
            //与方块碰撞时
            this.onCollideWithTerrain(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
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


    @Override
    protected void onCollideWithTerrain(PhysicsRigidBody other, Vector3f normal, Vector3f worldContactPoint, Vector3f localContactPoint, Vector3f otherLocalContactPoint, Vector3f contactVel, int hitBoxIndex, int otherHitBoxIndex, float impactAngle, long manifoldPointId) {
        super.onCollideWithTerrain(other, normal, worldContactPoint, localContactPoint, otherLocalContactPoint, contactVel, hitBoxIndex, otherHitBoxIndex, impactAngle, manifoldPointId);
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
            float blockSlip = block.getSlip();
            //等效质量计算，考虑连接部件的影响
            double partMass = body.getMass();
            for (AbstractConnector connector : this.connectors.values()) {
                if (connector.hasPart())
                    partMass += (0.3 * connector.attachedConnector.subPart.body.getMass());
            }
            partMass += 0.05 * (part.vehicle.totalMass - body.getMass());
            //摩擦力修正
            float slip = (float) 1 - (blockSlip * (1 - hitBox.attr.slipAdaptation()));//潮湿与打滑带来的修正系数
            if (contactVel.length() > 1f && impactAngle > 60f && impactAngle < 120f) {//打滑时
                slip = (float) (Math.pow(slip, 0.5 * (contactVel.length() - 1)) * 0.9f);//根据打滑情况额外降低摩擦系数
                //TODO:漂移音效，摩擦力应先上升后下降
            }
            //重设摩擦系数
            float bodyFriction = body.getFriction();
            ManifoldPoints.setCombinedFriction(manifoldPointId, Math.max(0.001f, bodyFriction * blockFriction * slip));
            ManifoldPoints.setCombinedRollingFriction(manifoldPointId, Math.max(0f, body.getRollingFriction() * blockRollingFriction));
            //若是需要攀爬辅助处理的方块
            if (climbableBlocks.contains(blockPos)) {
                if (attr.isClimbAssist()) {
                    Vector3f frictionTorque = contactVel.normalize()
                            .mult((float) (1 - Math.exp(-0.05 * Math.abs(contactVel.lengthSquared()))))
                            .mult(ManifoldPoints.getCombinedFriction(manifoldPointId))
                            .mult((float) (partMass / 3 * ManifoldPoints.getDistance1(manifoldPointId)));
                    Vector3f frictionImpulse = new Vector3f(0, (float) (1 - Math.exp(-0.5 * Math.abs(contactVel.y))), 0)
                            .mult(ManifoldPoints.getCombinedFriction(manifoldPointId))
                            .mult((float) (-partMass / 3 * ManifoldPoints.getDistance1(manifoldPointId)));
                    if (frictionTorque.lengthSquared() > 0.1f)
                        body.applyTorqueImpulse(worldContactPoint.subtract(body.getPhysicsLocation(null)).cross(frictionTorque));
                    if (frictionImpulse.lengthSquared() > 0.1f)
                        body.applyCentralImpulse(frictionImpulse);
                    //手动给予摩擦力
                    Vector3f lateral1 = new Vector3f();
                    Vector3f lateral2 = new Vector3f();
                    ManifoldPoints.getLateralFrictionDir1(manifoldPointId, lateral1);
                    ManifoldPoints.getLateralFrictionDir2(manifoldPointId, lateral2);
                    lateral1 = lateral1.normalize();
                    lateral2 = lateral2.normalize();
                    lateral1.multLocal((float) (-partMass / 60f * MMMath.sigmoidSignum(lateral1.dot(contactVel))));
                    lateral2.multLocal((float) (-partMass / 60f * MMMath.sigmoidSignum(lateral2.dot(contactVel))));
                    body.applyCentralImpulse(lateral1);
                    body.applyCentralImpulse(lateral2);
                }
                //穿透深度设为正值代表分离，让物理引擎忽视该接触点的处理
                ManifoldPoints.setDistance1(manifoldPointId, 500f);
                //重设碰撞法线方向
                normal = Vector3f.UNIT_Y;
                ManifoldPoints.setNormalWorldOnB(manifoldPointId, normal);
                ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                return; //爬坡辅助的方块不参与后续碰撞处理
            } else { //非爬坡辅助的一般方块
                double velXZ = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                if (velXZ > 12.5f) {
                    //临近区块交界处的高速碰撞额外处理增稳
                    var relContactPoint = SparkMathKt.toVector3f(worldContactPoint).sub(SparkMathKt.toVector3f(terrain.getSectionPos().origin()));
                    if (relContactPoint.x <= velXZ / 60f + 0.1
                            || relContactPoint.x >= 15.9 - velXZ / 60f
                            || relContactPoint.z <= velXZ / 60f + 0.1
                            || relContactPoint.z >= 15.9 - velXZ / 60f) {
                        if (normal.y > 0.9f) {
                            normal = Vector3f.UNIT_Y;
                            ManifoldPoints.setNormalWorldOnB(manifoldPointId, normal);
                            if (vel.y > 0.1f) {
                                body.setLinearVelocity(new Vector3f(vel.x, vel.y * 0.75f, vel.z));
                            }
                            vel.set(1, 0f);
                            contactVel.set(1, 0f);
                        }
                    }
                }
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
                        level.submitDeduplicatedTask(blockPos.toShortString(), PPhase.PRE, () -> {
                            level.destroyBlock(blockPos, Math.random() < blockDropRate);
                            return null;
                        });
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
                    if (partDamage > 1)
                        onHurt(level.damageSources().flyIntoWall(), partDamage,
                                null, this, normal, vel, worldContactPoint, hitBox);
                    return;
                } else {//否则以三分之一的能量计算伤害，冲量交给物理引擎处理
                    // 与一个物体发生碰撞时会创建3个(4个?)碰撞点，因此在单点处理计算时只取部分能量用于计算伤害
                    //TODO:对方块累积伤害
                    //对部件造成伤害
                    float partDamage = (float) (0.2 * 0.33 * partEnergy / 250);
                    if (partDamage > hitBox.getCollisionDamageReduction())
                        onHurt(level.damageSources().flyIntoWall(), partDamage - hitBox.getCollisionDamageReduction(),
                                null, this, normal, vel, worldContactPoint, hitBox);
                }
            }
            //通常粒子效果
            float speed = vel.length();
            if (contactVel.length() > 1f) {
                if (blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SAND) || blockState.is(BlockTags.SNOW)) {
                    if (speed > 10 || Math.random() < 1 - Math.exp(-0.5 * speed)) {
                        level.submitImmediateTask(PPhase.PRE, () -> {
                            //飞溅草石
                            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, blockState),
                                    worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                                    contactVel.x * (1f + 0.2f * (Math.random() - 0.5f)),
                                    contactVel.y * (1f + 0.2f * (Math.random() - 0.5f)),
                                    contactVel.z * (1f + 0.2f * (Math.random() - 0.5f)));
                            return null;
                        });
                    }
                }
                level.submitDeduplicatedTask(part.uuid + "_" + name + "_slide_sound", PPhase.PRE, () -> {
                    level.playLocalSound(worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                            blockState.getSoundType(part.level, blockPos, null).getStepSound(), SoundSource.BLOCKS,
                            (float) (0.3f * (1f - Math.exp(-0.1 * (vel.length() - 2)))), 0.75f, false);
                    return null;
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
            //TODO:撞击伤害计算
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
            if (contactVel.subtract(PhysicsHelperKt.toBVector3f(livingEntity.getDeltaMovement().scale(20))).length() < 4f) {
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
            if (partDamage > hitBox.getCollisionDamageReduction())
                onHurt(level.damageSources().flyIntoWall(), partDamage - hitBox.getCollisionDamageReduction(),
                        null, this, normal, vel, worldContactPoint, hitBox);
            //部件减速
            getPhysicsLevel().submitDeduplicatedTask(part.uuid + "_" + name + "_entity_impulse", PPhase.PRE, () -> {
                body.applyImpulse(impulseVec.mult(-0.3f), worldContactPoint.subtract(body.getPhysicsLocation(null)));
                return null;
            });
            //实体击退与伤害
            other.setLinearVelocity(other.getLinearVelocity(null).add(impulseVec.mult((float) (1f / entityMass))));
            level.submitDeduplicatedTask(livingEntity.getStringUUID() + "_entity_collision_damage", PPhase.PRE, () -> {
                float damage = (float) (contactEnergy * miu / (250 * entityMass));
                if (damage > 1) {
                    if (!level.isClientSide) {
                        livingEntity.hurt(level.damageSources().flyIntoWall(), damage);
                    }
                    level.playSound(null, worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                            SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.AMBIENT, 1f, 1f);
                }
                livingEntity.push(SparkMathKt.toVec3(impulseVec.mult((float) (0.1 / entityMass)).add(0, 0.1f, 0)));
                return null;
            });
        }
    }

    @SubscribeEvent
    public static void onPreCollision(NeedsCollisionEvent event) {
        var ownerA = PhysicsBodyExtensionKt.getOwner(event.getPcoA());
        var ownerB = PhysicsBodyExtensionKt.getOwner(event.getPcoB());
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
        if (ownerA instanceof SubPart subPart && ownerB instanceof LivingEntity livingEntity) {
            var sub = ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem();
            if (sub != null && sub.getOwner().getSubPart().getPart().getVehicle() == subPart.part.vehicle) {
                event.setShouldCollide(false);
            }
        } else if (ownerB instanceof SubPart subPart && ownerA instanceof LivingEntity livingEntity) {
            var sub = ((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem();
            if (sub != null && sub.getOwner().getSubPart().getPart().getVehicle() == subPart.part.vehicle) {
                event.setShouldCollide(false);
            }
        }
    }

    public void tick() {
        super.tick();
        if (!isRemoved()) {
            if (this.entity == null || this.entity.isRemoved()) {
                if (!getLevel().isClientSide()) refreshPartEntity();
            }
            if (this.entity != null && !this.entity.isRemoved()) {
                BoundingBox box = PhysicsBodyExtensionKt.stateOf(body).getCachedBoundingBox();
                entity.boundingBox.set(box);
            }
            var animSet = OAnimationSet.getORIGINS().get(new ModelIndex("part", attr.getAnimation("default")));
            if (!animController.isPlayingAnim() && animSet != null && !animSet.getAnimations().isEmpty()) {
                for (Map.Entry<String, OAnimation> entry : animSet.getAnimations().entrySet()) {
                    String name = entry.getKey();
                    var animInstance = new AnimInstance(this, new AnimIndex(new ModelIndex("part", attr.getAnimation("default")), name));
                    animInstance.enter();
                }
            }
            animController.tick();
        }
    }

    public void prePhysicsTick() {
        super.prePhysicsTick();
        Vector3f vel = this.body.getLinearVelocity(null);
        //仅在有速度时应用流体动力
        if (vel.length() > 0.1f) {
            for (Map.Entry<String, HydrodynamicAttr> entry : attr.hydrodynamics.entrySet()) {
                String locatorName = entry.getKey();
                HydrodynamicAttr hydrodynamicAttr = entry.getValue();
                Vector3f hydroCenter = getLocatorLocalPos(locatorName);
                Vector3f localVel = MMMath.relPointLocalVel(hydroCenter, this.body);
                Vector3f pos = getLocatorWorldPos(locatorName);
                //遮挡关系处理
                float xOcclusion = 1;//遮挡系数，1为无遮挡，应用全部流体动力；0为完全被遮挡，不应用流体动力
                float yOcclusion = 1;
                float zOcclusion = 1;
                if (Math.abs(localVel.x) > 0.1f && hydrodynamicAttr.effectiveRange().x > 0) {
                    Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(Math.signum(localVel.x), 0, 0), this.body).mult((float) hydrodynamicAttr.effectiveRange().x));
                    if (!target.equals(pos)) {
                        List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                        for (PhysicsRayTestResult ray : result) {
                            var hit = ray.getCollisionObject();
                            var hitOwner = PhysicsBodyExtensionKt.getOwner(hit);
                            if (hit == this.body || !(hitOwner instanceof SubPart)) continue;
                            if ((hitOwner instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                                if (sp.attr.hydroPriority > attr.hydroPriority) {
                                    float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                    if (tempOcclusion < xOcclusion) xOcclusion = Math.max(0, tempOcclusion);
                                    if (xOcclusion <= 0) break;
                                }
                            }
                        }
                    }
                }
                if (Math.abs(localVel.y) > 0.1f && hydrodynamicAttr.effectiveRange().y > 0) {
                    Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(0, Math.signum(localVel.y), 0), this.body).mult((float) hydrodynamicAttr.effectiveRange().y));
                    if (!target.equals(pos)) {
                        List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                        for (PhysicsRayTestResult ray : result) {
                            var hit = ray.getCollisionObject();
                            var hitOwner = PhysicsBodyExtensionKt.getOwner(hit);
                            if (hit == this.body || !(hitOwner instanceof SubPart)) continue;
                            if ((hitOwner instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                                if (sp.attr.hydroPriority > attr.hydroPriority) {
                                    float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                    if (tempOcclusion < yOcclusion) yOcclusion = Math.max(0, tempOcclusion);
                                    if (yOcclusion <= 0) break;
                                }
                            }
                        }
                    }
                }
                if (Math.abs(localVel.z) > 0.1f && hydrodynamicAttr.effectiveRange().z > 0) {
                    Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(0, 0, Math.signum(localVel.z)), this.body).mult((float) hydrodynamicAttr.effectiveRange().z));
                    if (!target.equals(pos)) {
                        List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                        for (PhysicsRayTestResult ray : result) {
                            var hit = ray.getCollisionObject();
                            var hitOwner = PhysicsBodyExtensionKt.getOwner(hit);
                            if (hit == this.body || !(hitOwner instanceof SubPart)) continue;
                            if ((hitOwner instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                                if (sp.attr.hydroPriority > attr.hydroPriority) {
                                    float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                    if (tempOcclusion < zOcclusion) zOcclusion = Math.max(0, tempOcclusion);
                                    if (zOcclusion <= 0) break;
                                }
                            }
                        }
                    }
                }
                //流体动力计算
                Vector3f localAeroForce = DynamicUtil.aeroDynamicForce(
                        1.29f,//kg/m^3 流体密度
                        this.projectedArea,
                        hydrodynamicAttr,
                        localVel).mult(xOcclusion, yOcclusion, zOcclusion).mult(1f);
                this.body.applyForce(//应用流体动力
                        MMMath.localVectorToWorldVector(localAeroForce, this.body),
                        MMMath.localVectorToWorldVector(hydroCenter, this.body));
            }
        }
        //攀爬辅助处理
        climbableBlocks.clear();
        if (!level.isClientSide() && body.isActive() && attr.blockCollision == SubPartAttr.BlockCollisionType.GROUND) {
            bodyMinY = ShapeHelper.getShapeMinY(this.body, 0.1f);
            //遍历范围内的方块
            AABB aabb = SparkMathKt.toAABB(PhysicsBodyExtensionKt.stateOf(this.body).getCachedBoundingBox())
                    .expandTowards(new Vec3(vel.x, 0, vel.z).scale(0.1f));
            int minX = (int) Math.floor(aabb.minX);
            int minZ = (int) Math.floor(aabb.minZ);
            int maxX = (int) Math.ceil(aabb.maxX);
            int maxZ = (int) Math.ceil(aabb.maxZ);
            float y0 = bodyMinY + 0.2f;
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
                                    // 仅在部件重心所处方块柱施加额外攀爬辅助力
                                    if (height > 0 && height <= stepHeight) {
                                        var horizonVel = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
                                        var speedFactor = 0.7 * Math.exp(-0.25 * horizonVel) + 0.3;
                                        var ang = Math.max(0, Math.atan2(vel.y, horizonVel));
                                        var tgtAng = speedFactor * Math.atan2(height, 1) + (1 - speedFactor) * ang;
                                        float mass = body.getMass() + 0.015f * (part.vehicle.totalMass - body.getMass());
                                        float extraVel = (float) Math.max(-5, Math.max(Math.sin(tgtAng) * vel.length(), 2f * speedFactor) - vel.y);

                                        if (!(extraVel <= 0 && vel.y < 0)) {
                                            float horizontalVelScale = (float) Math.max(0, (Math.cos(ang) - Math.cos(tgtAng)));
                                            body.applyCentralImpulse(new Vector3f(
                                                    -horizontalVelScale * vel.x,
                                                    extraVel,
                                                    -horizontalVelScale * vel.z).mult(mass));
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

    public void postPhysicsTick() {
        super.postPhysicsTick();
        getAnimController().physTick();
    }

    public boolean onHurt(DamageSource source,
                          float damage,
                          IPhysicsProjectile projectileSource,
                          SubPart subPart,
                          Vector3f normal,
                          Vector3f worldContactSpeed,
                          Vector3f worldContactPoint,
                          HitBox hitBox) {
        Vec3 sourcePos = source.getSourcePosition();
        if (sourcePos == null)
            sourcePos = SparkMathKt.toVec3(PhysicsBodyExtensionKt.stateOf(body).getTransform().getTranslation());
        Vec3 finalSourcePos = sourcePos;
        float armor = hitBox.getRHA(subPart);
        float armorPenetration = 0;
        //击退处理与特殊逻辑
        if (projectileSource == null && !level.isClientSide) {//原版伤害处理
            //冲击效果
            float knockBack = (float) (Math.log10(Math.max(1.01, 10 * Math.sqrt(damage / getMaxDurability()))) * 150f);//伤害转化为动量
            if (source.getDirectEntity() != null && source.getWeaponItem() != null) {//应用附魔等效果调整击退力度
                knockBack *= EnchantmentHelper.modifyKnockback((ServerLevel) level, source.getWeaponItem(), source.getDirectEntity(), source, 1.0f);
            }
            if (source.is(DamageTypes.EXPLOSION) || source.is(DamageTypes.PLAYER_EXPLOSION))
                knockBack *= 15.0f;
            float finalKnockBack = knockBack;
            level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {//施加动量
                part.vehicle.activate();
                subPart.body.applyImpulse(worldContactSpeed.normalize().mult(finalKnockBack), worldContactPoint.subtract(subPart.body.getPhysicsLocation(null)));
                return null;
            });
            //换算穿深
            armorPenetration = damage / 2f;
        } else if (projectileSource != null) {//甲弹对抗处理
            //获取穿深
            try {
                armorPenetration = damage;
                //TODO:研究一下Key是怎么用的
//                armorPenetration = (float) source.getExtraData().getBlackBoard().getStorage().getOrDefault(new Key<>("armor_pierce", Float.class), 0f);
            } catch (Exception e) {
                armorPenetration = damage / 2f;
                MachineMax.LOGGER.warn("{}受到的伤害不包含穿甲值信息", subPart.part.name);
            }
        }
        //线性减伤处理
        float impactDamage = damage - hitBox.getDamageReduction();
        //累积冲击效果用于削减结构完整性
        part.accumulatedImpact.put(worldContactPoint, impactDamage);
        //甲弹对抗相关处理
        if (hitBox.hasAngleEffect()) armorPenetration *= -normal.dot(worldContactSpeed.normalize());//按照设置考虑入射角影响
        //击穿判定
        if (armorPenetration > armor || hitBox.hasUnPenetrateDamage()) {
            if (armorPenetration < armor)//未击穿且有未击穿伤害时按照设置造成部分伤害
                impactDamage *= (float) Math.pow(armorPenetration / armor, hitBox.getUnPenetrateDamageFactor());
            impactDamage *= hitBox.getDamageMultiplier();
            //对部件造成伤害
            PartDamageData data = new PartDamageData(source, projectileSource, normal, worldContactSpeed, worldContactPoint, hitBox);
            accumulateDamage(impactDamage, data);
            return true;
        } else {
            if (!level.isClientSide) {
                level.submitImmediateTask(PPhase.ALL, () -> {
                    //播放命中音效
                    SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.no_pen"));
                    SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, finalSourcePos, Vec3.ZERO, 64f,
                            (float) ((2 - Math.min(7f, damage) / 7f) * (1f + 0.2f * (Math.random() - 0.5f))),
                            0.1f + 0.4f * Math.min(7f, damage) / 7f);
                    return null;
                });
            }
            return false;
        }
    }

    /**
     * <p>处理各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link DestroyableObject#tick()}</p>
     * <p>Handles the damage caused by each thread and applies it to the subsystem, which will be handled in the main thread, see {@link DestroyableObject#tick()}</p>
     */
    protected void handleAccumulatedDamage() {
        if (!level.isClientSide() && !accumulatedDamage.isEmpty()) {
            float totalDamage = 0;
            Vec3 soundPos = Vec3.ZERO;
            while (!accumulatedDamage.isEmpty()) {
                Pair<Float, PartDamageData> pair = accumulatedDamage.poll();
                float damage = pair.getFirst();
                PartDamageData data = pair.getSecond();
                //对子系统造成伤害
                if (data.hitBox().getSubsystem() != null)
                    data.hitBox().getSubsystem().onHurt(damage, data);
                totalDamage += damage;
                soundPos = SparkMathKt.toVec3(data.worldContactPoint());
            }
            setDurability(Math.clamp(getDurability() - totalDamage, 0, getMaxDurability()));
            //TODO:对载具造成伤害
            //发包同步部件与子系统状态
            sync();
            //播放音效
            SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.penetrate"));
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, soundPos, Vec3.ZERO, 64f,
                    (float) ((2 - 2 * Math.min(0.5f * getMaxDurability(), totalDamage) / getMaxDurability()) * (1f + 0.2f * (Math.random() - 0.5f))),
                    0.2f + 0.8f * 2 * Math.min(0.5f * getMaxDurability(), totalDamage) / getMaxDurability());
        }
    }

    protected void onDestroyed() {
        super.onDestroyed();
        for (AbstractSubsystem subsystem : subsystems.values()) {
            subsystem.setActive(false);
        }
        for (AbstractConnector connector : connectors.values()) {
            //TODO:随机锁定/解锁某个关节的自由度？
            if (connector.attr.breakable()) {

            }
        }
        if (level.isClientSide) {
            SoundEvent sound = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part.destroyed"));
            SpreadingSoundHelper.playSpreadingSound(level, sound, SoundSource.NEUTRAL, SparkMathKt.toVec3(getTransform().getTranslation()), Vec3.ZERO, 64f,
                    (float) (1f + 0.2f * (Math.random() - 0.5f)),
                    1f);
        }
    }

    @Override
    public void sync() {
        super.sync();
        if (!level.isClientSide()) {
            SynchedEntityData synchedentitydata = this.getSynchedData();
            List<SynchedEntityData.DataValue<?>> list = synchedentitydata.packDirty();
            if (list != null) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new SubPartDataSyncPayload(part.vehicle.uuid, part.uuid, name, list));
            }
        }
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

    public Transform getLerpedLocatorWorldTransform(String locatorName, float partialTick) {
        return getLerpedLocatorWorldTransform(locatorName, new Transform(), partialTick);
    }

    public Transform getLerpedLocatorWorldTransform(String locatorName, Transform offset, float partialTick) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            Transform localTransform = MyMath.combine(offset, attr.getLocatorTransforms().get(part.variant).get(locatorName), null);
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
            Transform localTransform = attr.getLocatorTransforms().get(part.variant).get(locatorName);
            return MyMath.combine(localTransform, body.getTransform(null), null);
        } catch (Exception e) {
            return body.getTransform(null);
        }
    }

    public Transform getLocatorLocalTransform(String locatorName) {
        try {
            if (locatorName.isEmpty()) throw new NullPointerException();
            return attr.getLocatorTransforms().get(part.variant).get(locatorName);
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
    void setPosition(Vector3f position) {
        if (entity != null && !entity.isRemoved()) entity.setPos(position.x, position.y, position.z);
        super.setPosition(position);
    }

    /**
     * <p>获取零件的最大耐久度，如果部件内零件共享耐久度，则返回共享最大耐久度；否则返回自身最大耐久度。</p>
     * <p>This method returns the maximum durability of the sub-part, taking into account whether the part shares durability with its sub-parts.</p>
     *
     * @return 最大耐久度 The maximum durability of the sub-part.
     */
    @Override
    public float getMaxDurability() {
        return part.type.shareDurability ? attr.durability : part.getSharedMaxDurability();
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
        return signalChannels;
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
        return new ModelIndex("part", attr.getModel("default"));
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> dataAccessor) {
        super.onSyncedDataUpdated(dataAccessor);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {

    }

}
