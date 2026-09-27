package io.github.sweetzonzi.machine_max.client.input;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaData;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaHelper;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaSelectShortcutPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * 设计模式下的原版输入拦截：把滚轮与数字键 1-9 从"切换玩家热栏格"改接到"切换 PDA 当前格位"。
 *
 * <p>前置条件为：本地玩家手持 PDA、该 PDA 的 {@code designMode} 为 true，且当前没有打开任何界面。
 * 条件不满足时立即返回，不触碰任何原版输入状态，因此常态下滚轮与数字键的行为完整保留。</p>
 *
 * <p>拦截生效的原理：原版 {@code Minecraft.handleKeybinds()} 以 {@code consumeClick()} 读取热栏切换意图，
 * 而 {@link InputEvent.Key} 在同一帧内先于它执行，先消费掉 click 即可让原版取不到。滚轮则直接取消事件。</p>
 */
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class PdaInputInterceptor {
    private PdaInputInterceptor() {
    }

    /**
     * 滚轮上下切换格位（越界环绕）。
     *
     * <p>方向与原版热栏一致：向上滚（{@code getScrollDeltaY() > 0}）取前一格，向下滚取后一格。</p>
     *
     * <p>按住 Alt 时不切格位：安装角旋转沿用 {@code RawInputHandler} 的既有行为。两种情形都取消滚轮事件，
     * 否则原版热栏切换会与格位切换同时发生。</p>
     */
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (!isDesignModeHeld()) return;
        if (!isAltDown()) {
            double delta = event.getScrollDeltaY();
            if (delta != 0) {
                LocalPlayer player = Minecraft.getInstance().player;
                InteractionHand hand = PdaHelper.heldPdaHand(player);
                if (hand != null) {
                    PdaData data = PdaHelper.getData(player.getItemInHand(hand));
                    select(player, hand, data.selected() + (delta > 0 ? -1 : 1));
                }
            }
        }
        event.setCanceled(true);
    }

    /**
     * 数字键 1-9 直接选中对应格位。
     *
     * <p>逐项 {@code consumeClick()} 而不比较 GLFW 键码，以尊重玩家的按键重绑定。</p>
     */
    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (!isDesignModeHeld()) return;
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        InteractionHand hand = PdaHelper.heldPdaHand(player);
        if (hand == null) return;
        for (int i = 0; i < PdaData.SHORTCUT_COUNT; i++) {
            if (minecraft.options.keyHotbarSlots[i].consumeClick()) {
                minecraft.options.keyHotbarSlots[i].setDown(false);
                select(player, hand, i);
            }
        }
    }

    /** 前置条件是否成立：无界面打开、手持 PDA、且该 PDA 处于设计模式。 */
    private static boolean isDesignModeHeld() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen != null || minecraft.player == null) return false;
        InteractionHand hand = PdaHelper.heldPdaHand(minecraft.player);
        return hand != null && PdaHelper.getData(minecraft.player.getItemInHand(hand)).designMode();
    }

    /** 预写本地格位（与服务端共用同一段归一逻辑）并发送载荷。 */
    private static void select(LocalPlayer player, InteractionHand hand, int index) {
        ItemStack stack = player.getItemInHand(hand);
        PdaData next = PdaHelper.getData(stack).withSelected(index);
        PdaHelper.setData(stack, next);
        PacketDistributor.sendToServer(new PdaSelectShortcutPayload(hand, next.selected()));
    }

    /** 左右 Alt 任一按下即视为按住 Alt。 */
    private static boolean isAltDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }
}
