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
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class MotorSubsystemStaticAttr extends BasicSubsystemStaticAttr implements ICustomSoundSubsystemAttr {
    public final String particleLocator;
    public final float maxPower;
    public final float maxTorque;
    public final float redLineRpm;
    public final double inertia;//电机系统转动惯量(kg·m²)
    public final List<Double> dampingFactors;//电机系统各阶阻力系数，分别为常数项，一次项，二次项，…递增(N·m/(rad/s)^n)
    public final float generatorEfficiency; // 发电效率（0-1）
    public final List<String> throttleInputKeys;//优先级从高至低
    public final MotorSoundAttr sounds;

    public record MotorSoundAttr(BasicSoundAttr basicSounds, Map<String, SoundEvent> workingSounds) {
        public static final MotorSoundAttr DEFAULT = new MotorSoundAttr(BasicSoundAttr.DEFAULT, Map.of());

        public static final Codec<MotorSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(MotorSoundAttr::basicSounds),
                Codec.unboundedMap(Codec.STRING, SoundEvent.DIRECT_CODEC)
                        .optionalFieldOf("working_sounds", Map.of())
                        .forGetter(MotorSoundAttr::workingSounds)
        ).apply(instance, MotorSoundAttr::new));
    }

    public static final MapCodec<MotorSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.optionalFieldOf("particle_locator", "").forGetter(MotorSubsystemStaticAttr::getParticleLocator),
            Codec.FLOAT.fieldOf("max_power").forGetter(MotorSubsystemStaticAttr::getMaxPower),
            Codec.FLOAT.optionalFieldOf("max_torque", 100f).forGetter(MotorSubsystemStaticAttr::getMaxTorque),
            Codec.FLOAT.optionalFieldOf("red_line_rpm", 10000f).forGetter(MotorSubsystemStaticAttr::getRedLineRpm),
            Codec.DOUBLE.optionalFieldOf("inertia", 10.0).forGetter(MotorSubsystemStaticAttr::getInertia),
            Codec.DOUBLE.listOf().optionalFieldOf("damping_factors", List.of(10.0, 0.1, 0.00005)).forGetter(MotorSubsystemStaticAttr::getDampingFactors),
            Codec.FLOAT.optionalFieldOf("generator_efficiency", 0.85f).forGetter(MotorSubsystemStaticAttr::getGeneratorEfficiency),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("motor_control", "move_control")).forGetter(MotorSubsystemStaticAttr::getThrottleInputKeys),
            MotorSoundAttr.CODEC.optionalFieldOf("sounds", MotorSoundAttr.DEFAULT).forGetter(MotorSubsystemStaticAttr::getSounds)
    ).apply(instance, MotorSubsystemStaticAttr::new));
    public static final float baseRPM = 400.0f;
    public static final int LOAD_STATE_COUNT = 4;
    public static final double RPM_INCREASE_RATIO = 1.4; // 40% 增加，即 1.4 倍

    public final ArrayList<ArrayList<WorkingState>> workingStates = new ArrayList<>();//工况-音效列表，外层转速，内层负载，对应音效文件名

    public static final WorkingState EMPTY_WORKING_STATE = new WorkingState(
            0.0f,
            0.0f,
            SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty_sound"), 0f)
    );
    public record RpmWorkingStates(WorkingState left, WorkingState center, WorkingState right) {}

    public MotorSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            String particleLocator,
            float maxPower,
            float maxTorque,
            float redLineRpm,
            double inertia,
            List<Double> dampingFactors,
            float generatorEfficiency,
            List<String> throttleInputKeys,
            MotorSoundAttr sounds
    ) {
        super(basicAttr, sounds.basicSounds());
        this.particleLocator = particleLocator;
        this.maxPower = maxPower;
        this.maxTorque = maxTorque;
        this.redLineRpm = redLineRpm;
        this.inertia = inertia;
        this.dampingFactors = dampingFactors;
        this.generatorEfficiency = generatorEfficiency;
        this.throttleInputKeys = throttleInputKeys;
        this.sounds = sounds;
    }

    @Override
    public void createSounds() {
        workingStates.clear();
        if (!sounds.workingSounds().isEmpty()) {
            sounds.workingSounds().entrySet().stream()
                    .sorted((a, b) -> Float.compare(parseRpm(a.getKey()), parseRpm(b.getKey())))
                    .forEach(entry -> {
                        float rpm = parseRpm(entry.getKey());
                        if (!Float.isFinite(rpm)) {
                            MachineMax.LOGGER.warn("Invalid motor working_sounds rpm key '{}', skipped.", entry.getKey());
                            return;
                        }
                        ArrayList<WorkingState> loadWorkingStates = new ArrayList<>();
                        loadWorkingStates.add(new WorkingState(rpm, 1.0f, entry.getValue()));
                        workingStates.add(loadWorkingStates);
                    });
            return;
        }
        //确定转速区间数量
        int rpmCount = getRpmStateIndex(redLineRpm * 2);
        //外层循环：转速区间
        for (int i = 1; i < rpmCount + 1; i++) {
            ArrayList<WorkingState> loadWorkingStates = new ArrayList<>();
            float rpm = baseRPM * (float) Math.pow(RPM_INCREASE_RATIO, i);
            float load = 1.0f;
            SoundEvent sound = createStateSound(rpm);
            loadWorkingStates.add(new WorkingState(rpm, load, sound));
            workingStates.add(loadWorkingStates);
        }
    }

    private SoundEvent createStateSound(float rpm){
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                MachineMax.MOD_ID,
                "subsystem/motor/" + this.hashCode() + "/" + rpm + "rpm_" + getLoadStateIndex(1.0f));
//        MachineMax.LOGGER.debug("Creating motor sound: {}", id);
        MotorSoundSynthesizer.synthesizeBrushlessMotor(3f, rpm, 1.0f,
                new MotorSoundSynthesizer.MotorConfig(6, 8000, this.redLineRpm, 1200)).register(id);
        MachineMax.LOGGER.debug("Motor sound created: {}", id);
        return SoundEvent.createFixedRangeEvent(id, 64f);
    }

    public WorkingState getBestMatchWorkingState(double rpm, double load) {
        int rpmIndex = (int) Math.round(getRPMCoordinate(rpm));
        WorkingState result = MotorSubsystemStaticAttr.EMPTY_WORKING_STATE;
        if(rpmIndex >=0 && rpmIndex < workingStates.size()){
            if (!workingStates.get(rpmIndex).isEmpty()){
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
        if (Math.abs(rpm) <= baseRPM) return 0;
        // 使用频率增加量为底的对数计算转速坐标
        return Math.log(Math.abs(rpm) / baseRPM) / Math.log(RPM_INCREASE_RATIO);
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
        return SubsystemTypes.MOTOR;
    }

}
