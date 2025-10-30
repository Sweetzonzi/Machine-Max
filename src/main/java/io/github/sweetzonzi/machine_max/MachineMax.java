package io.github.sweetzonzi.machine_max;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import com.mojang.logging.LogUtils;
import io.github.sweetzonzi.machine_max.common.registry.*;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import static io.github.sweetzonzi.machine_max.MachineMax.MOD_ID;

@Mod(MOD_ID)
public class MachineMax {
    //TODO:限制带阻尼关节安装部件时的质量差距/警告
    //TODO:排查AE86AT左轮胎转动惯量计算BUG
    //TODO:GUI贴图渲染
    //TODO:交互可视化
    //TODO:重构流体动力，额外引入升力/阻力系数随攻角变化，以及叶元体理论
    //TODO:交互系统的反馈信号以及连接多个子系统，依次互动/同时互动？
    //TODO:过载与座椅过载吸收/耐受
    //TODO:可配置的全覆盖座椅转移伤害至部件和子系统，订阅实体受伤事件处理
    //TODO:放置部件前检查空间是否足够
    //TODO:投射物与刀刃判定，使用Capability系统控制子部件是否具有此功能？
    //TODO:炮塔控制
    //TODO:通用分层作动器控制，计算期望姿态，计算所需角速度，计算所需舵面偏角/推进器推力等
    //TODO:外骨骼与机甲，穿戴外骨骼时仍可乘坐载具
    //TODO:方块/方块实体代理子系统（存在于一个FakeLevel），互动以及tick时有对应方块的功能
    //TODO:载具触发压力板
    //TODO:把拼好的载具保存为微缩模型，分不同可选比例
    //TODO:使用蓝图快速重新组装部分零件缺失的载具，需要图匹配算法
    //TODO:指定连接口是否有部件连接的molang，或许返回部件名？
    //TODO:显示内含物品的fast_item_storage子系统，容量为1，互动立刻和手中物品交换，无GUI
    //TODO:对接口支持信号转义，改变接收到的信号内容的频道再输出
    //TODO:部件耐久度迁移至subpart，结构完整度迁移至对接口，并根据命中点到各对接口的距离控制结构完整性损失分配
    //TODO:预制装配体，类似于部件的组装，但可预先装好方便使用。与蓝图不同的是使用后消耗，且拆卸时获得的是零散部件而非装配体本身。可json配置，也可动态保存生成，同时保留部件内数据如储物子系统内容物。
    //TODO:改装件，改变部件或子系统属性
    //TODO:子系统数据同步
    //TODO:molang驱动的实时属性？护甲水平，摩擦等（性能问题？）
    public static final String MOD_ID = "machine_max";
    public static final Logger LOGGER = LogUtils.getLogger();
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
    }
}