package io.github.tt432.machinemax.common.vehicle.attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;

public record ConnectorAttr(
        String locatorName,//对接口对应的Locator名称
        String type,//对接口类型
        //TODO:关节属性(限制，驱动，刚性与阻尼等)
        List<String> acceptableVariants,//对接口接受的部件变体类型列表(左，右等)
        boolean breakable,//对接口是否可破坏(与内部零件相连接的对接口恒定不可破坏，不受此影响)
//        List<TagKey<Part>> acceptablePartTags,
        String ConnectedTo//对接口默认连接到的部件内对接口名称(不是骨骼名！)
) {
    public static final Codec<ConnectorAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("locator").forGetter(ConnectorAttr::locatorName),
            Codec.STRING.fieldOf("type").forGetter(ConnectorAttr::type),
            Codec.STRING.listOf().optionalFieldOf("variant", List.of()).forGetter(ConnectorAttr::acceptableVariants),
            Codec.BOOL.fieldOf("breakable").forGetter(ConnectorAttr::breakable),
            Codec.STRING.optionalFieldOf("connected_to", "").forGetter(ConnectorAttr::ConnectedTo)
    ).apply(instance, ConnectorAttr::new));

    public static final Codec<Map<String, ConnectorAttr>> MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,//对接口名称
            ConnectorAttr.CODEC//对接口属性
    );
}
