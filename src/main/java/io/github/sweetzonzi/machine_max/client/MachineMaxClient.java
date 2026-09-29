package io.github.sweetzonzi.machine_max.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.sighs.apricityui.init.Element;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
import io.github.sweetzonzi.machine_max.client.render.gui.element.PartModelElement;
import io.github.sweetzonzi.machine_max.client.render.gui.screen.PdaScreen;
import io.github.sweetzonzi.machine_max.common.item.prop.PdaItem;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import java.io.IOException;
@Mod(value = MachineMax.MOD_ID, dist = Dist.CLIENT)
public class MachineMaxClient {

    public MachineMaxClient(IEventBus bus, ModContainer container) {
        MachineMax.REGISTER.register(bus);
        registerAuiElements();
        // 共通物品类的客户端界面钩子
        PdaItem.setScreenOpener(PdaScreen::open);
        // 配置菜单
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        bus.addListener(MMClientConfig::onChangeConfig);
        // 注册自定义着色器
        bus.addListener(MachineMaxClient::onRegisterShaders);
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
}
