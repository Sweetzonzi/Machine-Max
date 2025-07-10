package io.github.sweetzonzi.machinemax.common.vehicle.molang.part;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machinemax.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.LivingEntity;

public class GetPartVariable extends ContextFunction<IAnimatable<?>> {

    @Override
    protected Object eval(ExecutionContext<IAnimatable<IAnimatable<?>>> executionContext, ArgumentCollection argumentCollection) {
        Object ctx = executionContext.entity().getAnimatable();
        Part part = null;
        if (ctx instanceof LivingEntity) {
            if (((IEntityMixin) ctx).machine_Max$getRidingSubsystem() instanceof SeatSubsystem seat) {
                part = seat.getPart();
            } else return null;
        } else if (ctx instanceof Part)
            part = (Part) ctx;
        if (part == null) return null;
        else {
            String key = argumentCollection.getAsString(executionContext, 0);
            return part.foreignStorage.getPublic(key);
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

}
