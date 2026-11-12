package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoRackSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AmmoRackSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * 物品弹药架子系统动态属性。<br>
 * 目前只有型号引用：实例侧还没有需要区分的东西，物品容器本身按型号容量建立。
 * <p>
 * <b>WIP：</b>物品往返尚未实现（见 {@link AmmoRackSubsystem}）。
 */
@ApiStatus.Experimental
@Getter
public class AmmoRackSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final AmmoRackSubsystemStaticAttr staticAttribute;

    public static final MapCodec<AmmoRackSubsystemAttr> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
            ).apply(instance, AmmoRackSubsystemAttr::new));

    public AmmoRackSubsystemAttr(ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (AmmoRackSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.AMMO_RACK;
    }

    @Override
    public AmmoRackSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new AmmoRackSubsystem(owner, name, this);
    }
}
