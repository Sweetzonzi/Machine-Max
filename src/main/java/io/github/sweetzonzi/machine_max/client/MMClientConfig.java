package io.github.sweetzonzi.machine_max.client;

import io.github.sweetzonzi.machine_max.common.attachment.ControlPreference;
import io.github.sweetzonzi.machine_max.common.attachment.ControlPreferenceAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.ControlPreferencePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.network.PacketDistributor;
@EventBusSubscriber(value = Dist.CLIENT)
public class MMClientConfig {

    public static final ModConfigSpec CLIENT_SPEC;

    private static final ModConfigSpec.DoubleValue GROUND_FULL_POWER_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_POWER_OFF_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_FULL_BRAKE_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_BRAKE_OFF_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_FULL_STEERING_TIME;
    private static final ModConfigSpec.DoubleValue GROUND_STEERING_OFF_TIME;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_AUTO_SWITCH_GEAR;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_AUTO_HANDBRAKE;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_DRIFT_ASSIST;
    private static final ModConfigSpec.EnumValue<ControlPreference> GROUND_POSE_PREFERENCE;
    private static final ModConfigSpec.BooleanValue GROUND_SPEED_TURNING_LIMIT;
    private static final ModConfigSpec.BooleanValue GROUND_SEPARATE_THROTTLE_BRAKE;
    /** 是否在启动后首次进入标题画面时显示欢迎页面 */
    private static final ModConfigSpec.BooleanValue SHOW_WELCOME_SCREEN;

    // ---- 视觉特效 ----
    private static final ModConfigSpec.BooleanValue OVERLOAD_ENABLED;
    private static final ModConfigSpec.BooleanValue SUPPRESSION_ENABLED;
    private static final ModConfigSpec.DoubleValue OVERLOAD_INTENSITY;
    private static final ModConfigSpec.DoubleValue SUPPRESSION_INTENSITY;
    private static final ModConfigSpec.BooleanValue RENDER_HIT_WHITENING;
    private static final ModConfigSpec.BooleanValue RENDER_DESTROY_BLACKENING;
    private static final ModConfigSpec.BooleanValue RENDER_FORCE_TRANSLUCENT_PARTS;

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
                getGroundSpeedTurningLimit(),
                isSeparateThrottleBrake()
        );
        event.getPlayer().setData(MMAttachments.getCONTROL_PREFERENCE(), preferences);
        PacketDistributor.sendToServer(new ControlPreferencePayload(preferences));
    }

    public static void onChangeConfig(ModConfigEvent.Reloading event) {
        //不使用@SubscribeEvent注解，而是在MachineMaxClient中注册监听器，因为与onPlayerLogin的bus不同
        if (event.getConfig().getSpec() == CLIENT_SPEC) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                var preferences = new ControlPreferenceAttachment(
                        getGroundAutoSwitchGear(),
                        getGroundAutoHandbrake(),
                        getGroundDriftAssist(),
                        getGroundSpeedTurningLimit(),
                        isSeparateThrottleBrake()
                );
                player.setData(MMAttachments.getCONTROL_PREFERENCE(), preferences);
                PacketDistributor.sendToServer(new ControlPreferencePayload(preferences));
            }
        }
    }


    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SHOW_WELCOME_SCREEN = builder
                .comment("Show the welcome screen when entering the title screen for the first time after launch.\nSet to false to skip it. Can be re-enabled in the config menu later.\nDefault: true")
                .define("show_welcome_screen", true);

        builder.push("visual_effects");

        OVERLOAD_ENABLED = builder
                .comment("Enable overload blackout/redout post-processing effect.\nDefault: true")
                .define("overload_enabled", true);

        SUPPRESSION_ENABLED = builder
                .comment("Enable suppression (desaturation/grayscale) post-processing effect.\nDefault: true")
                .define("suppression_enabled", true);

        OVERLOAD_INTENSITY = builder
                .comment("Overload effect intensity multiplier.\nRange: 0.0 ~ 1.0. Default: 1.0")
                .defineInRange("overload_intensity", 1.0, 0.0, 1.0);

        SUPPRESSION_INTENSITY = builder
                .comment("Suppression effect intensity multiplier.\nRange: 0.0 ~ 1.0. Default: 1.0")
                .defineInRange("suppression_intensity", 1.0, 0.0, 1.0);

        RENDER_HIT_WHITENING = builder
                .comment("Show a white flash on parts when they are hit.\nDefault: true")
                .define("render_hit_whitening", true);

        RENDER_DESTROY_BLACKENING = builder
                .comment("Show a dark overlay on parts that have been destroyed.\nDefault: true")
                .define("render_destroy_blackening", true);

        RENDER_FORCE_TRANSLUCENT_PARTS = builder
                .comment("Whether to replace cutout part rendering with translucent entity rendering.\nOff by default because translucent rendering often causes incorrect z-sorting and object culling.")
                .define("render_force_translucent_parts", false);

        builder.pop();

        builder.push("ground_vehicle");

        GROUND_FULL_POWER_TIME = builder
                .comment("Time to reach full throttle after pressing the key (seconds).\nLower = faster response. Default: 1.2. Range: 0.05 ~ 99999.0")
                .defineInRange("full_power_time", 1.5, 0.05, 99999.0);

        GROUND_POWER_OFF_TIME = builder
                .comment("Time to release throttle to 0 after letting go of the key (seconds).\nLower = faster throttle cut. Default: 0.3. Range: 0.05 ~ 99999.0")
                .defineInRange("power_off_time", 2.0, 0.05, 99999.0);

        GROUND_FULL_BRAKE_TIME = builder
                .comment("Time to reach full brake after pressing the key (seconds).\nLower = faster braking response. Default: 0.3. Range: 0.05 ~ 99999.0")
                .defineInRange("full_brake_time", 0.3, 0.05, 99999.0);

        GROUND_BRAKE_OFF_TIME = builder
                .comment("Time to release brake to 0 after letting go of the key (seconds).\nLower = wheels resume rolling faster. Default: 0.2. Range: 0.05 ~ 99999.0")
                .defineInRange("brake_off_time", 0.1, 0.05, 99999.0);

        GROUND_FULL_STEERING_TIME = builder
                .comment("Time to reach full steering angle after pressing the key (seconds).\nLower = faster steering response. Default: 0.4. Range: 0.05 ~ 99999.0")
                .defineInRange("full_steering_time", 0.4, 0.05, 99999.0);

        GROUND_STEERING_OFF_TIME = builder
                .comment("Time to return steering to center after letting go of the key (seconds).\nLower = wheels straighten faster. Default: 0.4. Range: 0.05 ~ 99999.0")
                .defineInRange("steering_off_time", 0.2, 0.05, 99999.0);

        GROUND_AUTO_SWITCH_GEAR = builder
                .comment("Whether to automatically shift gears based on speed and engine RPM.\nFOLLOW_VEHICLE = use vehicle definition. ALWAYS_ENABLED = always auto. ALWAYS_DISABLED = manual only.")
                .defineEnum("auto_switch_gear", ControlPreference.FOLLOW_VEHICLE);

        GROUND_AUTO_HANDBRAKE = builder
                .comment("Whether to automatically apply handbrake when stopped and release when accelerating.\nFOLLOW_VEHICLE = use vehicle definition. ALWAYS_ENABLED = always auto. ALWAYS_DISABLED = manual only.")
                .defineEnum("auto_handbrake", ControlPreference.FOLLOW_VEHICLE);

        GROUND_DRIFT_ASSIST = builder
                .comment("Whether to automatically counter-steer during drifting to maintain control.\nFOLLOW_VEHICLE = use vehicle definition. ALWAYS_ENABLED = always on. ALWAYS_DISABLED = always off.")
                .defineEnum("drift_assist", ControlPreference.FOLLOW_VEHICLE);

        GROUND_POSE_PREFERENCE = builder
                .comment("Whether to automatically rotate camera to follow vehicle orientation.\nFOLLOW_VEHICLE = use vehicle definition. ALWAYS_ENABLED = always follow. ALWAYS_DISABLED = free camera.")
                .defineEnum("pose_preference", ControlPreference.FOLLOW_VEHICLE);

        GROUND_SPEED_TURNING_LIMIT = builder
                .comment("Limit steering angle at high speed to prevent loss of control or rollover.\nDefault: true")
                .define("speed_turning_limit", true);

        GROUND_SEPARATE_THROTTLE_BRAKE = builder
                .comment("Separate throttle and brake into independent channels.\nfalse = intent mode (W = move forward, S = move backward, vehicle auto-decides gas/brake).\ntrue = raw mode (W = gas pedal, S = brake pedal).")
                .define("separate_throttle_brake", false);

        builder.pop();

        builder.push("ship");

        SHIP_FULL_STEERING_TIME = builder
                .comment("Time to reach full steering after pressing the key (seconds).\nLower = faster steering. Default: 0.25. Range: 0.05 ~ 99999.0")
                .defineInRange("full_steering_time", 0.25, 0.05, 99999.0);

        builder.pop();

        builder.push("plane");

        PLANE_FULL_POWER_TIME = builder
                .comment("Time to reach full throttle after pressing the key (seconds).\nLower = faster response. Default: 2.5. Range: 0.05 ~ 99999.0")
                .defineInRange("full_power_time", 2.5, 0.05, 99999.0);

        PLANE_FULL_PITCH_TIME = builder
                .comment("Time to reach full pitch after pressing the key (seconds).\nLower = faster pitch response. Default: 0.25. Range: 0.05 ~ 99999.0")
                .defineInRange("full_pitch_time", 0.25, 0.05, 99999.0);

        PLANE_FULL_YAW_TIME = builder
                .comment("Time to reach full yaw after pressing the key (seconds).\nLower = faster yaw response. Default: 0.25. Range: 0.05 ~ 99999.0")
                .defineInRange("full_yaw_time", 0.25, 0.05, 99999.0);

        PLANE_FULL_ROLL_TIME = builder
                .comment("Time to reach full roll after pressing the key (seconds).\nLower = faster roll response. Default: 0.25. Range: 0.05 ~ 99999.0")
                .defineInRange("full_roll_time", 0.25, 0.05, 99999.0);

        builder.pop();

        CLIENT_SPEC = builder.build();
    }

    /**
     * 获取地面载具每tick动力变化的百分比步长（油门/前进建立）
     */
    public static int getGroundFullPowerStep() {
        return (int) (100 / (GROUND_FULL_POWER_TIME.get() * 20));
    }

    /**
     * 获取地面载具每tick动力归零的百分比步长（收油）
     */
    public static int getGroundPowerOffStep() {
        return (int) (100 / (GROUND_POWER_OFF_TIME.get() * 20));
    }

    /**
     * 获取地面载具每tick刹车建立的百分比步长
     */
    public static int getGroundFullBrakeStep() {
        return (int) (100 / (GROUND_FULL_BRAKE_TIME.get() * 20));
    }

    /**
     * 获取地面载具每tick刹车释放的百分比步长
     */
    public static int getGroundBrakeOffStep() {
        return (int) (100 / (GROUND_BRAKE_OFF_TIME.get() * 20));
    }

    /**
     * 获取地面载具每tick转向变化的百分比步长
     */
    public static int getGroundFullSteeringStep() {
        return (int) (100 / (GROUND_FULL_STEERING_TIME.get() * 20));
    }

    /**
     * 获取地面载具每tick转向回正的百分比步长
     */
    public static int getGroundSteeringOffStep() {
        return (int) (100 / (GROUND_STEERING_OFF_TIME.get() * 20));
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

    /**
     * 获取油门刹车通道是否分离（供客户端 RawInputHandler 使用）
     *
     * @return true=分离模式(W油门/S刹车), false=意图模式(W前进/S后退)
     */
    public static boolean isSeparateThrottleBrake() {
        return GROUND_SEPARATE_THROTTLE_BRAKE.get();
    }

    /**
     * 获取是否启用部件受击视觉效果
     *
     * @return true 表示启用部件受击视觉效果
     */
    public static boolean getRenderHitWhitening() {
        return RENDER_HIT_WHITENING.get();
    }

    /**
     * 获取是否启用部件损毁视觉效果
     *
     * @return true 表示启用部件损毁视觉效果
     */
    public static boolean getRenderDestroyBlackening() {
        return RENDER_DESTROY_BLACKENING.get();
    }

    /**
     * 获取是否启用部件半透明渲染
     *
     * @return true 表示将Cutout部件渲染替换为半透明实体渲染
     */
    public static boolean getRenderForceTranslucentParts() {
        return RENDER_FORCE_TRANSLUCENT_PARTS.get();
    }

    /**
     * 获取是否在启动后显示欢迎页面
     *
     * @return true 表示启动后进入标题画面时弹出欢迎页面
     */
    public static boolean isShowWelcomeScreen() {
        return SHOW_WELCOME_SCREEN.get();
    }

    /**
     * 设置是否在启动后显示欢迎页面（用于 "不再显示" 复选框）
     *
     * @param show 是否显示
     */
    public static void setShowWelcomeScreen(boolean show) {
        SHOW_WELCOME_SCREEN.set(show);
        CLIENT_SPEC.save();
    }

    // ---- 视觉特效配置 ----

    /**
     * 获取过载效果开关状态。
     *
     * @return true 表示启用过载效果
     */
    public static boolean isOverloadEnabled() {
        return OVERLOAD_ENABLED.get();
    }

    /**
     * 获取压制（灰度化）效果开关状态。
     *
     * @return true 表示启用压制效果
     */
    public static boolean isSuppressionEnabled() {
        return SUPPRESSION_ENABLED.get();
    }

    /**
     * 获取过载效果强度倍率。
     *
     * @return [0, 1] 范围内的倍率，1.0 为原始强度
     */
    public static float getOverloadIntensity() {
        return OVERLOAD_INTENSITY.get().floatValue();
    }

    /**
     * 获取压制效果强度倍率。
     *
     * @return [0, 1] 范围内的倍率，1.0 为原始强度
     */
    public static float getSuppressionIntensity() {
        return SUPPRESSION_INTENSITY.get().floatValue();
    }
}

