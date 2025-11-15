package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.registry.MMDataRegistries;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

import java.util.function.Function;

@Getter
abstract public class AbstractSubsystemStaticAttr {

    public final float basicDurability;

    protected AbstractSubsystemStaticAttr(float basicDurability) {
        this.basicDurability = basicDurability;
    }

    public abstract MapCodec<? extends AbstractSubsystemStaticAttr> codec();

    public abstract SubsystemTypes getType();

    public static final Codec<AbstractSubsystemStaticAttr> CODEC = MMDataRegistries.getSUBSYSTEM_STATIC_ATTR_CODEC().byNameCodec()
            .dispatch(
                    AbstractSubsystemStaticAttr::codec,
                    Function.identity()
            );
}
