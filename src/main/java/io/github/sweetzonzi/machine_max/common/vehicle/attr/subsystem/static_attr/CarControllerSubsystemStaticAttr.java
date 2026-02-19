package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Getter
public class CarControllerSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final Vec3 steeringCenter;
    public final TreeMap<Float, Float> steeringRadiusMap;
    public final boolean manualGearShift;
    public final boolean autoHandBrake;
    public final List<String> controlInputKeys;
    public final boolean absEnabled;
    public final float absTargetSlipRatio;
    public final float absWheelRadius;

    public static final Codec<TreeMap<Float, Float>> STEERING_RADIUS_CODEC =
            Codec.either(Codec.FLOAT, Codec.unboundedMap(Codec.STRING, Codec.FLOAT))
                    .xmap(
                            either -> either.map(
                                    // 单值 -> TreeMap 包含 {0.0 -> value}
                                    value -> {
                                        TreeMap<Float, Float> map = new TreeMap<>();
                                        map.put(0.0f, value);
                                        return map;
                                    },
                                    // Map<String, Float> -> TreeMap<Float, Float>（按键转换为Float并排序）
                                    stringMap -> {
                                        TreeMap<Float, Float> floatMap = new TreeMap<>();
                                        for (Map.Entry<String, Float> entry : stringMap.entrySet()) {
                                            try {
                                                float key = Float.parseFloat(entry.getKey());
                                                floatMap.put(key, entry.getValue());
                                            } catch (NumberFormatException ignore) {
                                                // 忽略无法解析为浮点数的键
                                            }
                                        }
                                        return floatMap;
                                    }
                            ),
                            treeMap -> {
                                // 编码时：若只有键 0.0，则压缩为单值；否则编码为 Map<String, Float>
                                if (treeMap.size() == 1 && treeMap.containsKey(0.0f)) {
                                    return Either.left(treeMap.get(0.0f));
                                } else {
                                    Map<String, Float> stringMap = new java.util.HashMap<>();
                                    for (Map.Entry<Float, Float> entry : treeMap.entrySet()) {
                                        stringMap.put(entry.getKey().toString(), entry.getValue());
                                    }
                                    return Either.right(stringMap);
                                }
                            }
                    );

    public static final MapCodec<CarControllerSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Vec3.CODEC.optionalFieldOf("steering_center", Vec3.ZERO).forGetter(CarControllerSubsystemStaticAttr::getSteeringCenter),
            STEERING_RADIUS_CODEC.optionalFieldOf("steering_radius", createDefaultSteeringRadiusMap()).forGetter(CarControllerSubsystemStaticAttr::getSteeringRadiusMap),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(CarControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(CarControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("move_control")).forGetter(CarControllerSubsystemStaticAttr::getControlInputKeys),
            Codec.BOOL.optionalFieldOf("abs_enabled", true).forGetter(CarControllerSubsystemStaticAttr::isAbsEnabled),
            Codec.FLOAT.optionalFieldOf("abs_target_slip_ratio", 0.15f).forGetter(CarControllerSubsystemStaticAttr::getAbsTargetSlipRatio),
            Codec.FLOAT.optionalFieldOf("abs_wheel_radius", 0.5f).forGetter(CarControllerSubsystemStaticAttr::getAbsWheelRadius)
    ).apply(instance, CarControllerSubsystemStaticAttr::new));

    public static TreeMap<Float, Float> createDefaultSteeringRadiusMap() {
        TreeMap<Float, Float> map = new TreeMap<>();
        map.put(0.0f, 5.0f);
        return map;
    }

    public CarControllerSubsystemStaticAttr(
            float basicDurability,
            Vec3 steeringCenter,
            TreeMap<Float, Float> steeringRadiusMap,
            boolean manualGearShift,
            boolean autoHandBrake,
            List<String> controlInputKeys,
            boolean absEnabled,
            float absTargetSlipRatio,
            float absWheelRadius) {
        super(basicDurability);
        this.steeringCenter = steeringCenter;
        this.steeringRadiusMap = steeringRadiusMap;
        this.manualGearShift = manualGearShift;
        this.autoHandBrake = autoHandBrake;
        this.controlInputKeys = controlInputKeys;
        this.absEnabled = absEnabled;
        this.absTargetSlipRatio = absTargetSlipRatio;
        this.absWheelRadius = absWheelRadius;
    }

    public float getSteeringRadiusAtSpeed(float currentMps) {
        if (steeringRadiusMap.isEmpty()) {
            return 5.0f;
        }
        float currentKmh = currentMps * 3.6f; // 转为 km/h
        Map.Entry<Float, Float> floor = steeringRadiusMap.floorEntry(currentKmh);
        Map.Entry<Float, Float> ceiling = steeringRadiusMap.ceilingEntry(currentKmh);
        
        if (floor == null && ceiling == null) return 5.0f;
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
        return SubsystemTypes.CAR_CTRL;
    }

}
