package io.github.sweetzonzi.machine_max.common.vehicle.data;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.List;
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
    public final float durability;//部件的耐久度
    public final float integrity;//部件的完整度
    public final Map<String, PosRotVelVel> subPartTransforms;//尚存的零件的位置、旋转、速度、角速度
    public final Map<String, Integer> textureIndexes;//尚存的零件的贴图索引
    public final Map<String, Map<String, CompoundTag>> subsystemData;//尚存的零件的子系统数据

    public static final Codec<Map<String, Integer>> TEXTURE_INDEXES_CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT);

    public static final Codec<Map<String, CompoundTag>> SUBPART_SUBSYSTEM_DATA_CODEC = Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC);

    public static final Codec<Map<String, Map<String, CompoundTag>>> SUBSYSTEM_DATA_CODEC = Codec.unboundedMap(Codec.STRING, SUBPART_SUBSYSTEM_DATA_CODEC);

    public static final Codec<PartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("registry_key").forGetter(PartData::getRegistryKey),
            Codec.STRING.fieldOf("subpart").forGetter(PartData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(PartData::getUuid),
            Codec.STRING.fieldOf("variant").forGetter(PartData::getVariant),
            Codec.FLOAT.fieldOf("durability").forGetter(PartData::getDurability),
            Codec.FLOAT.optionalFieldOf("integrity", 20f).forGetter(PartData::getIntegrity),
            PosRotVelVel.MAP_CODEC.fieldOf("subpart_transforms").forGetter(PartData::getSubPartTransforms),
            TEXTURE_INDEXES_CODEC.fieldOf("texture_indexes").forGetter(PartData::getTextureIndexes),
            SUBSYSTEM_DATA_CODEC.optionalFieldOf("subsystem_data", Map.of()).forGetter(PartData::getSubsystemData)
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
            String uuid = buffer.readUtf();
            String variant = buffer.readUtf();
            float durability = buffer.readFloat();
            float integrity = buffer.readFloat();
            Map<String, PosRotVelVel> subPartTransforms = buffer.readJsonWithCodec(PosRotVelVel.MAP_CODEC);
            Map<String, Integer> textureIndexes = buffer.readJsonWithCodec(TEXTURE_INDEXES_CODEC);
            Map<String, Map<String, CompoundTag>> subsystemData = buffer.readJsonWithCodec(SUBSYSTEM_DATA_CODEC);
            return new PartData(registryKey, name, uuid, variant, durability, integrity, subPartTransforms, textureIndexes, subsystemData);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull PartData value) {
            buffer.writeResourceLocation(value.registryKey);
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeUtf(value.variant);
            buffer.writeFloat(value.durability);
            buffer.writeFloat(value.integrity);
            buffer.writeJsonWithCodec(PosRotVelVel.MAP_CODEC, value.subPartTransforms);
            buffer.writeJsonWithCodec(TEXTURE_INDEXES_CODEC, value.textureIndexes);
            buffer.writeJsonWithCodec(SUBSYSTEM_DATA_CODEC, value.subsystemData);
        }
    };

    public PartData(
            ResourceLocation registryKey,
            String name,
            String uuid,
            String variant,
            float durability,
            float integrity,
            Map<String, PosRotVelVel> subPartTransforms,
            Map<String, Integer> textureIndexes,
            Map<String, Map<String, CompoundTag>> subsystemData) {
        this.registryKey = registryKey;
        this.name = name;
        this.uuid = uuid;
        this.variant = variant;
        this.durability = durability;
        this.integrity = integrity;
        this.subPartTransforms = subPartTransforms;
        this.textureIndexes = textureIndexes;
        this.subsystemData = subsystemData;
        //校验数据
        for (Map.Entry<String, PosRotVelVel> entry : subPartTransforms.entrySet()) {
            String subPartName = entry.getKey();
            PosRotVelVel transform = entry.getValue();
            Vector3f position = transform.position();
            Quaternionf rotation = transform.rotation();
            Vector3f linearVel = transform.linearVel();
            Vector3f angularVel = transform.angularVel();
            if (!Vector3f.isValidVector(position) ||
                    !rotation.isFinite() ||
                    !Vector3f.isValidVector(linearVel) ||
                    !Vector3f.isValidVector(angularVel) ||
                    position.length() > 3e8 ||
                    linearVel.length() > 4e8 ||
                    angularVel.length() > 4e8) {
                throw new IllegalArgumentException("Invalid transform data for sub-part " + subPartName + " " + transform);
            }
        }
    }

    /**
     * 将部件数据打包为方便传输与读取的PartData
     *
     * @param part 部件实例
     */
    public PartData(Part part) {
        this.registryKey = part.type.registryKey;
        this.name = part.name;
        this.uuid = part.getUuid().toString();
        this.variant = part.variant;
        this.durability = part.durability;
        this.integrity = part.integrity;
        this.subPartTransforms = HashMap.newHashMap(1);
        this.textureIndexes = HashMap.newHashMap(1);
        this.subsystemData = HashMap.newHashMap(1);
        for (Map.Entry<String, SubPart> entry : part.subParts.entrySet()) {
            String subPartName = entry.getKey();
            SubPart subPart = entry.getValue();
            //保存子零件的位置、旋转、速度、角速度
            this.subPartTransforms.put(subPartName, new PosRotVelVel(
                    subPart.body.getPhysicsLocation(null),
                    SparkMathKt.toQuaternionf(subPart.body.getPhysicsRotation(null)),
                    subPart.body.getLinearVelocity(null),
                    subPart.body.getAngularVelocity(null)
            ));
            //保存子零件的贴图索引
            this.textureIndexes.put(subPartName, subPart.getTextureIndex());
            //保存子零件的子系统数据
            Map<String, CompoundTag> subPartSubsystemData = HashMap.newHashMap(1);
            for (Map.Entry<String, AbstractSubsystem> entry2 : subPart.subsystems.entrySet()) {
                String subsystemName = entry2.getKey();
                AbstractSubsystem subsystem = entry2.getValue();
                subPartSubsystemData.put(subsystemName, subsystem.saveData(new CompoundTag()));
            }
            subsystemData.put(subPartName, subPartSubsystemData);
        }
    }
}
