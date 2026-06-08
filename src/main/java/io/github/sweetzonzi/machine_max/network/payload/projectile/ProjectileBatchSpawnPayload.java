package io.github.sweetzonzi.machine_max.network.payload.projectile;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.projectile.IProjectile;
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
 * 替代旧 {@link ProjectileSpawnPayload} 的单发模式，将一帧内同一发射器产生的
 * 多发投射物合并为一个包广播，减少网络帧头开销。
 * <p>
 * 不携带 {@code maxLifetime} 和 {@code isRigid}——客户端从 {@link ProjectileType} 本地读取。
 * <p>
 * <b>调用线程：</b>主线程（由 {@link ProjectileManager#flushProjectileEntities()} 调用）。
 *
 * @see ProjectileManager#flushProjectileEntities()
 */
public record ProjectileBatchSpawnPayload(
        List<SpawnEntry> entries
) implements CustomPacketPayload {

    public static final Type<ProjectileBatchSpawnPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectile_batch_spawn"));

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

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileBatchSpawnPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProjectileBatchSpawnPayload decode(RegistryFriendlyByteBuf buf) {
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
                    return new ProjectileBatchSpawnPayload(entries);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ProjectileBatchSpawnPayload pkt) {
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
     * 从 {@link IProjectile} 引用列表内部构造 {@link SpawnEntry}，调用方不需要接触 SpawnEntry 类型。
     * 即使投射物已销毁（{@code isAlive() == false}），持有的 Java 引用仍可读取 pos/vel/typeKey，
     * 保证出膛即命中的投射物也能正确在客户端生成视觉效果。
     *
     * @param level       服务端维度
     * @param projectiles 本批待广播的 {@link IProjectile} 引用列表
     */
    public static void broadcast(ServerLevel level, List<IProjectile> projectiles) {
        List<SpawnEntry> entries = new ArrayList<>(projectiles.size());
        for (IProjectile p : projectiles) {
            Vector3f pos = p.getPosition();
            Vector3f vel = p.getVelocity();
            ResourceLocation typeKey = p.getProjectileType().getRegistryKey();
            entries.add(new SpawnEntry(
                    ((DestroyableObject) p).getId(), typeKey,
                    pos.x, pos.y, pos.z, vel.x, vel.y, vel.z));
        }
        if (!entries.isEmpty()) {
            PacketDistributor.sendToPlayersInDimension(level,
                    new ProjectileBatchSpawnPayload(entries));
        }
    }

    /**
     * 客户端处理：逐条构造本地投射物实例并注册到 SoA。
     * <p>
     * <b>调用线程：</b>主线程（NeoForge 网络处理器）。
     */
    public static void handle(final ProjectileBatchSpawnPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            for (SpawnEntry entry : payload.entries) {
                ProjectileType type = ProjectileType.get(level, entry.typeKey);
                if (type == null) continue;

                Vector3f pos = new Vector3f(
                        (float) entry.posX, (float) entry.posY, (float) entry.posZ);
                Vector3f vel = new Vector3f(
                        (float) entry.velX, (float) entry.velY, (float) entry.velZ);

                // create → addToLevel：ObjectManager 注册 + SoA 写入 + 客户端音效
                type.create(level, pos, vel);
            }
        });
    }
}
