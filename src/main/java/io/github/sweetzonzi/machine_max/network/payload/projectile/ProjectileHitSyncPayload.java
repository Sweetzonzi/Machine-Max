package io.github.sweetzonzi.machine_max.network.payload.projectile;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 投射物命中同步包（服务端→客户端）。
 * <p>
 * 携带命中结果状态 + 视觉特效信息，替代旧的纯视觉 {@link ProjectileHitEffectPayload}。
 * 客户端收到后同时更新 SoA 状态和播放粒子特效，确保渲染和逻辑同步。
 */
public record ProjectileHitSyncPayload(
    int objId,
    double hitX, double hitY, double hitZ,
    double normalX, double normalY, double normalZ,
    boolean destroyed,
    double newVelX, double newVelY, double newVelZ,
    boolean isArmorHit
) implements CustomPacketPayload {

    public static final Type<ProjectileHitSyncPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectile_hit_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileHitSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ProjectileHitSyncPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ProjectileHitSyncPayload(
                buffer.readInt(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readBoolean(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readBoolean());
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, ProjectileHitSyncPayload value) {
            buffer.writeInt(value.objId);
            buffer.writeDouble(value.hitX);
            buffer.writeDouble(value.hitY);
            buffer.writeDouble(value.hitZ);
            buffer.writeDouble(value.normalX);
            buffer.writeDouble(value.normalY);
            buffer.writeDouble(value.normalZ);
            buffer.writeBoolean(value.destroyed);
            buffer.writeDouble(value.newVelX);
            buffer.writeDouble(value.newVelY);
            buffer.writeDouble(value.newVelZ);
            buffer.writeBoolean(value.isArmorHit);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端广播：向维度内所有玩家发送命中同步包。
     */
    public static void broadcast(
        ServerLevel serverLevel, int objId, Vec3 hitPoint, Vec3 hitNormal,
        boolean destroyed, Vector3f newVelocity, boolean isArmorHit
    ) {
        PacketDistributor.sendToPlayersInDimension(serverLevel,
            new ProjectileHitSyncPayload(
                objId,
                hitPoint.x, hitPoint.y, hitPoint.z,
                hitNormal.x, hitNormal.y, hitNormal.z,
                destroyed,
                destroyed ? 0 : newVelocity.x,
                destroyed ? 0 : newVelocity.y,
                destroyed ? 0 : newVelocity.z,
                isArmorHit));
    }

    /**
     * 客户端处理：更新 SoA 状态 + 播放粒子特效。
     */
    public static void handle(final ProjectileHitSyncPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
            if (pm == null) return;

            Vec3 hitPoint = new Vec3(payload.hitX, payload.hitY, payload.hitZ);
            Vec3 hitNormal = new Vec3(payload.normalX, payload.normalY, payload.normalZ);

            // 更新 SoA 状态
            int idx = pm.findIndexByObjId(payload.objId);
            if (idx >= 0) {
                if (payload.destroyed) {
                    pm.alive[idx] = false;
                } else {
                    pm.velX[idx] = (float) payload.newVelX;
                    pm.velY[idx] = (float) payload.newVelY;
                    pm.velZ[idx] = (float) payload.newVelZ;
                }
            }

            // ========== 粒子特效 ==========
            if (payload.isArmorHit) {
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
