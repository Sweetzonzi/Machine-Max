package io.github.sweetzonzi.machine_max.network.payload.library;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 服务端 → 客户端：蓝图取出结果回执。
 *
 * @param success          是否成功
 * @param cost             实际扣减的研发点（失败为 0）
 * @param reason           失败原因，成功为 {@link Reason#NONE}
 * @param missingPartTypes 缺失的零件 id 列表，仅 {@link Reason#MISSING_PARTS} 时非空
 */
public record BlueprintExtractResultPayload(
        boolean success,
        int cost,
        Reason reason,
        List<ResourceLocation> missingPartTypes
) implements CustomPacketPayload {
    public static final Type<BlueprintExtractResultPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint_extract_result")
    );

    public static final StreamCodec<ByteBuf, BlueprintExtractResultPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BlueprintExtractResultPayload::success,
            ByteBufCodecs.VAR_INT, BlueprintExtractResultPayload::cost,
            Reason.STREAM_CODEC, BlueprintExtractResultPayload::reason,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), BlueprintExtractResultPayload::missingPartTypes,
            BlueprintExtractResultPayload::new
    );

    /** 失败原因 */
    public enum Reason {
        /** 成功 */
        NONE,
        /** 结构非法（数据校验未通过） */
        INVALID_STRUCTURE,
        /** 零件缺失（内容包未安装或零件已删除） */
        MISSING_PARTS,
        /** 研发点余额不足 */
        INSUFFICIENT_RP,
        /** 未知蓝图（内容包蓝图 id 不存在） */
        UNKNOWN_BLUEPRINT;

        public static final StreamCodec<ByteBuf, Reason> STREAM_CODEC = new StreamCodec<>() {
            @Override
            public @NotNull Reason decode(ByteBuf buffer) {
                int id = buffer.readUnsignedByte();
                Reason[] values = values();
                return id < values.length ? values[id] : NONE;
            }

            @Override
            public void encode(ByteBuf buffer, Reason value) {
                buffer.writeByte(value.ordinal());
            }
        };
    }

    public static BlueprintExtractResultPayload success(int cost) {
        return new BlueprintExtractResultPayload(true, cost, Reason.NONE, List.of());
    }

    public static BlueprintExtractResultPayload failure(Reason reason, int cost) {
        return new BlueprintExtractResultPayload(false, cost, reason, List.of());
    }

    public static BlueprintExtractResultPayload missingParts(List<ResourceLocation> missingPartTypes) {
        return new BlueprintExtractResultPayload(false, 0, Reason.MISSING_PARTS, missingPartTypes);
    }

    public static void clientHandler(BlueprintExtractResultPayload packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (packet.success()) {
                context.player().displayClientMessage(
                        Component.translatable("message.machine_max.blueprint_extract.success", packet.cost()), true);
            } else {
                context.player().displayClientMessage(
                        Component.translatable("message.machine_max.blueprint_extract.failed",
                                Component.translatable("message.machine_max.blueprint_extract.reason."
                                        + packet.reason().name().toLowerCase(java.util.Locale.ROOT))),
                        true);
            }
        });
    }

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
