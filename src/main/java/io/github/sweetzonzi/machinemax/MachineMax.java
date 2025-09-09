package io.github.sweetzonzi.machinemax;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import cn.solarmoon.spark_core.resource.common.MultiModResourceRegistry;
import com.mojang.logging.LogUtils;
import io.github.sweetzonzi.machinemax.common.registry.*;
import io.github.sweetzonzi.machinemax.external.MMDynamicRes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import static io.github.sweetzonzi.machinemax.MachineMax.MOD_ID;

@Mod(MOD_ID)
public class MachineMax {
    //TODO:限制带阻尼关节安装部件时的质量差距/警告
    //TODO:排查AE86AT左轮胎转动惯量计算BUG
    //TODO:GUI贴图渲染
    //TODO:交互可视化
    //TODO:重构流体动力，额外引入升力/阻力系数随攻角变化，以及叶元体理论
    //TODO:交互系统的反馈信号以及连接多个子系统，依次互动/同时互动？
    //TODO:过载与座椅过载吸收/耐受
    //TODO:TAC:Z式部件制造
    //TODO:放置部件前检查空间是否足够
    //TODO:投射物与刀刃判定，使用Capability系统控制部件功能？
    //TODO:炮塔控制
    //TODO:方块代理子系统，互动以及tick时有对应方块的功能？
    //TODO:载具触发压力板
    //TODO:把拼好的载具保存为微缩模型，分不同可选比例
    //TODO:使用蓝图快速重新组装部分零件缺失的载具，需要图匹配算法
    public static final String MOD_ID = "machine_max";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ObjectRegister REGISTER = new ObjectRegister(MachineMax.MOD_ID, false);//一体化注册器

    public MachineMax(IEventBus bus, ModContainer container) {
        MultiModResourceRegistry.INSTANCE.registerModResources(MOD_ID, MachineMax.class);
        REGISTER.register(bus);
        MMDataRegistries.register();//注册所有自定义注册器
        MMBlocks.register();//注册所有方块
        MMEntities.register();//注册所有实体
        MMBlockEntities.register();//注册所有方块实体
        MMDataComponents.register();//注册所有物品数据组件
        MMAttachments.register();//注册所有附件类型
        MMCodecs.register(bus);//注册所有编解码器
        MMCommands.register();//注册所有指令
        MMDynamicRes.initResources();//首次启动时读取外部资源文件
        MMItems.register();//通过kotlin注册的所有物品
        MMCreativeTabs.register();//注册所有创造模式物品栏
        MMMenus.register(bus);//注册所有菜单
        MMSounds.register();//注册所有音效
        MMPackModuleRegistries.register();//注册所有SparkCore扩展包模块
    }

}