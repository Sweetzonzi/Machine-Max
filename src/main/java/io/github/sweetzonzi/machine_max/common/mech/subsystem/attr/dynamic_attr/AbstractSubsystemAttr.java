package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.registry.MMDataRegistries;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
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

    /** 控制组预设的"空"哨兵，表示无预设 */
    public static final ResourceLocation NO_CONTROL_GROUP_PRESET = ResourceLocation.parse("machine_max:empty");

    /**
     * 获取控制组预设的 ResourceLocation。
     * 子类（如 SeatSubsystemAttr）如有预设则覆盖此方法返回实际 RL；
     * 无预设的子系统继承此默认实现返回 NO_CONTROL_GROUP_PRESET。
     */
    public ResourceLocation getControlGroupPresetRl() {
        return NO_CONTROL_GROUP_PRESET;
    }

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
            staticAttr = MMDynamicRes.SERVER_STATIC_SUBSYSTEM_ATTRS.get(getModelName());
        if (staticAttr == null)
            throw new NullPointerException("Subsystem definition " + getModelName() + "(" + getType() + ") not found");
        if (staticAttr.getType() != getType())
            throw new ClassCastException("Subsystem definition " + getModelName() + " type mismatches with subsystem type " + getType());
        else return staticAttr;
    }

    public float getBasicDurability() {
        var staticAttr = getStaticAttr();
        if (staticAttr == null) {
            return 1.0f;
        } else return staticAttr.basicDurability;
    }
}
