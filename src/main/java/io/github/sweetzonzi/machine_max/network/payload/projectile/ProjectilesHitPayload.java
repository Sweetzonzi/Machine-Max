package io.github.sweetzonzi.machine_max.network.payload.projectile;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile;
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

import java.util.ArrayList;
import java.util.List;

/**
 * 投射物批量命中同步包（服务端→客户端）。
 * <p>
 * 替代旧 {@code ProjectileHitSyncPayload} 的单发模式，将一帧内物理线程产生的
 * 所有命中事件合并为一个包广播。与 {@link ProjectilesSpawnPayload} 配合，
 * 保证"创建包先到、命中包后到"的严格顺序，消除近距目标穿透 bug。
 * <p>
 * 单发命中包约 73 bytes，批量包每增一条命中仅增 57 bytes（varInt count 前缀）。
 * 霰弹 + 穿透场景下最多可省 23 个协议帧头（~92 bytes/帧）。
 * <p>
 * <b>调用线程：</b>主线程（由 {@link ProjectileManager#flushPendingHitSyncs()} 调用）。
 *
 * @see ProjectileManager#flushPendingHitSyncs()
 */
public record ProjectilesHitPayload(
        List<HitEntry> entries
) implements CustomPacketPayload {

    /**
     * 单次命中条目，字段与旧单发命中包完全一致。
     */
    public record HitEntry(
            int objId,
            double hitX, double hitY, double hitZ,
            double normalX, double normalY, double normalZ,
            boolean destroyed,
            double newVelX, double newVelY, double newVelZ,
            boolean isArmorHit
    ) {}

    public static final Type<ProjectilesHitPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectiles_hit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectilesHitPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull ProjectilesHitPayload decode(RegistryFriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<HitEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new HitEntry(
                                buf.readVarInt(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readBoolean(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readBoolean()));
                    }
                    return new ProjectilesHitPayload(entries);
                }

                @Override
                public void encode(@NotNull RegistryFriendlyByteBuf buf, ProjectilesHitPayload pkt) {
                    buf.writeVarInt(pkt.entries.size());
                    for (HitEntry e : pkt.entries) {
                        buf.writeVarInt(e.objId);
                        buf.writeDouble(e.hitX);
                        buf.writeDouble(e.hitY);
                        buf.writeDouble(e.hitZ);
                        buf.writeDouble(e.normalX);
                        buf.writeDouble(e.normalY);
                        buf.writeDouble(e.normalZ);
                        buf.writeBoolean(e.destroyed);
                        buf.writeDouble(e.newVelX);
                        buf.writeDouble(e.newVelY);
                        buf.writeDouble(e.newVelZ);
                        buf.writeBoolean(e.isArmorHit);
                    }
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端广播：将命中条目列表批量发送到维度内所有玩家。
     * <p>
     * <b>调用线程：</b>仅主线程（由 {@link ProjectileManager#flushPendingHitSyncs()} 调用）。
     *
     * @param serverLevel 服务端维度
     * @param entries     命中条目列表（已从物理线程缓冲的 PendingHitSync 转换而来）
     */
    public static void broadcast(ServerLevel serverLevel, List<HitEntry> entries) {
        PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectilesHitPayload(entries));
    }

    /**
     * 客户端处理：逐条更新 SoA 状态 + 播放粒子特效。
     * <p>
     * 逻辑与旧单发命中包逐条处理完全一致。
     * 创建包可能尚未到达时（旧版本），命中条目会被 skip（findIndexByObjId 返回 -1），
     * 仅播放粒子特效。新版本中创建包严格先于命中包到达，因此不会出现此情况。
     * <p>
     * <b>调用线程：</b>主线程（NeoForge 网络处理器）。
     */
    public static void handle(final ProjectilesHitPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
            if (pm == null) return;

            for (HitEntry e : payload.entries) {
                Vec3 hitPoint = new Vec3(e.hitX, e.hitY, e.hitZ);
                Vec3 hitNormal = new Vec3(e.normalX, e.normalY, e.normalZ);

                // 更新 SoA 状态（命中位置修正、速度更新、销毁标记）
                int idx = pm.findIndexByObjId(e.objId);
                if (idx >= 0) {
                    if (e.destroyed) {
                        // 修正客户端外推导致的位置穿模：将 SoA 位置拉回到实际命中点
                        pm.posX[idx] = (float) e.hitX;
                        pm.posY[idx] = (float) e.hitY;
                        pm.posZ[idx] = (float) e.hitZ;
                        pm.alive[idx] = false;
                        // 标记客户端投射物对象为已命中，确保 MMProjectileEntity.isAlive() 返回 false
                        DestroyableObject obj = pm.getProjectile(e.objId);
                        if (obj instanceof IProjectile proj) {
                            proj.markHit();
                        }
                    } else {
                        pm.velX[idx] = (float) e.newVelX;
                        pm.velY[idx] = (float) e.newVelY;
                        pm.velZ[idx] = (float) e.newVelZ;
                    }
                }

                // 粒子特效（与旧单发命中包完全一致）
                if (e.isArmorHit) {
                    // 装甲命中：烟雾 + 火星
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
                    // 地形/非装甲命中：营火烟雾 + 普通烟雾
                    level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            hitPoint.x, hitPoint.y, hitPoint.z,
                            0, 0.1, 0);
                    level.addParticle(ParticleTypes.SMOKE,
                            hitPoint.x, hitPoint.y, hitPoint.z,
                            hitNormal.x * 0.3, hitNormal.y * 0.3, hitNormal.z * 0.3);
                }
            }
        });
    }
}
