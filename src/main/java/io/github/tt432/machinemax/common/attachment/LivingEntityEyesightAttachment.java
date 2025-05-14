package io.github.tt432.machinemax.common.attachment;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.PhysicsRayTestResult;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.InteractBox;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.connector.AbstractConnector;
import lombok.Getter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;

@Getter
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class LivingEntityEyesightAttachment {
    public final LivingEntity owner;
    private final ConcurrentMap<PhysicsRigidBody, PhysicsRayTestResult> targets = new ConcurrentHashMap<>(2);
    private final ConcurrentSkipListSet<PhysicsRigidBody> sortedTargets = new ConcurrentSkipListSet<>();
    private final ConcurrentSkipListSet<PhysicsGhostObject> fastInteractBoxes = new ConcurrentSkipListSet<>();
    private HashMap<PhysicsRigidBody, PhysicsRayTestResult> targetsCache = new HashMap<>(2);
    private List<PhysicsRigidBody> sortedTargetsCache = new LinkedList<>();
    private double eyesightRange;

    public LivingEntityEyesightAttachment(LivingEntity entity) {
        this.owner = entity;
    }

    @SubscribeEvent
    public static void onTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity entity && entity.hasData(MMAttachments.getENTITY_EYESIGHT().get())) {
            Level level = entity.level();
            LivingEntityEyesightAttachment eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT().get());
            eyesight.eyesightRange = entity.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);//更新射线距离
            if (eyesight.eyesightRange <= 0) return;
            level.getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
                Vector3f startPos = PhysicsHelperKt.toBVector3f(entity.getEyePosition());
                Vector3f endPos = startPos.add(PhysicsHelperKt.toBVector3f(
                        entity.getViewVector(1).normalize().scale(eyesight.eyesightRange)
                ));
                eyesight.targets.clear();//清空targets列表
                eyesight.fastInteractBoxes.clear();//清空fastInteractBoxes列表
                eyesight.sortedTargets.clear();//清空sortedTargets列表
                var rayTestResults = level.getPhysicsLevel().getWorld().rayTest(startPos, endPos);
                rayTestResults.forEach(//获取射线命中物体
                        result -> {
                            PhysicsCollisionObject object = result.getCollisionObject();
                            if (object instanceof PhysicsRigidBody body && body.getOwner() != null && body.getOwner() != entity) {//如果射线命中物体是刚体
                                eyesight.targets.put(body, result);//将射线命中物体和相应信息存入targets列表
                                eyesight.sortedTargets.add(body);//将射线命中物体加入sortedTargets列表
//                                if (body.getOwner() instanceof SubPart subPart){
//                                    long hitId = subPart.collisionShape.findChild(result.triangleIndex()).getShape().nativeId();
//                                    boolean hasId = subPart.part.type.getHitBoxes().containsKey(hitId);
//                                    MachineMax.LOGGER.info("hit:{}, hasId:{}", hitId, hasId);
//                                }
                            }
                        }
                );
                eyesight.sortedTargetsCache = eyesight.sortedTargets.stream().toList();
                eyesight.targetsCache = new HashMap<>(eyesight.targets);
                return null;
            });
//            if(level instanceof ClientLevel && entity instanceof Player player){//正观察的部件
//                Part targetPart = eyesight.getPart();
//                if (targetPart!= null)
//                    player.sendSystemMessage(Component.nullToEmpty(targetPart.hitBoxName));
//            }
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
                            if (!connector.internal && !connector.hasPart() && connector.body!=null){
                                Vector3f attachPos =  connector.body.getPhysicsLocation(null);
                                float dist = attachPos.subtract(hitPoint).lengthSquared();
                                if(dist < distance) {//如果距离更近
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
            for (PhysicsRigidBody body : sortedTargetsCache){
                if (body.getOwner() != null && body.getOwner() instanceof InteractBox interactBox) {
                    return interactBox;
                }
            }
        }
        return null;
    }

    public InteractBox getFastInteractBox() {
        if (!fastInteractBoxes.isEmpty()) {
            for (PhysicsGhostObject interactBox : fastInteractBoxes) {
                if (interactBox.getUserObject() != null && interactBox.getUserObject() instanceof InteractBox interactBox1) {
                    return interactBox1;
                }
            }
        }
        return null;
    }

    public void addFastInteractBox(PhysicsGhostObject interactBox) {
        fastInteractBoxes.add(interactBox);
    }

    public void clientInteract() {}
}
