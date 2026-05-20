package io.github.sweetzonzi.machine_max.common.mech.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.js.molang.IMolangContext;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import kotlin.jvm.JvmField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SubPartBinding implements IMolangContext {

    private AnimInstance anim;

    @Override
    public void update(@NotNull String molang, @NotNull AnimInstance anim, @NotNull Context context, @NotNull Value bindings) {
        this.anim = anim;
        durability = getDurability(getAnimatable());
        max_durability = getMaxDurability(getAnimatable());
        is_destroyed = isDestroyed(getAnimatable());
    }

    @HostAccess.Export
    @JvmField
    private Double durability;

    @HostAccess.Export
    @JvmField
    private Double max_durability;

    @HostAccess.Export
    @JvmField
    private Double is_destroyed;

    /**
     * <p>获取被传输存储到零件的特定频道信号的值</p>
     * <p>Get the value of the specific channel signal stored in the part</p>
     *
     * @param channel 信号频道名 channel name
     * @return 信号值 signal value
     */
    @HostAccess.Export
    @Nullable
    public Object get(String channel) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof LivingEntity) {
            if (((IEntityMixin) holder).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getOwner().getSubPart().variables.get(channel);
            } else return null;
        } else if (holder instanceof SubPart subPart)
            return subPart.variables.get(channel);
        else return null;
    }

    @HostAccess.Export
    public double has_connector(String connectorName) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof SubPart subPart) {
            AbstractConnector connector = subPart.getConnectors().get(connectorName);
            if (connector != null) {
                return connector.hasPart() ? 1.0 : 0.0;
            } else return 0.0;
        } else return 0.0;
    }

    @HostAccess.Export
    public double connector_offset(String connectorName, int axis) {
        if (axis > 2 || axis < 0)
            throw new IllegalArgumentException("the axis param of 'subpart.connector_offset(name, axis)' must be between 0 and 2");
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof SubPart subPart) {
            AbstractConnector connector = subPart.getConnectors().get(connectorName);
            if (connector != null) {
                var translation = connector.getPivotOffset();
                return translation.get(axis);
            } else return 0.0;
        } else return 0.0;
    }

    @HostAccess.Export
    public double connector_rotation(String connectorName, int axis) {
        if (axis > 2 || axis < 0)
            throw new IllegalArgumentException("the axis param of 'subpart.connector_offset(name, axis)' must be between 0 and 2");
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof SubPart subPart) {
            AbstractConnector connector = subPart.getConnectors().get(connectorName);
            if (connector != null) {
                var rotation = connector.getPivotRotation();
                return rotation.get(axis);
            } else return 0.0;
        } else return 0.0;
    }

    /**
     * <p>检查当前零件是否拥有指定名称的子系统</p>
     * <p>Check if the current part has a subsystem with the specified name</p>
     *
     * @param subsystemName 子系统名称 subsystem name
     * @return 如果拥有该子系统则返回1.0，否则返回0.0 returns 1.0 if the subsystem exists, 0.0 otherwise
     */
    @HostAccess.Export
    public double has_subsystem(String subsystemName) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof SubPart subPart) {
            return subPart.getSubsystems().containsKey(subsystemName) ? 1.0 : 0.0;
        } else return 0.0;
    }

    /**
     * <p>获取指定子系统的当前耐久度</p>
     * <p>Get the current durability of the specified subsystem</p>
     *
     * @param subsystemName 子系统名称 subsystem name
     * @return 子系统耐久度，如果子系统不存在则返回0.0 subsystem durability, returns 0.0 if subsystem doesn't exist
     */
    @HostAccess.Export
    public double subsystem_durability(String subsystemName) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof ISubsystemHost host) {
            AbstractSubsystem subsystem = host.getSubsystems().get(subsystemName);
            if (subsystem != null) {
                return subsystem.getDurability();
            }
        }
        return 0.0;
    }

    /**
     * <p>获取指定子系统的最大耐久度</p>
     * <p>Get the maximum durability of the specified subsystem</p>
     *
     * @param subsystemName 子系统名称 subsystem name
     * @return 子系统最大耐久度，如果子系统不存在则返回0.0 subsystem max durability, returns 0.0 if subsystem doesn't exist
     */
    @HostAccess.Export
    public double subsystem_max_durability(String subsystemName) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof ISubsystemHost host) {
            AbstractSubsystem subsystem = host.getSubsystems().get(subsystemName);
            if (subsystem != null) {
                return subsystem.getMaxDurability();
            }
        }
        return 0.0;
    }

    /**
     * <p>检查指定子系统是否处于激活状态</p>
     * <p>Check if the specified subsystem is active</p>
     *
     * @param subsystemName 子系统名称 subsystem name
     * @return 如果子系统激活则返回1.0，否则返回0.0 returns 1.0 if subsystem is active, 0.0 otherwise
     */
    @HostAccess.Export
    public double subsystem_active(String subsystemName) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof ISubsystemHost host) {
            AbstractSubsystem subsystem = host.getSubsystems().get(subsystemName);
            if (subsystem != null) {
                return subsystem.isActive() ? 1.0 : 0.0;
            }
        }
        return 0.0;
    }

    /**
     * <p>检查指定子系统是否已被摧毁</p>
     * <p>Check if the specified subsystem is destroyed</p>
     *
     * @param subsystemName 子系统名称 subsystem name
     * @return 如果子系统被摧毁则返回1.0，否则返回0.0 returns 1.0 if subsystem is destroyed, 0.0 otherwise
     */
    @HostAccess.Export
    public double subsystem_destroyed(String subsystemName) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof ISubsystemHost host) {
            AbstractSubsystem subsystem = host.getSubsystems().get(subsystemName);
            if (subsystem != null) {
                return subsystem.isDestroyed() ? 1.0 : 0.0;
            }
        }
        return 0.0;
    }

    @Nullable
    private static Double getDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity livingEntity) {
            if (((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return (double) seat.getOwner().getSubPart().getDurability();
            } else return null;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return (double) subPart.getDurability();
        else return null;
    }

    @Nullable
    private static Double getMaxDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity livingEntity) {
            if (((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return (double) seat.getOwner().getSubPart().getMaxDurability();
            } else return null;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return (double) subPart.getMaxDurability();
        else return null;
    }

    @Nullable
    private static Double isDestroyed(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity livingEntity) {
            if (((IEntityMixin) livingEntity).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getOwner().getSubPart().isDestroyed() ? 1.0 : 0.0;
            } else return null;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return subPart.isDestroyed() ? 1.0 : 0.0;
        else return null;
    }

    private IAnimatable<?> getAnimatable() {
        return anim.getHolder();
    }

    private Level getLevel() {
        return getAnimatable().getAnimLevel();
    }

}
