package io.github.sweetzonzi.machine_max.client;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.event.RenderLevelLastEvent;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.io.IOException;
import java.util.Random;
@EventBusSubscriber(Dist.CLIENT)
@Mod(value = MachineMax.MOD_ID, dist = Dist.CLIENT)
public class MachineMaxClient {

    /** 失色后处理 PostChain 实例，进入世界时通过手动构造加载 */
    private static PostChain desaturateEffect;
    /** 当前失色程度（0.0 = 原色，1.0 = 完全灰度） */
    private static float desaturationLevel = 0f;
    /** 随机数生成器，用于连续随机游走 */
    private static final Random RANDOM = new Random();
    /** 后处理是否已成功加载 */
    private static boolean effectLoaded = false;

    /** 过载黑视/红视后处理，正值为黑视，负值为红视 */
    private static PostChain overloadVisionEffect;
    /** 当前测试过载值，范围 [-1, 1] */
    private static float overloadLevel = 0f;
    /** 过载后处理是否已尝试加载 */
    private static boolean overloadEffectLoaded = false;
    private static int overloadEffectWidth = -1;
    private static int overloadEffectHeight = -1;

    public MachineMaxClient(IEventBus bus, ModContainer container) {
        MachineMax.REGISTER.register(bus);
        // 配置菜单
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener(MMClientConfig::onChangeConfig);
        // 注册自定义着色器
        bus.addListener(MachineMaxClient::onRegisterShaders);
    }

    /**
     * 注册自定义着色器：rendertype_inspector（内构查看剪影）。
     * 着色器文件位于 assets/machine_max/shaders/core/rendertype_inspector.json/.vsh/.fsh，
     * 使用 {@link DefaultVertexFormat#NEW_ENTITY}（含 UV0 属性用于纹理采样做 alpha 遮罩）。
     */
    private static void onRegisterShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.parse("machine_max:rendertype_inspector"),
                    DefaultVertexFormat.NEW_ENTITY
                ),
                shader -> {
                    MMRenderTypes.inspectorShader = shader;
                    MachineMax.LOGGER.debug("rendertype_inspector 着色器注册成功");
                }
            );
        } catch (IOException e) {
            MachineMax.LOGGER.error("无法加载 rendertype_inspector 着色器", e);
        }
    }

    /** 手持物渲染完成后执行失色后处理，当前启用用于验证最终场景挂载点。 */
    @SubscribeEvent
    private static void onRenderDesaturateLast(RenderLevelLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        // 首次进入世界时手动构造 PostChain（仅一次，构造失败不再重试）
        if (!effectLoaded) {
            try {
                desaturateEffect = new PostChain(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    mc.getMainRenderTarget(),
                    ResourceLocation.parse("machine_max:shaders/post/desaturate.json")
                );
                desaturateEffect.resize(mc.getWindow().getWidth(), mc.getWindow().getHeight());
                effectLoaded = true;
                MachineMax.LOGGER.debug("失色后处理已加载，开始随机游走测试");
            } catch (IOException e) {
                MachineMax.LOGGER.error("失色后处理加载失败", e);
                effectLoaded = true; // 标记已尝试，不再重复失败
                return;
            }
        }

        if (desaturateEffect == null) return;

        // 随机游走：每帧向 desaturationLevel 叠加微小偏移，钳位到 [0, 1]
        desaturationLevel += (RANDOM.nextFloat() - 0.5f) * 0.04f;
        desaturationLevel = Math.clamp(desaturationLevel, 0f, 1f);

        // 写入 GPU 侧 uniform（PostChain 内置方法，遍历所有 pass 写入）
        desaturateEffect.setUniform("Desaturation", desaturationLevel);

        // 执行后处理：读主缓冲 → 着色器处理 → 写回主缓冲
        desaturateEffect.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(true);
    }

    /**
     * 过载视觉测试：Overload > 0 为正过载黑视，Overload < 0 为负过载红视。
     * 测试值随时间在 [-1, 1] 间平滑往返。
     */
    // @SubscribeEvent // 暂时禁用过载测试；与失色测试二选一启用
    private static void onRenderOverloadLast(RenderLevelLastEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        if (!overloadEffectLoaded) {
            try {
                overloadVisionEffect = new PostChain(
                    mc.getTextureManager(),
                    mc.getResourceManager(),
                    mc.getMainRenderTarget(),
                    ResourceLocation.parse("machine_max:shaders/post/overload_vision.json")
                );
                overloadEffectWidth = mc.getWindow().getWidth();
                overloadEffectHeight = mc.getWindow().getHeight();
                overloadVisionEffect.resize(overloadEffectWidth, overloadEffectHeight);
                overloadEffectLoaded = true;
                MachineMax.LOGGER.debug("过载黑视/红视后处理已加载，开始 [-1, 1] 循环测试");
            } catch (IOException | JsonSyntaxException e) {
                MachineMax.LOGGER.error("过载黑视/红视后处理加载失败", e);
                overloadEffectLoaded = true;
                return;
            }
        }

        if (overloadVisionEffect == null) return;

        int width = mc.getWindow().getWidth();
        int height = mc.getWindow().getHeight();
        if (width != overloadEffectWidth || height != overloadEffectHeight) {
            overloadEffectWidth = width;
            overloadEffectHeight = height;
            overloadVisionEffect.resize(width, height);
        }

        double seconds = System.nanoTime() / 1_000_000_000.0;
        overloadLevel = (float) Math.sin(seconds * 0.7);
        overloadVisionEffect.setUniform("Overload", overloadLevel);
        overloadVisionEffect.process(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        mc.getMainRenderTarget().bindWrite(true);
    }

    /**
     * 退出世界时释放 PostChain GPU 资源。
     */
    @SubscribeEvent
    private static void onClientTick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().level == null && (effectLoaded || overloadEffectLoaded)) {
            if (desaturateEffect != null) {
                desaturateEffect.close();
                desaturateEffect = null;
            }
            if (overloadVisionEffect != null) {
                overloadVisionEffect.close();
                overloadVisionEffect = null;
            }
            effectLoaded = false;
            desaturationLevel = 0f;
            overloadEffectLoaded = false;
            overloadLevel = 0f;
            overloadEffectWidth = -1;
            overloadEffectHeight = -1;
            MachineMax.LOGGER.debug("客户端测试后处理已释放");
        }
    }
}
