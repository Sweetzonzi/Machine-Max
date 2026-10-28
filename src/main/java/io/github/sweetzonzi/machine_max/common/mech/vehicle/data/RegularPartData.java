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
 * 普通部件的动态存档 DTO，包含公共实例状态与制造进度。
 *
 * <p>同一份 DTO 可用于收纳物品的完整恢复或蓝图的骨架恢复；是否采用库存、损伤和制造进度
 * 由 {@link io.github.sweetzonzi.machine_max.common.mech.part.PartRestoreMode} 决定，不能由数据是否归一化推断。</p>
 * <p>JSON 和网络编码均按普通部件的固定布局处理，不含扩展种类判别字段。
 * 通用存档入口应经 {@link io.github.sweetzonzi.machine_max.common.mech.part.PartFactory} 选择注册的专用 Codec。</p>
 */
@Getter
public class RegularPartData extends AbstractPartData {
    public final float assemblingProgress;//部件的组装进度
    public final int materialAssemblingProgress;//部件的材料供给进度
    public final boolean assembling;//部件是否仍处于组装形态（骨架）

    /**
     * 普通部件 JSON 存档 Codec，保留可选制造与涂装字段的默认值。
     * <p>解码只生成 DTO，不要求定义已经发布，也不分配刚体；实际恢复前由工厂预检型号、变体和 UUID。</p>
     */
    public static final Codec<RegularPartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registry_key").forGetter(RegularPartData::getRegistryKey),
            Codec.STRING.fieldOf("name").forGetter(RegularPartData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(RegularPartData::getUuid),
            Codec.STRING.fieldOf("variant").forGetter(RegularPartData::getVariant),
            Codec.FLOAT.optionalFieldOf("assembling_progress", 1f).forGetter(RegularPartData::getAssemblingProgress),
            Codec.FLOAT.optionalFieldOf("shared_durability_ratio", 1f).forGetter(RegularPartData::getSharedDurabilityRatio),
            Codec.INT.optionalFieldOf("material_assembling_progress", 99999).forGetter(RegularPartData::getMaterialAssemblingProgress),
            Codec.BOOL.optionalFieldOf("assembling", false).forGetter(RegularPartData::isAssembling),
            Codec.STRING.optionalFieldOf("texture_name", "default").forGetter(RegularPartData::getTextureName),
            SubPartData.MAP_CODEC.fieldOf("sub_parts").forGetter(RegularPartData::getSubParts)
    ).apply(instance, RegularPartData::new));

    /**
     * 按调用方字符串键保存普通部件 DTO；键与 DTO 内部 UUID 是否对应由装配体入口处理。
     */
    public static final Codec<Map<String, RegularPartData>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC);

    /**
     * 单个普通部件的固定字段序网络编码；读写必须同步维护，不支持直接写入扩展种类 DTO。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, RegularPartData> STREAM_CODEC = new StreamCodec<>() {

        /**
         * 按单部件固定字段序读取 DTO，不创建实例或应用恢复策略。
         */
        @Override
        public @NotNull RegularPartData decode(RegistryFriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            String name = buffer.readUtf();
            String uuid = buffer.readUtf();
            String variant = buffer.readUtf();
            float assemblingProgress = buffer.readFloat();
            float sharedDurabilityRatio = buffer.readFloat();
            int materialAssemblingProgress = buffer.readInt();
            boolean assembling = buffer.readBoolean();
            String textureName = buffer.readUtf();
            var subParts = SubPartData.MAP_STREAM_CODEC.decode(buffer);
            return new RegularPartData(registryKey, name, uuid, variant, assemblingProgress, sharedDurabilityRatio, materialAssemblingProgress, assembling, textureName, subParts);
        }

        /**
         * 写出单部件全部动态字段，与单部件解码器逐字段对应。
         */
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull RegularPartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeUtf(value.variant);
            buffer.writeFloat(value.assemblingProgress);
            buffer.writeFloat(value.sharedDurabilityRatio);
            buffer.writeInt(value.materialAssemblingProgress);
            buffer.writeBoolean(value.assembling);
            buffer.writeUtf(value.textureName);
            SubPartData.MAP_STREAM_CODEC.encode(buffer, value.subParts);
        }
    };

    /**
     * 普通部件映射的网络编码：先写数量，再逐项写字符串键和单个部件状态。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, Map<String, RegularPartData>> MAP_STREAM_CODEC = new StreamCodec<>() {

        /**
         * 先读取项数，再逐项读取字符串键和普通部件 DTO。
         */
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

        /**
         * 写入映射大小以及各项的字符串键和普通部件状态。
         */
        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull Map<String, RegularPartData> value) {
            buffer.writeVarInt(value.size());
            for (Map.Entry<String, RegularPartData> entry : value.entrySet()) {
                buffer.writeUtf(entry.getKey());
                STREAM_CODEC.encode(buffer, entry.getValue());
            }
        }
    };


    /**
     * 保存解码得到的普通部件动态字段；不复制子零件映射，也不修改或清空保存的资源状态。
     * <p>材料和组装进度在实例恢复时按模式处理，此处保留原始数值以供重复构造与数据往返。</p>
     */
    public RegularPartData(
            ResourceLocation registryKey,
            String name,
            String uuid,
            String variant,
            float assemblingProgress,
            float sharedDurabilityRatio,
            int materialAssemblingProgress,
            boolean assembling,
            String textureName,
            Map<String, SubPartData> subParts) {
        super(registryKey, name, uuid, variant, sharedDurabilityRatio, textureName, subParts);
        this.assemblingProgress = assemblingProgress;
        this.materialAssemblingProgress = materialAssemblingProgress;
        this.assembling = assembling;
    }

    /**
     * 从普通部件采集公共动态状态、组装比例、材料进度和组装形态。
     * <p>调用者应在宿主安排的存档阶段采集；骨架恢复所需的清空策略不在保存阶段执行。</p>
     */
    public RegularPartData(RegularPart part) {
        super(part);
        this.assemblingProgress = part.assemblingProgress;
        this.materialAssemblingProgress = part.materialProgress;
        this.assembling = part.assembling;
    }
}
