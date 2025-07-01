package io.github.sweetzonzi.machinemax;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import com.mojang.logging.LogUtils;
import io.github.sweetzonzi.machinemax.client.input.CameraController;
import io.github.sweetzonzi.machinemax.common.registry.*;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import io.github.sweetzonzi.machinemax.util.MMJoystickHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import static io.github.sweetzonzi.machinemax.MachineMax.MOD_ID;

@Mod(MOD_ID)
public class MachineMax {
    //TODO:限制带阻尼关节安装部件时的质量差距/警告
    //TODO:排查AE86AT左轮胎转动惯量计算BUG
    //TODO:动画支持
    //TODO:交互系统的反馈信号以及连接多个子系统，依次互动/同时互动？
    //TODO:过载与座椅过载吸收/耐受
    //TODO:自定义HUD
    //TODO:TAC:Z式部件制造
    //TODO:基本材料物品:结构零件、动力零件、电子零件
    //TODO:放置部件前检查空间是否足够
    //TODO:带声速和多普勒效应的音效系统
    //TODO:投射物
    //TODO:炮塔控制
    //TODO:载具触发压力板
    //TODO:内容包之间的依赖关系
    //TODO:蓝图对内容包的依赖关系
    public static final String MOD_ID = "machine_max";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ObjectRegister REGISTER = new ObjectRegister(MachineMax.MOD_ID, false);//一体化注册器

    public MachineMax(IEventBus bus) {
        REGISTER.register(bus);
        bus.addListener(CameraController::init);
        MMDataRegistries.register();//注册所有自定义注册器
        MMBlocks.register();//注册所有方块
        MMEntities.register();//注册所有实体
        MMBlockEntities.register();//注册所有方块实体
        MMDataComponents.register();//注册所有物品数据组件
        MMAttachments.register();//注册所有附件类型
        MMCodecs.register(bus);//注册所有编解码器
        MMCommands.register();//注册所有指令
        MMVisualEffects.register();//注册所有视觉效果
        MMDynamicRes.initResources();//首次启动时读取外部资源文件
        bus.addListener(MMDynamicRes::init);//CommonSetup时读取外部数据文件
        bus.addListener(MMDynamicRes.DataPackReloader::registerClientReloadListeners);//服务端的注册Listener位于DataPackReloader中
        MMItems.register();//通过kotlin注册的所有物品
        MMCreativeTabs.register();//注册所有创造模式物品栏
        MMMenus.register(bus);//注册所有菜单
    }

}