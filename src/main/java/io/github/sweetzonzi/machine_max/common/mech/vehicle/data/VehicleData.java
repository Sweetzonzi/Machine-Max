package io.github.sweetzonzi.machine_max.common.mech.vehicle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import lombok.Getter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Getter
public class VehicleData {
    public final String name;
    public final String uuid;
    public final Vec3 pos;
    public final Vec3 min;
    public final Vec3 max;
    public final float hp;
    public final Map<String, PartData> parts;
    public final List<ConnectionData> connections;

    public static final Codec<VehicleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "Vehicle").forGetter(VehicleData::getName),
            Codec.STRING.fieldOf("uuid").forGetter(VehicleData::getUuid),
            Vec3.CODEC.fieldOf("pos").forGetter(VehicleData::getPos),
            Vec3.CODEC.optionalFieldOf("min", Vec3.ZERO).forGetter(VehicleData::getMin),
            Vec3.CODEC.optionalFieldOf("max", Vec3.ZERO).forGetter(VehicleData::getMax),
            Codec.FLOAT.fieldOf("hp").forGetter(VehicleData::getHp),
            PartData.MAP_CODEC.fieldOf("parts").forGetter(VehicleData::getParts),
            ConnectionData.CODEC.listOf().fieldOf("connections").forGetter(VehicleData::getConnections)
    ).apply(instance, VehicleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull VehicleData decode(RegistryFriendlyByteBuf buffer) {
            String name = buffer.readUtf();
            String uuid = buffer.readUtf();
            double x = buffer.readFloat();
            double y = buffer.readFloat();
            double z = buffer.readFloat();
            Vec3 pos = new Vec3(x, y, z);
            double minX = buffer.readFloat();
            double minY = buffer.readFloat();
            double minZ = buffer.readFloat();
            Vec3 min = new Vec3(minX, minY, minZ);
            double maxX = buffer.readFloat();
            double maxY = buffer.readFloat();
            double maxZ = buffer.readFloat();
            Vec3 max = new Vec3(maxX, maxY, maxZ);
            float hp = buffer.readFloat();
            Map<String, PartData> parts = PartData.MAP_STREAM_CODEC.decode(buffer);
            List<ConnectionData> connections = buffer.readList(ConnectionData.STREAM_CODEC);
            return new VehicleData(name, uuid, pos, min, max, hp, parts, connections);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, @NotNull VehicleData value) {
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.uuid);
            buffer.writeFloat((float) value.pos.x);
            buffer.writeFloat((float) value.pos.y);
            buffer.writeFloat((float) value.pos.z);
            buffer.writeFloat((float) value.min.x);
            buffer.writeFloat((float) value.min.y);
            buffer.writeFloat((float) value.min.z);
            buffer.writeFloat((float) value.max.x);
            buffer.writeFloat((float) value.max.y);
            buffer.writeFloat((float) value.max.z);
            buffer.writeFloat(value.hp);
            PartData.MAP_STREAM_CODEC.encode(buffer, value.parts);
            buffer.writeCollection(value.connections, ConnectionData.STREAM_CODEC);
        }
    };

    public VehicleData(String name, String uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hp, Map<String, PartData> parts, List<ConnectionData> connections) {
        this.name = name;
        this.uuid = uuid;
        this.pos = pos;
        this.min = min;
        this.max = max;
        this.hp = hp;
        this.parts = parts;
        this.connections = connections;
    }

    /**
     * 将载具数据打包为方便传输与读取的VehicleData
     *
     * @param vehicle 载具实例
     */
    public VehicleData(VehicleCore vehicle) {
        this.name = vehicle.name;
        this.uuid = vehicle.getUuid().toString();
        this.pos = vehicle.getPosition();
        AABB aabb = vehicle.getAABB();
        this.min = aabb.getMinPosition().subtract(pos);
        this.max = aabb.getMaxPosition().subtract(pos);
        this.hp = vehicle.getHp();
        this.parts = vehicle.getPartData();
        this.connections = vehicle.getConnectionData();
    }

    /**
     * 将载具数据序列化为 JSON 字符串
     */
    public static String serializeToJsonString(VehicleData vehicleData) {
        JsonElement encoded = VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, vehicleData)
                .getOrThrow(IllegalArgumentException::new);
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(encoded);
    }

    public static void serializeVehicleDataToJson(VehicleData vehicleData, File filePath) throws IOException {
        // 将 JSON 字符串写入文件
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.write(serializeToJsonString(vehicleData));
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VehicleData that)) return false;
        return Float.compare(hp, that.hp) == 0 && Objects.equals(name, that.name) && Objects.equals(uuid, that.uuid) && Objects.equals(pos, that.pos) && Objects.equals(min, that.min) && Objects.equals(max, that.max) && Objects.equals(parts, that.parts) && Objects.equals(connections, that.connections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uuid, pos, min, max, hp, parts, connections);
    }

    public VehicleData withNewName(String name) {
        return new VehicleData(name, uuid, pos, min, max, hp, parts, connections);
    }

    public VehicleData withNewUUID(UUID uuid) {
        return new VehicleData(name, uuid.toString(), pos, min, max, hp, parts, connections);
    }
}
