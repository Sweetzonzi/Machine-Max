package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import cn.solarmoon.spark_core.pack.modules.SoundModule;
import cn.solarmoon.spark_core.sound.SoundData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.util.sound.PistonEngineSoundSynthesizer;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class EngineSubsystemStaticAttr extends BasicSubsystemStaticAttr implements ICustomSoundSubsystemAttr {
    public final float maxPower;
    public final float maxTorque;
    public final float idleRpm;
    public final float idleRpmTorqueRatio;
    public final float maxTorqueRpm;
    public final float redLineRpm;
    public final float redLineRpmTorqueRatio;
    public final double inertia; //发动机系统转动惯量(kg·m²)
    public final boolean fourStroke; //是否是四冲程，false则为二冲程
    public final int cylinderCount; // 气缸数
    public final List<Double> dampingFactors; //发动机各阶阻力系数，分别为常数项，一次项，二次项，…递增(N·m/(rad/s)^n)
    public final List<String> throttleInputKeys; //优先级从高至低
    public final EngineSoundAttr sounds;

    public record EngineSoundAttr(BasicSoundAttr basicSounds, Map<String, SoundEvent> workingSounds) {
        public static final EngineSoundAttr DEFAULT = new EngineSoundAttr(BasicSoundAttr.DEFAULT, Map.of());

        public static final Codec<EngineSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(EngineSoundAttr::basicSounds),
                Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                        .optionalFieldOf("working_sounds", Map.of())
                        .forGetter(EngineSoundAttr::workingSounds)
        ).apply(instance, EngineSoundAttr::new));
    }

    public static final MapCodec<EngineSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.fieldOf("max_power").forGetter(EngineSubsystemStaticAttr::getMaxPower),
            Codec.FLOAT.fieldOf("max_torque").forGetter(EngineSubsystemStaticAttr::getMaxTorqueRpm),
            Codec.FLOAT.optionalFieldOf("idle_rpm", 500f).forGetter(EngineSubsystemStaticAttr::getIdleRpm),
            Codec.FLOAT.optionalFieldOf("idle_rpm_torque_ratio", 0.333f).forGetter(EngineSubsystemStaticAttr::getIdleRpmTorqueRatio),
            Codec.FLOAT.optionalFieldOf("max_torque_rpm", 5200f).forGetter(EngineSubsystemStaticAttr::getMaxTorqueRpm),
            Codec.FLOAT.optionalFieldOf("red_line_rpm", 7500f).forGetter(EngineSubsystemStaticAttr::getRedLineRpm),
            Codec.FLOAT.optionalFieldOf("red_line_torque_ratio", 0.9f).forGetter(EngineSubsystemStaticAttr::getRedLineRpmTorqueRatio),
            Codec.DOUBLE.optionalFieldOf("inertia", 10.0).forGetter(EngineSubsystemStaticAttr::getInertia),
            Codec.BOOL.optionalFieldOf("four_stroke", true).forGetter(EngineSubsystemStaticAttr::isFourStroke),
            Codec.INT.optionalFieldOf("cylinder", 4).forGetter(EngineSubsystemStaticAttr::getCylinderCount),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(20.0, 0.1, 0.00005)).forGetter(EngineSubsystemStaticAttr::getDampingFactors),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("car_control")).forGetter(EngineSubsystemStaticAttr::getThrottleInputKeys),
            EngineSoundAttr.CODEC.optionalFieldOf("sounds", EngineSoundAttr.DEFAULT).forGetter(EngineSubsystemStaticAttr::getSounds)
    ).apply(instance, EngineSubsystemStaticAttr::new));
    public static final int LOAD_STATE_COUNT = 4;
    public static final double RPM_INCREASE_RATIO = 1.4; // 40% 增加，即 1.4 倍

    public final ArrayList<ArrayList<WorkingState>> workingStates = new ArrayList<>();//工况-音效列表，外层转速，内层负载，对应音效文件名

    public static final WorkingState EMPTY_WORKING_STATE = new WorkingState(
            0.0f,
            0.0f,
            SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty_sound"), 0f)
    );
    public record RpmWorkingStates(WorkingState left, WorkingState center, WorkingState right) {}


    public EngineSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            float maxPower,
            float maxTorque,
            float idleRpm,
            float idleRpmTorqueRatio,
            float maxTorqueRpm,
            float redLineRpm,
            float redLineRpmTorqueRatio,
            double inertia,
            boolean fourStroke,
            int cylinderCount,
            List<Double> dampingFactors,
            List<String> throttleInputKeys,
            EngineSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.maxPower = maxPower;
        this.maxTorque = maxTorque;
        this.idleRpm = idleRpm;
        this.idleRpmTorqueRatio = idleRpmTorqueRatio;
        this.maxTorqueRpm = maxTorqueRpm;
        this.redLineRpm = redLineRpm;
        this.redLineRpmTorqueRatio = redLineRpmTorqueRatio;
        this.inertia = inertia;
        this.fourStroke = fourStroke;
        this.cylinderCount = cylinderCount;
        this.dampingFactors = dampingFactors;
        this.throttleInputKeys = throttleInputKeys;
        this.sounds = sounds;
    }

    public void createSounds() {
        workingStates.clear();
        if (!sounds.workingSounds().isEmpty()) {
            sounds.workingSounds().entrySet().stream()
                    .sorted((a, b) -> Float.compare(parseRpm(a.getKey()), parseRpm(b.getKey())))
                    .forEach(entry -> {
                        float rpm = parseRpm(entry.getKey());
                        if (!Float.isFinite(rpm)) {
                            MachineMax.LOGGER.warn("Invalid engine working_sounds rpm key '{}', skipped.", entry.getKey());
                            return;
                        }
                        ArrayList<WorkingState> loadWorkingStates = new ArrayList<>();
                        loadWorkingStates.add(new WorkingState(rpm, 1.0f, entry.getValue()));
                        workingStates.add(loadWorkingStates);
                    });
            return;
        }
        // 确定转速区间数量，按照 1.4 倍递增
        int rpmCount = getRpmStateIndex(getRedLineRpm() * 2);
        // 外层循环：转速区间
        for (int i = 0; i < rpmCount + 1; i++) {
            ArrayList<WorkingState> loadWorkingStates = new ArrayList<>();
            float rpm = getIdleRpm() * (float) Math.pow(RPM_INCREASE_RATIO, i);
            float load = 1.0f;
            PistonEngineSoundSynthesizer synthesizer = new PistonEngineSoundSynthesizer();
            List<Double> firingAngles = new ArrayList<>(cylinderCount);
            List<Double> exhaustLengths = new ArrayList<>(cylinderCount);
            for (int k = 0; k < cylinderCount; k++) {
                firingAngles.add((fourStroke ? 720.0 : 360.0) / cylinderCount * k);
                exhaustLengths.add(0.6); // 固定排气歧管长度0.6m
            }
            var param = new PistonEngineSoundSynthesizer.EngineParams(
                    cylinderCount, fourStroke,
                    500.0,
                    redLineRpm, idleRpm,
                    firingAngles, exhaustLengths
            );
            synthesizer.setEngineParams(param);
            SoundEvent sound = createStateSound(synthesizer, rpm);
            loadWorkingStates.add(new WorkingState(rpm, load, sound));
            workingStates.add(loadWorkingStates);
        }
    }

    private SoundEvent createStateSound(PistonEngineSoundSynthesizer synthesizer, float rpm) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                MachineMax.MOD_ID,
                "subsystem/engine/" + this.hashCode() + "/" + rpm + "rpm_" + getLoadStateIndex(1.0f));
//        MachineMax.LOGGER.debug("Creating engine sound: {}", id);
        SoundData ignition = SoundModule.getSound(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "ignite"));
        if (ignition != null) {
            synthesizer.updateEngineState(rpm, 1.0f);
            synthesizer.synthesizeEngineSound(3f).register(id);
            MachineMax.LOGGER.debug("Engine sound created: {}", id);
        } else {
            MachineMax.LOGGER.warn("Failed to synthesize engine sound because ignition sound is missing: {}", id);
        }
        return SoundEvent.createFixedRangeEvent(id, 64f);
    }

    public WorkingState getBestMatchWorkingState(double rpm, double load) {
        int rpmIndex = (int) Math.round(getRPMCoordinate(rpm));
        WorkingState result = null;
        if (rpmIndex >= 0 && rpmIndex < workingStates.size()) {
            if (!workingStates.get(rpmIndex).isEmpty()) {
                result = workingStates.get(rpmIndex).getFirst();
            }
        }
        return result;
    }

    public RpmWorkingStates getAdjacentWorkingStates(double rpm) {
        if (workingStates.isEmpty()) {
            return new RpmWorkingStates(EMPTY_WORKING_STATE, EMPTY_WORKING_STATE, EMPTY_WORKING_STATE);
        }
        int centerIndex = Math.clamp((int) Math.round(getRPMCoordinate(rpm)), 0, workingStates.size() - 1);
        int leftIndex = centerIndex - 1;
        int rightIndex = centerIndex + 1;
        WorkingState left = leftIndex >= 0 ? getRpmOnlyState(leftIndex) : EMPTY_WORKING_STATE;
        WorkingState center = getRpmOnlyState(centerIndex);
        WorkingState right = rightIndex < workingStates.size() ? getRpmOnlyState(rightIndex) : EMPTY_WORKING_STATE;
        return new RpmWorkingStates(left, center, right);
    }

    private WorkingState getRpmOnlyState(int rpmIndex) {
        if (rpmIndex < 0 || rpmIndex >= workingStates.size()) return EMPTY_WORKING_STATE;
        if (workingStates.get(rpmIndex).isEmpty()) return EMPTY_WORKING_STATE;
        return workingStates.get(rpmIndex).getFirst();
    }

    public int getRpmStateIndex(double rpm) {
        return (int) Math.floor(getRPMCoordinate(rpm));
    }

    public double getRPMCoordinate(double rpm) {
        if (Math.abs(rpm) <= getIdleRpm()) return 0;
        // 使用频率增加量为底的对数计算转速坐标
        return Math.log(Math.abs(rpm) / getIdleRpm()) / Math.log(RPM_INCREASE_RATIO);
    }

    public int getLoadStateIndex(double load) {
        return Math.clamp((int) Math.floor(getLoadCoordinate(load)), 0, LOAD_STATE_COUNT - 1);
    }

    public double getLoadCoordinate(double load) {
        return load * (LOAD_STATE_COUNT - 1);
    }

    private static float parseRpm(String rpm) {
        try {
            return Float.parseFloat(rpm);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }


    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.ENGINE;
    }

}
