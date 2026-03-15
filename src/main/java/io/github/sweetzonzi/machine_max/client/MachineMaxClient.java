package io.github.sweetzonzi.machine_max.client;

import io.github.sweetzonzi.machine_max.MachineMax;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MachineMax.MOD_ID, dist = Dist.CLIENT)
public class MachineMaxClient {
    public MachineMaxClient(IEventBus bus, ModContainer container) {
        MachineMax.REGISTER.register(bus);
        // 配置菜单
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
