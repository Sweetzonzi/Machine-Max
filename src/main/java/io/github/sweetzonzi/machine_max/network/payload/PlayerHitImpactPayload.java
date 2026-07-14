package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 玩家受投射物命中冲击同步包（服务端→客户端）。
 * <p>
 * 携带命中速度、命中点和基础伤害，客户端 {@link CameraController} 消费后
 * 产生玩家头部冲击效果（视角抖动）。
 * <p>
 * <b>调用线程：</b>主线程（由 {@link io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile#dealDamage} 发送）。
 */
public record PlayerHitImpactPayload(
        double hitVelX,
        double hitVelY,
        double hitVelZ,
        double hitPointX,
        double hitPointY,
        double hitPointZ,
        float baseDamage
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PlayerHitImpactPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "player_hit_impact"));
    public static final StreamCodec<FriendlyByteBuf, PlayerHitImpactPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull PlayerHitImpactPayload decode(FriendlyByteBuf buf) {
            return new PlayerHitImpactPayload(
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull PlayerHitImpactPayload payload) {
            buffer.writeDouble(payload.hitVelX());
            buffer.writeDouble(payload.hitVelY());
            buffer.writeDouble(payload.hitVelZ());
            buffer.writeDouble(payload.hitPointX());
            buffer.writeDouble(payload.hitPointY());
            buffer.writeDouble(payload.hitPointZ());
            buffer.writeFloat(payload.baseDamage());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：将命中冲击数据入队到 {@link CameraController} 的缓冲区，
     * 下一渲染帧由 {@link CameraController#updateCameraRot} 消费。
     */
    public static void handle(final PlayerHitImpactPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            CameraController.enqueueHitImpact(
                    new Vec3(payload.hitVelX(), payload.hitVelY(), payload.hitVelZ()),
                    new Vec3(payload.hitPointX(), payload.hitPointY(), payload.hitPointZ()),
                    payload.baseDamage());
        });
    }
}
