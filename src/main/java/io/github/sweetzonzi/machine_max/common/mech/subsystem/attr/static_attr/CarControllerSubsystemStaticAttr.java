package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Getter
public class CarControllerSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    
    public record HandBrakeSoundAttr(
            BasicSoundAttr basicSounds,
            SoundEvent handBrakeOn,
            SoundEvent handBrakeOff
    ) {
        public static final HandBrakeSoundAttr DEFAULT = new HandBrakeSoundAttr(
                BasicSoundAttr.DEFAULT,
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.car_controller.handbrake_on"), 16),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.car_controller.handbrake_off"), 16)
        );
        
        public static final Codec<HandBrakeSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(HandBrakeSoundAttr::basicSounds),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("handbrake_on", DEFAULT.handBrakeOn).forGetter(HandBrakeSoundAttr::handBrakeOn),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("handbrake_off", DEFAULT.handBrakeOff).forGetter(HandBrakeSoundAttr::handBrakeOff)
        ).apply(instance, HandBrakeSoundAttr::new));
    }
    public final float minSteeringRadius;
    public final TreeMap<Float, Float> lateralAccelerationMap;
    public final TreeMap<Float, Float> maxDriftAngularVelocityMap;
    public final boolean manualGearShift;
    public final boolean autoHandBrake;
    public final boolean driftAssist;
    public final List<String> controlInputKeys;
    public final HandBrakeSoundAttr sounds;//音效配置

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
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.optionalFieldOf("min_steering_radius", 5.0f).forGetter(CarControllerSubsystemStaticAttr::getMinSteeringRadius),
            STEERING_RADIUS_CODEC.optionalFieldOf("lateral_acceleration", createDefaultLateralAccelerationMap()).forGetter(CarControllerSubsystemStaticAttr::getLateralAccelerationMap),
            STEERING_RADIUS_CODEC.optionalFieldOf("max_drift_angular_velocity", createDefaultMaxDriftAngularVelocityMap()).forGetter(CarControllerSubsystemStaticAttr::getMaxDriftAngularVelocityMap),
            Codec.BOOL.optionalFieldOf("manual_gear_shift", false).forGetter(CarControllerSubsystemStaticAttr::isManualGearShift),
            Codec.BOOL.optionalFieldOf("auto_hand_brake", true).forGetter(CarControllerSubsystemStaticAttr::isAutoHandBrake),
            Codec.BOOL.optionalFieldOf("drift_assist", true).forGetter(CarControllerSubsystemStaticAttr::isDriftAssist),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("move_input_p0", "move_input_p1", "move_input_p2", "move_input_p3", "move_input")).forGetter(CarControllerSubsystemStaticAttr::getControlInputKeys),
            HandBrakeSoundAttr.CODEC.optionalFieldOf("sounds", HandBrakeSoundAttr.DEFAULT).forGetter(CarControllerSubsystemStaticAttr::getSounds)
    ).apply(instance, CarControllerSubsystemStaticAttr::new));

    public static TreeMap<Float, Float> createDefaultLateralAccelerationMap() {
        TreeMap<Float, Float> map = new TreeMap<>();
        map.put(0.0f, 8.0f); // 默认侧向加速度 8 m/s²
        return map;
    }

    public static TreeMap<Float, Float> createDefaultMaxDriftAngularVelocityMap() {
        TreeMap<Float, Float> map = new TreeMap<>();
        map.put(0.0f, 57.3f); // 默认最大漂移角速度 57.3 °/s
        return map;
    }

    public CarControllerSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            float minSteeringRadius,
            TreeMap<Float, Float> lateralAccelerationMap,
            TreeMap<Float, Float> maxDriftAngularVelocityMap,
            boolean manualGearShift,
            boolean autoHandBrake,
            boolean driftAssist,
            List<String> controlInputKeys,
            HandBrakeSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.minSteeringRadius = minSteeringRadius;
        this.lateralAccelerationMap = lateralAccelerationMap;
        this.maxDriftAngularVelocityMap = maxDriftAngularVelocityMap;
        this.manualGearShift = manualGearShift;
        this.autoHandBrake = autoHandBrake;
        this.driftAssist = driftAssist;
        this.controlInputKeys = controlInputKeys;
        this.sounds = sounds;
    }

    public float getSteeringRadiusAtSpeed(float currentMps) {
        // 根据侧向加速度计算转向半径: radius = v² / a
        float lateralAcceleration = getLateralAccelerationAtSpeed(currentMps);
        if (lateralAcceleration <= 0) {
            return minSteeringRadius;
        }
        float radius = (currentMps * currentMps) / lateralAcceleration;
        return Math.max(radius, minSteeringRadius);
    }

    public float getLateralAccelerationAtSpeed(float currentMps) {
        if (lateralAccelerationMap.isEmpty()) {
            return 8.0f;
        }
        float currentKmh = currentMps * 3.6f; // 转为 km/h
        Map.Entry<Float, Float> floor = lateralAccelerationMap.floorEntry(currentKmh);
        Map.Entry<Float, Float> ceiling = lateralAccelerationMap.ceilingEntry(currentKmh);
        
        if (floor == null && ceiling == null) return 8.0f;
        if (floor == null) return ceiling.getValue();
        if (ceiling == null) return floor.getValue();
        if (floor.getKey().equals(ceiling.getKey())) return floor.getValue();
        
        float ratio = (currentKmh - floor.getKey()) / (ceiling.getKey() - floor.getKey());
        return floor.getValue() + (ceiling.getValue() - floor.getValue()) * ratio;
    }

    public float getMaxDriftAngularVelocityAtSpeed(float currentMps) {
        if (maxDriftAngularVelocityMap.isEmpty()) {
            return 2.0f;
        }
        float currentKmh = currentMps * 3.6f; // 转为 km/h
        Map.Entry<Float, Float> floor = maxDriftAngularVelocityMap.floorEntry(currentKmh);
        Map.Entry<Float, Float> ceiling = maxDriftAngularVelocityMap.ceilingEntry(currentKmh);
        
        if (floor == null && ceiling == null) return 2.0f;
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

    public SoundEvent getHandBrakeOnSound() {
        return sounds.handBrakeOn;
    }

    public SoundEvent getHandBrakeOffSound() {
        return sounds.handBrakeOff;
    }

}
