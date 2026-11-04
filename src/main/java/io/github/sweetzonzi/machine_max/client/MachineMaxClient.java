package io.github.sweetzonzi.machine_max.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sighs.apricityui.init.Element;
import cn.solarmoon.spark_core.pack.diagnostic.event.PackAllFinishedEvent;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.element.PartModelElement;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.PdaScreen;
import io.github.sweetzonzi.machine_max.client.render.post.ExplosionLightState;
import io.github.sweetzonzi.machine_max.client.render.post.MuzzleFlashLightState;
import io.github.sweetzonzi.machine_max.client.render.renderer.TrackRenderer;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaItem;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
import io.github.sweetzonzi.machine_max.common.mech.projectile.MuzzleFlashLight;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
@Mod(value = MachineMax.MOD_ID, dist = Dist.CLIENT)
public class MachineMaxClient {

    public MachineMaxClient(IEventBus bus, ModContainer container) {
        MachineMax.REGISTER.register(bus);
        registerAuiElements();
        // 共通物品类的客户端界面钩子
        PdaItem.setScreenOpener(PdaScreen::open);
        // 共通起爆流程的客户端火光登记钩子
        ExplosionManager.setLightSink(ExplosionLightState::onDetonate);
        // 共通开火流程的客户端枪口火光登记钩子
        MuzzleFlashLight.setSink(MuzzleFlashLightState::onMuzzleFlash);
        // 配置菜单
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener(MMClientConfig::onChangeConfig);
        // 注册自定义着色器
        bus.addListener(MachineMaxClient::onRegisterShaders);
        // 内容包重载后丢掉按型号缓存的履带模型外观
        NeoForge.EVENT_BUS.addListener(MachineMaxClient::onPackFinished);
    }

    /**
     * 服务端下发的内容包加载完成：型号模型与型号表已被替换，按型号缓存的外观必须丢掉。
     *
     * <p>只在客户端接收远端内容包（{@code fromServer}）时失效——本地包不重读模型
     * （{@code ModelModule} 对本地包直接返回），清缓存没有意义。</p>
     */
    private static void onPackFinished(PackAllFinishedEvent event) {
        if (!event.getContext().isClientSide() || !event.getContext().getFromServer()) return;
        TrackRenderer.invalidateModels();
    }

    /**
     * 登记本项目的 AUI 自定义元素。
     *
     * <p>这里用 {@link Element#register} 直接登记标签，而不用 AUI 的 {@code @ElementRegister} 注解：
     * AUI 的注解扫描在它自己的模组构造期执行，且执行前先把扫描范围收窄到
     * {@code com.sighs.apricityui.element}，本项目声明了 {@code ordering="AFTER"}，
     * 注定在 AUI 之后构造，注解扫描看不到本项目的类。</p>
     *
     * <p>时机：模组构造期早于任何 Document 的创建，因此在页面被解析前登记即可生效。</p>
     */
    private static void registerAuiElements() {
        Element.register(PartModelElement.TAG_NAME,
                (document, tagName) -> new PartModelElement(document));
    }

    /**
     * 注册自定义着色器：
     * <ul>
     *   <li>rendertype_inspector（内构查看剪影）</li>
     *   <li>rendertype_anchor（锚点公告板，穿透方块、不透明满亮度）</li>
     * </ul>
     * 着色器文件位于 assets/machine_max/shaders/core/ 下，
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
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    ResourceLocation.parse("machine_max:rendertype_anchor"),
                    DefaultVertexFormat.NEW_ENTITY
                ),
                shader -> {
                    MMRenderTypes.anchorShader = shader;
                    MachineMax.LOGGER.debug("rendertype_anchor 着色器注册成功");
                }
            );
        } catch (IOException e) {
            MachineMax.LOGGER.error("无法加载自定义核心着色器", e);
        }
    }
}
