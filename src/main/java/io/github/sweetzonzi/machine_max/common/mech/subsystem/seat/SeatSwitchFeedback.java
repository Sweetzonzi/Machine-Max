package io.github.sweetzonzi.machine_max.common.mech.subsystem.seat;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 客户端换座的界面状态：在途请求、脉冲与失败原因。
 *
 * <p>按键路径写、HUD 读，同一线程（都是主线程）。放在 common 是因为它只涉及
 * {@link Component} 与座位引用，不引用任何客户端类；服务端从不读它。</p>
 *
 * <p>客户端不做预测：这里记下的只是“已经发出请求、等结果包”，座位与骑乘状态在这个窗口里
 * 一个都不改。结果包到达后 {@link #onResult} 清掉在途标记，成功则触发脉冲，失败则保留原因
 * 供 actionbar 与标记闪烁使用。</p>
 */
public final class SeatSwitchFeedback {

    /** 发出请求后忽略重复按键的窗口 */
    private static final int PENDING_TICKS = 20;
    /** 失败提示的显示时长 */
    private static final int FAILURE_TICKS = 40;
    /** 成功后的脉冲显示时长 */
    private static final int PULSE_TICKS = 12;

    private static int pendingTicks;
    @Nullable
    private static SeatSubsystem pendingTarget;
    private static int pulseTicks;
    private static int failureTicks;
    private static SeatSwitchReason failureReason = SeatSwitchReason.OK;
    private static int failureSubPartId = -1;
    private static String failureSeatName = "";

    private SeatSwitchFeedback() {
    }

    /** 请求已发出，进入去重窗口 */
    public static void beginPending(SeatSubsystem target) {
        pendingTarget = target;
        pendingTicks = PENDING_TICKS;
        failureTicks = 0;
    }

    /** 是否处于“已发请求、等结果”的窗口内 */
    public static boolean isPending() {
        return pendingTicks > 0;
    }

    @Nullable
    public static SeatSubsystem pendingTarget() {
        return pendingTicks > 0 ? pendingTarget : null;
    }

    /** 成功后的脉冲剩余 tick，供标记放大动画使用 */
    public static int pulseTicks() {
        return pulseTicks;
    }

    public static int failureTicks() {
        return failureTicks;
    }

    public static SeatSwitchReason failureReason() {
        return failureReason;
    }

    /** 失败原因是否指向某个具体座位（用于让那张标记闪红） */
    public static boolean failureAt(int subPartId, String seatName) {
        return failureTicks > 0 && failureSubPartId == subPartId && failureSeatName.equals(seatName);
    }

    /** 每个客户端 tick 调一次：递减计时，并在目标成为当前座位后清掉在途标记 */
    public static void tick(@Nullable SeatSubsystem currentSeat) {
        if (pendingTicks > 0) {
            pendingTicks--;
            if (pendingTicks <= 0) pendingTarget = null;
            // 在途标记在目标座位变成当前座位时立即失效：结果包可能比下一个 tick 更早到
            else if (pendingTarget != null && pendingTarget == currentSeat) {
                pendingTicks = 0;
                pendingTarget = null;
            }
        }
        if (pulseTicks > 0) pulseTicks--;
        if (failureTicks > 0) failureTicks--;
    }

    /** 结果包到达 */
    public static void onResult(boolean success, SeatSwitchReason reason, @Nullable SeatSubsystem seat) {
        pendingTicks = 0;
        pendingTarget = null;
        if (success) {
            pulseTicks = PULSE_TICKS;
            failureTicks = 0;
            failureReason = SeatSwitchReason.OK;
            return;
        }
        failureTicks = FAILURE_TICKS;
        failureReason = reason;
        if (seat != null) {
            failureSubPartId = seat.getSubPart() == null ? -1 : seat.getSubPart().getId();
            failureSeatName = seat.getName();
        } else {
            failureSubPartId = -1;
            failureSeatName = "";
        }
    }

    /** 换车、下车时清空，避免旧的脉冲/失败提示漂到下一台载具上 */
    public static void clear() {
        pendingTicks = 0;
        pendingTarget = null;
        pulseTicks = 0;
        failureTicks = 0;
        failureReason = SeatSwitchReason.OK;
        failureSubPartId = -1;
        failureSeatName = "";
    }

    /** 失败原因对应的本地化文案 */
    public static Component failureMessage() {
        return Component.translatable(failureKey(failureReason));
    }

    /** 成功文案：{@code 2/3 · Gunner Seat}，序号来自本地几何顺序，名字由子系统名的翻译键给出 */
    public static Component successMessage(SeatSubsystem seat) {
        int index = 1;
        int total = 1;
        if (seat.getSubPart() != null && seat.getSubPart().getPart().assembly instanceof VehicleCore vehicle) {
            LayoutIndex layout = indexOf(vehicle, seat);
            index = layout.index();
            total = layout.total();
        }
        return Component.translatable("message.machine_max.seat.switched",
                index, total, Component.translatable(seat.getName()));
    }

    /** 满员提示 */
    public static Component fullMessage() {
        return Component.translatable("message.machine_max.seat.full");
    }

    /** 循环键在本地算不出目标时的提示 */
    public static Component noSeatMessage() {
        return Component.translatable("message.machine_max.seat.not_seated");
    }

    private record LayoutIndex(int index, int total) {
    }

    private static LayoutIndex indexOf(VehicleCore vehicle, SeatSubsystem seat) {
        List<SeatSubsystem> order = SeatOrder.order(vehicle, seat);
        int index = order.indexOf(seat);
        return new LayoutIndex(index < 0 ? 1 : index + 1, Math.max(1, order.size()));
    }

    private static String failureKey(SeatSwitchReason reason) {
        return switch (reason) {
            case OK -> "message.machine_max.seat.switched";
            case NOT_SEATED -> "message.machine_max.seat.not_seated";
            case SAME_SEAT -> "message.machine_max.seat.same_seat";
            case OCCUPIED -> "message.machine_max.seat.occupied";
            case UNAVAILABLE -> "message.machine_max.seat.unavailable";
            case DIFFERENT_ASSEMBLY -> "message.machine_max.seat.different_assembly";
            case NO_ENTITY -> "message.machine_max.seat.no_entity";
            case MOUNT_FAILED -> "message.machine_max.seat.mount_failed";
            case RATE_LIMITED -> "message.machine_max.seat.rate_limited";
        };
    }
}
