package io.github.sweetzonzi.machine_max.client.render.renderer;

import io.github.sweetzonzi.machine_max.client.render.renderer.block.FabricatorBlockEntityRenderer;
import io.github.sweetzonzi.machine_max.client.render.renderer.block.ResearchTableBlockEntityRenderer;
import io.github.sweetzonzi.machine_max.client.render.renderer.block.TotalStationBlockEntityRenderer;
import io.github.sweetzonzi.machine_max.common.registry.MMBlockEntities;
import io.github.sweetzonzi.machine_max.common.registry.MMEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * 在此注册所有Renderer和Layer
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class MMRenderHandler {
    @SubscribeEvent//注册每个实体渲染器
    public static void onEntityRendererRegistry(EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(MMEntities.getPART_ENTITY().get(), PartEntityRenderer::new);
        event.registerBlockEntityRenderer(MMBlockEntities.getFABRICATOR_BLOCK_ENTITY().get(), FabricatorBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MMBlockEntities.getRESEARCH_TABLE_BLOCK_ENTITY().get(), ResearchTableBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(MMBlockEntities.getTOTAL_STATION_BLOCK_ENTITY().get(), TotalStationBlockEntityRenderer::new);
    }
}
