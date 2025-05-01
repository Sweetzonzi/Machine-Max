package io.github.tt432.machinemax;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import com.mojang.logging.LogUtils;
import io.github.tt432.machinemax.common.registry.*;
import io.github.tt432.machinemax.external.MMDynamicRes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC;
import org.slf4j.Logger;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@Mod(MOD_ID)
public class MachineMax {
    //TODO:物品本地化名
    //TODO:物品栏模型渲染
    //TODO:载具的保存与一键部署
    //TODO:自动匹配部件变体类型与对接口？
    //TODO:动画支持
    //TODO:部件放置方式修改：避免陷入地面
    //TODO:指令删除世界内的物理体
    //TODO:无成员且低速时自动刹车
    public static final String MOD_ID = "machine_max";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ObjectRegister REGISTER = new ObjectRegister(MachineMax.MOD_ID, false);//一体化注册器

    public MachineMax(IEventBus bus) {
        REGISTER.register(bus);
        MMDynamicRes.loadData();//读取外部资源文件
        bus.addListener(MMDynamicRes::registerReloadListeners);
        MMDataRegistries.register();//注册所有自定义注册器
        MMBlocks.register();//注册所有方块
        MMEntities.register();//注册所有实体
        MMBlockEntities.register();//注册所有方块实体
        MMDataComponents.register();//注册所有物品数据组件
        MMItems.register();//注册所有物品
        MMCreativeTabs.register();//注册所有创造模式物品栏
        MMAttachments.register();//注册所有附件类型
        MMCodecs.register(bus);//注册所有编解码器
        MMCommands.register();//注册所有指令
        MMVisualEffects.register();//注册所有视觉效果
    }

}