package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SightSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SightSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/**
 * 固定炮镜的安装属性。<br>
 * 宿主子部件由子系统所在的配置节点确定，locator 同时提供位置和旋转；
 * 光轴随宿主刚体运动，输入路由由操作者的控制组负责。
 */
@Getter
public class SightSubsystemAttr extends AbstractViewpointSubsystemAttr {
    public final SightSubsystemStaticAttr staticAttribute;

    public static final MapCodec<SightSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition")
                    .forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "")
                    .forGetter(SightSubsystemAttr::getLocator)
    ).apply(instance, SightSubsystemAttr::new));

    public SightSubsystemAttr(
            ResourceLocation modelName,
            String locator) {
        super(modelName, locator);
        this.staticAttribute = (SightSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGHT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new SightSubsystem(owner, name, this);
    }
}
