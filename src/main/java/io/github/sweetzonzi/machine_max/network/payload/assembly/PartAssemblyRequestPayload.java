package io.github.sweetzonzi.machine_max.network.payload.assembly;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyServerHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <p>手动零件组装的放置请求（客户端 → 服务端）。</p>
 * <p>客户端本地完成装配选择（部件类型、变体、对外连接点、安装角），仅在放置时把选择结果上报，
 * 由服务端 {@link VehicleAssemblyServerHelper} 做权威校验后执行放置。</p>
 * <p>载荷不携带放置意图字段（蓝图填进度 / 仅改配方由服务端按手持物品 + 姿态 + 视线目标判定）。</p>
 *
 * @param registryKey      部件类型注册名
 * @param variant          变体名
 * @param subPart          部件对外连接点所属子部件名（可为空）
 * @param connector        部件对外连接点名（可为空）
 * @param hand             使用的手，服务端据此取对应 {@code ItemStack}
 * @param attachToTarget   是否安装到现有部件的连接点（{@code false} = 凭空新放置悬浮零件）
 * @param targetSubPartId  目标连接点所在 SubPart 的持久 id，仅 {@code attachToTarget} 时有意义
 * @param targetConnector  目标连接点名称，仅 {@code attachToTarget} 时有意义
 * @param attachRotation   安装角（服务端仅归一化到 [0,360)）
 */
public record PartAssemblyRequestPayload(
        @NotNull ResourceLocation registryKey,
        @NotNull String variant,
        @Nullable String subPart,
        @Nullable String connector,
        @NotNull InteractionHand hand,
        boolean attachToTarget,
        int targetSubPartId,
        @Nullable String targetConnector,
        float attachRotation
) implements CustomPacketPayload {
    public static final Type<PartAssemblyRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "part_assembly_request_payload")
    );
    public static final StreamCodec<FriendlyByteBuf, PartAssemblyRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, @NotNull PartAssemblyRequestPayload payload) {
            buffer.writeResourceLocation(payload.registryKey);
            buffer.writeUtf(payload.variant);
            boolean hasPartConnector = payload.subPart != null && payload.connector != null;
            buffer.writeBoolean(hasPartConnector);
            if (hasPartConnector) {
                buffer.writeUtf(payload.subPart);
                buffer.writeUtf(payload.connector);
            }
            buffer.writeEnum(payload.hand);
            buffer.writeBoolean(payload.attachToTarget);
            if (payload.attachToTarget) {
                buffer.writeInt(payload.targetSubPartId);
                buffer.writeUtf(payload.targetConnector);
            }
            buffer.writeFloat(payload.attachRotation);
        }

        @Override
        public @NotNull PartAssemblyRequestPayload decode(@NotNull FriendlyByteBuf buffer) {
            ResourceLocation registryKey = buffer.readResourceLocation();
            String variant = buffer.readUtf();
            String subPart = null;
            String connector = null;
            if (buffer.readBoolean()) {
                subPart = buffer.readUtf();
                connector = buffer.readUtf();
            }
            InteractionHand hand = buffer.readEnum(InteractionHand.class);
            boolean attachToTarget = buffer.readBoolean();
            int targetSubPartId = 0;
            String targetConnector = null;
            if (attachToTarget) {
                targetSubPartId = buffer.readInt();
                targetConnector = buffer.readUtf();
            }
            float attachRotation = buffer.readFloat();
            return new PartAssemblyRequestPayload(registryKey, variant, subPart, connector, hand,
                    attachToTarget, targetSubPartId, targetConnector, attachRotation);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端处理器（主线程）：交由 {@link VehicleAssemblyServerHelper} 完成权威校验与放置。
     */
    public static void serverHandler(final PartAssemblyRequestPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> VehicleAssemblyServerHelper.handle(context.player(), payload));
    }
}
