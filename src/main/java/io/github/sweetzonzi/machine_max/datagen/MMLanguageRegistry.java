package io.github.sweetzonzi.machine_max.datagen;

import com.google.common.collect.ImmutableMap;
import io.github.sweetzonzi.machine_max.MachineMax;
import lombok.RequiredArgsConstructor;
import net.minecraft.data.PackOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry.Locale.*;

@RequiredArgsConstructor
public enum MMLanguageRegistry {
    // 内置标签
    DIRECTION_LEFT("machine_max.direction.left", ImmutableMap.of(
            zh_cn, "左",
            en_us, "Left"
    )),
    DIRECTION_RIGHT("machine_max.direction.right", ImmutableMap.of(
            zh_cn, "右",
            en_us, "Right"
    )),
    DIRECTION_FRONT("machine_max.direction.front", ImmutableMap.of(
            zh_cn, "前",
            en_us, "Front"
    )),
    DIRECTION_BACK("machine_max.direction.back", ImmutableMap.of(
            zh_cn, "后",
            en_us, "Back"
    )),
    DIRECTION_TOP("machine_max.direction.top", ImmutableMap.of(
            zh_cn, "上",
            en_us, "Top"
    )),
    DIRECTION_BOTTOM("machine_max.direction.bottom", ImmutableMap.of(
            zh_cn, "下",
            en_us, "Bottom"
    )),
    CATEGORY_STRUCTURAL("machine_max.category.structural", ImmutableMap.of(
            zh_cn, "结构",
            en_us, "Structural"
    )),
    CATEGORY_DECORATION("machine_max.category.decoration", ImmutableMap.of(
            zh_cn, "装饰",
            en_us, "Decoration"
    )),
    CATEGORY_MOBILITY("machine_max.category.mobility", ImmutableMap.of(
            zh_cn, "机动",
            en_us, "Mobility"
    )),
    CATEGORY_WEAPON("machine_max.category.weapon", ImmutableMap.of(
            zh_cn, "武器",
            en_us, "Weapon"
    )),
    CATEGORY_MISC("machine_max.category.misc", ImmutableMap.of(
            zh_cn, "杂项",
            en_us, "Misc"
    )),
    TYPE_LAND("machine_max.type.land", ImmutableMap.of(
            zh_cn, "陆地",
            en_us, "Land"
    )),
    TYPE_MARINE("machine_max.type.marine", ImmutableMap.of(
            zh_cn, "水上",
            en_us, "Marine"
    )),
    TYPE_AERIAL("machine_max.type.aerial", ImmutableMap.of(
            zh_cn, "空中",
            en_us, "Aerial"
    )),
    TYPE_MECHA("machine_max.type.mecha", ImmutableMap.of(
            zh_cn, "机甲",
            en_us, "Mecha"
    )),
    ATTRIBUTE_GRAB_STRENGTH("attribute.machine_max.grab_strength", ImmutableMap.of(
            zh_cn, "抓取力量",
            en_us, "Grab Strength"
    )),
    // 控制组名称
    CONTROL_GROUP_BASE("controlGroup.machine_max.base", ImmutableMap.of(
            zh_cn, "基础",
            en_us, "Base"
    )),
    CONTROL_GROUP_BACKUP_FIRE_CONTROL("controlGroup.machine_max.backup_fire_control", ImmutableMap.of(
            zh_cn, "备用火控",
            en_us, "Backup Fire Control"
    )),
    CONTROL_GROUP_COMMANDER_OVERRIDE("controlGroup.machine_max.commander_override", ImmutableMap.of(
            zh_cn, "车长超控",
            en_us, "Commander Override"
    )),
    // 按键类别
    KEY_CATEGORY_GENERAL("key.category.machine_max.general", ImmutableMap.of(
            zh_cn, "Machine Max:通用",
            en_us, "Machine Max:General"
    )),
    KEY_CATEGORY_GROUND("key.category.machine_max.ground", ImmutableMap.of(
            zh_cn, "Machine Max:地面载具",
            en_us, "Machine Max:Ground"
    )),
    KEY_CATEGORY_SHIP("key.category.machine_max.ship", ImmutableMap.of(
            zh_cn, "Machine Max:舰艇",
            en_us, "Machine Max:Ship"
    )),
    KEY_CATEGORY_PLANE("key.category.machine_max.plane", ImmutableMap.of(
            zh_cn, "Machine Max:飞行器",
            en_us, "Machine Max:Plane"
    )),
    KEY_CATEGORY_MECH("key.category.machine_max.mech", ImmutableMap.of(
            zh_cn, "Machine Max:机甲",
            en_us, "Machine Max:Mech"
    )),
    KEY_CATEGORY_ASSEMBLY("key.category.machine_max.assembly", ImmutableMap.of(
            zh_cn, "Machine Max:组装",
            en_us, "Machine Max:Assembly"
    )),
    // 按键名称-通用
    KEY_GENERAL_FREE_CAM("key.machine_max.general.free_cam", ImmutableMap.of(
            zh_cn, "自由视角",
            en_us, "Free Camera"
    )),
    KEY_GENERAL_INTERACT("key.machine_max.general.interact", ImmutableMap.of(
            zh_cn, "交互",
            en_us, "Interact with Vehicle"
    )),
    KEY_GENERAL_LEAVE_VEHICLE("key.machine_max.general.leave_vehicle", ImmutableMap.of(
            zh_cn, "离开载具",
            en_us, "Leave Vehicle"
    )),
    KEY_GENERAL_TOGGLE_LIGHT("key.machine_max.general.toggle_light", ImmutableMap.of(
            zh_cn, "灯光开关",
            en_us, "Toggle Light"
    )),
    KEY_GENERAL_VEHICLE_INFO("key.machine_max.general.vehicle_info", ImmutableMap.of(
            zh_cn, "载具信息面板",
            en_us, "Vehicle Info Panel"
    )),
    KEY_GENERAL_CYCLE_CAMERA("key.machine_max.general.cycle_camera", ImmutableMap.of(
            zh_cn, "切换摄像机",
            en_us, "Cycle Camera"
    )),
    KEY_GENERAL_CAMERA_ZOOM("key.machine_max.general.camera_zoom", ImmutableMap.of(
            zh_cn, "一键变焦",
            en_us, "Toggle Zoom"
    )),
    KEY_GENERAL_CAMERA_ZOOM_IN("key.machine_max.general.camera_zoom_in", ImmutableMap.of(
            zh_cn, "放大",
            en_us, "Zoom In"
    )),
    KEY_GENERAL_CAMERA_ZOOM_OUT("key.machine_max.general.camera_zoom_out", ImmutableMap.of(
            zh_cn, "缩小",
            en_us, "Zoom Out"
    )),
    KEY_GENERAL_MAIN_WEAPON_FIRE("key.machine_max.general.main_weapon_fire", ImmutableMap.of(
            zh_cn, "主武器开火",
            en_us, "Main Weapon Fire"
    )),
    KEY_GENERAL_SECONDARY_WEAPON_FIRE("key.machine_max.general.secondary_weapon_fire", ImmutableMap.of(
            zh_cn, "副武器开火",
            en_us, "Secondary Weapon Fire"
    )),
    KEY_GENERAL_NEXT_AMMO_TYPE("key.machine_max.general.next_ammo_type", ImmutableMap.of(
            zh_cn, "下一个弹种",
            en_us, "Next Ammo Type"
    )),
    KEY_GENERAL_PREV_AMMO_TYPE("key.machine_max.general.prev_ammo_type", ImmutableMap.of(
            zh_cn, "上一个弹种",
            en_us, "Previous Ammo Type"
    )),
    KEY_GENERAL_CYCLE_CONTROL_GROUP("key.machine_max.general.cycle_control_group", ImmutableMap.of(
            zh_cn, "轮换控制组",
            en_us, "Cycle Control Group"
    )),
    KEY_GENERAL_VIEW_VEHICLE_INTERNAL("key.machine_max.general.view_vehicle_internal", ImmutableMap.of(
            zh_cn, "查看内构",
            en_us, "Inspect Internal"
    )),
    KEY_GENERAL_CYCLE_SEAT("key.machine_max.general.cycle_seat", ImmutableMap.of(
            zh_cn, "循环换座",
            en_us, "Cycle Seat"
    )),
    // 按键名称-地面载具
    KEY_GROUND_FORWARD("key.machine_max.ground.forward", ImmutableMap.of(
            zh_cn, "前进",
            en_us, "Forward"
    )),
    KEY_GROUND_BACKWARD("key.machine_max.ground.backward", ImmutableMap.of(
            zh_cn, "后退",
            en_us, "Backward"
    )),
    KEY_GROUND_LEFTWARD("key.machine_max.ground.leftward", ImmutableMap.of(
            zh_cn, "左转",
            en_us, "Leftward"
    )),
    KEY_GROUND_RIGHTWARD("key.machine_max.ground.rightward", ImmutableMap.of(
            zh_cn, "右转",
            en_us, "Rightward"
    )),
    KEY_GROUND_CLUTCH("key.machine_max.ground.clutch", ImmutableMap.of(
            zh_cn, "离合",
            en_us, "Clutch"
    )),
    KEY_GROUND_UP_SHIFT("key.machine_max.ground.up_shift", ImmutableMap.of(
            zh_cn, "升档",
            en_us, "Shift Up"
    )),
    KEY_GROUND_DOWN_SHIFT("key.machine_max.ground.down_shift", ImmutableMap.of(
            zh_cn, "降档",
            en_us, "Shift Down"
    )),
    KEY_GROUND_HAND_BRAKE("key.machine_max.ground.hand_brake", ImmutableMap.of(
            zh_cn, "手刹 (按住)",
            en_us, "Hand Brake (Press)"
    )),
    KEY_GROUND_TOGGLE_HAND_BRAKE("key.machine_max.ground.toggle_hand_brake", ImmutableMap.of(
            zh_cn, "手刹 (切换)",
            en_us, "Hand Brake (Toggle)"
    )),
    // 按键名称-组装
    KEY_ASSEMBLY_ADD_ATTACH_ANGLE("key.machine_max.assembly.add_attach_angle", ImmutableMap.of(
            zh_cn, "旋转部件(+)",
            en_us, "Rotate Attach Angle (+)"
    )),
    KEY_ASSEMBLY_SUB_ATTACH_ANGLE("key.machine_max.assembly.sub_attach_angle", ImmutableMap.of(
            zh_cn, "旋转部件(-)",
            en_us, "Rotate Attach Angle (-)"
    )),
    KEY_ASSEMBLY_CYCLE_CONNECTOR("key.machine_max.assembly.cycle_connector", ImmutableMap.of(
            zh_cn, "循环部件连接口",
            en_us, "Cycle Part Connector"
    )),
    KEY_ASSEMBLY_CYCLE_VARIANT("key.machine_max.assembly.cycle_variant", ImmutableMap.of(
            zh_cn, "循环部件变体",
            en_us, "Cycle Part Variant"
    )),
    // 内容包异常处理
    ERROR_LOAD("error.machine_max.load", ImmutableMap.of(
            zh_cn, "加载外部包文件: %1$s 时出错，原因: ",
            en_us, "An error occurred when loading external pack file at: %1$s, Reason: "
    )),
    ERROR_RECIPE_WRONG_DIRECTORY("error.machine_max.recipe.wrong_directory", ImmutableMap.of(
            zh_cn, "零件配方必须放在 recipe/part_fabricating/ 目录下",
            en_us, "Part recipes must be placed under recipe/part_fabricating/"
    )),
    ERROR_RECIPE_WRONG_TYPE_DIRECTORY("error.machine_max.recipe.wrong_type_directory", ImmutableMap.of(
            zh_cn, "recipe/part_fabricating/ 目录下的配方类型必须为 machine_max:part_fabricating",
            en_us, "Recipes under recipe/part_fabricating/ must use type machine_max:part_fabricating"
    )),
    ERROR_RECIPE_UNKNOWN_PART("error.machine_max.recipe.unknown_part", ImmutableMap.of(
            zh_cn, "配方文件名未对应任何已注册零件",
            en_us, "The recipe file name does not match any registered part"
    )),
    ERROR_RECIPE_DUPLICATE_PART("error.machine_max.recipe.duplicate_part", ImmutableMap.of(
            zh_cn, "同一零件存在第二条零件配方",
            en_us, "A second part recipe exists for the same part"
    )),
    ERROR_RECIPE_INVALID_RESULT_COUNT("error.machine_max.recipe.invalid_result_count", ImmutableMap.of(
            zh_cn, "产物数量必须大于 0 且不超过该零件的堆叠上限",
            en_us, "Result count must be greater than 0 and not exceed the part's stack limit"
    )),
    ERROR_RESEARCH_SELF_PREREQUISITE("error.machine_max.research.self_prerequisite", ImmutableMap.of(
            zh_cn, "项目把自己列为前置，该条目永远无法研发",
            en_us, "The project lists itself as a prerequisite, so it can never be researched"
    )),
    ERROR_RESEARCH_PREREQUISITE_CYCLE("error.machine_max.research.prerequisite_cycle", ImmutableMap.of(
            zh_cn, "前置依赖成环，环上项目永远无法研发: %1$s",
            en_us, "Prerequisite dependency cycle, the projects on the cycle can never be researched: %1$s"
    )),
    ERROR_RESEARCH_UNREACHABLE_PREREQUISITE("error.machine_max.research.unreachable_prerequisite", ImmutableMap.of(
            zh_cn, "依赖了不可达的研发项目，该条目永远无法研发: %1$s",
            en_us, "Depends on an unreachable research project, so it can never be researched: %1$s"
    )),
    ERROR_RESEARCH_MISSING_PREREQUISITE("error.machine_max.research.missing_prerequisite", ImmutableMap.of(
            zh_cn, "前置引用了未注册的研发 id，该前置永远不会完成: %1$s",
            en_us, "A prerequisite references an unregistered research id, that prerequisite never completes: %1$s"
    )),
    ERROR_INVALID_RESOURCE_LOCATION("error.machine_max.invalid_resource_location", ImmutableMap.of(
            zh_cn, "文件资源路径非法，仅允许小写英文字母、数字、下划线和连字符",
            en_us, "Invalid resource location. Only lowercase letters, numbers, hyphens and underscores are allowed."
    )),
    ERROR_DOCUMENT_INVALID_PATH("error.machine_max.document.invalid_path", ImmutableMap.of(
            zh_cn, "非法的 AUI 文档路径",
            en_us, "Invalid AUI document path"
    )),
    ERROR_HUD_LAYOUT_INVALID_PATH("error.machine_max.hud_layout.invalid_path", ImmutableMap.of(
            zh_cn, "非法的 HUD 布局路径",
            en_us, "Invalid HUD layout path"
    )),
    ERROR_HUD_STYLE_INVALID_PATH("error.machine_max.hud_style.invalid_path", ImmutableMap.of(
            zh_cn, "非法的 HUD 样式路径",
            en_us, "Invalid HUD style path"
    )),
    ERROR_SUBPART_ZERO_MASS("error.machine_max.subpart.zero_mass", ImmutableMap.of(
            zh_cn, "零件质量必须大于零",
            en_us, "Sub-part mass must be greater than zero"
    )),
    ERROR_SUBPART_EMPTY_HIT_BOXES("error.machine_max.subpart.empty_hit_boxes", ImmutableMap.of(
            zh_cn, "零件需要至少被指定一个碰撞体积",
            en_us, "Sub-part must have at least one hit-box"
    )),
    ERROR_SUBPART_EMPTY_COLLISION_SHAPE("error.machine_max.subpart.empty_collision_shape", ImmutableMap.of(
            zh_cn, "零件碰撞形状不能为空",
            en_us, "Sub-part shape cannot be empty"
    )),
    ERROR_SUBPART_LOCATOR_NOT_FOUND("error.machine_max.subpart.locator_not_found", ImmutableMap.of(
            zh_cn, "未能在部件的模型中找到定位器%1$s",
            en_us, "Locator %1$s not found in part model"
    )),
    ERROR_PART_SUBSYSTEM_HITBOX_NOT_FOUND("error.machine_max.part.subsystem_hitbox_not_found", ImmutableMap.of(
            zh_cn, "未能在部件%1$s中为子系统%2$s找到碰撞体积%3$s",
            en_us, "Failed to find hit-box %3$s for subsystem %2$s in part %1$s"
    )),
    ERROR_SEAT_SUBSYSTEM_NO_LOCATOR("error.machine_max.seat_subsystem.no_locator", ImmutableMap.of(
            zh_cn, "座椅子系统必须填写定位器名称(如\"locator\": \"seat_locator\")以指定乘客乘坐位置",
            en_us, "Seat subsystem requires a locator (e.g. \"locator\": \"seat_locator\") to define sitting position"
    )),
    ERROR_SEAT_SUBSYSTEM_NO_VIEW("error.machine_max.seat_subsystem.no_view", ImmutableMap.of(
            zh_cn, "座椅子系统必须允许乘客使用第一人称视角或第三人称视角之一",
            en_us, "Seat subsystem must either allow first person view or third person view"
    )),
    ERROR_SEAT_SUBSYSTEM_EMPTY_DISMOUNT_LOCATOR("error.machine_max.seat_subsystem.empty_dismount_locator", ImmutableMap.of(
            zh_cn, "座椅子系统的下车定位器列表(dismount_locators)中不允许出现空名称，请填写定位器名或删除该项",
            en_us, "The dismount_locators list of a seat subsystem must not contain empty names; fill in a locator name or remove the entry"
    )),
    ERROR_ITEM_STORAGE_SUBSYSTEM_INVALID_ROW_NUM("error.machine_max.item_storage_subsystem.invalid_row_num", ImmutableMap.of(
            zh_cn, "储物子系统储物空间行数必须大于1之间",
            en_us, "The row number of item-storage subsystem must be greater than 1"
    )),
    ERROR_ITEM_STORAGE_SUBSYSTEM_INVALID_COLUMN_NUM("error.machine_max.item_storage_subsystem.invalid_column_num", ImmutableMap.of(
            zh_cn, "储物子系统储物空间列数必须大于1之间",
            en_us, "The column number of item-storage subsystem must be greater than 1"
    )),
    // 组装异常处理
    ERROR_PART_MODEL_NOT_FOUND("error.machine_max.part.model_not_found", ImmutableMap.of(
            zh_cn, "未找到模型文件: %1$s",
            en_us, "Model file not found at: %1$s"
    )),
    ERROR_PART_MISSING_TEXTURES("error.machine_max.part.missing_textures", ImmutableMap.of(
            zh_cn, "零件贴图列表不能为空",
            en_us, "Part textures must not be empty"
    )),
    ERROR_PART_CONNECTOR_LOCATOR_NOT_FOUND("error.machine_max.part.connector_locator_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 中未找到连接点 %2$s 的定位器 %3$s",
            en_us, "Failed to find locator %3$s of connector %2$s in model %1$s"
    )),
    ERROR_PART_INVALID_CONNECTOR_TYPE("error.machine_max.part.invalid_connector_type", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，连接点 %3$s 的类型 %4$s 非法，只能是 simple 或 advanced",
            en_us, "Connector %3$s in sub-part %2$s of model %1$s declares type %4$s; expected simple or advanced"
    )),
    ERROR_PART_INVALID_INTERNAL_CONNECTOR_CONNECTION("error.machine_max.part.invalid_internal_connector_connection", ImmutableMap.of(
            zh_cn, "部件 %1$s 中的内部接口 %2$s 与 %3$s 的类型不匹配，至多只能有一个接口的类型为\"advanced\"",
            en_us, "Illegal internal locator connection between %2$s and %3$s in part %1$s. Only at most one \"Special\" locator is allowed"
    )),
    ERROR_PART_DUPLICATE_NAME("error.machine_max.part.duplicate_name", ImmutableMap.of(
            zh_cn, "部件变体 %4$s 内名称 %1$s 重复：%2$s 与 %3$s 冲突",
            en_us, "Duplicate name %1$s in part variant %4$s: %2$s conflicts with %3$s"
    )),
    ERROR_PART_RESERVED_NAME("error.machine_max.part.reserved_name", ImmutableMap.of(
            zh_cn, "部件变体 %4$s 内名称 %1$s（%3$s 的 %2$s）为信号保留地址，不得用作连接点/子系统/交互区名",
            en_us, "Name %1$s (%2$s of %3$s) in part variant %4$s is a reserved signal address and cannot be used for connectors/subsystems/interact boxes"
    )),
    // 装载期跨引用检查
    ERROR_PART_EMPTY_SUB_PARTS("error.machine_max.part.empty_sub_parts", ImmutableMap.of(
            zh_cn, "变体 %1$s 的模型 %2$s 没有声明任何子部件",
            en_us, "Variant %1$s declares no sub-part in model %2$s"
    )),
    ERROR_PART_SUBSYSTEM_LOCATOR_NOT_FOUND("error.machine_max.part.subsystem_locator_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，子系统 %3$s 引用的定位器 %4$s 不存在",
            en_us, "Locator %4$s referenced by subsystem %3$s was not found in sub-part %2$s of model %1$s"
    )),
    ERROR_PART_HITBOX_BONE_NOT_FOUND("error.machine_max.part.hitbox_bone_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 中找不到碰撞箱 %2$s 对应的骨骼，或该骨骼不属于本子部件区间",
            en_us, "No bone for hit-box %2$s in model %1$s, or the bone is outside this sub-part's interval"
    )),
    ERROR_PART_HITBOX_UNSUPPORTED_SHAPE("error.machine_max.part.hitbox_unsupported_shape", ImmutableMap.of(
            zh_cn, "碰撞箱形状 %1$s 不受支持，可选 box/sphere/cylinder/capsule/wheel",
            en_us, "Unsupported hit-box shape %1$s; expected box/sphere/cylinder/capsule/wheel"
    )),
    ERROR_PART_HITBOX_SUBSYSTEM_NOT_FOUND("error.machine_max.part.hitbox_subsystem_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，碰撞箱 %3$s 引用的子系统 %4$s 不存在",
            en_us, "Hit-box %3$s in sub-part %2$s of model %1$s references missing subsystem %4$s"
    )),
    ERROR_PART_INTERACTBOX_BONE_NOT_FOUND("error.machine_max.part.interactbox_bone_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，交互区 %3$s 的骨骼 %4$s 不在本子部件区间内",
            en_us, "Bone %4$s of interact box %3$s is outside the interval of sub-part %2$s in model %1$s"
    )),
    ERROR_PART_INTERACTBOX_UNSUPPORTED_MODE("error.machine_max.part.interactbox_unsupported_mode", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，交互区 %3$s 的交互模式 %4$s 不受支持，可选 fast/accurate",
            en_us, "Unsupported interact mode %4$s for interact box %3$s in sub-part %2$s of model %1$s; expected fast/accurate"
    )),
    ERROR_PART_SUBSYSTEM_CONNECTOR_NOT_FOUND("error.machine_max.part.subsystem_connector_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，子系统 %3$s 受控的连接点 %4$s 不存在",
            en_us, "Connector %4$s controlled by subsystem %3$s was not found in sub-part %2$s of model %1$s"
    )),
    ERROR_PART_SUBSYSTEM_CONNECTOR_NOT_ADVANCED("error.machine_max.part.subsystem_connector_not_advanced", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，子系统 %3$s 受控的连接点 %4$s 不是高级连接点",
            en_us, "Connector %4$s controlled by subsystem %3$s in sub-part %2$s of model %1$s is not an advanced connector"
    )),
    ERROR_PART_CONNECTOR_INVALID_JOINT_AXIS("error.machine_max.part.connector_invalid_joint_axis", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，连接点 %3$s 的 joint_attrs 轴名 %4$s 非法，只能是 X、Y、Z、XR、YR、ZR",
            en_us, "joint_attrs axis %4$s of connector %3$s in sub-part %2$s of model %1$s is invalid; expected X, Y, Z, XR, YR or ZR"
    )),
    ERROR_PART_CONNECTOR_POWER_TARGET_NOT_FOUND("error.machine_max.part.connector_power_target_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，连接点 %3$s 的 power_target %4$s 既不是本子部件的子系统也不是连接点",
            en_us, "power_target %4$s of connector %3$s in sub-part %2$s of model %1$s is neither a subsystem nor a connector of that sub-part"
    )),
    ERROR_PART_CONNECTOR_SIGNAL_TARGET_NOT_FOUND("error.machine_max.part.connector_signal_target_not_found", ImmutableMap.of(
            zh_cn, "模型 %1$s 的子部件 %2$s 中，连接点 %3$s 的 signal_targets 把频道 %4$s 指向了不存在于本子部件的 %5$s",
            en_us, "signal_targets of connector %3$s in sub-part %2$s of model %1$s routes channel %4$s to %5$s, which is not a subsystem, interact box or connector of that sub-part"
    )),
    // 提示信息
    TOAST_RESEARCH_COMPLETE("toast.machine_max.research_complete", ImmutableMap.of(
            zh_cn, "研发完成！",
            en_us, "Research Complete!"
    )),
    MISC("machine_max.misc", ImmutableMap.of(
            zh_cn, "杂项",
            en_us, "Misc"
    )),
    MESSAGE_CONTROL_GROUP_SWITCHED("message.machine_max.control_group_switched", ImmutableMap.of(
            zh_cn, "已切换控制组：%s",
            en_us, "Control Group: %s"
    )),
    MESSAGE_SEAT_SWITCHED("message.machine_max.seat.switched", ImmutableMap.of(
            zh_cn, "%1$s/%2$s · %3$s",
            en_us, "%1$s/%2$s · %3$s"
    )),
    MESSAGE_SEAT_FULL("message.machine_max.seat.full", ImmutableMap.of(
            zh_cn, "没有其它可用座位",
            en_us, "No other seat available"
    )),
    MESSAGE_SEAT_NOT_SEATED("message.machine_max.seat.not_seated", ImmutableMap.of(
            zh_cn, "当前不在座位上",
            en_us, "You are not seated"
    )),
    MESSAGE_SEAT_SAME_SEAT("message.machine_max.seat.same_seat", ImmutableMap.of(
            zh_cn, "已经在这个座位上",
            en_us, "Already in that seat"
    )),
    MESSAGE_SEAT_OCCUPIED("message.machine_max.seat.occupied", ImmutableMap.of(
            zh_cn, "该座位已被占用",
            en_us, "That seat is occupied"
    )),
    MESSAGE_SEAT_UNAVAILABLE("message.machine_max.seat.unavailable", ImmutableMap.of(
            zh_cn, "该座位不可用",
            en_us, "That seat is unavailable"
    )),
    MESSAGE_SEAT_DIFFERENT_ASSEMBLY("message.machine_max.seat.different_assembly", ImmutableMap.of(
            zh_cn, "该座位不属于当前载具",
            en_us, "That seat belongs to another vehicle"
    )),
    MESSAGE_SEAT_NO_ENTITY("message.machine_max.seat.no_entity", ImmutableMap.of(
            zh_cn, "该座位所在的零件尚未就绪",
            en_us, "That seat's part is not ready yet"
    )),
    MESSAGE_SEAT_MOUNT_FAILED("message.machine_max.seat.mount_failed", ImmutableMap.of(
            zh_cn, "无法就座",
            en_us, "Could not take that seat"
    )),
    MESSAGE_SEAT_RATE_LIMITED("message.machine_max.seat.rate_limited", ImmutableMap.of(
            zh_cn, "换座过于频繁",
            en_us, "Switching seats too quickly"
    )),
    SEAT_MAP_LEGEND_YOU("seat_map.legend.you", ImmutableMap.of(
            zh_cn, "你",
            en_us, "YOU"
    )),
    SEAT_MAP_LEGEND_NEXT("seat_map.legend.next", ImmutableMap.of(
            zh_cn, "下一个",
            en_us, "NEXT"
    )),
    SEAT_MAP_LEGEND_EMPTY("seat_map.legend.empty", ImmutableMap.of(
            zh_cn, "空",
            en_us, "EMPTY"
    )),
    SEAT_MAP_LEGEND_BUSY("seat_map.legend.busy", ImmutableMap.of(
            zh_cn, "占用",
            en_us, "BUSY"
    )),
    MESSAGE_LEAVING_VEHICLE("message.machine_max.leaving_vehicle", ImmutableMap.of(
            zh_cn, "长按[%1$s]键%2$s/0.50秒以离开载具",
            en_us, "Hold [%1$s] %2$s/0.50s to leave the vehicle."
    )),
    MESSAGE_WATCH_INTERACT_BOX_INFO("message.machine_max.watch_interact_box_info", ImmutableMap.of(
            zh_cn, "[%1$s]",
            en_us, "[%1$s]"
    )),
    ERROR_USE_PART_ITEM("error.machine_max.use_part_item", ImmutableMap.of(
            zh_cn, "尝试放置 %1$s 时出现错误：%2$s",
            en_us, "An error occurred while trying to deploy %1$s：%2$s"
    )),
    TOOLTIP_CROWBAR_SAFE_DISASSEMBLY("tooltip.machine_max.crowbar.safe_disassembly", ImmutableMap.of(
            zh_cn, "互动以安全拆除%1$s",
            en_us, "Interact to safely disassemble %1$s"
    )),
    TOOLTIP_CROWBAR_DETACH_CONNECTOR("tooltip.machine_max.crowbar.detach_connector", ImmutableMap.of(
            zh_cn, "互动以断开%1$s和%2$s的连接",
            en_us, "Interact to detach %1$s from %2$s"
    )),
    TOOLTIP_CROWBAR_UNSAFE_DISASSEMBLY("tooltip.machine_max.crowbar.unsafe_disassembly", ImmutableMap.of(
            zh_cn, "互动以强行拆除%1$s (可能损坏部件)",
            en_us, "Interact to force disassemble %1$s (Could damage part)"
    )),
    TOOLTIP_WRENCH_DISASSEMBLY("tooltip.machine_max.wrench.disassembly", ImmutableMap.of(
            zh_cn, "结构完整性：%1$s/%2$s 潜行互动以安全解除%3$s的固定",
            en_us, "Integrity: %1$s/%2$s Interact to safely unbolt %3$s"
    )),
    TOOLTIP_WRENCH_REPAIR("tooltip.machine_max.wrench.repair", ImmutableMap.of(
            zh_cn, "结构完整性：%2$s/%3$s 部件耐久：%4$s/%5$s 互动以维修%1$s",
            en_us, "Integrity: %2$s/%3$s Durability: %4$s/%5$s Interact to repair %1$s"
    )),
    TOOLTIP_WRENCH_CANT_REPAIR("tooltip.machine_max.wrench.cant_repair", ImmutableMap.of(
            zh_cn, "%1$s已被摧毁，无法修复，潜行互动以解除固定便于拆除",
            en_us, "%1$s has been destroyed, cannot repair. Crouch and interact to unbolt the part"
    )),
    TOOLTIP_WRENCH_NO_NEED_TO_REPAIR("tooltip.machine_max.wrench.no_need_to_repair", ImmutableMap.of(
            zh_cn, "%1$s无需修复或固定",
            en_us, "%1$s is no need to repair"
    )),
    TOOLTIP_SPRAY_CAN_INTERACT("tooltip.machine_max.spray_can.interact", ImmutableMap.of(
            zh_cn, "互动以喷涂：",
            en_us, "Interact to paint:"
    )),
    TOOLTIP_JADE_SUBPART_DURABILITY("tooltip.machine_max.jade.subpart_durability", ImmutableMap.of(
            zh_cn, "零件耐久: %1$s",
            en_us, "SubPart: %1$s"
    )),
    TOOLTIP_JADE_VEHICLE_DURABILITY("tooltip.machine_max.jade.vehicle_durability", ImmutableMap.of(
            zh_cn, "载具耐久: %1$s",
            en_us, "Vehicle: %1$s"
    )),
    MESSAGE_BLUEPRINT_SAVED("message.machine_max.blueprint_saved", ImmutableMap.of(
            zh_cn, "蓝图已保存至蓝图库",
            en_us, "Blueprint has been saved to the blueprint library"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_SUCCESS("message.machine_max.blueprint_extract.success", ImmutableMap.of(
            zh_cn, "已取出蓝图，消耗研发点 %1$s",
            en_us, "Blueprint extracted, consumed %1$s research points"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_FAILED("message.machine_max.blueprint_extract.failed", ImmutableMap.of(
            zh_cn, "取出蓝图失败：%1$s",
            en_us, "Failed to extract blueprint: %1$s"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_REASON_NONE("message.machine_max.blueprint_extract.reason.none", ImmutableMap.of(
            zh_cn, "未知原因",
            en_us, "unknown reason"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_REASON_INVALID_STRUCTURE("message.machine_max.blueprint_extract.reason.invalid_structure", ImmutableMap.of(
            zh_cn, "蓝图数据非法",
            en_us, "invalid blueprint data"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_REASON_MISSING_PARTS("message.machine_max.blueprint_extract.reason.missing_parts", ImmutableMap.of(
            zh_cn, "缺少内容包零件",
            en_us, "missing content pack parts"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_REASON_INSUFFICIENT_RP("message.machine_max.blueprint_extract.reason.insufficient_rp", ImmutableMap.of(
            zh_cn, "研发点不足",
            en_us, "insufficient research points"
    )),
    MESSAGE_BLUEPRINT_EXTRACT_REASON_UNKNOWN_BLUEPRINT("message.machine_max.blueprint_extract.reason.unknown_blueprint", ImmutableMap.of(
            zh_cn, "未知蓝图",
            en_us, "unknown blueprint"
    )),
    MESSAGE_BLUEPRINT_ERROR("message.machine_max.blueprint_error", ImmutableMap.of(
            zh_cn, "保存蓝图失败: %1$s",
            en_us, "Failed to save blueprint: %1$s"
    )),
    MESSAGE_ASSEMBLY_LOCKED("message.machine_max.assembly.locked", ImmutableMap.of(
            zh_cn, "无法装配 %1$s：需先完成对应研发，或持有该零件的制造蓝图",
            en_us, "Cannot assemble %1$s: research it first or hold its fabricating blueprint"
    )),
    MESSAGE_BLUEPRINT_PASS("message.machine_max.blueprint_pass", ImmutableMap.of(
            zh_cn, "未选中任何载具，取消保存蓝图",
            en_us, "No vehicle selected, skipping blueprint save"
    )),
    MESSAGE_BLUEPRINT_PLACE_FAILED("message.machine_max.blueprint.place_failed", ImmutableMap.of(
            zh_cn, "空间不足，无法部署载具",
            en_us, "No enough space to deploy vehicle"
    )),
    MESSAGE_VEHICLE_PLACE_FAILED("message.machine_max.vehicle.place_failed", ImmutableMap.of(
            zh_cn, "部署载具失败: %1$s",
            en_us, "Failed to deploy vehicle: %1$s"
    )),
    MESSAGE_PART_PLACE_FAILED("message.machine_max.part.place_failed", ImmutableMap.of(
            zh_cn, "放置部件失败: %1$s",
            en_us, "Failed to place part: %1$s"
    )),
    // 蓝图终端
    MESSAGE_PDA_DEPOSIT_SUCCESS("message.machine_max.pda.deposit.success", ImmutableMap.of(
            zh_cn, "已存入 %1$s 张蓝图",
            en_us, "Stored %1$s blueprint(s)"
    )),
    MESSAGE_PDA_DEPOSIT_REJECTED("message.machine_max.pda.deposit.rejected", ImmutableMap.of(
            zh_cn, "%1$s 张蓝图已收纳，未重复存入",
            en_us, "%1$s blueprint(s) already stored"
    )),
    MESSAGE_PDA_DESIGN_MODE_ENTER("message.machine_max.pda.design_mode.enter", ImmutableMap.of(
            zh_cn, "已进入设计模式",
            en_us, "Design mode enabled"
    )),
    MESSAGE_PDA_DESIGN_MODE_EXIT("message.machine_max.pda.design_mode.exit", ImmutableMap.of(
            zh_cn, "已退出设计模式",
            en_us, "Design mode disabled"
    )),
    // 创造模式物品栏
    ITEM_GROUP_MAIN("itemGroup.machine_max.main", ImmutableMap.of(
            zh_cn, "MachineMax: 工具与材料",
            en_us, "MachineMax: Materials and Tools"
    )),
    ITEM_GROUP_PART("itemGroup.machine_max.part", ImmutableMap.of(
            zh_cn, "MachineMax: 零部件",
            en_us, "MachineMax: Parts"
    )),
    ITEM_GROUP_FITTING("itemGroup.machine_max.fitting", ImmutableMap.of(
            zh_cn, "MachineMax: 配件",
            en_us, "MachineMax: Fittings"
    )),
    ITEM_GROUP_VEHICLE_BLUEPRINT("itemGroup.machine_max.vehicle_blueprint", ImmutableMap.of(
            zh_cn, "MachineMax: 载具设计图",
            en_us, "MachineMax: Vehicle Designs"
    )),
    ITEM_GROUP_FABRICATING_BLUEPRINT("itemGroup.machine_max.fabricating_blueprint", ImmutableMap.of(
            zh_cn, "MachineMax: 制造蓝图",
            en_us, "MachineMax: Fabricating Blueprints"
    )),
    ITEM_GROUP_ASSEMBLY("itemGroup.machine_max.assembly", ImmutableMap.of(
            zh_cn, "MachineMax: 装配体",
            en_us, "MachineMax: Assembly"
    )),
    // 物品
    BLOCK_FABRICATOR("block.machine_max.fabricator", ImmutableMap.of(
            zh_cn, "制造器(WIP)",
            en_us, "Fabricator(WIP)"
    )),
    BLOCK_RESEARCH_TABLE("block.machine_max.research_table", ImmutableMap.of(
            zh_cn, "研究台",
            en_us, "Research Table"
    )),
    ITEM_CROWBAR("item.machine_max.crowbar", ImmutableMap.of(
            zh_cn, "撬棍",
            en_us, "Crowbar"
    )),
    ITEM_WELDING_TORCH("item.machine_max.welding_torch", ImmutableMap.of(
            zh_cn, "焊枪",
            en_us, "Welding Torch"
    )),
    ITEM_SPRAY_CAN("item.machine_max.spray_can", ImmutableMap.of(
            zh_cn, "喷漆罐",
            en_us, "Spray Can"
    )),
    ITEM_ENDER_GK_RESIN("item.machine_max.ender_gk_resin", ImmutableMap.of(
            zh_cn, "末影GK树脂",
            en_us, "Ender GK Resin"
    )),
    ITEM_EMPTY_BLUEPRINT("item.machine_max.empty_blueprint", ImmutableMap.of(
            zh_cn, "空白载具蓝图",
            en_us, "Empty Vehicle Blueprint"
    )),
    ITEM_FABRICATING_BLUEPRINT("item.machine_max.fabricating_blueprint", ImmutableMap.of(
            zh_cn, "制造蓝图",
            en_us, "Fabricating Blueprint"
    )),
    ITEM_PART_FABRICATING_BLUEPRINT("item.machine_max.part_fabricating_blueprint", ImmutableMap.of(
            zh_cn, "零件制造蓝图",
            en_us, "Part Fabricating Blueprint"
    )),
    ITEM_STRUCTURAL_COMPONENT_1("item.machine_max.structural_component_1", ImmutableMap.of(
            zh_cn, "初级结构部件",
            en_us, "Basic Structural Component"
    )),
    ITEM_MECHANIC_COMPONENT_1("item.machine_max.mechanic_component_1", ImmutableMap.of(
            zh_cn, "初级机械构件",
            en_us, "Basic Mechanical Component"
    )),
    ITEM_WEAPON_COMPONENT_1("item.machine_max.weapon_component_1", ImmutableMap.of(
            zh_cn, "初级武器零件",
            en_us, "Basic Weapon Component"
    )),
    ITEM_ELECTRONIC_COMPONENT_1("item.machine_max.electronic_component_1", ImmutableMap.of(
            zh_cn, "初级电子元件",
            en_us, "Basic Electronic Component"
    )),
    ITEM_POWER_COMPONENT_1("item.machine_max.power_component_1", ImmutableMap.of(
            zh_cn, "初级能源组件",
            en_us, "Basic Power Component"
    )),
    ITEM_ENERGETIC_COMPONENT_1("item.machine_max.energetic_component_1", ImmutableMap.of(
            zh_cn, "初级含能材料",
            en_us, "Basic Energetic Material"
    )),
    ITEM_PDA("item.machine_max.pda", ImmutableMap.of(
            zh_cn, "蓝图终端",
            en_us, "Blueprint Terminal"
    )),
    // 物品音效
    SUBTITLES_ITEM_WELDING_TORCH_START("subtitles.item.welding_torch.start", ImmutableMap.of(
            zh_cn, "焊枪启动",
            en_us, "Welding torch starts"
    )),
    SUBTITLES_ITEM_WELDING_TORCH_LOOP("subtitles.item.welding_torch.loop", ImmutableMap.of(
            zh_cn, "焊接中",
            en_us, "Welding torch running"
    )),
    SUBTITLES_ITEM_WELDING_TORCH_END("subtitles.item.welding_torch.end", ImmutableMap.of(
            zh_cn, "焊枪关闭",
            en_us, "Welding torch stops"
    )),
    // 菜单
    GUI_WELCOME_TITLE("gui.machine_max.welcome.title", ImmutableMap.of(
            zh_cn, "欢迎参与 Machine Max Beta 公开测试!",
            en_us, "Welcome to Machine Max Beta!"
    )),
    GUI_WELCOME_CONTENT("gui.machine_max.welcome.content", ImmutableMap.of(
            zh_cn, "这是一款以物理和载具组装为主题的 Minecraft模组。\n本模组仍处于早期版本，可能存在各类问题，不代表最终品质。\n使用旧有存档时请务必做好备份工作。\n\n祝游玩愉快！",
            en_us, "This is a Minecraft mod themed around physics and vehicle assembly.\nThis mod is still in an early version, may have various issues, and does not represent final quality.\nPlease make sure to back up your old saves.\n\nHave fun!"
    )),
    GUI_WELCOME_PROCEED("gui.machine_max.welcome.proceed", ImmutableMap.of(
            zh_cn, "确认",
            en_us, "Confirm"
    )),
    GUI_WELCOME_DONT_SHOW_AGAIN("gui.machine_max.welcome.dont_show_again", ImmutableMap.of(
            zh_cn, "不再显示此页面",
            en_us, "Don't show this again"
    )),
    GUI_SET_VEHICLE_NAME("gui.machine_max.set_vehicle_name", ImmutableMap.of(
            en_us, "Set Vehicle Name"
    )),
    GUI_CONFIRM("gui.machine_max.confirm", ImmutableMap.of(
            zh_cn, "确认",
            en_us, "Confirm"
    )),
    GUI_CANCEL("gui.machine_max.cancel", ImmutableMap.of(
            zh_cn, "取消",
            en_us, "Cancel"
    )),
    GUI_ENTER_VEHICLE_NAME("gui.machine_max.enter_vehicle_name", ImmutableMap.of(
            zh_cn, "设置载具名称",
            en_us, "Enter Vehicle Name"
    )),
    GUI_ENTER_VEHICLE_DESCRIPTION("gui.machine_max.enter_vehicle_description", ImmutableMap.of(
            zh_cn, "设置设计描述（可选）",
            en_us, "Enter design description (optional)"
    )),
    GUI_FABRICATOR_RECIPES("gui.machine_max.fabricator.recipes", ImmutableMap.of(
            zh_cn, "配方列表",
            en_us, "Recipes"
    )),
    GUI_FABRICATOR_TASKS("gui.machine_max.fabricator.tasks", ImmutableMap.of(
            zh_cn, "制造任务",
            en_us, "Tasks"
    )),
    GUI_FABRICATOR_PREVIEW("gui.machine_max.fabricator.preview", ImmutableMap.of(
            zh_cn, "预览",
            en_us, "Preview"
    )),
    GUI_FABRICATOR_MATERIALS("gui.machine_max.fabricator.materials", ImmutableMap.of(
            zh_cn, "所需材料",
            en_us, "Material requirements"
    )),
    GUI_FABRICATOR_FREE_SLOTS("gui.machine_max.fabricator.free_slots", ImmutableMap.of(
            zh_cn, "任务队列空闲剩余: %1$s/%2$s",
            en_us, "Idle task queue: %1$s/%2$s"
    )),
    GUI_FABRICATOR_INSUFFICIENT_MATERIALS("gui.machine_max.fabricator.insufficient_materials", ImmutableMap.of(
            zh_cn, "材料不足",
            en_us, "Insufficient materials"
    )),
    GUI_FABRICATOR_NO_FREE_SLOTS("gui.machine_max.fabricator.no_free_slots", ImmutableMap.of(
            zh_cn, "任务队列已满",
            en_us, "Task queue is full"
    )),
    GUI_FABRICATOR_START_PRODUCTION("gui.machine_max.fabricator.start_production", ImmutableMap.of(
            zh_cn, "开始制造",
            en_us, "FABRICATE"
    )),
    GUI_FABRICATOR_CANCEL_TASK("gui.machine_max.fabricator.cancel_task", ImmutableMap.of(
            zh_cn, "取消任务",
            en_us, "CANCEL"
    )),
    GUI_FABRICATOR_COLLECT_TASK("gui.machine_max.fabricator.collect_task", ImmutableMap.of(
            zh_cn, "领取产出",
            en_us, "CLAIM"
    )),
    GUI_FABRICATOR_COLLECT_ALL_TASK("gui.machine_max.fabricator.collect_all_task", ImmutableMap.of(
            zh_cn, "领取所有产出",
            en_us, "CLAIM ALL"
    )),
    GUI_FABRICATOR_SEARCH_HINT("gui.machine_max.fabricator.search_hint", ImmutableMap.of(
            zh_cn, "搜索配方…",
            en_us, "Search recipe…"
    )),
    GUI_FABRICATOR_STATUS_QUEUED("gui.machine_max.fabricator.status.queued", ImmutableMap.of(
            zh_cn, "排队中",
            en_us, "Queued"
    )),
    GUI_FABRICATOR_STATUS_PRODUCING("gui.machine_max.fabricator.status.producing", ImmutableMap.of(
            zh_cn, "制造中",
            en_us, "Producing"
    )),
    GUI_FABRICATOR_STATUS_COMPLETED("gui.machine_max.fabricator.status.completed", ImmutableMap.of(
            zh_cn, "已完成",
            en_us, "Completed"
    )),
    GUI_FABRICATOR_STATUS_IDLE("gui.machine_max.fabricator.status.idle", ImmutableMap.of(
            zh_cn, "空闲",
            en_us, "Idle"
    )),
    GUI_FABRICATOR_READY_FOR_COLLECTION("gui.machine_max.fabricator.ready_for_collection", ImmutableMap.of(
            zh_cn, "已完成",
            en_us, "Complete!"
    )),
    GUI_FABRICATOR_ESTIMATED_TIME("gui.machine_max.fabricator.estimated_time", ImmutableMap.of(
            zh_cn, "预计: %s",
            en_us, "EST: %s"
    )),
    GUI_FABRICATOR_REMAINING_TIME("gui.machine_max.fabricator.remaining_time", ImmutableMap.of(
            zh_cn, "剩余: %s",
            en_us, "Remaining: %s"
    )),
    GUI_FABRICATOR_NO_ITEM("gui.machine_max.fabricator.no_item", ImmutableMap.of(
            zh_cn, "无物品",
            en_us, "No Item"
    )),
    GUI_FABRICATOR_SELECT_RECIPE_HINT("gui.machine_max.fabricator.select_recipe_hint", ImmutableMap.of(
            zh_cn, "选择配方查看详情",
            en_us, "Select a recipe to view details"
    )),
    GUI_FABRICATOR_RECIPE_DETAILS("gui.machine_max.fabricator.recipe_details", ImmutableMap.of(
            zh_cn, "配方详情",
            en_us, "Recipe Details"
    )),
    GUI_FABRICATOR_ACTUAL_TIME("gui.machine_max.fabricator.actual_time", ImmutableMap.of(
            zh_cn, "制造时间: %s",
            en_us, "Fabrication Time: %s"
    )),
    GUI_FABRICATOR_BASE_TIME("gui.machine_max.fabricator.base_time", ImmutableMap.of(
            zh_cn, "基础时间: %s",
            en_us, "Base Time: %s"
    )),
    GUI_FABRICATOR_EFFICIENCY("gui.machine_max.fabricator.efficiency", ImmutableMap.of(
            zh_cn, "效率: %sx",
            en_us, "Efficiency: %sx"
    )),
    GUI_FABRICATOR_OUTPUT_COUNT("gui.machine_max.fabricator.output_count", ImmutableMap.of(
            zh_cn, "产出数量: %s",
            en_us, "Output Count: %s"
    )),
    GUI_FABRICATOR_RECIPE_DESCRIPTION("gui.machine_max.fabricator.recipe_description", ImmutableMap.of(
            zh_cn, "配方描述:",
            en_us, "Recipe Description:"
    )),
    GUI_FABRICATOR_ITEM_DESCRIPTION("gui.machine_max.fabricator.item_description", ImmutableMap.of(
            zh_cn, "物品描述:",
            en_us, "Item Description:"
    )),
    // 研究台界面
    GUI_RESEARCH_FREE_RP("gui.machine_max.research.free_rp", ImmutableMap.of(
            zh_cn, "\uD83D\uDD2C自由研发点：",
            en_us, "\uD83D\uDD2CFree Research Points:"
    )),
    GUI_RESEARCH_TITLE("gui.machine_max.research.title", ImmutableMap.of(
            zh_cn, "研究台",
            en_us, "Research Table"
    )),
    GUI_RESEARCH_TAB_RESEARCH("gui.machine_max.research.tab.research", ImmutableMap.of(
            zh_cn, "研发",
            en_us, "Research"
    )),
    GUI_RESEARCH_TAB_LIBRARY("gui.machine_max.research.tab.library", ImmutableMap.of(
            zh_cn, "蓝图库",
            en_us, "Library"
    )),
    GUI_RESEARCH_SEARCH_PLACEHOLDER("gui.machine_max.research.search_placeholder", ImmutableMap.of(
            zh_cn, "搜索研发项目或载具",
            en_us, "Search projects or vehicles"
    )),
    GUI_RESEARCH_EXPAND_ALL("gui.machine_max.research.expand_all", ImmutableMap.of(
            zh_cn, "展开全部",
            en_us, "Expand All"
    )),
    GUI_RESEARCH_COLLAPSE_ALL("gui.machine_max.research.collapse_all", ImmutableMap.of(
            zh_cn, "折叠全部",
            en_us, "Collapse All"
    )),
    GUI_RESEARCH_STATUS_DONE("gui.machine_max.research.status.done", ImmutableMap.of(
            zh_cn, "已完成",
            en_us, "Completed"
    )),
    GUI_RESEARCH_STATUS_AVAILABLE("gui.machine_max.research.status.available", ImmutableMap.of(
            zh_cn, "可研发",
            en_us, "Available"
    )),
    GUI_RESEARCH_STATUS_INSUFFICIENT("gui.machine_max.research.status.insufficient", ImmutableMap.of(
            zh_cn, "材料不足",
            en_us, "Missing Materials"
    )),
    GUI_RESEARCH_STATUS_PREREQUISITE_MISSING("gui.machine_max.research.status.prerequisite_missing", ImmutableMap.of(
            zh_cn, "前置未完成",
            en_us, "Prerequisite Missing"
    )),
    GUI_RESEARCH_STATUS_UNREACHABLE("gui.machine_max.research.status.unreachable", ImmutableMap.of(
            zh_cn, "不可研发",
            en_us, "Unreachable"
    )),
    GUI_RESEARCH_NOTE_ADDITIONAL("gui.machine_max.research.note.additional", ImmutableMap.of(
            zh_cn, "另需 %1$s 项",
            en_us, "%1$s more required"
    )),
    GUI_RESEARCH_GROUP_PROGRESS("gui.machine_max.research.group.progress", ImmutableMap.of(
            zh_cn, "已完成 %1$s / %2$s",
            en_us, "Completed %1$s / %2$s"
    )),
    GUI_RESEARCH_DETAIL_EMPTY("gui.machine_max.research.detail.empty", ImmutableMap.of(
            zh_cn, "选择左侧条目查看详情",
            en_us, "Select an entry to view details"
    )),
    GUI_RESEARCH_DETAIL_PREVIEW("gui.machine_max.research.detail.preview", ImmutableMap.of(
            zh_cn, "产物预览",
            en_us, "Product Preview"
    )),
    GUI_RESEARCH_DETAIL_POINTS("gui.machine_max.research.detail.points", ImmutableMap.of(
            zh_cn, "研发点",
            en_us, "Research Points"
    )),
    GUI_RESEARCH_DETAIL_MATERIALS("gui.machine_max.research.detail.materials", ImmutableMap.of(
            zh_cn, "研发材料",
            en_us, "Research Materials"
    )),
    GUI_RESEARCH_DETAIL_PREREQUISITES("gui.machine_max.research.detail.prerequisites", ImmutableMap.of(
            zh_cn, "前置条件",
            en_us, "Prerequisites"
    )),
    GUI_RESEARCH_DETAIL_DESCRIPTION("gui.machine_max.research.detail.description", ImmutableMap.of(
            zh_cn, "说明",
            en_us, "Description"
    )),
    GUI_RESEARCH_DETAIL_DISTRIBUTION("gui.machine_max.research.detail.distribution", ImmutableMap.of(
            zh_cn, "状态分布",
            en_us, "Status Breakdown"
    )),
    GUI_RESEARCH_DETAIL_NO_PREVIEW("gui.machine_max.research.detail.no_preview", ImmutableMap.of(
            zh_cn, "无可预览产物",
            en_us, "No product to preview"
    )),
    GUI_RESEARCH_DETAIL_NO_PREREQUISITE("gui.machine_max.research.detail.no_prerequisite", ImmutableMap.of(
            zh_cn, "无前置条件，位于组根层。",
            en_us, "No prerequisite, at the group root."
    )),
    GUI_RESEARCH_DETAIL_UNFINISHED("gui.machine_max.research.detail.unfinished", ImmutableMap.of(
            zh_cn, "未完成",
            en_us, "Unfinished"
    )),
    GUI_RESEARCH_DETAIL_OTHER_GROUP("gui.machine_max.research.detail.other_group", ImmutableMap.of(
            zh_cn, "其他分组",
            en_us, "Other Group"
    )),
    GUI_RESEARCH_DETAIL_NO_ACTION("gui.machine_max.research.detail.no_action", ImmutableMap.of(
            zh_cn, "当前状态无可执行操作",
            en_us, "No action available in the current state"
    )),
    GUI_RESEARCH_DETAIL_DEFAULT_DESCRIPTION("gui.machine_max.research.detail.default_description", ImmutableMap.of(
            zh_cn, "研发完成后面向该零件产出制造蓝图，之后可据蓝图制造与装配。",
            en_us, "Once researched, the part's fabricating blueprint can be transcribed and then used to fabricate and assemble it."
    )),
    GUI_RESEARCH_DETAIL_GROUP_HINT("gui.machine_max.research.detail.group_hint", ImmutableMap.of(
            zh_cn, "组头显示该组名称与「已完成 / 总数」，兼作进度总览。组内条目按前置关系缩进，缩进连接线表示依赖层级；不可研发的条目置于组根层并标注原因。",
            en_us, "A group header shows its name and \"completed / total\", doubling as a progress overview. Entries are indented by prerequisite depth; unreachable entries stay at the group root with the reason marked."
    )),
    GUI_RESEARCH_REASON_CYCLE("gui.machine_max.research.reason.cycle", ImmutableMap.of(
            zh_cn, "位于依赖环上，该条目永远无法研发",
            en_us, "On a prerequisite cycle, this entry can never be researched"
    )),
    GUI_RESEARCH_ACTION_RESEARCH("gui.machine_max.research.action.research", ImmutableMap.of(
            zh_cn, "开始研究",
            en_us, "Start Research"
    )),
    GUI_RESEARCH_ACTION_TAKE("gui.machine_max.research.action.take", ImmutableMap.of(
            zh_cn, "取出",
            en_us, "Extract"
    )),
    GUI_RESEARCH_ACTION_RENAME("gui.machine_max.research.action.rename", ImmutableMap.of(
            zh_cn, "改名",
            en_us, "Rename"
    )),
    GUI_RESEARCH_ACTION_DELETE("gui.machine_max.research.action.delete", ImmutableMap.of(
            zh_cn, "删除",
            en_us, "Delete"
    )),
    // 蓝图终端界面
    GUI_PDA_TITLE("gui.machine_max.pda.title", ImmutableMap.of(
            zh_cn, "蓝图终端",
            en_us, "Blueprint Terminal"
    )),
    GUI_PDA_GROUP_STORED("gui.machine_max.pda.group.stored", ImmutableMap.of(
            zh_cn, "已收纳",
            en_us, "Stored"
    )),
    GUI_PDA_GROUP_INVENTORY("gui.machine_max.pda.group.inventory", ImmutableMap.of(
            zh_cn, "背包中的蓝图",
            en_us, "Blueprints in Inventory"
    )),
    GUI_PDA_GROUP_SHORTCUT("gui.machine_max.pda.group.shortcut", ImmutableMap.of(
            zh_cn, "设计模式快捷栏",
            en_us, "Design Mode Shortcuts"
    )),
    GUI_PDA_BTN_DEPOSIT("gui.machine_max.pda.btn.deposit", ImmutableMap.of(
            zh_cn, "存入",
            en_us, "Store"
    )),
    GUI_PDA_BTN_DEPOSIT_ALL("gui.machine_max.pda.btn.deposit_all", ImmutableMap.of(
            zh_cn, "一键存入全部",
            en_us, "Store All"
    )),
    GUI_PDA_BTN_STORED("gui.machine_max.pda.btn.stored", ImmutableMap.of(
            zh_cn, "已收纳",
            en_us, "Stored"
    )),
    GUI_PDA_BTN_MERGE("gui.machine_max.pda.btn.merge", ImmutableMap.of(
            zh_cn, "合并 (+%1$s)",
            en_us, "Merge (+%1$s)"
    )),
    GUI_PDA_BTN_UNBIND("gui.machine_max.pda.btn.unbind", ImmutableMap.of(
            zh_cn, "解除绑定",
            en_us, "Unbind"
    )),
    GUI_PDA_STATUS_SELECT_ENTRY("gui.machine_max.pda.status.select_entry", ImmutableMap.of(
            zh_cn, "已选中「%1$s」，请点击快捷栏位以完成绑定",
            en_us, "Selected \"%1$s\" - click a shortcut slot to bind"
    )),
    GUI_PDA_STATUS_SELECT_SLOT("gui.machine_max.pda.status.select_slot", ImmutableMap.of(
            zh_cn, "已选中第 %1$s 栏，请点击左侧条目以完成绑定",
            en_us, "Selected slot %1$s - click a stored entry to bind"
    )),
    GUI_PDA_HINT_GENERAL("gui.machine_max.pda.hint.general", ImmutableMap.of(
            zh_cn, "该蓝图暂无直接使用方式",
            en_us, "This blueprint has no direct use"
    )),
    GUI_PDA_HINT_EMPTY_SLOT("gui.machine_max.pda.hint.empty_slot", ImmutableMap.of(
            zh_cn, "当前栏位未绑定蓝图",
            en_us, "No blueprint bound to this slot"
    )),
    GUI_PDA_TAG_UNKNOWN("gui.machine_max.pda.tag.unknown", ImmutableMap.of(
            zh_cn, "未知蓝图",
            en_us, "Unknown Blueprint"
    )),
    GUI_PDA_TAG_PART("gui.machine_max.pda.tag.part", ImmutableMap.of(
            zh_cn, "零件",
            en_us, "Part"
    )),
    GUI_PDA_TAG_GENERAL("gui.machine_max.pda.tag.general", ImmutableMap.of(
            zh_cn, "通用",
            en_us, "General"
    )),
    GUI_PDA_HINT_UNAVAILABLE("gui.machine_max.pda.hint.unavailable", ImmutableMap.of(
            zh_cn, "该蓝图当前不可用（配方已失效）",
            en_us, "This blueprint is currently unavailable (recipe missing)"
    )),
    GUI_PDA_COUNT_STORED("gui.machine_max.pda.count.stored", ImmutableMap.of(
            zh_cn, "已收纳 %1$s 项",
            en_us, "Stored %1$s"
    )),
    GUI_PDA_COUNT_INFINITE("gui.machine_max.pda.count.infinite", ImmutableMap.of(
            zh_cn, "无限 %1$s",
            en_us, "Infinite %1$s"
    )),
    GUI_PDA_COUNT_LIMITED("gui.machine_max.pda.count.limited", ImmutableMap.of(
            zh_cn, "有限 %1$s",
            en_us, "Limited %1$s"
    )),
    GUI_PDA_HUD_TITLE("gui.machine_max.pda.hud.title", ImmutableMap.of(
            zh_cn, "设计模式",
            en_us, "Design Mode"
    )),
    GUI_PDA_KEY_RIGHT_CLICK("gui.machine_max.pda.key.right_click", ImmutableMap.of(
            zh_cn, "右键",
            en_us, "Right-click"
    )),
    GUI_PDA_KEY_SNEAK("gui.machine_max.pda.key.sneak", ImmutableMap.of(
            zh_cn, "潜行键",
            en_us, "Sneak"
    )),
    GUI_PDA_ACTION_PLACE("gui.machine_max.pda.action.place", ImmutableMap.of(
            zh_cn, "放置零件",
            en_us, "Place Part"
    )),
    GUI_PDA_ACTION_EXIT("gui.machine_max.pda.action.exit", ImmutableMap.of(
            zh_cn, "退出",
            en_us, "Exit"
    )),
    // 武器状态 HUD
    GUI_WEAPON_HUD_READY("gui.machine_max.weapon_hud.ready", ImmutableMap.of(
            zh_cn, "就绪",
            en_us, "READY"
    )),
    GUI_WEAPON_HUD_EMPTY("gui.machine_max.weapon_hud.empty", ImmutableMap.of(
            zh_cn, "空弹",
            en_us, "EMPTY"
    )),
    // 蓝图库标签页
    GUI_RESEARCH_LIBRARY_STATUS_NORMAL("gui.machine_max.research.library.status.normal", ImmutableMap.of(
            zh_cn, "正常",
            en_us, "OK"
    )),
    GUI_RESEARCH_LIBRARY_STATUS_PARSE_ERROR("gui.machine_max.research.library.status.parse_error", ImmutableMap.of(
            zh_cn, "解析失败",
            en_us, "Parse Failed"
    )),
    GUI_RESEARCH_LIBRARY_STATUS_MISSING_PARTS("gui.machine_max.research.library.status.missing_parts", ImmutableMap.of(
            zh_cn, "零件缺失",
            en_us, "Missing Parts"
    )),
    GUI_RESEARCH_LIBRARY_ATTRIBUTES("gui.machine_max.research.library.attributes", ImmutableMap.of(
            zh_cn, "成品属性",
            en_us, "Design Attributes"
    )),
    GUI_RESEARCH_LIBRARY_SOURCE("gui.machine_max.research.library.source", ImmutableMap.of(
            zh_cn, "来源",
            en_us, "Source"
    )),
    GUI_RESEARCH_LIBRARY_RENAME_LABEL("gui.machine_max.research.library.rename_label", ImmutableMap.of(
            zh_cn, "名称",
            en_us, "Name"
    )),
    GUI_RESEARCH_LIBRARY_DEFAULT_DESCRIPTION("gui.machine_max.research.library.default_description", ImmutableMap.of(
            zh_cn, "保存的载具设计，可取出成品、改名或删除。",
            en_us, "A saved vehicle design; extract it, rename it or delete it."
    )),
    GUI_RESEARCH_LIBRARY_GROUP_HINT("gui.machine_max.research.library.group_hint", ImmutableMap.of(
            zh_cn, "蓝图库按来源分层：本地保存的设计可取出、改名或删除；内容包蓝图只读，可取出。",
            en_us, "The library is layered by source: locally saved designs can be extracted, renamed or deleted; content pack blueprints are read-only and can be extracted."
    )),
    GUI_RESEARCH_LIBRARY_STAT_PARTS("gui.machine_max.research.library.stat.parts", ImmutableMap.of(
            zh_cn, "零件",
            en_us, "parts"
    )),
    GUI_RESEARCH_LIBRARY_STAT_MASS("gui.machine_max.research.library.stat.mass", ImmutableMap.of(
            zh_cn, "质量 kg",
            en_us, "mass kg"
    )),
    GUI_RESEARCH_LIBRARY_ROW_SOURCE("gui.machine_max.research.library.row.source", ImmutableMap.of(
            zh_cn, "来源",
            en_us, "Source"
    )),
    GUI_RESEARCH_LIBRARY_ROW_TEMPLATE("gui.machine_max.research.library.row.template", ImmutableMap.of(
            zh_cn, "装配模板",
            en_us, "Assembly Template"
    )),
    GUI_RESEARCH_LIBRARY_ROW_AUTHOR("gui.machine_max.research.library.row.author", ImmutableMap.of(
            zh_cn, "作者",
            en_us, "Author"
    )),
    GUI_RESEARCH_LIBRARY_ROW_CREATED_AT("gui.machine_max.research.library.row.created_at", ImmutableMap.of(
            zh_cn, "创建时间",
            en_us, "Created At"
    )),
    GUI_RESEARCH_LIBRARY_ROW_WRITABLE("gui.machine_max.research.library.row.writable", ImmutableMap.of(
            zh_cn, "写入权限",
            en_us, "Write Access"
    )),
    GUI_RESEARCH_LIBRARY_READ_ONLY("gui.machine_max.research.library.read_only", ImmutableMap.of(
            zh_cn, "只读",
            en_us, "Read Only"
    )),
    GUI_RESEARCH_LIBRARY_READ_WRITE("gui.machine_max.research.library.read_write", ImmutableMap.of(
            zh_cn, "可改名 / 可删除",
            en_us, "Rename / Delete"
    )),
    GUI_BLUEPRINT_LIBRARY_SECTION_LOCAL("gui.machine_max.blueprint_library.section.local", ImmutableMap.of(
            zh_cn, "我的设计",
            en_us, "My Designs"
    )),
    GUI_BLUEPRINT_LIBRARY_SECTION_PACK("gui.machine_max.blueprint_library.section.pack", ImmutableMap.of(
            zh_cn, "内容包蓝图",
            en_us, "Content Pack Blueprints"
    )),
    JEI_BLUEPRINT_RESEARCH_RP_COST("jei.machine_max.blueprint_research.rp_cost", ImmutableMap.of(
            zh_cn, "研发点需求: %1$s",
            en_us, "Research Point Cost: %1$s"
    )),
    // 组装HUD
    HUD_WARN_SUBPART_DESTROYING("hud.warn.machine_max.subpart_destroying", ImmutableMap.of(
            zh_cn, "❌ 零件已损毁，销毁倒计时: %1$s秒",
            en_us, "❌ DESTROYED, DESTRUCT IN: %1$ss"
    )),
    HUD_WARN_SUBPART_DESTROYED("hud.warn.machine_max.subpart_destroyed", ImmutableMap.of(
            zh_cn, "❌ 零件已损毁",
            en_us, "❌ PART DESTROYED"
    )),
    HUD_WARN_SUBSYSTEM_MALFUNCTION("hud.warn.machine_max.subsystem_malfunction", ImmutableMap.of(
            zh_cn, "❌ %1$s瘫痪",
            en_us, "❌ %1$s MALFUNCTION"
    )),
    HUD_HINT_SUBSYSTEM_DURABILITY("hud.hint.machine_max.subsystem_durability", ImmutableMap.of(
            zh_cn, "⚠ %1$s耐久度未满: %2$s/%3$s",
            en_us, "⚠ %1$s DURABILITY: %2$s/%3$s"
    )),
    HUD_WARN_CONNECTOR_INTEGRITY_LOW("hud.warn.machine_max.connector_integrity_low", ImmutableMap.of(
            zh_cn, "❌ %1$s结构完整性极低，存在脱落风险",
            en_us, "❌ %1$s INTEGRITY LOW"
    )),
    HUD_HINT_CONNECTOR_INTEGRITY("hud.hint.machine_max.connector_integrity", ImmutableMap.of(
            zh_cn, "⚠ %1$s结构完整性未满: %2$s/%3$s",
            en_us, "⚠ %1$s INTEGRITY: %2$s/%3$s"
    )),
    HUD_INFO_ASSEMBLING_PROGRESS("hud.info.machine_max.assembling_progress", ImmutableMap.of(
            zh_cn, "装配进度: ",
            en_us, "ASSEMBLY: "
    )),
    HUD_HINT_CANNOT_ASSEMBLE("hud.hint.machine_max.cannot_assemble", ImmutableMap.of(
            zh_cn, "未研发且未持有蓝图，无法组装",
            en_us, "Not researched or have no blueprint, cannot assemble"
    )),
    HUD_KEY_ASSEMBLE("hud.key.machine_max.assemble", ImmutableMap.of(
            zh_cn, "[%1$s] 组装&修复&加固",
            en_us, "[%1$s] Assemble & Repair & Reinforce"
    )),
    HUD_KEY_REPAIR_WITHOUT_ASSEMBLE("hud.key.machine_max.repair_without_assemble", ImmutableMap.of(
            zh_cn, "[%1$s] 修复&加固",
            en_us, "[%1$s] Repair & Reinforce"
    )),
    HUD_KEY_DISASSEMBLE("hud.key.machine_max.disassemble", ImmutableMap.of(
            zh_cn, "[%1$s+%2$s] 拆解",
            en_us, "[%1$s+%2$s] Disassemble"
    )),
    HUD_KEY_TEAR_DOWN("hud.key.machine_max.tear_down", ImmutableMap.of(
            zh_cn, "[%1$s] 拆除部件",
            en_us, "[%1$s] Remove part"
    )),
    HUD_KEY_DETACH("hud.key.machine_max.detach", ImmutableMap.of(
            zh_cn, "[%1$s+%2$s] 断开连接点",
            en_us, "[%1$s+%2$s] Detach connector"
    )),
    // 研究点显示HUD
    HUD_HINT_RP_ADD_REASON_EXP("hud.hint.machine_max.rp_add_reason.exp", ImmutableMap.of(
            zh_cn, "获得经验",
            en_us, "Gain EXP"
    )),
    HUD_HINT_RP_ADD_REASON_REPAIR("hud.hint.machine_max.rp_add_reason.repair", ImmutableMap.of(
            zh_cn, "维修",
            en_us, "Repair"
    )),
    HUD_HINT_RP_ADD_REASON_ASSEMBLY("hud.hint.machine_max.rp_add_reason.assembly", ImmutableMap.of(
            zh_cn, "组装",
            en_us, "Assembly"
    )),
    HUD_HINT_RP_ADD_REASON_HIT("hud.hint.machine_max.rp_add_reason.hit", ImmutableMap.of(
            zh_cn, "命中",
            en_us, "Hit"
    )),
    HUD_HINT_RP_ADD_REASON_PART_DAMAGE("hud.hint.machine_max.rp_add_reason.part_damage", ImmutableMap.of(
            zh_cn, "伤害部件",
            en_us, "Damage Part"
    )),
    HUD_HINT_RP_ADD_REASON_PART_DESTROY("hud.hint.machine_max.rp_add_reason.part_destroy", ImmutableMap.of(
            zh_cn, "摧毁部件",
            en_us, "Destroy Part"
    )),
    HUD_HINT_RP_ADD_REASON_SUBSYSTEM_DAMAGE("hud.hint.machine_max.rp_add_reason.subsystem_damage", ImmutableMap.of(
            zh_cn, "伤害子系统",
            en_us, "Damage Subsystem"
    )),
    HUD_HINT_RP_ADD_REASON_SUBSYSTEM_DESTROY("hud.hint.machine_max.rp_add_reason.subsystem_destroy", ImmutableMap.of(
            zh_cn, "瘫痪子系统",
            en_us, "Destroy Subsystem"
    )),
    HUD_HINT_RP_ADD_REASON_UNKNOWN("hud.hint.machine_max.rp_add_reason.unknown", ImmutableMap.of(
            zh_cn, "未知",
            en_us, "Unknown"
    )),
    // 配置项
    CONFIGURATION_TITLE("machine_max.configuration.title", ImmutableMap.of(
            zh_cn, "Machine Max 配置",
            en_us, "Machine Max Configuration"
    )),
    CONFIGURATION_SECTION_MACHINE_MAX_CLIENT_TOML_TITLE("machine_max.configuration.section.machine.max.client.toml.title", ImmutableMap.of(
            zh_cn, "客户端配置",
            en_us, "Client Configuration"
    )),
    CONFIGURATION_SECTION_MACHINE_MAX_CLIENT_TOML("machine_max.configuration.section.machine.max.client.toml", ImmutableMap.of(
            zh_cn, "客户端配置",
            en_us, "Client Configuration"
    )),
    CONFIGURATION_SECTION_MACHINE_MAX_COMMON_TOML("machine_max.configuration.section.machine.max.common.toml", ImmutableMap.of(
            zh_cn, "通用配置",
            en_us, "Common Configuration"
    )),
    CONFIGURATION_SECTION_MACHINE_MAX_SERVER_TOML("machine_max.configuration.section.machine.max.server.toml", ImmutableMap.of(
            zh_cn, "服务器配置",
            en_us, "Server Configuration"
    )),
    CONFIG_JADE_PLUGIN_MM_PART_ENTITY_STATUS("config.jade.plugin_machine_max.mm_part_entity_status", ImmutableMap.of(
            zh_cn, "Machine Max 部件耐久显示",
            en_us, "Machine Max Part Durability"
    )),
    // 地面载具配置
    CONFIGURATION_GROUND_VEHICLE("machine_max.configuration.ground_vehicle", ImmutableMap.of(
            zh_cn, "地面载具",
            en_us, "Ground Vehicle"
    )),
    CONFIGURATION_GROUND_VEHICLE_BUTTON("machine_max.configuration.ground_vehicle.button", ImmutableMap.of(
            zh_cn, "地面载具",
            en_us, "Ground Vehicle"
    )),
    CONFIGURATION_GROUND_VEHICLE_TOOLTIP("machine_max.configuration.ground_vehicle.tooltip", ImmutableMap.of(
            zh_cn, "地面载具控制的平滑度与行为设置",
            en_us, "Ground vehicle control smoothness and behavior settings"
    )),
    CONFIGURATION_FULL_POWER_TIME("machine_max.configuration.full_power_time", ImmutableMap.of(
            zh_cn, "油门建立时间",
            en_us, "Throttle Ramp-Up Time"
    )),
    CONFIGURATION_FULL_POWER_TIME_TOOLTIP("machine_max.configuration.full_power_time.tooltip", ImmutableMap.of(
            zh_cn, "按下按键后达到满油门所需时间（秒）。越小响应越快。默认值：1.5（地面载具）、2.5（飞行器）。范围：0.05 ~ 99999.0",
            en_us, "Seconds to reach full throttle after pressing the key. Lower = faster response. Default: 1.5 (ground), 2.5 (plane). Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_POWER_OFF_TIME("machine_max.configuration.power_off_time", ImmutableMap.of(
            zh_cn, "收油归零时间",
            en_us, "Throttle Release Time"
    )),
    CONFIGURATION_POWER_OFF_TIME_TOOLTIP("machine_max.configuration.power_off_time.tooltip", ImmutableMap.of(
            zh_cn, "松开按键后油门回零所需时间（秒）。越小收油越快。默认值：2.0。范围：0.05 ~ 99999.0",
            en_us, "Seconds to return throttle to 0 after releasing the key. Lower = faster throttle cut. Default: 2.0. Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_FULL_BRAKE_TIME("machine_max.configuration.full_brake_time", ImmutableMap.of(
            zh_cn, "刹车建立时间",
            en_us, "Brake Ramp-Up Time"
    )),
    CONFIGURATION_FULL_BRAKE_TIME_TOOLTIP("machine_max.configuration.full_brake_time.tooltip", ImmutableMap.of(
            zh_cn, "按下按键后达到满刹车所需时间（秒）。越小刹车响应越快。默认值：0.3。范围：0.05 ~ 99999.0",
            en_us, "Seconds to reach full brake after pressing the key. Lower = faster braking. Default: 0.3. Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_BRAKE_OFF_TIME("machine_max.configuration.brake_off_time", ImmutableMap.of(
            zh_cn, "刹车释放时间",
            en_us, "Brake Release Time"
    )),
    CONFIGURATION_BRAKE_OFF_TIME_TOOLTIP("machine_max.configuration.brake_off_time.tooltip", ImmutableMap.of(
            zh_cn, "松开按键后刹车归零所需时间（秒）。越小车轮恢复滚动越快。默认值：0.1。范围：0.05 ~ 99999.0",
            en_us, "Seconds to release brake to 0 after releasing the key. Lower = wheels resume rolling faster. Default: 0.1. Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_FULL_STEERING_TIME("machine_max.configuration.full_steering_time", ImmutableMap.of(
            zh_cn, "转向建立时间",
            en_us, "Steering Ramp-Up Time"
    )),
    CONFIGURATION_FULL_STEERING_TIME_TOOLTIP("machine_max.configuration.full_steering_time.tooltip", ImmutableMap.of(
            zh_cn, "按下按键后达到满转向角所需时间（秒）。越小转向越快。默认值：0.4（地面载具）、0.25（舰艇）。范围：0.05 ~ 99999.0",
            en_us, "Seconds to reach full steering angle after pressing the key. Lower = faster steering. Default: 0.4 (ground), 0.25 (ship). Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_STEERING_OFF_TIME("machine_max.configuration.steering_off_time", ImmutableMap.of(
            zh_cn, "转向回正时间",
            en_us, "Steering Return Time"
    )),
    CONFIGURATION_STEERING_OFF_TIME_TOOLTIP("machine_max.configuration.steering_off_time.tooltip", ImmutableMap.of(
            zh_cn, "松开按键后转向回正到中央所需时间（秒）。越小车轮回正越快。默认值：0.2。范围：0.05 ~ 99999.0",
            en_us, "Seconds to return steering to center after releasing the key. Lower = wheels straighten faster. Default: 0.2. Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_AUTO_SWITCH_GEAR("machine_max.configuration.auto_switch_gear", ImmutableMap.of(
            zh_cn, "自动换挡",
            en_us, "Auto Gear Shift"
    )),
    CONFIGURATION_AUTO_SWITCH_GEAR_TOOLTIP("machine_max.configuration.auto_switch_gear.tooltip", ImmutableMap.of(
            zh_cn, "FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=始终自动。ALWAYS_DISABLED=仅手动。默认值：FOLLOW_VEHICLE",
            en_us, "FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = always auto. ALWAYS_DISABLED = manual only. Default: FOLLOW_VEHICLE"
    )),
    CONFIGURATION_AUTO_HANDBRAKE("machine_max.configuration.auto_handbrake", ImmutableMap.of(
            zh_cn, "自动手刹",
            en_us, "Auto Handbrake"
    )),
    CONFIGURATION_AUTO_HANDBRAKE_TOOLTIP("machine_max.configuration.auto_handbrake.tooltip", ImmutableMap.of(
            zh_cn, "FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=始终自动。ALWAYS_DISABLED=仅手动。默认值：FOLLOW_VEHICLE",
            en_us, "FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = always auto. ALWAYS_DISABLED = manual only. Default: FOLLOW_VEHICLE"
    )),
    CONFIGURATION_DRIFT_ASSIST("machine_max.configuration.drift_assist", ImmutableMap.of(
            zh_cn, "漂移辅助",
            en_us, "Drift Assist"
    )),
    CONFIGURATION_DRIFT_ASSIST_TOOLTIP("machine_max.configuration.drift_assist.tooltip", ImmutableMap.of(
            zh_cn, "漂移时自动反打方向。FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=始终开启。ALWAYS_DISABLED=关闭。默认值：FOLLOW_VEHICLE",
            en_us, "Auto counter-steer during drifting. FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = always on. ALWAYS_DISABLED = off. Default: FOLLOW_VEHICLE"
    )),
    CONFIGURATION_POSE_PREFERENCE("machine_max.configuration.pose_preference", ImmutableMap.of(
            zh_cn, "视角跟随",
            en_us, "Camera Follow Vehicle"
    )),
    CONFIGURATION_POSE_PREFERENCE_TOOLTIP("machine_max.configuration.pose_preference.tooltip", ImmutableMap.of(
            zh_cn, "自动旋转摄像机跟随载具朝向。FOLLOW_VEHICLE=由载具定义决定。ALWAYS_ENABLED=跟随。ALWAYS_DISABLED=自由视角。默认值：FOLLOW_VEHICLE",
            en_us, "Auto rotate camera to follow vehicle heading. FOLLOW_VEHICLE = vehicle-defined. ALWAYS_ENABLED = follow. ALWAYS_DISABLED = free. Default: FOLLOW_VEHICLE"
    )),
    CONFIGURATION_SPEED_TURNING_LIMIT("machine_max.configuration.speed_turning_limit", ImmutableMap.of(
            zh_cn, "高速转向限制",
            en_us, "High-Speed Steering Limit"
    )),
    CONFIGURATION_SPEED_TURNING_LIMIT_TOOLTIP("machine_max.configuration.speed_turning_limit.tooltip", ImmutableMap.of(
            zh_cn, "高速时限制转向角度防止侧翻。默认值：true",
            en_us, "Limit steering angle at high speed to prevent rollover. Default: true"
    )),
    CONFIGURATION_SEPARATE_THROTTLE_BRAKE("machine_max.configuration.separate_throttle_brake", ImmutableMap.of(
            zh_cn, "分离油门刹车通道",
            en_us, "Separate Throttle & Brake"
    )),
    CONFIGURATION_SEPARATE_THROTTLE_BRAKE_TOOLTIP("machine_max.configuration.separate_throttle_brake.tooltip", ImmutableMap.of(
            zh_cn, "将油门和刹车分离为独立通道。false=意图模式（W前进/S后退，由载具自动判断油门刹车）。true=分离模式（W油门/S刹车）。默认值：false",
            en_us, "Separate throttle and brake into independent channels. false = intent mode (W forward, S backward, vehicle auto-decides). true = raw pedal mode (W gas, S brake). Default: false"
    )),
    CONFIGURATION_RENDER_HIT_WHITENING("machine_max.configuration.render_hit_whitening", ImmutableMap.of(
            zh_cn, "部件受击闪白",
            en_us, "Part Hit Flash"
    )),
    CONFIGURATION_RENDER_HIT_WHITENING_TOOLTIP("machine_max.configuration.render_hit_whitening.tooltip", ImmutableMap.of(
            zh_cn, "部件被击中时显示白色闪烁效果。默认值：true",
            en_us, "Show a white flash on parts when hit. Default: true"
    )),
    CONFIGURATION_RENDER_DESTROY_BLACKENING("machine_max.configuration.render_destroy_blackening", ImmutableMap.of(
            zh_cn, "部件损毁变暗",
            en_us, "Part Destroyed Overlay"
    )),
    CONFIGURATION_RENDER_DESTROY_BLACKENING_TOOLTIP("machine_max.configuration.render_destroy_blackening.tooltip", ImmutableMap.of(
            zh_cn, "部件损毁后显示暗色覆盖。默认值：true",
            en_us, "Dark overlay on destroyed parts. Default: true"
    )),
    CONFIGURATION_RENDER_FORCE_TRANSLUCENT_PARTS("machine_max.configuration.render_force_translucent_parts", ImmutableMap.of(
            zh_cn, "强制半透明部件",
            en_us, "Force Translucent Parts"
    )),
    CONFIGURATION_RENDER_FORCE_TRANSLUCENT_PARTS_TOOLTIP("machine_max.configuration.render_force_translucent_parts.tooltip", ImmutableMap.of(
            zh_cn, "将使用Cutout渲染的部件改为半透明实体渲染。默认关闭是因为半透明渲染容易出现排序错误，导致其后的对象被错误剔除。默认值：false",
            en_us, "Replace cutout part rendering with translucent entity rendering. Off by default because translucent rendering often causes incorrect z-sorting and object culling. Default: false"
    )),
    CONFIGURATION_SHOW_WELCOME_SCREEN("machine_max.configuration.show_welcome_screen", ImmutableMap.of(
            zh_cn, "显示欢迎页面",
            en_us, "Show Welcome Screen"
    )),
    CONFIGURATION_SHOW_WELCOME_SCREEN_TOOLTIP("machine_max.configuration.show_welcome_screen.tooltip", ImmutableMap.of(
            zh_cn, "启动后首次进入标题画面时弹出欢迎页面。默认值：true",
            en_us, "Show the welcome screen when entering the title screen for the first time after launch. Default: true"
    )),
    // 后处理视觉特效配置
    CONFIGURATION_VISUAL_EFFECTS("machine_max.configuration.visual_effects", ImmutableMap.of(
            zh_cn, "视觉特效",
            en_us, "Visual Effects"
    )),
    CONFIGURATION_VISUAL_EFFECTS_BUTTON("machine_max.configuration.visual_effects.button", ImmutableMap.of(
            zh_cn, "视觉特效",
            en_us, "Visual Effects"
    )),
    CONFIGURATION_VISUAL_EFFECTS_TOOLTIP("machine_max.configuration.visual_effects.tooltip", ImmutableMap.of(
            zh_cn, "后处理视觉特效设置",
            en_us, "Post-processing visual effects settings"
    )),
    CONFIGURATION_OVERLOAD_ENABLED("machine_max.configuration.overload_enabled", ImmutableMap.of(
            zh_cn, "过载效果",
            en_us, "Overload Effect"
    )),
    CONFIGURATION_OVERLOAD_ENABLED_TOOLTIP("machine_max.configuration.overload_enabled.tooltip", ImmutableMap.of(
            zh_cn, "启用过载黑视/红视后处理效果。默认值：true",
            en_us, "Enable overload blackout/redout post-processing effect. Default: true"
    )),
    CONFIGURATION_SUPPRESSION_ENABLED("machine_max.configuration.suppression_enabled", ImmutableMap.of(
            zh_cn, "压制效果",
            en_us, "Suppression Effect"
    )),
    CONFIGURATION_SUPPRESSION_ENABLED_TOOLTIP("machine_max.configuration.suppression_enabled.tooltip", ImmutableMap.of(
            zh_cn, "启用压制（灰度化）后处理效果。默认值：true",
            en_us, "Enable suppression (desaturation/grayscale) post-processing effect. Default: true"
    )),
    CONFIGURATION_OVERLOAD_INTENSITY("machine_max.configuration.overload_intensity", ImmutableMap.of(
            zh_cn, "过载强度",
            en_us, "Overload Intensity"
    )),
    CONFIGURATION_OVERLOAD_INTENSITY_TOOLTIP("machine_max.configuration.overload_intensity.tooltip", ImmutableMap.of(
            zh_cn, "过载效果的强度倍率。范围 0.0 ~ 1.0。默认值：1.0",
            en_us, "Intensity multiplier for overload effect. Range: 0.0 ~ 1.0. Default: 1.0"
    )),
    CONFIGURATION_SUPPRESSION_INTENSITY("machine_max.configuration.suppression_intensity", ImmutableMap.of(
            zh_cn, "压制强度",
            en_us, "Suppression Intensity"
    )),
    CONFIGURATION_SUPPRESSION_INTENSITY_TOOLTIP("machine_max.configuration.suppression_intensity.tooltip", ImmutableMap.of(
            zh_cn, "压制效果的强度倍率。范围 0.0 ~ 1.0。默认值：1.0",
            en_us, "Intensity multiplier for suppression effect. Range: 0.0 ~ 1.0. Default: 1.0"
    )),
    // 舰艇配置
    CONFIGURATION_SHIP("machine_max.configuration.ship", ImmutableMap.of(
            zh_cn, "舰艇",
            en_us, "Ship"
    )),
    CONFIGURATION_SHIP_BUTTON("machine_max.configuration.ship.button", ImmutableMap.of(
            zh_cn, "舰艇",
            en_us, "Ship"
    )),
    CONFIGURATION_SHIP_TOOLTIP("machine_max.configuration.ship.tooltip", ImmutableMap.of(
            zh_cn, "舰艇转向平滑度设置",
            en_us, "Ship steering smoothness settings"
    )),
    // 飞行器配置
    CONFIGURATION_PLANE("machine_max.configuration.plane", ImmutableMap.of(
            zh_cn, "飞行器",
            en_us, "Plane"
    )),
    CONFIGURATION_PLANE_BUTTON("machine_max.configuration.plane.button", ImmutableMap.of(
            zh_cn, "飞行器",
            en_us, "Plane"
    )),
    CONFIGURATION_PLANE_TOOLTIP("machine_max.configuration.plane.tooltip", ImmutableMap.of(
            zh_cn, "飞行器操纵平滑度设置",
            en_us, "Plane control smoothness settings"
    )),
    CONFIGURATION_FULL_PITCH_TIME("machine_max.configuration.full_pitch_time", ImmutableMap.of(
            zh_cn, "俯仰建立时间",
            en_us, "Pitch Ramp-Up Time"
    )),
    CONFIGURATION_FULL_PITCH_TIME_TOOLTIP("machine_max.configuration.full_pitch_time.tooltip", ImmutableMap.of(
            zh_cn, "按下按键后达到满俯仰角所需时间（秒）。越小越快。默认值：0.25。范围：0.05 ~ 99999.0",
            en_us, "Seconds to reach full pitch after pressing the key. Lower = faster. Default: 0.25. Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_FULL_YAW_TIME("machine_max.configuration.full_yaw_time", ImmutableMap.of(
            zh_cn, "偏航建立时间",
            en_us, "Yaw Ramp-Up Time"
    )),
    CONFIGURATION_FULL_YAW_TIME_TOOLTIP("machine_max.configuration.full_yaw_time.tooltip", ImmutableMap.of(
            zh_cn, "按下按键后达到满偏航角所需时间（秒）。越小越快。默认值：0.25。范围：0.05 ~ 99999.0",
            en_us, "Seconds to reach full yaw after pressing the key. Lower = faster. Default: 0.25. Range: 0.05 ~ 99999.0"
    )),
    CONFIGURATION_FULL_ROLL_TIME("machine_max.configuration.full_roll_time", ImmutableMap.of(
            zh_cn, "滚转建立时间",
            en_us, "Roll Ramp-Up Time"
    )),
    CONFIGURATION_FULL_ROLL_TIME_TOOLTIP("machine_max.configuration.full_roll_time.tooltip", ImmutableMap.of(
            zh_cn, "按下按键后达到满滚转角所需时间（秒）。越小越快。默认值：0.25。范围：0.05 ~ 99999.0",
            en_us, "Seconds to reach full roll after pressing the key. Lower = faster. Default: 0.25. Range: 0.05 ~ 99999.0"
    )),
    // 服务器配置
    CONFIGURATION_SHOULD_DESTROY_BLOCKS("machine_max.configuration.should_destroy_blocks", ImmutableMap.of(
            zh_cn, "允许碰撞破坏方块",
            en_us, "Allow Collision Destroy Blocks"
    )),
    CONFIGURATION_SHOULD_DESTROY_BLOCKS_TOOLTIP("machine_max.configuration.should_destroy_blocks.tooltip", ImmutableMap.of(
            zh_cn, "是否允许部件在冲击力足够大时破坏方块。默认值：true",
            en_us, "Whether or not the parts should destroy blocks when the impact is big enough. Default: true"
    )),
    CONFIGURATION_PROJECTILE_DESTROY_BLOCKS("machine_max.configuration.projectile_destroy_blocks", ImmutableMap.of(
            zh_cn, "允许投射物破坏方块",
            en_us, "Allow Projectile Destroy Blocks"
    )),
    CONFIGURATION_PROJECTILE_DESTROY_BLOCKS_TOOLTIP("machine_max.configuration.projectile_destroy_blocks.tooltip", ImmutableMap.of(
            zh_cn, "是否允许投射物在击穿方块时破坏方块。默认值：true",
            en_us, "Whether or not projectiles should destroy blocks when penetrating them. Default: true"
    )),
    CONFIGURATION_IGNORE_ASSEMBLY_TAG_REQUIREMENTS("machine_max.configuration.ignore_assembly_tag_requirements", ImmutableMap.of(
            zh_cn, "忽略组装Tag要求",
            en_us, "Ignore Assembly Tag Requirements"
    )),
    CONFIGURATION_IGNORE_ASSEMBLY_TAG_REQUIREMENTS_TOOLTIP("machine_max.configuration.ignore_assembly_tag_requirements.tooltip", ImmutableMap.of(
            zh_cn, "使用零件组装载具时是否忽视连接点的Tag要求。默认值：false",
            en_us, "Whether to ignore connector tag requirements when assembling vehicles. Default: false"
    )),
    CONFIGURATION_AUTO_OVERRIDE_OFFICIAL_PACK("machine_max.configuration.auto_override_official_pack", ImmutableMap.of(
            zh_cn, "自动覆盖官方内容包",
            en_us, "Auto Override Official Pack"
    )),
    CONFIGURATION_AUTO_OVERRIDE_OFFICIAL_PACK_TOOLTIP("machine_max.configuration.auto_override_official_pack.tooltip", ImmutableMap.of(
            zh_cn, "是否在启动和重载时自动打包并覆盖 machine_max:official 官方内容包。默认值：true",
            en_us, "Whether to auto-pack and override machine_max:official content pack on startup/reload. Default: true"
    )),
    CONFIGURATION_SUBPART_DESTROY_TICKS_PER_DURABILITY("machine_max.configuration.subpart_destroy_ticks_per_durability", ImmutableMap.of(
            zh_cn, "零件销毁每耐久Tick",
            en_us, "SubPart Destroy Ticks Per Durability"
    )),
    CONFIGURATION_SUBPART_DESTROY_TICKS_PER_DURABILITY_TOOLTIP("machine_max.configuration.subpart_destroy_ticks_per_durability.tooltip", ImmutableMap.of(
            zh_cn, "零件损毁后，每1点最大耐久对应的销毁倒计时Tick数。默认值：10。范围：0 ~ 100000",
            en_us, "Destroy timer ticks per 1 max durability point for destroyed sub-parts. Default: 10. Range: 0 ~ 100000"
    )),
    CONFIGURATION_SUBPART_DESTROY_MIN_TICKS("machine_max.configuration.subpart_destroy_min_ticks", ImmutableMap.of(
            zh_cn, "零件销毁最小Tick",
            en_us, "SubPart Destroy Minimum Ticks"
    )),
    CONFIGURATION_SUBPART_DESTROY_MIN_TICKS_TOOLTIP("machine_max.configuration.subpart_destroy_min_ticks.tooltip", ImmutableMap.of(
            zh_cn, "零件损毁后的最小销毁倒计时Tick数。默认值：200。范围：0 ~ 1000000",
            en_us, "Minimum destroy timer ticks for destroyed sub-parts. Default: 200. Range: 0 ~ 1000000"
    )),
    CONFIGURATION_SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE("machine_max.configuration.subpart_destroy_advance_ticks_per_damage", ImmutableMap.of(
            zh_cn, "零件受伤加速销毁Tick",
            en_us, "SubPart Destroy Advance Ticks Per Damage"
    )),
    CONFIGURATION_SUBPART_DESTROY_ADVANCE_TICKS_PER_DAMAGE_TOOLTIP("machine_max.configuration.subpart_destroy_advance_ticks_per_damage.tooltip", ImmutableMap.of(
            zh_cn, "零件已损毁时，每1点伤害额外推进的销毁倒计时Tick数。默认值：20。范围：0 ~ 100000",
            en_us, "Additional destroy timer advance ticks per 1 damage applied to already destroyed sub-parts. Default: 20. Range: 0 ~ 100000"
    )),
    // 伤害类型死亡信息（键为 death.attack.<MMDamageTypes 的 message_id>）
    DEATH_ATTACK_PROJECTILE("death.attack.machine_max.projectile", ImmutableMap.of(
            zh_cn, "%1$s 被炮弹撕成碎片",
            en_us, "%1$s was torn apart by a shell"
    )),
    DEATH_ATTACK_PROJECTILE_PLAYER("death.attack.machine_max.projectile.player", ImmutableMap.of(
            zh_cn, "%1$s 被 %2$s 的炮弹撕成碎片",
            en_us, "%1$s was torn apart by %2$s's shell"
    )),
    DEATH_ATTACK_BLAST("death.attack.machine_max.blast", ImmutableMap.of(
            zh_cn, "%1$s 被炸得支离破碎",
            en_us, "%1$s was blown to pieces"
    )),
    DEATH_ATTACK_BLAST_PLAYER("death.attack.machine_max.blast.player", ImmutableMap.of(
            zh_cn, "%1$s 被 %2$s 炸得支离破碎",
            en_us, "%1$s was blown to pieces by %2$s"
    )),
    DEATH_ATTACK_PART_COLLISION("death.attack.machine_max.part_collision", ImmutableMap.of(
            zh_cn, "%1$s 被碾碎",
            en_us, "%1$s was crushed"
    )),
    DEATH_ATTACK_PART_COLLISION_PLAYER("death.attack.machine_max.part_collision.player", ImmutableMap.of(
            zh_cn, "%1$s 被 %2$s 碾碎",
            en_us, "%1$s was crushed by %2$s"
    )),
    // 研发菜单（仅中文）
    GUI_RESEARCH_RECIPE_ON_HOLD_1("gui.machine_max.research.recipe.on_hold_1", ImmutableMap.of(
            zh_cn, "⏸ 暂停研究"
    )),
    GUI_RESEARCH_RECIPE_ON_HOLD_2("gui.machine_max.research.recipe.on_hold_2", ImmutableMap.of(
            zh_cn, "后续获得的研发点将全部转化为自由研发点"
    )),
    GUI_RESEARCH_RECIPE_START_1("gui.machine_max.research.recipe.start_1", ImmutableMap.of(
            zh_cn, "▶ 开始研究"
    )),
    GUI_RESEARCH_RECIPE_START_2("gui.machine_max.research.recipe.start_2", ImmutableMap.of(
            zh_cn, "获得经验、组装、维修或伤害载具部件以获取研发点"
    )),
    GUI_RESEARCH_RECIPE_CONTINUE("gui.machine_max.research.recipe.continue", ImmutableMap.of(
            zh_cn, "▶ 继续研究"
    )),
    GUI_RESEARCH_RECIPE_CANT_APPLY_FREE_RP_1("gui.machine_max.research.recipe.cant_apply_free_rp_1", ImmutableMap.of(
            zh_cn, "⚠ 无法加速研究"
    )),
    GUI_RESEARCH_RECIPE_CANT_APPLY_FREE_RP_2("gui.machine_max.research.recipe.cant_apply_free_rp_2", ImmutableMap.of(
            zh_cn, "研究项目未开始"
    )),
    GUI_RESEARCH_RECIPE_NO_APPLY_FREE_RP_1("gui.machine_max.research.recipe.no_apply_free_rp_1", ImmutableMap.of(
            zh_cn, "⚠ 无法加速研究"
    )),
    GUI_RESEARCH_RECIPE_NO_APPLY_FREE_RP_2("gui.machine_max.research.recipe.no_apply_free_rp_2", ImmutableMap.of(
            zh_cn, "无可用自由研发点"
    )),
    GUI_RESEARCH_RECIPE_APPLY_FREE_RP_COMPLETE_1("gui.machine_max.research.recipe.apply_free_rp_complete_1", ImmutableMap.of(
            zh_cn, "⏭ 立即完成研究"
    )),
    GUI_RESEARCH_RECIPE_APPLY_FREE_RP_COMPLETE_2("gui.machine_max.research.recipe.apply_free_rp_complete_2", ImmutableMap.of(
            zh_cn, "自由研发点消耗: %1$s"
    )),
    GUI_RESEARCH_RECIPE_APPLY_FREE_RP_1("gui.machine_max.research.recipe.apply_free_rp_1", ImmutableMap.of(
            zh_cn, "⏭ 加速研究"
    )),
    GUI_RESEARCH_RECIPE_APPLY_FREE_RP_2("gui.machine_max.research.recipe.apply_free_rp_2", ImmutableMap.of(
            zh_cn, "预计进度: %1$s/%2$s"
    )),
    GUI_RESEARCH_RECIPE_INSUFFICIENT_MATERIAL_1("gui.machine_max.research.recipe.insufficient_material_1", ImmutableMap.of(
            zh_cn, "⚠ 无法开始研究"
    )),
    GUI_RESEARCH_RECIPE_INSUFFICIENT_MATERIAL_2("gui.machine_max.research.recipe.insufficient_material_2", ImmutableMap.of(
            zh_cn, "材料不足"
    ))
    ;

    public enum Locale {
        zh_cn,
        en_us,
    }

    public final String tag;
    public final Map<Locale, String> content;

    private final org.apache.logging.log4j.Logger log = org.apache.logging.log4j.LogManager.getLogger(MMLanguageRegistry.class);

    public MutableComponent translated() {
        return Component.translatable(tag);
    }

    /**
     * 带英文兜底的可翻译组件，用于内容包装载期抛出的诊断。
     *
     * <p>装载发生在语言表加载之前，纯 {@link Component#translatable} 在日志里只会渲染出键名、
     * 参数全部丢失；这里以 {@code en_us} 文案为兜底，参数照常格式化，日志因此能读到具体是哪个
     * 模型、变体或名字出错。语言表可用时（客户端）仍走正常翻译。</p>
     *
     * @param args 翻译参数，按 {@code %1$s}、{@code %2$s} 顺序填入
     * @return 可翻译组件，键缺失时退化为英文文案
     */
    public MutableComponent component(Object... args) {
        return Component.translatableWithFallback(tag, content.getOrDefault(Locale.en_us, tag), args);
    }

    public static List<LanguageProvider> register(PackOutput output) {
        List<LanguageProvider> li = new ArrayList<>();

        for (Locale locale : Locale.values()) {
            li.add(new LanguageProvider(output, MachineMax.MOD_ID, locale.name()) {
                @Override
                protected void addTranslations() {
                    for (MMLanguageRegistry instance : values()) {
                        String content = instance.content.get(locale);
                        if (content != null) {
                            add(instance.tag, content);
                        }
                    }
                }
            });
        }

        return li;
    }

    public static void check() {
        Map<Locale, Integer> count = new HashMap<>();
        for (MMLanguageRegistry registry : MMLanguageRegistry.values()) {
            for (Locale locale : registry.content.keySet()) {
                count.put(locale, count.getOrDefault(locale, 0) + 1);
            }
        }

        // 二次扫描，低于平均数的说明可能缺少语言翻译进行预警
        for (MMLanguageRegistry registry : MMLanguageRegistry.values()) {
            count.forEach((key, value) -> {
                if (!registry.content.containsKey(key)) {
                    registry.log.warn("{} 缺少翻译语言 {} 的内容", registry, key);
                }
            });
        }
    }

}
