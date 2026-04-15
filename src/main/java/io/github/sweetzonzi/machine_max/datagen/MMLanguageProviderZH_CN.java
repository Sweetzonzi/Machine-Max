package io.github.sweetzonzi.machine_max.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderZH_CN extends LanguageProvider {
    public MMLanguageProviderZH_CN(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        // 按键类别
        this.add("key.category.machine_max.general", "Machine Max:通用");
        this.add("key.category.machine_max.ground", "Machine Max:地面载具");
        this.add("key.category.machine_max.ship", "Machine Max:舰艇");
        this.add("key.category.machine_max.plane", "Machine Max:飞行器");
        this.add("key.category.machine_max.mech", "Machine Max:机甲");
        this.add("key.category.machine_max.assembly", "Machine Max:组装");
        // 按键名称-通用
        this.add("key.machine_max.general.free_cam", "自由视角");
        this.add("key.machine_max.general.interact", "交互");
        this.add("key.machine_max.general.leave_vehicle", "离开载具");
        // 按键名称-地面载具
        this.add("key.machine_max.ground.forward", "前进");
        this.add("key.machine_max.ground.backward", "后退");
        this.add("key.machine_max.ground.leftward", "左转");
        this.add("key.machine_max.ground.rightward", "右转");
        this.add("key.machine_max.ground.clutch", "离合");
        this.add("key.machine_max.ground.up_shift", "升档");
        this.add("key.machine_max.ground.down_shift", "降档");
        this.add("key.machine_max.ground.hand_brake", "手刹 (按住)");
        this.add("key.machine_max.ground.toggle_hand_brake", "手刹 (切换)");
        // 按键名称-组装
        this.add("key.machine_max.assembly.add_attach_angle", "旋转部件(+)");
        this.add("key.machine_max.assembly.sub_attach_angle", "旋转部件(-)");
        this.add("key.machine_max.assembly.cycle_connector", "循环部件连接口");
        this.add("key.machine_max.assembly.cycle_variant", "循环部件变体");
        this.add("key.machine_max.assembly.cycle_recipe", "循环切换配方");
        // 内容包异常处理
        this.add("error.machine_max.load", "加载外部包文件: %1$s 时出错，原因: ");
        this.add("error.machine_max.invalid_resource_location", "文件资源路径非法，仅允许小写英文字母、数字、下划线和连字符");
        this.add("error.machine_max.subpart.zero_mass", "零件质量必须大于零");
        this.add("error.machine_max.subpart.empty_hit_boxes", "零件需要至少被指定一个碰撞体积");
        this.add("error.machine_max.subpart.empty_collision_shape", "零件碰撞形状不能为空");
        this.add("error.machine_max.subpart.locator_not_found", "未能在部件的模型中找到定位器%1$s");
        this.add("error.machine_max.part.subsystem_hitbox_not_found", "未能在部件%1$s中为子系统%2$s找到碰撞体积%3$s");
        this.add("error.machine_max.seat_subsystem.no_locator", "座椅子系统必须填写定位器名称(如\"locator\": \"seat_locator\")以指定乘客乘坐位置");
        this.add("error.machine_max.seat_subsystem.no_view", "座椅子系统必须允许乘客使用第一人称视角或第三人称视角之一");
        this.add("error.machine_max.item_storage_subsystem.invalid_row_num", "储物子系统储物空间行数必须大于1之间");
        this.add("error.machine_max.item_storage_subsystem.invalid_column_num", "储物子系统储物空间列数必须大于1之间");
        // 组装异常处理
        this.add("error.machine_max.part.model_not_found", "未找到模型文件: %1$s");
        this.add("error.machine_max.part.connector_locator_not_found", "部件%1$s的模型中未找到的连接点%2$s的定位器%3$s");
        this.add("error.machine_max.part.invalid_connector_type", "部件%1$s的连接点%2$s的类型%3$s非法，必须为\"simple\"或\"advanced\"");
        this.add("error.machine_max.part.invalid_internal_connector_connection", "部件%1$s中的内部接口%2$s与%3$s的类型不匹配，至多只能有一个接口的类型为\"Special\"");
        // 提示信息
        this.add("toast.machine_max.research_complete", "研发完成！");
        this.add("message.machine_max.leaving_vehicle", "长按[%1$s]键%2$s/0.50秒以离开载具");
        this.add("message.machine_max.watch_interact_box_info", "[%1$s]");
        this.add("error.machine_max.use_part_item", "尝试放置%1$s时出现错误：%2$s");
        this.add("tooltip.machine_max.crowbar.safe_disassembly", "互动以安全拆除%1$s");
        this.add("tooltip.machine_max.crowbar.detach_connector", "互动以断开%1$s和%2$s的连接");
        this.add("tooltip.machine_max.crowbar.unsafe_disassembly", "互动以强行拆除%1$s (可能损坏部件)");
        this.add("tooltip.machine_max.wrench.disassembly", "结构完整性：%1$s/%2$s 潜行互动以安全解除%3$s的固定");
        this.add("tooltip.machine_max.wrench.repair", "结构完整性：%2$s/%3$s 部件耐久：%4$s/%5$s 互动以维修%1$s");
        this.add("tooltip.machine_max.wrench.cant_repair", "%1$s已被摧毁，无法修复，潜行互动以解除固定便于拆除");
        this.add("tooltip.machine_max.wrench.no_need_to_repair", "%1$s无需修复或固定");
        this.add("tooltip.machine_max.spray_can.interact", "互动以喷涂：");
        this.add("tooltip.machine_max.jade.subpart_durability", "零件耐久: %1$s");
        this.add("tooltip.machine_max.jade.vehicle_durability", "载具耐久: %1$s");
        this.add("message.machine_max.blueprint_saved", "蓝图已保存至%1$s");
        this.add("message.machine_max.blueprint_error", "保存蓝图失败:%1$s");
        this.add("message.machine_max.blueprint_pass", "未选中任何载具，取消保存蓝图");
        this.add("message.machine_max.blueprint.place_failed", "空间不足，无法部署载具");
        // 物品
        this.add("itemGroup.machine_max.main", "MachineMax: 工具与材料");
        this.add("itemGroup.machine_max.part", "MachineMax: 零部件");
        this.add("itemGroup.machine_max.vehicle_blueprint", "MachineMax: 载具设计图");
        this.add("itemGroup.machine_max.fabricating_blueprint", "MachineMax: 制造蓝图");
        this.add("itemGroup.machine_max.assembly", "MachineMax: 装配体");
        this.add("block.machine_max.fabricator", "制造器(WIP)");
        this.add("block.machine_max.research_table", "研究台");
        this.add("item.machine_max.crowbar", "撬棍");
        this.add("item.machine_max.welding_torch", "焊枪");
        this.add("item.machine_max.spray_can", "喷漆罐");
        this.add("item.machine_max.empty_blueprint", "空白载具蓝图");
        this.add("item.machine_max.fabricating_blueprint", "制造蓝图");
        this.add("item.machine_max.structural_component_1", "初级结构部件");
        this.add("item.machine_max.mechanic_component_1", "初级机械构件");
        this.add("item.machine_max.weapon_component_1", "初级武器零件");
        this.add("item.machine_max.electronic_component_1", "初级电子元件");
        this.add("item.machine_max.power_component_1", "初级能源组件");
        this.add("item.machine_max.energetic_component_1", "初级含能材料");
        // 菜单
        this.add("gui.machine_max.confirm", "确认");
        this.add("gui.machine_max.cancel", "取消");
        this.add("gui.machine_max.enter_vehicle_name", "设置载具名称");
        this.add("gui.machine_max.fabricator.recipes", "配方列表");
        this.add("gui.machine_max.fabricator.tasks", "制造任务");
        this.add("gui.machine_max.fabricator.preview", "预览");
        this.add("gui.machine_max.fabricator.materials", "所需材料");
        this.add("gui.machine_max.fabricator.free_slots", "任务队列空闲剩余: %1$s/%2$s");
        this.add("gui.machine_max.fabricator.insufficient_materials", "材料不足");
        this.add("gui.machine_max.fabricator.no_free_slots", "任务队列已满");
        this.add("gui.machine_max.fabricator.start_production", "开始制造");
        this.add("gui.machine_max.fabricator.cancel_task", "取消任务");
        this.add("gui.machine_max.fabricator.collect_task", "领取产出");
        this.add("gui.machine_max.fabricator.collect_all_task", "领取所有产出");
        this.add("gui.machine_max.fabricator.search_hint", "搜索配方…");
        this.add("gui.machine_max.fabricator.status.queued", "排队中");
        this.add("gui.machine_max.fabricator.status.producing", "制造中");
        this.add("gui.machine_max.fabricator.status.completed", "已完成");
        this.add("gui.machine_max.fabricator.status.idle", "空闲");
        this.add("gui.machine_max.fabricator.ready_for_collection", "已完成");
        this.add("gui.machine_max.fabricator.estimated_time", "预计: %s");
        this.add("gui.machine_max.fabricator.remaining_time", "剩余: %s");
        this.add("gui.machine_max.fabricator.no_item", "无物品");
        this.add("gui.machine_max.fabricator.select_recipe_hint", "选择配方查看详情");
        this.add("gui.machine_max.fabricator.recipe_details", "配方详情");
        this.add("gui.machine_max.fabricator.actual_time", "制造时间: %s");
        this.add("gui.machine_max.fabricator.base_time", "基础时间: %s");
        this.add("gui.machine_max.fabricator.efficiency", "效率: %sx");
        this.add("gui.machine_max.fabricator.output_count", "产出数量: %s");
        this.add("gui.machine_max.fabricator.recipe_description", "配方描述:");
        this.add("gui.machine_max.fabricator.item_description", "物品描述:");
        // 研发菜单
        this.add("gui.machine_max.research.free_rp", "\uD83D\uDD2C自由研发点：");
        this.add("gui.machine_max.research.recipe.on_hold_1", "⏸ 暂停研究");
        this.add("gui.machine_max.research.recipe.on_hold_2", "后续获得的研发点将全部转化为自由研发点");
        this.add("gui.machine_max.research.recipe.start_1", "▶ 开始研究");
        this.add("gui.machine_max.research.recipe.start_2", "获得经验、组装、维修或伤害载具部件以获取研发点");
        this.add("gui.machine_max.research.recipe.continue", "▶ 继续研究");
        this.add("gui.machine_max.research.recipe.cant_apply_free_rp_1", "⚠ 无法加速研究");
        this.add("gui.machine_max.research.recipe.cant_apply_free_rp_2", "研究项目未开始");
        this.add("gui.machine_max.research.recipe.no_apply_free_rp_1", "⚠ 无法加速研究");
        this.add("gui.machine_max.research.recipe.no_apply_free_rp_2", "无可用自由研发点");
        this.add("gui.machine_max.research.recipe.apply_free_rp_complete_1", "⏭ 立即完成研究");
        this.add("gui.machine_max.research.recipe.apply_free_rp_complete_2", "自由研发点消耗: %1$s");
        this.add("gui.machine_max.research.recipe.apply_free_rp_1", "⏭ 加速研究");
        this.add("gui.machine_max.research.recipe.apply_free_rp_2", "预计进度: %1$s/%2$s");
        this.add("gui.machine_max.research.recipe.insufficient_material_1", "⚠ 无法开始研究");
        this.add("gui.machine_max.research.recipe.insufficient_material_2", "材料不足");
        this.add("gui.machine_max.research.recipe.claim_1", "↓ 获取研究成果");
        this.add("gui.machine_max.research.recipe.claim_2", "成果将在关闭菜单后进入物品栏");
        this.add("gui.machine_max.research.recipe.reclaim_1", "↓ 重新获取研究成果");
        this.add("gui.machine_max.research.recipe.reclaim_2", "自由研发点消耗: %1$s");
        this.add("gui.machine_max.research.recipe.insufficient_free_rp_1", "⚠ 无法重新获取研究成果");
        this.add("gui.machine_max.research.recipe.insufficient_free_rp_2", "需要自由研发点: %1$s");
        this.add("gui.machine_max.research.recipe.research_unfinished_1", "⚠ 无法获取成果");
        this.add("gui.machine_max.research.recipe.research_unfinished_2", "研发未完成");
        this.add("gui.machine_max.research.no_preview", "无可预览产物");
        this.add("gui.machine_max.research.no_blueprint_product", "未找到可预览产物");
        this.add("gui.machine_max.research.status.can_research", "可研发");
        this.add("gui.machine_max.research.status.can_claim", "可领取");
        this.add("gui.machine_max.research.status.can_reprint", "可重获");
        this.add("gui.machine_max.research.status.prereq_missing", "前置缺失 x%1$s");
        this.add("gui.machine_max.research.status.need_materials_rp", "材料或研发点不足");
        this.add("gui.machine_max.research.status.unavailable", "不可用");
        this.add("jei.machine_max.blueprint_research.rp_cost", "研发点需求: %1$s");
        // 组装HUD
        this.add("hud.warn.machine_max.subpart_destroying", "❌ 零件已损毁，销毁倒计时: %1$s秒");
        this.add("hud.warn.machine_max.subsystem_malfunction", "❌ %1$s瘫痪");
        this.add("hud.hint.machine_max.subsystem_durability", "⚠ %1$s耐久度未满: %2$s/%3$s");
        this.add("hud.warn.machine_max.connector_integrity_low", "❌ %1$s结构完整性极低，存在脱落风险");
        this.add("hud.hint.machine_max.connector_integrity", "⚠ %1$s结构完整性未满: %2$s/%3$s");
        this.add("hud.info.machine_max.assembling_progress", "装配进度: ");
        this.add("hud.hint.machine_max.cannot_assemble", "未研发且未持有蓝图，无法组装");
        this.add("hud.key.machine_max.assemble", "[%1$s] 组装&修复&加固");
        this.add("hud.key.machine_max.repair_without_assemble", "[%1$s] 修复&加固");
        this.add("hud.key.machine_max.disassemble", "[%1$s+%2$s] 拆解");
        this.add("hud.key.machine_max.cycle_recipe", "[%1$s] 切换配方(%2$s可用)");
        this.add("hud.key.machine_max.tear_down", "[%1$s] 拆除部件");
        this.add("hud.key.machine_max.detach", "[%1$s+%2$s] 断开连接点");
        // 研究点显示HUD
        this.add("hud.hint.machine_max.rp_add_reason.exp", "获得经验");
        this.add("hud.hint.machine_max.rp_add_reason.repair", "维修");
        this.add("hud.hint.machine_max.rp_add_reason.assembly", "组装");
        this.add("hud.hint.machine_max.rp_add_reason.hit", "命中");
        this.add("hud.hint.machine_max.rp_add_reason.part_damage", "伤害部件");
        this.add("hud.hint.machine_max.rp_add_reason.part_destroy", "摧毁部件");
        this.add("hud.hint.machine_max.rp_add_reason.subsystem_damage", "伤害子系统");
        this.add("hud.hint.machine_max.rp_add_reason.subsystem_destroy", "瘫痪子系统");
        this.add("hud.hint.machine_max.rp_add_reason.unknown", "未知");
        // 配置项
        this.add("machine_max.configuration.title", "Machine Max 配置");
        this.add("machine_max.configuration.section.machine.max.client.toml.title", "客户端配置");
        this.add("machine_max.configuration.section.machine.max.client.toml", "客户端配置");
        this.add("machine_max.configuration.section.machine.max.server.toml", "服务器配置");
        this.add("config.jade.plugin_machine_max.mm_part_entity_status", "Machine Max 部件耐久显示");
        // 地面载具配置
        this.add("machine_max.configuration.ground_vehicle", "地面载具");
        this.add("machine_max.configuration.ground_vehicle.button", "地面载具");
        this.add("machine_max.configuration.ground_vehicle.tooltip", "地面载具相关设置");
        this.add("machine_max.configuration.full_power_time", "满动力时间");
        this.add("machine_max.configuration.full_power_time.tooltip", "按住按键达到满动力所需的时间（秒）");
        this.add("machine_max.configuration.full_steering_time", "满转向时间");
        this.add("machine_max.configuration.full_steering_time.tooltip", "按住按键达到满转向所需的时间（秒）");
        this.add("machine_max.configuration.auto_switch_gear", "自动换挡");
        this.add("machine_max.configuration.auto_switch_gear.tooltip", "根据输入、车速和发动机转速自动换挡");
        this.add("machine_max.configuration.auto_handbrake", "自动手刹");
        this.add("machine_max.configuration.auto_handbrake.tooltip", "车辆停止时自动拉手刹，启动时自动释放");
        this.add("machine_max.configuration.drift_assist", "漂移辅助");
        this.add("machine_max.configuration.drift_assist.tooltip", "漂移时自动反打方向以保持控制");
        this.add("machine_max.configuration.pose_preference", "视角跟随");
        this.add("machine_max.configuration.pose_preference.tooltip", "自动旋转摄像机视角以跟随载具方向");
        this.add("machine_max.configuration.speed_turning_limit", "高速转向限制");
        this.add("machine_max.configuration.speed_turning_limit.tooltip", "高速行驶时限制转向时的侧向加速度，防止失控或侧翻");
        // 舰艇配置
        this.add("machine_max.configuration.ship", "舰艇");
        this.add("machine_max.configuration.ship.button", "舰艇");
        this.add("machine_max.configuration.ship.tooltip", "舰艇相关设置");
        // 飞行器配置
        this.add("machine_max.configuration.plane", "飞行器");
        this.add("machine_max.configuration.plane.button", "飞行器");
        this.add("machine_max.configuration.plane.tooltip", "飞行器相关设置");
        // 服务器配置
        this.add("machine_max.configuration.should_destroy_blocks", "允许碰撞破坏方块");
        this.add("machine_max.configuration.should_destroy_blocks.tooltip", "是否允许部件在冲击力足够大时破坏方块");
        this.add("machine_max.configuration.ignore_assembly_tag_requirements", "忽略组装Tag要求");
        this.add("machine_max.configuration.ignore_assembly_tag_requirements.tooltip", "使用零件组装载具时是否忽视连接点的Tag要求");
        this.add("machine_max.configuration.subpart_destroy_ticks_per_durability", "零件销毁每耐久Tick");
        this.add("machine_max.configuration.subpart_destroy_ticks_per_durability.tooltip", "零件损毁后，每1点最大耐久对应的销毁倒计时Tick数");
        this.add("machine_max.configuration.subpart_destroy_min_ticks", "零件销毁最小Tick");
        this.add("machine_max.configuration.subpart_destroy_min_ticks.tooltip", "零件损毁后的最小销毁倒计时Tick数");
        this.add("machine_max.configuration.subpart_destroy_advance_ticks_per_damage", "零件受伤加速销毁Tick");
        this.add("machine_max.configuration.subpart_destroy_advance_ticks_per_damage.tooltip", "零件已损毁时，每1点伤害额外推进的销毁倒计时Tick数");
    }
}
