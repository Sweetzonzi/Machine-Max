package io.github.sweetzonzi.machine_max.common.vehicle.data;

import cn.solarmoon.spark_core.util.SparkMathKt;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.util.data.PosRotVelVel;
import lombok.Getter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;

import java.util.HashMap;
import java.util.Map;
@Getter
public class SubPartData {
    public final int id;// 零件的ID
    public final float durability;// 零件的耐久度
    public final PosRotVelVel posRotVelVel;// 零件的位置、朝向、速度、角速度
    public final int textureIndex;// 零件的纹理索引
    public final Map<String, CompoundTag> connectorData;// 连接点结构完整性
    public final Map<String, CompoundTag> subsystemData;// 零件的子系统数据

    public static final Codec<Map<String, CompoundTag>> DATA_CODEC = Codec.unboundedMap(Codec.STRING, CompoundTag.CODEC);

    public static final Codec<SubPartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("id").forGetter(SubPartData::getId),
            Codec.FLOAT.optionalFieldOf("durability", Float.MAX_VALUE).forGetter(SubPartData::getDurability),
            PosRotVelVel.CODEC.fieldOf("pos_rot_vel_vel").forGetter(SubPartData::getPosRotVelVel),
            Codec.INT.optionalFieldOf("texture_index", 0).forGetter(SubPartData::getTextureIndex),
            DATA_CODEC.optionalFieldOf("connector_data", Map.of()).forGetter(SubPartData::getConnectorData),
            DATA_CODEC.optionalFieldOf("subsystem_data", Map.of()).forGetter(SubPartData::getSubsystemData)
    ).apply(instance, SubPartData::new));

    public static final Codec<Map<String, SubPartData>> MAP_CODEC = Codec.unboundedMap(Codec.STRING, CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, SubPartData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SubPartData decode(RegistryFriendlyByteBuf buffer) {
            int id = buffer.readInt();
            float durability = buffer.readFloat();
            PosRotVelVel posRotVelVel = PosRotVelVel.STREAM_CODEC.decode(buffer);
            int textureIndex = buffer.readInt();

            // 解码 connectorData
            int connectorSize = buffer.readVarInt();
            Map<String, CompoundTag> connectorData = new HashMap<>(connectorSize);
            for (int i = 0; i < connectorSize; i++) {
                String key = buffer.readUtf();
                CompoundTag value = buffer.readNbt();
                connectorData.put(key, value);
            }

            // 解码 subsystemData
            int subsystemSize = buffer.readVarInt();
            Map<String, CompoundTag> subsystemData = new HashMap<>(subsystemSize);
            for (int i = 0; i < subsystemSize; i++) {
                String key = buffer.readUtf();
                CompoundTag value = buffer.readNbt();
                subsystemData.put(key, value);
            }

            return new SubPartData(id, durability, posRotVelVel, textureIndex, connectorData, subsystemData);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, SubPartData value) {
            buffer.writeInt(value.id);
            buffer.writeFloat(value.durability);
            PosRotVelVel.STREAM_CODEC.encode(buffer, value.posRotVelVel);
            buffer.writeInt(value.textureIndex);

            // 编码 connectorData
            Map<String, CompoundTag> connectorIntegrity = value.connectorData;
            buffer.writeVarInt(connectorIntegrity.size());
            for (Map.Entry<String, CompoundTag> entry : connectorIntegrity.entrySet()) {
                buffer.writeUtf(entry.getKey());
                buffer.writeNbt(entry.getValue());
            }

            // 编码 subsystemData
            Map<String, CompoundTag> subsystemData = value.subsystemData;
            buffer.writeVarInt(subsystemData.size());
            for (Map.Entry<String, CompoundTag> entry : subsystemData.entrySet()) {
                buffer.writeUtf(entry.getKey());
                buffer.writeNbt(entry.getValue());
            }
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, Map<String, SubPartData>> MAP_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull Map<String, SubPartData> decode(RegistryFriendlyByteBuf buffer) {
            int mapSize = buffer.readVarInt();
            Map<String, SubPartData> map = new HashMap<>(mapSize);
            for (int i = 0; i < mapSize; i++) {
                String key = buffer.readUtf();
                SubPartData value = STREAM_CODEC.decode(buffer);
                map.put(key, value);
            }
            return map;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, Map<String, SubPartData> value) {
            buffer.writeVarInt(value.size());

            for (Map.Entry<String, SubPartData> entry : value.entrySet()) {
                buffer.writeUtf(entry.getKey());
                STREAM_CODEC.encode(buffer, entry.getValue());
            }
        }
    };

    /**
     * @param id                 零件的ID
     * @param durability         零件的耐久度
     * @param posRotVelVel       零件的位置、朝向、速度、角速度
     * @param textureIndex       零件的纹理索引
     * @param connectorData      零件的连接点数据
     * @param subsystemData      零件的子系统数据
     */
    public SubPartData(int id, float durability, PosRotVelVel posRotVelVel, int textureIndex,
                      Map<String, CompoundTag> connectorData, Map<String, CompoundTag> subsystemData) {
        this.id = id;
        this.durability = durability;
        this.posRotVelVel = posRotVelVel;
        this.textureIndex = textureIndex;
        this.connectorData = connectorData;
        this.subsystemData = subsystemData;
    }

    public SubPartData(SubPart subPart) {
        Vector3f position = subPart.getPosition();
        Quaternionf rotation = SparkMathKt.toQuaternionf(subPart.getRotation());
        Vector3f linearVel = subPart.getLinearVelocity();
        Vector3f angularVel = subPart.getAngularVelocity();
        Map<String, CompoundTag> connectorData = HashMap.newHashMap(1);
        for (Map.Entry<String, AbstractConnector> entry : subPart.connectors.entrySet()) {
            String connectorName = entry.getKey();
            AbstractConnector connector = entry.getValue();
            connectorData.put(connectorName, connector.saveData(new CompoundTag()));
        }
        Map<String, CompoundTag> subPartSubsystemData = HashMap.newHashMap(1);
        for (Map.Entry<String, AbstractSubsystem> entry : subPart.subsystems.entrySet()) {
            String subsystemName = entry.getKey();
            AbstractSubsystem subsystem = entry.getValue();
            subPartSubsystemData.put(subsystemName, subsystem.saveData(new CompoundTag()));
        }
        this.id = subPart.getId();
        this.durability = subPart.getDurability();
        this.posRotVelVel = new PosRotVelVel(position, rotation, linearVel, angularVel);
        this.textureIndex = subPart.getTextureIndex();
        this.connectorData = connectorData;
        this.subsystemData = subPartSubsystemData;
    }
}
