package io.github.sweetzonzi.machine_max.common.vehicle.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import kotlin.jvm.JvmField;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.graalvm.polyglot.HostAccess;

public class VehicleBinding{
    private final AnimInstance anim;

    public VehicleBinding(AnimInstance anim) {
        this.anim = anim;
        this.durability = getDurability(getAnimatable());
        this.max_durability = getMaxDurability(getAnimatable());
    }

    @HostAccess.Export
    @JvmField
    public final Double durability;

    @HostAccess.Export
    @JvmField
    public final Double max_durability;

    @HostAccess.Export
    public Object get(String key) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof LivingEntity) {
            if (((IEntityMixin) holder).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getOwner().getSubPart().getPart().getVehicle().getSubSystemController().signalStorage.get(key);
            } else return null;
        } else if (holder instanceof SubPart subPart) {
            return subPart.part.getVehicle().getSubSystemController().signalStorage.get(key);
        }
        else return null;
    }

    private static Double getDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return (double) seat.getOwner().getSubPart().getPart().getVehicle().getHp();
            } else return 0.0;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return (double) subPart.part.getVehicle().getHp();
        else return 0.0;
    }

    private static Double getMaxDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                //TODO:修改为总血量
                return (double) seat.getOwner().getSubPart().getPart().getVehicle().getHp();
            } else return 0.0;
        } else if (ctx.getAnimatable() instanceof SubPart subPart)
            return (double) subPart.part.getVehicle().getHp();
        else return 0.0;
    }

    private IAnimatable<?> getAnimatable() {
        return anim.getHolder();
    }

    private Level getLevel() {
        return getAnimatable().getAnimLevel();
    }
}
