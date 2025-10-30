package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
public abstract class DestroyableObject implements SyncedDataHolder {
    protected static final AtomicInteger ENTITY_COUNTER = new AtomicInteger();
    //渲染属性 Renderer attributes
    public int hurtTime = 0;
    public int hurtDuration = 3;
    //物理属性 Physical attributes
    public Transform transform = new Transform();
    public Transform oldTransform = new Transform();//用于渲染插值
    public Transform syncTransformBuffer = new Transform();//用于缓存同步数据
    public long lastSync;//记录距离上次同步经过的时间，用于外推
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_POS_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Quaternionf> DATA_ROT_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.QUATERNION);
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_VEL_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<org.joml.Vector3f> DATA_ANG_VEL_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    //常规属性 General attributes
    public final Level level;
    @Setter
    private int id = ENTITY_COUNTER.incrementAndGet();//客户端的ID应当根据收到的创建包更新
    private static final EntityDataAccessor<Float> DATA_DURABILITY_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.FLOAT);
    public volatile boolean destroyed = false;
    protected final ConcurrentLinkedQueue<Pair<Float, PartDamageData>> accumulatedDamage = new ConcurrentLinkedQueue<>();
    protected final SynchedEntityData synchedData;
    //运行中
    public int tickCount = 0;
    public int physicsTickCount = 0;
    public volatile boolean isRemoved = false;

    protected DestroyableObject(Level level) {
        this.level = level;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_POS_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_ROT_ID, new Quaternionf());
        syncheddata$builder.define(DATA_VEL_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_ANG_VEL_ID, new org.joml.Vector3f());
        syncheddata$builder.define(DATA_DURABILITY_ID, 20.0F);
        this.defineSynchedData(syncheddata$builder);
        this.synchedData = syncheddata$builder.build();
    }

    public void tick() {
        if (isRemoved) return;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        if (!level.isClientSide()) {
            //处理各线程造成的伤害
            handleAccumulatedDamage();
        } else {
            //客户端处理同步位姿数据
            clientSyncPose();
        }
        //判定摧毁
        if (!destroyed && getDurability() <= 0) onDestroyed();
        syncToClient();
    }

    public void prePhysicsTick() {
        if (isRemoved) return;
        physicsTickCount++;
    }

    public void postPhysicsTick() {
        if (isRemoved) return;
    }

    /**
     * <p>线程安全地对部件造成伤害，伤害会被在主线程统一处理，参见 {@link #handleAccumulatedDamage()}</p>
     * <p>Accumulates damage to the part thread safely, which will be handled in the main thread, see {@link #handleAccumulatedDamage()}</p>
     *
     * @param damage 伤害值 damage value
     * @param data   伤害源、命中点、判定区等 damage source, hit point, hit box, etc.
     */
    protected void accumulateDamage(float damage, PartDamageData data) {
        if (damage > 0) {
            hurtTime = hurtDuration;
            accumulatedDamage.add(Pair.of(damage, data));
        }
    }

    protected void onDestroyed() {
        this.destroyed = true;
    }

    /**
     * <p>处理各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link DestroyableObject#tick()}</p>
     * <p>Handles the damage caused by each thread and applies it to the subsystem, which will be handled in the main thread, see {@link DestroyableObject#tick()}</p>
     */
    abstract protected void handleAccumulatedDamage();


    protected void onCollideWithTerrain(
            PhysicsRigidBody other,
            Vector3f normal,
            Vector3f worldContactPoint,
            Vector3f localContactPoint,
            Vector3f otherLocalContactPoint,
            Vector3f contactVel,
            int hitBoxIndex,
            int otherHitBoxIndex,
            float impactAngle,
            long manifoldPointId
    ) {
    }

    protected void onCollideWithRigid(
            PhysicsRigidBody other,
            Vector3f normal,
            Vector3f worldContactPoint,
            Vector3f localContactPoint,
            Vector3f otherLocalContactPoint,
            Vector3f contactVel,
            int hitBoxIndex,
            int otherHitBoxIndex,
            float impactAngle,
            long manifoldPointId
    ) {
    }

    protected void onCollideWithEntity(
            PhysicsRigidBody other,
            Vector3f normal,
            Vector3f worldContactPoint,
            Vector3f localContactPoint,
            Vector3f otherLocalContactPoint,
            Vector3f contactVel,
            int hitBoxIndex,
            int otherHitBoxIndex,
            float impactAngle,
            long manifoldPointId
    ) {
    }

    public void addToLevel() {
        ObjectManager.addDestroyableObject(this);
    }

    public void destroy() {
        isRemoved = true;
        ObjectManager.removeDestroyableObject(this.level, this.getId());
    }

    /**
     * <p>立即同步发生变化的数据至客户端</p>
     */
    public void syncToClient() {

    }

    protected void clientSyncPose() {
        if (syncTransformBuffer != null) {
            oldTransform = transform.clone();
            transform = syncTransformBuffer.clone();
            syncTransformBuffer = null;
            lastSync = System.nanoTime();
        }
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> dataValues) {
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        if (!level.isClientSide()) return;//服务器在需同步数据变化时不做特殊处理
        if (key.equals(DATA_DURABILITY_ID)) {
            hurtTime = hurtDuration;
        } else if (key.equals(DATA_POS_ID)) {
            Vector3f position = PhysicsHelperKt.toBVector3f(getSynchedData().get(DATA_POS_ID));
//            this.setPosition(position);//应用到刚体(若有)
        } else if (key.equals(DATA_ROT_ID)) {
            Quaternion rotation = SparkMathKt.toBQuaternion(getSynchedData().get(DATA_ROT_ID));
//            this.setRotation(rotation);//应用到刚体(若有)
        } else if (key.equals(DATA_VEL_ID)) {
            Vector3f linearVelocity = PhysicsHelperKt.toBVector3f(getSynchedData().get(DATA_VEL_ID));
            this.setLinearVelocity(linearVelocity);//应用到刚体(若有)
        } else if (key.equals(DATA_ANG_VEL_ID)) {
            Vector3f angularVelocity = PhysicsHelperKt.toBVector3f(getSynchedData().get(DATA_ANG_VEL_ID));
            this.setAngularVelocity(angularVelocity);//应用到刚体(若有)
        }
        if (key.equals(DATA_POS_ID) || key.equals(DATA_ROT_ID)) {
            //更新位姿同步缓冲区
            if (syncTransformBuffer == null) {
                syncTransformBuffer = new Transform(getPosition(), getRotation());
            } else if (key.equals(DATA_POS_ID)) {
                syncTransformBuffer.setTranslation(getPosition());
            } else if (key.equals(DATA_ROT_ID)) {
                syncTransformBuffer.setRotation(getRotation());
            }
        }
    }

    protected abstract void defineSynchedData(SynchedEntityData.Builder builder);

    public Vector3f getPosition() {
        return PhysicsHelperKt.toBVector3f(getSynchedData().get(DATA_POS_ID));
    }

    public Quaternion getRotation() {
        return SparkMathKt.toBQuaternion(getSynchedData().get(DATA_ROT_ID));
    }

    public Vector3f getLinearVelocity() {
        return PhysicsHelperKt.toBVector3f(getSynchedData().get(DATA_VEL_ID));
    }

    public Vector3f getAngularVelocity() {
        return PhysicsHelperKt.toBVector3f(getSynchedData().get(DATA_ANG_VEL_ID));
    }

    public void setPosition(Vector3f position) {
        getSynchedData().set(DATA_POS_ID, SparkMathKt.toVector3f(position));
    }

    public void setRotation(Quaternion rotation) {
        getSynchedData().set(DATA_ROT_ID, SparkMathKt.toQuaternionf(rotation));
    }

    public void setLinearVelocity(Vector3f linearVelocity) {
        getSynchedData().set(DATA_VEL_ID, SparkMathKt.toVector3f(linearVelocity));
    }

    public void setAngularVelocity(Vector3f angularVelocity) {
        getSynchedData().set(DATA_ANG_VEL_ID, SparkMathKt.toVector3f(angularVelocity));
    }

    public Vector3f getLinearVelocityLocal() {
        Vector3f result = getLinearVelocity();//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    @NotNull
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        long time = System.nanoTime();
        float deltaTime = (time - lastSync) / 1000000000.0F;//距离上次同步经过的时间(秒)
        if (deltaTime > 0.05f) {//大于1Tick，根据速度外推新的位置
            float partialTime = number.floatValue() * 0.05f;
            Vector3f translation = transform.getTranslation().add(getLinearVelocity().mult(deltaTime + partialTime));
            Transform result = SparkMathKt.lerp(oldTransform, transform, number.floatValue()).setTranslation(translation);
            return SparkMathKt.toMatrix4f(result.toTransformMatrix());
        } else {//否则插值计算位姿
            return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
        }
    }

    public float getDurability() {
        return this.synchedData.get(DATA_DURABILITY_ID);
    }

    public void setDurability(float durability) {
        this.synchedData.set(DATA_DURABILITY_ID, Mth.clamp(durability, 0.0F, this.getMaxDurability()));
    }

    abstract public float getMaxDurability();

    abstract public PhysicsLevel getPhysicsLevel();
}
