package io.github.sweetzonzi.machine_max.client.input;

import io.github.sweetzonzi.machine_max.client.render.gui.seat.SeatMapLayout;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchFeedback;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchReason;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.SeatSwitchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 换座键的客户端逻辑：只按换座键切到下一个可用座位；按住换座键再按 {@code 1}–{@code 9}
 * 直接切到编号为该数字的座位。
 *
 * <p>只在座位控制下响应，且不打开界面、不隐藏 GUI、不在炮镜/摄像机模式。目标座位由本地按几何顺序
 * 算出（{@link SeatMapLayout#snapshot}），发出去的是具体座位，不是方向或序号——服务端不排序，只校验。
 * 算不出目标时本地提示，不发包。</p>
 *
 * <p>编号就是座位图标记上的数字（几何顺序，从 1 起）。数字键默认同时是热栏键：按住换座键期间按下的
 * 数字归换座，{@link #chordNumber} 吞掉对应的热栏点击（{@code ClientTickEvent.Pre} 早于原版
 * {@code Minecraft#handleKeybinds}），槽位因此不切。</p>
 *
 * <p>客户端不做预测：发完包只记一个在途标记（用于去重与 HUD 高亮），座位、骑乘关系与控制目标
 * 都要等结果包到达才变。</p>
 */
public final class SeatSwitchClient {

    /** 换座键按住期间：数字键按座位编号解释，热栏点击归换座 */
    private static boolean chordArmed;
    /** 本次按住期间已经用数字选过座位，松开时不再切下一个 */
    private static boolean chordUsed;

    private SeatSwitchClient() {
    }

    /**
     * 每个客户端 tick 在本类的数字键处理之前调用一次，跟踪换座键的按下与松开。
     *
     * <p>切下一个座位放在松开时：按下时先切一次，就没法再把“按住 + 数字”当成直选。
     * 状态读 {@code isDown()} 而不是按键事件，窗口失焦时原版会 releaseAll，状态自己回到“未按下”。</p>
     */
    public static void tickChord() {
        if (KeyBinding.generalCycleSeatKey.isDown()) {
            if (!chordArmed) {
                chordArmed = true;
                chordUsed = false;
            }
            return;
        }
        if (!chordArmed) return;
        boolean cycle = !chordUsed;
        chordArmed = false;
        chordUsed = false;
        if (cycle) requestCycle(true);
    }

    /**
     * 换座键是否被按住，也就是数字键当前是否按座位编号解释。
     *
     * <p>座位图 HUD 的行内提示据此提示数字直选已经生效（{@code vehicle.seat_chord_armed}）。</p>
     */
    public static boolean isChordArmed() {
        return chordArmed;
    }

    /**
     * 换座键按住期间按下数字键。
     *
     * @param number 座位编号，与座位图标记上的数字一致，取值 {@code 1}–{@code 9}
     * @return true 表示这次按键已经归换座处理，调用方应当吞掉对应的热栏点击
     */
    public static boolean chordNumber(int number) {
        if (!chordArmed || number < 1 || number > 9) return false;
        if (!requestNumber(number)) return false;
        chordUsed = true;
        // 数字键默认也是热栏键：这次按下属于换座，槽位不切
        Minecraft.getInstance().options.keyHotbarSlots[number - 1].consumeClick();
        return true;
    }

    /**
     * 按一次换座键：切到几何顺序里的下一个可用座位。
     *
     * @param forward true 顺向、false 逆向
     */
    public static void requestCycle(boolean forward) {
        SeatSubsystem seat = activeSeat();
        if (seat == null) return;
        VehicleCore vehicle = vehicleOf(seat);
        SeatSwitchFeedback.observe(vehicle);
        if (SeatSwitchFeedback.isPending()) return;
        SeatSubsystem target = SeatOrder.next(SeatMapLayout.snapshot(vehicle).seats(), seat, forward);
        if (target == null) {
            actionBar(SeatSwitchFeedback.fullMessage());
            return;
        }
        dispatch(target);
    }

    /**
     * 切到编号为 {@code number} 的座位。
     *
     * <p>本地先按编号取座位并复核可用性，理由与循环键一致：占用状态是双端同步值，本地就能给出
     * 即时反馈。不可用、越界、就是当前座位都不发包；服务端仍会独立复核同一个目标。</p>
     *
     * @param number 座位编号，从 1 起
     * @return true 表示按键发生在换座上下文里（无论最终是否发包），调用方据此吞掉热栏点击
     */
    public static boolean requestNumber(int number) {
        SeatSubsystem seat = activeSeat();
        if (seat == null) return false;
        VehicleCore vehicle = vehicleOf(seat);
        SeatSwitchFeedback.observe(vehicle);
        if (SeatSwitchFeedback.isPending()) return true;
        List<SeatSubsystem> seats = SeatMapLayout.snapshot(vehicle).seats();
        if (number < 1 || number > seats.size()) {
            actionBar(SeatSwitchFeedback.outOfRangeMessage(number, seats.size()));
            return true;
        }
        SeatSubsystem target = seats.get(number - 1);
        if (target == seat) {
            actionBar(SeatSwitchFeedback.failureMessage(SeatSwitchReason.SAME_SEAT));
            return true;
        }
        if (!SeatOrder.available(target)) {
            actionBar(SeatSwitchFeedback.failureMessage(
                    target.isOccupied() ? SeatSwitchReason.OCCUPIED : SeatSwitchReason.UNAVAILABLE));
            return true;
        }
        dispatch(target);
        return true;
    }

    public static void updateFeedback(Player local, @Nullable SeatSubsystem target, boolean success) {
        if (!(((IEntityMixin) local).machine_Max$getControllingSubsystem() instanceof SeatSubsystem current)
                || !(current.getSubPart().getPart().assembly instanceof VehicleCore vehicle)) return;
        if (target != null && target.getSubPart().getPart().assembly != vehicle) return;
        SeatSwitchFeedback.observe(vehicle);
        if (target == null && !SeatSwitchFeedback.isPending()) return;
        SeatSwitchFeedback.onResult(success, target);
    }

    public static Component successMessage(SeatSubsystem seat) {
        int number = 1;
        int total = 1;
        if (seat.getSubPart().getPart().assembly instanceof VehicleCore vehicle) {
            var layout = SeatMapLayout.snapshot(vehicle);
            number = Math.max(1, layout.numberOf(seat));
            total = Math.max(1, layout.seats().size());
        }
        return Component.translatable("message.machine_max.seat.switched",
                number, total, Component.translatable(seat.getName()));
    }

    /**
     * 换座动作可以发生的上下文：玩家在座位控制下、没开界面、没隐藏 GUI、不在摄像机模式，
     * 且座位挂在一个装配体上。
     *
     * @return 当前座位；任一条件不满足时返回 null
     */
    @Nullable
    private static SeatSubsystem activeSeat() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui) return null;
        if (CameraController.isCameraMode()) return null;
        if (!(((IEntityMixin) minecraft.player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat))
            return null;
        if (seat.getSubPart() == null || !(seat.getSubPart().getPart().assembly instanceof VehicleCore)) return null;
        return seat;
    }

    private static VehicleCore vehicleOf(SeatSubsystem seat) {
        return (VehicleCore) seat.getSubPart().getPart().assembly;
    }

    /** 发出换座请求：只记在途标记，不改任何座位状态 */
    private static void dispatch(SeatSubsystem target) {
        if (target.getSubPart() == null) return;
        SeatSwitchFeedback.beginPending(target);
        PacketDistributor.sendToServer(new SeatSwitchPayload(target.getSubPart().getId(), target.getName()));
    }

    private static void actionBar(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(message, true);
    }
}
