package io.github.sweetzonzi.machinemax.common.vehicle.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.jme3.math.Vector3f;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machinemax.common.vehicle.Part;
import io.github.sweetzonzi.machinemax.common.vehicle.SubPart;
import io.github.sweetzonzi.machinemax.common.vehicle.VehicleCore;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Getter
public class VehicleData {
    public final String name;
    public final String tooltip;
    public final ResourceLocation icon;
    public final String uuid;
    public final Vec3 pos;
    public final Vec3 min;
    public final Vec3 max;
    public final float hp;
    public final Map<String, PartData> parts;
    public final List<ConnectionData> connections;

    public static final Codec<VehicleData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("vehicle_name").forGetter(VehicleData::getName),
            Codec.STRING.fieldOf("tooltip").forGetter(VehicleData::getTooltip),
            ResourceLocation.CODEC.optionalFieldOf("icon", ResourceLocation.withDefaultNamespace("missingno")).forGetter(VehicleData::getIcon),
            Codec.STRING.fieldOf("uuid").forGetter(VehicleData::getUuid),
            Vec3.CODEC.fieldOf("pos").forGetter(VehicleData::getPos),
            Vec3.CODEC.optionalFieldOf("min", Vec3.ZERO).forGetter(VehicleData::getMin),
            Vec3.CODEC.optionalFieldOf("max", Vec3.ZERO).forGetter(VehicleData::getMax),
            Codec.FLOAT.fieldOf("hp").forGetter(VehicleData::getHp),
            PartData.MAP_CODEC.fieldOf("parts").forGetter(VehicleData::getParts),
            ConnectionData.CODEC.listOf().fieldOf("connections").forGetter(VehicleData::getConnections)

    ).apply(instance, VehicleData::new));

    public static final StreamCodec<FriendlyByteBuf, VehicleData> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull VehicleData decode(FriendlyByteBuf buffer) {
            String name = buffer.readUtf();
            String tooltip = buffer.readUtf();
            ResourceLocation icon = buffer.readResourceLocation();
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
            Map<String, PartData> parts = buffer.readJsonWithCodec(PartData.MAP_CODEC);
            List<ConnectionData> connections = buffer.readJsonWithCodec(ConnectionData.CODEC.listOf());
            return new VehicleData(name, tooltip, icon, uuid, pos, min, max, hp, parts, connections);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull VehicleData value) {
            buffer.writeUtf(value.name);
            buffer.writeUtf(value.tooltip);
            buffer.writeResourceLocation(value.icon);
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
            buffer.writeJsonWithCodec(PartData.MAP_CODEC, value.parts);
            buffer.writeJsonWithCodec(ConnectionData.CODEC.listOf(), value.connections);
        }
    };

    public VehicleData(String name, String tooltip, ResourceLocation icon, String uuid,
                       Vec3 pos, Vec3 min, Vec3 max,
                       float hp, Map<String, PartData> parts, List<ConnectionData> connections) {
        this.name = name;
        this.tooltip = tooltip;
        this.icon = icon;
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
        this.tooltip = "";
        this.icon = ResourceLocation.withDefaultNamespace("missingno");
        this.uuid = vehicle.getUuid().toString();
        this.pos = vehicle.getPosition();
        AABB aabb = vehicle.getAABB();
        this.min = aabb.getMinPosition().subtract(pos);
        this.max = aabb.getMaxPosition().subtract(pos);
        this.hp = vehicle.getHp();
        this.parts = vehicle.getPartData();
        this.connections = vehicle.getConnectionData();
    }

    public static void serializeVehicleDataToJson(VehicleData vehicleData, File filePath) throws IOException {
        // 使用 codec 将 VehicleData 编码为 JsonElement
        JsonElement encoded = VehicleData.CODEC.encodeStart(JsonOps.INSTANCE, vehicleData).getOrThrow(IllegalArgumentException::new);

        // 使用 Gson 将 JsonElement 转换为 JSON 字符串
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonString = gson.toJson(encoded);

        // 将 JSON 字符串写入文件
        try (FileWriter fileWriter = new FileWriter(filePath)) {
            fileWriter.write(jsonString);
        }
    }

}
