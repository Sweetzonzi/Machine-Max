package io.github.sweetzonzi.machine_max.common.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
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
    public final int materialAssemblingProgress;//部件的材料供给进度
    public final Map<String, SubPartData> subParts;//尚存的零件数据

    public static final Codec<PartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registry_key").forGetter(PartData::getRegistryKey),
            Codec.STRING.fieldOf("name").forGetter(PartData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(PartData::getUuid),
            Codec.STRING.fieldOf("variant").forGetter(PartData::getVariant),
            ResourceLocation.CODEC.optionalFieldOf("custom_recipe", FabricatingRecipe.EMPTY).forGetter(PartData::getCustomRecipe),
            Codec.FLOAT.optionalFieldOf("assembling_progress", 1f).forGetter(PartData::getAssemblingProgress),
            Codec.INT.optionalFieldOf("material_assembling_progress", 99999).forGetter(PartData::getMaterialAssemblingProgress),
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
            int materialAssemblingProgress = buffer.readInt();
            var subParts = SubPartData.MAP_STREAM_CODEC.decode(buffer);
            return new PartData(registryKey, name, uuid, variant, customRecipe, assemblingProgress, materialAssemblingProgress, subParts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull PartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeUtf(value.variant);
            buffer.writeResourceLocation(value.customRecipe);
            buffer.writeFloat(value.assemblingProgress);
            buffer.writeInt(value.materialAssemblingProgress);
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
            int materialAssemblingProgress,
            Map<String, SubPartData> subParts) {
        this.registryKey = registryKey;
        this.name = name;
        this.uuid = uuid;
        this.variant = variant;
        this.customRecipe = customRecipe;
        this.assemblingProgress = assemblingProgress;
        this.materialAssemblingProgress = materialAssemblingProgress;
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
        this.customRecipe = part.customRecipe == null ? FabricatingRecipe.EMPTY : part.customRecipe;
        this.assemblingProgress = part.assemblingProgress;
        this.materialAssemblingProgress = part.materialProgress;
        this.subParts = new HashMap<>();
        for (Map.Entry<String, SubPart> entry : part.subParts.entrySet()) {
            subParts.put(entry.getKey(), new SubPartData(entry.getValue()));
        }
    }
}
