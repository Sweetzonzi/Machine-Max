package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 履带控制器静态属性。
 *
 * <p>单流差速只使用横摆角速度上限、最小转弯半径、侧向加速度和 PD 参数；
 * 转向电机/转向传动的双流参数先保留字段，单流阶段不读取。</p>
 */
@Getter
public class TrackControllerSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    public final List<String> controlInputKeys;
    public final TreeMap<Float, Float> maxYawRateMap;
    public final float minTurningRadius;
    public final float maxLateralAcceleration;
    public final float yawP;
    public final float yawD;
    public final float steeringSmoothing;
    public final float brakeSmoothing;
    public final boolean autoHandBrake;
    public final boolean manualGearShift;
    public final boolean separateThrottleBrake;
    public final float pivotYawRate;
    public final float pivotSpeedThreshold;

    private static final List<String> DEFAULT_CONTROL_INPUTS = List.of(
            "move_input_p0", "regular_input_p0",
            "move_input_p1", "regular_input_p1",
            "move_input_p2", "regular_input_p2",
            "move_input_p3", "regular_input_p3",
            "move_input", "regular_input"
    );

    public static final Codec<TreeMap<Float, Float>> YAW_RATE_CODEC =
            Codec.either(Codec.FLOAT, Codec.unboundedMap(Codec.STRING, Codec.FLOAT))
                    .xmap(
                            either -> either.map(
                                    value -> {
                                        TreeMap<Float, Float> map = new TreeMap<>();
                                        map.put(0.0f, value);
                                        return map;
                                    },
                                    stringMap -> {
                                        TreeMap<Float, Float> floatMap = new TreeMap<>();
                                        for (Map.Entry<String, Float> entry : stringMap.entrySet()) {
                                            try {
                                                floatMap.put(Float.parseFloat(entry.getKey()), entry.getValue());
                                            } catch (NumberFormatException ignore) {
                                                // 忽略无法解析为浮点数的键
                                            }
                                        }
                                        return floatMap;
                                    }
                            ),
                            treeMap -> {
                                if (treeMap.size() == 1 && treeMap.containsKey(0.0f)) {
                                    return Either.left(treeMap.get(0.0f));
                                }
                                Map<String, Float> stringMap = new java.util.HashMap<>();
                                for (Map.Entry<Float, Float> entry : treeMap.entrySet()) {
                                    stringMap.put(entry.getKey().toString(), entry.getValue());
                                }
                                return Either.right(stringMap);
                            }
                    );

    public static final MapCodec<TrackControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                    .forGetter(BasicSubsystemStaticAttr::getSoundAttr),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", DEFAULT_CONTROL_INPUTS)
                    .forGetter(TrackControllerSubsystemStaticAttr::getControlInputKeys),
            YAW_RATE_CODEC.optionalFieldOf("max_yaw_rate", createDefaultYawRateMap())
                    .forGetter(TrackControllerSubsystemStaticAttr::getMaxYawRateMap),
            Codec.FLOAT.optionalFieldOf("min_turning_radius", 4.0f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getMinTurningRadius),
            Codec.FLOAT.optionalFieldOf("max_lateral_acceleration", 6.0f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getMaxLateralAcceleration),
            Codec.FLOAT.optionalFieldOf("yaw_p", 1.0f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getYawP),
            Codec.FLOAT.optionalFieldOf("yaw_d", 0.05f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getYawD),
            Codec.FLOAT.optionalFieldOf("steering_smoothing", 0.1f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getSteeringSmoothing),
            Codec.FLOAT.optionalFieldOf("brake_smoothing", 0.2f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getBrakeSmoothing),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true)
                    .forGetter(TrackControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false)
                    .forGetter(TrackControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("separate_throttle_brake", false)
                    .forGetter(TrackControllerSubsystemStaticAttr::isSeparateThrottleBrake),
            Codec.FLOAT.optionalFieldOf("pivot_yaw_rate", 45.0f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getPivotYawRate),
            Codec.FLOAT.optionalFieldOf("pivot_speed_threshold", 0.5f)
                    .forGetter(TrackControllerSubsystemStaticAttr::getPivotSpeedThreshold)
    ).apply(instance, TrackControllerSubsystemStaticAttr::new));

    public TrackControllerSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            BasicSubsystemStaticAttr.BasicSoundAttr soundAttr,
            List<String> controlInputKeys,
            TreeMap<Float, Float> maxYawRateMap,
            float minTurningRadius,
            float maxLateralAcceleration,
            float yawP,
            float yawD,
            float steeringSmoothing,
            float brakeSmoothing,
            boolean autoHandBrake,
            boolean manualGearShift,
            boolean separateThrottleBrake,
            float pivotYawRate,
            float pivotSpeedThreshold) {
        super(basicAttr, soundAttr);
        this.controlInputKeys = controlInputKeys;
        this.maxYawRateMap = maxYawRateMap;
        this.minTurningRadius = minTurningRadius;
        this.maxLateralAcceleration = maxLateralAcceleration;
        this.yawP = yawP;
        this.yawD = yawD;
        this.steeringSmoothing = steeringSmoothing;
        this.brakeSmoothing = brakeSmoothing;
        this.autoHandBrake = autoHandBrake;
        this.manualGearShift = manualGearShift;
        this.separateThrottleBrake = separateThrottleBrake;
        this.pivotYawRate = pivotYawRate;
        this.pivotSpeedThreshold = pivotSpeedThreshold;
    }

    public static TreeMap<Float, Float> createDefaultYawRateMap() {
        TreeMap<Float, Float> map = new TreeMap<>();
        map.put(0.0f, 0.0f);
        map.put(10.0f, 30.0f);
        map.put(30.0f, 18.0f);
        map.put(50.0f, 10.0f);
        return map;
    }

    /**
     * 按车速插值横摆角速度上限，返回角度制（°/s）。
     */
    public float getMaxYawRateAtSpeed(float currentMps) {
        if (maxYawRateMap.isEmpty()) {
            return 0f;
        }
        float currentKmh = Math.abs(currentMps) * 3.6f;
        Map.Entry<Float, Float> floor = maxYawRateMap.floorEntry(currentKmh);
        Map.Entry<Float, Float> ceiling = maxYawRateMap.ceilingEntry(currentKmh);
        if (floor == null && ceiling == null) return 0f;
        if (floor == null) return ceiling.getValue();
        if (ceiling == null) return floor.getValue();
        if (floor.getKey().equals(ceiling.getKey())) return floor.getValue();
        float ratio = (currentKmh - floor.getKey()) / (ceiling.getKey() - floor.getKey());
        return floor.getValue() + (ceiling.getValue() - floor.getValue()) * ratio;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TRACK_CTRL;
    }
}
