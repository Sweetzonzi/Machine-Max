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
        this.add("error.machine_max.invalid_resource_location", "文件资源路径非法，仅允许小写英文字母、数字、下划线和连字符");
        this.add("error.machine_max.subpart.zero_mass", "零件质量必须大于零");
        this.add("error.machine_max.subpart.empty_hit_boxes", "零件需要至少被指定一个碰撞体积");
        this.add("error.machine_max.subpart.locator_not_found", "未能在部件的模型中找到定位器%1$s");
        this.add("error.machine_max.part.subsystem_hitbox_not_found", "未能在部件%1$s中为子系统%2$s找到碰撞体积%3$s");
        this.add("error.machine_max.seat_subsystem.no_locator", "座椅子系统必须填写定位器名称(如\"locator\": \"seat_locator\")以指定乘客乘坐位置");
        this.add("error.machine_max.seat_subsystem.no_view", "座椅子系统必须允许乘客使用第一人称视角或第三人称视角之一");
        //组装异常处理
        this.add("error.machine_max.part.connector_locator_not_found", "部件%1$的模型中未找到的对接口%2$s的定位器%3$s");
        this.add("error.machine_max.part.invalid_connector_type", "部件%1$的对接口%2$s的类型%3$s非法，必须为\"Special\"或\"AttachPoint\"");
        this.add("error.machine_max.part.invalid_internal_connector_connection", "部件%1$s中的内部接口%2$s与%3$s的类型不匹配，至多只能有一个接口的类型为\"Special\"");
        //提示信息
        this.add("message.machine_max.leaving_vehicle", "长按[%1$s]键%2$s/0.50秒以离开载具");
        this.add("message.machine_max.watch_interact_box_info", "按[%1$s]键与%2$s互动");
        this.add("error.machine_max.use_part_item", "尝试放置%1$s时出现错误：%2$s");
        this.add("tooltip.machine_max.crowbar.safe_disassembly", "互动以安全拆除%1$s");
        this.add("tooltip.machine_max.crowbar.unsafe_disassembly", "结构完整性：%1$s/%2$s 互动以强行拆除%3$s (可能损坏部件)");
        this.add("tooltip.machine_max.wrench.disassembly", "结构完整性：%1$s/%2$s 潜行互动以安全解除%3$s的固定");
        this.add("tooltip.machine_max.wrench.repair", "结构完整性：%2$s/%3$s 部件耐久：%4$s/%5$s 互动以维修%1$s");
        this.add("tooltip.machine_max.wrench.cant_repair", "%1$s已被摧毁，无法通过扳手修复，潜行互动以解除固定便于拆除");
        this.add("tooltip.machine_max.wrench.no_need_to_repair", "%1$s无需修复或固定");
        this.add("tooltip.machine_max.spray_can.interact", "互动以喷涂：");
        this.add("message.machine_max.blueprint_saved", "蓝图已保存至%1$s");
        this.add("message.machine_max.blueprint_error", "保存蓝图失败:%1$s");
        this.add("message.machine_max.blueprint_pass", "未选中任何载具，取消保存蓝图");
        this.add("message.machine_max.blueprint.place_failed", "空间不足，无法部署载具");
        //物品
        //Item
        this.add("itemGroup.machine_max.vehicle_blueprint", "MachineMax: 自定义载具蓝图");
        this.add("itemGroup.machine_max.fabricating_blueprint", "MachineMax: 制造蓝图");
        this.add("itemGroup.machine_max.main", "MachineMax: 部件与工具");
        this.add("block.machine_max.fabricator", "载具制造台");
        this.add("item.machine_max.crowbar", "撬棍");
        this.add("item.machine_max.wrench", "扳手");
        this.add("item.machine_max.spray_can", "喷漆罐");
        this.add("item.machine_max.empty_vehicle_blueprint", "空白载具蓝图");
        this.add("item.machine_max.fabricating_blueprint", "制造蓝图");
    }
}
