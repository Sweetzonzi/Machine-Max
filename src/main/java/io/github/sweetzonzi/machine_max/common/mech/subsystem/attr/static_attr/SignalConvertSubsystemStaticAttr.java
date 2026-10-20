package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;

/**
 * 信号转换器子系统静态属性（占位）。
 * <p>
 * <b>WIP：</b>尚无实现类，{@link #codec()} 返回 {@code null}。
 */
@ApiStatus.Experimental
@Getter
public class SignalConvertSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    protected SignalConvertSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr) {
        super(basicAttr, BasicSoundAttr.DEFAULT);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return null;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SIGNAL_CONVERT;
    }

}
