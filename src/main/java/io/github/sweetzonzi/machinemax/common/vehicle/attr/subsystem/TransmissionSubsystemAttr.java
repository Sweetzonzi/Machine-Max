package io.github.sweetzonzi.machinemax.common.vehicle.attr.subsystem;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machinemax.common.vehicle.subsystem.TransmissionSubsystem;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 传动系统属性，将输入的动力按权重分流至各个输出端，再将各个输出端的运行速度反馈加权平均汇总至输入端。
 */
@Getter
public class TransmissionSubsystemAttr extends AbstractSubsystemAttr {
    public final Map<String, Float> powerOutputs;//功率输出目标，及输出功率减速比
    public final diffLockMode diffLock;//是否启用差速锁，即强制限制输出端转速成固定比例，可选ture,false,auto,manual
    public final float diffLockSensitivity;//差速锁灵敏度
    public final float autoDiffLockThreshold;//自动差速锁阈值，当输出端反馈转速差距百分比超过该值且diff_lock为auto时，自动启用差速锁
    public final List<String> manualDiffLockInputChannels;//控制信号名，优先级递减，留空接收所有信号
    public static final Codec<Map<String, Float>> POWER_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.FLOAT
    );

    public enum diffLockMode {
        TRUE,
        FALSE,
        AUTO,
        MANUAL
    }

    public static final MapCodec<TransmissionSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("basic_durability", 100f).forGetter(AbstractSubsystemAttr::getBasicDurability),
            Codec.STRING.optionalFieldOf("hit_box", "").forGetter(AbstractSubsystemAttr::getHitBox),
            POWER_OUTPUTS_CODEC.fieldOf("power_outputs").forGetter(TransmissionSubsystemAttr::getPowerOutputs),
            Codec.STRING.optionalFieldOf("diff_lock", "auto").forGetter(TransmissionSubsystemAttr::getDiffLock),
            Codec.FLOAT.optionalFieldOf("diff_lock_sensitivity", 1f).forGetter(TransmissionSubsystemAttr::getDiffLockSensitivity),
            Codec.FLOAT.optionalFieldOf("auto_diff_lock_threshold", 10f).forGetter(TransmissionSubsystemAttr::getAutoDiffLockThreshold),
            Codec.STRING.listOf().optionalFieldOf("distribute_inputs", List.of("diff_lock_control")).forGetter(TransmissionSubsystemAttr::getManualDiffLockInputChannels)
    ).apply(instance, TransmissionSubsystemAttr::new));

    public TransmissionSubsystemAttr(
            float basicDurability,
            String hitBox,
            Map<String, Float> powerOutputs,
            String diffLock,
            float diffLockSensitivity,
            float autoDiffLockThreshold,
            List<String> manualDiffLockInputChannels) {
        super(basicDurability, hitBox);
        this.powerOutputs = powerOutputs;
        for (Map.Entry<String, Float> entry : powerOutputs.entrySet()) {
            String targetName = entry.getKey();
            float gearRatio = entry.getValue();
            if (gearRatio <= 0) {
                throw new IllegalArgumentException("Invalid transmission power output target's gear ratio: " + gearRatio + " for target: " + targetName + ". It must be positive.");
            }
        }
        this.diffLock = diffLockMode.valueOf(diffLock.toUpperCase());
        this.diffLockSensitivity = diffLockSensitivity;
        this.autoDiffLockThreshold = autoDiffLockThreshold;
        this.manualDiffLockInputChannels = manualDiffLockInputChannels;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemType getType() {
        return SubsystemType.TRANSMISSION;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new TransmissionSubsystem(owner, name, this);
    }

    private String getDiffLock() {
        return diffLock.toString().toLowerCase();
    }
}
