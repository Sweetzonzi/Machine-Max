package io.github.tt432.machinemax;

import cn.solarmoon.spark_core.entry_builder.ObjectRegister;
import com.mojang.logging.LogUtils;
import io.github.tt432.machinemax.common.attachment.MMAttachments;
import io.github.tt432.machinemax.common.block.MMBlockEntities;
import io.github.tt432.machinemax.common.block.MMBlocks;
import io.github.tt432.machinemax.common.creative_tab.MMCreativeTabs;
import io.github.tt432.machinemax.common.entity.MMEntities;
import io.github.tt432.machinemax.common.item.MMItems;
import io.github.tt432.machinemax.common.part.PartType;
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
        REGISTER.register(bus);
        MMBlocks.BLOCKS.register(bus);//注册所有方块
        MMEntities.register();//注册所有实体
        MMBlockEntities.BLOCK_ENTITIES.register(bus);//注册所有方块实体
        MMItems.ITEMS.register(bus);//注册所有物品
        MMCreativeTabs.CREATIVE_MODE_TABS.register(bus);//注册所有创造模式物品栏
        MMAttachments.ATTACHMENTS.register(bus);//注册所有附件类型
        PartType.PART_TYPE.register(bus);
    }

}