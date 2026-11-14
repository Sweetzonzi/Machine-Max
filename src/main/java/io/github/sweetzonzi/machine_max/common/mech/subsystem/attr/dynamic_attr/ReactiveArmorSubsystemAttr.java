package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ReactiveArmorSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.ReactiveArmorSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/**
 * 爆反砖的安装属性：只有型号引用，字段形态与 {@link BasicSubsystemDynamicAttr} 相同。
 *
 * <p>静态属性在父类里是 {@link io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.BasicSubsystemStaticAttr}
 * 类型，这里收窄回本类型，供 {@link ReactiveArmorSubsystem} 直接读 {@code trigger_penetration}。</p>
 */
@Getter
public class ReactiveArmorSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final ReactiveArmorSubsystemStaticAttr staticAttribute;

    public static final MapCodec<ReactiveArmorSubsystemAttr> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName)
            ).apply(instance, ReactiveArmorSubsystemAttr::new));

    public ReactiveArmorSubsystemAttr(ResourceLocation modelName) {
        super(modelName);
        this.staticAttribute = (ReactiveArmorSubsystemStaticAttr) getStaticAttr();
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new ReactiveArmorSubsystem(owner, name, this);
    }
}
