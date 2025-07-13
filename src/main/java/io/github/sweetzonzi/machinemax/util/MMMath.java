package io.github.sweetzonzi.machinemax.util;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import jme3utilities.math.MyQuaternion;
import net.minecraft.core.Vec3i;
import net.minecraft.world.phys.Vec3;
import org.joml.Math;

import static java.lang.Math.exp;

public class MMMath {
    /**
     * 取符号，但与signum不同，这个方法的输出值与输入值的关系是连续的
     * 即，±0左右不会发生跳变，而是得到一个0附近的数字
     *
     * @param a 要取符号的值
     * @return 取得的值，介于[-1,1]之间
     */
    public static double sigmoidSignum(double a) {
        return (2 / (1 + exp(-a)) - 1);
    }

    /**
     * 计算sigmoid函数的值
     *
     * @param a 输入值
     * @return sigmoid函数的结果，介于[0,1]之间
     */
    public static double sigmoid(double a) {
        return 1 / (1 + Math.exp(-a));
    }

    /**
     * 获取距离原始角度最近的等效目标角度，如目标角度为180，原始角度为539，则返回540
     *
     * @param origin 原始角度(角度值)
     * @param target 目标角度(角度值)
     * @return 等效目标角度(角度值)
     */
    public static float getNearestEqualAngle(float origin, float target) {
        int loop = (int) Math.floor((origin - target) / 360f);
        return target % 360 + loop * 360;
    }

    /**
     * 将相对物体质心一点的位置坐标转为相对世界原点的位置坐标
     *
     * @param relPointPos 相对物体质心的位置坐标
     * @param obj         物体
     * @return 相对世界原点的位置坐标
     */
    public static Vector3f relPointWorldPos(Vector3f relPointPos, PhysicsCollisionObject obj) {
        Vector3f absPos = obj.getPhysicsLocation(null);//获取物体质心世界坐标
        Quaternion localToWorld = obj.getPhysicsRotation(null).normalizeLocal(); //获取物体相对世界坐标的四元数
        Vector3f result = MyQuaternion.rotate(localToWorld, relPointPos, null);//旋转相对位置向量到世界坐标系
        result.addLocal(absPos);//相对位置加上物体质心坐标
        return result;
    }

    public static Vector3f localVectorToWorldVector(Vector3f localVec, PhysicsRigidBody obj) {
        Quaternion localToWorld = obj.getPhysicsRotation(null); //获取物体相对世界坐标的四元数
        return MyQuaternion.rotate(localToWorld, localVec, null);
    }

    public static Vector3f getLinearVelocityLocal(PhysicsRigidBody obj) {
        //TODO:检查逻辑
        Vector3f result = obj.getLinearVelocity(null);//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = obj.getPhysicsRotation(null); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public static Vector3f relPointLocalVel(Vector3f relPointPos, PhysicsRigidBody obj) {
        Vector3f result = getLinearVelocityLocal(obj);//获取物体质心在刚体坐标系下的线速度
        Vector3f relAngularVel = obj.getAngularVelocityLocal(null);//获取物体相对自身坐标系的三轴角速度
        // 计算旋转带来的额外速度
        Vector3f extraVelocity = new Vector3f();
        relAngularVel.cross(relPointPos, extraVelocity);
        return result.addLocal(extraVelocity);
    }

    public static Vector3f relPointWorldVel(Vector3f relPointPos, PhysicsRigidBody obj) {
        Vector3f result = obj.getLinearVelocity(null);//获取物体质心在世界坐标系下的线速度
        Vector3f relAngularVel = obj.getAngularVelocity(null);//获取物体在世界坐标系的三轴角速度
        // 计算旋转带来的额外速度
        Vector3f extraVelocity = new Vector3f();
        relAngularVel.cross(localVectorToWorldVector(relPointPos, obj), extraVelocity);
        return result.addLocal(extraVelocity);
    }

    public static Vec3i getClosestAxisAlignedVector(Vec3 vec3) {
        // 比较每个分量的绝对值大小
        double absX = Math.abs(vec3.x);
        double absY = Math.abs(vec3.y);
        double absZ = Math.abs(vec3.z);

        // 找出绝对值最大的分量
        if (absX >= absY && absX >= absZ) {
            // X分量最大
            return new Vec3i(vec3.x >= 0 ? 1 : -1, 0, 0);
        } else if (absY >= absX && absY >= absZ) {
            // Y分量最大
            return new Vec3i(0, vec3.y >= 0 ? 1 : -1, 0);
        } else {
            // Z分量最大
            return new Vec3i(0, 0, vec3.z >= 0 ? 1 : -1);
        }
    }
}
