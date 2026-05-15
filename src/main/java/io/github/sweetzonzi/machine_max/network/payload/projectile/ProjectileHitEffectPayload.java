package io.github.sweetzonzi.machine_max.network.payload.projectile;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 投射物命中视觉效果网络包。
 * <p>
 * 服务端在检测到投射物命中后广播到维度内所有玩家。
 * 客户端收到后播放相应的粒子特效和音效。
 * <p>
 * 命中装甲目标（{@code isArmorHit = true}）时产生火花 + 烟雾粒子；
 * 命中地形时产生粉尘 + 烟雾粒子。
 */
public record ProjectileHitEffectPayload(
    double hitX,
    double hitY,
    double hitZ,
    double normalX,
    double normalY,
    double normalZ,
    boolean isArmorHit
) implements CustomPacketPayload {

    public static final Type<ProjectileHitEffectPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectile_hit_effect"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileHitEffectPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ProjectileHitEffectPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ProjectileHitEffectPayload(
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readBoolean());
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, ProjectileHitEffectPayload value) {
            buffer.writeDouble(value.hitX);
            buffer.writeDouble(value.hitY);
            buffer.writeDouble(value.hitZ);
            buffer.writeDouble(value.normalX);
            buffer.writeDouble(value.normalY);
            buffer.writeDouble(value.normalZ);
            buffer.writeBoolean(value.isArmorHit);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端处理：在命中位置播放粒子特效。
     * <p>
     * 装甲命中 → 火焰粒子 + 烟雾
     * 地形命中 → 篝火烟雾 + 普通烟雾
     */
    public static void handle(final ProjectileHitEffectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            Vec3 hitPoint = new Vec3(payload.hitX, payload.hitY, payload.hitZ);
            Vec3 hitNormal = new Vec3(payload.normalX, payload.normalY, payload.normalZ);

            if (payload.isArmorHit) {
                // 装甲命中：火花 + 浓烟
                level.addParticle(ParticleTypes.SMOKE,
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    hitNormal.x * 0.5, hitNormal.y * 0.5, hitNormal.z * 0.5);
                for (int i = 0; i < 5; i++) {
                    level.addParticle(ParticleTypes.FLAME,
                        hitPoint.x, hitPoint.y, hitPoint.z,
                        hitNormal.x * (0.3 + level.random.nextDouble() * 0.5),
                        hitNormal.y * (0.3 + level.random.nextDouble() * 0.5),
                        hitNormal.z * (0.3 + level.random.nextDouble() * 0.5));
                }
            } else {
                // 地形命中：粉尘 + 轻烟
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    0, 0.1, 0);
                level.addParticle(ParticleTypes.SMOKE,
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    hitNormal.x * 0.3, hitNormal.y * 0.3, hitNormal.z * 0.3);
            }
        });
    }
}
