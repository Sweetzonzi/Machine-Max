package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CameraSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/** 可转摄像头的安装配置，观察转轴状态由客户端逐设备持有。 */
@Getter
public class CameraSubsystemAttr extends AbstractViewpointSubsystemAttr {
    public final CameraSubsystemStaticAttr staticAttribute;


    public static final MapCodec<CameraSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition")
                    .forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "")
                    .forGetter(CameraSubsystemAttr::getLocator)
    ).apply(instance, CameraSubsystemAttr::new));

    public CameraSubsystemAttr(
            ResourceLocation modelName,
            String locator) {
        super(modelName, locator);
        this.staticAttribute = (CameraSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.CAMERA;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CameraSubsystem(owner, name, this);
    }
}
