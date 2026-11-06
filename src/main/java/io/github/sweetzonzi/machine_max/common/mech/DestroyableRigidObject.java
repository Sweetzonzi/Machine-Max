package io.github.sweetzonzi.machine_max.common.mech;

import cn.solarmoon.spark_core.api.SparkLevel;
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
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

@Getter
abstract public class DestroyableRigidObject extends DestroyableObject implements PhysicsHost {
    public CompoundCollisionShape collisionShape;
    public final PhysicsRigidBody body;
    private final HashMap<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();
    protected boolean updateLock = true;//是否禁止同步应用位姿数据到刚体
    protected static final EntityDataAccessor<Boolean> IS_ACTIVE_ID = SynchedEntityData.defineId(DestroyableRigidObject.class, EntityDataSerializers.BOOLEAN);

    protected DestroyableRigidObject(Level level, CompoundCollisionShape shape, float mass) {
        super(level);
        if (level.isClientSide()) updateLock = false;
        this.collisionShape = shape;
        this.body = new PhysicsRigidBody(shape, mass);
        if (level.isClientSide()) body.setKinematic(true);
    }

    @Override
    public void postTick() {
        if (!level.isClientSide() && body.isInWorld()) {
            if ((body.isActive() || body.isKinematic())) {
                //从刚体同步数据
                oldTransform = transform.clone();
                transform = PhysicsBodyExtensionKt.stateOf(body).getTransform();
                updateLock = true;//锁定刚体数据，仅利用服务端刚体数据更新同步用数据
                setPosition(transform.getTranslation());
                setRotation(transform.getRotation());
                setLinearVelocity(body.getLinearVelocity(null));
                setAngularVelocity(body.getAngularVelocity(null));
                getSyncedData().set(IS_ACTIVE_ID, true);
                updateLock = false;//解锁刚体数据，允许set时应用位姿数据到刚体
            } else if (!body.isActive()) { // 更新休眠状态
                // 休眠后刚体不再步进，同步值会停在最后一次活跃时的读数上。速度必须显式清零：
                // 客户端把这份残值当成本帧速度写给运动学刚体，履带相位、轮面接触音与控制器
                // 的车速反馈都读它，于是停车后轮子看着停了、履带还在转、摩擦声还在响。
                setLinearVelocity(Vector3f.ZERO);
                setAngularVelocity(Vector3f.ZERO);
                getSyncedData().set(IS_ACTIVE_ID, false);
            }
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

    /**
     * 呈现位姿推进后把同一份位姿落到客户端的运动学镜像刚体上。
     * <p>
     * 客户端的刚体只承载呈现位，不参与模拟（构造时 {@code setKinematic(true)}），因此它就该与模型
     * 同一条时间轴：否则 Spark 的刚体调试框（读 {@code stateOf(body)} 的
     * {@code lastTransform/transform}）、以及 {@code SubPart#postTick} 从同一处取出的实体包围盒，
     * 会比模型早到一至两个 tick，两个调试框各走各的。
     * <p>
     * <b>时机是硬约束。</b> 本方法由 {@link DestroyableObject#advancePresentationPose()} 在客户端
     * {@code ClientTickEvent.Pre} 里调用（主线程），早于 {@code LevelTickEvent.Pre} 里
     * {@code PhysicsLevel#requestStep} 对每个刚体做的 {@code stateOf(...).update()} 快照，
     * 因此物理线程与渲染读到的都是本次推进后的位姿。
     */
    @Override
    protected void onPresentationPoseAdvanced() {
        super.onPresentationPoseAdvanced();
        if (!level.isClientSide() || isRemoved) return;
        body.setPhysicsLocation(transform.getTranslation());
        body.setPhysicsRotation(transform.getRotation());
    }

    @Override
    public void addToLevel() {
        getPhysicsLevel().submitImmediateTask(PPhase.PRE, () -> {
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

    public boolean isActive() {
        return getSyncedData().get(IS_ACTIVE_ID);
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
        return SparkLevel.getPhysicsLevel(level);
    }

    @Override
    protected void defineSyncedData(SynchedEntityData.Builder builder) {
        builder.define(IS_ACTIVE_ID, true);
    }
}
