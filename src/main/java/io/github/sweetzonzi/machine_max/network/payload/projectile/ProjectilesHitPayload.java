package io.github.sweetzonzi.machine_max.network.payload.projectile;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * 单次命中条目。
     * <p>
     * {@code hitBlockPos} 为非 null 时表示地形命中，客户端据此查方块类型播放对应粒子/音效。
     * 为 null 时表示 SubPart/Entity 命中，命中包仅做弹道状态同步，特效由各自的自理包负责。
     */
    public record HitEntry(
            int objId,
            double hitX, double hitY, double hitZ,
            double normalX, double normalY, double normalZ,
            boolean destroyed,
            double newVelX, double newVelY, double newVelZ,
            @Nullable Long hitBlockPos
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
                                buf.readBoolean() ? buf.readLong() : null));
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
                        buf.writeBoolean(e.hitBlockPos != null);
                        if (e.hitBlockPos != null) buf.writeLong(e.hitBlockPos);
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
     * 客户端处理：逐条更新 SoA 状态并播放地形命中特效。
     * <p>
     * {@code hitBlockPos != null}：地形命中，客户端查方块类型播放原生粒子/音效。
     * {@code hitBlockPos == null}：SubPart/Entity 命中，仅做弹道状态同步，特效自理。
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

                // 更新 SoA 状态：所有命中统一修正位置到实际命中点，暂停一帧外推以绘制折角
                int idx = pm.findIndexByObjId(e.objId);
                if (idx >= 0) {
                    pm.posX[idx] = (float) e.hitX;
                    pm.posY[idx] = (float) e.hitY;
                    pm.posZ[idx] = (float) e.hitZ;
                    pm.skipExtrapolate[idx] = true;
                    if (e.destroyed) {
                        pm.alive[idx] = false;
                        // 标记客户端投射物对象为已命中，确保 MMProjectileEntity.isAlive() 返回 false
                        DestroyableObject obj = pm.getProjectile(e.objId);
                        if (obj instanceof IProjectile proj) {
                            proj.markHit();
                        }
                    } else {
                        // 跳弹/穿透：同步修正速度
                        pm.velX[idx] = (float) e.newVelX;
                        pm.velY[idx] = (float) e.newVelY;
                        pm.velZ[idx] = (float) e.newVelZ;
                    }
                }

                // 粒子特效与音效
                // ============================================================
                if (e.hitBlockPos != null) {
                    // ── 地形命中：客户端根据 BlockPos 查方块，播放方块原生的粒子/音效 ──
                    BlockPos bp = BlockPos.of(e.hitBlockPos);
                    BlockState state = level.getBlockState(bp);
                    if (!state.isAir()) {
                        // 方块破坏粒子（泥土→土屑，石头→石屑，木头→木屑...）
                        level.addAlwaysVisibleParticle(
                                new BlockParticleOption(ParticleTypes.BLOCK, state),
                                hitPoint.x, hitPoint.y, hitPoint.z,
                                hitNormal.x, hitNormal.y, hitNormal.z);
                        level.addAlwaysVisibleParticle(
                                new BlockParticleOption(ParticleTypes.BLOCK, state),
                                hitPoint.x, hitPoint.y, hitPoint.z,
                                hitNormal.x, hitNormal.y, hitNormal.z);
                        // 方块原生命中音效
                        SoundType soundType = state.getSoundType();
                        level.playLocalSound(hitPoint.x, hitPoint.y, hitPoint.z,
                                soundType.getHitSound(), net.minecraft.sounds.SoundSource.BLOCKS,
                                soundType.getVolume(), soundType.getPitch(), false);
                    }
                }
                // 若 hitBlockPos == null：SubPart/Entity 命中，命中包仅做弹道状态同步
                // 特效由 SubPartHitEffectPayload 等自理包负责
            }
        });
    }
}
