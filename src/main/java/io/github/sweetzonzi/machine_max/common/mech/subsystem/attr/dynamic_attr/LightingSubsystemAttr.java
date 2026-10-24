package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LightingSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LightingSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
public class LightingSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final LightingSubsystemStaticAttr staticAttribute;
    public final String lightLocator;

    public static final MapCodec<LightingSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "").forGetter(LightingSubsystemAttr::getLightLocator)
    ).apply(instance, LightingSubsystemAttr::new));

    public LightingSubsystemAttr(ResourceLocation modelName, String lightLocator) {
        super(modelName);
        this.staticAttribute = (LightingSubsystemStaticAttr) getStaticAttr();
        this.lightLocator = lightLocator == null ? "" : lightLocator;
    }

    /**
     * 只有会实际发光的型号才要求定位器：范围为 0 或强度为 0 的定义（如 dummy_light）本身不渲染，
     * 缺定位器不会造成观感错误。会发光的型号没有回退，名字解析不到时灯光整盏不出现。
     */
    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        if (staticAttribute.getRange() <= 0f || staticAttribute.getIntensity() <= 0f) return;
        if (!hasLocator(variant, subPartName, lightLocator)) {
            throw locatorNotFound(variant, subPartName, subsystemName, lightLocator);
        }
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LIGHTING;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new LightingSubsystem(owner, name, this);
    }
}
