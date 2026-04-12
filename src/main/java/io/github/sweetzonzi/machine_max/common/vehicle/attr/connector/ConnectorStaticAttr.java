package io.github.sweetzonzi.machine_max.common.vehicle.attr.connector;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.util.data.Axis;

import java.util.List;
import java.util.Map;

/**
 * @param type 连接点类型
 * @param direction 连接点的法线方向
 * @param integrity 连接点结构完整性，受到大于此数值的伤害时会断开连接的关节
 * @param impactAbsorption 连接点受到冲击，但未超过剩余结构完整性即未能断开连接时，冲击转化为结构完整性损耗的比例，例如0.2表示20%的冲击会转化为结构完整性的损耗
 * @param impactReduction 连接点受到冲击时减少的冲击量
 * @param impactMultiplier 连接点受到冲击时的伤害倍率(与内部零件相连接的连接点恒定不可破坏，不受此影响)
 * @param collideBetweenParts 连接点是否允许部件间碰撞
 * @param requiredTags 连接点的必需标签
 * @param acceptableTags 连接点的可接受标签
 * @param forbiddenTags 连接点的禁止标签
 * @param jointAttrs 连接点的关节属性(限制，刚性与阻尼)
 */
public record ConnectorStaticAttr(
        String type,
        Axis direction,
        float integrity,
        float impactAbsorption,
        float impactReduction,
        float impactMultiplier,
        boolean collideBetweenParts,
        List<String> requiredTags,
        List<String> acceptableTags,
        List<String> forbiddenTags,
        Map<String, JointAttr> jointAttrs
) {
    public static final Codec<ConnectorStaticAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("type", "simple").forGetter(ConnectorStaticAttr::type),
            Axis.CODEC.optionalFieldOf("direction", Axis.YP).forGetter(ConnectorStaticAttr::direction),
            Codec.FLOAT.optionalFieldOf("integrity", 20f).forGetter(ConnectorStaticAttr::integrity),
            Codec.FLOAT.optionalFieldOf("impact_absorption", 0.2f).forGetter(ConnectorStaticAttr::impactAbsorption),
            Codec.FLOAT.optionalFieldOf("impact_reduction", 2f).forGetter(ConnectorStaticAttr::impactReduction),
            Codec.FLOAT.optionalFieldOf("impact_multiplier", 1.5f).forGetter(ConnectorStaticAttr::impactMultiplier),
            Codec.BOOL.optionalFieldOf("collide_between_parts", false).forGetter(ConnectorStaticAttr::collideBetweenParts),
            Codec.STRING.listOf().optionalFieldOf("required_tags", List.of()).forGetter(ConnectorStaticAttr::requiredTags),
            Codec.STRING.listOf().optionalFieldOf("acceptable_tags", List.of()).forGetter(ConnectorStaticAttr::requiredTags),
            Codec.STRING.listOf().optionalFieldOf("forbidden_tags", List.of()).forGetter(ConnectorStaticAttr::requiredTags),
            JointAttr.MAP_CODEC.optionalFieldOf("joint_attrs", Map.of()).forGetter(ConnectorStaticAttr::jointAttrs)
    ).apply(instance, ConnectorStaticAttr::new));
}
