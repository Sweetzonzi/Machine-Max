package io.github.sweetzonzi.machine_max.common.attachment;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.common.vehicle.interact.InteractBoxes;
import io.github.sweetzonzi.machine_max.network.payload.SubsystemInteractPayload;
import io.github.sweetzonzi.machine_max.util.MMMath;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class LivingEntityEyesightAttachment implements PhysicsCollisionListener {
    public final LivingEntity owner;
    public final PhysicsGhostObject trigger;
    private Vector3f startPos;
    private Vector3f view;
    private Vector3f endPos;
    private final ConcurrentMap<PhysicsRigidBody, PhysicsRayTestResult> targetBodies = new ConcurrentHashMap<>(2);
    private final List<PhysicsRigidBody> sortedTargetBodies = new LinkedList<>();
    private final HashMap<PhysicsRigidBody, PhysicsRayTestResult> targetBodyCache = new HashMap<>(2);
    private final CopyOnWriteArraySet<PhysicsRigidBody> sortedTargetBodyCache = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<Object> sortedTargets = new CopyOnWriteArraySet<>();//刚体的持有者而非刚体本身
    private final CopyOnWriteArraySet<Object> sortedTargetCache = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<InteractBox> fastInteractBoxes = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<InteractBox> fastInteractBoxCache = new CopyOnWriteArraySet<>();
    private double eyesightRange;

    public LivingEntityEyesightAttachment(LivingEntity entity) {
        this.owner = entity;
        var boundingBox = entity.getBoundingBox();
        BoxCollisionShape shape = new BoxCollisionShape((float) (boundingBox.getXsize() * 0.5f), (float) (boundingBox.getYsize() * 0.5f), (float) (boundingBox.getZsize() * 0.5f));
        this.trigger = new PhysicsGhostObject(shape);
        this.trigger.setPhysicsLocation(PhysicsHelperKt.toBVector3f(entity.getPosition(1f)));
        this.trigger.setCollisionGroup(CollisionGroups.TRIGGER);
        this.trigger.setCollideWithGroups(CollisionGroups.TRIGGER);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && entity.hasData(MMAttachments.getENTITY_EYESIGHT().get())) {
            Level level = entity.level();
            LivingEntityEyesightAttachment eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT().get());
            eyesight.eyesightRange = entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);//更新射线距离
            if (eyesight.eyesightRange <= 0) return;
            eyesight.startPos = PhysicsHelperKt.toBVector3f(entity.getEyePosition());
            eyesight.view = PhysicsHelperKt.toBVector3f(entity.getForward().normalize().scale(eyesight.eyesightRange));
            eyesight.endPos = eyesight.startPos.add(eyesight.view);
            level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                eyesight.trigger.setPhysicsLocation(PhysicsHelperKt.toBVector3f(entity.getPosition(1f)));
                eyesight.targetBodies.clear();//清空射线检测结果列表
                eyesight.sortedTargetBodies.clear();//清空排序后的射线检测结果列表
                eyesight.sortedTargets.clear();//清空排序后的射线检测结果列表
                var rayTestResults = level.getPhysicsLevel().getWorld().rayTest(eyesight.startPos, eyesight.endPos);
                rayTestResults.forEach(//获取射线命中物体
                        result -> {
                            PhysicsCollisionObject object = result.getCollisionObject();
                            if (object instanceof PhysicsRigidBody body
                                    && PhysicsBodyExtensionKt.getOwner(body) != null
                                    && PhysicsBodyExtensionKt.getOwner(body) != entity) {//如果射线命中物体是刚体
                                eyesight.targetBodies.put(body, result);//将射线命中物体和相应信息存入targets列表
                                eyesight.sortedTargetBodies.add(body);//将射线命中物体加入sortedTargets列表
                                if (PhysicsBodyExtensionKt.getOwner(body) instanceof InteractBoxes interactBoxes) {
                                    int interactBoxIndex = result.triangleIndex();
                                    InteractBox interactBox = interactBoxes.getInteractBox(interactBoxIndex);
                                    if (interactBox != null
                                            && interactBox.interactMode == InteractBox.InteractMode.ACCURATE
                                            && interactBox.isEnabled())
                                        eyesight.sortedTargets.add(interactBox);
                                } else {
                                    eyesight.sortedTargets.add(PhysicsBodyExtensionKt.getOwner(body));
                                }
                            }
                        }
                );
                eyesight.sortedTargetBodyCache.clear();
                eyesight.sortedTargetBodyCache.addAll(eyesight.sortedTargetBodies);
                eyesight.targetBodyCache.clear();
                eyesight.targetBodyCache.putAll(eyesight.targetBodies);
                eyesight.sortedTargetCache.clear();
                eyesight.sortedTargetCache.addAll(eyesight.sortedTargets);
                eyesight.fastInteractBoxes.clear();//清空交互判定区列表
                level.getPhysicsLevel().getWorld().contactTest(eyesight.trigger, eyesight);
                level.getPhysicsLevel().submitImmediateTask(PPhase.POST, () -> {
                    eyesight.fastInteractBoxCache.clear();
                    eyesight.fastInteractBoxCache.addAll(eyesight.fastInteractBoxes);
                    return null;
                });
                return null;
            });
        }
    }

    @Override
    public void collision(PhysicsCollisionEvent event) {
        PhysicsCollisionObject interactHitBox;
        int interactBoxIndex;
        if (event.getObjectA() == this.trigger) {
            interactHitBox = event.getObjectB();
            interactBoxIndex = event.getIndex1();
        } else if (event.getObjectB() == this.trigger) {
            interactHitBox = event.getObjectA();
            interactBoxIndex = event.getIndex0();
        } else return;//事件与交互判定无关时提前返回
        if (PhysicsBodyExtensionKt.getOwner(interactHitBox) instanceof InteractBoxes interactBoxes) {
            InteractBox interactBox = interactBoxes.getInteractBox(interactBoxIndex);
            if (interactBox != null) {
                InteractBox.InteractMode mode = interactBox.interactMode;
                if (mode == InteractBox.InteractMode.FAST) {
                    this.fastInteractBoxes.add(interactBox);
                }
            }
        }
    }

    /**
     * 获取指向的最近的尚未被占用的部件连接点，如果没有则返回null
     *
     * @return 线段命中的最近的尚未被占用的部件连接点
     */
    public AbstractConnector getEmptyConnector() {
        if (!sortedTargetBodyCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetBodyCache) {
                if (PhysicsBodyExtensionKt.getOwner(body) instanceof SubPart subPart && targetBodyCache.get(body) instanceof PhysicsRayTestResult rayTestResult) {//如果射线命中物体是部件
                    rayTestResult.getHitFraction();//获取距离命中点最近的可用部件接口
                    Vector3f hitPoint = PhysicsHelperKt.toBVector3f(owner.position()
                            .add(0, owner.getEyeHeight(), 0)
                            .add(owner.getViewVector(1).normalize().scale(this.eyesightRange * rayTestResult.getHitFraction())));
                    AbstractConnector result = null;
                    float distance = Float.MAX_VALUE;
                    for (AbstractConnector connector : subPart.connectors.values()) {
                        if (!connector.internal && !connector.hasPart() && connector.body != null) {
                            Vector3f attachPos = connector.body.getPhysicsLocation(null);
                            float dist = attachPos.subtract(hitPoint).lengthSquared();
                            if (dist < distance) {//如果距离更近
                                distance = dist;//更新距离
                                result = connector;//更新结果
                            }
                        }
                    }
                    return result;
                } else if (PhysicsBodyExtensionKt.getOwner(body) instanceof AbstractConnector connector)
                    return connector;
            }
        }
        return null;
    }

    /**
     * 获取指向的最近的已连接的部件连接点，如果没有则返回null
     *
     * @return 线段命中的最近的已连接的部件连接点
     */
    public AbstractConnector getAttachedConnector() {
        if (!sortedTargetBodyCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetBodyCache) {
                if (PhysicsBodyExtensionKt.getOwner(body) instanceof SubPart subPart && targetBodyCache.get(body) instanceof PhysicsRayTestResult rayTestResult) {//如果射线命中物体是部件
                    rayTestResult.getHitFraction();//获取距离命中点最近的可用部件接口
                    Vector3f hitPoint = PhysicsHelperKt.toBVector3f(owner.position()
                            .add(0, owner.getEyeHeight(), 0)
                            .add(owner.getViewVector(1).normalize().scale(this.eyesightRange * rayTestResult.getHitFraction())));
                    AbstractConnector result = null;
                    float distance = Float.MAX_VALUE;
                    for (AbstractConnector connector : subPart.connectors.values()) {
                        if (!connector.internal && connector.hasPart()) {
                            Vector3f attachPos = MMMath.relPointWorldPos(connector.offsetFromMassCenter.getTranslation(), subPart.body);
                            float dist = attachPos.subtract(hitPoint).lengthSquared();
                            if (dist < distance) {//如果距离更近
                                distance = dist;//更新距离
                                result = connector;//更新结果
                            }
                        }
                    }
                    return result;
                } else if (PhysicsBodyExtensionKt.getOwner(body) instanceof AbstractConnector connector)
                    return connector;
            }
        }
        return null;
    }

    /**
     * 获取指向的最近的零件，如果没有则返回null
     *
     * @return 线段命中的最近的零件
     */
    public SubPart getSubPart() {
        if (!sortedTargetBodyCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetBodyCache) {
                if (PhysicsBodyExtensionKt.getOwner(body) != null && PhysicsBodyExtensionKt.getOwner(body) instanceof SubPart part) {
                    return part;
                }
            }
        }
        return null;
    }

    /**
     * 获取命中点坐标
     *
     * @param result 射线检测结果
     * @return 命中点坐标
     */
    public Vector3f getHitPoint(PhysicsRayTestResult result) {
        return startPos.add(view.mult(result.getHitFraction()));
    }

    /**
     * 获取命中点坐标
     *
     * @param hitFraction 命中点距离起点的比例
     * @return 命中点坐标
     */
    public Vector3f getHitPoint(float hitFraction) {
        return startPos.add(view.mult(hitFraction));
    }

    /**
     * 获取指向的最近的实体，如果没有则返回null
     *
     * @return 线段命中的最近的实体
     */
    public Entity getEntity() {
        if (!sortedTargetBodyCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetBodyCache) {
                if (PhysicsBodyExtensionKt.getOwner(body) != null && PhysicsBodyExtensionKt.getOwner(body) instanceof Entity entity) {
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * 获取射线检测命中的零件的碰撞箱，如果没有则返回null
     *
     * @return 射线检测命中的零件的碰撞箱
     */
    @Nullable
    public HitBox getHitBox() {
        HitBox result = null;
        for (PhysicsRigidBody body : sortedTargetBodyCache) {
            if (PhysicsBodyExtensionKt.getOwner(body) != null && PhysicsBodyExtensionKt.getOwner(body) instanceof SubPart subPart) {
                result = subPart.getHitBox(getTargetBodyCache().get(body).triangleIndex());
            }
        }
        return result;
    }

    public InteractBox getAccurateInteractBox() {
        if (!sortedTargetCache.isEmpty()) {
            for (Object owner : sortedTargetCache) {
                if (owner instanceof InteractBox interactBox
                        && interactBox.interactMode == InteractBox.InteractMode.ACCURATE && interactBox.isEnabled()) {
                    return interactBox;
                } else if (owner instanceof AbstractConnector) {
                    continue;
                } else return null;
            }
        }
        return null;
    }

    public InteractBox getFastInteractBox() {
        if (!fastInteractBoxCache.isEmpty()) {
            for (InteractBox interactBox : fastInteractBoxCache) {
                if (interactBox.isEnabled()) {
                    return interactBox;
                }
            }
        }
        return null;
    }


    /**
     * 客户端尝试与交互判定区交互，先尝试精确交互，如果没有则尝试快速交互
     */
    public void clientInteract() {
        InteractBox interactBox = getAccurateInteractBox();
        if (interactBox == null) interactBox = getFastInteractBox();
        if (interactBox != null) {
            SubPart subPart = interactBox.subPart;
            Part part = subPart.part;
            VehicleCore vehicle = part.vehicle;
            PacketDistributor.sendToServer(new SubsystemInteractPayload(vehicle.uuid, part.uuid, subPart.name, interactBox.name));
        }
    }
}
