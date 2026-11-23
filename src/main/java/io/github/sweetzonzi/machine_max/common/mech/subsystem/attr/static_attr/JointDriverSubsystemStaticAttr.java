package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;

import java.util.Map;

/** 动画关节执行器型号参数：旋转顺序及参与动画跟随的平动轴、旋转轴。 */
@Getter
public class JointDriverSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final String rotationOrder;
    public final Map<Axis, JointDriverAxisAttr> axes;

    public static final MapCodec<JointDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.optionalFieldOf("rotation_order", "ZYX").forGetter(JointDriverSubsystemStaticAttr::getRotationOrder),
            Codec.unboundedMap(Axis.CODEC, JointDriverAxisAttr.CODEC).fieldOf("axes").forGetter(JointDriverSubsystemStaticAttr::getAxes),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, JointDriverSubsystemStaticAttr::new));

    public JointDriverSubsystemStaticAttr(
            BasicAttr basicAttr,
            String rotationOrder,
            Map<Axis, JointDriverAxisAttr> axes,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        if (axes.isEmpty()) throw new IllegalArgumentException("axes must not be empty");
        if (axes.keySet().stream().anyMatch(axis -> axis.getValue() < 0 || axis.getValue() > 5)) {
            throw new IllegalArgumentException("axes must be one of X, Y, Z, XR, YR or ZR");
        }
        com.jme3.bullet.RotationOrder.valueOf(rotationOrder);
        this.rotationOrder = rotationOrder;
        this.axes = Map.copyOf(axes);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }
}
