package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public record DragAttr(
        int priority,
        Vec3 center,
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
    public static final Codec<DragAttr> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(DragAttr::priority),
                    Vec3.CODEC.optionalFieldOf("center", Vec3.ZERO).forGetter(DragAttr::center),
                    Vec3.CODEC.optionalFieldOf("effective_range", new Vec3(1, 1, 1)).forGetter(DragAttr::effectiveRange),
                    Codec.FLOAT.optionalFieldOf("transonic_amplifier", 5f).forGetter(DragAttr::transSonicAmplifier),
                    Codec.FLOAT.listOf().optionalFieldOf("front_drag", List.of(0.1f,1f)).forGetter(DragAttr::forward),
                    Codec.FLOAT.listOf().optionalFieldOf("back_drag", List.of(0.1f,1f)).forGetter(DragAttr::backward),
                    Codec.FLOAT.listOf().optionalFieldOf("left_drag", List.of(0.1f,1f)).forGetter(DragAttr::leftward),
                    Codec.FLOAT.listOf().optionalFieldOf("right_drag", List.of(0.1f,1f)).forGetter(DragAttr::rightward),
                    Codec.FLOAT.listOf().optionalFieldOf("up_drag", List.of(0.1f,1f)).forGetter(DragAttr::upward),
                    Codec.FLOAT.listOf().optionalFieldOf("down_drag", List.of(0.1f,1f)).forGetter(DragAttr::downward),
                    Codec.FLOAT.listOf().optionalFieldOf("x_lift", List.of()).forGetter(DragAttr::xLift),
                    Codec.FLOAT.listOf().optionalFieldOf("y_lift", List.of()).forGetter(DragAttr::yLift),
                    Codec.FLOAT.listOf().optionalFieldOf("z_lift", List.of()).forGetter(DragAttr::zLift)
            ).apply(instance, DragAttr::new)
    );
}
