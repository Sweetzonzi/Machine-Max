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
        //内容包异常处理
        this.add("error.machine_max.load", "加载外部包文件: %1$s 时出错，原因: ");
        this.add("error.machine_max.seat_subsystem.no_locator", "座椅子系统必须填写定位器名称(如\"locator\": \"seat_locator\")以指定乘客乘坐位置");
        this.add("error.machine_max.seat_subsystem.no_view", "座椅子系统必须允许乘客使用第一人称视角或第三人称视角之一");
        //提示信息
        this.add("message.machine_max.leaving_vehicle", "长按[%1$s]键%2$s/0.50秒以离开载具");
        this.add("message.machine_max.watch_interact_box_info", "按[%1$s]键与%2$s互动");
        this.add("error.machine_max.use_part_item", "尝试放置%1$s时出现错误：%2$s");
        this.add("tooltip.machinemax.crowbar.interact", "互动以拆除：");
        this.add("tooltip.machinemax.spray_can.interact", "互动以喷涂：");
        this.add("message.machine_max.blueprint_saved", "蓝图已保存至%1$s");
        this.add("message.machine_max.blueprint_error", "保存蓝图失败:%1$s");
        this.add("message.machine_max.blueprint_pass", "未选中任何载具，取消保存蓝图");
        //物品
        //Item
        this.add("itemGroup.machine_max.blueprint", "MachineMax: 自定义蓝图");
        this.add("itemGroup.machine_max.main", "MachineMax: 部件与工具");
        this.add("item.machine_max.crowbar", "撬棍");
        this.add("item.machine_max.spray_can", "喷漆罐");
        this.add("item.machine_max.empty_vehicle_blueprint", "空白载具蓝图");
    }
}
