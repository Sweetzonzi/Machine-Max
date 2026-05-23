package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

@Getter
public class WheelDriverSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    
    public record BrakeSoundAttr(
            BasicSoundAttr basicSounds,
            SoundEvent brakeOn,
            SoundEvent brakeOff
    ) {
        public static final BrakeSoundAttr DEFAULT = new BrakeSoundAttr(
                BasicSoundAttr.DEFAULT,
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.wheel_driver.brake_on"), 16),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.wheel_driver.brake_off"), 16)
        );
        
        public static final Codec<BrakeSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(BrakeSoundAttr::basicSounds),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("brake_on", DEFAULT.brakeOn).forGetter(BrakeSoundAttr::brakeOn),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("brake_off", DEFAULT.brakeOff).forGetter(BrakeSoundAttr::brakeOff)
        ).apply(instance, BrakeSoundAttr::new));
    }
    public final List<String> controlSignalKeys;
    public final StaticWheelRollingAxisAttr rollingAxis;
    public final StaticWheelSteeringAxisAttr steeringAxis;
    public final boolean absEnabled;
    public final float absTargetSlipRatio;
    public final float absWheelRadius;
    public final BrakeSoundAttr sounds;//刹车音效配置

    public static final MapCodec<WheelDriverSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("car_control")).forGetter(WheelDriverSubsystemStaticAttr::getControlSignalKeys),
            StaticWheelRollingAxisAttr.CODEC.optionalFieldOf("roll", new StaticWheelRollingAxisAttr(
                    10000f,
                    3500f,
                    0f,
                    30000f
            )).forGetter(WheelDriverSubsystemStaticAttr::getRollingAxis),
            StaticWheelSteeringAxisAttr.CODEC.optionalFieldOf("steering", new StaticWheelSteeringAxisAttr(
                    2000f,
                    180f
            )).forGetter(WheelDriverSubsystemStaticAttr::getSteeringAxis),
            Codec.BOOL.optionalFieldOf("abs_enabled", false).forGetter(WheelDriverSubsystemStaticAttr::isAbsEnabled),
            Codec.FLOAT.optionalFieldOf("abs_target_slip_ratio", 0.15f).forGetter(WheelDriverSubsystemStaticAttr::getAbsTargetSlipRatio),
            Codec.FLOAT.optionalFieldOf("abs_wheel_radius", 0.3f).forGetter(WheelDriverSubsystemStaticAttr::getAbsWheelRadius),
            BrakeSoundAttr.CODEC.optionalFieldOf("sounds", BrakeSoundAttr.DEFAULT).forGetter(WheelDriverSubsystemStaticAttr::getSounds)
    ).apply(instance, WheelDriverSubsystemStaticAttr::new
    ));

    public WheelDriverSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            List<String> controlSignalKeys,
            StaticWheelRollingAxisAttr rollingAxis,
            StaticWheelSteeringAxisAttr steeringAxis,
            boolean absEnabled,
            float absTargetSlipRatio,
            float absWheelRadius,
            BrakeSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.controlSignalKeys = controlSignalKeys;
        this.rollingAxis = rollingAxis;
        this.steeringAxis = steeringAxis;
        this.absEnabled = absEnabled;
        this.absTargetSlipRatio = absTargetSlipRatio;
        this.absWheelRadius = absWheelRadius;
        this.sounds = sounds;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.WHEEL;
    }

    public SoundEvent getBrakeOnSound() {
        return sounds.brakeOn;
    }

    public SoundEvent getBrakeOffSound() {
        return sounds.brakeOff;
    }

    public record StaticWheelRollingAxisAttr(
            float maxForce,
            float maxBrakeForce,
            float maxHandBrakeForce,
            float maxRpm
    ) {
        public static final Codec<StaticWheelRollingAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("max_drive_force", 10000f).forGetter(StaticWheelRollingAxisAttr::maxForce),
                Codec.FLOAT.optionalFieldOf("max_brake_force", 3500f).forGetter(StaticWheelRollingAxisAttr::maxBrakeForce),
                Codec.FLOAT.optionalFieldOf("max_hand_brake_force", 0f).forGetter(StaticWheelRollingAxisAttr::maxHandBrakeForce),
                Codec.FLOAT.optionalFieldOf("max_rpm", 30000f).forGetter(StaticWheelRollingAxisAttr::maxRpm)
        ).apply(instance, StaticWheelRollingAxisAttr::new));
    }

    public record StaticWheelSteeringAxisAttr(
            float maxForce,
            float maxSpeed
    ) {
        public static final Codec<StaticWheelSteeringAxisAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.FLOAT.optionalFieldOf("max_force", 2000f).forGetter(StaticWheelSteeringAxisAttr::maxForce),
                Codec.FLOAT.optionalFieldOf("max_speed", 180f).forGetter(StaticWheelSteeringAxisAttr::maxSpeed)
        ).apply(instance, StaticWheelSteeringAxisAttr::new));
    }
}
