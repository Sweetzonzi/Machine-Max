package io.github.sweetzonzi.machine_max.common.item.prop;

import cn.solarmoon.spark_core.animation.IAnimatable;
import cn.solarmoon.spark_core.animation.ItemAnimatable;
import com.jme3.math.Transform;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.AssemblyData;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData;
import io.github.sweetzonzi.machine_max.common.visual.VehicleAnimatable;
import io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.*;

public class AssemblyItem extends BaseVehicleItem {

    public AssemblyItem() {
        super(new Properties().stacksTo(1).fireResistant());
    }

    @Nullable
    @Override
    protected VehicleData getVehicleData(ItemStack stack) {
        return getVehicleDataStatic(stack);
    }

    @Override
    protected VehicleCore createVehicle(Level level, VehicleData vehicleData) {
        return new VehicleCore(level, vehicleData, true);
    }

    @Override
    protected String getNameTranslationKey(ItemStack stack) {
        String itemName;
        ResourceLocation location = stack.get(MMDataComponents.getASSEMBLY_PATH());
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
            if (MMDynamicRes.TOOLTIPS.get(getAssemblyData(stack).getTooltip()) instanceof String content) {
                return content;
            }
        } catch (NullPointerException e) {
            return null;
        }
        return null;
    }

    @Override
    protected void onPlaceSuccess(Level level, PlacementContext ctx, ItemStack stack, Player player) {
        if (!player.hasInfiniteMaterials()) VisualEffectHelper.boundingBox = null;
        stack.consume(1, player);
        var pos = ctx.transform.getTranslation();
        ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL, pos.x, pos.y, pos.z, Math.max((int) ctx.halfExtents.length(), 30),
                ctx.halfExtents.x / 1.5f, ctx.halfExtents.y / 1.5f, ctx.halfExtents.z / 1.5f, 0.2f);
    }

    @Override
    public boolean use2dModel(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI) {
            AssemblyData assemblyData = getAssemblyData(itemStack);
            return !assemblyData.getIcon().equals(AssemblyData.EMPTY);
        }
        return false;
    }

    @Override
    public IAnimatable<?> createItemAnimatable(ItemStack itemStack, Level level, ItemDisplayContext context) {
        AssemblyData assemblyData = getAssemblyData(itemStack);

        if (context == ItemDisplayContext.GUI) {
            if (!assemblyData.getIcon().equals(AssemblyData.EMPTY)) {
                var animatable = new ItemAnimatable(itemStack, level);
                animatable.getModelController().setModel(VehicleBlueprintItem.ICON_MODEL);
                animatable.getModelController().setTextureLocation(assemblyData.getIcon());
                cacheAnimatable(itemStack, context, animatable);
                return animatable;
            }

            VehicleData vehicleData = getVehicleData(itemStack);
            if (vehicleData != null) {
                float size = (float) vehicleData.max.subtract(vehicleData.min).length();
                VehicleAnimatable vehicleAnimatable = new VehicleAnimatable(level, vehicleData, 1f / size);
                vehicleAnimatable.alignToAxesByFirstSubPart();
                vehicleAnimatable.setTransform(new Transform());
                cacheAnimatable(itemStack, context, vehicleAnimatable);
                return vehicleAnimatable;
            }
        }

        VehicleData vehicleData = getVehicleData(itemStack);
        if (vehicleData != null) {
            float scale = 1 / assemblyData.getScale();
            if (context.firstPerson()) scale *= 4f;
            VehicleAnimatable vehicleAnimatable = new VehicleAnimatable(level, vehicleData, scale);
            vehicleAnimatable.alignToAxesByFirstSubPart();
            vehicleAnimatable.setTransform(new Transform());
            cacheAnimatable(itemStack, context, vehicleAnimatable);
            return vehicleAnimatable;
        }

        var animatable = new ItemAnimatable(itemStack, level);
        animatable.getModelController().setModel(EmptyBlueprintItem.MODEL);
        animatable.getModelController().setTextureLocation(EmptyBlueprintItem.TEXTURE);
        cacheAnimatable(itemStack, context, animatable);
        return animatable;
    }

    @Override
    public Vector3f getRenderRotation(ItemStack itemStack, Level level, ItemDisplayContext displayContext) {
        if (displayContext == ItemDisplayContext.GUI && !itemStack.has(MMDataComponents.getASSEMBLY_PATH())) {
            return new Vector3f(10f, -150f, -20f).mul((float) (Math.PI / 180f));
        }
        return super.getRenderRotation(itemStack, level, displayContext);
    }

    @NotNull
    public static AssemblyData getAssemblyData(ItemStack stack) {
        AssemblyData assemblyData = AssemblyData.DEFAULT;
        if (stack.has(MMDataComponents.getASSEMBLY_PATH())) {
            assemblyData = MMDynamicRes.ASSEMBLIES.getOrDefault(stack.get(MMDataComponents.getASSEMBLY_PATH()), AssemblyData.DEFAULT);
        } else if (stack.has(MMDataComponents.getBLUEPRINT_DATA())) {
            assemblyData = stack.getOrDefault(MMDataComponents.getASSEMBLY_DATA(), AssemblyData.DEFAULT);
        }
        return assemblyData;
    }

    @Nullable
    private static VehicleData getVehicleDataStatic(ItemStack stack) {
        VehicleData vehicleData = null;
        AssemblyData assemblyData = getAssemblyData(stack);
        if (assemblyData.getTemplate() != AssemblyData.EMPTY) {
            vehicleData = MMDynamicRes.TEMPLATES.get(assemblyData.getTemplate());
        } else if (stack.has(MMDataComponents.getVEHICLE_DATA())) {
            vehicleData = stack.get(MMDataComponents.getVEHICLE_DATA());
        }
        return vehicleData;
    }
}
