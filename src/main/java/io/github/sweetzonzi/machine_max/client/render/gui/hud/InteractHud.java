package io.github.sweetzonzi.machine_max.client.render.gui.hud;

import io.github.sweetzonzi.machine_max.client.assembly.VehicleAssemblyClientHelper;
import io.github.sweetzonzi.machine_max.client.input.KeyBinding;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.item.prop.BaseVehicleItem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InteractHud implements LayeredDraw.Layer {
    Minecraft minecraft = Minecraft.getInstance();
    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker deltaTracker) {
        if (minecraft.options.hideGui) return;
        LocalPlayer localPlayer = minecraft.player;
        if (localPlayer == null) return;

        List<Component> hints = new ArrayList<>();
        LivingEntityEyesightAttachment eyesight = localPlayer.getData(MMAttachments.getENTITY_EYESIGHT().get());
        InteractBox interactBox = eyesight.getAccurateInteractBox();
        if (interactBox == null) interactBox = eyesight.getFastInteractBox();
        if (interactBox != null) {
            hints.add(Component.translatable(MMLanguageRegistry.MESSAGE_WATCH_INTERACT_BOX_INFO.tag,
                            KeyBinding.generalInteractKey.getTranslatedKeyMessage())
                    .append(Component.translatable(interactBox.name)));
        }

        VehicleAssemblyHelper assembly = VehicleAssemblyHelper.getInstance();
        if (minecraft.screen == null && SeatSubsystem.canUseItems(localPlayer)
                && hasVehiclePlacementItem(localPlayer)) {
            addBoundHint(hints, minecraft.options.keyUse, MMLanguageRegistry.HUD_KEY_VEHICLE_PLACE);
            addBoundHint(hints, KeyBinding.assemblyAddAttachAngleKey,
                    MMLanguageRegistry.HUD_KEY_VEHICLE_ROTATE_FORWARD);
            addBoundHint(hints, KeyBinding.assemblySubAttachAngleKey,
                    MMLanguageRegistry.HUD_KEY_VEHICLE_ROTATE_BACKWARD);
        }

        if (VehicleAssemblyClientHelper.getInstance().canUseAssemblyInput(localPlayer)) {
            addBoundHint(hints, minecraft.options.keyUse, MMLanguageRegistry.HUD_KEY_ASSEMBLY_PLACE);
            if (assembly.getPartType().getVariants().size() > 1) {
                addBoundHint(hints, KeyBinding.assemblyCycleVariantKey,
                        MMLanguageRegistry.HUD_KEY_ASSEMBLY_CYCLE_VARIANT);
            }
            addBoundHint(hints, KeyBinding.assemblyAddAttachAngleKey,
                    MMLanguageRegistry.HUD_KEY_ASSEMBLY_ADD_ANGLE);
            addBoundHint(hints, KeyBinding.assemblySubAttachAngleKey,
                    MMLanguageRegistry.HUD_KEY_ASSEMBLY_SUB_ANGLE);

            VariantAttr variant = assembly.getVariant();
            if (variant != null && variant.getPartOutwardConnectors().size() > 1) {
                addBoundHint(hints, KeyBinding.assemblyCycleConnectorKey,
                        MMLanguageRegistry.HUD_KEY_ASSEMBLY_CYCLE_CONNECTOR);
            }
        }

        int x = guiGraphics.guiWidth() / 2 + 3;
        int y = guiGraphics.guiHeight() / 2 + 2;
        for (Component hint : hints) {
            guiGraphics.drawString(minecraft.font, hint, x, y, 0xFFFFFFFF);
            y += minecraft.font.lineHeight + 1;
        }
    }

    /** 检查任一只手是否持有可放置载具的物品。 */
    private static boolean hasVehiclePlacementItem(LocalPlayer player) {
        return player.getMainHandItem().getItem() instanceof BaseVehicleItem
                || player.getOffhandItem().getItem() instanceof BaseVehicleItem;
    }

    private static void addBoundHint(List<Component> hints, KeyMapping key, MMLanguageRegistry text) {
        if (!key.isUnbound()) {
            hints.add(Component.translatable(text.tag, key.getTranslatedKeyMessage()));
        }
    }
}
