package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import io.github.sweetzonzi.machine_max.common.menu.VehicleNamingMenu;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;


public class EmptyBlueprintItem extends Item implements ICustomModelItem, MenuProvider {
    public static final ModelIndex MODEL = new ModelIndex(
            "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint"));
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/blueprint.png");

    public EmptyBlueprintItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        if (!level.isClientSide()) {
            LivingEntityEyesightAttachment eyesight = player.getData(MMAttachments.getENTITY_EYESIGHT());
            SubPart subPart = eyesight.getSubPart();
            if (subPart != null && subPart.part.assembly != null) {
                // 打开命名GUI
                ItemStack stack = player.getItemInHand(usedHand);
                player.openMenu(this, buf -> {
                    buf.writeInt(player.getInventory().selected);
                    buf.writeUtf(subPart.part.assembly.getAssemblyName());
                });
                return InteractionResultHolder.consume(stack);
            } else {
                player.sendSystemMessage(MMLanguageRegistry.MESSAGE_BLUEPRINT_PASS.translated());
            }
        }
        return super.use(level, player, usedHand);
    }

    public IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        var animatable = new ItemAnimatable(itemStack, level);
        animatable.getModelController().setModel(MODEL);
        animatable.getModelController().setTextureLocation(TEXTURE);
        cacheAnimatable(itemStack, context, animatable);
        return animatable;
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.FIXED
                || displayContext == ItemDisplayContext.GROUND) {
            return new Vector3f(25, 30, 0).mul((float) (Math.PI / 180));
        }
        return ICustomModelItem.super.getRenderRotation(itemStack, level, displayContext);
    }

    @Override
    public @NotNull Component getDisplayName() {
        return Component.empty();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new VehicleNamingMenu(containerId, playerInventory, playerInventory.getSelected());
    }
}
