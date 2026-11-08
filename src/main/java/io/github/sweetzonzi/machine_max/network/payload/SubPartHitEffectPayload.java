package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPartHitEffects;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import io.github.sweetzonzi.machine_max.util.NonFiniteDiagnostics;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
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
 * 零件命中表现包（服务端 → 客户端）：一次结算批次内该子部件受到的命中表现，每笔命中一条。
 *
 * <p>载荷只带<b>身份与几何</b>——子部件 id、碰撞箱骨骼名、命中点相对子部件的偏移、
 * 喷射轴，以及击穿/履带吸收两个标志。<b>音效事件与粒子效果 id 都不上线</b>：两者都由
 * 客户端按碰撞箱身份回查内容包里的同一份配置得到（音效走材质的 {@code sounds}，
 * 火花走材质的 {@code particles}，两者都可被碰撞箱的 {@code overwrite} 覆写），
 * 因此两端不可能出现"音效说击穿、火花说没击穿"这类错配，改效果也不需要动协议。</p>
 *
 * <p><b>喷射轴由服务端定</b>：击穿取命中速度方向（弹体穿出方向），未击穿与跳弹取命中面
 * 法线（火花沿装甲表面弹回）。客户端只负责把发射器的局部 +Z 旋到该轴上。</p>
 *
 * <p><b>广播半径按可见距离取，不按音效半径取</b>：同一次命中的音效可能只写在十几格，
 * 但火花能被更远看见，所以取 {@link #RADIUS}。</p>
 *
 * <p><b>调用线程：</b>发在主线程（{@code SubPart#settleAccumulatedDamage}），收在主线程
 * （{@code MainThreadPayloadHandler}）。</p>
 *
 * @see SubPart#settleAccumulatedDamage()
 */
public record SubPartHitEffectPayload(
        int subPartId,
        List<Entry> entries
) implements CustomPacketPayload {

    /**
     * 单次命中表现条目。
     *
     * @param hitBoxKey      碰撞箱骨骼名，即 {@code SubPart#hitBoxes} 的键
     * @param offsetX        命中点相对子部件呈现位置的偏移 X（世界轴）
     * @param offsetY        同上，Y
     * @param offsetZ        同上，Z
     * @param dirX           喷射轴 X（已归一化）
     * @param dirY           喷射轴 Y
     * @param dirZ           喷射轴 Z
     * @param penetrated     是否击穿
     * @param trackAbsorbed  是否被履带装甲层吸收（决定音效取履带型号而不是碰撞箱）
     */
    public record Entry(
            String hitBoxKey,
            float offsetX, float offsetY, float offsetZ,
            float dirX, float dirY, float dirZ,
            boolean penetrated,
            boolean trackAbsorbed
    ) {
        /** 偏移与轴向要能安全写进粒子发射器的位姿。 */
        public boolean isFinite() {
            return Float.isFinite(offsetX) && Float.isFinite(offsetY) && Float.isFinite(offsetZ)
                    && Float.isFinite(dirX) && Float.isFinite(dirY) && Float.isFinite(dirZ);
        }
    }

    /**
     * 单包条目上限，同时是解码端的硬边界。
     *
     * <p>条目按命中点计，不按碰撞箱合并——霰弹会在同一块装甲板上落下多个弹丸，每个命中点都该有自己的
     * 火花。因此生产侧在收集时封顶（{@code SubPart#collectHitEffect}）：超出的一律不入列，
     * 编码端也就不会写出解码端会拒收的包。一次霰弹或一次殉爆里，同一块装甲板叠出上百条表现条目
     * 并不罕见。</p>
     */
    public static final int MAX_ENTRIES = 64;

    /** 表现广播半径（格）。按火花可见距离取，而不是按音效的 fixed range。 */
    public static final double RADIUS = 96.0;

    /** 标志位：击穿。 */
    private static final int FLAG_PENETRATED = 1;
    /** 标志位：被履带吸收。 */
    private static final int FLAG_TRACK_ABSORBED = 1 << 1;

    public static final Type<SubPartHitEffectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subpart_hit_effect"));

    public static final StreamCodec<FriendlyByteBuf, SubPartHitEffectPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public @NotNull SubPartHitEffectPayload decode(FriendlyByteBuf buf) {
                    int subPartId = buf.readVarInt();
                    int count = buf.readVarInt();
                    if (count < 0 || count > MAX_ENTRIES) {
                        throw new DecoderException("SubPartHitEffectPayload entry count out of range: " + count);
                    }
                    List<Entry> entries = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        String hitBoxKey = buf.readUtf();
                        float ox = buf.readFloat();
                        float oy = buf.readFloat();
                        float oz = buf.readFloat();
                        float dx = buf.readFloat();
                        float dy = buf.readFloat();
                        float dz = buf.readFloat();
                        int flags = buf.readByte();
                        entries.add(new Entry(hitBoxKey, ox, oy, oz, dx, dy, dz,
                                (flags & FLAG_PENETRATED) != 0,
                                (flags & FLAG_TRACK_ABSORBED) != 0));
                    }
                    return new SubPartHitEffectPayload(subPartId, entries);
                }

                @Override
                public void encode(FriendlyByteBuf buf, SubPartHitEffectPayload pkt) {
                    buf.writeVarInt(pkt.subPartId());
                    buf.writeVarInt(pkt.entries().size());
                    for (Entry e : pkt.entries()) {
                        buf.writeUtf(e.hitBoxKey());
                        buf.writeFloat(e.offsetX());
                        buf.writeFloat(e.offsetY());
                        buf.writeFloat(e.offsetZ());
                        buf.writeFloat(e.dirX());
                        buf.writeFloat(e.dirY());
                        buf.writeFloat(e.dirZ());
                        int flags = (e.penetrated() ? FLAG_PENETRATED : 0)
                                | (e.trackAbsorbed() ? FLAG_TRACK_ABSORBED : 0);
                        buf.writeByte(flags);
                    }
                }
            };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端广播：只发给能看见火花的玩家。
     *
     * <p>条目数必须不超过 {@link #MAX_ENTRIES}：解码端超限即判失败，而客户端在解码处抛异常
     * 意味着断开连接。生产侧在收集时已封顶（{@code SubPart#collectHitEffect}）。</p>
     *
     * <p><b>调用线程：</b>主线程（{@link SubPart#settleAccumulatedDamage()}）。</p>
     *
     * @param level     服务端维度
     * @param center    广播中心（子部件的呈现位置）
     * @param subPartId 子部件 id
     * @param entries   本批命中的表现条目
     */
    public static void broadcast(ServerLevel level, Vec3 center, int subPartId, List<Entry> entries) {
        if (entries.isEmpty()) return;
        PacketDistributor.sendToPlayersNear(level, null,
                center.x, center.y, center.z, RADIUS,
                new SubPartHitEffectPayload(subPartId, entries));
    }

    /**
     * 客户端处理：按子部件与碰撞箱身份回查内容包配置，播放音效与命中火花。
     *
     * <p>子部件解不到（不在同步范围内、或已重建）或碰撞箱名对不上（内容包两端不一致）时
     * 跳过该条，不报错——这两者都只是"本机没有这份内容"。</p>
     *
     * <p><b>调用线程：</b>主线程（NeoForge 网络处理器）。</p>
     */
    public static void handle(final SubPartHitEffectPayload payload, final IPayloadContext context) {
        Level level = context.player().level();
        DestroyableObject obj = ObjectManager.getDestroyableObject(level, payload.subPartId());
        if (!(obj instanceof SubPart subPart)) return;

        for (Entry e : payload.entries()) {
            if (!e.isFinite()) {
                NonFiniteDiagnostics.reportOnce("subpart-hit-effect/" + payload.subPartId(),
                        () -> NonFiniteDiagnostics.world(level) + " rejectedHitEffect=" + e);
                continue;
            }
            HitBox hitBox = subPart.hitBoxes.get(e.hitBoxKey());
            if (hitBox == null) continue;
            SubPartHitEffects.play(level, subPart, hitBox,
                    new Vec3(e.offsetX(), e.offsetY(), e.offsetZ()),
                    new Vec3(e.dirX(), e.dirY(), e.dirZ()),
                    e.penetrated(), e.trackAbsorbed());
        }
    }
}
