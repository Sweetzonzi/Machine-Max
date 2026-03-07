package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.physics.PhysicsHelperKt;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import io.github.sweetzonzi.machine_max.network.payload.SubPartSyncPayload;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
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
    public volatile Transform transform = new Transform();
    public volatile Transform oldTransform = new Transform();//用于渲染插值
    public Transform syncTransformBuffer = new Transform();//用于缓存同步数据
    public long lastSync;//记录距离上次同步经过的时间，用于外推
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_POS_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<Quaternionf> DATA_ROT_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.QUATERNION);
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_VEL_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    protected static final EntityDataAccessor<org.joml.Vector3f> DATA_ANG_VEL_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.VECTOR3);
    //常规属性 General attributes
    public final Level level;
    @Setter
    private int id = ENTITY_COUNTER.incrementAndGet();//客户端的ID应当根据收到的创建包更新
    protected static final EntityDataAccessor<Float> DATA_DURABILITY_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.FLOAT);
    protected static final EntityDataAccessor<Boolean> DATA_DESTROYED_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.BOOLEAN);
    protected static final EntityDataAccessor<Integer> DESTROY_TIME_ID = SynchedEntityData.defineId(DestroyableObject.class, EntityDataSerializers.INT);
    ConcurrentLinkedQueue<Pair<Float, PartDamageData>> accumulatedDamage = new ConcurrentLinkedQueue<>();
    protected final SynchedEntityData syncedData;
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
        syncheddata$builder.define(DATA_DESTROYED_ID, false);
        syncheddata$builder.define(DESTROY_TIME_ID, 200);
        this.defineSyncedData(syncheddata$builder);
        this.syncedData = syncheddata$builder.build();
    }

    public void preTick() {
        if (isRemoved) return;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        if (!level.isClientSide()) {
            handleAccumulatedDamage(); // 处理各线程造成的伤害
            syncToClient(); // 同步数据至客户端
        } else {
            //客户端处理同步位姿数据
            clientSyncPose();
        }
        //判定摧毁
        if (!level.isClientSide() && checkDestroyed())
            setDestroyed();
    }

    public void postTick() {
        if (!level.isClientSide()) {
            if (isDestroyed()) {//物体已被摧毁，倒计时结束后移除
                int destroyTime = getDestroyTime();
                if (destroyTime > 0) {
                    setDestroyTime(destroyTime - 1);
                }
            }
            syncToClient();
        }
        if (isDestroyed() && getDestroyTime() <= 0) this.destroy();
    }

    public void prePhysicsTick() {
        if (isRemoved) return;
        physicsTickCount++;
    }

    public void postPhysicsTick() {
    }

    /**
     * <p>线程安全地对部件造成伤害，伤害会被在主线程统一处理，参见 {@link #handleAccumulatedDamage()}</p>
     * <p>Accumulates damage to the part thread safely, which will be handled in the main thread, see {@link #handleAccumulatedDamage()}</p>
     *
     * @param damage 伤害值 damage value
     * @param data   伤害源、命中点、判定区等 damage source, hit point, hit box, etc.
     */
    public void accumulateDamage(float damage, PartDamageData data) {
        if (damage > 0) {
            hurtTime = hurtDuration;
            accumulatedDamage.add(Pair.of(damage, data));
        }
    }

    public boolean isDestroyed() {
        return getSyncedData().get(DATA_DESTROYED_ID);
    }

    protected boolean checkDestroyed() {
        return !getSyncedData().get(DATA_DESTROYED_ID) && getDurability() <= 0;
    }

    protected void setDestroyed() {
        getSyncedData().set(DATA_DESTROYED_ID, true);
    }

    /**
     * <p>处理各线程造成的伤害并相应对子系统造成伤害，在主线程中统一处理，参见 {@link DestroyableObject#preTick()}</p>
     * <p>Handles the damage caused by each thread and applies it to the subsystem, which will be handled in the main thread, see {@link DestroyableObject#preTick()}</p>
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
            ManifoldPoint point1, ManifoldPoint point2,
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
        if (!level.isClientSide()) {
            SynchedEntityData synchedentitydata = this.getSyncedData();
            List<SynchedEntityData.DataValue<?>> list = synchedentitydata.packDirty();
            if (list != null) {
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new SubPartSyncPayload(getId(), list));
            }
        }
    }

    protected void clientSyncPose() {
        oldTransform = transform.clone();
        transform = new Transform(getPosition(), getRotation());
        lastSync = System.nanoTime();
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> dataValues) {
    }

    @Override
    public void onSyncedDataUpdated(@NotNull EntityDataAccessor<?> key) {
        if (!level.isClientSide()) return;//服务器在需同步数据变化时不做特殊处理
        if (key.equals(DATA_DURABILITY_ID)) {
            hurtTime = hurtDuration;
        } else if (key.equals(DATA_VEL_ID)) {
            Vector3f linearVelocity = PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_VEL_ID));
            this.setLinearVelocity(linearVelocity);//应用到刚体(若有)
        } else if (key.equals(DATA_ANG_VEL_ID)) {
            Vector3f angularVelocity = PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_ANG_VEL_ID));
            this.setAngularVelocity(angularVelocity);//应用到刚体(若有)
        } else if (key.equals(DATA_DESTROYED_ID)) {
            this.setDestroyed();
        }
    }

    protected abstract void defineSyncedData(SynchedEntityData.Builder builder);

    public Vector3f getPosition() {
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_POS_ID));
    }

    public Quaternion getRotation() {
        return SparkMathKt.toBQuaternion(getSyncedData().get(DATA_ROT_ID));
    }

    public Quaternionf getQuaternionf() {
        return getSyncedData().get(DATA_ROT_ID);
    }

    public Vector3f getLinearVelocity() {
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_VEL_ID));
    }

    public Vector3f getAngularVelocity() {
        return PhysicsHelperKt.toBVector3f(getSyncedData().get(DATA_ANG_VEL_ID));
    }

    public void setPosition(Vector3f position) {
        getSyncedData().set(DATA_POS_ID, SparkMathKt.toVector3f(position));
    }

    public void setRotation(Quaternion rotation) {
        getSyncedData().set(DATA_ROT_ID, SparkMathKt.toQuaternionf(rotation));
    }

    public void setLinearVelocity(Vector3f linearVelocity) {
        getSyncedData().set(DATA_VEL_ID, SparkMathKt.toVector3f(linearVelocity));
    }

    public void setAngularVelocity(Vector3f angularVelocity) {
        getSyncedData().set(DATA_ANG_VEL_ID, SparkMathKt.toVector3f(angularVelocity));
    }

    public Vector3f getLinearVelocityLocal() {
        Vector3f result = getLinearVelocity();//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public Vector3f getAngularVelocityLocal() {
        Vector3f result = getAngularVelocity();//获取物体在世界坐标系下的角速度
        Quaternion worldToLocal = getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public Vector3f getFrontVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(0, 0, -1)));
    }

    public Vector3f getUpVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(0, 1, 0)));
    }

    public Vector3f getRightVector() {
        return PhysicsHelperKt.toBVector3f(getQuaternionf().transform(new org.joml.Vector3f(1, 0, 0)));
    }

    /**
     * <p>基于前向量计算pitch角（俯仰角）</p>
     * <p>pitch角表示物体前后倾斜的角度，范围[-90°, 90°]</p>
     * <p>基于前向量在y轴上的投影计算，避免旋转顺序问题</p>
     *
     * @return pitch角（弧度）
     */
    public float getPitch() {
        Vector3f frontVector = getFrontVector();
        // pitch = arcsin(前向量的y分量)
        return (float) Math.asin(frontVector.y);
    }

    /**
     * <p>基于前向量计算yaw角（偏航角）</p>
     * <p>yaw角表示物体左右旋转的角度，范围[-180°, 180°]</p>
     * <p>基于前向量在xz平面上的投影计算，避免旋转顺序问题</p>
     *
     * @return yaw角（弧度）
     */
    public float getYaw() {
        Vector3f frontVector = getFrontVector();
        // yaw = atan2(前向量的x分量, 前向量的z分量)
        return (float) Math.atan2(frontVector.x, -frontVector.z);
    }

    /**
     * <p>基于右向量和上向量计算roll角（滚转角）</p>
     * <p>roll角表示物体绕前向轴旋转的角度，范围[-180°, 180°]</p>
     * <p>基于右向量在世界坐标系上向量上的投影计算，避免旋转顺序问题</p>
     *
     * @return roll角（弧度）
     */
    public float getRoll() {
        Vector3f rightVector = getRightVector();
        // 计算右向量在世界坐标系上向量(0,1,0)上的投影
        // roll = atan2(右向量的y分量, 右向量在xz平面上的长度)
        float rightVectorLengthXZ = (float) Math.sqrt(rightVector.x * rightVector.x + rightVector.z * rightVector.z);
        return (float) Math.atan2(rightVector.y, rightVectorLengthXZ);
    }


    @NotNull
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
//        long time = System.nanoTime();
//        float deltaTime = (time - lastSync) / 1000000000.0F;//距离上次同步经过的时间(秒)
//        if (deltaTime > 0.05f) {//大于1Tick，根据速度外推新的位置
//            float partialTime = number.floatValue() * 0.05f;
//            Vector3f translation = transform.getTranslation().add(getLinearVelocity().mult(deltaTime + partialTime));
//            Transform result = SparkMathKt.lerp(oldTransform, transform, number.floatValue()).setTranslation(translation);
//            return SparkMathKt.toMatrix4f(result.toTransformMatrix());
//        } else {//否则插值计算位姿
//            return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
//        }
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
    }

    public float getDurability() {
        return this.syncedData.get(DATA_DURABILITY_ID);
    }

    public void setDurability(float durability) {
        this.syncedData.set(DATA_DURABILITY_ID, Math.clamp(durability, 0.0f, this.getMaxDurability()));
    }

    public int getDestroyTime() {
        return this.syncedData.get(DESTROY_TIME_ID);
    }

    public void setDestroyTime(int time) {
        this.syncedData.set(DESTROY_TIME_ID, time);
    }

    abstract public float getMaxDurability();

    abstract public PhysicsLevel getPhysicsLevel();
}
