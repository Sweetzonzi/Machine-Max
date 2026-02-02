package io.github.sweetzonzi.machine_max.client.render.toast;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

public class BlueprintResearchToast implements Toast {
    private static final ResourceLocation BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("toast/advancement");

    private final ItemStack icon;
    private final Component title;
    private final Component subtitle;
    private long firstDrawTime = -1L;

    public BlueprintResearchToast(
            ItemStack icon,
            Component title,
            Component subtitle
    ) {
        this.icon = icon;
        this.title = title;
        this.subtitle = subtitle;
    }

    @Override
    public @NotNull Visibility render(
            @NotNull GuiGraphics guiGraphics,
            @NotNull ToastComponent toastComponent,
            long timeSinceLastVisible
    ) {
        if (firstDrawTime < 0) {
            firstDrawTime = timeSinceLastVisible;
        }

        // 背景：与成就一致
        guiGraphics.blitSprite(BACKGROUND_SPRITE, 0, 0, this.width(), this.height());

        // 图标
        guiGraphics.renderItem(icon, 8, 8);

        // 文本
        guiGraphics.drawString(
                toastComponent.getMinecraft().font,
                title,
                30,
                7,
                0xFFFF66,
                false
        );
        guiGraphics.drawString(
                toastComponent.getMinecraft().font,
                subtitle,
                30,
                18,
                0xAAAAAA,
                false
        );

        return timeSinceLastVisible - firstDrawTime >= 5000
                ? Visibility.HIDE
                : Visibility.SHOW;
    }

    @Override
    public int width() {
        return Minecraft.getInstance().font.width(subtitle) + 40;
    }
}

