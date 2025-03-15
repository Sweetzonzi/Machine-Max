package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.Map;

@Getter
public class WheelSubsystemAttr extends AbstractSubsystemAttr {

    public final Map<String, Pair<Float, Float>> wheelAttrs;

    public static final Codec<Pair<Float, Float>> WHEEL_CODEC = Codec.pair(
            Codec.FLOAT.fieldOf("radius").codec(),
            Codec.FLOAT.fieldOf("step_height").codec()
    );
    public static final Codec<Map<String, Pair<Float, Float>>> WHEEL_MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,
            WHEEL_CODEC
    );
    public static final MapCodec<WheelSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WHEEL_MAP_CODEC.fieldOf("wheels").forGetter(WheelSubsystemAttr::getWheelAttrs)
    ).apply(instance, WheelSubsystemAttr::new));

    public WheelSubsystemAttr(Map<String, Pair<Float, Float>> wheelAttrs) {
        this.wheelAttrs = wheelAttrs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return null;
    }
}
