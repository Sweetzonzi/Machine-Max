package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 动画关节轴的最大驱动力（或力矩）与目标速度。 */
public record JointDriverAxisAttr(float maxForce, float maxSpeed) {

    public static final Codec<JointDriverAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("max_force").forGetter(JointDriverAxisAttr::maxForce),
            Codec.FLOAT.fieldOf("max_speed").forGetter(JointDriverAxisAttr::maxSpeed)
    ).apply(instance, JointDriverAxisAttr::new));

    public JointDriverAxisAttr {
        if (!Float.isFinite(maxForce) || maxForce <= 0f) {
            throw new IllegalArgumentException("max_force must be finite and greater than zero");
        }
        if (!Float.isFinite(maxSpeed) || maxSpeed <= 0f) {
            throw new IllegalArgumentException("max_speed must be finite and greater than zero");
        }
    }
}
