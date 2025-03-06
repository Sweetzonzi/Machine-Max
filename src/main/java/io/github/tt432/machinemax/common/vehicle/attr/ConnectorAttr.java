package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.util.data.KeyInputMapping;
import lombok.Getter;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * @param locatorName 对接口对应的Locator名称
 * @param type 对接口类型
 * @param acceptableVariants 对接口接受的部件变体类型列表(左，右等)，留空时表示接受所有部件变体类型
 * @param jointAttrs 对接口的关节属性(限制，刚性与阻尼)
 * @param portAttr 对接口的资源/信号传输端口属性
 * @param collideBetweenParts 对接口是否允许部件间碰撞
 * @param breakable 对接口是否可破坏(与内部零件相连接的对接口恒定不可破坏，不受此影响)
 * @param ConnectedTo 对接口默认连接到的部件内对接口名称(不是骨骼名！)
 */
public record ConnectorAttr(
        String locatorName,
        String type,
        List<String> acceptableVariants,
//        List<TagKey<Part>> acceptablePartTags,
        Map<String, JointAttr> jointAttrs,
        @Nullable PortAttr portAttr,
        boolean collideBetweenParts,
        boolean breakable,
        String ConnectedTo
) {
    public static final Codec<ConnectorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("locator").forGetter(ConnectorAttr::locatorName),
            Codec.STRING.fieldOf("type").forGetter(ConnectorAttr::type),
            Codec.STRING.listOf().optionalFieldOf("variant", List.of()).forGetter(ConnectorAttr::acceptableVariants),
            JointAttr.MAP_CODEC.optionalFieldOf("joint_attrs", Map.of()).forGetter(ConnectorAttr::jointAttrs),
            PortAttr.CODEC.optionalFieldOf("port").forGetter(c -> Optional.ofNullable(c.portAttr)),
            Codec.BOOL.optionalFieldOf("collide_between_parts", false).forGetter(ConnectorAttr::collideBetweenParts),
            Codec.BOOL.optionalFieldOf("breakable", false).forGetter(ConnectorAttr::breakable),
            Codec.STRING.optionalFieldOf("connected_to", "").forGetter(ConnectorAttr::ConnectedTo)
    ).apply(instance, (name, type, variants, jointAttrs, portAttr, collide, breakable, connectedTo) -> new ConnectorAttr(
            name,
            type,
            variants,
            jointAttrs,
            portAttr.orElse(null),
            collide,
            breakable,
            connectedTo
    )));

    public static final Codec<Map<String, ConnectorAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//对接口名称
            ConnectorAttr.CODEC//对接口属性
    );
}
