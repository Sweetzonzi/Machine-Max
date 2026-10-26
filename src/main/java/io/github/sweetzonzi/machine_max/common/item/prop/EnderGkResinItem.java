package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.ICustomModelItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

import java.awt.*;

public class EnderGkResinItem extends Item implements ICustomModelItem, VehicleCaptureItem {
    private static final ModelIndex MODEL = new ModelIndex(
            "item", ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "blueprint"));
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "textures/item/blueprint.png");
    private static final Color COLOR = Color.RED;
    public EnderGkResinItem() {
        super(new Properties());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        return VehicleCaptureItem.captureVehicleToAssembly(level, player, player.getItemInHand(usedHand), true);
    }

    @Override
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
    public Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return COLOR;
    }
}
