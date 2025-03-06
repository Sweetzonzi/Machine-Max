package io.github.tt432.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

import java.util.List;

@Getter
public class GearboxSubsystemAttr extends AbstractSubsystemAttr {
    public final List<Float> ratios;//减速比，升序排列 TODO:写入后进行排序
    public final float switchTime;//换挡时间(秒)
    public final int gearN;//空挡位索引，无控制输入时默认停留于此挡位
    public final String powerType;//传递的功率类型，默认扭矩
    public final List<String> ratioControlSignalKeys;//换挡控制信号，必须为一个int值，指定选用的减速比
    public final String rpmInputKey;//动力输入端名
    public final String rpmFeedbackOutputKey;//动力输入端速度反馈信号名
    public final String powerOutputTarget;//动力输出端名
    public final String rpmOutputKey;//动力输出端信号名

    public GearboxSubsystemAttr(
            List<Float> ratios,
            float switchTime,
            int gearN,
            String powerType,
            List<String> ratioControlSignalKeys,
            String rpmInputKey,
            String rpmFeedbackOutputKey,
            String powerOutputTarget,
            String rpmOutputKey) {
        this.ratios = ratios;
        this.switchTime = switchTime;
        this.gearN = gearN;
        this.powerType = powerType;
        this.ratioControlSignalKeys = ratioControlSignalKeys;
        this.rpmInputKey = rpmInputKey;
        this.rpmFeedbackOutputKey = rpmFeedbackOutputKey;
        this.powerOutputTarget = powerOutputTarget;
        this.rpmOutputKey = rpmOutputKey;
    }

    public static final MapCodec<GearboxSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.list(Codec.FLOAT).fieldOf("ratios").forGetter(GearboxSubsystemAttr::getRatios),
            Codec.FLOAT.fieldOf("switch_time").forGetter(GearboxSubsystemAttr::getSwitchTime),
            Codec.INT.fieldOf("n_gear").forGetter(GearboxSubsystemAttr::getGearN),
            Codec.STRING.optionalFieldOf("power_type", "torque").forGetter(GearboxSubsystemAttr::getPowerType),
            Codec.STRING.listOf().fieldOf("ratio_ctrl_inputs").forGetter(GearboxSubsystemAttr::getRatioControlSignalKeys),
            Codec.STRING.fieldOf("rpm_input").forGetter(GearboxSubsystemAttr::getRpmInputKey),
            Codec.STRING.fieldOf("rpm_feedback_output").forGetter(GearboxSubsystemAttr::getRpmFeedbackOutputKey),
            Codec.STRING.fieldOf("power_output_target").forGetter(GearboxSubsystemAttr::getPowerOutputTarget),
            Codec.STRING.fieldOf("rpm_output").forGetter(GearboxSubsystemAttr::getRpmOutputKey)
    ).apply(instance, GearboxSubsystemAttr::new));

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.GEARBOX;
    }
}
