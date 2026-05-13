package io.github.sweetzonzi.machine_max.common.registry;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.menu.BlueprintResearchMenu;
import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import io.github.sweetzonzi.machine_max.common.menu.VehicleNamingMenu;
import io.github.sweetzonzi.machine_max.common.menu.ItemStorageSubsystemMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MMMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MachineMax.MOD_ID);

    public static final Supplier<MenuType<FabricatingMenu>> FABRICATING_MENU = MENU_TYPES.register(
            "fabricating_menu",
            ()-> IMenuTypeExtension.create(FabricatingMenu::new)
    );

    public static final Supplier<MenuType<VehicleNamingMenu>> VEHICLE_NAMING_MENU = MENU_TYPES.register(
            "vehicle_naming_menu",
            ()-> IMenuTypeExtension.create(VehicleNamingMenu::new)
    );

    /**
     * 蓝图研发菜单
     */
    public static final Supplier<MenuType<BlueprintResearchMenu>> BLUEPRINT_RESEARCH_MENU = MENU_TYPES.register(
            "blueprint_research",
            () -> IMenuTypeExtension.create(BlueprintResearchMenu::new));

    public static final Supplier<MenuType<ItemStorageSubsystemMenu>> ITEM_STORAGE_SUBSYSTEM_MENU = MENU_TYPES.register(
            "item_storage_subsystem_menu",
            () -> IMenuTypeExtension.create(ItemStorageSubsystemMenu::new)
    );

    public static void register(IEventBus bus){
        MENU_TYPES.register(bus);
    }
}
