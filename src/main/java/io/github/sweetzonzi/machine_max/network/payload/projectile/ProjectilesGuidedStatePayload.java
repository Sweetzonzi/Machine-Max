package io.github.sweetzonzi.machine_max.network.payload.projectile;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 投射物权威位姿快照包（服务端→客户端）。
 * <p>
 * 承载两类客户端无法自行复现运动的投射物：
 * <ul>
 *   <li><b>制导弹</b>——客户端复现制导必然静默漂移（PN 依赖视线角速度历史，
 *       导引头还有视场/丢锁状态）</li>
 *   <li><b>刚体投射物</b>——运动由 Bullet 刚体驱动，客户端没有对应积分器，
 *       也不参与 {@code clientExtrapolate}</li>
 * </ul>
 * 服务端每 tick 广播位置/速度/寿命快照，客户端以之覆盖本地 SoA。
 * <b>朝向不随本包传输</b>：刚体姿态约定为"弹轴 = 速度方向"，
 * 两端用同一函数（{@code RigidProjectile#facingFromVelocity}）从速度推导。
 * <p>
 * 与 {@link ProjectilesSpawnPayload} 的分工：生成包只负责"创建 + 初速"，
 * 本包负责此后每 tick 的状态覆盖。
 * <p>
 * 无制导的质点弹不进入本包，客户端行为不受影响。
 * <p>
 * <b>调用线程：</b>主线程（由 {@link ProjectileManager#postTick()} 调用）。
 *
 * @see ProjectileManager#flushAuthoritativeState()
 */
public record ProjectilesGuidedStatePayload(
        List<StateEntry> entries
) implements CustomPacketPayload {

    public static final Type<ProjectilesGuidedStatePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "projectiles_guided_state"));

    /**
     * 单个投射物的状态条目。
     * <p>
     * {@code lifetime} 一并携带：质点弹在客户端每 tick 多次递减寿命，
     * 刚体弹的寿命则完全由本快照覆盖——两种情况都需要服务端值兜底，
     * 否则客户端会提前清理该投射物。
     *
     * @param objId    DestroyableObject ID
     * @param posX     世界坐标 X
     * @param posY     世界坐标 Y
     * @param posZ     世界坐标 Z
     * @param velX     速度 X (m/s)
     * @param velY     速度 Y (m/s)
     * @param velZ     速度 Z (m/s)
     * @param lifetime 剩余存活 tick
     */
    public record StateEntry(
            int objId,
            double posX, double posY, double posZ,
            double velX, double velY, double velZ,
            int lifetime
    ) {}

    public static final StreamCodec<RegistryFriendlyByteBuf, ProjectilesGuidedStatePayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public ProjectilesGuidedStatePayload decode(RegistryFriendlyByteBuf buf) {
                    int count = buf.readVarInt();
                    List<StateEntry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        entries.add(new StateEntry(
                                buf.readVarInt(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readDouble(), buf.readDouble(), buf.readDouble(),
                                buf.readVarInt()
                        ));
                    }
                    return new ProjectilesGuidedStatePayload(entries);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, ProjectilesGuidedStatePayload pkt) {
                    buf.writeVarInt(pkt.entries.size());
                    for (StateEntry e : pkt.entries) {
                        buf.writeVarInt(e.objId);
                        buf.writeDouble(e.posX);
                        buf.writeDouble(e.posY);
                        buf.writeDouble(e.posZ);
                        buf.writeDouble(e.velX);
                        buf.writeDouble(e.velY);
                        buf.writeDouble(e.velZ);
                        buf.writeVarInt(e.lifetime);
                    }
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端广播：向维度内所有玩家发送一批投射物权威位姿快照。
     * <p>
     * <b>调用线程：</b>仅主线程（{@link ProjectileManager#postTick()}）。
     *
     * @param serverLevel 服务端维度
     * @param entries     本批状态条目（从 SoA 直接读取）
     */
    public static void broadcast(ServerLevel serverLevel, List<StateEntry> entries) {
        PacketDistributor.sendToPlayersInDimension(serverLevel,
                new ProjectilesGuidedStatePayload(entries));
    }

    /**
     * 客户端处理：逐条覆盖本地 SoA 的位姿/速度/寿命。
     * <p>
     * 复用既有的 {@link ProjectileManager#syncPointProjectileState}（语义完全吻合）。
     * 找不到 objId 时静默跳过——生成包可能尚未到达或已销毁。
     * <p>
     * <b>调用线程：</b>主线程（NeoForge 网络处理器）。
     */
    public static void handle(final ProjectilesGuidedStatePayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Level level = context.player().level();
            ProjectileManager pm = ObjectManager.levelProjectileManagers.get(level);
            if (pm == null) return;

            for (StateEntry entry : payload.entries) {
                pm.syncPointProjectileState(
                        entry.objId,
                        new Vector3f((float) entry.posX, (float) entry.posY, (float) entry.posZ),
                        new Vector3f((float) entry.velX, (float) entry.velY, (float) entry.velZ),
                        entry.lifetime);
            }
        });
    }
}
