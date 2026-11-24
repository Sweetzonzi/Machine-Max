package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.CouplerSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.CouplerSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 锁扣子系统安装属性：绑定本子零件上的一个高级连接点作为主动端。
 *
 * <p>被动端不在这里配置——运行时在同一载具内按几何发现最近的兼容简单端。</p>
 */
@Getter
public class CouplerSubsystemAttr extends BasicSubsystemDynamicAttr {

    public final CouplerSubsystemStaticAttr staticAttribute;
    public final String connector;

    public static final MapCodec<CouplerSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("connector").forGetter(CouplerSubsystemAttr::getConnector)
    ).apply(instance, CouplerSubsystemAttr::new));

    public CouplerSubsystemAttr(ResourceLocation modelName, String connector) {
        super(modelName);
        this.staticAttribute = (CouplerSubsystemStaticAttr) getStaticAttr();
        this.connector = connector;
    }

    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        requireAdvancedConnector(variant, subPartName, subsystemName, connector);
        var subPart = variant.getSubParts().get(subPartName);
        if (subPart != null) {
            // 锁扣的语义是运行时解扣；内部连接点按定义永久且不可解扣，两者互斥。
            var connectorAttr = subPart.connectors.get(connector);
            if (connectorAttr != null && connectorAttr.isInternal()) {
                throw new PackContentException(Component.literal(
                        "Coupler " + subsystemName + " of " + variant.getModel()
                                + " targets internal connector " + connector
                                + "; internal connectors are permanent and cannot be unlatched"));
            }
            // 同一子零件最多安装一个锁扣：主动端按连接点名唯一，两个锁扣抢同一个端点无法表达。
            long count = subPart.getSubsystems().values().stream()
                    .filter(install -> install instanceof CouplerSubsystemAttr)
                    .count();
            if (count > 1) {
                throw new PackContentException(Component.literal(
                        "Sub part " + subPartName + " of " + variant.getModel()
                                + " installs more than one coupler"));
            }
        }
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public CouplerSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new CouplerSubsystem(owner, name, this);
    }
}
