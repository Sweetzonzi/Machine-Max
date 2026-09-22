package io.github.sweetzonzi.machine_max.client.input;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.network.payload.grab.GrabReleasePayload;
import io.github.sweetzonzi.machine_max.network.payload.grab.GrabStartPayload;
import io.github.sweetzonzi.machine_max.network.payload.grab.GrabThrowPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 抓取的客户端输入：观察右键的按下与松开、执行形态门禁、拦截原版按键并发包。
 * <p>
 * 抓取输入与右键共用：只在"主手为空（徒手形态）且准星命中零件"时接管右键并吞掉原版交互，
 * 其余情况放行给原版（详细设计文档 §8.5）。门禁在客户端执行，但结论不作为已校验的事实发出，
 * 服务端会复核同一条件。
 * <p>
 * 按下由原版交互键事件吞掉，松开走按键状态轮询——该事件只在按键按下时触发（详细设计文档 §7.4）。
 * 首期不做跨端表现，牵引中状态由客户端按按键状态自行推断。
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class GrabInputHandler {

    /** 客户端本地的牵引中状态。 */
    private static boolean grabbing = false;
    /** 上一客户端 tick 的右键按下状态，用于把按键状态轮询翻译成"按下 / 松开"两类事件。 */
    private static boolean useKeyDown = false;

    /**
     * 观察右键的按下与松开（按键状态轮询）。
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null) {
            grabbing = false;
            useKeyDown = false;
            return;
        }
        // 与按键状态保持一致：主手不再为空即本地结束牵引，服务端会自行复核并释放
        if (grabbing && !player.getMainHandItem().isEmpty()) grabbing = false;
        boolean down = client.options.keyUse.isDown();
        if (down && !useKeyDown) {
            if (!grabbing && grabGatePasses(player)) {
                grabbing = true;
                PacketDistributor.sendToServer(new GrabStartPayload());
            }
        } else if (!down && useKeyDown) {
            // 放下是显式事件，延迟为一个收发往返
            if (grabbing) {
                grabbing = false;
                PacketDistributor.sendToServer(new GrabReleasePayload());
            }
        }
        useKeyDown = down;
    }

    /**
     * 形态门禁通过时吞掉右键的原版交互触发；牵引中吞掉原版攻击并发出丢出动作。
     * <p>
     * 零件实体没有原版交互功能，本方法只处理抓取用的左右键，其余按键一律放行。
     */
    @SubscribeEvent
    public static void onInteractionKeyMappingTriggered(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        if (event.isAttack()) {
            // 牵引中左键为丢出；客户端本地的挥手动画不受影响，可作为抛掷的起手表现
            if (grabbing) {
                event.setCanceled(true);
                grabbing = false;
                PacketDistributor.sendToServer(new GrabThrowPayload());
            }
        } else if (event.isUseItem()) {
            // 按下时本地状态可能尚未更新，因此与门禁结果取并集
            if (grabbing || grabGatePasses(player)) {
                event.setSwingHand(false);
                event.setCanceled(true);
            }
        }
    }

    /**
     * 形态门禁：徒手形态（主手为空）且准星命中零件。
     *
     * @param player 本地玩家
     * @return 门禁是否通过
     */
    private static boolean grabGatePasses(LocalPlayer player) {
        if (!player.getMainHandItem().isEmpty()) return false;
        if (!player.hasData(MMAttachments.getENTITY_EYESIGHT())) return false;
        return player.getData(MMAttachments.getENTITY_EYESIGHT()).getSubPart() != null;
    }
}
