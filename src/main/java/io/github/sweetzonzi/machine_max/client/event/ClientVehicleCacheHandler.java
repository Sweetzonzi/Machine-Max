package io.github.sweetzonzi.machine_max.client.event;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端载具缓存的兜底清理。
 *
 * <p>常规路径由进入世界时的 {@link ObjectManager#clearClientVehiclesOnLevelLoad} 与卸载世界时的
 * {@link ObjectManager#unloadVehicleData} 覆盖。退出服务器的流程里连接会在世界卸载之前关闭，
 * 这里再清一次，使上一个连接的载具实例不会因为卸载回调缺失而留在
 * {@link ObjectManager#clientAllVehicles} 中。</p>
 *
 * <p>调用线程：主线程（客户端）。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class ClientVehicleCacheHandler {

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ObjectManager.clearClientVehicleCache();
    }
}
