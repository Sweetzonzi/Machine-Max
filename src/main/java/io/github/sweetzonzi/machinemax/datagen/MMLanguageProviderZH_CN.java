package io.github.sweetzonzi.machinemax.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderZH_CN extends LanguageProvider {
    public MMLanguageProviderZH_CN(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        //按键类别
        this.add("key.category.machine_max.general", "Machine Max:通用");
        this.add("key.category.machine_max.ground", "Machine Max:地面载具");
        this.add("key.category.machine_max.ship", "Machine Max:舰艇");
        this.add("key.category.machine_max.plane", "Machine Max:飞行器");
        this.add("key.category.machine_max.mech", "Machine Max:机甲");
        this.add("key.category.machine_max.assembly", "Machine Max:组装");
        //按键名称-通用
        this.add("key.machine_max.general.free_cam", "自由摄像");
        this.add("key.machine_max.general.interact", "交互");
        this.add("key.machine_max.general.leave_vehicle", "离开载具");
        //按键名称-地面载具
        this.add("key.machine_max.ground.forward", "前进");
        this.add("key.machine_max.ground.backward", "后退");
        this.add("key.machine_max.ground.leftward", "左转");
        this.add("key.machine_max.ground.rightward", "右转");
        this.add("key.machine_max.ground.clutch", "离合");
        this.add("key.machine_max.ground.up_shift", "升档");
        this.add("key.machine_max.ground.down_shift", "降档");
        this.add("key.machine_max.ground.hand_brake", "手刹 (按住)");
        this.add("key.machine_max.ground.toggle_hand_brake", "手刹 (切换)");
        //按键名称-组装
        this.add("key.machine_max.assembly.cycle_connector", "循环部件连接口");
        this.add("key.machine_max.assembly.cycle_variant", "循环部件变体");
        //提示信息
        this.add("message.machine_max.leaving_vehicle", "长按[%1$s]键%2$s/0.50秒以离开载具");
        this.add("message.machine_max.watch_interact_box_info", "按[%1$s]键与%2$s互动");
        this.add("error.machine_max.use_part_item", "尝试放置%1$s时出现错误：%2$s");
        this.add("tooltip.machinemax.crossbar.interact", "互动以拆除：");
        this.add("tooltip.machinemax.spray_can.interact", "互动以喷涂：");
        //物品
        //Item
        this.add("itemGroup.machine_max.blueprint", "MachineMax: 自定义蓝图");
        this.add("itemGroup.machine_max.main", "MachineMax: 部件与工具");
        this.add("item.machine_max.crossbar_item", "撬棍");
        this.add("item.machine_max.spray_can_item", "喷漆罐");
        this.add("item.machine_max.test_car_spawner", "测试车辆生成器");
        this.add("item.machine_max.vehicle_recorder_item", "载具设计记录器");
    }
}
