package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintMeta;
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
import java.util.UUID;

/**
 * 服务端→客户端的载具数据保存包。
 *
 * <p>使用 {@link VehicleData#STREAM_CODEC} 传输载具数据（不含 meta），元信息以<b>独立字段</b>
 * 随包附带，由客户端写文件时合并进 JSON。同时被「抄录」与「存入库」两条链路复用。</p>
 */
public record VehicleDataSavedPayload(
        VehicleData vehicleData,
        BlueprintMeta meta
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<VehicleDataSavedPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "vehicle_data_saved")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, VehicleDataSavedPayload> STREAM_CODEC = StreamCodec.composite(
            VehicleData.STREAM_CODEC, VehicleDataSavedPayload::vehicleData,
            BlueprintMeta.STREAM_CODEC, VehicleDataSavedPayload::meta,
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

            // 文件名与展示名解耦：每次写入生成新的随机 UUID，与 VehicleData.uuid 无关，不再静默覆盖
            var saveFile = new File(saveDir, UUID.randomUUID() + ".json");
            try (FileWriter writer = new FileWriter(saveFile)) {
                writer.write(VehicleData.serializeToJsonString(packet.vehicleData().withMeta(packet.meta())));
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
