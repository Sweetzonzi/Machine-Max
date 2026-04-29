package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
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
 * 将序列化后的载具 JSON 发送到客户端，由客户端保存到本地文件系统
 */
public record VehicleDataSavedPayload(
        String jsonData,
        String fileName
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleDataSavedPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_data_saved")
    );

    public static final StreamCodec<ByteBuf, VehicleDataSavedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, VehicleDataSavedPayload::jsonData,
            ByteBufCodecs.STRING_UTF8, VehicleDataSavedPayload::fileName,
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
                writer.write(packet.jsonData());
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
