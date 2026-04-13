package io.github.sweetzonzi.machine_max.common.vehicle.attr.connector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public class ConnectorAttr {
    /**
     * 连接点对应的Locator名称
     */
    public final String locatorName;
    /**
     * 连接点属性定义
     */
    public final ResourceLocation definition;
    /**
     * 连接点属性覆写
     */
    @Nullable
    public final OverwriteAttr overwrite;
    /**
     * 从对侧连接点接收到的信号频道转译规则(channel_a->channel_c; channel_b->channel_c)
     */
    public final Map<String, String> signalTranslations;
    /**
     * 连接点在本零件内的控制信号传输目标(子系统/连接点名/subpart/vehicle)
     */
    public final Map<String, List<String>> signalTargets;
    /**
     * 是否为部件内部连接点（不参与对外拼装）
     */
    public final boolean internal;
    /**
     * 连接点最终属性，考虑用户自定义覆写
     */
    private final ConnectorStaticAttr attr;

    public ConnectorAttr(String locatorName, ResourceLocation definition, @Nullable OverwriteAttr overwrite, Map<String, String> signalTranslations, Map<String, List<String>> signalTargets, boolean internal) {
        this.locatorName = locatorName;
        this.definition = definition;
        this.overwrite = overwrite;
        this.signalTranslations = signalTranslations;
        this.signalTargets = signalTargets;
        this.internal = internal;
        this.attr = getEffectiveAttr();
    }

    /**
     * @param type                连接点类型
     * @param direction           连接点的法线方向
     * @param integrity           连接点结构完整性，受到大于此数值的伤害时会断开连接的关节
     * @param impactAbsorption    连接点受到冲击，但未超过剩余结构完整性即未能断开连接时，冲击转化为结构完整性损耗的比例，例如0.2表示20%的冲击会转化为结构完整性的损耗
     * @param impactReduction     连接点受到冲击时减少的冲击量
     * @param impactMultiplier    连接点受到冲击时的伤害倍率(与内部零件相连接的连接点恒定不可破坏，不受此影响)
     * @param collideBetweenParts 连接点是否允许部件间碰撞
     * @param requiredTags        连接点的必需标签
     * @param acceptableTags      连接点的可接受标签
     * @param forbiddenTags       连接点的禁止标签
     * @param jointAttrs          连接点的关节属性(限制，刚性与阻尼)
     */
    public record OverwriteAttr(
            Optional<String> type,
            Optional<Axis> direction,
            Optional<Float> integrity,
            Optional<Float> impactAbsorption,
            Optional<Float> impactReduction,
            Optional<Float> impactMultiplier,
            Optional<Boolean> collideBetweenParts,
            Optional<List<String>> requiredTags,
            Optional<List<String>> acceptableTags,
            Optional<List<String>> forbiddenTags,
            Optional<Map<String, JointAttr>> jointAttrs
    ) {
        public static final Codec<OverwriteAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.optionalFieldOf("type").forGetter(OverwriteAttr::type),
                Axis.CODEC.optionalFieldOf("direction").forGetter(OverwriteAttr::direction),
                Codec.FLOAT.optionalFieldOf("integrity").forGetter(OverwriteAttr::integrity),
                Codec.FLOAT.optionalFieldOf("impact_absorption").forGetter(OverwriteAttr::impactAbsorption),
                Codec.FLOAT.optionalFieldOf("impact_reduction").forGetter(OverwriteAttr::impactReduction),
                Codec.FLOAT.optionalFieldOf("impact_multiplier").forGetter(OverwriteAttr::impactMultiplier),
                Codec.BOOL.optionalFieldOf("collide_between_parts").forGetter(OverwriteAttr::collideBetweenParts),
                Codec.STRING.listOf().optionalFieldOf("required_tags").forGetter(OverwriteAttr::requiredTags),
                Codec.STRING.listOf().optionalFieldOf("acceptable_tags").forGetter(OverwriteAttr::acceptableTags),
                Codec.STRING.listOf().optionalFieldOf("forbidden_tags").forGetter(OverwriteAttr::forbiddenTags),
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
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("signal_translations", Map.of()).forGetter(ConnectorAttr::getSignalTranslations),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("signal_targets", Map.of()).forGetter(ConnectorAttr::getSignalTargets),
            Codec.BOOL.optionalFieldOf("internal", false).forGetter(ConnectorAttr::isInternal)
    ).apply(instance, (
            locator,
            definition,
            overwrite,
            signalTranslations,
            signalTargets,
            internal
    ) -> new ConnectorAttr(locator, definition, overwrite.orElse(null), signalTranslations, signalTargets, internal)));

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

    public boolean conditionCheck(PartType partType, String variant) {
        if (MMServerConfig.ignoreAssemblyTagRequirements()) return true; // 检查服务端配置，视情况忽略配方要求
        Set<String> tags = new HashSet<>(partType.getVariant(variant).getTags());
        var effectiveAttr = getEffectiveAttr();
        //检查必须拥有的tag情况(必须全都有)
        if (effectiveAttr.requiredTags().isEmpty() || tags.containsAll(effectiveAttr.requiredTags())) {
            boolean hasAcceptableTags = false;
            for (String acceptableTag : effectiveAttr.acceptableTags()) {
                if (tags.contains(acceptableTag)) {
                    hasAcceptableTags = true;
                    break;
                }
            }
            //检查可接受的tag情况(有一个符合要求即可)
            if (effectiveAttr.acceptableTags().isEmpty() || hasAcceptableTags) {
                boolean hasForbiddenTags = false;
                for (String forbiddenTag : effectiveAttr.forbiddenTags()) {
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
                overwrite.impactReduction().isPresent() ? overwrite.impactReduction().get() : baseAttr.impactReduction(),
                overwrite.impactMultiplier().isPresent() ? overwrite.impactMultiplier().get() : baseAttr.impactMultiplier(),
                overwrite.collideBetweenParts().isPresent() ? overwrite.collideBetweenParts().get() : baseAttr.collideBetweenParts(),
                overwrite.requiredTags().isPresent() ? overwrite.requiredTags().get() : baseAttr.requiredTags(),
                overwrite.acceptableTags().isPresent() ? overwrite.acceptableTags().get() : baseAttr.acceptableTags(),
                overwrite.forbiddenTags().isPresent() ? overwrite.forbiddenTags().get() : baseAttr.forbiddenTags(),
                overwrite.jointAttrs().isPresent() ? overwrite.jointAttrs().get() : baseAttr.jointAttrs()
        );
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

    public float getImpactReduction() {
        return attr.impactReduction();
    }

    public float getImpactMultiplier() {
        return attr.impactMultiplier();
    }

    public boolean hasCollideBetweenParts() {
        return attr.collideBetweenParts();
    }

    List<String> getRequiredTags() {
        return attr.requiredTags();
    }

    public List<String> acceptableTags() {
        return attr.acceptableTags();
    }

    public List<String> forbiddenTags() {
        return attr.forbiddenTags();
    }

    public Map<String, JointAttr> getJointAttrs() {
        return attr.jointAttrs();
    }
}
