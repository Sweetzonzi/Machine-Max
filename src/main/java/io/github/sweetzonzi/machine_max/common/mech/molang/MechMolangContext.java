package io.github.sweetzonzi.machine_max.common.mech.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.SparkMolangContext;
import cn.solarmoon.spark_core.molang.runtime.binding.QueryBinding;
import cn.solarmoon.spark_core.molang.runtime.binding.StringQueryBinding;
import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.IPartAssembly;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Machine-Max 的 MoLang 求值上下文，继承 {@link SparkMolangContext}。
 * <p>
 * 动画体已上移到 {@link Part}，因此命名空间重划为：
 * <ul>
 *   <li>{@code local.*} —— 当前动画体（Part）：耐久、连接点、子系统、信号存储</li>
 *   <li>{@code global.*} —— 所属装配体（VehicleCore / MechUnit 等）：HP、能源、装配体信号存储</li>
 * </ul>
 * 通过 {@code @QueryBinding} / {@code @StringQueryBinding} 注解映射为 Context 实例方法，
 * 编译器生成直接 {@code INVOKEVIRTUAL} 字节码，零反射开销。
 * <p>
 * 泛型约束为 {@code IAnimatable<Part>}，因此 {@code getEntity().getAnimatable()}
 * 直接返回 {@link Part}，无需 instanceof 检查。
 */
public class MechMolangContext extends SparkMolangContext<IAnimatable<Part>> {

    /** 无参构造（供编译器创建原型实例用） */
    public MechMolangContext() {
        super();
    }

    /** 绑定 Part 的构造 */
    public MechMolangContext(@Nullable IAnimatable<Part> animatable) {
        super(animatable);
    }

    // ======================== 辅助方法 ========================

    /** 获取当前动画体（Part），null 安全 */
    @Nullable
    private Part part() {
        IAnimatable<?> e = getEntity();
        if (e == null) return null;
        Object animatable = e.getAnimatable();
        // 动画体直接是 Part（正常物理线程 / 直接 reset 的路径）
        if (animatable instanceof Part p) return p;
        // 动画体不是 Part（如 ModelAnimatable 持有 Player），
        // 尝试从玩家乘坐的座椅找到所属零件，再取 Part
        if (animatable instanceof Player player
                && player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat
                && seat.getOwner() instanceof ISubsystemHost host) {
            return host.getSubPart().part;
        }
        return null;
    }

    /** 获取所属装配体 */
    @Nullable
    private IPartAssembly assembly() {
        Part p = part();
        return p != null ? p.getAssembly() : null;
    }

    /** 获取装配体子系统控制器 */
    @Nullable
    private SubsystemController subsystems() {
        IPartAssembly a = assembly();
        return a != null ? a.getSubsystemController() : null;
    }

    /**
     * 获取代表 Part 耐久的子零件（rootSubPart）。
     * <p>Part 自身无单耐久，且 {@code getSharedDurability()} 在 shareDurability=false 时恒为 0，
     * 故此处以 rootSubPart 代表 Part 耐久。</p>
     */
    @Nullable
    private SubPart root() {
        Part p = part();
        return p != null ? p.getRootSubPart() : null;
    }

    // ======================== local.* 命名空间（当前动画体 Part） ========================

    @QueryBinding(value = "durability", namespace = "local")
    public double localDurability() { SubPart s = root(); return s != null ? s.getDurability() : 0.0; }

    @QueryBinding(value = "max_durability", namespace = "local")
    public double localMaxDurability() { SubPart s = root(); return s != null ? s.getMaxDurability() : 0.0; }

    @QueryBinding(value = "is_destroyed", namespace = "local")
    public double localIsDestroyed() { SubPart s = root(); return s != null && s.isDestroyed() ? 1.0 : 0.0; }

    @QueryBinding(value = "has_connector", namespace = "local")
    public double localHasConnector(String name) {
        Part p = part(); if (p == null) return 0.0;
        AbstractConnector c = p.getConnectorsByName().get(name);
        return c != null && c.hasPart() ? 1.0 : 0.0;
    }

    @QueryBinding(value = "connector_offset", namespace = "local")
    public double localConnectorOffset(String name, int axis) {
        if (axis < 0 || axis > 2) return 0.0;
        Part p = part(); if (p == null) return 0.0;
        AbstractConnector c = p.getConnectorsByName().get(name);
        return c != null ? c.getPivotOffset().get(axis) : 0.0;
    }

    @QueryBinding(value = "connector_rotation", namespace = "local")
    public double localConnectorRotation(String name, int axis) {
        if (axis < 0 || axis > 2) return 0.0;
        Part p = part(); if (p == null) return 0.0;
        AbstractConnector c = p.getConnectorsByName().get(name);
        return c != null ? c.getPivotRotation().get(axis) : 0.0;
    }

    @QueryBinding(value = "has_subsystem", namespace = "local")
    public double localHasSubsystem(String name) {
        Part p = part(); return p != null && p.getSubsystemsByName().containsKey(name) ? 1.0 : 0.0;
    }

    @QueryBinding(value = "subsystem_durability", namespace = "local")
    public double localSubsystemDurability(String name) {
        Part p = part(); if (p == null) return 0.0;
        AbstractSubsystem sub = p.getSubsystemsByName().get(name);
        return sub != null ? sub.getDurability() : 0.0;
    }

    @QueryBinding(value = "subsystem_max_durability", namespace = "local")
    public double localSubsystemMaxDurability(String name) {
        Part p = part(); if (p == null) return 0.0;
        AbstractSubsystem sub = p.getSubsystemsByName().get(name);
        return sub != null ? sub.getMaxDurability() : 0.0;
    }

    @QueryBinding(value = "subsystem_active", namespace = "local")
    public double localSubsystemActive(String name) {
        Part p = part(); if (p == null) return 0.0;
        AbstractSubsystem sub = p.getSubsystemsByName().get(name);
        return sub != null && sub.isActive() ? 1.0 : 0.0;
    }

    @QueryBinding(value = "subsystem_destroyed", namespace = "local")
    public double localSubsystemDestroyed(String name) {
        Part p = part(); if (p == null) return 0.0;
        AbstractSubsystem sub = p.getSubsystemsByName().get(name);
        return sub != null && sub.isDestroyed() ? 1.0 : 0.0;
    }

    /** local.get(channel) — 读取 Part 信号存储 */
    @QueryBinding(value = "get", namespace = "local")
    public double localGet(String channel) {
        Part p = part(); if (p == null) return 0.0;
        Object val = p.getSignalStorage().get(channel);
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** local.get_str(channel) — 字符串版本，null → ?? 兜底 */
    @StringQueryBinding(value = "get_str", namespace = "local")
    public String localGetStr(String channel) {
        Part p = part(); if (p == null) return null;
        Object val = p.getSignalStorage().get(channel);
        return val != null ? val.toString() : null;
    }

    // ======================== global.* 命名空间（所属装配体） ========================

    @QueryBinding(value = "hp", namespace = "global")
    public double globalHp() { IPartAssembly a = assembly(); return a != null ? a.getHp() : 0.0; }

    @QueryBinding(value = "max_hp", namespace = "global")
    public double globalMaxHp() { IPartAssembly a = assembly(); return a != null ? a.getMaxHp() : 0.0; }

    @QueryBinding(value = "energy", namespace = "global")
    public double globalEnergy() {
        SubsystemController c = subsystems(); if (c == null) return 0.0;
        EnergyGrid g = c.getEnergyGrid();
        return g != null ? g.getTotalStoredEnergy() : 0.0;
    }

    @QueryBinding(value = "max_energy", namespace = "global")
    public double globalMaxEnergy() {
        SubsystemController c = subsystems(); if (c == null) return 0.0;
        EnergyGrid g = c.getEnergyGrid();
        return g != null ? g.getMaxStoredEnergy() : 0.0;
    }

    /** global.get(key) — 读取装配体信号存储 */
    @QueryBinding(value = "get", namespace = "global")
    public double globalGet(String key) {
        SubsystemController c = subsystems(); if (c == null) return 0.0;
        Object val = c.signalStorage.get(key);
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** global.get_str(key) — 字符串版本，null → ?? 兜底 */
    @StringQueryBinding(value = "get_str", namespace = "global")
    public String globalGetStr(String key) {
        SubsystemController c = subsystems(); if (c == null) return null;
        Object val = c.signalStorage.get(key);
        return val != null ? val.toString() : null;
    }
}
