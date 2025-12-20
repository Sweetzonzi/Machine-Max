package io.github.sweetzonzi.machine_max.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.gui.hud3d.Hud3DContext;
import io.github.sweetzonzi.machine_max.client.gui.hud3d.IHud3DElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class Hud3DRenderer {

    /**
     * 所有已注册的 3D HUD 元素
     */
    private static final List<IHud3DElement> ELEMENTS = new ArrayList<>();

    /**
     * 注册一个 3D HUD 元素
     * 建议在 ClientSetup 阶段调用
     */
    public static void register(IHud3DElement element) {
        ELEMENTS.add(element);
    }

    /**
     * 第一人称手部渲染阶段事件
     */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        // 只在主手阶段渲染一次，避免左右手重复
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        render(
                mc,
                player,
                event.getPoseStack(),
                event.getMultiBufferSource(),
                event.getPartialTick(),
                event.getPackedLight()
        );
    }

    /**
     * 实际执行 3D HUD 渲染
     */
    private static void render(
            Minecraft mc,
            LocalPlayer player,
            PoseStack poseStack,
            MultiBufferSource buffer,
            float partialTicks,
            int packedLight
    ) {
        if (ELEMENTS.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        if (mc.options.bobView().get()) {
            // 补偿视角摇晃
            float f = player.walkDist - player.walkDistO;
            float f1 = -(player.walkDist + f * partialTicks);
            float f2 = Mth.lerp(partialTicks, player.oBob, player.bob);
            poseStack.translate(0.5f * Mth.sin(f1 * (float) Math.PI) * f2 * 0.5F,
                    0.5f * Math.abs(Mth.cos(f1 * (float) Math.PI) * f2), -0.5F);
        } else {
            // 否则仅挪动视平面
            poseStack.translate(0, 0, -0.5F);
        }
        poseStack.pushPose();
        /*
         * 这里定义“HUD 锚点”
         *   +X 向右
         *   +Y 向上
         *   -Z 朝向视线前方
         */
        // 整体缩放（UI 尺寸控制）
        poseStack.scale(0.005F, 0.005F, 0.005F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.mulPose(Axis.ZP.rotationDegrees(180));
        // 生成上下文对象
        Hud3DContext context = new Hud3DContext(
                mc,
                player,
                poseStack,
                buffer,
                partialTicks,
                packedLight
        );
        // 遍历所有已注册的 3D HUD 元素
        for (IHud3DElement element : ELEMENTS) {
            if (!element.shouldRender(player)) {
                continue;
            }
            poseStack.pushPose();
            element.render(context);// 渲染当前元素
            poseStack.popPose();
        }
        poseStack.popPose();
        poseStack.popPose();
    }
}
