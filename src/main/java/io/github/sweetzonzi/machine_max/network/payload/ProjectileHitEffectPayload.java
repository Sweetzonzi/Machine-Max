package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

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

    public static void handle(final ProjectileHitEffectPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            Vec3 hitPoint = new Vec3(payload.hitX, payload.hitY, payload.hitZ);
            Vec3 hitNormal = new Vec3(payload.normalX, payload.normalY, payload.normalZ);

            if (payload.isArmorHit) {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    hitNormal.x * 0.5, hitNormal.y * 0.5, hitNormal.z * 0.5);
                for (int i = 0; i < 5; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.FLAME,
                        hitPoint.x, hitPoint.y, hitPoint.z,
                        hitNormal.x * (0.3 + level.random.nextDouble() * 0.5),
                        hitNormal.y * (0.3 + level.random.nextDouble() * 0.5),
                        hitNormal.z * (0.3 + level.random.nextDouble() * 0.5));
                }
            } else {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    0, 0.1, 0);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE,
                    hitPoint.x, hitPoint.y, hitPoint.z,
                    hitNormal.x * 0.3, hitNormal.y * 0.3, hitNormal.z * 0.3);
            }
        });
    }
}
