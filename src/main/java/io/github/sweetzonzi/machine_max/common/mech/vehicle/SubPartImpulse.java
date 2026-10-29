package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

/**
 * 一条零件物理冲量命令：目标零件、世界坐标下的冲量矢量与世界坐标作用点。
 *
 * <p>主线程在零件统一结算阶段构造并收进 Level 级主线程列表；同一 Level 的全部零件结算完成后，
 * {@code ObjectManager} 取出不可变批次、清空主线程列表，并以单个 {@code PPhase.PRE} 任务发布。
 * 物理线程只读取发布后的不可变快照，不读取会被主线程继续写入的列表。</p>
 *
 * <p>作用点按世界坐标保存，施加时才换算到目标刚体的局部坐标，因此同一命中的线冲量与角冲量
 * （偏心、反向命中）都由一次 {@code applyImpulse} 表达，不做标量平均。</p>
 *
 * @param target  目标零件；其刚体是冲量的实际受力体
 * @param impulse 世界坐标下的冲量矢量（N·s）
 * @param point   世界坐标下的作用点
 */
public record SubPartImpulse(SubPart target, Vector3f impulse, Vector3f point) {

    /** 复制传入的向量，避免命令发布后仍与调用方的可变实例共享。 */
    public SubPartImpulse {
        impulse = impulse.clone();
        point = point.clone();
    }

    /**
     * 把本命令施加到目标刚体。
     *
     * <p>结算期间目标被销毁或刚体已移出物理世界时命令失效并跳过：主线程只负责登记，
     * 有效性检查在物理线程执行，避免主线程读取正在步进的刚体状态。</p>
     *
     * <p><b>调用线程：</b>物理线程（本批 PPhase.PRE 阶段）。</p>
     */
    public void apply() {
        if (target.isRemoved()) return;
        PhysicsRigidBody body = target.body;
        if (body == null || !body.isInWorld()) return;
        body.applyImpulse(impulse, point.subtract(body.getPhysicsLocation(null)));
    }
}
