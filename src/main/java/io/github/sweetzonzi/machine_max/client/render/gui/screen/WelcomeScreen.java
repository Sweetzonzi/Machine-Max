package io.github.sweetzonzi.machine_max.client.render.gui.screen;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * 首次启动欢迎画面
 * <p>
 * 代码来源：参考 SuperbWarfare 的 SnapshotWarningScreen
 * 原代码由 SuperbWarfare 团队编写，移植至 MachineMax 并修改了显示内容
 */
@OnlyIn(Dist.CLIENT)
public class WelcomeScreen extends Screen {

    /** 按钮冻结时间（单位：tick，20 tick = 1 秒） */
    private static final int FROZEN_TICKS = 60;

    /** 内容文本的垂直起始绘制位置 */
    private static final int CONTENT_TOP = 70;

    /** 标题的垂直绘制位置 */
    private static final int TITLE_TOP = 30;

    /** 行高 */
    private static final int LINE_HEIGHT = 18;

    /** 内容与按钮之间的间距 */
    private static final int CONTENT_BUTTON_GAP = 18;

    /** 按钮距屏幕底部的固定间距 */
    private static final int BUTTON_BOTTOM_MARGIN = 40;

    /** 按钮宽度 */
    private static final int BUTTON_WIDTH = 150;

    /** 按钮高度 */
    private static final int BUTTON_HEIGHT = 20;

    private MultiLineLabel message = MultiLineLabel.EMPTY;
    private int freezeTicks = FROZEN_TICKS;
    private AbstractButton proceedButton;

    protected WelcomeScreen() {
        super(Component.translatable("gui.machine_max.welcome.title").withStyle(ChatFormatting.BOLD));
    }

    @Override
    protected void init() {
        super.init();

        message = MultiLineLabel.create(
                font,
                Component.translatable("gui.machine_max.welcome.content"),
                this.width - 100
        );

        int buttonY = Math.min(
                CONTENT_TOP + message.getLineCount() * LINE_HEIGHT + CONTENT_BUTTON_GAP,
                this.height - BUTTON_BOTTOM_MARGIN
        );

        proceedButton = createProceedButton(buttonY);
        proceedButton.active = false;
        addRenderableWidget(proceedButton);
    }

    @Override
    public void tick() {
        super.tick();

        if (proceedButton != null) {
            if (freezeTicks > 0) {
                freezeTicks--;
                proceedButton.setMessage(
                        Component.translatable("gui.machine_max.welcome.proceed")
                                .append(Component.literal(" (" + (freezeTicks + 19) / 20 + "s)"))
                );
            } else if (!proceedButton.active) {
                proceedButton.active = true;
                proceedButton.setMessage(Component.translatable("gui.machine_max.welcome.proceed"));
            }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 绘制深色半透明背景
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        // 绘制标题（白色加粗）
        graphics.drawString(
                font, this.title,
                (this.width - font.width(this.title)) / 2, TITLE_TOP,
                0xFFFFFF, false
        );

        // 绘制内容文本（浅灰色）
        int contentX = this.width / 2 - message.getWidth() / 2;
        message.renderLeftAligned(graphics, contentX, CONTENT_TOP, LINE_HEIGHT, 0xCCCCCC);

    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new TitleScreen());
    }

    private AbstractButton createProceedButton(int buttonY) {
        return Button.builder(
                Component.translatable("gui.machine_max.welcome.proceed")
                        .append(Component.literal(" (" + (FROZEN_TICKS + 19) / 20 + "s)")),
                btn -> Minecraft.getInstance().setScreen(new TitleScreen())
        ).bounds(this.width / 2 - BUTTON_WIDTH / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build();
    }

    /**
     * 在标题画面出现时检测并弹出欢迎画面
     */
    @EventBusSubscriber(value = Dist.CLIENT, modid = MachineMax.MOD_ID)
    public static class WelcomeHandler {

        private static boolean alreadyShown = false;

        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onTitleScreenInit(ScreenEvent.Init.Post event) {
            if (alreadyShown) return;
            if (!(event.getScreen() instanceof TitleScreen)) return;

            alreadyShown = true;
            Minecraft.getInstance().setScreen(new WelcomeScreen());
        }
    }
}
