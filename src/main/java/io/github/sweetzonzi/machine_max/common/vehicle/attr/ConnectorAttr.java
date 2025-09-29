package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.PartType;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @param locatorName 对接口对应的Locator名称
 * @param type 对接口类型
 * @param requiredTags 对接口的必需标签
 * @param acceptableTags 对接口的可接受标签
 * @param forbiddenTags 对接口的禁止标签
 * @param jointAttrs 对接口的关节属性(限制，刚性与阻尼)
 * @param signalTargets 对接口的控制信号传输目标(子系统/对接口名/part/vehicle)
 * @param collideBetweenParts 对接口是否允许部件间碰撞
 * @param breakable 对接口是否可破坏(与内部零件相连接的对接口恒定不可破坏，不受此影响)
 * @param ConnectedTo 对接口默认连接到的部件内对接口名称(不是骨骼名！)
 */
public record ConnectorAttr(
        String locatorName,
        String type,
        List<String> requiredTags,
        List<String> acceptableTags,
        List<String> forbiddenTags,
        Map<String, JointAttr> jointAttrs,
        Map<String, List<String>> signalTargets,
        boolean collideBetweenParts,
        boolean breakable,
        String ConnectedTo
) {

    public static final Codec<Map<String, List<String>>> SIGNAL_TARGET_CODEC = Codec.unboundedMap(
            Codec.STRING,
            Codec.STRING.listOf()
    );

    public static final Codec<ConnectorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("locator").forGetter(ConnectorAttr::locatorName),
            Codec.STRING.fieldOf("type").forGetter(ConnectorAttr::type),
            Codec.STRING.listOf().optionalFieldOf("required_tags", List.of()).forGetter(ConnectorAttr::requiredTags),
            Codec.STRING.listOf().optionalFieldOf("acceptable_tags", List.of()).forGetter(ConnectorAttr::requiredTags),
            Codec.STRING.listOf().optionalFieldOf("forbidden_tags", List.of()).forGetter(ConnectorAttr::requiredTags),
            JointAttr.MAP_CODEC.optionalFieldOf("joint_attrs", Map.of()).forGetter(ConnectorAttr::jointAttrs),
            SIGNAL_TARGET_CODEC.optionalFieldOf("signal_targets", Map.of()).forGetter(ConnectorAttr::signalTargets),
            Codec.BOOL.optionalFieldOf("collide_between_parts", false).forGetter(ConnectorAttr::collideBetweenParts),
            Codec.BOOL.optionalFieldOf("breakable", false).forGetter(ConnectorAttr::breakable),
            Codec.STRING.optionalFieldOf("connected_to", "").forGetter(ConnectorAttr::ConnectedTo)
    ).apply(instance, ConnectorAttr::new));

    public static final Codec<Map<String, ConnectorAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//对接口名称
            ConnectorAttr.CODEC//对接口属性
    );

    public boolean conditionCheck(PartType partType, String variant){
        Set<String> tags = new HashSet<>(partType.tags);
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
