package io.github.sweetzonzi.machine_max.common.vehicle.interact;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.InteractBoxAttr;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class InteractBoxes extends ConcurrentHashMap<String, InteractBox> implements PhysicsHost {

    public final SubPart subPart;
    public final CompoundCollisionShape interactBoxShape;
    public final PhysicsRigidBody body;
    private final Map<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();

    public InteractBoxes(SubPart subPart, Map<String, InteractBoxAttr> boxes, CompoundCollisionShape interactBoxShape) {
        this.subPart = subPart;
        this.interactBoxShape = interactBoxShape;
        for (Map.Entry<String, InteractBoxAttr> entry : boxes.entrySet()) {
            String name = entry.getKey();
            InteractBox interactBox = new InteractBox(subPart, name, entry.getValue());
            this.put(name, interactBox);
        }
        this.body = new PhysicsRigidBody(interactBoxShape, 0);
        PhysicsBodyExtensionKt.setOwner(this.body, this);
        this.body.setContactResponse(false);
        this.body.setKinematic(true); // 非常诡异，不设置运动学模式会导致射线检测等判定不上
        this.body.setCollisionGroup(CollisionGroups.TRIGGER);
        this.body.setCollideWithGroups(CollisionGroups.NONE);
        PhysicsBodyExtensionKt.addPhysicsBody(subPart.getLevel(), this.body);
        this.body.shouldShowDebugBoxWhenNonColldeWith = true;
    }

    /**
     * 更新交互判定区的位置和姿态，使其与零件统一
     */
    public void updatePose(){
        this.body.setPhysicsLocation(this.subPart.getPosition());//应用到刚体
        this.body.setPhysicsRotation(this.subPart.getRotation());
    }

    public InteractBox getInteractBox(long childShapeId) {
        String name = this.subPart.attr.interactBoxNames.get(childShapeId);
        return this.get(name);
    }

    public InteractBox getInteractBox(int contactPointIndex) {
        try {
            long childShapeId = this.interactBoxShape.listChildren()[contactPointIndex].getShape().nativeId();
            return getInteractBox(childShapeId);
        } catch (IndexOutOfBoundsException e) {
            MachineMax.LOGGER.error("No matching child shape of interact box {}-{} found for contact point id: {}", subPart.part.name, subPart.name, contactPointIndex);
            return this.values().iterator().next();
        }
    }

    public void destroy() {
        this.clear();
        PhysicsBodyExtensionKt.removePhysicsBody(subPart.getLevel(), this.body);
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return subPart.getPhysicsLevel();
    }
}
