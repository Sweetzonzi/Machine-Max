# 载具 HUD：状态图与席位列表

四种样式均位于 builtin 包，配套的 `huds/` 样式键与 `hud_layouts/` 布局键同名：

| 名称 | 资源键 | 内容 |
| --- | --- | --- |
| 方位跟随状态图 | `machine_max:vehicle_status_heading` | 圆盘衬底、固定 70° 俯角，模型随观察方位转动 |
| 视角跟随状态图 | `machine_max:vehicle_status_camera` | 开放式布局，模型跟随实际相机的方位、俯仰及横滚 |
| 固定俯视状态图 | `machine_max:vehicle_status_topdown` | 车头朝上的固定俯视模型，紧凑显示 |
| 席位列表 | `machine_max:vehicle_seat_list` | 全部座位的名称、乘员名称、当前／下个目标，无载具模型 |

三种状态图显示零件／子系统损伤、整机 HP 与席位标记；席位列表只显示座位、乘员及换座信息。整机 HP 来自 `global.hp/global.max_hp`，不是子系统耐久的平均值。四个页面共用 `documents/vehicle_hud/` 中的样式表。

`machine_max:vehicle_seat_map` 使用方位跟随状态图外观，沿其资源引用接入的载具会显示此样式。四个独立布局默认位于左下角。

状态图与席位列表的布局缩放均为 `scale: [1, 1, 1]`。组合仪表中仅这些条目使用此缩放，各仪表的尺寸由自己的布局条目决定。

HP 文字位于条上方，按 `当前值/最大值` 显示整数读数，例如 `2300/2500`，分别取 `global.hp` 与 `global.max_hp`。最大值未知时显示 `--/--`；填充长度按当前值与最大值的比例限制在 0～100%。现代观瞄的独立车体耐久条使用相同读数格式。

座位在视角配置中选择布局，例如：

```json
{
  "first_hud_layout": "machine_max:vehicle_status_heading",
  "third_hud_layout": "machine_max:vehicle_seat_list"
}
```

这些字段属于座位的 `views` 对象。独立布局可直接替换；组合仪表应把相应样式条目合入所用布局数组，避免叠放两套座位图。

## 第一方载具的默认接入

| 载具 | 第一人称 | 第三人称 |
| --- | --- | --- |
| 民用多座载具 | 固定俯视状态图 | 席位列表 |
| 军用载具 | 方位跟随状态图 | 方位跟随状态图 |

民用驾驶座通过 `seat_dashboard`／`seat_dashboard_2` 及其第三人称布局组合仪表与上述元素，乘客座直接引用独立布局。R700、VN、SZN 的整车预设各有一个座位，状态图与席位列表受 `vehicle.seat_map_visible` 的至少两座条件限制，不显示这些元素。

军用组合布局中的方向指示、仪表与方位跟随状态图分别为独立条目。座位型号决定可用视角，HUD 配置不启用型号关闭的第一人称。Sd.Kfz. 234/2 默认使用方位跟随状态图。

## 动态名单

页面通过已有的 `data-mm-repeat` 绑定器展开 `vehicle.seats`。每个座位的稳定身份为 `(SubPart ID, 座位键)`，座号仅表示本轮排序。行的占用、名称或载具归属变化不会更换行身份。

```html
<div data-mm-repeat="vehicle.seats" data-mm-class="seat.state">
  <span data-mm-text="seat.number">--</span>
  <span data-mm-text="seat.name">--</span>
  <span data-mm-text="seat.occupied ? (seat.entity_name == '' ? '…' : seat.entity_name) : '---'">---</span>
</div>
```

| 当前项查询 | 口径 |
| --- | --- |
| `seat.index`／`seat.number` | 零基下标／从 1 开始的座号 |
| `seat.name` | 座位的本地化名称 |
| `seat.entity_name` | 当前乘员实体的名称，含非玩家乘员；未占用或本端实体引用暂缺时为空文本 |
| `seat.occupied`／`seat.available` | 是否占用／当前可否换入；二者不是互相取反 |
| `seat.current`／`seat.next`／`seat.pending` | 当前座位／下个候选／请求在途目标 |
| `seat.failed` | 此座位是否有换座失败反馈 |
| `seat.state` | `failed/current/pending/next/occupied/empty/unavailable` 中的一项，按此顺序确定 |

显式索引查询 `vehicle.seat_count`、`vehicle.seat_name(i)`、`vehicle.seat_occupied(i)`、`vehicle.seat_available(i)` 和 `vehicle.seat_entity_name(i)` 可用于重复项外。座号 `i` 从 1 开始，无效索引返回 0 或空文本。`seat.*` 仅在 `vehicle.seats` 及其后代可用；可选 `data-mm-index` 别名从零开始且只读。集合可用于座位、瞄具和武器 HUD，武器集合保持其武器宿主约束。

乘员名直接从本端座位的 `passenger.getName()` 读取，随实体的名称和自定义名称变化。空位为 `---`；已占用但乘员实体引用暂缺时为 `…`。座位归属恢复属于独立工作，本组件依赖当前已有的乘员绑定。

## 可见性和真实模型

四个页面使用现有 `vehicle.seat_map_visible` 门槛：同车观察者、至少两座、无打开界面、GUI 可见、非相机模式，并服从玩家设置的常驻／换座窗口。作者可在自定义页面选择其他门槛。

图形页使用 `<machinemax-vehicle-model>` 的实时整车几何，保留当前／下个／占用／空位标记与零件、子系统损伤层。模型绘制受现有损伤层设置控制。程序化履带几何及单独的履带／子系统数值条属于独立接入项。

模型标签的 `orientation` 决定观察方式：缺省或 `fixed` 使用固定 `yaw/pitch/roll`；`heading` 仅随相机方位改变 yaw，保留页面的 pitch/roll，yaw 可作为方位偏置；`camera` 直接使用主相机相对于载具的完整姿态，忽略固定角度属性。模型和座位标记使用同一投影，每渲染帧采样相机和载具插值位姿。NeoForge 的相机四元数已包含横滚，绘制直接使用该四元数。

三种模式均为按包围盒自适应的正交缩略图。`camera` 跟随朝向，不随镜头距离或 FOV 改变大小；`heading` 的局部方位无定义时保持上一帧角度。

## 验证

回归覆盖名单增减／重排、载具归属变化时的行复用、姓名变化、只读下标、普通／武器 HUD 的集合边界，以及四页通过 AUI 解析和绑定。服务端 GameTest 覆盖乘员实体改名的即时读取、实体引用缺失及离座时的空值。客户端的不同 GUI scale、日夜背景、拆装和重载画面需要实机验收。
