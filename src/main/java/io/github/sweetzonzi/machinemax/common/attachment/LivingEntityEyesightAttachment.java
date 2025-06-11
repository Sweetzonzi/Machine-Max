package io.github.sweetzonzi.machinemax.common.attachment;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionEvent;
import com.jme3.bullet.collision.PhysicsCollisionListener;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.input.KeyBinding;
import io.github.sweetzonzi.machinemax.common.registry.MMAttachments;
import io.github.sweetzonzi.machinemax.common.vehicle.*;
import io.github.sweetzonzi.machinemax.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machinemax.network.payload.SubsystemInteractPayload;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArraySet;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class LivingEntityEyesightAttachment implements PhysicsCollisionListener {
    public final LivingEntity owner;
    public final PhysicsGhostObject trigger;
    private final ConcurrentMap<PhysicsRigidBody, PhysicsRayTestResult> targets = new ConcurrentHashMap<>(2);
    private final ConcurrentSkipListSet<PhysicsRigidBody> sortedTargets = new ConcurrentSkipListSet<>();
    private final CopyOnWriteArraySet<InteractBox> fastInteractBoxes = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<InteractBox> accurateInteractBoxes = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<InteractBox> fastInteractBoxCache = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<InteractBox> accurateInteractBoxCache = new CopyOnWriteArraySet<>();
    private HashMap<PhysicsRigidBody, PhysicsRayTestResult> targetsCache = new HashMap<>(2);
    private List<PhysicsRigidBody> sortedTargetsCache = new LinkedList<>();
    private double eyesightRange;

    public LivingEntityEyesightAttachment(LivingEntity entity) {
        this.owner = entity;
        var boundingBox = entity.getBoundingBox();
        BoxCollisionShape shape = new BoxCollisionShape((float) (boundingBox.getXsize() * 0.5f), (float) (boundingBox.getYsize() * 0.5f), (float) (boundingBox.getZsize() * 0.5f));
        this.trigger = new PhysicsGhostObject("interact_trigger", entity, shape);
        this.trigger.setPhysicsLocation(PhysicsHelperKt.toBVector3f(entity.getPosition(1f)));
        this.trigger.setCollisionGroup(VehicleManager.COLLISION_GROUP_NO_COLLISION);
        this.trigger.setCollideWithGroups(VehicleManager.COLLISION_GROUP_INTERACT);
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && entity.hasData(MMAttachments.getENTITY_EYESIGHT().get())) {
            Level level = entity.level();
            LivingEntityEyesightAttachment eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT().get());
            eyesight.eyesightRange = entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);//更新射线距离
            if (eyesight.eyesightRange <= 0) return;
            level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                eyesight.trigger.setPhysicsLocation(PhysicsHelperKt.toBVector3f(entity.getPosition(1f)));
                Vector3f startPos = PhysicsHelperKt.toBVector3f(entity.getEyePosition());
                Vector3f endPos = startPos.add(PhysicsHelperKt.toBVector3f(
                        entity.getViewVector(1).normalize().scale(eyesight.eyesightRange)
                ));
                eyesight.targets.clear();//清空射线检测结果列表
                eyesight.sortedTargets.clear();//清空排序后的射线检测结果列表
                eyesight.accurateInteractBoxes.clear();//清空交互判定区列表
                var rayTestResults = level.getPhysicsLevel().getWorld().rayTest(startPos, endPos);
                rayTestResults.forEach(//获取射线命中物体
                        result -> {
                            PhysicsCollisionObject object = result.getCollisionObject();
                            if (object instanceof PhysicsRigidBody body && body.getOwner() != null && body.getOwner() != entity) {//如果射线命中物体是刚体
                                eyesight.targets.put(body, result);//将射线命中物体和相应信息存入targets列表
                                eyesight.sortedTargets.add(body);//将射线命中物体加入sortedTargets列表
                                if (body.getOwner() instanceof SubPart.InteractBoxes interactBoxes) {
                                    int interactBoxIndex = result.triangleIndex();
                                    InteractBox interactBox = interactBoxes.getInteractBox(interactBoxIndex);
                                    if (interactBox != null && interactBox.interactMode == InteractBox.InteractMode.ACCURATE)
                                        eyesight.accurateInteractBoxes.add(interactBox);
                                }
                            }
                        }
                );
                eyesight.accurateInteractBoxCache.clear();
                eyesight.accurateInteractBoxCache.addAll(eyesight.accurateInteractBoxes);
                eyesight.sortedTargetsCache = eyesight.sortedTargets.stream().toList();
                eyesight.targetsCache = new HashMap<>(eyesight.targets);
                eyesight.fastInteractBoxes.clear();//清空交互判定区列表
                level.getPhysicsLevel().getWorld().contactTest(eyesight.trigger, eyesight);
                level.getPhysicsLevel().submitImmediateTask(PPhase.POST, () -> {
                    eyesight.fastInteractBoxCache.clear();
                    eyesight.fastInteractBoxCache.addAll(eyesight.fastInteractBoxes);
                    return null;
                });
                return null;
            });
            if (entity instanceof Player player && player.isLocalPlayer()) {
                InteractBox interactBox = eyesight.getAccurateInteractBox();
                if (interactBox == null) interactBox = eyesight.getFastInteractBox();
                if (interactBox != null) {
                    player.displayClientMessage(Component.translatable("message.machine_max.watch_interact_box_info", KeyBinding.generalInteractKey.getTranslatedKeyMessage(), interactBox.name), true);
                }
            }
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
        if (interactHitBox.getOwner() instanceof SubPart.InteractBoxes interactBoxes) {
            InteractBox interactBox = interactBoxes.getInteractBox(interactBoxIndex);
            if (interactBox!=null) {
                InteractBox.InteractMode mode = interactBox.interactMode;
                if (mode == InteractBox.InteractMode.FAST) {
                    this.fastInteractBoxes.add(interactBox);
                }
            }
        }
    }

    /**
     * 获取指向的最近的部件对接口，如果没有则返回null
     *
     * @return 线段命中的最近的部件对接口
     */
    public AbstractConnector getConnector() {
        if (!sortedTargetsCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetsCache) {
                if (body.getOwner() != null) {
                    if (body.getOwner() instanceof SubPart && targetsCache.get(body) instanceof PhysicsRayTestResult rayTestResult) {//如果射线命中物体是部件
                        rayTestResult.getHitFraction();//获取距离命中点最近的可用部件接口
                        Vector3f hitPoint = PhysicsHelperKt.toBVector3f(owner.position()
                                .add(0, owner.getEyeHeight(), 0)
                                .add(owner.getViewVector(1).normalize().scale(this.eyesightRange * rayTestResult.getHitFraction())));
                        AbstractConnector result = null;
                        float distance = Float.MAX_VALUE;
                        for (AbstractConnector connector : ((SubPart) body.getOwner()).connectors.values()) {
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
                    } else if (body.getOwner() instanceof AbstractConnector connector) return connector;
                }
            }
        }
        return null;
    }

    /**
     * 获取指向的最近的部件，如果没有则返回null
     *
     * @return 线段命中的最近的部件
     */
    public Part getPart() {
        if (!sortedTargetsCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetsCache) {
                if (body.getOwner() != null && body.getOwner() instanceof SubPart part) {
                    return part.part;
                }
            }
        }
        return null;
    }

    /**
     * 获取指向的最近的实体，如果没有则返回null
     *
     * @return 线段命中的最近的实体
     */
    public Entity getEntity() {
        if (!sortedTargetsCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetsCache) {
                if (body.getOwner() != null && body.getOwner() instanceof Entity entity) {
                    return entity;
                }
            }
        }
        return null;
    }

    public InteractBox getAccurateInteractBox() {
        if (!sortedTargetsCache.isEmpty()) {
            for (PhysicsRigidBody body : sortedTargetsCache) {
                if (body.getOwner() != null && body.getOwner() instanceof SubPart.InteractBoxes) {
                    for (InteractBox interactBox : accurateInteractBoxCache) {
                        if (interactBox.interactMode == InteractBox.InteractMode.ACCURATE) return interactBox;
                    }
                } else if (body.getOwner() != null && body.getOwner() instanceof AbstractConnector) {
                    continue;
                } else return null;
            }
        }
        return null;
    }

    public InteractBox getFastInteractBox() {
        if (!fastInteractBoxCache.isEmpty()) {
            return fastInteractBoxCache.iterator().next();
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
