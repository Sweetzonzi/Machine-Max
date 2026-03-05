package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

@Getter
public class ControlPreferenceAttachment {
    public ControlPreference groundGearPreference = ControlPreference.FOLLOW_VEHICLE;
    public ControlPreference groundHandBrakePreference = ControlPreference.FOLLOW_VEHICLE;
    public ControlPreference groundDriftPreference = ControlPreference.FOLLOW_VEHICLE;
    public boolean groundSpeedTurningLimitPreference = true;

    public static final Codec<ControlPreferenceAttachment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ControlPreference.CODEC.optionalFieldOf("ground_gear_preference", ControlPreference.FOLLOW_VEHICLE).forGetter(ControlPreferenceAttachment::getGroundGearPreference),
            ControlPreference.CODEC.optionalFieldOf("ground_hand_brake_preference", ControlPreference.FOLLOW_VEHICLE).forGetter(ControlPreferenceAttachment::getGroundHandBrakePreference),
            ControlPreference.CODEC.optionalFieldOf("ground_drift_preference", ControlPreference.FOLLOW_VEHICLE).forGetter(ControlPreferenceAttachment::getGroundDriftPreference),
            Codec.BOOL.optionalFieldOf("ground_speed_turning_limit_preference", true).forGetter(ControlPreferenceAttachment::isGroundSpeedTurningLimitPreference)
    ).apply(instance, ControlPreferenceAttachment::new));

    public static final StreamCodec<FriendlyByteBuf, ControlPreferenceAttachment> STREAM_CODEC =
            StreamCodec.of(
                    // encode
                    (buf, value) -> {
                        ControlPreference.STREAM_CODEC.encode(buf, value.groundGearPreference);
                        ControlPreference.STREAM_CODEC.encode(buf, value.groundHandBrakePreference);
                        ControlPreference.STREAM_CODEC.encode(buf, value.groundDriftPreference);
                        buf.writeBoolean(value.groundSpeedTurningLimitPreference);
                    },
                    // decode
                    buf -> {
                        ControlPreference gear = ControlPreference.STREAM_CODEC.decode(buf);
                        ControlPreference handBrake = ControlPreference.STREAM_CODEC.decode(buf);
                        ControlPreference drift = ControlPreference.STREAM_CODEC.decode(buf);
                        boolean speedLimit = buf.readBoolean();
                        return new ControlPreferenceAttachment(
                                gear,
                                handBrake,
                                drift,
                                speedLimit
                        );
                    }
            );

    public ControlPreferenceAttachment(
            ControlPreference groundGearPreference,
            ControlPreference groundHandBrakePreference,
            ControlPreference groundDriftPreference,
            boolean groundSpeedTurningLimitPreference
    ) {
        this.groundGearPreference = groundGearPreference;
        this.groundHandBrakePreference = groundHandBrakePreference;
        this.groundDriftPreference = groundDriftPreference;
        this.groundSpeedTurningLimitPreference = groundSpeedTurningLimitPreference;
    }

    public ControlPreferenceAttachment() {}
}
