package io.github.sweetzonzi.machinemax.common.vehicle;

import cn.solarmoon.spark_core.event.NeedsCollisionEvent;
import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.SparkMathKt;
import cn.solarmoon.spark_core.physics.collision.CollisionCallback;
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.TaskSubmitOffice;
import com.jme3.bullet.collision.*;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.common.entity.MMPartEntity;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.InteractBoxAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IProjectileMixin;
import io.github.sweetzonzi.machinemax.util.MMMath;
import io.github.sweetzonzi.machinemax.util.ShapeHelper;
import io.github.sweetzonzi.machinemax.util.mechanic.ArmorUtil;
import io.github.sweetzonzi.machinemax.util.mechanic.DamageUtil;
import io.github.sweetzonzi.machinemax.util.mechanic.DynamicUtil;
import io.github.sweetzonzi.machinemax.util.mechanic.MassUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME)
public class SubPart implements PhysicsHost, CollisionCallback, PhysicsCollisionObjectTicker {
    public final Part part;
    public String name;
    public final SubPartAttr attr;
    public SubPart parent;
    public Transform massCenterTransform = new Transform();
    public final HashMap<String, AbstractConnector> connectors = HashMap.newHashMap(1);
    public final PhysicsRigidBody body;//物理对象
    public final InteractBoxes interactBoxes;//交互判定
    public final CompoundCollisionShape collisionShape;//碰撞形状
    public final boolean GROUND_COLLISION_ONLY;//是否仅和零件之下的地面方块碰撞
    public final float stepHeight;
    public int tickCount = 0;
    //流体动力相关参数
    private final boolean ENABLE_FLUID_DYNAMIC_SWEEP_TEST = false;//TODO:true时，检测流体遮挡效果时将使用球形扫掠而非射线检测
    public Vec3 projectedArea;

    public SubPart(String name, Part part, SubPartAttr attr) {
        this.part = part;
        this.name = name;
        this.attr = attr;
        this.collisionShape = attr.getCollisionShape(part.variant, part.type);
        if (!attr.interactBoxes.isEmpty())
            this.interactBoxes = new InteractBoxes(this, attr.interactBoxes, attr.getInteractBoxShape(part.variant, part.type));
        else this.interactBoxes = null;
        this.body = new PhysicsRigidBody(name, this, this.collisionShape, attr.mass);
        this.body.setFriction(1.0f);
        this.body.setAnisotropicFriction(PhysicsHelperKt.toBVector3f(attr.friction), AfMode.basic);//各向异性摩擦系数
        this.body.setRollingFriction(attr.rollingFriction);//滚动摩擦系数（车轮滚动）
        this.body.setSpinningFriction(attr.rollingFriction);//旋转摩擦系数（车轮转向）
        this.body.setRestitution(attr.getRestitution());//恢复系数（碰撞能量损耗）
        this.body.setCollisionGroup(VehicleManager.COLLISION_GROUP_PART);
        if (attr.blockCollision.equals("true") || attr.blockCollision.equals("True")) {
            GROUND_COLLISION_ONLY = false;
            this.body.addCollideWithGroup(VehicleManager.COLLISION_GROUP_BLOCK);
        } else if (attr.blockCollision.equals("ground") || attr.blockCollision.equals("Ground")) {
            GROUND_COLLISION_ONLY = true;
            this.body.addCollideWithGroup(VehicleManager.COLLISION_GROUP_BLOCK);
        } else {
            GROUND_COLLISION_ONLY = false;
        }
        this.stepHeight = attr.stepHeight;
        this.projectedArea = attr.projectedArea;
    }

    public void addToLevel() {
        this.bindBody(body, part.level.getPhysicsLevel(), true,
                (body -> {
                    body.addCollisionCallback(this);
                    body.addPhysicsTicker(this);
                    body.setProtectGravity(true);
                    body.setSleepingThresholds(0.1f, 0.1f);
                    body.setContactStiffness(1e30f);
                    return null;
                }));
    }

    public void destroy() {
        for (AbstractConnector connector : connectors.values()) {
            connector.destroy();
        }
        if (body.isInWorld()) this.removeAllBodies();
        if (interactBoxes != null) {
            for (InteractBox interactBox : interactBoxes.values()) interactBox.destroy();
            interactBoxes.clear();
            getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                getPhysicsLevel().getWorld().removeCollisionObject(interactBoxes.body);
                return null;
            });
        }
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return part.level.getPhysicsLevel();
    }

    /**
     * Invoked immediately after a contact manifold is removed.
     *
     * @param manifoldId the native ID of the {@code btPersistentManifold} (not
     *                   zero)
     */
    @Override
    public void onEnded(@NotNull PhysicsCollisionObject pcoA, @NotNull PhysicsCollisionObject pcoB, long manifoldId) {
//        MachineMax.LOGGER.debug("onEnded: {} {}", pcoA.name, pcoB.name);
    }

    /**
     * Invoked immediately after a contact point is refreshed without being
     * removed. Skipped for Sphere-Sphere contacts.
     *
     * @param pcoA            the first involved object (not null)
     * @param pcoB            the 2nd involved object (not null)
     * @param manifoldPointId the native ID of the {@code btManifoldPoint} (not
     *                        zero)
     */
    @Override
    public void onProcessed(PhysicsCollisionObject pcoA, @NotNull PhysicsCollisionObject pcoB, long manifoldPointId) {
        PhysicsRigidBody other;
        Level level = part.level;
        int hitBoxIndex, otherHitBoxIndex;
        Vector3f worldContactPoint = new Vector3f(), otherWorldContactPoint = new Vector3f();
        Vector3f localContactPoint = new Vector3f(), otherLocalContactPoint = new Vector3f();
        long childShapeId, otherChildShapeId;
        if (pcoA.getOwner() == this) {
            other = (PhysicsRigidBody) pcoB;
            hitBoxIndex = ManifoldPoints.getIndex0(manifoldPointId);
            otherHitBoxIndex = ManifoldPoints.getIndex1(manifoldPointId);
            ManifoldPoints.getPositionWorldOnA(manifoldPointId, worldContactPoint);
            ManifoldPoints.getPositionWorldOnB(manifoldPointId, otherWorldContactPoint);
            ManifoldPoints.getLocalPointA(manifoldPointId, localContactPoint);
            ManifoldPoints.getLocalPointB(manifoldPointId, otherLocalContactPoint);

        } else {
            other = (PhysicsRigidBody) pcoA;
            hitBoxIndex = ManifoldPoints.getIndex1(manifoldPointId);
            otherHitBoxIndex = ManifoldPoints.getIndex0(manifoldPointId);
            ManifoldPoints.getPositionWorldOnB(manifoldPointId, worldContactPoint);
            ManifoldPoints.getPositionWorldOnA(manifoldPointId, otherWorldContactPoint);
            ManifoldPoints.getLocalPointB(manifoldPointId, localContactPoint);
            ManifoldPoints.getLocalPointA(manifoldPointId, otherLocalContactPoint);
        }
        //获取世界坐标下的碰撞点法线
        Vector3f normal = new Vector3f();
        ManifoldPoints.getNormalWorldOnB(manifoldPointId, normal);
        //计算相对接触速度
        Vector3f contactVel = MMMath.relPointWorldVel(localContactPoint, body);
        contactVel.subtractLocal((pcoB instanceof PhysicsRigidBody) ? MMMath.relPointWorldVel(otherLocalContactPoint, other) : new Vector3f());
        //计算碰撞角度（法线与速度方向的夹角）
        float impactAngle = (float) Math.toDegrees(Math.acos(normal.dot(contactVel.normalize())));
        if (Float.isNaN(impactAngle)) impactAngle = 0; // 处理NaN情况
        //获取参与碰撞的子系统
        childShapeId = collisionShape.findChild(hitBoxIndex).getShape().nativeId();
        String hitBoxName = part.type.hitBoxes.get(childShapeId);
        var subsystems = part.subsystemHitBoxes.get(hitBoxName);
        //调用子系统碰撞回调
        for (AbstractSubsystem subsystem : subsystems) subsystem.onCollide(pcoA, pcoB, manifoldPointId);
        //与方块碰撞时
        if (other.getCollisionGroup() == VehicleManager.COLLISION_GROUP_BLOCK) {
            if (other.userIndex() <= 0) {
                other.setContactResponse(false);
                return;//忽略即将过期方块的碰撞
            }
            BlockState blockState = (BlockState) other.getUserObject();
            BlockPos blockPos = other.blockPos;
            //调用子系统碰撞回调
            for (AbstractSubsystem subsystem : subsystems) {
                subsystem.onCollideWithBlock(
                        this.body, other, blockPos, blockState, contactVel, normal, worldContactPoint, impactAngle, childShapeId, manifoldPointId
                );
                if (other.userIndex() <= 0) {
                    other.setContactResponse(false);
                    return;//所有子系统处理完碰撞后，忽略可能被手动设置为过期的方块的碰撞
                }
            }
            //根据碰撞速度、碰撞角、方块硬度和爆炸抗性，摧毁碰撞的方块，同时对自身造成伤害
            //TODO:配置文件开关冲撞可破坏方块
            if (attr.blockDamageFactor > 0 && blockState.getDestroySpeed(part.level, blockPos) >= 0) {//碰撞的方块可破坏时
                //计算碰撞法线方向上的速度(考虑冲量影响)
                float blockArmor = ArmorUtil.getBlockArmor(part.level, blockState, blockPos);
                float subPartArmor = part.type.thickness.get(childShapeId);
                double partMass = body.getMass();//等效质量，考虑连接部件的影响
                for (AbstractConnector connector : this.connectors.values()) {
                    if (connector.hasPart())
                        partMass += (0.3 * connector.attachedConnector.subPart.body.getMass());
                }
                partMass += 0.05 * (part.vehicle.totalMass - body.getMass());
                double contactNormalSpeed = Math.abs(contactVel.dot(normal)) + ManifoldPoints.getAppliedImpulse(manifoldPointId) / body.getMass();
                float restitution = Math.clamp(body.getRestitution() * other.getRestitution(), 0f, 1f);//TODO:考虑二者护甲差距调整此系数
                ManifoldPoints.setCombinedRestitution(manifoldPointId, restitution);
                double contactEnergy = 0.5 * partMass * contactNormalSpeed * contactNormalSpeed * (1 - restitution);//此次碰撞损失的能量
                //TODO:根据硬度差距调整能量释放速度
                double blockDurability = DamageUtil.getMaxBlockDurability(level, blockState, blockPos);
                //方块有支撑时将强化其耐久度
                Vec3i supportBlockPos = MMMath.getClosestAxisAlignedVector(SparkMathKt.toVec3(normal.mult(-1)));
                PhysicsRigidBody supportBlockBody = getPhysicsLevel().getTerrainBlockBodies().get(blockPos.offset(supportBlockPos));
                if (supportBlockBody != null) {
                    blockDurability += 0.5 * DamageUtil.getMaxBlockDurability(level, (BlockState) supportBlockBody.getUserObject(), supportBlockBody.blockPos);
                }
                double blockEnergy = contactEnergy * subPartArmor / (subPartArmor + blockArmor);//方块吸收的碰撞能量
                double partEnergy = contactEnergy - blockEnergy;//部件吸收的碰撞能量
                if (attr.blockDamageFactor * blockEnergy > 250 * blockDurability) {//能量能够一次摧毁则摧毁,计算额外冲量使部件减速
                    //摧毁方块
                    other.setContactResponse(false);
                    other.setUserIndex(0);
                    //被摧毁的方块掉落为物品的概率，方块吸收的碰撞能量恰好与耐久度相同时必定掉落，掉落率随能量增加而递减
                    double blockDropRate = Math.exp(1 - (attr.blockDamageFactor * blockEnergy / (250 * blockDurability)));
                    if (!level.isClientSide) {
                        ((TaskSubmitOffice) level).submitDeduplicatedTask(other.blockPos.toShortString(), PPhase.PRE, () -> {
                            level.destroyBlock(other.blockPos, Math.random() < blockDropRate);
                            return null;
                        });
                    }
                    //根据方块被破坏实际消耗的能量调整部件吸收的能量，但不全额作用为反冲量以提升操控流畅性
                    double actualPartEnergy = 0.1 * partEnergy * ((250 * blockDurability) / blockEnergy);
                    if (actualPartEnergy < 0 || Double.isNaN(actualPartEnergy)) actualPartEnergy = 0f;
                    double finalActualPartEnergy = actualPartEnergy;
                    //部件减速
                    ManifoldPoints.setDistance1(manifoldPointId, 1f);//阻止接触约束计算
                    ManifoldPoints.setAppliedImpulse(manifoldPointId, 0f);//重置默认冲量，采用计算结果
                    Vector3f vel = body.getLinearVelocity(null);
                    Vector3f aVel = body.getAngularVelocity(null);
                    Vector3f impulse = normal.mult((float) (Math.sqrt(2 * finalActualPartEnergy * body.getMass())));
                    Vector3f offset = worldContactPoint.subtract(body.getPhysicsLocation(null));
                    Matrix3f inertia = body.getInverseInertiaWorld(null);
                    body.setLinearVelocity(vel.add(impulse.mult(1f / body.getMass())));
                    Vector3f deltaOmega = inertia.mult(offset.cross(impulse), null);
                    body.setAngularVelocity(aVel.add(deltaOmega));
                    //TODO:对部件造成伤害
//                    part.onHurt()
                    return;
                } else {//否则以三分之一的能量计算伤害，冲量交给物理引擎处理
                    // 与一个物体发生碰撞时会创建3个(4个?)碰撞点，因此在单点处理计算时只取部分能量用于计算伤害
                    //TODO:对方块累积伤害
                    //TODO:对部件造成伤害
                }
            }
            //摩擦力修正
            float friction1 = pcoA.getFriction();
            float friction2 = pcoB.getFriction();
            float slip = (float) 1 - ((other.userIndex2()) * (1 - attr.slipAdaptation) / 100);//潮湿与打滑带来的修正系数
            //通常粒子效果
            float speed = body.getLinearVelocity(null).length();
            if (contactVel.length() > 1f) {
                if (blockState.is(BlockTags.DIRT) || blockState.is(BlockTags.SAND) || blockState.is(BlockTags.SNOW)) {
                    if (speed > 10 || Math.random() < 1 - Math.exp(-0.5 * speed)) {
                        ((TaskSubmitOffice) level).submitImmediateTask(PPhase.PRE, () -> {
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
                ((TaskSubmitOffice) level).submitDeduplicatedTask(part.uuid + "_" + name + "_slide_sound", PPhase.PRE, () -> {
                    level.playLocalSound(worldContactPoint.x, worldContactPoint.y, worldContactPoint.z,
                            blockState.getSoundType(part.level, blockPos, null).getStepSound(), SoundSource.BLOCKS,
                            (float) (0.3f * (1f - Math.exp(-0.1 * (body.getLinearVelocity(null).length() - 2)))), 0.75f, false);
                    return null;
                });
            }
            if (contactVel.length() > 1f && impactAngle > 60f && impactAngle < 120f) {//打滑时
                slip = (float) (Math.pow(slip, 0.5 * (contactVel.length() - 1)) * 0.9f);//根据打滑情况额外降低摩擦系数
                //TODO:漂移音效
            }
            //重设摩擦系数
            ManifoldPoints.setCombinedFriction(manifoldPointId, Math.max(0.001f, friction1 * friction2 * slip));
        } else if (other.getCollisionGroup() == VehicleManager.COLLISION_GROUP_PART) {
            if (other.getOwner() instanceof SubPart otherSubPart) {//与零件碰撞时
                otherChildShapeId = otherSubPart.collisionShape.findChild(otherHitBoxIndex).getShape().nativeId();
                //调用子系统碰撞回调
                for (AbstractSubsystem subsystem : subsystems) {
                    subsystem.onCollideWithPart(
                            this.body, other, contactVel, normal, worldContactPoint, impactAngle, childShapeId, otherChildShapeId, manifoldPointId
                    );
                }
            } else if (other.getOwner() instanceof Entity entity) {//与实体碰撞时
                //调用子系统碰撞回调
                for (AbstractSubsystem subsystem : subsystems) {
                    subsystem.onCollideWithEntity(
                            this.body, other, contactVel, normal, worldContactPoint, impactAngle, childShapeId, manifoldPointId
                    );
                }
                switch (entity) {
                    //原版投射物处理
                    case Projectile projectile when !projectile.isRemoved() && part.getEntity() != null -> {
                        IProjectileMixin mixinProjectile = (IProjectileMixin) projectile;
                        if (mixinProjectile.machine_Max$getHitSubPart() == null && projectile.getDeltaMovement().length() > 0) {//若投射物还未与任何零件碰撞过
                            var start = PhysicsHelperKt.toBVector3f(projectile.getEyePosition().subtract(projectile.getDeltaMovement()));
                            var end = PhysicsHelperKt.toBVector3f(projectile.getEyePosition().add(projectile.getDeltaMovement()));
                            var results = getPhysicsLevel().getWorld().rayTest(start, end);
                            for (PhysicsRayTestResult result : results) {//遍历射线检测结果
                                if (result.getCollisionObject() == this.body) {//若命中本零件
                                    HitResult hitResult = new EntityHitResult(part.getEntity(), SparkMathKt.toVec3(worldContactPoint));
                                    if (!EventHooks.onProjectileImpact(projectile, hitResult)) {//若命中事件未被取消
                                        mixinProjectile.machine_Max$setHitPoint(start.add(end.subtract(start).mult(result.getHitFraction())));
                                        mixinProjectile.machine_Max$setHitNormal(result.getHitNormalLocal(null));
                                        mixinProjectile.machine_Max$setHitBoxId(childShapeId);
                                        mixinProjectile.machine_Max$setHitSubPart(this);
                                        ((TaskSubmitOffice) part.level).submitDeduplicatedTask(projectile.getStringUUID(), PPhase.POST, () -> {
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
                        float contactNormalSpeed = body.getLinearVelocity(null).dot(normal);//直接取接触点碰撞速度似乎不准确

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
                        //TODO:部件伤害
                        //部件减速
                        getPhysicsLevel().submitDeduplicatedTask(part.uuid + "_" + name + "_entity_impulse", PPhase.PRE, () -> {
                            body.applyImpulse(impulseVec.mult(-0.3f), worldContactPoint.subtract(body.getPhysicsLocation(null)));
                            return null;
                        });
                        //实体击退与伤害
                        other.setLinearVelocity(other.getLinearVelocity(null).add(impulseVec.mult((float) (1f / entityMass))));
                        ((TaskSubmitOffice) level).submitDeduplicatedTask(entity.getStringUUID() + "_entity_collision_damage", PPhase.PRE, () -> {
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

    /**
     * Invoked immediately after a contact manifold is created.
     *
     * @param manifoldId the native ID of the {@code btPersistentManifold} (not
     *                   zero)
     */
    @Override
    public void onStarted(@NotNull PhysicsCollisionObject pcoA, @NotNull PhysicsCollisionObject pcoB, long manifoldId) {
//        MachineMax.LOGGER.debug("onStarted: {} {}", pcoA.name, pcoB.name);
    }

    @SubscribeEvent
    public static void onPreCollision(NeedsCollisionEvent event) {
        //同载具部件不发生碰撞
        if (event.getPcoA().getOwner() instanceof SubPart subPartA && event.getPcoB().getOwner() instanceof SubPart subPartB) {
            if (subPartA.part.vehicle instanceof VehicleCore vehicleA && subPartB.part.vehicle instanceof VehicleCore vehicleB) {
                if (vehicleA == vehicleB) {
                    event.setShouldCollide(false);
                    return;
                }
            }
        }
        //特殊碰撞模式的处理
        PhysicsRigidBody terrain;
        PhysicsRigidBody partBody;
        if (event.getPcoA() instanceof PhysicsRigidBody pcoA && event.getPcoB() instanceof PhysicsRigidBody pcoB) {
            if (pcoA.getOwner() instanceof SubPart && pcoB.name.equals("terrain")) {
                terrain = pcoB;
                partBody = pcoA;
            } else if (pcoB.getOwner() instanceof SubPart && pcoA.name.equals("terrain")) {
                terrain = pcoA;
                partBody = pcoB;
            } else return;
        } else return;
        if (partBody.getOwner() instanceof SubPart subPart && subPart.GROUND_COLLISION_ONLY) {//仅与地面方块碰撞的零件遭遇方块时
            float terrainHeight = terrain.cachedBoundingBox.getMax(null).y;
            float y0 = ShapeHelper.getShapeMinY(partBody, 0.2f) + 0.05f;//计算部件最低点高度
            if (terrainHeight > y0) {//若地形高于于部件最低位置，则视情况修改碰撞检测结果
                float height = terrainHeight - y0;//部件最低点与地形的高度差
                BlockPos highestBlockPos = terrain.blockPos.above();
                while (height <= subPart.stepHeight && subPart.getPhysicsLevel().getTerrainBlockBodies().containsKey(highestBlockPos)) {
                    height = subPart.getPhysicsLevel().getTerrainBlockBodies().get(highestBlockPos).cachedBoundingBox.getMax(null).y - y0;
                    highestBlockPos = highestBlockPos.above();
                }
                if (height <= subPart.stepHeight) {
                    event.setShouldCollide(false);
                }
            }
        }
    }

    @Override
    public void prePhysicsTick(@NotNull PhysicsCollisionObject pco, @NotNull PhysicsLevel physicsLevel) {
        tickCount++;
        Vector3f vel = this.body.getLinearVelocity(null);
        Vector3f localVel = MMMath.relPointLocalVel(PhysicsHelperKt.toBVector3f(attr.aeroDynamic.center()), this.body);
        Vector3f pos = MMMath.relPointWorldPos(PhysicsHelperKt.toBVector3f(attr.aeroDynamic.center()), this.body);
        //仅在有速度时应用流体动力
        if (vel.length() > 0.1f) {
            //遮挡关系处理
            float xOcclusion = 1;//遮挡系数，1为无遮挡，应用全部流体动力；0为完全被遮挡，不应用流体动力
            float yOcclusion = 1;
            float zOcclusion = 1;
            if (Math.abs(localVel.x) > 0.1f && attr.aeroDynamic.effectiveRange().x > 0) {
                Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(Math.signum(localVel.x), 0, 0), this.body).mult((float) attr.aeroDynamic.effectiveRange().x));
                if (!target.equals(pos)) {
                    List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                    for (PhysicsRayTestResult ray : result) {
                        var hit = ray.getCollisionObject();
                        if (hit == this.body || !(hit.getOwner() instanceof SubPart)) continue;
                        if ((hit.getOwner() instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                            if (sp.attr.aeroDynamic.priority() > this.attr.aeroDynamic.priority()) {
                                float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                if (tempOcclusion < xOcclusion) xOcclusion = Math.max(0, tempOcclusion);
                                if (xOcclusion <= 0) break;
                            }
                        }
                    }
                }
            }
            if (Math.abs(localVel.y) > 0.1f && attr.aeroDynamic.effectiveRange().y > 0) {
                Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(0, Math.signum(localVel.y), 0), this.body).mult((float) attr.aeroDynamic.effectiveRange().y));
                if (!target.equals(pos)) {
                    List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                    for (PhysicsRayTestResult ray : result) {
                        var hit = ray.getCollisionObject();
                        if (hit == this.body || !(hit.getOwner() instanceof SubPart)) continue;
                        if ((hit.getOwner() instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                            if (sp.attr.aeroDynamic.priority() > this.attr.aeroDynamic.priority()) {
                                float tempOcclusion = ray.getHitFraction();//距离越近，遮挡效果越大
                                if (tempOcclusion < yOcclusion) yOcclusion = Math.max(0, tempOcclusion);
                                if (yOcclusion <= 0) break;
                            }
                        }
                    }
                }

            }
            if (Math.abs(localVel.z) > 0.1f && attr.aeroDynamic.effectiveRange().z > 0) {
                Vector3f target = pos.add(MMMath.localVectorToWorldVector(new Vector3f(0, 0, Math.signum(localVel.z)), this.body).mult((float) attr.aeroDynamic.effectiveRange().z));
                if (!target.equals(pos)) {
                    List<PhysicsRayTestResult> result = getPhysicsLevel().getWorld().rayTest(pos, target);
                    for (PhysicsRayTestResult ray : result) {
                        var hit = ray.getCollisionObject();
                        if (hit == this.body || !(hit.getOwner() instanceof SubPart)) continue;
                        if ((hit.getOwner() instanceof SubPart sp && sp.part.vehicle == this.part.vehicle)) {
                            if (sp.attr.aeroDynamic.priority() > this.attr.aeroDynamic.priority()) {
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
                    attr.aeroDynamic,
                    localVel).mult(xOcclusion, yOcclusion, zOcclusion);
            this.body.applyForce(//应用流体动力
                    MMMath.localVectorToWorldVector(localAeroForce, this.body),
                    MMMath.localVectorToWorldVector(PhysicsHelperKt.toBVector3f(attr.aeroDynamic.center()), this.body));
        }
        //攀爬辅助处理
        if (this.GROUND_COLLISION_ONLY && stepHeight > 0 && attr.climbAssist) {
            float y0 = ShapeHelper.getShapeMinY(this.body, 0.2f);
            Vector3f start = this.body.getPhysicsLocation(null);
            start.set(1, y0 - 1);
            var end = start.add(0f, 1 + stepHeight, 0f);
            if (start.equals(end)) {
                MachineMax.LOGGER.error("Same start and end position for climb assist ray test, canceling climb assist.");
                //TODO:治标不治本，需要排查原因
                return;
            }
            var test = getPhysicsLevel().getWorld().rayTest(start, end);
            PhysicsRigidBody terrainsUnder = null;//清空先前记录的地面碰撞体
            float height = -1;
            for (var hit : test) {//寻找部件下最低的方块
                if (hit.getCollisionObject() instanceof PhysicsRigidBody terrain && terrain.name.equals("terrain")) {
                    terrainsUnder = terrain;
                    height = Math.max(terrain.cachedBoundingBox.getMax(null).y - y0, height);
                    break;
                }
            }
            if (terrainsUnder != null) {//若接地/轮子质心竖直投影方向有方块，寻找投影方向连续方块的最高点
                BlockPos highestBlockPos = terrainsUnder.blockPos.above();
                while (height <= stepHeight && getPhysicsLevel().getTerrainBlockBodies().containsKey(highestBlockPos)) {
                    height = Math.max(getPhysicsLevel().getTerrainBlockBodies().get(highestBlockPos).cachedBoundingBox.getMax(null).y - y0, height);
                    highestBlockPos = highestBlockPos.above();
                }
            }
            if (height > 0 && height <= stepHeight) {//若最高点小于容许高度，则额外为车轮赋予速度
                var horizonVel = Math.sqrt(vel.x * vel.x + vel.z * vel.z);//根据水平速度决定赋予的额外垂直速度
                var ang = Math.atan2(height, 1);
                float mass = body.getMass() + 0.015f * (part.vehicle.totalMass - body.getMass());
                float extraVel = (float) Math.max(Math.sin(ang) * horizonVel, 2f) - vel.y;
                float horizontalVelScale = (float) Math.cos(ang);
                body.applyCentralImpulse(new Vector3f((horizontalVelScale - 1) * vel.x, extraVel, (horizontalVelScale - 1) * vel.z).mult(mass));
            }
        }
    }

    public void mcTick(@NotNull PhysicsCollisionObject pco, @NotNull Level level) {
    }

    public class InteractBoxes extends ConcurrentHashMap<String, InteractBox> implements PhysicsHost, PhysicsCollisionObjectTicker, PhysicsCollisionListener {

        public final SubPart subPart;
        public final CompoundCollisionShape interactBoxShape;
        public final PhysicsRigidBody body;

        public InteractBoxes(SubPart subPart, Map<String, InteractBoxAttr> boxes, CompoundCollisionShape interactBoxShape) {
            this.subPart = subPart;
            this.interactBoxShape = interactBoxShape;
            for (Map.Entry<String, InteractBoxAttr> entry : boxes.entrySet()) {
                String name = entry.getKey();
                InteractBox interactBox = new InteractBox(subPart, name, entry.getValue());
                this.put(name, interactBox);
            }
            this.body = new PhysicsRigidBody(name, this, interactBoxShape);
            this.body.setKinematic(true);
            this.body.setCollisionGroup(VehicleManager.COLLISION_GROUP_NO_COLLISION);
            this.body.setCollideWithGroups(VehicleManager.COLLISION_GROUP_NONE);
            bindBody(this.body, getPhysicsLevel(), true, body -> {
                body.addPhysicsTicker(this);
                return null;
            });
        }

        @Override
        public void postPhysicsTick(@NotNull PhysicsCollisionObject body, @NotNull PhysicsLevel level) {
            PhysicsCollisionObjectTicker.super.postPhysicsTick(body, level);
            Vector3f position = subPart.body.getPhysicsLocation(null);
            Quaternion rotation = subPart.body.getPhysicsRotation(null);
            if (body instanceof PhysicsRigidBody rigidBody) {
                rigidBody.setPhysicsLocation(position);
                rigidBody.setPhysicsRotation(rotation);
                getPhysicsLevel().getWorld().contactTest(this.body, this);
            }
        }

        @Override
        public void collision(PhysicsCollisionEvent event) {
            PhysicsRigidBody entityHitBox;
            int interactBoxIndex;
            if (event.getObjectA() == this.body) {
                entityHitBox = (PhysicsRigidBody) event.getObjectB();
                interactBoxIndex = event.getIndex0();
            } else if (event.getObjectB() == this.body) {
                entityHitBox = (PhysicsRigidBody) event.getObjectA();
                interactBoxIndex = event.getIndex1();
            } else return;//事件与交互判定无关时提前返回
            if (entityHitBox.getOwner() instanceof LivingEntity entity) {
                InteractBox interactBox = getInteractBox(interactBoxIndex);
                InteractBox.InteractMode mode = interactBox.interactMode;
                if (mode == InteractBox.InteractMode.FAST && entity.hasData(MMAttachments.getENTITY_EYESIGHT())) {
                    var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                    eyesight.addFastInteractBox(interactBox);
                }
            }
        }

        public InteractBox getInteractBox(long childShapeId) {
            String name = subPart.attr.interactBoxNames.get(childShapeId);
            return this.get(name);
        }

        public InteractBox getInteractBox(int contactPointIndex) {
            long childShapeId = this.interactBoxShape.findChild(contactPointIndex).getShape().nativeId();
            return getInteractBox(childShapeId);
        }

        @NotNull
        @Override
        public PhysicsLevel getPhysicsLevel() {
            return subPart.getPhysicsLevel();
        }

    }
}
