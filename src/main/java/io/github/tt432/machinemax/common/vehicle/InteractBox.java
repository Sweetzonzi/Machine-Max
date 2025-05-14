package io.github.tt432.machinemax.common.vehicle;

import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker;
import cn.solarmoon.spark_core.physics.host.PhysicsHost;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CollisionShape;
import com.jme3.bullet.objects.PhysicsGhostObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import io.github.tt432.machinemax.common.registry.MMAttachments;
import io.github.tt432.machinemax.common.vehicle.attr.InteractBoxAttr;
import io.github.tt432.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

public class InteractBox implements PhysicsHost, PhysicsCollisionObjectTicker {
    public final String name;
    public final SubPart subPart;
    public final InteractMode interactMode;
    public final PhysicsCollisionObject body;
    public AbstractSubsystem subsystem;

    public enum InteractMode {
        FAST,
        ACCURATE
    }

    public InteractBox(SubPart subPart, InteractBoxAttr attr, CollisionShape shape) {
        this.subPart = subPart;
        this.name = attr.interactBoxName();
        this.interactMode = InteractMode.valueOf(attr.mode().toUpperCase());
        if (interactMode == InteractMode.FAST) this.body = new PhysicsGhostObject(name, this, shape);
        else {
            this.body = new PhysicsRigidBody(name, this, shape);
            ((PhysicsRigidBody) this.body).setKinematic(true);
        }
        body.setCollisionGroup(VehicleManager.COLLISION_GROUP_NO_COLLISION);
        body.setCollideWithGroups(VehicleManager.COLLISION_GROUP_NONE);
        bindBody(body, getPhysicsLevel(), true, body -> {
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
        } else if (body instanceof PhysicsGhostObject ghostObject) {
            ghostObject.setPhysicsLocation(position);
            ghostObject.setPhysicsRotation(rotation);
            if (subsystem != null) {//存在可交互子系统时，通知重叠实体的交互接口
                var overlappingObjects = ghostObject.getOverlappingObjects();
                for (var obj : overlappingObjects) {
                    if (obj instanceof PhysicsRigidBody && obj.getOwner() instanceof LivingEntity entity) {
                        if (entity.hasData(MMAttachments.getENTITY_EYESIGHT())) {
                            var eyesight = entity.getData(MMAttachments.getENTITY_EYESIGHT());
                            eyesight.addFastInteractBox(ghostObject);
                            entity.sendSystemMessage(Component.literal("You can interact with this vehicle using the fast interact box."));
                        }
                    }
                }
            }
        }
    }

    @NotNull
    @Override
    public PhysicsLevel getPhysicsLevel() {
        return subPart.getPhysicsLevel();
    }
}
