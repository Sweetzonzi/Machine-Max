package io.github.sweetzonzi.machine_max.common.mech.grab;

import cn.solarmoon.spark_core.physics.level.PhysicsLevel;
import cn.solarmoon.spark_core.util.PPhase;
import com.jme3.bullet.RotationOrder;
import com.jme3.bullet.joints.New6Dof;
import com.jme3.bullet.joints.motors.MotorParam;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Matrix3f;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.util.MMMath;
import lombok.Getter;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * 一次抓取，聚合根：包装单端 {@link New6Dof}，持有该次抓取的抓取点、参数与判据状态。
 * <p>
 * 线程约定（接口设计文档 §五）：
 * <ul>
 *   <li>对象由主线程持有；{@link #updateSnapshot} 由主线程每 tick 覆写；</li>
 *   <li>约束的建立与移除、零力点写入、冲量施加都提交为物理任务；</li>
 *   <li>{@link #physicsStep} 在物理步的步进前阶段由物理线程调用，与刚体同线程。</li>
 * </ul>
 * 判据只回答"是否应当结束"（写入 {@link #pendingRelease}），释放的执行只有一处：
 * {@link GrabManager}（接口设计文档 §1.4）。
 *
 * @see GrabPhysics
 */
@Getter
public class GrabConstraint {

    /**
     * 结束原因。判据、玩家输入、玩家状态变化、目标失效四条来源都只是"请求结束"。
     */
    public enum ReleaseReason {
        /** 手距连续超过触及距离（脱手判据）。 */
        OUT_OF_RANGE,
        /** 玩家显式放下，或开始新的抓取前先释放。 */
        INPUT,
        /** 玩家死亡、切换维度、断线，或徒手形态下主手不再为空。 */
        PLAYER_STATE,
        /** 被抓刚体被摧毁、脱落、转为运动学模式，或装配体分裂。 */
        TARGET_INVALID,
        /** 玩家丢出（释放并施加冲量）。 */
        THROWN
    }

    /** 一次抓取归属的玩家。 */
    private final ServerPlayer player;
    /** 被抓零件的零件实体，即约束的 B 端。 */
    private final SubPart target;
    /** 建立抓取时所在的装配体，用于感知装配体分裂。 */
    private final IPartAssembly assemblyAtCreation;
    /** 建立抓取时视线射线命中被抓刚体的距离 d（m）：牵引期间的稳态持握距离。 */
    private final float hitDistance;
    /** 建立抓取时的世界命中点（抓取点的世界坐标），在创建任务内局部化。 */
    private final Vector3f hitPointWorld;
    /** frame A 原点（世界坐标，即"眼位 + 视线 × 射线长度"），创建时写入后不再改变。 */
    private final Vector3f anchorOrigin;
    /** 抓取时写死的平动刚度（N/m）。 */
    private final float stiffness;
    /** 抓取时写死的平动阻尼系数（N·s/m）。 */
    private final float damping;

    /** 抓取点在刚体局部坐标系中的位置；物理线程在创建任务内写入。 */
    private volatile Vector3f gripLocal;
    /** 单端 New6Dof 约束；物理线程在创建任务内写入。 */
    private volatile New6Dof joint;
    /** 约束是否已建立并加入物理世界。 */
    private volatile boolean created;
    /** 约束是否已请求移除，避免重复提交移除任务。 */
    private volatile boolean removed;
    /** 物理线程判定出的结束原因，主线程据此执行释放。 */
    private volatile ReleaseReason pendingRelease;

    /** 玩家眼位快照（世界坐标）；主线程写入，物理线程只读。 */
    private volatile Vector3f eyeSnapshot;
    /** 玩家视线方向的单位向量；主线程写入，物理线程只读。 */
    private volatile Vector3f viewSnapshot;
    /** 脱手阈值快照，取自触及距离属性；主线程写入，物理线程只读。 */
    private volatile float breakThreshold;

    /** 脱手防抖计数，按物理步计；仅物理线程读写。 */
    private int breakCounter;

    public GrabConstraint(ServerPlayer player, SubPart target, Vector3f hitPointWorld, Vector3f anchorOrigin,
                          float hitDistance, float stiffness, float damping) {
        this.player = player;
        this.target = target;
        this.assemblyAtCreation = target.getPart().getAssembly();
        this.hitPointWorld = hitPointWorld;
        this.anchorOrigin = anchorOrigin;
        this.hitDistance = hitDistance;
        this.stiffness = stiffness;
        this.damping = damping;
    }

    /**
     * 覆写玩家侧快照（眼位、视线与脱手阈值）。
     * <p>
     * 主线程每 tick 调用一次：物理线程不读实体状态，只读这份快照（详细设计文档 §3.4）。
     *
     * @param eye            玩家眼位（世界坐标）
     * @param view           玩家视线方向的单位向量
     * @param breakThreshold 当前的脱手阈值
     */
    public void updateSnapshot(Vector3f eye, Vector3f view, float breakThreshold) {
        this.eyeSnapshot = eye;
        this.viewSnapshot = view;
        this.breakThreshold = breakThreshold;
    }

    /**
     * 提交约束建立任务（详细设计文档 §2.1、§3.2、§3.3、§3.7）。
     * <p>
     * 在物理步的步进前阶段完成：建立单端约束、配置自由度与弹簧、局部化抓取点、加入物理世界。
     * 抓取点的"世界命中点 → 刚体局部点"换算必须与约束创建处在同一个物理任务内，用同一时刻的
     * 刚体位姿完成——主线程读到的位姿可能已经过期。
     */
    public void submitCreate() {
        PhysicsLevel physicsLevel = target.getPhysicsLevel();
        physicsLevel.submitImmediateTask(PPhase.PRE, () -> {
            PhysicsRigidBody body = target.getBody();
            // 动力学断言：单端约束要求 B 端为动力学模式（详细设计文档 P3、§8.2）
            if (!body.isDynamic()) {
                pendingRelease = ReleaseReason.TARGET_INVALID;
                return null;
            }
            Vector3f grip = MMMath.worldPointLocalPos(hitPointWorld, body);
            // 唤醒刚体：休眠中的刚体不会被弹簧唤回，弹簧力将完全不生效
            IPartAssembly assembly = target.getPart().getAssembly();
            if (assembly != null) assembly.activatePhysics();
            else body.activate(true);
            // frame A 姿态取世界单位阵 => 约束轴向就是世界轴；frame B 姿态取刚体姿态的逆，
            // 使两端 frame 在创建瞬间重合（详细设计文档 §3.2）
            Matrix3f rotB = body.getPhysicsRotation(null).inverse().toRotationMatrix();
            New6Dof constraint = new New6Dof(body, new Vector3f(grip), new Vector3f(anchorOrigin),
                    rotB, new Matrix3f(), RotationOrder.XYZ);
            // 平动轴出厂即为锁定（下限 == 上限），必须显式设成自由（下限 > 上限）才能解锁；
            // 开关弹簧不会改变上下限，两件事必须分别设置（详细设计文档 §3.3、§8.3）
            for (int i = 0; i < 3; i++) {
                constraint.set(MotorParam.LowerLimit, i, 1f);
                constraint.set(MotorParam.UpperLimit, i, -1f);
                constraint.enableSpring(i, true);
                // 末位 true 打开引擎自带的限幅（按步长与有效质量裁剪）
                constraint.setStiffness(i, stiffness, true);
                constraint.setDamping(i, damping, true);
            }
            // 转动轴为构造默认的"自由"档，且不挂弹簧：物体绕抓取点自然摆动、翻滚
            // 以当前相对位姿作为平衡位置，给出抓取瞬间的零冲量
            constraint.setEquilibriumPoint();
            physicsLevel.getWorld().addJoint(constraint);
            this.gripLocal = grip;
            this.joint = constraint;
            this.created = true;
            return null;
        });
    }

    /**
     * 物理步的步进前阶段调用（物理线程，与刚体同线程）。
     * <p>
     * 先判据、后写零力点：对已经判定应当释放的抓取不再做一次写入（接口设计文档 §2.2）。
     */
    public void physicsStep() {
        if (!created || removed || pendingRelease != null) return;
        New6Dof constraint = joint;
        if (constraint == null) return;
        PhysicsRigidBody body = target.getBody();
        if (!body.isDynamic() || target.isRemoved()) {
            pendingRelease = ReleaseReason.TARGET_INVALID;
            return;
        }
        Vector3f eye = eyeSnapshot;
        Vector3f view = viewSnapshot;
        if (eye == null || view == null) return;
        // 脱手判据：手距（眼位到抓取点）超过触及距离。形变量是弹簧的输出量，不能用作判据
        // （详细设计文档 §6.1）。稳态手距恒为命中距离 d，因此余量是固定的 D − d
        Vector3f gripWorld = MMMath.relPointWorldPos(gripLocal, body);
        if (eye.distance(gripWorld) > breakThreshold) {
            if (++breakCounter >= GrabPhysics.BREAK_DEBOUNCE_STEPS) {
                pendingRelease = ReleaseReason.OUT_OF_RANGE;
                return;
            }
        } else {
            breakCounter = 0;
        }
        // 目标抓取点 = 眼位 + 视线 × 命中距离；frame A 姿态是世界单位阵，因此零力点的三个分量
        // 就是世界轴分量（详细设计文档 §3.4）
        Vector3f targetPoint = view.mult(hitDistance, null).addLocal(eye);
        constraint.setEquilibriumPoint(0, targetPoint.x - anchorOrigin.x);
        constraint.setEquilibriumPoint(1, targetPoint.y - anchorOrigin.y);
        constraint.setEquilibriumPoint(2, targetPoint.z - anchorOrigin.z);
    }

    /**
     * 释放：提交约束移除任务。释放即销毁，约束不跨抓取存活（详细设计文档 §3.6）。
     */
    public void destroy() {
        if (removed) return;
        removed = true;
        PhysicsLevel physicsLevel = target.getPhysicsLevel();
        physicsLevel.submitImmediateTask(PPhase.PRE, () -> {
            // 在任务内读字段而不是捕获：建立任务可能排在本任务之前，尚未把约束写进字段
            New6Dof constraint = this.joint;
            if (constraint != null && constraint.getPhysicsSpace() != null) {
                physicsLevel.getWorld().removeJoint(constraint);
            }
            this.joint = null;
            return null;
        });
    }

    /**
     * 抛掷：在同一个物理任务内先移除约束、再在抓取点施加冲量。
     * <p>
     * 顺序不可颠倒：约束处于启用状态时会在冲量施加的同一物理步内反向拉扯，抵消掉大部分抛出速度
     * （详细设计文档 §2.3、§7.3）。冲量施加在抓取点而不是质心上，因此偏移量取抓取点相对质心的
     * 世界系向量，抛掷时会自然产生翻滚。
     *
     * @param impulse   冲量大小（N·s）
     * @param direction 抛出方向的单位向量（世界坐标）
     */
    public void throwWith(float impulse, Vector3f direction) {
        if (removed) return;
        removed = true;
        PhysicsLevel physicsLevel = target.getPhysicsLevel();
        Vector3f grip = gripLocal;
        physicsLevel.submitImmediateTask(PPhase.PRE, () -> {
            // 在任务内读字段而不是捕获：建立任务可能排在本任务之前，尚未把约束写进字段
            New6Dof constraint = this.joint;
            if (constraint != null && constraint.getPhysicsSpace() != null) {
                physicsLevel.getWorld().removeJoint(constraint);
            }
            this.joint = null;
            PhysicsRigidBody body = target.getBody();
            if (grip == null || !body.isDynamic()) return null;
            IPartAssembly assembly = target.getPart().getAssembly();
            if (assembly != null) assembly.activatePhysics();
            Vector3f gripWorld = MMMath.relPointWorldPos(grip, body);
            Vector3f offset = gripWorld.subtract(body.getPhysicsLocation(null));
            body.applyImpulse(direction.mult(impulse, null), offset);
            return null;
        });
    }

    /**
     * 目标是否已失效（被抓刚体被摧毁、脱落、转为运动学模式）。
     * <p>
     * 每 tick 的有效性检查（接口设计文档 Q1 的取法）：主线程在驱动时读一次。
     *
     * @return 目标是否已失效
     */
    public boolean isTargetInvalid() {
        if (target.isRemoved()) return true;
        if (!target.getBody().isDynamic()) return true;
        if (!target.getBody().isInWorld()) return true;
        // 装配体分裂：被抓零件被划归到另一个装配体
        return target.getPart().getAssembly() != assemblyAtCreation;
    }

    /**
     * 取物理线程给出的结束原因，并清空它。
     *
     * @return 结束原因，没有则为 null
     */
    @Nullable
    public ReleaseReason pollPendingRelease() {
        ReleaseReason reason = pendingRelease;
        pendingRelease = null;
        return reason;
    }
}
