package io.github.sweetzonzi.machine_max.client;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.input.CameraController;
import io.github.sweetzonzi.machine_max.client.input.NativeKeyListener;
import io.github.sweetzonzi.machine_max.common.registry.MMVisualEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void init(FMLClientSetupEvent event) {
        CameraController.init();
        MMVisualEffects.init();
        // 配置dll库在run路径下的相对子路径
        System.setProperty("jnativehook.max_machine.path"
                , "config/machine_max/libs/jNativeLib"); //todo: 应该实现自动从resource复制文件、生成相对子路径 | 参考早期外部表复制方法
        NativeKeyListener.setUp();
        MachineMax.LOGGER.debug("Client setup complete");
    }
}
