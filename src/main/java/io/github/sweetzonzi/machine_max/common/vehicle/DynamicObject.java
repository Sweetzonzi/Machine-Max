package io.github.sweetzonzi.machine_max.common.vehicle;

import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Quaternion;
import com.jme3.math.Transform;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import jme3utilities.math.MyQuaternion;
import lombok.Getter;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

@Getter
public abstract class DynamicObject {
    public final Level level;
    public Transform transform = new Transform();
    public Transform oldTransform = new Transform();//用于渲染插值
    public Transform syncTransformBuffer = new Transform();//用于缓存同步数据
    public int sinceLastSync;//记录距离上次同步经过的tick，用于外推
    protected boolean updateLock = true;//是否允许更新刚体数据
    public Vector3f linearVelocity = new Vector3f();
    public Vector3f angularVelocity = new Vector3f();
    //运行中
    public int tickCount = 0;
    public int physicsTickCount = 0;
    public volatile boolean isRemoved = false;

    protected DynamicObject(Level level) {
        this.level = level;
        if (!level.isClientSide()) updateLock = false;
    }

    public void tick(){
        if (isRemoved) return;
        tickCount++;
        sync();
    }

    public void prePhysicsTick(){
        if (isRemoved) return;
        physicsTickCount++;
    }

    public void postPhysicsTick(){
        if (isRemoved) return;
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

    protected void sync(){
        oldTransform = transform;
        transform = syncTransformBuffer;
        sinceLastSync++;
    }

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

    public PhysicsLevel getPhysicsLevel() {
        return getLevel().getPhysicsLevel();
    }
}
