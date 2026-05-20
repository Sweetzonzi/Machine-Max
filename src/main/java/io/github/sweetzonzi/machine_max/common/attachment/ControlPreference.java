package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.serialization.Codec;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CarControllerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
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

    /**
     * 判断是否启用分离模式（W=油门, S=刹车，油门刹车解耦）。
     * 服务端入口，从玩家 Attachment 读取。
     *
     * @param ctrl 车辆控制器子系统
     * @return true=分离模式, false=意图模式
     */
    public static boolean shouldRawThrottleBrake(CarControllerSubsystem ctrl) {
        if (ctrl.getController() == null)
            return false;
        var data = ctrl.getController().getData(MMAttachments.getCONTROL_PREFERENCE());
        return data.groundSeparateThrottleBrake;
    }

    @Override
    public @NotNull String getSerializedName() {
        return name().toLowerCase();
    }
}
