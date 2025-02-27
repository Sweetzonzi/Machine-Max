package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.common.vehicle.attr.PortAttr;
import lombok.Getter;

@Getter
public class SeatSubSystemAttr extends AbstractSubSystemAttr {
    public final String locatorName;
    public final PortAttr portAttr;

    public static final MapCodec<SeatSubSystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("locator").forGetter(SeatSubSystemAttr::getLocatorName),
            PortAttr.CODEC.fieldOf("port").forGetter(SeatSubSystemAttr::getPortAttr)
    ).apply(instance, SeatSubSystemAttr::new));

    public SeatSubSystemAttr(String locatorName, PortAttr portAttr) {
        super();
        this.locatorName = locatorName;
        this.portAttr = portAttr;
        this.portAttrs.put(portAttr.name, portAttr);
    }

    @Override
    public MapCodec<? extends AbstractSubSystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.SEAT;
    }
}
