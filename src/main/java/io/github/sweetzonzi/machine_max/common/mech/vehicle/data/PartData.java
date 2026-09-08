package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于保存部件动态数据(位置，耐久度等)
 */
@Getter
public class PartData {
    public final ResourceLocation registryKey;//注册表键
    public final String name;//部件的名称
    public final String uuid;//部件的UUID
    public final String variant;//部件的变体
    public final ResourceLocation customRecipe;//部件的自定义配方
    public final float assemblingProgress;//部件的组装进度
    public final float sharedDurabilityRatio;//共享耐久度比例（0~1，仅 shareDurability 部件有效）
    public final int materialAssemblingProgress;//部件的材料供给进度
    public final boolean renderWireframe;//部件是否渲染线框
    public final String textureName;//部件的涂装纹理名（Part 级统一）
    public final Map<String, SubPartData> subParts;//尚存的零件数据

    public static final Codec<PartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registry_key").forGetter(PartData::getRegistryKey),
            Codec.STRING.fieldOf("name").forGetter(PartData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(PartData::getUuid),
            Codec.STRING.fieldOf("variant").forGetter(PartData::getVariant),
            ResourceLocation.CODEC.optionalFieldOf("custom_recipe", FabricatingRecipe.EMPTY).forGetter(PartData::getCustomRecipe),
            Codec.FLOAT.optionalFieldOf("assembling_progress", 1f).forGetter(PartData::getAssemblingProgress),
            Codec.FLOAT.optionalFieldOf("shared_durability_ratio", 1f).forGetter(PartData::getSharedDurabilityRatio),
            Codec.INT.optionalFieldOf("material_assembling_progress", 99999).forGetter(PartData::getMaterialAssemblingProgress),
            Codec.BOOL.optionalFieldOf("render_wireframe", true).forGetter(PartData::isRenderWireframe),
            Codec.STRING.optionalFieldOf("texture_name", "default").forGetter(PartData::getTextureName),
            SubPartData.MAP_CODEC.fieldOf("sub_parts").forGetter(PartData::getSubParts)
    ).apply(instance, PartData::new));

    public static final Codec<Map<String, PartData>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, PartData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull PartData decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            String name = buffer.readUtf();
            String uuid = buffer.readUtf();
            String variant = buffer.readUtf();
            ResourceLocation customRecipe = buffer.readResourceLocation();
            float assemblingProgress = buffer.readFloat();
            float sharedDurabilityRatio = buffer.readFloat();
            int materialAssemblingProgress = buffer.readInt();
            boolean renderWireframe = buffer.readBoolean();
            String textureName = buffer.readUtf();
            var subParts = SubPartData.MAP_STREAM_CODEC.decode(buffer);
            return new PartData(registryKey, name, uuid, variant, customRecipe, assemblingProgress, sharedDurabilityRatio, materialAssemblingProgress, renderWireframe, textureName, subParts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull PartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeUtf(value.variant);
            buffer.writeResourceLocation(value.customRecipe);
            buffer.writeFloat(value.assemblingProgress);
            buffer.writeFloat(value.sharedDurabilityRatio);
            buffer.writeInt(value.materialAssemblingProgress);
            buffer.writeBoolean(value.renderWireframe);
            buffer.writeUtf(value.textureName);
            SubPartData.MAP_STREAM_CODEC.encode(buffer, value.subParts);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, Map<String, PartData>> MAP_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull Map<String, PartData> decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            var map = new HashMap<String, PartData>();
            for (int i = 0; i < size; i++) {
                String key = buffer.readUtf();
                PartData partData = STREAM_CODEC.decode(buffer);
                map.put(key, partData);
            }
            return map;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull Map<String, PartData> value) {
            buffer.writeVarInt(value.size());
            for (Map.Entry<String, PartData> entry : value.entrySet()) {
                buffer.writeUtf(entry.getKey());
                STREAM_CODEC.encode(buffer, entry.getValue());
            }
        }
    };


    public PartData(
            ResourceLocation registryKey,
            String name,
            String uuid,
            String variant,
            ResourceLocation customRecipe,
            float assemblingProgress,
            float sharedDurabilityRatio,
            int materialAssemblingProgress,
            boolean renderWireframe,
            String textureName,
            Map<String, SubPartData> subParts) {
        this.registryKey = registryKey;
        this.name = name;
        this.uuid = uuid;
        this.variant = variant;
        this.customRecipe = customRecipe;
        this.assemblingProgress = assemblingProgress;
        this.sharedDurabilityRatio = sharedDurabilityRatio;
        this.materialAssemblingProgress = materialAssemblingProgress;
        this.renderWireframe = renderWireframe;
        this.textureName = textureName;
        this.subParts = subParts;
    }

    /**
     * 将部件数据打包为方便传输与读取的PartData
     *
     * @param part 部件实例
     */
    public PartData(Part part) {
        this.registryKey = part.type.getRegistryKey();
        this.name = part.name;
        this.uuid = part.uuid.toString();
        this.variant = part.variantName;
        this.customRecipe = part.customRecipe;
        this.assemblingProgress = part.assemblingProgress;
        // 共享耐久以比例持久化；非共享部件固定写入 1
        float sharedMaxDurability = part.getSharedMaxDurability();
        this.sharedDurabilityRatio = (part.type.shareDurability && sharedMaxDurability > 0f)
                ? Math.clamp(part.getSharedDurability() / sharedMaxDurability, 0f, 1f)
                : 1f;
        this.materialAssemblingProgress = part.materialProgress;
        this.renderWireframe = part.renderWireframe;
        this.textureName = part.textureName;
        this.subParts = new HashMap<>();
        for (Map.Entry<String, SubPart> entry : part.subParts.entrySet()) {
            subParts.put(entry.getKey(), new SubPartData(entry.getValue()));
        }
    }
}
