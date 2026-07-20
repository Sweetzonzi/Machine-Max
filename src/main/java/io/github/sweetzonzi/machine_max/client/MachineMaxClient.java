package io.github.sweetzonzi.machine_max.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.client.render.MMRenderTypes;
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
}
