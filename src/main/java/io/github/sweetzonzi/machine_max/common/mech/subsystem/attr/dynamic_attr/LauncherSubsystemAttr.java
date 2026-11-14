package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.LauncherSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.LauncherSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 发射器子系统动态属性。<br>
 * 定义发射点 locator、弹药反馈输出，以及本管查询供弹来源与装填人员用的发现表。
 * 开火信号等接收频道在静态属性中定义。
 */
@Getter
public class LauncherSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final LauncherSubsystemStaticAttr staticAttribute;
    public final String locator;                              // 发射点locator名称
    public final String fireAnimation;                        // 开火时播放的动画名（零件动画集内的键）
    public final Map<String, List<String>> ammoCountOutputs;   // 剩余弹药反馈输出

    /**
     * 供弹来源查询：服务频道 → 目标名称列表。<br>
     * 本管在结构变化时按这张表做只读发现。命中自动装弹机时由机构装填；命中弹药架时只有本型号声明了
     * 人工段（{@code human_reload_time > 0}）才把它当成交付目标——那是人力直取路径：从架直接取弹、
     * 全程由乘员装填。没有人工段的管只经机构装填。
     */
    public final Map<String, List<String>> ammoDiscoveryTargets;

    /**
     * 人工段用人查询：装填人员频道 → 目标名称列表。<br>
     * 只有人力直取路径用它：机构有自己的那张表（人工段属于机构的流程）。常规写法是
     * {@code {"crew_loader_reload": ["local"]}}：{@code local} 是本 Part 的全部 SubPart，座位只要在自己的
     * 实例字段里声明同一频道就会被发现。
     */
    public final Map<String, List<String>> reloadOperatorDiscoveryTargets;

    /**
     * 岗位时间倍率 {@code P}：装填人员频道 → 该位置的乘员为本管装填时的便利程度（装填手 1.0、炮手 2.0）。<br>
     * 它只属于"本管 → 该座位"这条发现关系，不是座位的全局能力；同一个座位替另一门武器装填可以是别的倍率。
     * 未声明的频道按 1.0 处理。
     */
    public final Map<String, Double> reloadOperatorTimeMultipliers;

    public static final MapCodec<LauncherSubsystemAttr> CODEC =
            RecordCodecBuilder.<LauncherSubsystemAttr>mapCodec(instance -> instance.group(
                    ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
                    Codec.STRING.fieldOf("locator").forGetter(LauncherSubsystemAttr::getLocator),
                    Codec.STRING.optionalFieldOf("fire_animation", "fire").forGetter(LauncherSubsystemAttr::getFireAnimation),
                    AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("ammo_outputs", Map.of())
                            .forGetter(LauncherSubsystemAttr::getAmmoCountOutputs),
                    AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("ammo_discovery_targets", Map.of())
                            .forGetter(LauncherSubsystemAttr::getAmmoDiscoveryTargets),
                    AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC
                            .optionalFieldOf("reload_operator_discovery_targets", Map.of())
                            .forGetter(LauncherSubsystemAttr::getReloadOperatorDiscoveryTargets),
                    Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                            .optionalFieldOf("reload_operator_time_multipliers", Map.of())
                            .forGetter(LauncherSubsystemAttr::getReloadOperatorTimeMultipliers)
            ).apply(instance, LauncherSubsystemAttr::new))
                    .validate(LauncherSubsystemAttr::validateMultipliers);

    public LauncherSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            String fireAnimation,
            Map<String, List<String>> ammoCountOutputs,
            Map<String, List<String>> ammoDiscoveryTargets,
            Map<String, List<String>> reloadOperatorDiscoveryTargets,
            Map<String, Double> reloadOperatorTimeMultipliers) {
        super(modelName);
        this.staticAttribute = (LauncherSubsystemStaticAttr) getStaticAttr();
        this.locator = locator;
        this.fireAnimation = fireAnimation;
        this.ammoCountOutputs = ammoCountOutputs;
        this.ammoDiscoveryTargets = ammoDiscoveryTargets;
        this.reloadOperatorDiscoveryTargets = reloadOperatorDiscoveryTargets;
        this.reloadOperatorTimeMultipliers = reloadOperatorTimeMultipliers;
    }

    /**
     * 岗位倍率必须是有限正数：它直接乘在人工段时长上，零或负值会把时长变成 0 或负数，非有限值则算不出等待。
     * 未列出的频道取 1.0，因此只有真的写了一个坏值才拒绝。
     */
    private static DataResult<LauncherSubsystemAttr> validateMultipliers(LauncherSubsystemAttr attr) {
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
     * 发射点必须落在宿主子部件区间内。`locator` 是没有默认值的必填字段，缺字段在解码阶段
     * 就被 codec 拒绝，这里只需处理名字解析不到的情况。
     */
    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        if (!hasLocator(variant, subPartName, locator)) {
            throw locatorNotFound(variant, subPartName, subsystemName, locator);
        }
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new LauncherSubsystem(owner, name, this);
    }
}
