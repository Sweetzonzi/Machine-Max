package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class InteractHud implements LayeredDraw.Layer {
    Minecraft minecraft = Minecraft.getInstance();
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (Minecraft.getInstance().options.hideGui) return;
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) return;
        LivingEntityEyesightAttachment eyesight = localPlayer.getData(MMAttachments.getENTITY_EYESIGHT().get());
        InteractBox interactBox = eyesight.getAccurateInteractBox();
        if (interactBox == null) interactBox = eyesight.getFastInteractBox();
        if (interactBox != null) {
            guiGraphics.drawString(
                    minecraft.font,
                    Component.translatable("message.machine_max.watch_interact_box_info", KeyBinding.generalInteractKey.getTranslatedKeyMessage())
                            .append(Component.translatable(interactBox.name)),
                    guiGraphics.guiWidth() / 2 + 3,
                    guiGraphics.guiHeight() / 2 + 2,
                    Color.WHITE.getRGB());
        }
    }
}
