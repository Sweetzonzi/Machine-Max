package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import io.github.sweetzonzi.machine_max.common.mech.part.AbstractPartData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.RegularPart;
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
public class RegularPartData extends AbstractPartData {
    public final float assemblingProgress;//部件的组装进度
    public final int materialAssemblingProgress;//部件的材料供给进度
    public final boolean renderWireframe;//部件是否渲染线框

    public static final Codec<RegularPartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registry_key").forGetter(RegularPartData::getRegistryKey),
            Codec.STRING.fieldOf("name").forGetter(RegularPartData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(RegularPartData::getUuid),
            Codec.STRING.fieldOf("variant").forGetter(RegularPartData::getVariant),
            Codec.FLOAT.optionalFieldOf("assembling_progress", 1f).forGetter(RegularPartData::getAssemblingProgress),
            Codec.FLOAT.optionalFieldOf("shared_durability_ratio", 1f).forGetter(RegularPartData::getSharedDurabilityRatio),
            Codec.INT.optionalFieldOf("material_assembling_progress", 99999).forGetter(RegularPartData::getMaterialAssemblingProgress),
            Codec.BOOL.optionalFieldOf("render_wireframe", true).forGetter(RegularPartData::isRenderWireframe),
            Codec.STRING.optionalFieldOf("texture_name", "default").forGetter(RegularPartData::getTextureName),
            SubPartData.MAP_CODEC.fieldOf("sub_parts").forGetter(RegularPartData::getSubParts)
    ).apply(instance, RegularPartData::new));

    public static final Codec<Map<String, RegularPartData>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, RegularPartData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull RegularPartData decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            String name = buffer.readUtf();
            String uuid = buffer.readUtf();
            String variant = buffer.readUtf();
            float assemblingProgress = buffer.readFloat();
            float sharedDurabilityRatio = buffer.readFloat();
            int materialAssemblingProgress = buffer.readInt();
            boolean renderWireframe = buffer.readBoolean();
            String textureName = buffer.readUtf();
            var subParts = SubPartData.MAP_STREAM_CODEC.decode(buffer);
            return new RegularPartData(registryKey, name, uuid, variant, assemblingProgress, sharedDurabilityRatio, materialAssemblingProgress, renderWireframe, textureName, subParts);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull RegularPartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeUtf(value.variant);
            buffer.writeFloat(value.assemblingProgress);
            buffer.writeFloat(value.sharedDurabilityRatio);
            buffer.writeInt(value.materialAssemblingProgress);
            buffer.writeBoolean(value.renderWireframe);
            buffer.writeUtf(value.textureName);
            SubPartData.MAP_STREAM_CODEC.encode(buffer, value.subParts);
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, Map<String, RegularPartData>> MAP_STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull Map<String, RegularPartData> decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readVarInt();
            var map = new HashMap<String, RegularPartData>();
            for (int i = 0; i < size; i++) {
                String key = buffer.readUtf();
                RegularPartData partData = STREAM_CODEC.decode(buffer);
                map.put(key, partData);
            }
            return map;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull Map<String, RegularPartData> value) {
            buffer.writeVarInt(value.size());
            for (Map.Entry<String, RegularPartData> entry : value.entrySet()) {
                buffer.writeUtf(entry.getKey());
                STREAM_CODEC.encode(buffer, entry.getValue());
            }
        }
    };


    public RegularPartData(
            ResourceLocation registryKey,
            String name,
            String uuid,
            String variant,
            float assemblingProgress,
            float sharedDurabilityRatio,
            int materialAssemblingProgress,
            boolean renderWireframe,
            String textureName,
            Map<String, SubPartData> subParts) {
        super(registryKey, name, uuid, variant, sharedDurabilityRatio, textureName, subParts);
        this.assemblingProgress = assemblingProgress;
        this.materialAssemblingProgress = materialAssemblingProgress;
        this.renderWireframe = renderWireframe;
    }

    /**
     * 将部件数据打包为方便传输与读取的PartData
     *
     * @param part 部件实例
     */
    public RegularPartData(RegularPart part) {
        super(part);
        this.assemblingProgress = part.assemblingProgress;
        this.materialAssemblingProgress = part.materialProgress;
        this.renderWireframe = part.renderWireframe;
    }
}
