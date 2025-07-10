package io.github.sweetzonzi.machinemax.common.vehicle.molang.vehicle;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.LivingEntity;

public class GetVehicleVariable extends ContextFunction<IAnimatable<?>> {

    @Override
    protected Object eval(ExecutionContext<IAnimatable<IAnimatable<?>>> executionContext, ArgumentCollection argumentCollection) {
        Object ctx = executionContext.entity().getAnimatable();
        VehicleCore vehicle = null;
        if (ctx instanceof LivingEntity) {
            if (((IEntityMixin) ctx).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
                vehicle = seat.getPart().vehicle;
            } else return null;
        } else if (ctx instanceof Part)
            vehicle = ((Part) ctx).vehicle;
        if (vehicle == null) return null;
        else {
            String key = argumentCollection.getAsString(executionContext, 0);
            return vehicle.subSystemController.foreignStorage.getPublic(key);
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

}
