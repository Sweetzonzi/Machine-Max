package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemCodecs;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemKinds;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

@Getter
abstract public class AbstractSubsystemStaticAttr {

    public final float basicDurability;

    protected AbstractSubsystemStaticAttr(float basicDurability) {
        this.basicDurability = basicDurability;
    }

    public abstract MapCodec<? extends AbstractSubsystemStaticAttr> codec();

    /**
     * 类型 ID，由登记表按 {@link #codec()} 的实例身份反查。
     *
     * <p>型号 JSON 的 {@code type} 经 {@link SubsystemCodecs#STATIC_CODEC} 分派到这里登记的
     * 静态属性 Codec；型号与安装属性的类型绑定由
     * {@link io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AbstractSubsystemAttr#getStaticAttr()}
     * 按两侧派生 ID 的值相等校验。</p>
     *
     * @return 该型号的命名空间类型 ID，例如 {@code machine_max:wheel_driver}
     * @throws PackContentException {@code codec()} 返回的实例未登记时抛出
     */
    public final ResourceLocation getType() {
        return SubsystemKinds.nameOf(codec(), SubsystemKinds.Slot.STATIC);
    }

    public static final Codec<AbstractSubsystemStaticAttr> CODEC = SubsystemCodecs.STATIC_CODEC;
}
