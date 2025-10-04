package io.github.sweetzonzi.machine_max.common.vehicle.molang.part;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.molang.core.function.ContextFunction;
import cn.solarmoon.spark_core.molang.engine.runtime.ExecutionContext;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.world.entity.LivingEntity;

public class GetPartVariable extends ContextFunction<IAnimatable<?>> {

    @Override
    protected Object eval(ExecutionContext<IAnimatable<IAnimatable<?>>> executionContext, ArgumentCollection argumentCollection) {
        Object ctx = executionContext.entity().getAnimatable();
        SubPart subPart = null;
        if (ctx instanceof LivingEntity) {
            if (((IEntityMixin) ctx).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat) {
                subPart = seat.getOwner().getSubPart();
            } else return null;
        } else if (ctx instanceof SubPart)
            subPart = (SubPart) ctx;
        if (subPart == null) return null;
        else {
            String key = argumentCollection.getAsString(executionContext, 0);
            return subPart.animController.getForeignStorage().getPublic(key);
        }
    }

    @Override
    public boolean validateArgumentSize(int size) {
        return size == 1;
    }

}
