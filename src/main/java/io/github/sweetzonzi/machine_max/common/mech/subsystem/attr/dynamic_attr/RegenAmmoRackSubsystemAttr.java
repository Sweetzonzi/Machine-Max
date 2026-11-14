package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.ammo.AmmoBelt;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.RegenAmmoRackSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.RegenAmmoRackSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 再生弹药架子系统动态属性。<br>
 * 定义该架持有哪一份交付规格、以及在哪些供弹频道上被上游发现。
 * <p>
 * {@link #beltDefinition} 在实例上而不是型号上，因为同一型号会服务不同弹种（例如 2S38 的 AP 与 HE
 * 两个架共用一套静态属性）。{@link #ammoDiscoveryInputs} 是该架接受的供弹频道列表，机构的
 * 上游查询按它到达本架。
 * </p>
 */
@Getter
public class RegenAmmoRackSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final RegenAmmoRackSubsystemStaticAttr staticAttribute;

    /** 本架接受的供弹服务频道（机构的上游查询按它到达本架） */
    public final List<String> ammoDiscoveryInputs;

    /**
     * 交付规格：满额模板（{@code remaining == length}、回充运行态为零）。<br>
     * 既是交付规格，也是弹药池的分组键；持有者不得原地推进它。
     */
    public final AmmoBelt beltDefinition;

    public static final MapCodec<RegenAmmoRackSubsystemAttr> CODEC =
            RecordCodecBuilder.<RegenAmmoRackSubsystemAttr>mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
                    AmmoBelt.CODEC.fieldOf("belt_definition")
                            .forGetter(RegenAmmoRackSubsystemAttr::getBeltDefinition),
                    Codec.STRING.listOf().optionalFieldOf("ammo_discovery_inputs", List.of())
                            .forGetter(RegenAmmoRackSubsystemAttr::getAmmoDiscoveryInputs)
            ).apply(instance, RegenAmmoRackSubsystemAttr::new))
                    .validate(RegenAmmoRackSubsystemAttr::validateDefinition);

    public RegenAmmoRackSubsystemAttr(
            ResourceLocation modelName,
            AmmoBelt beltDefinition,
            List<String> ammoDiscoveryInputs) {
        super(modelName);
        this.staticAttribute = (RegenAmmoRackSubsystemStaticAttr) getStaticAttr();
        this.beltDefinition = beltDefinition;
        this.ammoDiscoveryInputs = ammoDiscoveryInputs;
    }

    /**
     * 交付规格的边界：一份至少 1 发，且不超过架容量。<br>
     * 长度大于容量时这一份永远攒不满，属于配置错误而不是运行期状态，因此在解码期拒绝并点名字段。
     */
    private static DataResult<RegenAmmoRackSubsystemAttr> validateDefinition(RegenAmmoRackSubsystemAttr attr) {
        AmmoBelt belt = attr.beltDefinition;
        int capacity = attr.staticAttribute.getCapacity();
        if (belt.length() < 1) {
            return DataResult.error(() -> "belt_definition.length 必须至少为 1，实际为 " + belt.length());
        }
        if (belt.length() > capacity) {
            return DataResult.error(() -> "belt_definition.length 不得超过 capacity（"
                    + capacity + "），实际为 " + belt.length());
        }
        return DataResult.success(attr);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public RegenAmmoRackSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new RegenAmmoRackSubsystem(owner, name, this);
    }
}
