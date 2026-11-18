package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import lombok.Getter;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Getter
public class InteractBoxAttr {
    /** 当前支持的交互模式，与 {@link io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox.InteractMode} 一一对应 */
    public static final Set<String> MODES = Set.of("fast", "accurate");

    /** 不做门控的写法：空串与字面量 {@code true} 都按"恒可交互"处理，与 {@code HitBoxAttr#condition} 同一口径。 */
    public static final String ALWAYS = "true";

    public final String boneName;
    public final Map<String, List<String>> signalTargets;
    public final String mode;
    /**
     * 门控表达式：MoLang 脚本，求值 {@code > 0} 表示交互区此刻可用。
     * <p>
     * 与命中框的 {@code condition} 是同一套写法，求值上下文是宿主部件的
     * {@code SparkMolangContext}（{@code local.*} / {@code global.*}），因此能读到子系统状态、
     * 信号存储与装配体数值。交互区只发不收，没有频道输入，也就没有"把若干输入信号按与／或组合"
     * 这类条件。
     * <p>
     * 默认 {@code "true"}：不写就是不门控。
     */
    public final String condition;

    public static final Codec<InteractBoxAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("bone").forGetter(InteractBoxAttr::getBoneName),
            ConnectorAttr.SIGNAL_TARGETS_CODEC.fieldOf("signal_targets").forGetter(InteractBoxAttr::getSignalTargets),
            Codec.STRING.optionalFieldOf("interact_mode", "fast").forGetter(InteractBoxAttr::getMode),
            Codec.STRING.optionalFieldOf("condition", ALWAYS).forGetter(InteractBoxAttr::getCondition)
    ).apply(instance, InteractBoxAttr::new));

    public static final Codec<Map<String, InteractBoxAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//交互区名称
            CODEC//形状属性，包含控制形状的骨骼名称、信号传输频道和目标、交互模式等
    );

    public InteractBoxAttr(String boneName, Map<String, List<String>> signalTargets, String mode, String condition) {
        this.boneName = boneName;
        this.signalTargets = signalTargets;
        this.mode = mode;
        this.condition = condition == null || condition.isBlank() ? ALWAYS : condition;
    }

    /**
     * 装载期检查：交互框的骨骼必须落在宿主子部件区间内，交互模式必须是受支持的取值。
     *
     * <p>骨骼不存在时 {@code getInteractBoxShape} 只记录日志并跳过，交互区会静默消失；
     * 这里在装载期把它变成内容错误。交互模式不受支持时 {@code InteractBox} 构造器会直接抛
     * {@link IllegalArgumentException}，同样提前拦截。</p>
     *
     * <p>门控表达式不在这里编译：MoLang 需要宿主部件的求值上下文，编译发生在
     * {@code InteractBox} 构造期；写错的表达式只让该交互区恒不可交互并留一条错误日志，
     * 不阻断内容加载。</p>
     *
     * @param variant        交互框所属变体
     * @param subPartName    宿主子部件名称
     * @param interactBoxName 交互框名称，仅用于错误信息
     */
    public void validate(VariantAttr variant, String subPartName, String interactBoxName) {
        String normalizedMode = mode == null ? "" : mode.toLowerCase(Locale.ROOT);
        if (!MODES.contains(normalizedMode)) throw new PackContentException(
                MMLanguageRegistry.ERROR_PART_INTERACTBOX_UNSUPPORTED_MODE.component(
                        variant.getModel().toString(), subPartName, interactBoxName, mode));
        SubPartAttr subPart = variant.getSubParts().get(subPartName);
        if (subPart != null && !subPart.getBones(variant).containsKey(boneName)) {
            throw new PackContentException(
                    MMLanguageRegistry.ERROR_PART_INTERACTBOX_BONE_NOT_FOUND.component(
                            variant.getModel().toString(), subPartName, interactBoxName, boneName));
        }
    }
}
