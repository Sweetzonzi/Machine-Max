package io.github.sweetzonzi.machine_max.client.renderer;

import io.github.sweetzonzi.machine_max.common.registry.MMBlockEntities;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 在此注册所有Renderer和Layer
 */
@EventBusSubscriber(value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class MMRenderHandler {
    @SubscribeEvent//注册每个实体渲染器
    public static void onEntityRendererRegistry(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(MMEntities.getPART_ENTITY().get(), PartEntityRenderer::new);
        event.registerBlockEntityRenderer(MMBlockEntities.FABRICATOR_BLOCK_ENTITY.get(), FabricatorBlockEntityRenderer::new);
    }
}
