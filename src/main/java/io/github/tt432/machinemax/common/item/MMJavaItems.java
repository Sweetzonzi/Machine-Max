package io.github.tt432.machinemax.common.item;

import io.github.tt432.machinemax.MachineMax;
import io.github.tt432.machinemax.common.vehicle.VehicleCore;
import io.github.tt432.machinemax.common.vehicle.VehicleManager;
import io.github.tt432.machinemax.common.vehicle.data.VehicleData;
import io.github.tt432.machinemax.external.MMDynamicRes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.internal.versions.neoforge.NeoForgeVersion;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static io.github.tt432.machinemax.MachineMax.LOGGER;
import static io.github.tt432.machinemax.MachineMax.MOD_ID;

public class MMJavaItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MOD_ID);
    public static List<DeferredHolder<Item, Item>> BLUEPRINT_EGGS = new ArrayList<>();
    public static DeferredHolder<Item, Item> getBluePrintSpawner(ResourceLocation location, VehicleData vehicleData) {
//        LOGGER.info("BluePrint SpawnEgg:  "+location+"  has been register.");
        return ITEMS.register(
                location.getPath(),
                () -> new Item(new Item.Properties()) {
                    @Override
                    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
                        if(!level.isClientSide()){
                            VehicleManager.addVehicle(new VehicleCore(level, vehicleData));
                        }
                        return super.use(level, player, usedHand);
                    }

                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                        //物品栏鼠标自定义信息 （正在筹备）
//                        tooltipComponents.add(Component.translatable("tooltip.%s.%s.details".formatted(MOD_ID, MMDynamicRes.getRealName(location.getPath()).replace("/", ".")))); // 支持本地化
                    }
                }
        );
    }

    public static void register(IEventBus modEventBus) {
        MMDynamicRes.BLUEPRINTS.forEach((location, vehicleData) -> {
            BLUEPRINT_EGGS.add(getBluePrintSpawner(location,vehicleData));
        });
        ITEMS.register(modEventBus);
//        CREATIVE_MODE_TABS.register(modEventBus);
    }

}
