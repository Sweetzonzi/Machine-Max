package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 零件装配体顶层接口
 * <p>
 * 表示"由 Part 通过 Connector 组装而成的集合体"——如一辆载具（VehicleCore）或一具机甲素体（MechUnit）。
 * 所有 Part / SubPart / Connector 中对 vehicle 的直接引用均改为通过此接口访问，
 * 从而解耦 Part 与 VehicleCore 的双向绑定。
 * </p>
 *
 * @see VehicleCore
 */
public interface IPartAssembly {

    // ========================================
    // 身份标识
    // ========================================

    /** 装配体唯一标识，用于碰撞过滤中判定"同装配体"、网络同步等 */
    UUID getAssemblyId();

    /** 所在世界 */
    Level getLevel();

    /** 是否已加入物理世界 */
    boolean isInLevel();

    // ========================================
    // 基本属性
    // ========================================

    /** 装配体名称 */
    String getAssemblyName();

    void setAssemblyName(String name);

    /** 所有 Part 的总质量 */
    float getTotalMass();

    /**
     * 装配体当前 HP（用于 Molang {@code global.hp}）。
     * <p>未实现 HP 概念的装配体可保持默认返回 0。</p>
     */
    default float getHp() {
        return 0f;
    }

    /**
     * 装配体 HP 上限（用于 Molang {@code global.max_hp}）。
     * <p>未实现 HP 概念的装配体可保持默认返回 0。</p>
     */
    default float getMaxHp() {
        return 0f;
    }

    // ========================================
    // 零件管理
    // ========================================

    /** 将 Part 加入装配体，初始化信号路由、注册子系统 */
    void addPart(Part part);

    /** 从装配体中移除 Part */
    void removePart(Part part);

    // ========================================
    // 拓扑操作 —— 替代 VehicleCore 的 attachConnector / detachConnector
    // ========================================

    /**
     * 连接两个 Connector 并更新拓扑图
     *
     * @param connector1 连接点1
     * @param connector2 连接点2（其一必须是 SimpleConnector）
     * @param newPart    新导入的 Part，可为 null
     */
    void connect(AbstractConnector connector1, AbstractConnector connector2, @Nullable Part newPart);

    /** 断开指定 Connector，触发图分裂检测 */
    void disconnect(AbstractConnector connector);

    // ========================================
    // 子系统与能量
    // ========================================

    /** 获取子系统控制器（ISignalBus + 信号存储 + 子系统生命周期管理） */
    SubsystemController getSubsystemController();

    // ========================================
    // 伤害传递
    // ========================================

    /** Part 受到伤害后向装配体顶层上报，由实现者决定如何折算到装配体的 HP */
    void onPartDamage(Part part, float damage);

    /**
     * 一次结算的通告：携带本零件本次结算产生的全部命中记录。
     * <p>
     * 与 {@link #onPartDamage(Part, float)} 的差别只在粒度——后者只有一个折算后的总量，
     * 前者还带有逐次命中的金额与协议上下文（伤害来源、命中几何），
     * 供需要把伤害继续投递给承载实体的装配体使用。
     * 记录里的零件字段让"把多个零件的记录汇总成一个扁平列表"的场景自带来源信息。
     * 通告发生在结算过程中（{@code LevelTickEvent.Post} 内），若实现要把伤害投递给承载实体，
     * 必须自行推迟到全部零件结算之后。
     * </p>
     * <p>默认实现把记录折算为总量后调用 {@link #onPartDamage(Part, float)}，因此只实现该重载的装配体照常工作。</p>
     */
    default void onPartDamage(Part part, List<SubPartHitDamage> hits) {
        float total = 0f;
        for (SubPartHitDamage hit : hits) {
            total += hit.vehicleDamage();
        }
        if (total > 0f) onPartDamage(part, total);
    }

    // ========================================
    // 物理
    // ========================================

    /** 激活装配体中所有子零件的物理刚体 */
    void activatePhysics();
}
