package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.CarControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.SeatSubsystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

public enum ControlPreference implements StringRepresentable {
    FOLLOW_VEHICLE,
    ALWAYS_ENABLED,
    ALWAYS_DISABLED;

    public static final Codec<ControlPreference> CODEC = StringRepresentable.fromEnum(ControlPreference::values);
    public static final StreamCodec<FriendlyByteBuf, ControlPreference> STREAM_CODEC =
            StreamCodec.of(
                    (buf, value) -> buf.writeUtf(value.name()),   // 编码：写入名称
                    buf -> ControlPreference.valueOf(buf.readUtf()) // 解码：读取名称并转换
            );

    public static boolean shouldAutoHandBrake(CarControllerSubsystem groundController) {
        if (groundController.getController() == null)
            return groundController.getAttr().getStaticAttribute().isAutoHandBrake();
        var data = groundController.getController().getData(MMAttachments.getCONTROL_PREFERENCE());
        return switch (data.groundHandBrakePreference) {
            case ALWAYS_ENABLED -> true;
            case ALWAYS_DISABLED -> false;
            default -> groundController.getAttr().getStaticAttribute().isAutoHandBrake();
        };
    }

    public static boolean shouldDriftAssist(CarControllerSubsystem groundController) {
        if (groundController.getController() == null)
            return groundController.getAttr().getStaticAttribute().isDriftAssist();
        var data = groundController.getController().getData(MMAttachments.getCONTROL_PREFERENCE());
        return switch (data.groundDriftPreference) {
            case ALWAYS_ENABLED -> true;
            case ALWAYS_DISABLED -> false;
            default -> groundController.getAttr().getStaticAttribute().isDriftAssist();
        };
    }

    public static boolean shouldFollowPose(SeatSubsystem groundController) {
        return switch (MMClientConfig.getGroundPosePreference()) {
            case ALWAYS_ENABLED -> true;
            case ALWAYS_DISABLED -> false;
            default -> groundController.getAttr().getStaticAttribute().getViews().followVehicle();
        };
    }

    public static boolean shouldAutoSwitchGear(CarControllerSubsystem groundController) {
        if (groundController.getController() == null)
            return !groundController.getAttr().getStaticAttribute().isManualGearShift();
        var data = groundController.getController().getData(MMAttachments.getCONTROL_PREFERENCE());
        return switch (data.groundGearPreference) {
            case ALWAYS_ENABLED -> true;
            case ALWAYS_DISABLED -> false;
            default -> !groundController.getAttr().getStaticAttribute().isManualGearShift();
        };
    }

    public static boolean shouldLimitSpeedTurning(CarControllerSubsystem groundController) {
        if (groundController.getController() == null) return true;
        var data = groundController.getController().getData(MMAttachments.getCONTROL_PREFERENCE());
        return data.groundSpeedTurningLimitPreference;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name().toLowerCase();
    }
}
