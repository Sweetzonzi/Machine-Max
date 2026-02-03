package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.TransmissionSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.TransmissionSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * 传动系统属性，将输入的动力按权重分流至各个输出端，再将各个输出端的运行速度反馈加权平均汇总至输入端。
 */
@Getter
public class TransmissionSubsystemAttr extends AbstractSubsystemAttr {
    public final TransmissionSubsystemStaticAttr staticAttribute;
    public final Map<String, Float> powerOutputs;//功率输出目标，及输出功率减速比
    public static final Codec<Map<String, Float>> POWER_OUTPUTS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.FLOAT
    );

    public static final MapCodec<TransmissionSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            POWER_OUTPUTS_CODEC.fieldOf("power_outputs").forGetter(TransmissionSubsystemAttr::getPowerOutputs)
    ).apply(instance, TransmissionSubsystemAttr::new));

    public TransmissionSubsystemAttr(
            ResourceLocation modelName,
            Map<String, Float> powerOutputs) {
        super(modelName);
        this.staticAttribute = (TransmissionSubsystemStaticAttr) getStaticAttr();
        this.powerOutputs = powerOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TRANSMISSION;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new TransmissionSubsystem(owner, name, this);
    }
}
