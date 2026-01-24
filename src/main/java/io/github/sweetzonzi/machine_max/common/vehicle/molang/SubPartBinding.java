package io.github.sweetzonzi.machine_max.common.vehicle.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import kotlin.jvm.JvmField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.graalvm.polyglot.HostAccess;
import org.jetbrains.annotations.Nullable;

public class SubPartBinding {
    private final AnimInstance anim;

    public SubPartBinding(AnimInstance anim) {
        this.anim = anim;
        this.durability = getDurability(getAnimatable());
        this.max_durability = getMaxDurability(getAnimatable());
        this.is_destroyed = isDestroyed(getAnimatable());
    }

    @HostAccess.Export
    @JvmField
    public final Double durability;

    @HostAccess.Export
    @JvmField
    public final Double max_durability;

    @HostAccess.Export
    @JvmField
    public final Double is_destroyed;

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
                return seat.getOwner().getSubPart().signalStorage.get(channel);
            } else return null;
        } else if (holder instanceof SubPart subPart)
            return subPart.signalStorage.get(channel);
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
