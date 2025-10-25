package io.github.sweetzonzi.machine_max.common.vehicle.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.graalvm.polyglot.HostAccess;

public class SubPartBinding {
    private final AnimInstance anim;

    public SubPartBinding(AnimInstance anim) {
        this.anim = anim;
    }

    @HostAccess.Export
    public Object get(String key) {
        var holder = getAnimatable().getAnimatable();
        if (holder instanceof LivingEntity) {
            if (((IEntityMixin) holder).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getOwner().getSubPart().signalStorage.get(key);
            } else return null;
        } else if (holder instanceof SubPart subPart)
            return subPart.signalStorage.get(key);
        else return null;
    }

//    @HostAccess.Export
//    private Float getDurability(IAnimatable<?> ctx) {
//        if (ctx.getAnimatable() instanceof LivingEntity) {
//            if (((IEntityMixin) ctx).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
//                return seat.getOwner().getSubPart().getPart().getDurability();
//            } else return null;
//        } else if (ctx.getAnimatable() instanceof Part part)
//            return part.getDurability();
//        else return null;
//    }
//    @HostAccess.Export
//    private Float getMaxDurability(IAnimatable<?> ctx) {
//        if (ctx.getAnimatable() instanceof LivingEntity) {
//            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
//                return seat.getOwner().getSubPart().getPart().getType().getBasicDurability();
//            } else return null;
//        } else if (ctx.getAnimatable() instanceof Part part)
//            return part.getType().getBasicDurability();
//        else return null;
//    }
//    @HostAccess.Export
//    private Float getIntegrity(IAnimatable<?> ctx) {
//        if (ctx.getAnimatable() instanceof LivingEntity) {
//            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
//                return seat.getOwner().getSubPart().getPart().getIntegrity();
//            } else return null;
//        } else if (ctx.getAnimatable() instanceof Part part)
//            return part.getIntegrity();
//        else return null;
//    }
//    @HostAccess.Export
//    private Float getMaxIntegrity(IAnimatable<?> ctx) {
//        if (ctx.getAnimatable() instanceof LivingEntity) {
//            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
//                return seat.getOwner().getSubPart().getPart().getType().getBasicIntegrity();
//            } else return null;
//        } else if (ctx.getAnimatable() instanceof Part part)
//            return part.getType().getBasicIntegrity();
//        else return null;
//    }

    private IAnimatable<?> getAnimatable() {
        return anim.getHolder();
    }

    private Level getLevel() {
        return getAnimatable().getAnimLevel();
    }
}
