package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenLoaderSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 再生装弹机子系统动态属性。<br>
 * 定义弹药发现频道与逐实例的输出模板。
 * <p>
 * {@link #beltDefinition} 是一份满额 {@link AmmoBelt}：模式、长度、标签与可选自身回充都由它表达。
 * 它在实例上而不是型号上，因为同一型号会服务不同弹种（例如 AP 与 HE 两个装弹机共用一套静态属性）。
 */
@Getter
public class RegenLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final RegenLoaderSubsystemStaticAttr staticAttribute;

    /** 弹药发现频道 → 目标名称列表（用于自动发现同载具内的 IAmmoConsumer） */
    public final Map<String, List<String>> discoveryOutputs;

    /**
     * 输出模板：每次交付取它的一份副本，余量按本次交付的发数填写（库存不足一份时小于 {@code length}）。<br>
     * 它自身按 {@code remaining == length}、回充运行态为零保存，既用作交付规格，也用作弹药池的分组键；
     * 持有者不得原地推进它。
     */
    public final AmmoBelt beltDefinition;

    public static final MapCodec<RegenLoaderSubsystemAttr> CODEC = RecordCodecBuilder.<RegenLoaderSubsystemAttr>mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(RegenLoaderSubsystemAttr::getModelName),
            AmmoBelt.CODEC.fieldOf("belt_definition").forGetter(RegenLoaderSubsystemAttr::getBeltDefinition),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("discovery_outputs", Map.of())
                    .forGetter(RegenLoaderSubsystemAttr::getDiscoveryOutputs)
    ).apply(instance, RegenLoaderSubsystemAttr::new)).validate(RegenLoaderSubsystemAttr::validateDefinition);

    public RegenLoaderSubsystemAttr(
            ResourceLocation modelName,
            AmmoBelt beltDefinition,
            Map<String, List<String>> discoveryOutputs) {
        super(modelName);
        this.staticAttribute = (RegenLoaderSubsystemStaticAttr) getStaticAttr();
        this.beltDefinition = beltDefinition;
        this.discoveryOutputs = discoveryOutputs;
    }

    /**
     * 交付规格的边界：一份至少 1 发，且不超过源容量。<br>
     * 长度大于容量时源永远攒不满一份，属于配置错误而不是运行期状态，因此在解码期拒绝并点名字段。
     */
    private static DataResult<RegenLoaderSubsystemAttr> validateDefinition(RegenLoaderSubsystemAttr attr) {
        AmmoBelt belt = attr.beltDefinition;
        int capacity = attr.staticAttribute.getMagazine().capacity();
        if (belt.length() < 1) {
            return DataResult.error(() -> "belt_definition.length 必须至少为 1，实际为 " + belt.length());
        }
        if (belt.length() > capacity) {
            return DataResult.error(() -> "belt_definition.length 不得超过 magazine.capacity（"
                    + capacity + "），实际为 " + belt.length());
        }
        return DataResult.success(attr);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.REGEN_LOADER;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new RegenLoaderSubsystem(owner, name, this);
    }
}
