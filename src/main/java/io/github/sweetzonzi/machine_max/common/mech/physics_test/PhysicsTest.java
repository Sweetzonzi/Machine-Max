package io.github.sweetzonzi.machine_max.common.mech.physics_test;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;

import java.util.concurrent.ConcurrentHashMap;

/**物理测试接口*/
public interface PhysicsTest {
    ConcurrentHashMap<PhysicsRigidBody, PhysicsRigidBodyDataHolder> BODY_DATA = new ConcurrentHashMap<>();

    /**用于暂存物理刚体的物理量 todo 也许后续需要退出游戏保存暂存状态？*/
    default void storeBody(PhysicsRigidBody body) {
        if (body == null) return;
        BODY_DATA.put(body, new PhysicsRigidBodyDataHolder(
                body.getLinearVelocity(new Vector3f()),
                body.getAngularVelocity(new Vector3f()),
                body.getLinearFactor(new Vector3f()),
                body.getAngularFactor(new Vector3f())
        ));
        body.clearForces();
        body.setLinearVelocity(new Vector3f(0, 0, 0));
        body.setAngularVelocity(new Vector3f(0, 0, 0));
        body.setLinearFactor(new Vector3f(0, 0, 0));
        body.setAngularFactor(new Vector3f(0, 0, 0));
    }

    /**用于恢复物理刚体暂存的物理量 todo 也许后续需要退出游戏保存暂存状态？*/
    default void resetBody(PhysicsRigidBody body) {
        PhysicsRigidBodyDataHolder dataHolder = BODY_DATA.get(body);
        if (dataHolder == null || body == null) return;
        body.setLinearFactor(dataHolder.getLinearFactor());
        body.setAngularFactor(dataHolder.getAngularFactor());
        body.setLinearVelocity(dataHolder.getLinearVelocity());
        body.setAngularVelocity(dataHolder.getAngularVelocity());
        BODY_DATA.remove(body);
        body.activate();

    }
    /**触发测试用例运行*/
    void run();
    /**可暂停、恢复该测试用例*/
    void onResume();
    void vehicle_core$prePhysicsTick(VehicleCore core);

    /**
     * SubPart 撞击地形时回调。
     * <p>由 {@link io.github.sweetzonzi.machine_max.mixin_native.CollisionHandlerMixin} 在物理线程
     * 记录事件（对应原版区块碰撞处理），随后由主线程在 LevelTickEvent 中经
     * {@link PhysicsTestBus#dispatchPending()} 回调本方法。可安全执行增删载具等主线程级操作。</p>
     */
    void subpart$onTerrainCollision(SubPart subPart);

    /**
     * SubPart 破坏方块前回调。
     * <p>由 {@link io.github.sweetzonzi.machine_max.mixin_native.CollisionHandlerMixin} 在物理线程
     * 记录事件（对应 applyBlockDamage 调用前），随后由主线程在 LevelTickEvent 中经
     * {@link PhysicsTestBus#dispatchPending()} 回调本方法。可安全执行增删载具等主线程级操作。</p>
     */
    void subpart$onDestroyBlock(SubPart subPart);

    /**返回该测试用例是否已经失效*/
    boolean isDisabled();

}
