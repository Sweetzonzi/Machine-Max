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
    public final Map<String, ResourceLocation> models; // 状态 -> 模型路径
    public final Map<String, List<ResourceLocation>> textures; // 状态 -> 纹理
    public final Map<String, ResourceLocation> animations; // 状态 -> 动画
    public final Map<String, SubPartAttr> subParts; //子部件名称-子部件属性
    // 编解码器 - 支持单值或映射
    public static final Codec<Map<String, ResourceLocation>> MODELS_CODEC = Codec.either(
            ResourceLocation.CODEC,
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
    ).xmap(
            either -> either.map(
                    model -> Map.of("default", model),
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

    public static final Codec<Map<String, List<ResourceLocation>>> TEXTURES_CODEC = Codec.either(
            ResourceLocation.CODEC.listOf(),
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC.listOf())
    ).xmap(
            either -> either.map(
                    textures -> Map.of("default", textures),
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

    public static final Codec<Map<String, ResourceLocation>> ANIMATIONS_CODEC = Codec.either(
            ResourceLocation.CODEC,
            Codec.unboundedMap(Codec.STRING, ResourceLocation.CODEC)
    ).xmap(
            either -> either.map(
                    anim -> Map.of("default", anim),
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
            ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("missingno")).forGetter(VariantAttr::getIcon),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(VariantAttr::getTags),
            MODELS_CODEC.fieldOf("models").forGetter(VariantAttr::getModels),
            TEXTURES_CODEC.optionalFieldOf("textures", Map.of()).forGetter(VariantAttr::getTextures),
            ANIMATIONS_CODEC.optionalFieldOf("animations", Map.of()).forGetter(VariantAttr::getAnimations),
            SubPartAttr.MAP_CODEC.fieldOf("sub_parts").forGetter(VariantAttr::getSubParts)
    ).apply(instance, VariantAttr::new));

    public VariantAttr(ResourceLocation icon, List<String> tags, Map<String, ResourceLocation> models, Map<String, List<ResourceLocation>> textures, Map<String, ResourceLocation> animations, Map<String, SubPartAttr> subParts) {
        this.icon = icon;
        this.tags = tags;
        this.models = models;
        this.textures = textures;
        this.animations = animations;
        this.subParts = subParts;
        for (ResourceLocation modelPath : models.values()) {
            OModel oModel = OModel.getOrEmpty(new ModelIndex("part", modelPath));
            if (oModel.equals(OModel.Companion.getEMPTY())){
                throw new IllegalArgumentException(Component.translatable("error.machine_max.part.model_not_found", modelPath.toString()).getString());
            }
        }
    }

    /**
     * 获取指定状态的模型
     */
    public ResourceLocation getModel(String state) {
        return models.getOrDefault(state, models.get("default"));
    }

    /**
     * 获取指定状态的纹理
     */
    public List<ResourceLocation> getTextures(String state) {
        return textures.getOrDefault(state, textures.getOrDefault("default", List.of()));
    }

    /**
     * 获取指定状态的动画
     */
    public ResourceLocation getAnimation(String state) {
        return animations.getOrDefault(state, animations.getOrDefault("default",
                ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty")));
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
