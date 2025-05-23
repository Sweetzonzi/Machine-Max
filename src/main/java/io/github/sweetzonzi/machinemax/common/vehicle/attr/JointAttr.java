package io.github.sweetzonzi.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

public record JointAttr(
        @Nullable Float lowerLimit,
        @Nullable Float upperLimit,
        @Nullable Float equilibrium,
        @Nullable Float stiffness,
        @Nullable Float damping
) {
    public static final Codec<JointAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("lower_limit")
                    .forGetter(j -> Optional.ofNullable(j.lowerLimit)),
            Codec.FLOAT.optionalFieldOf("upper_limit")
                    .forGetter(j -> Optional.ofNullable(j.upperLimit)),
            Codec.FLOAT.optionalFieldOf("equilibrium")
                    .forGetter(j -> Optional.ofNullable(j.equilibrium)),
            Codec.FLOAT.optionalFieldOf("stiffness")
                    .forGetter(j -> Optional.ofNullable(j.stiffness)),
            Codec.FLOAT.optionalFieldOf("damping")
                    .forGetter(j -> Optional.ofNullable(j.damping))
    ).apply(instance, (lower, upper, equal, stiff, damp) -> new JointAttr(
            lower.orElse(null),
            upper.orElse(null),
            equal.orElse(null),
            stiff.orElse(null),
            damp.orElse(null)
    )));

    public static final Codec<Map<String, JointAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//x,y,z代表x,y,z轴平移，xr,yr,zr代表x,y,z轴旋转
            CODEC//关节属性
    );
}
