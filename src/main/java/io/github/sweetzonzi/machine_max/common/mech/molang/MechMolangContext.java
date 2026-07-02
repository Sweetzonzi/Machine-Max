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
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Machine-Max 的 MoLang 求值上下文，继承 {@link SparkMolangContext}。
 * <p>
 * 通过 {@code @QueryBinding} 和 {@code @StringQueryBinding} 注解
 * 将 {@code subpart.* / spt.* / vehicle.* / veh.*} 命名空间映射为 Context 实例方法。
 * 编译器生成直接 {@code INVOKEVIRTUAL} 字节码，零反射开销。
 * <p>
 * 泛型约束为 {@code IAnimatable<SubPart>}，因此 {@code getEntity().getAnimatable()}
 * 直接返回 {@link SubPart}，无需 instanceof 检查。
 */
public class MechMolangContext extends SparkMolangContext<IAnimatable<SubPart>> {

    /** 无参构造（供编译器创建原型实例用） */
    public MechMolangContext() {
        super();
    }

    /** 绑定 SubPart 的构造 */
    public MechMolangContext(@Nullable IAnimatable<SubPart> animatable) {
        super(animatable);
    }

    // ======================== 辅助方法 ========================

    /** 获取当前零件，null 安全 */
    @Nullable
    private SubPart sp() {
        IAnimatable<?> e = getEntity();
        if (e == null) return null;
        Object animatable = e.getAnimatable();
        // entity 的持有者直接是 SubPart（正常物理线程/直接 reset 的路径）
        if (animatable instanceof SubPart sp) return sp;
        // entity 的持有者不是 SubPart（如 ModelAnimatable 持有 Player），
        // 尝试从玩家乘坐的座椅找到所属零件
        if (animatable instanceof Player player
                && player instanceof IEntityMixin mixin
                && mixin.machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat
                && seat.getOwner() instanceof ISubsystemHost host) {
            return host.getSubPart();
        }
        return null;
    }

    /** 通过 SubPart → Part → Assembly 链获取 VehicleCore */
    @Nullable
    private VehicleCore vehicle() {
        SubPart sp = sp();
        if (sp != null && sp.part.getAssembly() instanceof VehicleCore vc) return vc;
        return null;
    }

    /** 获取 SubsystemController */
    @Nullable
    private SubsystemController subsystems() {
        SubPart sp = sp();
        if (sp == null) return null;
        IPartAssembly assembly = sp.part.getAssembly();
        return assembly != null ? assembly.getSubsystemController() : null;
    }

    // ======================== subpart.* 命名空间 ========================

    @QueryBinding(value = "durability", namespace = "subpart", aliases = {"spt"})
    public double subpartDurability() { SubPart s = sp(); return s != null ? s.getDurability() : 0.0; }

    @QueryBinding(value = "max_durability", namespace = "subpart", aliases = {"spt"})
    public double subpartMaxDurability() { SubPart s = sp(); return s != null ? s.getMaxDurability() : 0.0; }

    @QueryBinding(value = "is_destroyed", namespace = "subpart", aliases = {"spt"})
    public double subpartIsDestroyed() { SubPart s = sp(); return s != null && s.isDestroyed() ? 1.0 : 0.0; }

    @QueryBinding(value = "has_connector", namespace = "subpart", aliases = {"spt"})
    public double subpartHasConnector(String name) {
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractConnector c = s.getConnectors().get(name);
        return c != null && c.hasPart() ? 1.0 : 0.0;
    }

    @QueryBinding(value = "connector_offset", namespace = "subpart", aliases = {"spt"})
    public double subpartConnectorOffset(String name, int axis) {
        if (axis < 0 || axis > 2) return 0.0;
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractConnector c = s.getConnectors().get(name);
        return c != null ? c.getPivotOffset().get(axis) : 0.0;
    }

    @QueryBinding(value = "connector_rotation", namespace = "subpart", aliases = {"spt"})
    public double subpartConnectorRotation(String name, int axis) {
        if (axis < 0 || axis > 2) return 0.0;
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractConnector c = s.getConnectors().get(name);
        return c != null ? c.getPivotRotation().get(axis) : 0.0;
    }

    @QueryBinding(value = "has_subsystem", namespace = "subpart", aliases = {"spt"})
    public double subpartHasSubsystem(String name) {
        SubPart s = sp(); return s != null && s.getSubsystems().containsKey(name) ? 1.0 : 0.0;
    }

    @QueryBinding(value = "subsystem_durability", namespace = "subpart", aliases = {"spt"})
    public double subpartSubsystemDurability(String name) {
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractSubsystem sub = s.getSubsystems().get(name);
        return sub != null ? sub.getDurability() : 0.0;
    }

    @QueryBinding(value = "subsystem_max_durability", namespace = "subpart", aliases = {"spt"})
    public double subpartSubsystemMaxDurability(String name) {
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractSubsystem sub = s.getSubsystems().get(name);
        return sub != null ? sub.getMaxDurability() : 0.0;
    }

    @QueryBinding(value = "subsystem_active", namespace = "subpart", aliases = {"spt"})
    public double subpartSubsystemActive(String name) {
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractSubsystem sub = s.getSubsystems().get(name);
        return sub != null && sub.isActive() ? 1.0 : 0.0;
    }

    @QueryBinding(value = "subsystem_destroyed", namespace = "subpart", aliases = {"spt"})
    public double subpartSubsystemDestroyed(String name) {
        SubPart s = sp(); if (s == null) return 0.0;
        AbstractSubsystem sub = s.getSubsystems().get(name);
        return sub != null && sub.isDestroyed() ? 1.0 : 0.0;
    }

    /** subpart.get(channel) — 读取 SubPart 信号值 */
    @QueryBinding(value = "get", namespace = "subpart", aliases = {"spt"})
    public double subpartGet(String channel) {
        SubPart s = sp(); if (s == null) return 0.0;
        Object val = s.signalStorage.get(channel);
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** subpart.get_str(channel) — 字符串版本，null → ?? 兜底 */
    @StringQueryBinding(value = "get_str", namespace = "subpart", aliases = {"spt"})
    public String subpartGetStr(String channel) {
        SubPart s = sp(); if (s == null) return null;
        Object val = s.signalStorage.get(channel);
        return val != null ? val.toString() : null;
    }

    // ======================== vehicle.* 命名空间 ========================

    @QueryBinding(value = "durability", namespace = "vehicle", aliases = {"veh"})
    public double vehicleDurability() { VehicleCore v = vehicle(); return v != null ? v.getHp() : 0.0; }

    @QueryBinding(value = "max_durability", namespace = "vehicle", aliases = {"veh"})
    public double vehicleMaxDurability() { VehicleCore v = vehicle(); return v != null ? v.getMaxHp() : 0.0; }

    @QueryBinding(value = "energy", namespace = "vehicle", aliases = {"veh"})
    public double vehicleEnergy() {
        SubsystemController c = subsystems(); if (c == null) return 0.0;
        EnergyGrid g = c.getEnergyGrid();
        return g != null ? g.getTotalStoredEnergy() : 0.0;
    }

    @QueryBinding(value = "max_energy", namespace = "vehicle", aliases = {"veh"})
    public double vehicleMaxEnergy() {
        SubsystemController c = subsystems(); if (c == null) return 0.0;
        EnergyGrid g = c.getEnergyGrid();
        return g != null ? g.getMaxStoredEnergy() : 0.0;
    }

    /** vehicle.get(key) — 读取装配体信号值 */
    @QueryBinding(value = "get", namespace = "vehicle", aliases = {"veh"})
    public double vehicleGet(String key) {
        SubsystemController c = subsystems(); if (c == null) return 0.0;
        Object val = c.signalStorage.get(key);
        return val instanceof Number n ? n.doubleValue() : 0.0;
    }

    /** vehicle.get_str(key) — 字符串版本，null → ?? 兜底 */
    @StringQueryBinding(value = "get_str", namespace = "vehicle", aliases = {"veh"})
    public String vehicleGetStr(String key) {
        SubsystemController c = subsystems(); if (c == null) return null;
        Object val = c.signalStorage.get(key);
        return val != null ? val.toString() : null;
    }
}
