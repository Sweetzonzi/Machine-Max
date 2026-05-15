package io.github.sweetzonzi.machine_max.network.payload;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.PointProjectile;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.projectile.RigidProjectile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

/**
 * 投射物创建网络包（服务端→客户端）。
 * <p>
 * 服务端在 {@link PointProjectile} 或 {@link RigidProjectile} 构造后立即
 * 广播到维度内所有玩家。客户端收到后在本地构造对应投射物实例，
 * 并注册到客户端 {@link ProjectileManager} SoA 以供渲染。
 */
public record ProjectileSpawnPayload(
    int objId,
    ResourceLocation typeKey,
    double posX, double posY, double posZ,
    double velX, double velY, double velZ,
    int maxLifetime,
    boolean isRigid
) implements CustomPacketPayload {

    public static final Type<ProjectileSpawnPayload> TYPE = new Type<>(
        ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectile_spawn"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectileSpawnPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull ProjectileSpawnPayload decode(RegistryFriendlyByteBuf buffer) {
            return new ProjectileSpawnPayload(
                buffer.readInt(),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readInt(),
                buffer.readBoolean());
        }

        @Override
        public void encode(@NotNull RegistryFriendlyByteBuf buffer, ProjectileSpawnPayload value) {
            buffer.writeInt(value.objId);
            ResourceLocation.STREAM_CODEC.encode(buffer, value.typeKey);
            buffer.writeDouble(value.posX);
            buffer.writeDouble(value.posY);
            buffer.writeDouble(value.posZ);
            buffer.writeDouble(value.velX);
            buffer.writeDouble(value.velY);
            buffer.writeDouble(value.velZ);
            buffer.writeInt(value.maxLifetime);
            buffer.writeBoolean(value.isRigid);
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端广播：向维度内所有玩家发送投射物创建包。
     *
     * @param level        维度
     * @param objId        投射物 DestroyableObject ID
     * @param typeKey      投射物类型注册键
     * @param position     初始世界坐标（JME）
     * @param velocity     初始速度（JME）
     * @param maxLifetime  最大存活 tick
     * @param isRigid      是否刚体投射物
     */
    public static void broadcast(
        Level level, int objId, ResourceLocation typeKey,
        Vector3f position, Vector3f velocity, int maxLifetime, boolean isRigid
    ) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectileSpawnPayload(objId, typeKey,
                    position.x, position.y, position.z,
                    velocity.x, velocity.y, velocity.z,
                    maxLifetime, isRigid));
        }
    }

    /**
     * 客户端处理：构造本地投射物实例并注册到 SoA。
     */
    public static void handle(final ProjectileSpawnPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            ProjectileType type = ProjectileType.get(level, payload.typeKey);
            if (type == null) return;

            Vector3f pos = new Vector3f((float) payload.posX, (float) payload.posY, (float) payload.posZ);
            Vector3f vel = new Vector3f((float) payload.velX, (float) payload.velY, (float) payload.velZ);

            ProjectileManager pm = ObjectManager.getOrCreateProjectileManager(level);
            if (payload.isRigid) {
                RigidProjectile rp = new RigidProjectile(level, type, pos, vel);
                rp.setId(payload.objId);
                pm.addRigidProjectile(rp);
            } else {
                PointProjectile pp = new PointProjectile(level, type, pos, vel);
                pp.setId(payload.objId);
                pm.addPointProjectile(pp);
            }
        });
    }
}
