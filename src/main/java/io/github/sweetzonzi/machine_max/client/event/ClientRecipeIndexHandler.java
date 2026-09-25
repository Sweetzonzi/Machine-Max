package io.github.sweetzonzi.machine_max.client.event;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RecipesUpdatedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端制造索引的生命周期。
 *
 * <p>客户端索引的数据源是服务器发来的配方包：每次收到配方包都在主线程整体重建，
 * 退出服务器时只清空客户端容器（服务端容器由服务端自己的重载流程维护，两者互不影响）。</p>
 *
 * <p>订阅优先级取 {@link EventPriority#HIGHEST}，保证 JEI 等按默认优先级订阅
 * {@link RecipesUpdatedEvent} 的消费方读到的是已建好的索引。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
@OnlyIn(Dist.CLIENT)
public class ClientRecipeIndexHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRecipesUpdated(RecipesUpdatedEvent event) {
        MMDynamicRes.rebuildFabricatingIndex(event.getRecipeManager(), false);
        MMDynamicRes.rebuildResearchIndex(event.getRecipeManager(), false);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        MMDynamicRes.clearRecipeIndex(false);
    }
}
