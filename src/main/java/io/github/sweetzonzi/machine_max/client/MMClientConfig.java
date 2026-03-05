package io.github.sweetzonzi.machine_max.client;

import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreferenceAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.ControlPreferencePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class MMClientConfig {

    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.DoubleValue GROUND_FULL_POWER_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_FULL_STEERING_TIME;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_AUTO_SWITCH_GEAR;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_AUTO_HANDBRAKE;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_DRIFT_ASSIST;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_POSE_PREFERENCE;
    private static final ModConfigSpec.BooleanValue GROUND_SPEED_TURNING_LIMIT;
    public static final ModConfigSpec.DoubleValue SHIP_FULL_STEERING_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_POWER_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_PITCH_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_YAW_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_ROLL_TIME;

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        var preferences = new ControlPreferenceAttachment(
                getGroundAutoSwitchGear(),
                getGroundAutoHandbrake(),
                getGroundDriftAssist(),
                getGroundSpeedTurningLimit()
        );
        event.getPlayer().setData(MMAttachments.getCONTROL_PREFERENCE(), preferences);
        PacketDistributor.sendToServer(new ControlPreferencePayload(preferences));
    }


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("ground_vehicle");

        GROUND_FULL_POWER_TIME = builder
                .comment("Time (in seconds) to reach full throttle when holding key")
                .defineInRange("full_power_time", 1.2, 0.05, 99999.0);

        GROUND_FULL_STEERING_TIME = builder
                .comment("Time (in seconds) to reach full steering when holding key")
                .defineInRange("full_steering_time", 0.4, 0.05, 99999.0);

        GROUND_AUTO_SWITCH_GEAR = builder
                .comment("Whether to automatically shift gears based on input, vehicle speed and engine RPM")
                .defineEnum("auto_switch_gear", ControlPreference.FOLLOW_VEHICLE);

        GROUND_AUTO_HANDBRAKE = builder
                .comment("Whether to automatically apply handbrake when vehicle stops and release it when starting")
                .defineEnum("auto_handbrake", ControlPreference.FOLLOW_VEHICLE);

        GROUND_DRIFT_ASSIST = builder
                .comment("Whether to automatically counter-steer during drifting to maintain control")
                .defineEnum("drift_assist", ControlPreference.FOLLOW_VEHICLE);

        GROUND_POSE_PREFERENCE = builder
                .comment("Whether to automatically rotate camera view to follow vehicle orientation")
                .defineEnum("pose_preference", ControlPreference.FOLLOW_VEHICLE);

        GROUND_SPEED_TURNING_LIMIT = builder
                .comment("Whether to limit lateral acceleration during high-speed turning to prevent loss of control or rollover")
                .define("speed_turning_limit", true);

        builder.pop();

        builder.push("ship");

        SHIP_FULL_STEERING_TIME = builder
                .comment("Time (in seconds) to reach full steering when holding key")
                .defineInRange("full_steering_time", 0.25, 0.05, 99999.0);

        builder.pop();

        builder.push("plane");

        PLANE_FULL_POWER_TIME = builder
                .comment("Time (in seconds) to reach full throttle when holding key")
                .defineInRange("full_power_time", 2.5, 0.05, 99999.0);

        PLANE_FULL_PITCH_TIME = builder
                .comment("Time (in seconds) to reach full pitch when holding key")
                .defineInRange("full_pitch_time", 0.25, 0.05, 99999.0);

        PLANE_FULL_YAW_TIME = builder
                .comment("Time (in seconds) to reach full yaw when holding key")
                .defineInRange("full_yaw_time", 0.25, 0.05, 99999.0);

        PLANE_FULL_ROLL_TIME = builder
                .comment("Time (in seconds) to reach full roll when holding key")
                .defineInRange("full_roll_time", 0.25, 0.05, 99999.0);

        builder.pop();

        CLIENT_SPEC = builder.build();
    }

    /**
     * 获取地面载具每tick动力变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getGroundFullPowerStep() {
        return (int) (100 / (GROUND_FULL_POWER_TIME.get() * 20));
    }

    /**
     * 获取地面载具每tick转向变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getGroundFullSteeringStep() {
        return (int) (100 / (GROUND_FULL_STEERING_TIME.get() * 20));
    }

    /**
     * 获取船载具每tick转向变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getShipFullSteeringStep() {
        return (int) (100 / (SHIP_FULL_STEERING_TIME.get() * 20));
    }

    /**
     * 获取飞机载具每tick动力变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getPlaneFullPowerStep() {
        return (int) (100 / (PLANE_FULL_POWER_TIME.get() * 20));
    }

    /**
     * 获取飞机载具每tick俯仰变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getPlaneFullPitchStep() {
        return (int) (100 / (PLANE_FULL_PITCH_TIME.get() * 20));
    }

    /**
     * 获取飞机载具每tick偏航变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getPlaneFullYawStep() {
        return (int) (100 / (PLANE_FULL_YAW_TIME.get() * 20));
    }

    /**
     * 获取飞机载具每tick滚转变化的百分比步长
     *
     * @return 步长百分比，如25表示每tick增加25%
     */
    public static int getPlaneFullRollStep() {
        return (int) (100 / (PLANE_FULL_ROLL_TIME.get() * 20));
    }

    /**
     * 获取地面载具自动换挡偏好设置
     *
     * @return 控制偏好枚举值
     */
    public static ControlPreference getGroundAutoSwitchGear() {
        return GROUND_AUTO_SWITCH_GEAR.get();
    }

    /**
     * 获取地面载具自动手刹偏好设置
     *
     * @return 控制偏好枚举值
     */
    public static ControlPreference getGroundAutoHandbrake() {
        return GROUND_AUTO_HANDBRAKE.get();
    }

    /**
     * 获取地面载具漂移辅助偏好设置
     *
     * @return 控制偏好枚举值
     */
    public static ControlPreference getGroundDriftAssist() {
        return GROUND_DRIFT_ASSIST.get();
    }

    /**
     * 获取地面载具姿势偏好设置
     *
     * @return 控制偏好枚举值
     */
    public static ControlPreference getGroundPosePreference() {
        return GROUND_POSE_PREFERENCE.get();
    }

    /**
     * 获取是否在高速行驶时限制转向时的侧向加速度
     *
     * @return true表示限制侧向加速度以避免失控或侧翻
     */
    public static boolean getGroundSpeedTurningLimit() {
        return GROUND_SPEED_TURNING_LIMIT.get();
    }
}
