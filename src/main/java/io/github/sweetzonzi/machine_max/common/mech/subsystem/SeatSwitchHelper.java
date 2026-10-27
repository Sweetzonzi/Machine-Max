package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchReason;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 车外登入之外的座位迁移：车内换座与跨子零件换座。
 *
 * <p>不走 {@link SeatSubsystem#setPassenger} 的完整下车/上车流程：那条路径会
 * {@code stopRiding} → 下车点搜索 → {@code dismountTo}，并给客户端玩家加一次冲量。
 * 对同一子零件的换座，那等于“先下车再上车”；跨子零件时还会把玩家短暂放到车外。</p>
 *
 * <p>整条路径在服务端主线程完成。客户端不做预测：结果包到达之前它不改任何座位状态，
 * 也不改骑乘关系，所以不存在“先设置、失败再撤回”。唯一可能失败的是跨实体挂载，
 * 而它被排在所有座位字段之前，失败时两个座位一个字段都没动。</p>
 */
public final class SeatSwitchHelper {

    /** 正在换座的乘客，仅覆盖一次 {@code startRiding} 调用；用于抑制下车点搜索 */
    private static final Set<UUID> SWITCHING = ConcurrentHashMap.newKeySet();

    private SeatSwitchHelper() {
    }

    /** 该乘客此刻是否处于换座挂载过程中 */
    public static boolean isSwitching(@Nullable Entity entity) {
        return entity != null && SWITCHING.contains(entity.getUUID());
    }

    public static void beginSwitch(@Nullable Entity entity) {
        if (entity != null) SWITCHING.add(entity.getUUID());
    }

    public static void endSwitch(@Nullable Entity entity) {
        if (entity != null) SWITCHING.remove(entity.getUUID());
    }

    /**
     * 把乘客从 {@code from} 迁移到 {@code to}。
     *
     * <p>顺序：① 交接上车点缓存 ② 跨子零件时先挂载（唯一会失败的一步）③ 清旧座位 ④ 接管新座位。
     * 挂载失败时直接返回 {@link SeatSwitchReason#MOUNT_FAILED}，两个座位的
     * {@code passenger}/{@code occupied}/{@code controllingSubsystem}/信号/额外质量全是原值。</p>
     *
     * @return {@link SeatSwitchReason#OK} 或 {@link SeatSwitchReason#MOUNT_FAILED}
     */
    public static SeatSwitchReason switchSeat(LivingEntity passenger, SeatSubsystem from, SeatSubsystem to) {
        SubPart fromSubPart = from.getSubPart();
        SubPart toSubPart = to.getSubPart();
        if (fromSubPart == null || toSubPart == null) return SeatSwitchReason.MOUNT_FAILED;

        // ① 交接上车点：此时乘客仍在旧实体上、控制关系仍指向旧座位，能取到旧座位的缓存
        to.updateBoardingOffset(passenger);

        boolean sameSubPart = fromSubPart == toSubPart;
        if (!sameSubPart) {
            // ② 先挂载：startRiding 内部会 stopRiding，换座标记让下车点搜索直接返回当前位置
            beginSwitch(passenger);
            boolean mounted;
            try {
                mounted = passenger.startRiding(toSubPart.getEntity(), true);
            } finally {
                endSwitch(passenger);
            }
            if (!mounted) return SeatSwitchReason.MOUNT_FAILED;
        }

        // ③ 清旧座位：不 stopRiding、不加冲量
        from.detachForSwitch(passenger);
        // ④ 接管新座位：同实体路径本来就不需要挂载，跨实体路径已经挂好
        to.attachForSwitch(passenger);
        return SeatSwitchReason.OK;
    }
}
