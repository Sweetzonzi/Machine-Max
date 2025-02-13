package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.util.data.PosRotVel;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public record PhysSyncPayload(int step, HashMap<Integer, PosRotVel> syncData) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PhysSyncPayload> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "phys_sync_payload"));
    public static final StreamCodec<ByteBuf, PhysSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT,
            PhysSyncPayload::step,//同步包时间戳
            ByteBufCodecs.map(
                    HashMap::new,
                    ByteBufCodecs.INT,
                    PosRotVel.DATA_CODEC
            ),
            PhysSyncPayload::syncData,//同步的所有运动体的位姿速度信息
            PhysSyncPayload::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handler(final PhysSyncPayload payload, final IPayloadContext context) {

        //TODO:根据时间戳判定数据包的有效性，并根据延迟情况对客户端位姿进行预测
    }
}
