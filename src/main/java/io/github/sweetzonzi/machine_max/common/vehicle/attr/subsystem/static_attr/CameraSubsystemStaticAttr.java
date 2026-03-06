package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

@Getter
public class CameraSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public static final MapCodec<CameraSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr)
    ).apply(instance, CameraSubsystemStaticAttr::new));

    protected CameraSubsystemStaticAttr(BasicSubsystemStaticAttr.BasicAttr basicAttr) {
        super(basicAttr);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAMERA;
    }

}
