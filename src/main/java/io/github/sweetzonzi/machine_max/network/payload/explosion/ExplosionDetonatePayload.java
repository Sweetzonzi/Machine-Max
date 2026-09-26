package io.github.sweetzonzi.machine_max.network.payload.explosion;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.explosion.BlastDetonationEffects;
import io.github.sweetzonzi.machine_max.common.mech.explosion.BlastFrontVisual;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionParams;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 起爆网络包（服务端 → 客户端）：载荷只含"起爆点 + 种子 + 参数集"。
 *
 * <p>客户端凭这三项即可完整重建射线方向集与波前半径（年龄各自从 0 起算），
 * <b>不逐条同步射线</b>，也不在客户端建立逻辑实例。</p>
 */
public record ExplosionDetonatePayload(
        ResourceKey<Level> dimension,
        Vector3f origin,
        long seed,
        ExplosionParams params
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ExplosionDetonatePayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "explosion_detonate")
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, ExplosionDetonatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ExplosionDetonatePayload decode(RegistryFriendlyByteBuf buf) {
                    ResourceKey<Level> dimension = ResourceKey.streamCodec(Registries.DIMENSION).decode(buf);
                    Vector3f origin = new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat());
                    long seed = buf.readLong();
                    ExplosionParams params = ExplosionParams.STREAM_CODEC.decode(buf);
                    return new ExplosionDetonatePayload(dimension, origin, seed, params);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ExplosionDetonatePayload p) {
                    ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, p.dimension());
                    buf.writeFloat(p.origin().x);
                    buf.writeFloat(p.origin().y);
                    buf.writeFloat(p.origin().z);
                    buf.writeLong(p.seed());
                    ExplosionParams.STREAM_CODEC.encode(buf, p.params());
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ExplosionDetonatePayload payload, IPayloadContext context) {
        Level level = context.player().level();
        if (payload.dimension() != level.dimension()) {
            MachineMax.LOGGER.error("从错误维度收到起爆包: " + payload.dimension());
            return;
        }
        // 只建立表现条目，不建逻辑实例
        ExplosionManager.get(level).addVisual(new BlastFrontVisual(payload.origin(), payload.params(), payload.seed()));
        // 起爆一次性表现：粒子 + 传播音效
        BlastDetonationEffects.playOnDetonate(level, payload.origin(), payload.params());
    }
}
