package io.github.sweetzonzi.machine_max.common.vehicle.attr;

import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public class VariantAttr {
    public final ResourceLocation icon; //图标路径
    public final List<String> tags; //部件标签
    @Getter
    public final ResourceLocation model; // 模型路径
    public final Map<String, ResourceLocation> textures; // 纹理名 -> 纹理
    public final ResourceLocation animations; // 状态 -> 动画
    public final Map<String, SubPartAttr> subParts; //子部件名称-子部件属性

    public static final ResourceLocation EMPTY_TEXTURE = ResourceLocation.withDefaultNamespace("missingno");
    public static final ResourceLocation EMPTY_ANIM = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty");

    public static final Codec<Map<String, ResourceLocation>> TEXTURES_CODEC = Codec.either(
            ResourceLocation.CODEC,
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
    ).xmap(
            either -> either.map(
                    texture -> Map.of("default", texture),
                    map -> map
            ),
            map -> {
                if (map.size() == 1 && map.containsKey("default")) {
                    return Either.left(map.get("default"));
                } else {
                    return Either.right(map);
                }
            }
    );

    public static final Codec<VariantAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("icon", EMPTY_TEXTURE).forGetter(VariantAttr::getIcon),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(VariantAttr::getTags),
            ResourceLocation.CODEC.fieldOf("model").forGetter(VariantAttr::getModel),
            TEXTURES_CODEC.optionalFieldOf("textures", Map.of("default", EMPTY_TEXTURE)).forGetter(VariantAttr::getTextures),
            ResourceLocation.CODEC.optionalFieldOf("animations", EMPTY_ANIM).forGetter(VariantAttr::getAnimations),
            SubPartAttr.MAP_CODEC.fieldOf("sub_parts").forGetter(VariantAttr::getSubParts)
    ).apply(instance, VariantAttr::new));

    public VariantAttr(ResourceLocation icon, List<String> tags, ResourceLocation model, Map<String, ResourceLocation> textures, ResourceLocation animations, Map<String, SubPartAttr> subParts) {
        this.icon = icon;
        this.tags = tags;
        this.model = model;
        this.textures = textures;
        this.animations = animations;
        this.subParts = subParts;
        OModel oModel = OModel.getOrEmpty(new ModelIndex("part", model));
        if (oModel.equals(OModel.Companion.getEMPTY())){
            throw new IllegalArgumentException(Component.translatable("error.machine_max.part.model_not_found", model.toString()).getString());
        }
        if (getTextures().isEmpty()) {
            throw new IllegalArgumentException(Component.translatable("error.machine_max.part.missing_textures").getString());
        }
        // 构建并缓存部件碰撞体积
        for (SubPartAttr subPartAttr : subParts.values()) {
            subPartAttr.getCollisionShape(this);
        }
    }

    /**
     * 获取可用纹理列表
     */
    public List<ResourceLocation> getTextureList() {
        return textures.values().stream().toList();
    }

    /**
     * 获取指定名称的纹理
     */
    public ResourceLocation getTexture(String name) {
        return textures.getOrDefault(name, EMPTY_TEXTURE);
    }

    @Nullable
    public Iterator<Pair<String, String>> getConnectorIterator() {
        Set<Pair<String, String>> connectors = new HashSet<>();
        for (Map.Entry<String, SubPartAttr> subParts : this.subParts.entrySet()) {//遍历零件
            String subPartName = subParts.getKey();
            SubPartAttr subPart = subParts.getValue();
            for (Map.Entry<String, ConnectorAttr> connector : subPart.connectors.entrySet()) {//遍历零件的接口
                if (connector.getValue().connectedTo().isEmpty())
                    connectors.add(Pair.of(subPartName, connector.getKey()));//外部接口加入可用接口集合
            }
        }
        if (!connectors.isEmpty()) return connectors.iterator();
        else return null;
    }

    /**
     * @return 部件所有外部连接点名称与对应的接口属性 The external connectors of the part and their corresponding locator attributes.
     */
    public Map<Pair<String, String>, ConnectorAttr> getPartOutwardConnectors() {
        Map<Pair<String, String>, ConnectorAttr> connectors = new HashMap<>(1);//获取部件所有外部连接点名称与类型
        for (Map.Entry<String, SubPartAttr> entry : this.subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPartAttr subPart = entry.getValue();
            for (Map.Entry<String, ConnectorAttr> entry1 : subPart.connectors.entrySet()) {
                if (entry1.getValue().connectedTo().isEmpty())//外部零件连接点
                    connectors.put(Pair.of(subPartName, entry1.getKey()), entry1.getValue());
            }
        }
        return connectors;
    }
}
