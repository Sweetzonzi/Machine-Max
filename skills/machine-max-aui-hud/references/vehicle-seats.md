# 载具座位与状态组件

内置布局与样式按显示内容及观察方式命名：

| 名称 | 布局／样式资源键 | 显示方式 |
| --- | --- | --- |
| 方位跟随状态图 | `machine_max:vehicle_status_heading` | 圆盘衬底，固定俯角，随观察方位转动 |
| 视角跟随状态图 | `machine_max:vehicle_status_camera` | 开放式布局，随相机方位、俯仰与横滚转动 |
| 固定俯视状态图 | `machine_max:vehicle_status_topdown` | 车头朝上，固定俯视 |
| 席位列表 | `machine_max:vehicle_seat_list` | 逐行显示全部座位、乘员及换座信息 |

三种状态图含模型损伤、整机 HP 与席位标记，席位列表只显示座位相关信息。`machine_max:vehicle_seat_map` 使用方位跟随状态图外观。座位把布局键填入 `views.first_hud_layout`／`views.third_hud_layout`；布局与样式分属两个模块。

名单使用同一套 AUI HUD 重复标记：

```html
<div class="crew-row" data-mm-repeat="vehicle.seats" data-mm-class="seat.state">
  <span data-mm-text="seat.number">--</span>
  <span data-mm-text="seat.name">--</span>
  <span data-mm-text="seat.occupied ? (seat.entity_name == '' ? '…' : seat.entity_name) : '---'">---</span>
</div>
```

`seat.index` 从 0 开始，`seat.number` 从 1 开始。`seat.occupied/available/current/next/pending/failed` 为数值布尔值。`seat.state` 为 `failed/current/pending/next/occupied/empty/unavailable` 之一，按该顺序判断。`seat.entity_name` 是乘员名称，含非玩家实体；缺失为空文本，空位显示 `---` 由页面决定。

区域外使用 `vehicle.seat_count`、`vehicle.seat_name(i)`、`vehicle.seat_occupied(i)`、`vehicle.seat_available(i)`、`vehicle.seat_entity_name(i)`，参数 i 为从 1 开始的座号。观察者查询为 `vehicle.current_seat_number/next_seat_number/pending_seat_number`，换座键为 `vehicle.seat_switch_key`。

`vehicle.seats` 可用于普通和武器 HUD；`seat.*` 只在对应集合内可用。`data-mm-index` 的可选别名从零开始且只读，模板禁止嵌套。座号会随排序改变，绑定器按 `(SubPart ID, 座位键)` 复用行；载具归属变化不影响行身份。

乘员名从本端座位的当前乘员实体读取。已占用但实体引用暂缺时，名称为空文本，页面可显示 `…`。座位归属恢复属于独立工作，此查询依赖现有的乘员绑定。只读显示查询与重复模板不依赖 Spark 通用变量写回修复。

图形页的真实模型直接绘制跨 Part 的损伤与席位。普通 `local.*` 子系统查询仍属于 HUD 宿主 Part，不能用它遍历整车。程序化履带及定制耐久数值应核对实际数据能力，不能用静态演示值填入正式页面。

原生模型标签的 `orientation` 取值为 `fixed`、`heading` 或 `camera`。fixed 为缺省，使用固定 yaw/pitch/roll；heading 跟随观察方位，以 yaw 为偏置并保留页面的 pitch/roll；camera 跟随主相机的完整姿态，包括俯仰和横滚，忽略固定角度。模型与席位标记共用投影。builtin 的方位跟随状态图使用 heading，视角跟随状态图使用 camera，固定俯视状态图使用 fixed。
