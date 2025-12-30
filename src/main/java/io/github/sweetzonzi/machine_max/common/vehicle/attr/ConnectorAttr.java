package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;
import io.github.sweetzonzi.machine_max.util.data.Axis;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @param locatorName 连接点对应的Locator名称
 * @param type 连接点类型
 * @param normal 连接点的法线方向
 * @param impactReduction 连接点受到冲击时减少的冲击量
 * @param impactMultiplier 连接点受到冲击时的伤害倍率(与内部零件相连接的连接点恒定不可破坏，不受此影响)
 * @param integrity 连接点结构完整性，受到大于此数值的伤害时会断开连接的关节
 * @param requiredTags 连接点的必需标签
 * @param acceptableTags 连接点的可接受标签
 * @param forbiddenTags 连接点的禁止标签
 * @param jointAttrs 连接点的关节属性(限制，刚性与阻尼)
 * @param signalTranslations 从对侧连接点接收到的信号频道转译规则(channel_a->channel_c; channel_b->channel_c)
 * @param signalTargets 连接点在本零件内的控制信号传输目标(子系统/连接点名/part/vehicle)
 * @param collideBetweenParts 连接点是否允许部件间碰撞
 * @param connectedTo 连接点默认连接到的部件内连接点名称(不是骨骼名！)
 */
public record ConnectorAttr(
        String locatorName,
        String type,
        Axis normal,
        float integrity,
        float impactReduction,
        float impactMultiplier,
        List<String> requiredTags,
        List<String> acceptableTags,
        List<String> forbiddenTags,
        Map<String, JointAttr> jointAttrs,
        Map<String, String> signalTranslations,
        Map<String, List<String>> signalTargets,
        boolean collideBetweenParts,
        String connectedTo
) {

    public static final Codec<Map<String, List<String>>> SIGNAL_TARGETS_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<ConnectorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("locator").forGetter(ConnectorAttr::locatorName),
            Codec.STRING.fieldOf("type").forGetter(ConnectorAttr::type),
            Axis.CODEC.optionalFieldOf("normal", Axis.YP).forGetter(ConnectorAttr::normal),
            Codec.FLOAT.optionalFieldOf("integrity", 20f).forGetter(ConnectorAttr::integrity),
            Codec.FLOAT.optionalFieldOf("impact_reduction", 2f).forGetter(ConnectorAttr::impactReduction),
            Codec.FLOAT.optionalFieldOf("impact_multiplier", 1.5f).forGetter(ConnectorAttr::impactMultiplier),
            Codec.STRING.listOf().optionalFieldOf("required_tags", List.of()).forGetter(ConnectorAttr::requiredTags),
            Codec.STRING.listOf().optionalFieldOf("acceptable_tags", List.of()).forGetter(ConnectorAttr::requiredTags),
            Codec.STRING.listOf().optionalFieldOf("forbidden_tags", List.of()).forGetter(ConnectorAttr::requiredTags),
            JointAttr.MAP_CODEC.optionalFieldOf("joint_attrs", Map.of()).forGetter(ConnectorAttr::jointAttrs),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("signal_translations", Map.of()).forGetter(ConnectorAttr::signalTranslations),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("signal_targets", Map.of()).forGetter(ConnectorAttr::signalTargets),
            Codec.BOOL.optionalFieldOf("collide_between_parts", false).forGetter(ConnectorAttr::collideBetweenParts),
            Codec.STRING.optionalFieldOf("connected_to", "").forGetter(ConnectorAttr::connectedTo)
    ).apply(instance, ConnectorAttr::new));

    public static final Codec<Map<String, ConnectorAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//连接点名称
            ConnectorAttr.CODEC//连接点属性
    );

    public boolean conditionCheck(PartType partType, String variant){
        Set<String> tags = new HashSet<>(partType.getVariant(variant).getTags());
        tags.add("variant:" + variant);
        //检查必须拥有的tag情况(必须全都有)
        if (this.requiredTags().isEmpty() || tags.containsAll(this.requiredTags())) {
            boolean hasAcceptableTags = false;
            for (String acceptableTag : this.acceptableTags()) {
                if (tags.contains(acceptableTag)) {
                    hasAcceptableTags = true;
                    break;
                }
            }
            //检查可接受的tag情况(有一个符合要求即可)
            if (this.acceptableTags().isEmpty() || hasAcceptableTags) {
                boolean hasForbiddenTags = false;
                for (String forbiddenTag : this.forbiddenTags()) {
                    if (tags.contains(forbiddenTag)) {
                        hasForbiddenTags = true;
                        break;
                    }
                }
                return this.forbiddenTags().isEmpty() || !hasForbiddenTags;
            } else return false;
        } else return false;
    }
}
