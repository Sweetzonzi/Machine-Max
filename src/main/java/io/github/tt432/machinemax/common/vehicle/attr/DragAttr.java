package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record DragAttr(
        float forward,
        float backward,
        float leftward,
        float rightward,
        float upward,
        float downward
        //TODO:考虑旋转对称性
) {
    public static final Codec<DragAttr> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.FLOAT.fieldOf("front").forGetter(DragAttr::forward),
                    Codec.FLOAT.fieldOf("back").forGetter(DragAttr::backward),
                    Codec.FLOAT.fieldOf("left").forGetter(DragAttr::leftward),
                    Codec.FLOAT.fieldOf("right").forGetter(DragAttr::rightward),
                    Codec.FLOAT.fieldOf("up").forGetter(DragAttr::upward),
                    Codec.FLOAT.fieldOf("down").forGetter(DragAttr::downward)
            ).apply(instance, DragAttr::new)
    );
}
