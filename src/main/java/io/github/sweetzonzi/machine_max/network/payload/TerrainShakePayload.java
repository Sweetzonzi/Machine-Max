package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 地形命中屏幕抖动同步包（服务端→客户端）。
 * <p>
 * 炮弹落地时向周围玩家广播此包，客户端 {@link CameraController} 随机生成一个
 * 方向的快速小幅度正弦振荡抖动，不复用直接命中的头部物理冲击效果。
 * <p>
 * <b>调用线程：</b>主线程（由 {@link io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile#onTerrainHit} 发送）。
 */
public record TerrainShakePayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TerrainShakePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "terrain_shake"));
    public static final StreamCodec<FriendlyByteBuf, TerrainShakePayload> STREAM_CODEC = StreamCodec.unit(new TerrainShakePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：入队地形抖动信号，{@link CameraController} 在下一渲染帧生成随机方向的小幅度抖动。
     */
    public static void handle(final TerrainShakePayload payload, final IPayloadContext context) {
        context.enqueueWork(CameraController::enqueueTerrainShake);
    }
}
