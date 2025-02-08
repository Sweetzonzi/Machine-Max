package io.github.tt432.machinemax.common.vehicle.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.Part;
import io.github.tt432.machinemax.common.vehicle.SubPart;
import io.github.tt432.machinemax.common.vehicle.attr.SubPartAttr;
import io.github.tt432.machinemax.util.data.PosRotVelVel;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于保存部件动态数据(位置，耐久度等)
 */
@Getter
public class PartData {
    public final ResourceLocation registryKey;
    public final String name;
    public final String variant;
    public final int textureIndex;
    public final String uuid;
    public final float durability;
    public final Map<String, PosRotVelVel> subPartTransforms;

    public static final Codec<Map<String, PosRotVelVel>> SUB_PART_TRANS_MAP_CODEC = Codec.unboundedMap(
            Codec.STRING,// 键：子部件名称，字符串
            PosRotVelVel.CODEC// 值：子部件位置、旋转、线速度和角速度
    );

    public static final Codec<PartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registryKey").forGetter(PartData::getRegistryKey),
            Codec.STRING.fieldOf("boneName").forGetter(PartData::getName),
            Codec.STRING.fieldOf("variant").forGetter(PartData::getVariant),
            Codec.INT.fieldOf("textureIndex").forGetter(PartData::getTextureIndex),
            Codec.STRING.fieldOf("uuid").forGetter(PartData::getUuid),
            Codec.FLOAT.fieldOf("durability").forGetter(PartData::getDurability),
            SUB_PART_TRANS_MAP_CODEC.fieldOf("subPartTransforms").forGetter(PartData::getSubPartTransforms)
    ).apply(instance, PartData::new));

    public static final Codec<Map<String, PartData>> MAP_CODEC = CODEC.listOf().xmap(
            list -> {
                Map<String, PartData> map = new java.util.HashMap<>();
                for (PartData data : list) {
                    map.put(data.uuid, data);
                }
                return map;
            },
            map -> map.values().stream().toList()
    );

    public static final StreamCodec<FriendlyByteBuf, PartData> STREAM_CODEC = new StreamCodec<>() {

        @Override
        public @NotNull PartData decode(FriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            String name = buffer.readUtf();
            String variant = buffer.readUtf();
            int textureIndex = buffer.readInt();
            String uuid = buffer.readUtf();
            float durability = buffer.readFloat();
            Map<String, PosRotVelVel> subPartTransforms = buffer.readJsonWithCodec(SUB_PART_TRANS_MAP_CODEC);
            return new PartData(registryKey, name, variant, textureIndex, uuid, durability, subPartTransforms);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull PartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.variant);
            buffer.writeInt(value.textureIndex);
            buffer.writeUtf(value.uuid);
            buffer.writeFloat(value.durability);
            buffer.writeJsonWithCodec(SUB_PART_TRANS_MAP_CODEC, value.subPartTransforms);
        }

    };

    public PartData(ResourceLocation registryKey, String name, String variant, int textureIndex, String uuid, float durability, Map<String, PosRotVelVel> subPartTransforms) {
        this.registryKey = registryKey;
        this.name = name;
        this.variant = variant;
        this.textureIndex = textureIndex;
        this.uuid = uuid;
        this.durability = durability;
        this.subPartTransforms = subPartTransforms;
    }

    /**
     * 将部件数据打包为方便传输与读取的PartData
     *
     * @param part 部件实例
     */
    public PartData(Part part) {
        this.registryKey = part.type.registryKey;
        this.name = part.name;
        this.variant = part.variant;
        this.textureIndex = part.textureIndex;
        this.uuid = part.getUuid().toString();
        this.durability = part.durability;
        this.subPartTransforms = HashMap.newHashMap(1);
        for (Map.Entry<String, SubPart> entry : part.subParts.entrySet()) {
            this.subPartTransforms.put(entry.getKey(), new PosRotVelVel(
//                    entry.getValue().position,
//                    entry.getValue().rotation,
//                    entry.getValue().linearVel,
//                    entry.getValue().angularVel
                    //TODO:改成Body的位置、旋转、线速度、角速度
                    new Vec3(0, 0, 0),
                    new Quaternionf(0, 0, 0, 1),
                    new Vec3(0, 0, 0),
                    new Vec3(0, 0, 0)
            ));
        }
    }
}
