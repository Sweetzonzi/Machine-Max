package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.GearboxSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.GearboxSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class GearboxSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final GearboxSubsystemStaticAttr staticAttribute;
    public final String powerOutputTarget;//动力输出端名
    public final Map<String, List<String>> gearOutputTargets;//输出反馈信号名，输出当前所处挡位供其他地方使用
    public final Map<String, List<String>> clutchOutputTargets;

    public GearboxSubsystemAttr(
            ResourceLocation modelName,
            String powerOutputTarget,
            Map<String, List<String>> gearOutputTargets) {
        this(modelName, powerOutputTarget, gearOutputTargets, Map.of());
    }

    public GearboxSubsystemAttr(
            ResourceLocation modelName,
            String powerOutputTarget,
            Map<String, List<String>> gearOutputTargets,
            Map<String, List<String>> clutchOutputTargets) {
        super(modelName);
        this.staticAttribute = (GearboxSubsystemStaticAttr) getStaticAttr();
        this.powerOutputTarget = powerOutputTarget;
        this.gearOutputTargets = gearOutputTargets;
        this.clutchOutputTargets = clutchOutputTargets;
    }

    public static final MapCodec<GearboxSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("power_output").forGetter(GearboxSubsystemAttr::getPowerOutputTarget),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("gear_outputs", Map.of("gear", List.of("local", "global"))).forGetter(GearboxSubsystemAttr::getGearOutputTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("clutch_outputs", Map.of()).forGetter(GearboxSubsystemAttr::getClutchOutputTargets)
    ).apply(instance, GearboxSubsystemAttr::new));

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new GearboxSubsystem(owner, name, this);
    }
}
