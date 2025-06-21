package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

public record HydrodynamicAttr(
        float scale,
        Vec3 effectiveRange,
        float transSonicAmplifier,
        List<Float> forward,
        List<Float> backward,
        List<Float> leftward,
        List<Float> rightward,
        List<Float> upward,
        List<Float> downward,
        List<Float> xLift,
        List<Float> yLift,
        List<Float> zLift
        //TODO:考虑旋转对称性
) {
    public static final Codec<HydrodynamicAttr> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.optionalFieldOf("scale", 1f).forGetter(HydrodynamicAttr::scale),
                    Vec3.CODEC.optionalFieldOf("effective_range", new Vec3(1, 1, 1)).forGetter(HydrodynamicAttr::effectiveRange),
                    Codec.FLOAT.optionalFieldOf("transonic_amplifier", 5f).forGetter(HydrodynamicAttr::transSonicAmplifier),
                    Codec.FLOAT.listOf().optionalFieldOf("front_drag", List.of(0.1f,1f)).forGetter(HydrodynamicAttr::forward),
                    Codec.FLOAT.listOf().optionalFieldOf("back_drag", List.of(0.1f,1f)).forGetter(HydrodynamicAttr::backward),
                    Codec.FLOAT.listOf().optionalFieldOf("left_drag", List.of(0.1f,1f)).forGetter(HydrodynamicAttr::leftward),
                    Codec.FLOAT.listOf().optionalFieldOf("right_drag", List.of(0.1f,1f)).forGetter(HydrodynamicAttr::rightward),
                    Codec.FLOAT.listOf().optionalFieldOf("up_drag", List.of(0.1f,1f)).forGetter(HydrodynamicAttr::upward),
                    Codec.FLOAT.listOf().optionalFieldOf("down_drag", List.of(0.1f,1f)).forGetter(HydrodynamicAttr::downward),
                    Codec.FLOAT.listOf().optionalFieldOf("x_lift", List.of()).forGetter(HydrodynamicAttr::xLift),
                    Codec.FLOAT.listOf().optionalFieldOf("y_lift", List.of()).forGetter(HydrodynamicAttr::yLift),
                    Codec.FLOAT.listOf().optionalFieldOf("z_lift", List.of()).forGetter(HydrodynamicAttr::zLift)
            ).apply(instance, HydrodynamicAttr::new)
    );

    public static final Codec<Map<String, HydrodynamicAttr>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC);

    public static final HydrodynamicAttr DEFAULT = new HydrodynamicAttr(
            1f,
            new Vec3(1, 1, 1),
            5f,
            List.of(0.1f,1f),
            List.of(0.1f,1f),
            List.of(0.1f,1f),
            List.of(0.1f,1f),
            List.of(0.1f,1f),
            List.of(0.1f,1f),
            List.of(),
            List.of(),
            List.of()
    );
}
