package io.github.sweetzonzi.machine_max.client.input;

import io.github.sweetzonzi.machine_max.client.render.gui.seat.SeatMapLayout;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatOrder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.seat.SeatSwitchFeedback;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.mixin_interface.IEntityMixin;
import io.github.sweetzonzi.machine_max.network.payload.SeatSwitchPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 循环换座键的客户端逻辑。
 *
 * <p>只在座位控制下响应，且不打开界面、不隐藏 GUI、不在炮镜/摄像机模式。目标座位由本地按几何顺序
 * 算出（{@link SeatOrder#next}），发出去的是具体座位，不是方向或序号——服务端不排序，只校验。
 * 算不出目标时本地提示“没有其它可用座位”，不发包。</p>
 *
 * <p>客户端不做预测：发完包只记一个在途标记（用于去重与 HUD 高亮），座位、骑乘关系与控制目标
 * 都要等结果包到达才变。</p>
 */
public final class SeatSwitchClient {

    private SeatSwitchClient() {
    }

    /**
     * 按一次循环换座键。
     *
     * @param forward true 顺向、false 逆向
     */
    public static void requestCycle(boolean forward) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) return;
        if (minecraft.screen != null || minecraft.options.hideGui) return;
        if (CameraController.isCameraMode()) return;
        if (!(((IEntityMixin) player).machine_Max$getControllingSubsystem() instanceof SeatSubsystem seat)) return;
        // 在途窗口内忽略重复按键：服务端另有 10 tick 节流，这一层只是省掉无谓的往返
        if (SeatSwitchFeedback.isPending()) return;
        if (seat.getSubPart() == null || !(seat.getSubPart().getPart().assembly instanceof VehicleCore vehicle))
            return;

        SeatSubsystem target = SeatOrder.next(vehicle, seat, forward);
        if (target == null) {
            player.displayClientMessage(SeatSwitchFeedback.fullMessage(), true);
            return;
        }
        if (target.getSubPart() == null) return;

        SeatSwitchFeedback.beginPending(target);
        // 立即让座位图按最新拓扑重算一次，避免刚装/拆过零件时高亮落在旧位置
        SeatMapLayout.markDirty();
        PacketDistributor.sendToServer(new SeatSwitchPayload(target.getSubPart().getId(), target.getName()));
    }
}
