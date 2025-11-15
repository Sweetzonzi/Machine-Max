package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.registry.MMDataRegistries;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Getter
abstract public class AbstractSubsystemAttr {
    public final ResourceLocation modelName;//注册的子系统型号

    protected AbstractSubsystemAttr(ResourceLocation modelName) {
        this.modelName = modelName;
    }

    public abstract MapCodec<? extends AbstractSubsystemAttr> codec();

    public abstract SubsystemTypes getType();

    public abstract AbstractSubsystem createSubsystem(ISubsystemHost owner, String name);

    public static final Codec<Map<String, List<String>>> SIGNAL_TARGETS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<AbstractSubsystemAttr> CODEC = MMDataRegistries.getSUBSYSTEM_ATTR_CODEC().byNameCodec()
            .dispatch(
                    AbstractSubsystemAttr::codec,
                    Function.identity()
            );

    public static final Codec<Map<String, AbstractSubsystemAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//子系统名称
            CODEC//子系统属性
    );

    protected AbstractSubsystemStaticAttr getStaticAttr() {
        var staticAttr = MMDynamicRes.STATIC_SUBSYSTEM_ATTRS.get(getModelName());
        if (staticAttr == null)
            throw new NullPointerException("Subsystem model " + getModelName() + "(" + getType() + ") not found");
        if (staticAttr.getType() != getType())
            throw new ClassCastException("Subsystem model " + getModelName() + " type mismatches with subsystem type " + getType());
        else return staticAttr;
    }

    public float getBasicDurability() {
        var staticAttr = getStaticAttr();
        if (staticAttr == null) {
            return -1.0f;
        } else return staticAttr.basicDurability;
    }
}
