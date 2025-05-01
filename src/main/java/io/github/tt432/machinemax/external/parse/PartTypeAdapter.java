package io.github.tt432.machinemax.external.parse;

import com.google.common.reflect.TypeToken;
import com.google.gson.*;
import io.github.tt432.machinemax.common.vehicle.PartType;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.common.vehicle.attr.subsystem.AbstractSubsystemAttr;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public class PartTypeAdapter implements JsonDeserializer<PartType> {
    @Override
    public PartType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        JsonObject jsonObject = json.getAsJsonObject();

        // 解析基础字段
        String name = jsonObject.get("name").getAsString();
        Map<String, ResourceLocation> variants = parseVariants(jsonObject.get("variants"), context);
        List<ResourceLocation> textures = parseTextures(jsonObject.get("textures"), context);
        ResourceLocation animation = parseAnimation(jsonObject.get("animation"), context);
        float basicDurability = jsonObject.get("basic_durability").getAsFloat();
        Map<String, AbstractSubsystemAttr> subsystems = parseSubsystems(jsonObject.get("subsystems"), context);
        Map<String, SubPartAttr> subParts = parseSubParts(jsonObject.get("sub_parts"), context);

        // 调用构造函数生成 registryKey
        return new PartType(
            name,
            variants,
            textures,
            animation,
            basicDurability,
            subsystems,
            subParts
        );
    }

    private Map<String, ResourceLocation> parseVariants(JsonElement element, JsonDeserializationContext context) {
        return context.deserialize(element, new TypeToken<Map<String, ResourceLocation>>(){}.getType());
    }

    private List<ResourceLocation> parseTextures(JsonElement element, JsonDeserializationContext context) {
        return context.deserialize(element, new TypeToken<List<ResourceLocation>>(){}.getType());
    }

    private ResourceLocation parseAnimation(JsonElement element, JsonDeserializationContext context) {
        return context.deserialize(element, ResourceLocation.class);
    }

    private Map<String, AbstractSubsystemAttr> parseSubsystems(JsonElement element, JsonDeserializationContext context) {
        return context.deserialize(element, new TypeToken<Map<String, AbstractSubsystemAttr>>(){}.getType());
    }

    private Map<String, SubPartAttr> parseSubParts(JsonElement element, JsonDeserializationContext context) {
        return context.deserialize(element, new TypeToken<Map<String, SubPartAttr>>(){}.getType());
    }
}