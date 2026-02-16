package io.github.sweetzonzi.machine_max.client;

import net.neoforged.neoforge.common.ModConfigSpec;

public class MMClientConfig {

    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.DoubleValue GROUND_FULL_POWER_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_FULL_STEERING_TIME;
    public static final ModConfigSpec.DoubleValue SHIP_FULL_STEERING_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_POWER_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_PITCH_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_YAW_TIME;
    public static final ModConfigSpec.DoubleValue PLANE_FULL_ROLL_TIME;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("ground_vehicle");

        GROUND_FULL_POWER_TIME = builder
                .comment("Time (in seconds) to reach full throttle when holding key")
                .defineInRange("full_power_time", 1.2, 0.05, 99999.0);

        GROUND_FULL_STEERING_TIME = builder
                .comment("Time (in seconds) to reach full steering when holding key")
                .defineInRange("full_steering_time", 0.4, 0.05, 99999.0);

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
}
