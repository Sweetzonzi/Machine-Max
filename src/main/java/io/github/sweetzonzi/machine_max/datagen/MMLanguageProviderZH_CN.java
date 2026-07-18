package io.github.sweetzonzi.machine_max.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class MMLanguageProviderZH_CN extends LanguageProvider {
    public MMLanguageProviderZH_CN(PackOutput output, String modid, String locale) {
        super(output, modid, locale);
    }

    @Override
    protected void addTranslations() {
        // 内置标签
        this.add("machine_max.direction.left", "左");
        this.add("machine_max.direction.right", "右");
        this.add("machine_max.direction.front", "前");
        this.add("machine_max.direction.back", "后");
        this.add("machine_max.direction.top", "上");
        this.add("machine_max.direction.bottom", "下");
        this.add("machine_max.category.structural", "结构");
        this.add("machine_max.category.decoration", "装饰");
        this.add("machine_max.category.mobility", "机动");
        this.add("machine_max.category.weapon", "武器");
        this.add("machine_max.category.misc", "杂项");
        this.add("machine_max.type.land", "陆地");
        this.add("machine_max.type.marine", "水上");
        this.add("machine_max.type.aerial", "空中");
        this.add("machine_max.type.mecha", "机甲");

        // 控制组名称
        this.add("controlGroup.machine_max.base", "基础");
        this.add("controlGroup.machine_max.backup_fire_control", "备用火控");
        this.add("controlGroup.machine_max.commander_override", "车长超控");

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
        this.add("key.machine_max.general.toggle_light", "灯光开关");
        this.add("key.machine_max.general.vehicle_info", "载具信息面板");
        this.add("key.machine_max.general.cycle_camera", "切换摄像机");
        this.add("key.machine_max.general.camera_zoom", "一键变焦");
        this.add("key.machine_max.general.camera_zoom_in", "放大");
        this.add("key.machine_max.general.camera_zoom_out", "缩小");
        this.add("key.machine_max.general.main_weapon_fire", "主武器开火");
        this.add("key.machine_max.general.secondary_weapon_fire", "副武器开火");
        this.add("key.machine_max.general.next_ammo_type", "下一个弹种");
        this.add("key.machine_max.general.prev_ammo_type", "上一个弹种");
        this.add("key.machine_max.general.cycle_control_group", "轮换控制组");
        this.add("key.machine_max.general.view_vehicle_internal", "查看内构");
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
        this.add("error.machine_max.part.connector_locator_not_found", "部件 %1$s 的模型中未找到连接点 %2$s 的定位器 %3$s");
        this.add("error.machine_max.part.invalid_connector_type", "部件 %1$s 的连接点 %2$s 的类型 %3$s 非法，必须为\"simple\"或\"advanced\"");
        this.add("error.machine_max.part.invalid_internal_connector_connection", "部件 %1$s 中的内部接口 %2$s 与 %3$s 的类型不匹配，至多只能有一个接口的类型为\"advanced\"");
        // 提示信息
        this.add("toast.machine_max.research_complete", "研发完成！");
        this.add("message.machine_max.control_group_switched", "已切换控制组：%s");
        this.add("message.machine_max.leaving_vehicle", "长按[%1$s]键%2$s/0.50秒以离开载具");
        this.add("message.machine_max.watch_interact_box_info", "[%1$s]");
        this.add("error.machine_max.use_part_item", "尝试放置 %1$s 时出现错误：%2$s");
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
        this.add("message.machine_max.blueprint_error", "保存蓝图失败: %1$s");
        this.add("message.machine_max.blueprint_pass", "未选中任何载具，取消保存蓝图");
        this.add("message.machine_max.blueprint.place_failed", "空间不足，无法部署载具");
        this.add("message.machine_max.vehicle.place_failed", "部署载具失败: %1$s");
        this.add("message.machine_max.part.place_failed", "放置部件失败: %1$s");
        // 创造模式物品栏
        this.add("itemGroup.machine_max.main", "MachineMax: 工具与材料");
        this.add("itemGroup.machine_max.part", "MachineMax: 零部件");
        this.add("itemGroup.machine_max.vehicle_blueprint", "MachineMax: 载具设计图");
        this.add("itemGroup.machine_max.fabricating_blueprint", "MachineMax: 制造蓝图");
        this.add("itemGroup.machine_max.assembly", "MachineMax: 装配体");
        // 物品
        this.add("block.machine_max.fabricator", "制造器(WIP)");
        this.add("block.machine_max.research_table", "研究台");
        this.add("item.machine_max.crowbar", "撬棍");
        this.add("item.machine_max.welding_torch", "焊枪");
        this.add("item.machine_max.spray_can", "喷漆罐");
        this.add("item.machine_max.ender_gk_resin", "末影GK树脂");
        this.add("item.machine_max.empty_blueprint", "空白载具蓝图");
        this.add("item.machine_max.fabricating_blueprint", "制造蓝图");
        this.add("item.machine_max.structural_component_1", "初级结构部件");
        this.add("item.machine_max.mechanic_component_1", "初级机械构件");
        this.add("item.machine_max.weapon_component_1", "初级武器零件");
        this.add("item.machine_max.electronic_component_1", "初级电子元件");
        this.add("item.machine_max.power_component_1", "初级能源组件");
        this.add("item.machine_max.energetic_component_1", "初级含能材料");
        // 物品音效
        this.add("subtitles.item.welding_torch.start", "焊枪启动");
        this.add("subtitles.item.welding_torch.loop", "焊接中");
        this.add("subtitles.item.welding_torch.end", "焊枪关闭");
        // 菜单
        this.add("gui.machine_max.welcome.title", "欢迎参与 Machine Max Beta 公开测试!");
        this.add("gui.machine_max.welcome.content", "这是一款以物理和载具组装为主题的 Minecraft模组。\n本模组仍处于早期版本，可能存在各类问题，不代表最终品质。\n使用旧有存档时请务必做好备份工作。\n\n祝游玩愉快！");
        this.add("gui.machine_max.welcome.proceed", "确认");
        this.add("gui.machine_max.welcome.dont_show_again", "不再显示此页面");
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
        this.add("gui.machine_max.research.tab.research_materials", "研发材料");
        this.add("gui.machine_max.research.tab.fabricating_materials", "制造材料");
        this.add("gui.machine_max.research.no_materials", "无材料需求");
        this.add("gui.machine_max.research.fabrication_time", "制造时间: %s秒");
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
        this.add("machine_max.configuration.section.machine.max.common.toml", "通用配置");
        this.add("machine_max.configuration.section.machine.max.server.toml", "服务器配置");
        this.add("config.jade.plugin_machine_max.mm_part_entity_status", "Machine Max 部件耐久显示");
        // 地面载具配置
        this.add("machine_max.configuration.ground_vehicle", "地面载具");
        this.add("machine_max.configuration.ground_vehicle.button", "地面载具");
        this.add("machine_max.configuration.ground_vehicle.tooltip", "地面载具控制的平滑度与行为设置");
        this.add("machine_max.configuration.full_power_time", "油门建立时间");
        this.add("machine_max.configuration.full_power_time.tooltip", "按下按键后达到满油门所需时间（秒）。越小响应越快。默认值：1.5（地面载具）、2.5（飞行器）。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.power_off_time", "收油归零时间");
        this.add("machine_max.configuration.power_off_time.tooltip", "松开按键后油门回零所需时间（秒）。越小收油越快。默认值：2.0。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_brake_time", "刹车建立时间");
        this.add("machine_max.configuration.full_brake_time.tooltip", "按下按键后达到满刹车所需时间（秒）。越小刹车响应越快。默认值：0.3。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.brake_off_time", "刹车释放时间");
        this.add("machine_max.configuration.brake_off_time.tooltip", "松开按键后刹车归零所需时间（秒）。越小车轮恢复滚动越快。默认值：0.1。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_steering_time", "转向建立时间");
        this.add("machine_max.configuration.full_steering_time.tooltip", "按下按键后达到满转向角所需时间（秒）。越小转向越快。默认值：0.4（地面载具）、0.25（舰艇）。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.steering_off_time", "转向回正时间");
        this.add("machine_max.configuration.steering_off_time.tooltip", "松开按键后转向回正到中央所需时间（秒）。越小车轮回正越快。默认值：0.2。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.auto_switch_gear", "自动换挡");
        this.add("machine_max.configuration.auto_switch_gear.tooltip", "FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=始终自动。ALWAYS_DISABLED=仅手动。默认值：FOLLOW_VEHICLE");
        this.add("machine_max.configuration.auto_handbrake", "自动手刹");
        this.add("machine_max.configuration.auto_handbrake.tooltip", "FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=始终自动。ALWAYS_DISABLED=仅手动。默认值：FOLLOW_VEHICLE");
        this.add("machine_max.configuration.drift_assist", "漂移辅助");
        this.add("machine_max.configuration.drift_assist.tooltip", "漂移时自动反打方向。FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=始终开启。ALWAYS_DISABLED=关闭。默认值：FOLLOW_VEHICLE");
        this.add("machine_max.configuration.pose_preference", "视角跟随");
        this.add("machine_max.configuration.pose_preference.tooltip", "自动旋转摄像机跟随载具朝向。FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=跟随。ALWAYS_DISABLED=自由视角。默认值：FOLLOW_VEHICLE");
        this.add("machine_max.configuration.speed_turning_limit", "高速转向限制");
        this.add("machine_max.configuration.speed_turning_limit.tooltip", "高速时限制转向角度防止侧翻。默认值：true");
        this.add("machine_max.configuration.separate_throttle_brake", "分离油门刹车通道");
        this.add("machine_max.configuration.separate_throttle_brake.tooltip", "将油门和刹车分离为独立通道。false=意图模式（W前进/S后退，由载具自动判断油门刹车）。true=分离模式（W油门/S刹车）。默认值：false");
        this.add("machine_max.configuration.render_hit_whitening", "部件受击闪白");
        this.add("machine_max.configuration.render_hit_whitening.tooltip", "部件被击中时显示白色闪烁效果。默认值：true");
        this.add("machine_max.configuration.render_destroy_blackening", "部件损毁变暗");
        this.add("machine_max.configuration.render_destroy_blackening.tooltip", "部件损毁后显示暗色覆盖。默认值：true");
        this.add("machine_max.configuration.render_force_translucent_parts", "强制半透明部件");
        this.add("machine_max.configuration.render_force_translucent_parts.tooltip", "将使用Cutout渲染的部件改为半透明实体渲染。默认关闭是因为半透明渲染容易出现排序错误，导致其后的对象被错误剔除。默认值：false");
        this.add("machine_max.configuration.show_welcome_screen", "显示欢迎页面");
        this.add("machine_max.configuration.show_welcome_screen.tooltip", "启动后首次进入标题画面时弹出欢迎页面。默认值：true");
        // 舰艇配置
        this.add("machine_max.configuration.ship", "舰艇");
        this.add("machine_max.configuration.ship.button", "舰艇");
        this.add("machine_max.configuration.ship.tooltip", "舰艇转向平滑度设置");
        // 飞行器配置
        this.add("machine_max.configuration.plane", "飞行器");
        this.add("machine_max.configuration.plane.button", "飞行器");
        this.add("machine_max.configuration.plane.tooltip", "飞行器操纵平滑度设置");
        this.add("machine_max.configuration.full_pitch_time", "俯仰建立时间");
        this.add("machine_max.configuration.full_pitch_time.tooltip", "按下按键后达到满俯仰角所需时间（秒）。越小越快。默认值：0.25。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_yaw_time", "偏航建立时间");
        this.add("machine_max.configuration.full_yaw_time.tooltip", "按下按键后达到满偏航角所需时间（秒）。越小越快。默认值：0.25。范围：0.05 ~ 99999.0");
        this.add("machine_max.configuration.full_roll_time", "滚转建立时间");
        this.add("machine_max.configuration.full_roll_time.tooltip", "按下按键后达到满滚转角所需时间（秒）。越小越快。默认值：0.25。范围：0.05 ~ 99999.0");
        // 服务器配置
        this.add("machine_max.configuration.should_destroy_blocks", "允许碰撞破坏方块");
        this.add("machine_max.configuration.should_destroy_blocks.tooltip", "是否允许部件在冲击力足够大时破坏方块。默认值：true");
        this.add("machine_max.configuration.projectile_destroy_blocks", "允许投射物破坏方块");
        this.add("machine_max.configuration.projectile_destroy_blocks.tooltip", "是否允许投射物在击穿方块时破坏方块。默认值：true");
        this.add("machine_max.configuration.ignore_assembly_tag_requirements", "忽略组装Tag要求");
        this.add("machine_max.configuration.ignore_assembly_tag_requirements.tooltip", "使用零件组装载具时是否忽视连接点的Tag要求。默认值：false");
        this.add("machine_max.configuration.auto_override_official_pack", "自动覆盖官方内容包");
        this.add("machine_max.configuration.auto_override_official_pack.tooltip", "是否在启动和重载时自动打包并覆盖 machine_max:official 官方内容包。默认值：true");
        this.add("machine_max.configuration.subpart_destroy_ticks_per_durability", "零件销毁每耐久Tick");
        this.add("machine_max.configuration.subpart_destroy_ticks_per_durability.tooltip", "零件损毁后，每1点最大耐久对应的销毁倒计时Tick数。默认值：10。范围：0 ~ 100000");
        this.add("machine_max.configuration.subpart_destroy_min_ticks", "零件销毁最小Tick");
        this.add("machine_max.configuration.subpart_destroy_min_ticks.tooltip", "零件损毁后的最小销毁倒计时Tick数。默认值：200。范围：0 ~ 1000000");
        this.add("machine_max.configuration.subpart_destroy_advance_ticks_per_damage", "零件受伤加速销毁Tick");
        this.add("machine_max.configuration.subpart_destroy_advance_ticks_per_damage.tooltip", "零件已损毁时，每1点伤害额外推进的销毁倒计时Tick数。默认值：20。范围：0 ~ 100000");
    }
}
