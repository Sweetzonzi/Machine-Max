package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController;
import cn.solarmoon.spark_core.animation.model.ModelController;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionObjectEntity;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.terrain.PhysicsChunkSection;
import cn.solarmoon.spark_core.physics.terrain.SectionSnapshot;
import cn.solarmoon.spark_core.util.*;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bounding.BoundingBox;
import com.jme3.bullet.collision.AfMode;
import com.jme3.bullet.collision.ManifoldPoints;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.collision.shapes.infos.ChildCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.entity.MMPartEntity;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.HydrodynamicAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBoxes;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.ISignalReceiver;
import io.github.sweetzonzi.machine_max.common.vehicle.signal.SignalChannel;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.mixin_interface.IProjectileMixin;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
@Getter
public class SubPart implements PhysicsHost, IAnimatable<SubPart>, ISubsystemHost, ISignalReceiver {
    //模型、动画与渲染
    public final ModelController modelController = new ModelController(this);
    public final AnimController animController = new AnimController(this);
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
    public final Map<String, AbstractSubsystem> subsystems = HashMap.newHashMap(1);
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public final ConcurrentMap<String, SignalChannel> signalChannels = new ConcurrentHashMap<>();//部件内共享的信号
    //物理
    public final PhysicsRigidBody body;//物理对象
    private final HashMap<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();
    public CompoundCollisionShape collisionShape;//碰撞形状
    public final boolean GROUND_COLLISION_ONLY;//是否仅和零件之下的地面方块碰撞
    public final float stepHeight;
    public Vec3 projectedArea = null;
    public float bodyMinY = -99999;
    public HashSet<BlockPos> climbableBlocks = new HashSet<>();
    //运行中
    public int tickCount = 0;
    public volatile boolean isRemoved = false;

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.attr = attr;

        this.getModelController().setModel(new ModelIndex(attr.getModel("default")));
        this.getModelController().setTextureLocation(attr.getTextures("default").get(textureIndex % attr.getTextures("default").size()));

        this.collisionShape = attr.getCollisionShape("default");
        if (!attr.interactBoxes.isEmpty()) {
            this.interactBoxes = new InteractBoxes(this, attr.interactBoxes, attr.getInteractBoxShape("default"));
        } else this.interactBoxes = null;
        this.body = createPhysicsBody(this.collisionShape, attr.mass);
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
            this.onProcessed(o1, o2, point1, point2, manifoldPointId);
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
        addPhysicsBody(body);
        for (AbstractConnector connector : connectors.values()) {
            if (connector.hasPart())
                connector.addToLevel();
        }
    }

    public void destroy() {
        isRemoved = true;
        subsystems.forEach((name, subsystem) -> subsystem.onDetach());
        subsystems.clear();
        for (AbstractConnector connector : connectors.values()) {
            connector.destroy();
        }
        if (body.isInWorld()) removePhysicsBody(body);
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

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return part.level.getPhysicsLevel();
    }

    public void onProcessed(PhysicsCollisionObject o1, @NotNull PhysicsCollisionObject o2, ManifoldPoint point1, ManifoldPoint point2, long manifoldPointId) {
        //TODO:拆分为多个简单方法以方便子类修改并提升可读性
        PhysicsRigidBody other = (PhysicsRigidBody) o2;
        Vector3f normal = new Vector3f();
        Level level = part.level;
        int hitBoxIndex, otherHitBoxIndex;
        Vector3f worldContactPoint = new Vector3f(), otherWorldContactPoint = new Vector3f();
        Vector3f localContactPoint = new Vector3f(), otherLocalContactPoint = new Vector3f();
        hitBoxIndex = point1.getTriangleIndex();
        otherHitBoxIndex = point2.getTriangleIndex();
        point1.getPositionWorld(worldContactPoint);
        point2.getPositionWorld(otherWorldContactPoint);
        point1.getLocalPoint(localContactPoint);
        point2.getLocalPoint(otherLocalContactPoint);
        //获取世界坐标下的碰撞点法线
        point1.getNormalWorldOnB(normal);
        //计算相对接触速度
        Vector3f vel = body.getLinearVelocity(null);
        Vector3f contactVel = MMMath.relPointWorldVel(localContactPoint, body);
        contactVel.subtractLocal((o2 instanceof PhysicsRigidBody) ? MMMath.relPointWorldVel(otherLocalContactPoint, other) : new Vector3f());
        //计算碰撞角度（法线与速度方向的夹角）
        float impactAngle = (float) Math.toDegrees(Math.acos(normal.dot(contactVel.normalize())));
        if (Float.isNaN(impactAngle)) impactAngle = 0; // 处理NaN情况
        //获取参与碰撞的碰撞箱
        HitBox hitBox = this.getHitBox(hitBoxIndex);
        //重设摩擦系数
        Vector3f friction = PhysicsHelperKt.toBVector3f(hitBox.attr.friction());
        if (!friction.equals(body.getAnisotropicFriction(null)))
            body.setAnisotropicFriction(friction, AfMode.basic);
        if (hitBox.attr.rollingFriction() != body.getRollingFriction())
            body.setRollingFriction(hitBox.attr.rollingFriction());
        if (hitBox.attr.rollingFriction() != body.getSpinningFriction())
            body.setSpinningFriction(hitBox.attr.spinningFriction());
        if (hitBox.attr.restitution() != body.getRestitution())
            body.setRestitution(hitBox.attr.restitution());
        //与方块碰撞时
        if (other.getCollisionGroup() == CollisionGroups.TERRAIN) {
            var owner = PhysicsBodyExtensionKt.getOwner(other);
            if (owner instanceof PhysicsChunkSection terrain) {
                //基本信息获取
                BlockPos blockPos = terrain.getBlockPosForChildShape(otherHitBoxIndex);
                SectionSnapshot.BlockSnapshot block = terrain.getBlockSnapshot(blockPos);
                if (block == null) {
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
                    normal = new Vector3f(0, 1, 0);
                    ManifoldPoints.setNormalWorldOnB(manifoldPointId, normal);
                    ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);
                    return;//爬坡辅助的方块不参与后续碰撞处理
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
                            part.onHurt(level.damageSources().flyIntoWall(), partDamage,
                                    null, this, normal, vel, worldContactPoint, hitBox);
                        return;
                    } else {//否则以三分之一的能量计算伤害，冲量交给物理引擎处理
                        // 与一个物体发生碰撞时会创建3个(4个?)碰撞点，因此在单点处理计算时只取部分能量用于计算伤害
                        //TODO:对方块累积伤害
                        //对部件造成伤害
                        float partDamage = (float) (0.2 * 0.33 * partEnergy / 250);
                        if (partDamage > hitBox.getCollisionDamageReduction())
                            part.onHurt(level.damageSources().flyIntoWall(), partDamage - hitBox.getCollisionDamageReduction(),
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
        } else if (other.getCollisionGroup() == CollisionGroups.PHYSICS_BODY) {
            var otherOwner = PhysicsBodyExtensionKt.getOwner(other);
            if (otherOwner instanceof SubPart otherSubPart) {
                //与零件碰撞时
                HitBox otherHitBox = otherSubPart.getHitBox(otherHitBoxIndex);
                //调用子系统碰撞回调
                if (hitBox.subsystem != null) {
                    hitBox.subsystem.onCollideWithPart(
                            this.body, other, contactVel, normal, worldContactPoint, impactAngle, hitBox, otherHitBox, manifoldPointId
                    );
                }
                //TODO:撞击伤害计算
            } else if (otherOwner instanceof Entity entity && !(entity instanceof CollisionObjectEntity)) {
                //与实体碰撞时
                //调用子系统碰撞回调
                if (hitBox.subsystem != null) {
                    hitBox.subsystem.onCollideWithEntity(
                            this.body, other, contactVel, normal, worldContactPoint, impactAngle, hitBox, manifoldPointId
                    );
                }
                switch (entity) {
                    //原版投射物处理 TODO:似乎不需要这么大一串？
                    case Projectile projectile when !projectile.isRemoved() && this.entity != null -> {
                        IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
                        if (mixinProjectile.machine_Max$getHitSubPart() == null && projectile.getDeltaMovement().length() > 0) {//若投射物还未与任何零件碰撞过
                            var start = PhysicsHelperKt.toBVector3f(projectile.getEyePosition().subtract(projectile.getDeltaMovement()));
                            var end = PhysicsHelperKt.toBVector3f(projectile.getEyePosition().add(projectile.getDeltaMovement()));
                            var results = getPhysicsLevel().getWorld().rayTest(start, end);
                            for (PhysicsRayTestResult result : results) {//遍历射线检测结果
                                if (result.getCollisionObject() == this.body) {//若命中本零件
                                    HitResult hitResult = new EntityHitResult(this.entity, SparkMathKt.toVec3(worldContactPoint));
                                    if (!EventHooks.onProjectileImpact(projectile, hitResult)) {//若命中事件未被取消
                                        mixinProjectile.machine_Max$setHitPoint(start.add(end.subtract(start).mult(result.getHitFraction())));
                                        mixinProjectile.machine_Max$setHitNormal(result.getHitNormalLocal(null));
                                        mixinProjectile.machine_Max$setHitBox(hitBox);
                                        mixinProjectile.machine_Max$setHitSubPart(this);
                                        part.level.submitDeduplicatedTask(projectile.getStringUUID(), PPhase.POST, () -> {
                                            ((IProjectileMixin) projectile).machine_Max$manualProjectileHit(hitResult);
                                            return null;
                                        });
                                    }
                                    break;
                                }
                            }
                        }
                    }
                    case LivingEntity livingEntity when !entity.isRemoved() && !livingEntity.isDeadOrDying() && !entity.hasImpulse && !(entity.getVehicle() instanceof MMPartEntity) -> {
                        //不处理相对速度不足的碰撞
                        if (contactVel.subtract(PhysicsHelperKt.toBVector3f(entity.getDeltaMovement().scale(20))).length() < 4f) {
                            return;
                        }
                        float contactNormalSpeed = vel.dot(normal);//直接取接触点碰撞速度似乎不准确
                        //计算并分配碰撞能量
                        double entityMass = MassUtil.getEntityMass(entity);
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
                            part.onHurt(level.damageSources().flyIntoWall(), partDamage - hitBox.getCollisionDamageReduction(),
                                    null, this, normal, vel, worldContactPoint, hitBox);
                        //部件减速
                        getPhysicsLevel().submitDeduplicatedTask(part.uuid + "_" + name + "_entity_impulse", PPhase.PRE, () -> {
                            body.applyImpulse(impulseVec.mult(-0.3f), worldContactPoint.subtract(body.getPhysicsLocation(null)));
                            return null;
                        });
                        //实体击退与伤害
                        other.setLinearVelocity(other.getLinearVelocity(null).add(impulseVec.mult((float) (1f / entityMass))));
                        level.submitDeduplicatedTask(entity.getStringUUID() + "_entity_collision_damage", PPhase.PRE, () -> {
                            float damage = (float) (contactEnergy * miu / (250 * entityMass));
                            if (damage > 1) {
                                if (!level.isClientSide) {
                                    entity.hurt(level.damageSources().flyIntoWall(), damage);
                                }
                                level.playSound(null, worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                                        SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.AMBIENT, 1f, 1f);
                            }
                            entity.push(SparkMathKt.toVec3(impulseVec.mult((float) (0.1 / entityMass)).add(0, 0.1f, 0)));
                            return null;
                        });
                    }
                    default -> {
                    }
                }
            }
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
        if (isRemoved) return;
        if (this.entity == null || this.entity.isRemoved()) {
            if (!getLevel().isClientSide()) refreshPartEntity();
        }
    }

    public void prePhysicsTick() {
        if (isRemoved) return;
        tickCount++;
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
        if (body.isActive() && attr.blockCollision == SubPartAttr.BlockCollisionType.GROUND) {
            bodyMinY = ShapeHelper.getShapeMinY(this.body, 0.1f);
            //遍历范围内的方块
            AABB aabb = SparkMathKt.toAABB(PhysicsBodyExtensionKt.stateOf(this.body).getCachedBoundingBox())
                    .expandTowards(new Vec3(vel.x, 0, vel.z).scale(0.1f));
            int minX = (int) Math.floor(aabb.minX);
            int minZ = (int) Math.floor(aabb.minZ);
            int maxX = (int) Math.ceil(aabb.maxX);
            int maxZ = (int) Math.ceil(aabb.maxZ);
            float y0 = bodyMinY + 0.1f;
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
        if (isRemoved) return;
        if (entity != null && !entity.isRemoved()) {//更新实体包围盒
            BoundingBox box = PhysicsBodyExtensionKt.stateOf(body).getCachedBoundingBox();
            entity.boundingBox.set(box);
        }
        getAnimController().physTick();
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
        return part.getLevel();
    }

    @NotNull
    @Override
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        Transform transform = PhysicsBodyExtensionKt.stateOf(body).getTransform();
        Transform lastTransform = PhysicsBodyExtensionKt.stateOf(body).getLastTransform();
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(lastTransform, transform, number.floatValue()).toTransformMatrix());
    }

    @Override
    public SubPart getSubPart() {
        return this;
    }

    @Override
    public Level getLevel() {
        return part.level;
    }
}
