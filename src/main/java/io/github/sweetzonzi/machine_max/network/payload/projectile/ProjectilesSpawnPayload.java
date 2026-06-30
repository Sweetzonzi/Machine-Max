package io.github.sweetzonzi.machine_max.network.payload.projectile;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * 批量投射物生成网络包（服务端→客户端）。
 * <p>
 * 将一帧内同一发射器产生的多发投射物合并为一个包广播，减少网络帧头开销。
 * <p>
 * 不携带 {@code maxLifetime} 和 {@code isRigid}——客户端从 {@link ProjectileType} 本地读取。
 * <p>
 * <b>调用线程：</b>主线程（由 {@link ProjectileManager#flushProjectileEntities()} 调用）。
 *
 * @see ProjectileManager#flushProjectileEntities()
 */
public record ProjectilesSpawnPayload(
        List<SpawnEntry> entries
) implements CustomPacketPayload {

    public static final Type<ProjectilesSpawnPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectiles_spawn"));

    /**
     * 单个投射物生成条目。
     *
     * @param objId    DestroyableObject ID
     * @param typeKey  投射物类型注册键
     * @param posX     世界坐标 X
     * @param posY     世界坐标 Y
     * @param posZ     世界坐标 Z
     * @param velX     初速 X (m/s)
     * @param velY     初速 Y (m/s)
     * @param velZ     初速 Z (m/s)
     */
    public record SpawnEntry(
            int objId,
            ResourceLocation typeKey,
            double posX, double posY, double posZ,
            double velX, double velY, double velZ
    ) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectilesSpawnPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProjectilesSpawnPayload decode(RegistryFriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<SpawnEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new SpawnEntry(
                                buf.readVarInt(),
                                ResourceLocation.STREAM_CODEC.decode(buf),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble()
                        ));
                    }
                    return new ProjectilesSpawnPayload(entries);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ProjectilesSpawnPayload pkt) {
                    buf.writeVarInt(pkt.entries.size());
                    for (SpawnEntry e : pkt.entries) {
                        buf.writeVarInt(e.objId);
                        ResourceLocation.STREAM_CODEC.encode(buf, e.typeKey);
                        buf.writeDouble(e.posX);
                        buf.writeDouble(e.posY);
                        buf.writeDouble(e.posZ);
                        buf.writeDouble(e.velX);
                        buf.writeDouble(e.velY);
                        buf.writeDouble(e.velZ);
                    }
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端广播：向维度内所有玩家发送一批投射物生成包。
     * <p>
     * <b>调用线程：</b>主线程（{@link ProjectileManager#flushProjectileEntities()} 内部调用）。
     * <p>
     * 调用方从 {@code PendingSpawn} 快照构造 {@link SpawnEntry} 列表传入，
     * 确保初速和炮口位置是创建时刻的快照，而非物理积分后的当前值。
     *
     * @param level   服务端维度
     * @param entries 本批生成条目（已从创建快照转换）
     */
    public static void broadcast(ServerLevel level, List<SpawnEntry> entries) {
        PacketDistributor.sendToPlayersInDimension(level,
                new ProjectilesSpawnPayload(entries));
    }

    /**
     * 客户端处理：逐条构造本地投射物实例并注册到 SoA。
     * <p>
     * <b>调用线程：</b>主线程（NeoForge 网络处理器）。
     */
    public static void handle(final ProjectilesSpawnPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            for (SpawnEntry entry : payload.entries) {
                ProjectileType type = ProjectileType.get(level, entry.typeKey);
                if (type == null) continue;

                Vector3f pos = new Vector3f(
                        (float) entry.posX, (float) entry.posY, (float) entry.posZ);
                Vector3f vel = new Vector3f(
                        (float) entry.velX, (float) entry.velY, (float) entry.velZ);

                // createWithId → setId(服务端objId) → addToLevel：
                // ObjectManager 注册 + SoA 写入使用服务端 ID，确保后续命中包可匹配
                type.createWithId(level, pos, vel, entry.objId);
            }
        });
    }
}
