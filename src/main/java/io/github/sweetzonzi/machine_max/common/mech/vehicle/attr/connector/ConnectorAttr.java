package io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector;

import cn.solarmoon.spark_core.pack.diagnostic.PackContentException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.SubPartAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public class ConnectorAttr {
    /** 连接点对应的Locator名称 */
    public final String locatorName;
    /** 连接点属性定义 */
    public final ResourceLocation definition;
    /** 连接点属性覆写 */
    @Nullable
    public final OverwriteAttr overwrite;
    /** 从对侧连接点接收到的信号频道转译规则(channel_a->channel_c; channel_b->channel_c) */
    public final Map<String, String> signalTranslations;
    /** 连接点在本零件内的控制信号传输目标(子系统/连接点名/subpart/vehicle) */
    public final Map<String, List<String>> signalTargets;
    /** 是否为部件内部连接点（不参与对外拼装） */
    public final boolean internal;
    /** 连接点的机械能输出目标（子系统名） */
    public final String powerTarget;
    /** 连接点最终属性，考虑用户自定义覆写 */
    private final ConnectorStaticAttr attr;

    public ConnectorAttr(String locatorName, ResourceLocation definition, @Nullable OverwriteAttr overwrite,
                         String powerTarget, Map<String, String> signalTranslations,
                         Map<String, List<String>> signalTargets, boolean internal) {
        this.locatorName = locatorName;
        this.definition = definition;
        this.overwrite = overwrite;
        this.powerTarget = powerTarget;
        this.signalTranslations = signalTranslations;
        this.signalTargets = signalTargets;
        this.internal = internal;
        this.attr = getEffectiveAttr();
    }

    /**
     * @param type                     连接点类型
     * @param direction                连接点的法线方向
     * @param integrity                连接点结构完整性，受到大于此数值的伤害时会断开连接的关节
     * @param impactAbsorption         连接点受到冲击，但未超过剩余结构完整性即未能断开连接时，冲击转化为结构完整性损耗的比例
     * @param impactAbsorptionDestroyed SubPart已摧毁时，冲击转化为连接点完整性损耗的比例
     * @param impactReduction          连接点受到冲击时减少的冲击量
     * @param impactMultiplier         连接点受到冲击时的伤害倍率(与内部零件相连接的连接点恒定不可破坏，不受此影响)
     * @param collideBetweenParts      连接点是否允许部件间碰撞
     * @param requiredTags             连接点的必需标签
     * @param acceptableTags           连接点的可接受标签
     * @param forbiddenTags            连接点的禁止标签
     * @param jointAttrs               连接点的关节属性(限制，刚性与阻尼)
     */
    public record OverwriteAttr(
            Optional<String> type,
            Optional<Axis> direction,
            Optional<Float> integrity,
            Optional<Float> impactAbsorption,
            Optional<Float> impactAbsorptionDestroyed,
            Optional<Float> impactReduction,
            Optional<Float> impactMultiplier,
            Optional<Boolean> collideBetweenParts,
            Optional<List<ResourceLocation>> requiredTags,
            Optional<List<ResourceLocation>> acceptableTags,
            Optional<List<ResourceLocation>> forbiddenTags,
            Optional<Map<String, JointAttr>> jointAttrs
    ) {
        public static final Codec<OverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("type").forGetter(OverwriteAttr::type),
                Axis.CODEC.optionalFieldOf("direction").forGetter(OverwriteAttr::direction),
                Codec.FLOAT.optionalFieldOf("integrity").forGetter(OverwriteAttr::integrity),
                Codec.FLOAT.optionalFieldOf("impact_absorption").forGetter(OverwriteAttr::impactAbsorption),
                Codec.FLOAT.optionalFieldOf("impact_absorption_destroyed").forGetter(OverwriteAttr::impactAbsorptionDestroyed),
                Codec.FLOAT.optionalFieldOf("impact_reduction").forGetter(OverwriteAttr::impactReduction),
                Codec.FLOAT.optionalFieldOf("impact_multiplier").forGetter(OverwriteAttr::impactMultiplier),
                Codec.BOOL.optionalFieldOf("collide_between_parts").forGetter(OverwriteAttr::collideBetweenParts),
                ResourceLocation.CODEC.listOf().optionalFieldOf("required_tags").forGetter(OverwriteAttr::requiredTags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("acceptable_tags").forGetter(OverwriteAttr::acceptableTags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("forbidden_tags").forGetter(OverwriteAttr::forbiddenTags),
                JointAttr.MAP_CODEC.optionalFieldOf("joint_attrs").forGetter(OverwriteAttr::jointAttrs)
        ).apply(instance, OverwriteAttr::new));
    }

    public static final Codec<Map<String, List<String>>> SIGNAL_TARGETS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<ConnectorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("locator").forGetter(ConnectorAttr::getLocatorName),
            ResourceLocation.CODEC.fieldOf("definition").forGetter(ConnectorAttr::getDefinition),
            OverwriteAttr.CODEC.optionalFieldOf("overwrite").forGetter(attr -> Optional.ofNullable(attr.getOverwrite())),
            Codec.STRING.optionalFieldOf("power_target", "").forGetter(ConnectorAttr::getPowerTarget),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("signal_translations", Map.of()).forGetter(ConnectorAttr::getSignalTranslations),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("signal_targets", Map.of()).forGetter(ConnectorAttr::getSignalTargets),
            Codec.BOOL.optionalFieldOf("internal", false).forGetter(ConnectorAttr::isInternal)
    ).apply(instance, (
            locator,
            definition,
            overwrite,
            powerTarget,
            signalTranslations,
            signalTargets,
            internal
    ) -> new ConnectorAttr(locator, definition, overwrite.orElse(null), powerTarget, signalTranslations, signalTargets, internal)));

    public static final Codec<Map<String, ConnectorAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//连接点名称
            ConnectorAttr.CODEC//连接点属性
    );

    private ConnectorStaticAttr getStaticAttr() {
        var result = MMDynamicRes.CONNECTORS.get(definition);
        if (result == null) {
            throw new IllegalArgumentException("Connector definition not found: " + definition);
        }
        return result;
    }

    public boolean conditionCheck(AbstractPartType partType, String variant) {
        if (MMServerConfig.ignoreAssemblyTagRequirements()) return true; // 检查服务端配置，视情况忽略配方要求
        Set<ResourceLocation> tags = new HashSet<>(partType.getVariant(variant).getTags());
        var effectiveAttr = getEffectiveAttr();
        //检查必须拥有的tag情况(必须全都有)
        if (effectiveAttr.requiredTags().isEmpty() || tags.containsAll(effectiveAttr.requiredTags())) {
            boolean hasAcceptableTags = false;
            for (ResourceLocation acceptableTag : effectiveAttr.acceptableTags()) {
                if (tags.contains(acceptableTag)) {
                    hasAcceptableTags = true;
                    break;
                }
            }
            //检查可接受的tag情况(有一个符合要求即可)
            if (effectiveAttr.acceptableTags().isEmpty() || hasAcceptableTags) {
                boolean hasForbiddenTags = false;
                for (ResourceLocation forbiddenTag : effectiveAttr.forbiddenTags()) {
                    if (tags.contains(forbiddenTag)) {
                        hasForbiddenTags = true;
                        break;
                    }
                }
                return effectiveAttr.forbiddenTags().isEmpty() || !hasForbiddenTags;
            } else return false;
        } else return false;
    }

    public boolean isSimpleConnector() {
        return getEffectiveAttr().type().equalsIgnoreCase("simple");
    }

    /**
     * 获取有效的连接点属性，合并基础属性和覆写属性
     *
     * @return 合并后的连接点属性
     */
    public ConnectorStaticAttr getEffectiveAttr() {
        ConnectorStaticAttr baseAttr = getStaticAttr();
        if (overwrite == null) {
            return baseAttr;
        }
        return new ConnectorStaticAttr(
                overwrite.type().isPresent() ? overwrite.type().get() : baseAttr.type(),
                overwrite.direction().isPresent() ? overwrite.direction().get() : baseAttr.direction(),
                overwrite.integrity().isPresent() ? overwrite.integrity().get() : baseAttr.integrity(),
                overwrite.impactAbsorption().isPresent() ? overwrite.impactAbsorption().get() : baseAttr.impactAbsorption(),
                overwrite.impactAbsorptionDestroyed().isPresent() ? overwrite.impactAbsorptionDestroyed().get() : baseAttr.impactAbsorptionDestroyed(),
                overwrite.impactReduction().isPresent() ? overwrite.impactReduction().get() : baseAttr.impactReduction(),
                overwrite.impactMultiplier().isPresent() ? overwrite.impactMultiplier().get() : baseAttr.impactMultiplier(),
                overwrite.collideBetweenParts().isPresent() ? overwrite.collideBetweenParts().get() : baseAttr.collideBetweenParts(),
                overwrite.requiredTags().isPresent() ? overwrite.requiredTags().get() : baseAttr.requiredTags(),
                overwrite.acceptableTags().isPresent() ? overwrite.acceptableTags().get() : baseAttr.acceptableTags(),
                overwrite.forbiddenTags().isPresent() ? overwrite.forbiddenTags().get() : baseAttr.forbiddenTags(),
                overwrite.jointAttrs().isPresent() ? overwrite.jointAttrs().get() : baseAttr.jointAttrs()
        );
    }

    /**
     * 关节属性允许的轴名：平动 X/Y/Z 与转动 XR/YR/ZR。
     *
     * <p>{@link Axis#getValue(String)} 还接受 XP/YP/ZP/XN/YN/ZN，但那些名字映射到 10~12 与 -10~-12，
     * 而 {@code adjustJoint} 把它们直接当作 Bullet 六自由度关节的轴下标使用，只有 0~5 有效。</p>
     */
    private static final Set<String> JOINT_AXES = Set.of("X", "Y", "Z", "XR", "YR", "ZR");

    /**
     * 装载期检查：连接点自身的引用字段必须能解析，且取值都在合法集合内。
     *
     * <p>这些字段在运行时失败的方式都是静默的：类型名写错只会让连接点退化成另一种连接器，
     * {@code joint_attrs} 的轴名要等到关节建立时才抛 {@link IllegalArgumentException}，
     * {@code power_target} 与 {@code signal_targets} 解析不到时会被直接丢弃。
     * 把问题提前到装载期，错误信息才能带上模型、子部件与连接点名。</p>
     *
     * @param variant       连接点所属变体
     * @param subPartName   宿主子部件名称，仅用于错误信息
     * @param connectorName 连接点名称，仅用于错误信息
     * @throws PackContentException 类型名非法、关节轴名非法、电力或信号目标解析不到时抛出
     */
    public void validate(VariantAttr variant, String subPartName, String connectorName) {
        String model = variant.getModel().toString();
        // 类型名：isSimpleConnector() 只认 "simple"，其余取值一律按 AdvancedConnector 处理
        if (!isSimpleConnector() && !attr.type().equalsIgnoreCase("advanced")) {
            throw new PackContentException(
                    MMLanguageRegistry.ERROR_PART_INVALID_CONNECTOR_TYPE.component(
                            model, subPartName, connectorName, attr.type()));
        }
        if (!attr.jointAttrs().isEmpty()) {
            for (String axis : attr.jointAttrs().keySet()) {
                if (!JOINT_AXES.contains(axis.toUpperCase(Locale.ROOT))) {
                    throw new PackContentException(
                            MMLanguageRegistry.ERROR_PART_CONNECTOR_INVALID_JOINT_AXIS.component(
                                    model, subPartName, connectorName, axis));
                }
            }
            // 关节属性只在 AdvancedConnector 上生效，写在简单连接点上是无操作的配置
            if (isSimpleConnector()) {
                MachineMax.LOGGER.warn(
                        "子部件 {}（模型 {}）的连接点 {} 是 simple 类型，其 joint_attrs 不会被应用",
                        subPartName, variant.getModel(), connectorName);
            }
        }
        SubPartAttr subPart = variant.getSubParts().get(subPartName);
        if (subPart == null) return;
        if (!powerTarget.isEmpty()
                && !subPart.getSubsystems().containsKey(powerTarget)
                && !subPart.getConnectors().containsKey(powerTarget)) {
            throw new PackContentException(
                    MMLanguageRegistry.ERROR_PART_CONNECTOR_POWER_TARGET_NOT_FOUND.component(
                            model, subPartName, connectorName, powerTarget));
        }
        for (Map.Entry<String, List<String>> entry : signalTargets.entrySet()) {
            if (entry.getKey().isEmpty()) continue;// 空频道名在运行时被跳过
            for (String target : entry.getValue()) {
                if (VariantAttr.RESERVED_SIGNAL_NAMES.contains(target)) continue;
                if (!subPart.getSubsystems().containsKey(target)
                        && !subPart.getConnectors().containsKey(target)
                        && !subPart.getInteractBoxes().containsKey(target)) {
                    throw new PackContentException(
                            MMLanguageRegistry.ERROR_PART_CONNECTOR_SIGNAL_TARGET_NOT_FOUND.component(
                                    model, subPartName, connectorName, entry.getKey(), target));
                }
            }
        }
    }

    public String getType() {
        return attr.type();
    }

    public Axis getDirection() {
        return attr.direction();
    }

    public float getIntegrity() {
        return attr.integrity();
    }

    public float getImpactAbsorption() {
        return attr.impactAbsorption();
    }

    /**
     * @return SubPart已摧毁时，冲击转化为连接点完整性损耗的比例
     */
    public float getImpactAbsorptionDestroyed() {
        return attr.impactAbsorptionDestroyed();
    }

    public float getImpactReduction() {
        return attr.impactReduction();
    }

    public float getImpactMultiplier() {
        return attr.impactMultiplier();
    }

    public boolean hasCollideBetweenParts() {
        return attr.collideBetweenParts();
    }

    List<ResourceLocation> getRequiredTags() {
        return attr.requiredTags();
    }

    public List<ResourceLocation> acceptableTags() {
        return attr.acceptableTags();
    }

    public List<ResourceLocation> forbiddenTags() {
        return attr.forbiddenTags();
    }

    public Map<String, JointAttr> getJointAttrs() {
        return attr.jointAttrs();
    }
}
