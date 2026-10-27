package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSwitchHelper;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchReason;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 换座请求（客户端→服务端）。
 *
 * <p>载荷带的是<strong>具体目标座位</strong>，不是方向或序号：循环键先在本地按几何顺序算出下一个
 * 可用座位，再把 {@code subPartId + seatName} 发出去；将来的点击、数字键或径向菜单直接填被点的
 * 座位，协议不变。服务端因此不需要排序，也不生成任何编号——两端顺序不一致不影响结果。</p>
 *
 * @param subPartId 目标座位所在子零件的全局 ID
 * @param seatName  目标座位的子系统名
 */
public record SeatSwitchPayload(int subPartId, String seatName) implements CustomPacketPayload {

    /** 同一玩家的换座请求最小间隔（tick）；一次成功换座要跑上车点交接、两组信号切换与相机距离重算 */
    private static final long MIN_INTERVAL_TICKS = 10;

    /** 上次被接受的请求时刻，按玩家 UUID 记录 */
    private static final Map<UUID, Long> LAST_REQUEST = new ConcurrentHashMap<>();

    public static final CustomPacketPayload.Type<SeatSwitchPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "seat_switch_payload"));
    public static final StreamCodec<FriendlyByteBuf, SeatSwitchPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SeatSwitchPayload decode(FriendlyByteBuf buf) {
            return new SeatSwitchPayload(buf.readInt(), buf.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, @NotNull SeatSwitchPayload payload) {
            buffer.writeInt(payload.subPartId());
            buffer.writeUtf(payload.seatName());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 服务端主线程：解析目标座位、逐条校验、执行迁移，最后回一条纯状态包。
     *
     * <p>校验顺序即失败原因的优先级：先确认目标座位存在，再确认发起者确实坐在某个座位上，
     * 然后才是目标座位自身的可用性。节流放在最后一步，避免被无效请求刷掉正常请求的窗口。</p>
     */
    public static void serverHandler(final SeatSwitchPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            // 1) 目标子零件
            DestroyableObject object = ObjectManager.getDestroyableObject(player.level(), payload.subPartId());
            if (!(object instanceof SubPart targetSubPart)) {
                MachineMax.LOGGER.debug("收到换座请求但维度 {} 中不存在 SubPart(id={})",
                        player.level().dimension().location(), payload.subPartId());
                return;
            }
            // 2) 目标座位
            AbstractSubsystem subsystem = targetSubPart.subsystems.get(payload.seatName());
            if (!(subsystem instanceof SeatSubsystem to)) {
                MachineMax.LOGGER.debug("收到换座请求但 SubPart(id={}) 上没有座位 {}",
                        payload.subPartId(), payload.seatName());
                return;
            }
            // 3) 发起者当前座位
            if (!(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem from)) {
                reply(serverPlayer, payload, to, SeatSwitchReason.NOT_SEATED);
                return;
            }
            // 4) 发起者确实坐在这个座位上并骑着它的实体
            if (from.passenger != player || from.getSubPart() == null
                    || player.getVehicle() != from.getSubPart().getEntity()) {
                reply(serverPlayer, payload, to, SeatSwitchReason.NOT_SEATED);
                return;
            }
            // 5) 目标就是当前座位
            if (from == to) {
                reply(serverPlayer, payload, to, SeatSwitchReason.SAME_SEAT);
                return;
            }
            // 6) 同一装配体
            if (from.getSubPart().getPart().assembly != to.getSubPart().getPart().assembly) {
                reply(serverPlayer, payload, to, SeatSwitchReason.DIFFERENT_ASSEMBLY);
                return;
            }
            // 7) 目标可用
            if (!to.isActive() || to.isDestroyed() || to.getSubPart().isDestroyed()) {
                reply(serverPlayer, payload, to, SeatSwitchReason.UNAVAILABLE);
                return;
            }
            // 8) 目标空着
            if (to.passenger != null || to.isOccupied()) {
                reply(serverPlayer, payload, to, SeatSwitchReason.OCCUPIED);
                return;
            }
            // 9) 目标有可骑乘实体
            if (to.getSubPart().getEntity() == null) {
                reply(serverPlayer, payload, to, SeatSwitchReason.NO_ENTITY);
                return;
            }
            // 10) 节流
            long now = player.level().getGameTime();
            Long last = LAST_REQUEST.get(player.getUUID());
            if (last != null && now - last < MIN_INTERVAL_TICKS) {
                reply(serverPlayer, payload, to, SeatSwitchReason.RATE_LIMITED);
                return;
            }
            LAST_REQUEST.put(player.getUUID(), now);
            pruneThrottle(now);

            SeatSwitchReason reason = SeatSwitchHelper.switchSeat(player, from, to);
            if (reason != SeatSwitchReason.OK) {
                reply(serverPlayer, payload, to, reason);
                return;
            }
            int controlGroup = to.getControlGroupSet() == null ? -1 : to.getControlGroupSet().getActiveIndex();
            // 成功：广播给维度内所有玩家——每个客户端的 controllingSubsystem 各一份，漏掉哪一份，
            // 那边的乘客就会被 getPassengerAttachmentPoint 摆到实体原点
            PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(),
                    new SeatSwitchResultPayload(player.getId(), to.getSubPart().getId(), to.getName(),
                            controlGroup, true, SeatSwitchReason.OK));
        });
    }

    /** 失败只回发起者：其他客户端不需要知道谁按键失败 */
    private static void reply(ServerPlayer player, SeatSwitchPayload payload, SeatSubsystem to, SeatSwitchReason reason) {
        int controlGroup = reason == SeatSwitchReason.OK && to.getControlGroupSet() != null
                ? to.getControlGroupSet().getActiveIndex() : -1;
        PacketDistributor.sendToPlayer(player, new SeatSwitchResultPayload(
                player.getId(), payload.subPartId(), payload.seatName(), controlGroup, false, reason));
    }

    /** 记录表超过一定规模时清掉早已过窗的条目：换座很稀疏，不需要无界增长 */
    private static void pruneThrottle(long now) {
        if (LAST_REQUEST.size() <= 256) return;
        LAST_REQUEST.entrySet().removeIf(entry -> now - entry.getValue() >= MIN_INTERVAL_TICKS);
    }
}
