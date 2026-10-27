package io.github.sweetzonzi.machine_max.network.payload;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.DestroyableObject;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchFeedback;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchReason;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 换座结果（服务端→客户端），纯状态包。
 *
 * <p>只写 {@code passenger}/{@code occupied}/{@code controllingSubsystem} 与活动控制组，
 * 不调 {@code interactBox.interact}、{@code setPassenger}、{@code removePassenger}——
 * 那些路径会重采样上车点、{@code stopRiding}、给本地玩家加一次冲量。这些字段也没有原版包承载：
 * {@code ClientboundSetPassengersPacket} 只带骑乘关系，而同一子零件换座时 {@code getVehicle()} 根本不变。</p>
 *
 * <p>成功时广播给维度内所有玩家：{@code controllingSubsystem} 每个客户端各一份，
 * {@code MMPartEntity.getPassengerAttachmentPoint} 在每个客户端都读它，漏掉哪一份，
 * 那边的乘客就被摆到实体原点。失败只回发起者。</p>
 *
 * @param passengerEntityId 换座的乘客实体 id
 * @param subPartId         目标座位所在子零件的全局 ID
 * @param seatName          目标座位的子系统名
 * @param controlGroup      目标座位复位后的活动控制组索引，失败时为 -1
 * @param success           是否完成
 * @param reason            结局，成功固定为 {@link SeatSwitchReason#OK}
 */
public record SeatSwitchResultPayload(int passengerEntityId, int subPartId, String seatName,
                                      int controlGroup, boolean success, SeatSwitchReason reason)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SeatSwitchResultPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "seat_switch_result_payload"));
    public static final StreamCodec<FriendlyByteBuf, SeatSwitchResultPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull SeatSwitchResultPayload decode(FriendlyByteBuf buf) {
            int passenger = buf.readInt();
            int subPart = buf.readInt();
            String seat = buf.readUtf();
            int group = buf.readVarInt() - 1;
            boolean success = buf.readBoolean();
            SeatSwitchReason reason = SeatSwitchReason.values()[buf.readVarInt()];
            return new SeatSwitchResultPayload(passenger, subPart, seat, group, success, reason);
        }

        @Override
        public void encode(FriendlyByteBuf buf, @NotNull SeatSwitchResultPayload payload) {
            buf.writeInt(payload.passengerEntityId());
            buf.writeInt(payload.subPartId());
            buf.writeUtf(payload.seatName());
            // 索引可能为 -1（无子控制组激活），用 +1 偏置写成无符号 varint
            buf.writeVarInt(payload.controlGroup() + 1);
            buf.writeBoolean(payload.success());
            buf.writeVarInt(payload.reason().ordinal());
        }
    };

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 客户端主线程：把结果落到本地状态。
     *
     * <p>顺序按依赖走：先定位乘客与目标座位，再清旧座位的本地字段、写新座位，
     * 最后（仅本地玩家、仅跨子零件时）主动对齐骑乘关系——服务端 {@code startRiding} 触发的
     * 原版 riders 包要下一 tick 才发，广播包会先到；客户端自己对齐后，原版包到达时重建列表是幂等的。</p>
     */
    public static void clientHandler(final SeatSwitchResultPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            Player local = context.player();
            Level level = local.level();
            SeatSubsystem target = resolveSeat(level, payload.subPartId(), payload.seatName());
            boolean localPassenger = payload.passengerEntityId() == local.getId();

            if (!payload.success()) {
                // 只有发起者的界面需要提示原因；其他客户端连这张标记都不必知道
                if (localPassenger) {
                    SeatSwitchFeedback.onResult(false, payload.reason(), target);
                    local.displayClientMessage(SeatSwitchFeedback.failureMessage(), true);
                }
                return;
            }

            if (localPassenger) SeatSwitchFeedback.onResult(true, SeatSwitchReason.OK, target);
            if (target == null) {
                // 目标座位还没在本地建好：它之后构造时走预设路径，活动组自然等于服务端刚写入的值
                return;
            }
            Entity entity = level.getEntity(payload.passengerEntityId());
            if (!(entity instanceof LivingEntity passenger)) return;

            SubPart targetSubPart = target.getSubPart();
            if (targetSubPart == null || targetSubPart.getEntity() == null) return;

            // 旧座位：客户端字段只用于显示，权威值仍在服务端（occupied 由同步值纠正）
            if (((IEntityMixin) passenger).machine_Max$getControllingSubsystem() instanceof SeatSubsystem previous
                    && previous != target) {
                previous.clientClearSeat();
            }
            target.clientAttachSeat(passenger, payload.controlGroup());

            if (localPassenger) {
                if (passenger.getVehicle() != targetSubPart.getEntity()) {
                    // 跨子零件：主动对齐，不等原版 riders 包；客户端 stopRiding 不触发下车搜索，也没有冲量
                    passenger.startRiding(targetSubPart.getEntity(), true);
                }
                local.displayClientMessage(SeatSwitchFeedback.successMessage(target), true);
            }
            // 其他玩家的骑乘关系交给原版 riders 包：提前 startRiding 反而可能与它打架
        });
    }

    @Nullable
    private static SeatSubsystem resolveSeat(Level level, int subPartId, String seatName) {
        DestroyableObject object = ObjectManager.getDestroyableObject(level, subPartId);
        if (!(object instanceof SubPart subPart)) return null;
        AbstractSubsystem subsystem = subPart.subsystems.get(seatName);
        return subsystem instanceof SeatSubsystem seat ? seat : null;
    }
}
