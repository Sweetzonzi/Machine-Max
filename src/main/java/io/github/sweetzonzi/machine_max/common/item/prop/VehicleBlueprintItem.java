package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import cn.solarmoon.spark_core.animation.model.ModelIndex;
import cn.solarmoon.spark_core.animation.model.origin.OModel;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.VehicleAnimatable;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.awt.*;
import java.util.*;
import java.util.List;

import static io.github.sweetzonzi.machine_max.common.item.prop.EmptyBlueprintItem.MODEL;
import static io.github.sweetzonzi.machine_max.common.item.prop.EmptyBlueprintItem.TEXTURE;

public class VehicleBlueprintItem extends BaseVehicleItem {
    public static final ModelIndex ICON_MODEL = new ModelIndex("item",
            ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "item_icon_2d_128x"));
    public static final ResourceLocation BG_TEXTURE = ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID,
            "textures/item/blueprint_bg.png");

    public VehicleBlueprintItem() {
        super(new Properties());
    }

    @Nullable
    @Override
    protected VehicleData getVehicleData(ItemStack stack) {
        return getVehicleDataStatic(stack);
    }

    @Override
    protected VehicleCore createVehicle(Level level, VehicleData vehicleData) {
        return new VehicleCore(level, vehicleData, false);
    }

    @Override
    protected String getNameTranslationKey(ItemStack stack) {
        String itemName;
        ResourceLocation location = stack.get(MMDataComponents.getVEHICLE_BLUEPRINT_PATH());
        if (location != null) {
            itemName = location.toLanguageKey().replace("/", ".");
        } else {
            VehicleData vehicleData = getVehicleData(stack);
            if (vehicleData != null) {
                itemName = vehicleData.getName();
            } else {
                itemName = "machine_max:unreadable_blueprint";
            }
        }
        return itemName;
    }

    @Nullable
    @Override
    protected String getTooltipContent(ItemStack stack) {
        try {
            if (MMDynamicRes.TOOLTIPS.get(getBlueprintData(stack).getTooltip()) instanceof String content) {
                return content;
            }
        } catch (NullPointerException e) {
            return null;
        }
        return null;
    }

    @Override
    public boolean use2dModel(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI) {
            BlueprintData blueprintData = getBlueprintData(itemStack);
            return !blueprintData.getIcon().equals(BlueprintData.EMPTY);
        }
        return false;
    }

    @Override
    public IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        BlueprintData blueprintData = getBlueprintData(itemStack);

        if (context == ItemDisplayContext.GUI) {
            if (!blueprintData.getIcon().equals(BlueprintData.EMPTY)) {
                var animatable = new ItemAnimatable(itemStack, level);
                animatable.getModelController().setModel(ICON_MODEL);
                animatable.getModelController().setTextureLocation(blueprintData.getIcon());
                cacheAnimatable(itemStack, context, animatable);
                return animatable;
            }

            VehicleData vehicleData = getVehicleData(itemStack);
            if (vehicleData != null) {
                float size = (float) vehicleData.max.subtract(vehicleData.min).length();
                VehicleAnimatable vehicleAnimatable = new VehicleAnimatable(level, vehicleData, 1f / size);
                vehicleAnimatable.getModelController().setModel(ICON_MODEL);
                vehicleAnimatable.getModelController().setTextureLocation(BG_TEXTURE);
                vehicleAnimatable.alignToAxesByFirstSubPart();
                vehicleAnimatable.setTransform(new Transform());
                cacheAnimatable(itemStack, context, vehicleAnimatable);
                return vehicleAnimatable;
            }
        }

        var animatable = new ItemAnimatable(itemStack, level);
        animatable.getModelController().setModel(MODEL);
        animatable.getModelController().setTextureLocation(TEXTURE);
        cacheAnimatable(itemStack, context, animatable);
        return animatable;
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (!getRenderInstance(itemStack, level, displayContext).getModelController().getOriginModel().equals(
                OModel.getOrEmpty(MODEL)
        )) {
            if (!itemStack.has(MMDataComponents.getVEHICLE_BLUEPRINT_PATH())) {
            return new Vector3f(10f, -150f, -20f).mul((float) (Math.PI / 180f));
        }
            return super.getRenderRotation(itemStack, level, displayContext);
        }
        if (displayContext == ItemDisplayContext.GUI
                || displayContext == ItemDisplayContext.FIXED
                || displayContext == ItemDisplayContext.GROUND) {
            return new Vector3f(-15f, -30f, 45f).mul((float) (Math.PI / 180f));
        }
        return super.getRenderRotation(itemStack, level, displayContext);
    }

    @Override
    public Color getColor(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        return displayContext == ItemDisplayContext.GUI ? FabricatingBlueprintItem.COLOR : Color.WHITE;
    }

    @NotNull
    public static BlueprintData getBlueprintData(ItemStack stack) {
        BlueprintData bluePrintData = BlueprintData.EMPTY_BLUEPRINT;
        if (stack.has(MMDataComponents.getVEHICLE_BLUEPRINT_PATH())) {
            bluePrintData = MMDynamicRes.BLUEPRINTS.getOrDefault(stack.get(MMDataComponents.getVEHICLE_BLUEPRINT_PATH()), BlueprintData.EMPTY_BLUEPRINT);
        } else if (stack.has(MMDataComponents.getBLUEPRINT_DATA())) {
            bluePrintData = stack.getOrDefault(MMDataComponents.getBLUEPRINT_DATA(), BlueprintData.EMPTY_BLUEPRINT);
        }
        return bluePrintData;
    }

    @Nullable
    private static VehicleData getVehicleDataStatic(ItemStack stack) {
        VehicleData vehicleData = null;
        BlueprintData blueprintData = getBlueprintData(stack);
        if (blueprintData.getTemplate() != BlueprintData.EMPTY) {
            vehicleData = MMDynamicRes.TEMPLATES.get(blueprintData.getTemplate());
        } else if (stack.has(MMDataComponents.getVEHICLE_DATA())) {
            vehicleData = stack.get(MMDataComponents.getVEHICLE_DATA());
        }
        return vehicleData;
    }
}
