package io.github.sweetzonzi.machine_max.common.mech.projectile;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.physics.PhysicsHost;
import cn.solarmoon.spark_core.physics.body.CollisionGroups;
import cn.solarmoon.spark_core.physics.body.PhysicsBodyExtensionKt;
import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import com.jme3.bullet.collision.PhysicsCollisionObject;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolveResult;
import io.github.sweetzonzi.ballistics_framework.api.BFHitResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * 投射物的运动学触发体宿主。
 * <p>
 * 弹道投射物没有 Bullet 刚体，其他投射物的射线检测无法发现它。本类把投射物
 * 暴露为物理世界中的一个体积，作为可被拦截的命中目标，形态与
 * {@code InteractBoxes} 同构：
 * <ul>
 *   <li>零质量运动学刚体，{@code setContactResponse(false)}——与任何组都不产生接触响应；</li>
 *   <li>碰撞组 {@link CollisionGroups#PROJECTILE}、{@code collideWithGroups} 为
 *       {@link CollisionGroups#NONE}——不与任何组产生接触对；</li>
 *   <li>属主是本类自身，因此 {@code PenetrationKey.fromCollision} 能取到密钥，
 *       {@code resolveHitTarget} 能取到投射物。</li>
 * </ul>
 * <p>
 * 射线查询不检查碰撞掩码，可见性由每个调用方的组白名单决定：攻击方
 * （{@link ProjectileManager#updateProjectiles}）显式放行本组，
 * 生物视野射线显式排除本组。
 * <p>
 * <b>调用线程：</b>构造与销毁可能来自物理线程或主线程（内部经
 * {@code addPhysicsBody} / {@code removePhysicsBody} 提交物理任务，两者都安全）；
 * {@link #syncPosition} 只在物理线程调用。
 */
public class ProjectileHitBox implements PhysicsHost, BFHitResolver {

    private final Level level;
    private final BallisticProjectile projectile;
    private final PhysicsRigidBody body;
    private final Map<String, PhysicsCollisionObject> allPhysicsBodies = new HashMap<>();

    /**
     * 创建触发体并加入物理世界。
     * <p>
     * 球半径取投射物口径换算出的半径——口径是投射物唯一的几何输入，
     * 同一个值已同时喂给风阻截面积与命中判定。
     *
     * @param projectile 触发体所代表的投射物
     */
    public ProjectileHitBox(BallisticProjectile projectile) {
        this.projectile = projectile;
        this.level = projectile.getLevel();
        this.body = new PhysicsRigidBody(new SphereCollisionShape(projectile.getRadius()), 0f);
        // 顺序与 InteractBoxes 一致：先写属主，再开关，最后入世。
        // 属主必须显式写入——addPhysicsBody 只把碰撞体加进物理世界，
        // 没有属主则 PenetrationKey.fromCollision 返回 null，该命中不参与去重，解析也取不到目标
        PhysicsBodyExtensionKt.setOwner(this.body, this);
        this.body.setKinematic(true);
        this.body.setContactResponse(false);
        this.body.setCollisionGroup(CollisionGroups.PROJECTILE);
        this.body.setCollideWithGroups(CollisionGroups.NONE);
        PhysicsBodyExtensionKt.addPhysicsBody(this.level, this.body);
    }

    /**
     * 从 SoA 刷新触发体位姿。
     * <p>
     * 只写位置：触发体是凸球，朝向对球体求交没有影响，因此不构造旋转。
     * <p>
     * <b>调用线程：</b>物理线程。
     *
     * @param position 投射物当前世界坐标（JME）
     */
    public void syncPosition(Vector3f position) {
        body.setPhysicsLocation(position);
    }

    /**
     * 从物理世界摘除触发体，并清空其属主（避免物理世界残留可被射线命中的幽灵目标）。
     * <p>
     * 幂等：对已摘除的刚体重复调用不产生副作用。
     */
    public void remove() {
        PhysicsBodyExtensionKt.removePhysicsBody(level, body);
    }

    /** @return 触发体所代表的投射物 */
    public BallisticProjectile getProjectile() {
        return projectile;
    }

    /** @return 触发体的物理刚体 */
    public PhysicsRigidBody getBody() {
        return body;
    }

    @Override
    public PhysicsLevel getPhysicsLevel() {
        return SparkLevel.getPhysicsLevel(level);
    }

    @Override
    public Map<String, PhysicsCollisionObject> getAllPhysicsBodies() {
        return allPhysicsBodies;
    }

    /**
     * 解析到所属投射物：命中判定与伤害都落在投射物本体上。
     * <p>
     * 不覆写 {@code getPenetrationZoneId}，整个触发体视为单一穿透区域
     * （凸球的三角形索引未定义，约定为 -1），因此密钥恒为
     * {@code PenetrationKey(this, null)}。
     */
    @Override
    public BFHitResolveResult resolveHit(Vec3 hitPoint, Vec3 delta) {
        return new BFHitResolveResult(projectile, hitPoint, Vec3.ZERO);
    }
}
