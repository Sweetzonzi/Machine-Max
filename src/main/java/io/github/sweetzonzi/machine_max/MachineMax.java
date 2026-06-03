package io.github.sweetzonzi.machine_max;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import io.github.sweetzonzi.machine_max.client.MMClientConfig;
import io.github.sweetzonzi.machine_max.common.MMCommonConfig;
import io.github.sweetzonzi.machine_max.common.MMServerConfig;
import io.github.sweetzonzi.machine_max.common.registry.*;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.github.sweetzonzi.machine_max.MachineMax.MOD_ID;

@Mod(MOD_ID)
public class MachineMax {
    //TODO:保存的蓝图在指定路径储存，可被特定方块访问蓝图库，并制作蓝图物品
    //TODO:重构网络包及各类同步系统，将拆除等事件化，特别是断开移除逻辑，使之支持移除个别零件
    //TODO:优化关节断开逻辑：Vehicle每刻/事件触发检查关节连接关系，检测到不连通再断开记录的关系；断开网络包靠id识别SubPart而非载具uuid-部件uuid-接口名的方式以节约带宽
    //TODO:限制带阻尼关节安装部件时的质量差距？
    //TODO:排查AE86AT左轮胎转动惯量计算BUG（可能已修复）
    //TODO:GUI贴图渲染
    //TODO:交互系统的反馈信号以及连接多个子系统，依次互动/同时互动？
    //TODO:过载与座椅过载吸收/耐受
    //TODO:放置部件前检查空间是否足够
    //TODO:刀刃判定子系统
    //TODO:炮塔控制
    //TODO:通用分层作动器控制，计算期望姿态，计算所需角速度，计算所需舵面偏角/推进器推力等，见 https://chat.deepseek.com/share/pneht1jesjnjakyh9g
    //TODO:外骨骼与机甲，穿戴外骨骼时仍可乘坐载具
    //TODO:方块/方块实体代理子系统（存在于一个FakeLevel），互动以及tick时有对应方块的功能
    //TODO:载具触发压力板
    //TODO:把拼好的载具保存为微缩模型，分不同可选比例
    //TODO:使用蓝图快速重新组装部分零件缺失的载具，需要图匹配算法
    //TODO:显示内含物品的fast_item_storage子系统，容量为1，互动立刻和手中物品交换，无GUI
    //TODO:预制装配体的可动态保存生成
    //TODO:改装件，改变部件或子系统属性
    //TODO:molang驱动的实时属性？护甲水平，摩擦等（性能问题？）
    public static final String MOD_ID = "machine_max";
    public static final Logger LOGGER = LoggerFactory.getLogger("MachineMax");
    public static final ObjectRegister REGISTER = new ObjectRegister(MachineMax.MOD_ID);//一体化注册器

    public MachineMax(IEventBus bus, ModContainer container) {
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
        MMItems.register();//注册所有物品
        MMCreativeTabs.register();//注册所有创造模式物品栏
        MMMenus.register(bus);//注册所有菜单
        MMSounds.register();//注册所有音效
        MMResources.register();//注册所有自定义配方类型
        MMPackModuleRegistries.register(bus);//注册所有SparkCore扩展包模块
        // 注册配置文件
        container.registerConfig(
                ModConfig.Type.CLIENT,
                MMClientConfig.CLIENT_SPEC
        );
        container.registerConfig(
                ModConfig.Type.COMMON,
                MMCommonConfig.COMMON_SPEC
        );
        container.registerConfig(
                ModConfig.Type.SERVER,
                MMServerConfig.SERVER_SPEC
        );
    }
}