package io.github.sweetzonzi.machinemax.util;

import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import jme3utilities.math.MyQuaternion;

import static java.lang.Math.exp;

public class MMMath {
    /**
     * 取符号，但与signum不同，这个方法的输出值与输入值的关系是连续的
     * 即，±0左右不会发生跳变，而是得到一个0附近的数字
     * @param a 要取符号的值
     * @return 取得的值，介于[-1,1]之间
     */
    public static double sigmoidSignum(double a){
        return (2/(1+exp(-a))-1);
    }

    /**
     * 将相对物体质心一点的位置坐标转为相对世界原点的位置坐标
     * @param relPointPos 相对物体质心的位置坐标
     * @param obj 物体
     * @return 相对世界原点的位置坐标
     */
    public static Vector3f relPointWorldPos(Vector3f relPointPos, PhysicsCollisionObject obj){
        Vector3f absPos = obj.getPhysicsLocation(null);//获取物体质心世界坐标
        Quaternion localToWorld = obj.getPhysicsRotation(null).normalizeLocal(); //获取物体相对世界坐标的四元数
        Vector3f result =MyQuaternion.rotate(localToWorld, relPointPos, null);//旋转相对位置向量到世界坐标系
        result.addLocal(absPos);//相对位置加上物体质心坐标
        return result;
    }

    public static Vector3f localVectorToWorldVector(Vector3f localVec, PhysicsRigidBody obj){
        Quaternion localToWorld = obj.getPhysicsRotation(null); //获取物体相对世界坐标的四元数
        return MyQuaternion.rotate(localToWorld, localVec, null);
    }

    public static Vector3f getLinearVelocityLocal(PhysicsRigidBody obj){
        //TODO:检查逻辑
        Vector3f result = obj.getLinearVelocity(null);//获取物体质心在世界坐标系下的线速度
        Quaternion worldToLocal = obj.getPhysicsRotation(null); //获取物体相对世界坐标的四元数
        MyQuaternion.rotateInverse(worldToLocal, result, result);//旋转世界坐标系向量到刚体自身坐标系
        return result;
    }

    public static Vector3f relPointLocalVel(Vector3f relPointPos, PhysicsRigidBody obj){
        Vector3f result = getLinearVelocityLocal(obj);//获取物体质心在刚体坐标系下的线速度
        Vector3f relAngularVel = obj.getAngularVelocityLocal(null);//获取物体相对自身坐标系的三轴角速度
        // 计算旋转带来的额外速度
        Vector3f extraVelocity = new Vector3f();
        relAngularVel.cross(relPointPos, extraVelocity);
        return result.addLocal(extraVelocity);
    }

    public static Vector3f relPointWorldVel(Vector3f relPointPos, PhysicsRigidBody obj){
        Vector3f result = obj.getLinearVelocity(null);//获取物体质心在世界坐标系下的线速度
        Vector3f relAngularVel = obj.getAngularVelocity(null);//获取物体在世界坐标系的三轴角速度
        // 计算旋转带来的额外速度
        Vector3f extraVelocity = new Vector3f();
        relAngularVel.cross(localVectorToWorldVector(relPointPos, obj), extraVelocity);
        return result.addLocal(extraVelocity);
    }
}
