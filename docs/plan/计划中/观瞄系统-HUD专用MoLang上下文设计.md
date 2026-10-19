# HUD 专用 MoLang 上下文设计

> 版本 1.5 · 2026-10-18
> **状态**：计划中。代码中尚无 `HudMolangContext` / `WeaponHudMolangContext`；V1 的模型 HUD 元素由 `client/render/renderable/GuiAnimatable.java` 持有样式与布局元素，上下文仍委派给玩家所坐座椅所属 Part 的 `SparkMolangContext`，由 `client/render/gui/hud/CustomHud.java` 驱动。
>
> 本文定义通用 HUD 元素的 `hud.*` 与武器控制器 HUD 元素的 `weapon.*`。样式、布局、元素、玩家覆盖及 AUI 页面绑定见《[界面-HUD样式挂载与布局设计](../进行中/界面-HUD样式挂载与布局设计.md)》；武器状态字段口径见《[武器系统-武器控制器HUD样式与布局设计](武器系统-武器控制器HUD样式与布局设计.md)》。

## 一、对象与上下文的边界

内容包中的 `HudStyle` 是可复用的样式定义；运行时的 HUD 元素是一次具体布局条目。它知道自己的宿主（座椅、相机或武器控制器）、所属 Part/装配体、当前生效的 `HudElement`，并持有自己的 MoLang 上下文。玩家覆盖布局后，元素采用实际生效的元素进行渲染。

```text
运行时通用 HUD 元素（实现 IAnimatable<HudElement>）
  ├─ 宿主、有效元素、相机视角数据
  └─ HudMolangContext                    → hud.*
       ↑
运行时武器 HUD 元素（继承通用元素，直接持有所属武器控制器）
  └─ WeaponHudMolangContext（继承 HudMolangContext）
                                       → hud.* + weapon.*
```

`SparkMolangContext<T>` 要求 `T extends IAnimatable<?>`。因此上下文以实现 `IAnimatable<HudElement>` 的 HUD 元素为关联对象，元素的 `getAnimatable()` 返回自身；宿主是元素上的独立引用，不要求座椅或武器控制器实现 `IAnimatable`。`HudMolangContext` 应直接继承 `SparkMolangContext`，而非继承现有 `MechMolangContext`：后者的泛型绑定到 `IAnimatable<Part>`，不能直接用 HUD 元素调用其 `reset`。可行的类型形态是 `HudMolangContext<E extends HudElement> extends SparkMolangContext<E>`，再由 `WeaponHudMolangContext extends HudMolangContext<WeaponHudElement>` 特化；具体类名在实现时确定。

`IAnimatable#getMolangContext()` 由元素返回它持有的上下文。模型载体用元素上下文计算骨骼动画与文本；AUI 页面绑定层用对应元素的上下文计算页面值。`IAnimatable` 还要求模型与动画控制器等成员；纯 AUI 样式的元素如何满足这些接口成员，应在载体实现中明确，不能假定页面本身就是动画体。现有 `GuiAnimatable` 实现 `IAnimatable<Player>`，`getAnimatable()` 返回玩家；新元素通过独立字段访问宿主及所属 Part，武器元素通过控制器字段取数。

**上下文按元素隔离**：相机边框、座椅仪表、两台武器的分划即使使用同一份样式，也分别持有上下文。`CustomHud` 可管理元素集合，但不持有供所有元素轮流改写的全局可变上下文。Spark 的 `MolangContextRegistry` 按“上下文类、表达式”缓存编译结果，实例之间可复用；它用上下文类的无参构造创建编译原型，因此两类上下文都须有可访问的无参构造。武器子类继承的 `hud.*` 绑定在编译时仍可见。

现有 `MMGuiManager#onAnimTick` 与 `#onPhysicsTick` 分别在客户端主线程和物理线程推进 HUD 动画；Spark 的 `JSMolangValue` 会对返回的上下文执行 `reset(holder, animTime)`。因此同一元素的可变上下文也不能在两条线程上同时求值。实现时按元素、按消费线程缓存上下文实例，两份实例读取同一份原子发布的不可变视角/布局及武器快照；`getMolangContext()` 返回当前求值线程对应的实例。文本与 AUI 在客户端主线程求值，物理线程上的动画表达式只读已发布快照。该规则不产生第二套 HUD 元素或武器控制器。

载具 HP、能量和通用信号属于 `global.*` / `local.*` 的长期查询面：现有官方 HUD 已用 `global.get('vehicle_speed')`。HUD 上下文须显式关联所属 Part 与装配体，并转发这两组查询，或把公共查询实现抽取为多个上下文共用的代码；跨物理/主线程读取时须采用已发布的数据，不能直接读取另一线程正在修改的 Part 状态。该能力不能靠继承 `MechMolangContext` 自动获得。`math.*` 是 Spark 的通用词表；新增的 HUD 专用查询只建立 `hud.*` 和 `weapon.*` 两个前缀。

## 二、`hud.*`：所有 HUD 元素共有

`hud.*` 只提供屏幕 HUD 普遍需要的**当前视角与视口**信息。座椅、相机、武器控制器持有的元素都读取玩家正在看到的相机；这里的“相机”不是相机子系统的 locator。表中查询均无参数，数值均在客户端渲染帧采样。

| 名字 | 参数 | 用途说明 |
| --- | --- | --- |
| `hud.camera_pos_x` | 无 | 当前渲染相机的世界 X 坐标；第三人称取实际镜头位置 |
| `hud.camera_pos_y` | 无 | 当前渲染相机的世界 Y 坐标，可显示镜头高度 |
| `hud.camera_pos_z` | 无 | 当前渲染相机的世界 Z 坐标；第三人称取实际镜头位置 |
| `hud.view_heading_deg` | 无 | 当前视线的水平航向，北为 0°、向东顺时针增加，范围 `[0, 360)`；电子罗盘或航向带可直接使用 |
| `hud.view_pitch_deg` | 无 | 当前视线俯仰角，度，抬头为正；用于俯仰刻度和姿态指示 |
| `hud.view_roll_deg` | 无 | 当前视线横滚角，度，顺时针为正；用于地平仪或倾斜提示 |
| `hud.fov_deg` | 无 | 当前渲染视角的垂直 FOV，度；用于视野相关刻度 |
| `hud.current_zoom` | 无 | 当前炮镜变焦倍率，1 为基准；普通视角为 1 |
| `hud.screen_width` | 无 | HUD GUI 的逻辑像素宽度，用于自适应排版；不是物理窗口像素 |
| `hud.screen_height` | 无 | HUD GUI 的逻辑像素高度，用于自适应排版；不是物理窗口像素 |

航向、俯仰和横滚取玩家**最终看到的视角**，包括稳定器和相机偏转的结果；载具车体、炮塔或相机 locator 的姿态不是这组查询的含义。内容包若要显示车体朝向，应另定义明确的装配体参考轴，不能复用 `hud.view_heading_deg`。摄像机旋转与变焦在渲染帧变化，因此按客户端 tick 更新会让电子罗盘与视野刻度跳动；实现须在这些表达式求值前提供同一渲染帧的视角快照。

装配体 HP 和能量继续使用 `global.hp` / `global.max_hp`、`global.energy` / `global.max_energy`。速度可继续从既有信号 `global.get('vehicle_speed')` 读取；该信号由汽车控制器提供有符号前向速度，并非所有装配体的通用速度。若增加直接的 `global.speed_mps`，应先定义为装配体速度模长及非载具装配体的取值，再在 `global.*` 中发布，不放进 `hud.*`。

## 三、`weapon.*`：所属武器控制器

只有武器控制器持有的 HUD 元素使用 `WeaponHudMolangContext`。它直接引用自己的 `WeaponControllerSubsystem`，按每客户端 tick 的武器显示快照提供名称、状态、弹药、发射管与弹链。具体字段和取数口径在《[武器系统-武器控制器HUD样式与布局设计](武器系统-武器控制器HUD样式与布局设计.md)》§二、§三定义。

武器业务查询统一收在 `weapon.*` 下：单值如 `weapon.ammo_remaining`，列表项如 `weapon.tube_fill(i)`、`weapon.belt_remaining(i)`。无参查询优先写成属性，带索引或距离的查询写成函数。普通 HUD 元素不编译武器表达式；绑定层应提示作者把武器表达式放在武器控制器的 HUD 样式中，避免未绑定查询静默退回 0。

### 3.1 炮镜下的分划

相机宿主自己的 `scope` HUD 元素提供边框、遮罩等基础显示。弹道分划是武器控制器 `scope_hud_layout` 中的元素。该元素从**所属控制器**确定当前弹药和弹道参数，不从座椅查找控制器，也不由相机反查武器。

| 查询 | 返回 | 口径 |
| --- | --- | --- |
| `weapon.active_ammo` | string | 所属控制器当前用于显示/分划的弹药注册键；没有时为空 |
| `weapon.bdc_hold_rad(distance_m)` | number | 该控制器当前弹道在给定距离的 hold-over 角，弧度；无可用弹道数据时为 0 |

武器快照中“当前弹药”按本控制器发射管的稳定显示顺序选取，与弹种槽一致。多发射管且弹种不同的型号如需逐管分划，应在 `weapon.*` 中增加显式管索引查询，不能隐式借用座椅的控制器顺序。弹道查表的缓存键须包含影响结果的弹药与发射/归零参数，实际输入由 BallisticsFramework 接口和武器数据确定。

弹道缓存按控制器实际使用的弹药及弹道参数生成距离到 hold-over 角的表；相邻采样点之间插值，避免刻度在每个采样距离处跳动。`weapon.bdc_hold_rad` 对非有限、负距离或无可用弹道数据返回 0；若样式必须区分“无需修正”与“没有数据”，再增加独立的可用性查询。缓存的建立和失效发生在内容包重载或影响弹道的参数变化时，不在逐帧表达式里运行弹道求解。

`weapon.bdc_hold_rad` 返回弹道角，具体样式如何把角度标定为分划模型的骨骼位置由该样式决定；通用 `hud.*` 不提供模型坐标换算查询。

同一武器元素也可以在炮镜视角显示弹药文本；相机边框元素只有 `hud.*`，不读取 `weapon.*`。若两台控制器都为 `scope` 组配置分划，它们各自显示自己的元素；是否同时显示由座椅选中集合、布局与控制器激活规则决定。

## 四、填充与求值时机

| 时机 | 动作 |
| --- | --- |
| 创建运行时元素 | 确定宿主和有效元素，创建当前消费线程的上下文；绑定所属 Part/装配体 |
| 每客户端 tick | 武器元素更新本控制器显示快照；AUI 页面按该元素上下文更新武器数据槽 |
| 每渲染帧采样后、消费 `hud.*` 的表达式求值前 | 发布该帧的相机视角与视口快照；当前线程上下文按 Spark 契约 `reset` 后读取已发布快照 |
| 每次模型文本渲染前 | 将客户端主线程上下文 `reset` 到当前元素，读取该渲染帧的视角快照，再计算 `molang_args` |
| 玩家布局修改、视角切换或内容包热重载 | 更新有效布局或重建受影响元素，再按新的上下文/样式编译或取缓存 |

逐帧视角快照必须早于消费 `hud.*` 的表达式求值。现有物理 tick 与 GUI 渲染的先后顺序不足以保证骨骼表达式使用本次渲染的 `partialTick` 值；实现时须让依赖实时航向的 HUD 骨骼表达式在渲染阶段求值，或接受并验证上一份快照的延迟。AUI 的“按发射管数量自动生成多段界面”属于页面绑定层，和 `weapon.tube_fill(i)` 的索引传递另在框架文档 §八说明，不改变上下文类层次。

## 五、实施与验收

1. 建立通用/武器 HUD 元素和对应上下文类，先让现有模型 HUD 的 `molang_args` 使用每元素上下文；让 `local.*` / `global.*` 在 HUD 上下文中保持可用。
2. 建立武器显示快照与 `weapon.*` 查询；炮镜边框由相机布局提供，分划由武器控制器布局的 `scope` 组提供。
3. AUI 绑定层按元素关联对应上下文；页面重复条目的具体索引规则在 AUI 实现阶段确定。
4. 采集并发布渲染帧视角快照，校验模型与 AUI 两种载体、视角切换和热重载。

验收至少覆盖：同一座位下两台控制器的分划/弹药互不串值；炮镜边框无武器查询；电子罗盘跟随渲染帧视线平滑转动且不误读车体朝向；第三人称坐标取实际镜头位置；模型骨骼和文字各在线程实例上读取同一元素发布的快照；现有官方 HUD 的 `global.get('vehicle_speed')` 仍能求值。本文是设计文档，代码尚未实现，因此不以现有运行结果宣称这些用例通过。

## 六、相关代码

| 当前代码 | 作用 |
| --- | --- |
| `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/animation/IAnimatable.kt#getMolangContext` | 动画体提供自己的上下文 |
| `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/SparkMolangContext.java#reset` | 上下文关联动画体 |
| `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/MolangContextRegistry.java#getOrCreateEngine` | 无参原型与按上下文类缓存编译结果 |
| `src/main/java/io/github/sweetzonzi/machine_max/common/mech/molang/MechMolangContext.java#localGet` | Part 上下文的现有 `local.*` / `global.*` 查询 |
| `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/GuiAnimatable.java#getMolangContext` | 当前模型 HUD 读取 Part 上下文的路径 |
| `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#syncHuds` | 当前模型 HUD 的创建与同步 |

## 七、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-06-15 | 提出 HUD 渲染参数、弹道分划查询与模型 HUD 接线 |
| 1.1 | 2026-10-16 | 补充模型 HUD 与武器 HUD 计划之间的引用关系 |
| 1.2 | 2026-10-16 | 定义通用/武器元素及上下文继承、`hud.*` / `weapon.*` 词表、武器控制器持有的分划和逐元素求值时机 |
| 1.3 | 2026-10-17 | 按实际显示需求收缩 `hud.*` 至视角与视口查询；载具仪表沿用 `global.*`，分划骨骼换算由样式负责 |
| 1.4 | 2026-10-17 | 运行时对象表述改为布局条目；布局与元素的术语对齐框架文档 |
| 1.5 | 2026-10-18 | 武器分划的可见性补座椅选中集合；与无主/副的 HUD 可见性规则对齐 |
