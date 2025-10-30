package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import lombok.Getter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
@Getter
abstract public class DestroyableRigidObject extends DestroyableObject implements PhysicsHost {
    public CompoundCollisionShape collisionShape;
    public final PhysicsRigidBody body;
    private final HashMap<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();

    protected DestroyableRigidObject(Level level, CompoundCollisionShape shape, float mass) {
        super(level);
        this.collisionShape = shape;
        this.body = new PhysicsRigidBody(shape, mass);
        if (level.isClientSide()) body.setKinematic(true);
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        if (level.isClientSide()) {
            body.setLinearVelocity(linearVelocity);
            body.setAngularVelocity(angularVelocity);
        }
    }

    @Override
    void setPosition(Vector3f position) {
        if (!updateLock) {
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setPhysicsLocation(position);
                return null;
            });
        }
    }

    @Override
    void setRotation(Quaternion rotation) {
        if (!updateLock) {
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setPhysicsRotation(rotation);
                return null;
            });
        }
    }

    @Override
    void setLinearVelocity(Vector3f linearVelocity) {
        if (!updateLock) {
            this.linearVelocity = linearVelocity;
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setLinearVelocity(linearVelocity);
                return null;
            });
        }
    }

    @Override
    void setAngularVelocity(Vector3f angularVelocity) {
        if (!updateLock) {
            this.angularVelocity = angularVelocity;
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setAngularVelocity(angularVelocity);
                return null;
            });
        }
    }

    @Override
    public Vector3f getLinearVelocity() {
        if (!getLevel().isClientSide()){
            this.linearVelocity = body.getLinearVelocity(null);
        }
        return this.linearVelocity;
    }

    @Override
    public Vector3f getAngularVelocity() {
        if (!getLevel().isClientSide()){
            this.angularVelocity = body.getAngularVelocity(null);
        }
        return this.angularVelocity;
    }

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return getLevel().getPhysicsLevel();
    }
}
