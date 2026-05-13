package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 服务端→客户端的载具数据保存包
 * 使用 VehicleData.STREAM_CODEC 传输完整载具数据，由客户端序列化为 JSON 保存到本地文件系统
 */
public record VehicleDataSavedPayload(
        VehicleData vehicleData,
        String fileName
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleDataSavedPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_data_saved")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleDataSavedPayload> STREAM_CODEC = StreamCodec.composite(
            VehicleData.STREAM_CODEC, VehicleDataSavedPayload::vehicleData,
            net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, VehicleDataSavedPayload::fileName,
            VehicleDataSavedPayload::new
    );

    public static void handler(VehicleDataSavedPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            // 保存到客户端游戏目录下的 saved_blueprints 文件夹
            var gameDir = FMLPaths.GAMEDIR.get().toFile();
            var saveDir = new File(gameDir, "saved_blueprints");
            if (!saveDir.exists()) {
                saveDir.mkdirs();
            }

            var saveFile = new File(saveDir, packet.fileName());
            try (FileWriter writer = new FileWriter(saveFile)) {
                writer.write(VehicleData.serializeToJsonString(packet.vehicleData));
                MachineMax.LOGGER.info("已保存载具蓝图到客户端目录: {}", saveFile.getAbsolutePath());
            } catch (IOException e) {
                MachineMax.LOGGER.error("客户端保存载具蓝图失败!", e);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
