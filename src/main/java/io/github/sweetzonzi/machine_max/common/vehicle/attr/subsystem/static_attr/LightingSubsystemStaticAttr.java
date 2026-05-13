package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;

import java.awt.Color;
import java.util.List;

@Getter
public class LightingSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final LightType lightType;
    public final float range;
    public final Color color;
    public final float intensity;
    public final float beamAngle;

    public static final Codec<Color> COLOR_CODEC = Codec.INT.listOf().xmap(
            values -> {
                int red = !values.isEmpty() ? values.get(0) : 255;
                int green = values.size() > 1 ? values.get(1) : 255;
                int blue = values.size() > 2 ? values.get(2) : 255;
                return new Color(clampColor(red), clampColor(green), clampColor(blue));
            },
            color -> List.of(color.getRed(), color.getGreen(), color.getBlue())
    );

    public static final MapCodec<LightingSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            LightType.CODEC.optionalFieldOf("light_type", LightType.BEAM).forGetter(LightingSubsystemStaticAttr::getLightType),
            Codec.FLOAT.optionalFieldOf("range", 16f).forGetter(LightingSubsystemStaticAttr::getRange),
            COLOR_CODEC.optionalFieldOf("color", Color.WHITE).forGetter(LightingSubsystemStaticAttr::getColor),
            Codec.FLOAT.optionalFieldOf("intensity", 1f).forGetter(LightingSubsystemStaticAttr::getIntensity),
            Codec.FLOAT.optionalFieldOf("beam_angle", 24f).forGetter(LightingSubsystemStaticAttr::getBeamAngle),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, LightingSubsystemStaticAttr::new));

    public LightingSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            LightType lightType,
            float range,
            Color color,
            float intensity,
            float beamAngle,
            BasicSoundAttr sounds
    ) {
        super(basicAttr, sounds);
        this.lightType = lightType;
        this.range = Math.max(0f, range);
        this.color = color;
        this.intensity = Math.clamp(intensity, 0f, 1f);
        this.beamAngle = Math.clamp(beamAngle, 0f, 179f);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LIGHTING;
    }

    private static int clampColor(int value) {
        return Math.clamp(value, 0, 255);
    }

    public enum LightType {
        BEAM,
        POINT;

        public static final Codec<LightType> CODEC = Codec.STRING.xmap(
                value -> switch (value.toLowerCase()) {
                    case "point" -> POINT;
                    case "beam" -> BEAM;
                    default -> throw new IllegalArgumentException("Unknown lighting type: " + value);
                },
                value -> value.name().toLowerCase()
        );
    }
}
