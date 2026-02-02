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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class EngineSubsystemStaticAttr extends AbstractSubsystemStaticAttr implements ICustomSoundSubsystemAttr {
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

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<EngineSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
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
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("engine_control", "move_control")).forGetter(EngineSubsystemStaticAttr::getThrottleInputKeys)
    ).apply(instance, EngineSubsystemStaticAttr::new));
    public static final int LOAD_STATE_COUNT = 4;
    public static final double RPM_INCREASE_RATIO = 1.4; // 40% 增加，即 1.4 倍

    public final ArrayList<ArrayList<WorkingState>> workingStates = new ArrayList<>();//工况-音效列表，外层转速，内层负载，对应音效文件名

    public static final WorkingState EMPTY_WORKING_STATE = new WorkingState(0.0f, 0.0f, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));


    public EngineSubsystemStaticAttr(
            float basicDurability,
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
            List<String> throttleInputKeys) {
        super(basicDurability);
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
    }

    public boolean shouldCreateSounds() {
        return true;
    }

    public void createSounds() {
        workingStates.clear();
        // 确定转速区间数量，按照 1.5 倍递增
        int rpmCount = getRpmStateIndex(getRedLineRpm() * 2);
        // 外层循环：转速区间
        for (int i = 0; i < rpmCount + 1; i++) {
            // 内层循环：负载区间
            ArrayList<WorkingState> loadWorkingStates = new ArrayList<>();
            for (int j = 0; j < LOAD_STATE_COUNT; j++) {
                // 创建工况
                float rpm = getIdleRpm() * (float) Math.pow(RPM_INCREASE_RATIO, i);
                float load = 0.25f * j;
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
                ResourceLocation sound = createStateSound(synthesizer, rpm, load);
                loadWorkingStates.add(new WorkingState(rpm, load, sound));
            }
            workingStates.add(loadWorkingStates);
        }
    }

    private ResourceLocation createStateSound(PistonEngineSoundSynthesizer synthesizer, float rpm, float load) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                MachineMax.MOD_ID,
                "subsystem/engine/" + this.hashCode() + "/" + rpm + "rpm_" + getLoadStateIndex(load));
//        MachineMax.LOGGER.debug("Creating engine sound: {}", id);
        SoundData ignition = SoundModule.getSound(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "ignite"));
        if (ignition != null) {
            synthesizer.updateEngineState(rpm, load);
            synthesizer.synthesizeEngineSound(3f).register(id);
            MachineMax.LOGGER.debug("Engine sound created: {}", id);
        } else {
            MachineMax.LOGGER.warn("Failed to synthesize engine sound because ignition sound is missing: {}", id);
        }
        return id;
    }

    public WorkingState getBestMatchWorkingState(double rpm, double load) {
        int rpmIndex = (int) Math.round(getRPMCoordinate(rpm));
        int loadIndex = (int) Math.round(getLoadCoordinate(load));
        WorkingState result = null;
        if (rpmIndex >= 0 && rpmIndex < workingStates.size()) {
            if (loadIndex >= 0 && loadIndex < workingStates.get(rpmIndex).size()) {
                result = workingStates.get(rpmIndex).get(loadIndex);
            }
        }
        return result;
    }

    public double distanceSqr(WorkingState state1, WorkingState state2) {
        double rpm1Coordinate = getRPMCoordinate(state1.rpm());
        double rpm2Coordinate = getRPMCoordinate(state2.rpm());
        double load1Coordinate = getLoadCoordinate(state1.load());
        double load2Coordinate = getLoadCoordinate(state2.load());
        return (rpm1Coordinate - rpm2Coordinate) * (rpm1Coordinate - rpm2Coordinate) + (load1Coordinate - load2Coordinate) * (load1Coordinate - load2Coordinate);
    }

    public double distance(WorkingState state1, WorkingState state2) {
        return Math.sqrt(distanceSqr(state1, state2));
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


    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.ENGINE;
    }

}
