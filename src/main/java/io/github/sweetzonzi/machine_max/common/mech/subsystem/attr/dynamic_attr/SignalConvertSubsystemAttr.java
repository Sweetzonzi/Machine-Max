package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

/**
 * 信号转换器子系统动态属性（占位）。
 * <p>
 * <b>WIP：</b>尚无实现类，{@link #codec()} 与 {@link #createSubsystem} 返回 {@code null}，
 * {@link SubsystemTypes#SIGNAL_CONVERT} 目前不能在内容包中使用。
 */
@ApiStatus.Experimental
@Getter
public class SignalConvertSubsystemAttr extends BasicSubsystemDynamicAttr{
    protected SignalConvertSubsystemAttr(
            ResourceLocation modelName) {
        super(modelName);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return null;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGNAL_CONVERT;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return null;
    }
}
