package io.github.sweetzonzi.machinemax.common.registry;

import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.client.gui.FabricatingMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class MMMenus {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MachineMax.MOD_ID);

    public static final Supplier<MenuType<FabricatingMenu>> FABRICATING_MENU = MENU_TYPES.register(
            "fabricating_menu",
            () -> new MenuType<>(FabricatingMenu::new, FeatureFlags.DEFAULT_FLAGS)
    );
}
