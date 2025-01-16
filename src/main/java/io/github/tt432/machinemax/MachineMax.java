package io.github.tt432.machinemax;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import com.mojang.logging.LogUtils;
import io.github.tt432.machinemax.common.registry.*;
import io.github.tt432.machinemax.common.registry.MMBlockEntities;
import io.github.tt432.machinemax.common.registry.MMCreativeTabs;
import io.github.tt432.machinemax.common.registry.PartType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

import static io.github.tt432.machinemax.MachineMax.MOD_ID;

@Mod(MOD_ID)
public class MachineMax {

    public static final String MOD_ID = "machine_max";
    public static final Logger LOGGER = LogUtils.getLogger();
    public static final ObjectRegister REGISTER = new ObjectRegister(MachineMax.MOD_ID, true);//一体化注册器
    public MachineMax(IEventBus bus){
        PartType.PART_TYPE.register(bus);
        REGISTER.register(bus);
        MMBlocks.register();//注册所有方块
        MMEntities.register();//注册所有实体
        MMBlockEntities.register();//注册所有方块实体
        MMDataComponents.register();//注册所有物品数据组件
        MMItems.register();//注册所有物品
        MMCreativeTabs.register();//注册所有创造模式物品栏
        MMAttachments.register();//注册所有附件类型

    }

}