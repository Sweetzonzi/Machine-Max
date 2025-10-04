package io.github.sweetzonzi.machine_max.common.vehicle.molang;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.binding.ContextBinding;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.molang.vehicle.GetVehicleVariable;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.LivingEntity;

public class VehicleBinding  extends ContextBinding {
    public static final VehicleBinding INSTANCE = new VehicleBinding();

    private VehicleBinding() {
        var("durability", VehicleBinding::getDurability);
        var("max_durability", VehicleBinding::getMaxDurability);
        function("get", new GetVehicleVariable());
    }

    private static Float getDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                return seat.getOwner().getSubPart().getPart().getVehicle().getHp();
            } else return null;
        } else if (ctx.getAnimatable() instanceof Part part)
            return part.getVehicle().getHp();
        else return null;
    }

    private static Float getMaxDurability(IAnimatable<?> ctx) {
        if (ctx.getAnimatable() instanceof LivingEntity) {
            if (((IEntityMixin) ctx.getAnimatable()).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                //TODO:修改为总血量
                return seat.getOwner().getSubPart().getPart().getVehicle().getHp();
            } else return null;
        } else if (ctx.getAnimatable() instanceof Part part)
            return part.getVehicle().getHp();
        else return null;
    }
}
