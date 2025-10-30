package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import com.mojang.datafixers.util.Pair;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import io.github.sweetzonzi.machine_max.network.payload.SubPartDataSyncPayload;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SyncedDataHolder;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Getter
public abstract class DestroyableObject implements SyncedDataHolder {
    //渲染属性 Renderer attributes
    public volatile int hurtTime = 0;
    public volatile int hurtDuration = 3;
    //物理属性 Physical attributes
    public Transform transform = new Transform();
    public Transform oldTransform = new Transform();//用于渲染插值
    public Transform syncTransformBuffer = new Transform();//用于缓存同步数据
    public int sinceLastSync;//记录距离上次同步经过的tick，用于外推
    protected boolean updateLock = true;//是否允许更新刚体数据
    public Vector3f linearVelocity = new Vector3f();
    public Vector3f angularVelocity = new Vector3f();
    //常规属性 General attributes
    public final Level level;
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
        if (!level.isClientSide()) updateLock = false;
        SynchedEntityData.Builder syncheddata$builder = new SynchedEntityData.Builder(this);
        syncheddata$builder.define(DATA_DURABILITY_ID, 20.0F);
        this.defineSynchedData(syncheddata$builder);
        this.synchedData = syncheddata$builder.build();
    }

    public void tick(){
        if (isRemoved) return;
        tickCount++;
        if (hurtTime > 0) hurtTime--;
        //处理各线程造成的伤害
        if (!level.isClientSide()) handleAccumulatedDamage();
        //判定摧毁
        if (!destroyed && getDurability() <= 0) onDestroyed();
        sync();
    }

    public void prePhysicsTick(){
        if (isRemoved) return;
        physicsTickCount++;
    }

    public void postPhysicsTick(){
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

    protected void onDestroyed(){
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
    ){
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
    ){}

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
    ){
    }

    public void destroy(){
        isRemoved = true;
    }

    /**
     * 同步位置姿态速度，被网络包处理所调用
     * @param data 同步数据
     */
    public void handleSyncData(PosRotVelVel data){
        sinceLastSync = 0;
        syncTransformBuffer = new Transform(data.position(), SparkMathKt.toBQuaternion(data.rotation()));
        updateLock = false;
        setPosition(data.position());
        setRotation(SparkMathKt.toBQuaternion(data.rotation()));
        setLinearVelocity(data.linearVel());
        setAngularVelocity(data.angularVel());
        updateLock = true;
    }

    /**
     * <p>立即同步发生变化的数据至客户端</p>
     */
    public void sync(){
        oldTransform = transform.clone();
        transform = syncTransformBuffer.clone();
        sinceLastSync = 0;
    }

    @Override
    public void onSyncedDataUpdated(@NotNull List<SynchedEntityData.DataValue<?>> dataValues) {
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (key.equals(DATA_DURABILITY_ID)){
            hurtTime = hurtDuration;
        }
    }

    protected abstract void defineSynchedData(SynchedEntityData.Builder builder);

    abstract void setPosition(Vector3f position);

    abstract void setRotation(Quaternion rotation);

    abstract void setLinearVelocity(Vector3f linearVelocity);

    abstract void setAngularVelocity(Vector3f angularVelocity);

    public Vector3f getLinearVelocityLocal() {
        Vector3f result = getLinearVelocity();//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = transform.getRotation(); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    @NotNull
    public Matrix4f getWorldPositionMatrix(@NotNull Number number) {
        //TODO:外推
        return SparkMathKt.toMatrix4f(SparkMathKt.lerp(oldTransform, transform, number.floatValue()).toTransformMatrix());
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
