package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public record VariantAttr(
        ResourceLocation icon, //图标路径
        List<String> tags, //部件标签
        Map<String, SubPartAttr> subParts //子部件名称-子部件属性
) {
    public static final Codec<VariantAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("icon").forGetter(VariantAttr::icon),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(VariantAttr::tags),
            SubPartAttr.MAP_CODEC.fieldOf("sub_parts").forGetter(VariantAttr::subParts)
    ).apply(instance, VariantAttr::new));

    public Iterator<Pair<String, String>> getConnectorIterator() {
        Set<Pair<String, String>> connectors = new HashSet<>();
        for (Map.Entry <String, SubPartAttr> subParts : this.subParts.entrySet()) {//遍历零件
            String subPartName = subParts.getKey();
            SubPartAttr subPart = subParts.getValue();
            for (Map.Entry<String, ConnectorAttr> connector : subPart.connectors.entrySet()) {//遍历零件的接口
                if (connector.getValue().ConnectedTo().isEmpty()) connectors.add(Pair.of(subPartName, connector.getKey()));//外部接口加入可用接口集合
            }
        }
        return connectors.iterator();
    }

    /**
     * @return 部件所有外部对接口名称与对应的接口属性 The external connectors of the part and their corresponding locator attributes.
     */
    public Map<Pair<String, String>, ConnectorAttr> getPartOutwardConnectors() {
        Map<Pair<String, String>, ConnectorAttr> connectors = new HashMap<>(1);//获取部件所有外部对接口名称与类型
        for (Map.Entry<String, SubPartAttr> entry : this.subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPartAttr subPart = entry.getValue();
            for (Map.Entry<String, ConnectorAttr> entry1 : subPart.connectors.entrySet()) {
                if (entry1.getValue().ConnectedTo().isEmpty())//外部零件对接口
                    connectors.put(Pair.of(subPartName, entry1.getKey()), entry1.getValue());
            }
        }
        return connectors;
    }
}
