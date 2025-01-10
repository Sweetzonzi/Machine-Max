package io.github.tt432.machinemax.network.payload;

import io.github.tt432.machinemax.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

public record RegularInputPayload(int key, int tick_count) implements CustomPacketPayload {
    public static final Type<RegularInputPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "regular_input_payload"));
    public static final StreamCodec<ByteBuf, RegularInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            RegularInputPayload::key,//按下的按键
            ByteBufCodecs.VAR_INT,
            RegularInputPayload::tick_count,//0为按下，1为松开
            RegularInputPayload::new
    );
    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
