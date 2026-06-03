package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

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

    // ========================================
    // 物理
    // ========================================

    /** 激活装配体中所有子零件的物理刚体 */
    void activatePhysics();
}
