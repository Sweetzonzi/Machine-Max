package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.util.sound.MotorSoundSynthesizer;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.WorkingState;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class EngineSubsystemStaticAttr extends AbstractSubsystemStaticAttr {
    public final String particleLocator;
    public final float maxPower;
    public final float baseRpm;
    public final float maxTorqueRpm;
    public final float maxRpm;
    public final double inertia;//发动机系统转动惯量(kg·m²)
    public final List<Double> dampingFactors;//发动机系统各阶阻力系数，分别为一次项，二次项，…递增
    public final List<String> throttleInputKeys;//优先级从高至低

    public static final Codec<Map<String, List<String>>> RPM_OUTPUT_TARGETS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf());

    public static final MapCodec<EngineSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(AbstractSubsystemStaticAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("particle_locator", "").forGetter(EngineSubsystemStaticAttr::getParticleLocator),
            Codec.FLOAT.fieldOf("max_power").forGetter(EngineSubsystemStaticAttr::getMaxPower),
            Codec.FLOAT.optionalFieldOf("base_rpm", 500f).forGetter(EngineSubsystemStaticAttr::getBaseRpm),
            Codec.FLOAT.optionalFieldOf("max_torque_rpm", 5500f).forGetter(EngineSubsystemStaticAttr::getMaxTorqueRpm),
            Codec.FLOAT.optionalFieldOf("max_rpm", 7500f).forGetter(EngineSubsystemStaticAttr::getMaxRpm),
            Codec.DOUBLE.optionalFieldOf("inertia", 500.0).forGetter(EngineSubsystemStaticAttr::getInertia),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(0.005, 0.00003)).forGetter(EngineSubsystemStaticAttr::getDampingFactors),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("engine_control", "move_control")).forGetter(EngineSubsystemStaticAttr::getThrottleInputKeys)
    ).apply(instance, EngineSubsystemStaticAttr::new));
    public static final int LOAD_STATE_COUNT = 5;

    public final ArrayList<ArrayList<WorkingState>> workingStates = new ArrayList<>();//工况-音效列表，外层转速，内层负载，对应音效文件名

    public static final WorkingState EMPTY_WORKING_STATE = new WorkingState(0.0f, 0.0f, ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty"));


    public EngineSubsystemStaticAttr(
            float basicDurability, 
            String particleLocator,
            float maxPower,
            float baseRpm,
            float maxTorqueRpm,
            float maxRpm,
            double inertia,
            List<Double> dampingFactors,
            List<String> throttleInputKeys) {
        super(basicDurability);
        this.particleLocator = particleLocator;
        this.maxPower = maxPower;
        this.baseRpm = baseRpm;
        this.maxTorqueRpm = maxTorqueRpm;
        this.maxRpm = maxRpm;
        this.inertia = inertia;
        this.dampingFactors = dampingFactors;
        this.throttleInputKeys = throttleInputKeys;
        createWorkingStates();
    }

    private void createWorkingStates() {
        workingStates.clear();
        //确定转速区间数量
        int rpmCount = getRpmStateIndex(getMaxRpm() * 1.5);
        //外层循环：转速区间
        for (int i = 1; i < rpmCount + 1; i++) {
            //内层循环：负载区间
            ArrayList<WorkingState> loadWorkingStates = new ArrayList<>();
            for (int j = 0; j < LOAD_STATE_COUNT; j++) {
                //创建工况
                float rpm = 2 * getBaseRpm() * (float) Math.pow(2, i);
                float load = 0.25f * j;
                ResourceLocation sound = createStateSound(rpm, load);
                loadWorkingStates.add(new WorkingState(rpm, load, sound));
            }
            workingStates.add(loadWorkingStates);
        }
    }

    private ResourceLocation createStateSound(float rpm, float load){
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                MachineMax.MOD_ID,
                "subsystem/engine/" + this.hashCode() + "/" + rpm + "rpm_" + getLoadStateIndex(load));
        MachineMax.LOGGER.debug("Creating engine sound: {}", id);
        MotorSoundSynthesizer.synthesizeBrushlessMotor(3f, rpm, load,
                new MotorSoundSynthesizer.MotorConfig(6, 8000, this.getMaxRpm(), 1200)).register(id);
        MachineMax.LOGGER.debug("Engine sound created: {}", id);
        return id;
    }

    public WorkingState getBestMatchWorkingState(double rpm, double load) {
        int rpmIndex = (int) getRPMCoordinate(rpm);
        int loadIndex = (int) getLoadCoordinate(load);
        WorkingState result = null;
        if(rpmIndex >=0 && rpmIndex < workingStates.size()){
            if (loadIndex >= 0 && loadIndex < workingStates.get(rpmIndex).size()){
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
        if (Math.abs(rpm) <= getBaseRpm()) return 0;
        return Math.log(Math.abs(rpm) / getBaseRpm()) / Math.log(2);
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
