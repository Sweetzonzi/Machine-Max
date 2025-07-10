package io.github.sweetzonzi.machinemax.common.vehicle.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.binding.ContextBinding;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.molang.part.GetPartVariable;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.LivingEntity;

public class PartBinding extends ContextBinding {
    public static final PartBinding INSTANCE = new PartBinding();

    private PartBinding() {
        var("durability", PartBinding::getDurability);
        var("max_durability", PartBinding::getMaxDurability);
        var("integrity", PartBinding::getIntegrity);
        var("max_integrity", PartBinding::getMaxIntegrity);
        function("get", new GetPartVariable());
    }

    private static Float getDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getPart().getDurability();
            } else return null;
        } else if (ctx.getAnimatable() instanceof Part part)
            return part.getDurability();
        else return null;
    }

    private static Float getMaxDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getPart().getType().getBasicDurability();
            } else return null;
        } else if (ctx.getAnimatable() instanceof Part part)
            return part.getType().getBasicDurability();
        else return null;
    }

    private static Float getIntegrity(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getPart().getIntegrity();
            } else return null;
        } else if (ctx.getAnimatable() instanceof Part part)
            return part.getIntegrity();
        else return null;
    }

    private static Float getMaxIntegrity(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getPart().getType().getBasicIntegrity();
            } else return null;
        } else if (ctx.getAnimatable() instanceof Part part)
            return part.getType().getBasicIntegrity();
        else return null;
    }
}
