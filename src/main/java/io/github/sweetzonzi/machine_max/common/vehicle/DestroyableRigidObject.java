package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.CompoundCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import lombok.Getter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

@Getter
abstract public class DestroyableRigidObject extends DestroyableObject implements PhysicsHost {
    public CompoundCollisionShape collisionShape;
    public final PhysicsRigidBody body;
    private final HashMap<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();
    protected boolean updateLock = true;//是否禁止同步应用位姿数据到刚体

    protected DestroyableRigidObject(Level level, CompoundCollisionShape shape, float mass) {
        super(level);
        if (level.isClientSide()) updateLock = false;
        this.collisionShape = shape;
        this.body = new PhysicsRigidBody(shape, mass);
        if (level.isClientSide()) body.setKinematic(true);
    }

    @Override
    public void postTick() {
        if (!level.isClientSide() && body.isInWorld() && (body.isActive() || body.isKinematic())) {
            //从刚体同步数据
            oldTransform = transform.clone();
            transform = PhysicsBodyExtensionKt.stateOf(body).getTransform();
            updateLock = true;//锁定刚体数据，仅利用服务端刚体数据更新同步用数据
            setPosition(transform.getTranslation());
            setRotation(transform.getRotation());
            setLinearVelocity(body.getLinearVelocity(null));
            setAngularVelocity(body.getAngularVelocity(null));
            updateLock = false;//解锁刚体数据，允许set时应用位姿数据到刚体
        }
        super.postTick();//发送所有变化的数据
    }

    @Override
    public void prePhysicsTick() {
        super.prePhysicsTick();
        if (level.isClientSide()) {
            body.setLinearVelocity(getLinearVelocity());
            body.setAngularVelocity(getAngularVelocity());
        }
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level.isClientSide()) return;//服务器在需同步数据变化时不做特殊处理
        if (key.equals(DATA_POS_ID)) {
            body.setPhysicsLocation(PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_POS_ID)));//应用到刚体
        } else if (key.equals(DATA_ROT_ID)) {
            body.setPhysicsRotation(SparkMathKt.toBQuaternion(getSyncedData().get(DATA_ROT_ID)));//应用到刚体
        }
    }

    @Override
    public void addToLevel() {
        getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
            if (body.isInWorld()) return null;
            getPhysicsLevel().getWorld().addCollisionObject(body);
            return null;
        });
        super.addToLevel();
    }

    @Override
    public void destroy() {
        super.destroy();
        if (body.isInWorld()) {
            PhysicsBodyExtensionKt.setOwner(body, null);
            PhysicsBodyExtensionKt.removePhysicsBody(getLevel(), this.body);
        }
    }

    @Override
    public void setPosition(Vector3f position) {
        super.setPosition(position);
        if (!updateLock) {
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setPhysicsLocation(position);
                return null;
            });
        }
    }

    @Override
    public void setRotation(Quaternion rotation) {
        super.setRotation(rotation);
        if (!updateLock) {
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setPhysicsRotation(rotation);
                return null;
            });
        }
    }

    @Override
    public void setLinearVelocity(Vector3f linearVelocity) {
        super.setLinearVelocity(linearVelocity);
        if (!updateLock) {
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setLinearVelocity(linearVelocity);
                return null;
            });
        }
    }

    @Override
    public void setAngularVelocity(Vector3f angularVelocity) {
        super.setAngularVelocity(angularVelocity);
        if (!updateLock) {
            getPhysicsLevel().submitImmediateTask(PPhase.ALL, () -> {
                body.setAngularVelocity(angularVelocity);
                return null;
            });
        }
    }

    @Override
    public @NotNull PhysicsLevel getPhysicsLevel() {
        return getLevel().getPhysicsLevel();
    }
}
