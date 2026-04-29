package io.github.sweetzonzi.machine_max.common.vehicle.collision;

import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.ManifoldPoint;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

/**
 * 碰撞上下文
 * <p>记录一次碰撞接触的全部参数，替代原先 SubPart 碰撞方法中的超长参数列表。
 * 创建为不可变 record，可在物理线程安全地传递。</p>
 *
 * @param other               碰撞对方刚体
 * @param otherOwner          碰撞对方的所有者（PhysicsChunkSection / SubPart / LivingEntity 等）
 * @param normal              碰撞法线（由对方指向自身）
 * @param worldContactPoint   世界坐标下的接触点
 * @param localContactPoint   接触点在自身局部坐标下的位置
 * @param otherLocalContactPoint 接触点在对方局部坐标下的位置
 * @param contactVel          接触点处的相对速度
 * @param hitBoxIndex         自身命中的碰撞箱索引
 * @param otherHitBoxIndex    对方命中的碰撞箱索引
 * @param impactAngle         碰撞角（法线与速度方向的夹角，单位：度）
 * @param point1              自身碰撞点数据
 * @param point2              对方碰撞点数据
 * @param manifoldPointId     碰撞点唯一ID
 */
public record CollisionContext(
        PhysicsRigidBody other,
        PhysicsHost otherOwner,
        Vector3f normal,
        Vector3f worldContactPoint,
        Vector3f localContactPoint,
        Vector3f otherLocalContactPoint,
        Vector3f contactVel,
        int hitBoxIndex,
        int otherHitBoxIndex,
        float impactAngle,
        ManifoldPoint point1,
        ManifoldPoint point2,
        long manifoldPointId
) {
}
