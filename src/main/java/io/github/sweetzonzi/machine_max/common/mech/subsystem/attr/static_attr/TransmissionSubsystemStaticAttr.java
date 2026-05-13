package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;

import java.util.List;

/**
 * 传动系统属性，将输入的动力按权重分流至各个输出端，再将各个输出端的运行速度反馈加权平均汇总至输入端。
 */
@Getter
public class TransmissionSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    public final diffLockMode diffLock;//是否启用差速锁，即强制限制输出端转速成固定比例，可选ture,false,auto,manual
    public final float diffLockSensitivity;//差速锁灵敏度
    public final float autoDiffLockThreshold;//自动差速锁阈值，当输出端反馈转速差距百分比超过该值且diff_lock为auto时，自动启用差速锁
    public final List<String> manualDiffLockInputChannels;//控制信号名，优先级递减，留空接收所有信号

    public enum diffLockMode {
        TRUE,
        FALSE,
        AUTO,
        MANUAL
    }

    public static final MapCodec<TransmissionSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.STRING.optionalFieldOf("diff_lock", "auto").forGetter(TransmissionSubsystemStaticAttr::getDiffLock),
            Codec.FLOAT.optionalFieldOf("diff_lock_sensitivity", 1f).forGetter(TransmissionSubsystemStaticAttr::getDiffLockSensitivity),
            Codec.FLOAT.optionalFieldOf("auto_diff_lock_threshold", 10f).forGetter(TransmissionSubsystemStaticAttr::getAutoDiffLockThreshold),
            // TODO: 更新schema
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("car_control")).forGetter(TransmissionSubsystemStaticAttr::getManualDiffLockInputChannels),
            BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
    ).apply(instance, TransmissionSubsystemStaticAttr::new));

    public TransmissionSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            String diffLock,
            float diffLockSensitivity,
            float autoDiffLockThreshold,
            List<String> manualDiffLockInputChannels,
            BasicSoundAttr sounds
    ) {
        super(basicAttr, sounds);
        this.diffLock = diffLockMode.valueOf(diffLock.toUpperCase());
        this.diffLockSensitivity = diffLockSensitivity;
        this.autoDiffLockThreshold = autoDiffLockThreshold;
        this.manualDiffLockInputChannels = manualDiffLockInputChannels;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TRANSMISSION;
    }

    private String getDiffLock() {
        return diffLock.toString().toLowerCase();
    }
}
