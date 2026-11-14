package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AmmoLoaderSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AmmoLoaderSubsystemStaticAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 自动装弹机子系统动态属性。<br>
 * 机构同时是下游（Launcher）的提供方、上游（弹药架）的需求方与人工段的用人方，因此三份发现配置都在这里：
 * <ul>
 *   <li>{@code ammo_discovery_inputs} — 本机构接受的供弹服务频道，下游按它发现本机</li>
 *   <li>{@code ammo_discovery_targets} — 本机构查询上游弹药架用的频道与目标</li>
 *   <li>{@code reload_operator_discovery_targets} — 本机构为人工段查询座位用的频道与目标；
 *       座位在自己的 {@code reload_operator_discovery_inputs} 里声明同一频道</li>
 * </ul>
 * 上游查询只接受库存持有者（弹药架），机构之间不互认上游，机械链因此不会成环；座位查询只认座位，
 * 命中的座位取它的实际乘员作为人工段执行者。
 */
@Getter
public class AmmoLoaderSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final AmmoLoaderSubsystemStaticAttr staticAttribute;

    /** 本机构接受的供弹服务频道（下游 Launcher 按它发现本机） */
    public final List<String> ammoDiscoveryInputs;

    /** 上游查询：供弹服务频道 → 目标名称列表（指向本机的上游弹药架） */
    public final Map<String, List<String>> ammoDiscoveryTargets;

    /**
     * 人工段用人查询：装填人员频道 → 目标名称列表。<br>
     * 常规写法是 {@code {"crew.reload.loader": ["local"]}}：{@code local} 是本 Part 的全部 SubPart，座位
     * 只要在自己的实例字段里声明同一频道就会被发现。两个独立炮塔做在两个 Part 里时，{@code local} 天然隔离。
     */
    public final Map<String, List<String>> reloadOperatorDiscoveryTargets;

    /**
     * 岗位时间倍率 {@code P}：装填人员频道 → 该位置的乘员为本机构操作时的便利程度（装填手 1.0、炮手 2.0）。<br>
     * 它只属于"本机构 → 该座位"这条发现关系，不是座位的全局能力；同一个座位替另一门武器装填可以是别的倍率。
     * 未声明的频道按 1.0 处理。
     */
    public final Map<String, Double> reloadOperatorTimeMultipliers;

    public static final MapCodec<AmmoLoaderSubsystemAttr> CODEC =
            RecordCodecBuilder.<AmmoLoaderSubsystemAttr>mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
                    Codec.STRING.listOf().optionalFieldOf("ammo_discovery_inputs", List.of())
                            .forGetter(AmmoLoaderSubsystemAttr::getAmmoDiscoveryInputs),
                    AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC
                            .optionalFieldOf("ammo_discovery_targets", Map.of())
                            .forGetter(AmmoLoaderSubsystemAttr::getAmmoDiscoveryTargets),
                    AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC
                            .optionalFieldOf("reload_operator_discovery_targets", Map.of())
                            .forGetter(AmmoLoaderSubsystemAttr::getReloadOperatorDiscoveryTargets),
                    Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                            .optionalFieldOf("reload_operator_time_multipliers", Map.of())
                            .forGetter(AmmoLoaderSubsystemAttr::getReloadOperatorTimeMultipliers)
            ).apply(instance, AmmoLoaderSubsystemAttr::new))
                    .validate(AmmoLoaderSubsystemAttr::validateMultipliers);

    public AmmoLoaderSubsystemAttr(
            ResourceLocation modelName,
            List<String> ammoDiscoveryInputs,
            Map<String, List<String>> ammoDiscoveryTargets,
            Map<String, List<String>> reloadOperatorDiscoveryTargets,
            Map<String, Double> reloadOperatorTimeMultipliers) {
        super(modelName);
        this.staticAttribute = (AmmoLoaderSubsystemStaticAttr) getStaticAttr();
        this.ammoDiscoveryInputs = ammoDiscoveryInputs;
        this.ammoDiscoveryTargets = ammoDiscoveryTargets;
        this.reloadOperatorDiscoveryTargets = reloadOperatorDiscoveryTargets;
        this.reloadOperatorTimeMultipliers = reloadOperatorTimeMultipliers;
    }

    /**
     * 岗位倍率必须是有限正数：它直接乘在人工段时长上，零或负值会把时长变成 0 或负数，非有限值则算不出等待。
     * 未列出的频道取 1.0，因此只有真的写了一个坏值才拒绝。
     */
    private static DataResult<AmmoLoaderSubsystemAttr> validateMultipliers(AmmoLoaderSubsystemAttr attr) {
        for (Map.Entry<String, Double> entry : attr.reloadOperatorTimeMultipliers.entrySet()) {
            double value = entry.getValue();
            if (!Double.isFinite(value) || value <= 0) {
                return DataResult.error(() -> "reload_operator_time_multipliers[" + entry.getKey()
                        + "] 必须是有限正数，实际为 " + value);
            }
        }
        return DataResult.success(attr);
    }

    /**
     * 某条发现关系上的岗位倍率；未声明时按标准 1.0。
     *
     * @param channel 装填人员频道
     */
    public double timeMultiplierFor(String channel) {
        Double value = reloadOperatorTimeMultipliers.get(channel);
        return value == null ? 1.0 : value;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public AmmoLoaderSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new AmmoLoaderSubsystem(owner, name, this);
    }
}
