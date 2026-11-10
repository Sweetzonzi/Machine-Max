# 载具模型与座位 HUD 的 AUI 接入设计

> **状态**：已实现。HUD 布局条目、模型标签、座位查询及示例资源已接入。离线单元测试与内容包引用检查通过，游戏内画面和性能待验收。

## 1. 现状与目标

内容包通过常规 HUD 布局选择投放座位图。AUI 负责页面排版与文本，`<machinemax-vehicle-model>` 绘制实时模型和可选的编号标记，MoLang 提供与页面尺寸和观察角独立的三维座位数据。模型部件数和座位数由宿主所属装配体决定。纯文字页面可以独立使用座位查询。

座位图的用途包括观察座位分布、占用、可换入性、当前座位和按键候选，以及总览零件和内构损伤。内置布局把面板锚定在左下角，内容包可按本车仪表和画面位置调整。
## 2. 挂载与资源契约

座位图是一条显式声明的普通 `HudElement`，样式类型为 `machine_max:aui_hud`。沿用宿主 `IHudHost.hudLayoutFor` → `CustomHud` → `AuiHudAnimatable`。内容包使用既有布局引用字段，通过条目的 `anchor`、`offset` 和 `scale` 投放。首版模型标签要求 `perspective: false`。这是 HUD 页面在屏幕上的投影模式，车体仍可在页面内做三轴旋转。

| 宿主 | 内容包入口 |
| --- | --- |
| 座椅 | 型号 `views.first_hud_layout` / `third_hud_layout`，或安装位同名动态属性覆盖。 |
| 相机、瞄具 | 宿主的 `scope_hud_layout`。炮镜中的正交 AUI 条目按自身锚点投放。 |
| 武器控制器 | `first_hud_layout`、`third_hud_layout`、`scope_hud_layout`。条目受现有 `selection_visibility` 调度。 |

提供内置页面 `machine_max/vehicle_seat_map/index.html`、样式 `machine_max:vehicle_seat_map` 和同名独立示例布局。作者可引用该布局，或在自己的布局数组中加入一个条目，与现有仪表共存。以下是条目示例：

```json
{
  "style": "machine_max:vehicle_seat_map",
  "perspective": false,
  "anchor": "bottom_left",
  "offset": [8, -22, 0],
  "scale": [2, 2, 1]
}
```

布局省略该条目，或对应视角使用 `machine_max:empty`、空数组时，该宿主不显示座位图。随附内容包的座椅在第三人称显式挂载座位图。带仪表的布局保留原条目，并为共用第一人称或炮镜布局的座椅提供第三人称专用布局。例如 `type59:type59_ground_orientation` 的座位图与耐久、方位和仪表条目共存，见 `src/main/resources/spark_modules/Machine-Max_Type59/type59/hud_layouts/type59_ground_orientation.json`。没有其他仪表的座椅可直接引用 `machine_max:vehicle_seat_map`。缺布局、缺样式或页面创建失败沿相应加载器的单次诊断跳过资源或条目。

内置页面通过 `data-mm-class` 消费 `vehicle.seat_map_visible`。显示要求当前玩家坐在同一装配体、至少两座、无打开的界面、GUI 可见、非相机模式，并满足玩家的常驻、换座窗口或关闭设置。换座窗口按请求在途或成功脉冲判定，见 `src/main/java/io/github/sweetzonzi/machine_max/client/MMClientConfig.java#isSeatMapVisible`。该查询只控制消费它的页面。作者可使用自定义显隐条件，例如单座车体仪表或炮镜页面。损伤层设置适用于标签中显式启用的损伤渲染，详见 §5。

挂载与加载入口见 `src/main/java/io/github/sweetzonzi/machine_max/common/visual/IHudHost.java#resolve`、`src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/SeatSubsystem.java#hudLayoutFor`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#rebuild`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#renderInScopeMode` 和 `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/AuiHudAnimatable.java#createDocument`。通用资源契约见 `docs/plan/进行中/界面-HUD样式挂载与布局设计.md` §3.2、§4、§6.1。原生换座与座位图的设计及验收见 `docs/plan/进行中/界面-车内换座与座位布局HUD调研.md` §五、§5.7、§七。

## 3. 座位数据与 MoLang

### 3.1 来源与发布

装配体状态以 **HUD 宿主所属的 VehicleCore** 为准。座椅、相机、瞄具和武器控制器均从各自 Part 解析装配体。观察者状态以 **本地玩家正在控制的座椅** 为准，只有它和宿主属于同一装配体时才提供当前、下一候选、在途和失败目标。控制组切换、挂到相机或 WC 都不会把宿主子系统解释成观察者座椅。

`CustomHud.tick` 按活跃布局的宿主解析载具，每个客户端 tick 每台载具采集一次座位和观察者数据，供各宿主条目共享。不可变的 `HudVehicleSnapshot` 发布到 `HudRuntimeElement.Frame`。其中保存装配体身份、座位键与编号、局部位置、占用与可用性、观察者目标及显示门槛；MoLang 和模型标签的标记都消费该快照。结构缓存持有参考子零件和用于绘制的零件引用，留在客户端路径。现有 `HudPartSnapshot` 保持 Part 级 `local.*` / `global.*` 数据。`HudExpression` 校验 `vehicle` 命名空间，基类 `HudMolangContext` 登记数值与字符串查询。未知查询或参数个数错误按现有槽位诊断处理。参考 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudRuntimeElement.java#Frame`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudExpression.java#validate`。

### 3.2 参考系、编号与中心

车体内部坐标取参考子零件的局部轴，`+X` 向右，`+Y` 向上，`−Z` 向前。`SeatOrder.layout` 执行排序和参考件选择，客户端以 `SeatMapLayout.Snapshot` 缓存结果。该快照测量三维碰撞包围盒。将所有碰撞形状可解析的子零件包围盒角点转换到参考系后合并，取中心作为座位坐标原点和模型旋转中心。缓存按当前 Level 和装配体实例区分，结构变化后在下一次取用前重算。车体运动和活动件动画只更新采样位置，中心保持固定在该次结构缓存中。若无有限包围盒，坐标原点取参考子零件原点，标签跳过依赖包围盒的几何绘制。没有参考件时三维位置不可用。

先将座位 locator 变换到参考子零件的局部空间，再减去缓存中心。若差为 `(x,y,z)`，查询值为 `(x,y,-z)`，单位为方块。先检查 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/attr/SubPartAttr.java#locatorTransforms`，再验证局部和最终位置均有限。解析失败不得把刚体原点记成有效位置。整车刚性运动不改变固定座位的读数，活动件上的座位随其 locator 更新。

座次取自同一份缓存的有序列表，内部身份为装配体内的 `subPartId + seatName`。座位序号从 **1** 开始，对应可见编号。武器显式索引从 0 开始。零、负数、非整数、非有限值或超出列表范围时，返回 0 或空字符串。对应实现见 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/seat/SeatOrder.java#layout`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/seat/SeatMapLayout.java#measure`、`src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java#getLerpedLocatorWorldTransform`。

### 3.3 查询表

| 查询 | 返回值 |
| --- | --- |
| `vehicle.seat_count` | 装配体的座位数量。无载具时为 0。 |
| `vehicle.seat_right(i)`、`vehicle.seat_up(i)`、`vehicle.seat_forward(i)` | 带符号的三维距离。无有效位置时为 0。 |
| `vehicle.seat_has_position(i)` | 存在有效位置时为 1，否则为 0。用于区分缺失与合法的 `(0,0,0)`。 |
| `vehicle.seat_name(i)` | 座位子系统名对应的本地化名称，缺翻译时使用键本身。无对应座位时为空字符串。 |
| `vehicle.seat_occupied(i)`、`vehicle.seat_available(i)` | 占用和可换入性，分别读同步字段和 `SeatOrder.available`。无对应座位时为 0。 |
| `vehicle.current_seat_number`、`vehicle.next_seat_number`、`vehicle.pending_seat_number` | 当前座位、按键的下一候选、实际在途目标的编号。无对应座位时为 0。 |
| `vehicle.seat_switch_key` | 玩家换座键的本地化显示名。宿主无载具时为空字符串。 |
| `vehicle.seat_map_visible` | §2 定义的内置页显示门槛，成立时为 1。 |

首版用计数和按序号的查询表达列表。动态名单有实际需求时，可将同一批数据接入 `data-mm-repeat`。现行重复器接受武器发射管和弹链，见 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudPageBinding.java#Region`。

## 4. 换座与反馈

客户端以结构缓存的有序列表作为 HUD 和按键的共同顺序来源。按键按实时 `SeatOrder.available` 扫描可用座位，服务端校验请求中的实际座位键。

快照使用 `SeatSwitchFeedback.pendingTarget()` 记录等待目标。候选变化时，等待与失败标记指向所请求的座位。失败目标以装配体与座位键匹配，成功脉冲指向已确认的当前座位。成功提示使用同一份有序列表显示编号。切换装配体、下车或退出连接时清理相应反馈与缓存。结构变动后不在当前布局中的目标不绘制标记。按键、actionbar 和服务器校验独立于 AUI 页面。来源见 `src/main/java/io/github/sweetzonzi/machine_max/client/input/SeatSwitchClient.java#requestCycle`、`src/main/java/io/github/sweetzonzi/machine_max/client/input/SeatSwitchClient.java#successMessage`、`src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/seat/SeatSwitchFeedback.java#pendingTarget` 和 `src/main/java/io/github/sweetzonzi/machine_max/network/payload/SeatSwitchPayload.java#serverHandler`。

## 5. 模型标签

`<machinemax-vehicle-model>` 是客户端块级绘制元素，使用 MM 专用名称和明确的 CSS 尺寸。通用 HUD 设计 §8.2 对行内文本扩展的评估适用于文本绑定，文本继续使用 `data-mm-*`。模型需要独立内容框与自绘钩子，AUI 对自定义标签的块级缺省行为正合适。AUI 1.3.0 内置 `<model>` 支持玩家及按注册类型创建的实体预览。MM 标签复用 AUI 自定义元素接口，同时保留 AUI 内置预览服务供其他页面使用。通用接口见 [AUI 二次开发文档](https://doc.sighs.cc/ApricityUI/guide/secondary-development/)。

| 属性 | 缺省及含义 |
| --- | --- |
| `yaw`、`pitch`、`roll` | 缺省为 `0, 90, 0` 度，得到车头朝上的俯视。零姿态从车后沿 −Z 看向车头。对车体向量依次应用绕 +Y、+X、+Z 的右手旋转，支持任意观察角。缺失或非有限的单个角度取该属性的缺省值。 |
| `markers` | 缺省 `false`。设为 `true` 时绘制带编号的座位标记。 |
| `damage` | 缺省 `false`。设为 `true` 且玩家允许损伤层时，绘制零件与内构损伤染色。 |

角度属于标签的观察变换，整车相对地平线的姿态属于独立仪表数据。常驻 HUD 由内容包设角度，鼠标拖动可在有鼠标焦点的界面另行接入。

**绘制上下文。** 客户端用 `Element.register` 注册，构造阶段只注册标签身份，绘制时读取标签属性。`AuiHudAnimatable.renderGui` 在调用 `Base.drawEmbeddedDocument` 时开启限于本次绘制的作用域，提供当前 `Document`、`HudRuntimeElement` 与传入的 `partialTick`，退出时恢复作用域。标签在绘制时匹配自己的 Document，从 runtime 取得宿主与本轮快照；预热、其他文档或无匹配上下文时只画普通元素外观。文档重建和 `/reload` 后，下一次绘制使用对应文档的作用域，无需持久注册表。实现见 `src/main/java/io/github/sweetzonzi/machine_max/client/MachineMaxClient.java#registerAuiElements`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/element/VehicleModelElement.java#drawPhase`、`src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/AuiHudAnimatable.java#renderGui`。

**共享投影。** 将缓存包围盒角点按标签角度旋转，在标签内容区等比适配，以缓存中心为旋转与摆放中心。模型几何与有效座位 locator 使用同一渲染帧参考件、`partialTick`、旋转和投影矩阵。标记按快照中的座位键关联编号与状态，遮挡的座位也可读。结构缓存与已发布快照错开时跳过无法匹配的标记，待下一次发布对齐。页面不接收模型框投影百分比，故内容区尺寸的计算不会反向依赖座位查询。

**绘制顺序。** BODY 阶段绘制元素底色，提交 AUI 本地批次，再提交外壳和内构，分别结束其批次以保证覆盖顺序。几何在标签内容框内裁剪，标记底板与编号通过 AUI 1.3.0 的 `Graph.drawFillRect` 与 `FontDrawer.drawFont(PoseStack, Text, Position)` 绘制于几何之上。外壳使用现有 `SOLID_XRAY` 的无深度测试和仅颜色写入。标记使用略高于背景的深度。位姿、裁剪和缓冲在 `finally` 恢复。外壳渲染类型见 `src/main/java/io/github/sweetzonzi/machine_max/client/render/MMRenderTypes.java#SOLID_XRAY`。

**退化。** 零尺寸、未就绪宿主、暂缺模型或无可用包围盒时跳过受影响的几何。页面文字和其他元素可继续绘制。模型绘制隔离运行时异常，按样式和故障类型去重记录，避免坏模型中止整页。内置页用 AUI 的 `<translation>` 渲染 `seat_map.legend.*` 图例，动态文本使用本地化查询和 `--` 等占位。

## 6. 验收

- `SeatMapProjectionTest` 覆盖包围盒中心、俯视、侧视、斜视及整车刚性位姿不变量。`HudMolangContextTest` 与 `HudPageBindingTest` 覆盖一基索引、无效参数、缺失位置与合法原点、共享发布帧、页面显隐和未知查询诊断。用例在 `src/test/java/io/github/sweetzonzi/machine_max/client/render/gui/`。
- 游戏内检查各车的第三人称仪表共存、不同 GUI scale、重载、上下车和玩家设置。使用活动件、多高度座位和可拆装的自组车核对模型与标记对齐、结构缓存失效、换座时的占用竞争和失败反馈。将条目投放到相机与武器控制器的自定义布局，检查宿主身份、显隐和空布局。
- 游戏内检查模型内容框裁剪、编号字体、模型与内构透明层以及相邻 AUI 内容的提交顺序。
- 性能验收使用同一场景、分辨率、GUI scale、视角与车辆，预热后测条目显示和隐藏时的 HUD CPU 耗时及整帧时间。稳态帧时间增量的目标为 **P95 < 1.5 ms**，预算取自 [车内换座与座位布局 HUD 调研](../进行中/界面-车内换座与座位布局HUD调研.md) §七。座位坐标和几何不写入 DOM，文字和显隐仅在绑定值变化时写入。帧耗时待测量。

内容包入口和查询表见 [HUD 编写指南](../../../src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/docs/zh_cn/hud.md) 的「载具模型与座位图」一节。
