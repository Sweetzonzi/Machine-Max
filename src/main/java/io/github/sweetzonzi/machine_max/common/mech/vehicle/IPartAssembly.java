package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPart;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 载具与外部身体装配体共用的部件宿主契约。
 *
 * <p>宿主负责部件生命周期、拓扑、子系统和动画调度。部件种类的接纳规则与是否创建 MM 代理实体
 * 是两个独立选择：身体宿主可以接纳普通装备，同时让所有子零件由自己的实体体系承载。</p>
 * <p>使用前先检查接纳规则，再分配实例或改变拓扑。装配体 UUID 与部件 UUID 用于成员管理和持久身份；
 * 子零件运行时状态可通过其对象 ID 独立寻址，具体载荷选择相应入口。</p>
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

    /**
     * 判断宿主是否允许接纳该静态部件种类，不修改任何运行状态。
     *
     * <p>创建前用于避免分配无效资源；添加部件、连接拓扑等变更入口也应重复执行必要的类型检查。
     * 载具只接受普通部件，外部宿主可定义自己的允许范围。</p>
     */
    boolean acceptsPartType(AbstractPartType type);

    /**
     * 声明宿主是否使用 MM 子零件代理实体，默认关闭。
     *
     * <p>这是整个装配体的实体所有权策略，普通装备装到外部宿主上时也服从该策略；
     * 不能仅按部件是否为普通种类推断。关闭后子零件入世流程不创建 MM 代理。</p>
     */
    default boolean usesMMPartEntities() { return false; }

    /**
     * 按运行时 UUID 查找本装配体已持有的部件。
     *
     * <p>供装配体自身的成员管理、保存与身份核对使用；不能在查询中创建部件。
     * 默认返回空，持有可寻址成员的宿主应覆盖。</p>
     *
     * @param id 部件实例 UUID，不是型号注册名
     * @return 已持有部件；未命中时返回 {@code null}
     */
    default AbstractPart<?> findPart(UUID id) { return null; }

    // ========================================
    // 基本属性
    // ========================================

    /** 装配体名称 */
    String getAssemblyName();

    /**
     * 修改装配体显示名称；具体实现负责需要的同步和持久化。
     */
    void setAssemblyName(String name);

    /** 返回所有部件的总质量，供控制与物理规则查询。 */
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

    /** 接纳部件并建立宿主归属、信号路由与子系统索引；实现应拒绝不接受的部件种类。 */
    void addPart(AbstractPart<?> part);

    /** 从装配体解除部件归属并更新相关索引；具体销毁或转移规则由宿主实现决定。 */
    void removePart(AbstractPart<?> part);

    // ========================================
    // 拓扑操作 —— 替代 VehicleCore 的 attachConnector / detachConnector
    // ========================================

    /**
     * 连接两个 Connector 并更新拓扑图
     *
     * @param connector1 连接点1
     * @param connector2 连接点2（其一必须是 SimpleConnector）
     * @param newPart    本次导入的部件实例；仅连接已有部件时可为 null
     */
    void connect(AbstractConnector connector1, AbstractConnector connector2, @Nullable AbstractPart<?> newPart);

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

    /** 接收种类策略折算后的部件伤害，由宿主决定是否以及如何扣除装配体 HP。 */
    void onPartDamage(AbstractPart<?> part, float damage);

    /**
     * 一次结算的通告：携带本零件本次结算产生的全部命中记录。
     * <p>
     * 与 {@link #onPartDamage(AbstractPart, float)} 的差别只在粒度——后者只有一个折算后的总量，
     * 前者还带有逐次命中的金额与协议上下文（伤害来源、命中几何），
     * 供需要把伤害继续投递给承载实体的装配体使用。
     * 记录里的零件字段让"把多个零件的记录汇总成一个扁平列表"的场景自带来源信息。
     * 通告发生在结算过程中（{@code LevelTickEvent.Post} 内），若实现要把伤害投递给承载实体，
     * 必须自行推迟到全部零件结算之后。
     * </p>
     * <p>默认实现把记录折算为总量后调用 {@link #onPartDamage(AbstractPart, float)}，因此只实现该重载的装配体照常工作。</p>
     */
    default void onPartDamage(AbstractPart<?> part, List<SubPartHitDamage> hits) {
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
