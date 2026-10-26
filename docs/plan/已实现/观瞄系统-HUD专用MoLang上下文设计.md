# HUD 专用 MoLang 上下文设计

> 版本 1.9 · 2026-10-22
> **状态**：已实现。元素上下文、武器业务快照、当前项作用域与 AUI 绑定均已落地；实机画面验收另行跟踪。代码坐标、执行线程、标准弹道标定范围和已执行测试见《[HUD 上下文实现与验收](HUD上下文实现与验收.md)》。
>
> 本文定义通用 HUD 元素的 `hud.*` 与武器控制器 HUD 元素的 `weapon.*`，以及当前发射管/弹链作用域的 `tube.*` / `belt.*`。样式、布局、元素、玩家覆盖及 AUI 页面绑定见《[界面-HUD样式挂载与布局设计](../进行中/界面-HUD样式挂载与布局设计.md)》；武器状态字段口径见《[武器系统-武器控制器HUD样式与布局设计](武器系统-武器控制器HUD样式与布局设计.md)》。

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
       └─ 逐数据槽安装的当前项作用域      → tube.* 或 belt.*
```

`SparkMolangContext<T>` 要求 `T extends IAnimatable<?>`。代码采用 `HudRuntimeElement implements IAnimatable<HudElement>`，其中 `HudElement` 是布局配置 record；武器身份类 `WeaponHudElement` 继承 `HudRuntimeElement` 并持有所属控制器。`HudMolangContext extends SparkMolangContext<HudRuntimeElement>`，`WeaponHudMolangContext` 继承通用上下文。`getAnimatable()` 返回本元素的布局配置，宿主通过独立引用访问。`MechMolangContext` 绑定 `IAnimatable<Part>`，不能用作 HUD 运行时身份的上下文。

模型载体 `GuiAnimatable` 与页面载体 `AuiHudAnimatable` 持有各自的 `HudRuntimeElement`；模型与动画控制器由该运行时身份提供。纯 AUI 元素使用空模型，Document 自身不是动画体。模型骨骼、文本和页面数据槽都向本元素取上下文。

**上下文按元素隔离**：相机边框、座椅仪表、两台武器的分划即使使用同一份样式，也分别持有上下文。`CustomHud` 可管理元素集合，但不持有供所有元素轮流改写的全局可变上下文。Spark 的 `MolangContextRegistry` 按“上下文类、表达式”缓存编译结果，实例之间可复用；它用上下文类的无参构造创建编译原型，因此两类上下文都须有可访问的无参构造。武器子类继承的 `hud.*` 绑定在编译时仍可见。

模型 HUD 的 `HudRuntimeElement#getAnimLevel` 返回宿主所属客户端 Level，Spark 通过该 Level 取得物理级别。`GuiAnimatable#physicsTick` 在匹配 Level 的客户端物理步固定一份输入快照并调用 `AnimController#physTick`；`GuiAnimatable#animTick` 调用 `AnimController#tick`，经 `BonePose#setChanged` 发布姿态。模型与附着文本位置使用 partialTick 插值。文本表达式与 AUI 在客户端主线程求值。上下文按元素、消费线程缓存，物理步的 `HudRuntimeElement#pinFrame` 保证多次 `reset` 使用同一份不可变求值帧；各线程使用独立的可变上下文实例。代码及测试范围见实现记录。

载具 HP、能量和通用信号属于 `global.*` / `local.*` 的长期查询面：现有官方 HUD 已用 `global.get('vehicle_speed')`。HUD 上下文须显式关联所属 Part 与装配体，并转发这两组查询，或把公共查询实现抽取为多个上下文共用的代码；跨物理/主线程读取时须采用已发布的数据，不能直接读取另一线程正在修改的 Part 状态。该能力不能靠继承 `MechMolangContext` 自动获得。`math.*` 是 Spark 的通用词表；HUD 查询按职责使用 `hud.*`、`weapon.*`，当前发射管/弹链分别使用 `tube.*` / `belt.*`。后二者要求绑定器显式安装当前项作用域，不表示 MoLang 任意对象别名。

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

整台控制器与显式索引查询收在 `weapon.*` 下，如 `weapon.ammo_remaining`、`weapon.tube_fill(i)`、`weapon.belt_remaining(i)`。当前项查询位于 `tube.*` / `belt.*`，例如 `tube.fill`、`belt.remaining`；词表及返回口径见武器文档 §三、§3.2。无参值写成属性，带索引或距离的值写成函数。普通 HUD 元素不编译武器表达式；绑定层须给出可定位诊断，避免未绑定查询静默退回 0。

### 3.1 炮镜下的分划

相机宿主自己的 `scope` HUD 元素提供边框、遮罩等基础显示。弹道分划是武器控制器 `scope_hud_layout` 中的元素。该元素从**所属控制器**确定当前弹药和弹道参数，不从座椅查找控制器，也不由相机反查武器。

| 查询 | 返回 | 口径 |
| --- | --- | --- |
| `weapon.active_ammo` | string | 所属控制器当前用于显示/分划的弹药注册键；没有时为空 |
| `weapon.bdc_hold_rad(distance_m)` | number | 该控制器当前弹道在给定距离的 hold-over 角，弧度；无可用弹道数据时为 0 |

武器快照中“当前弹药”按本控制器发射管的稳定显示顺序选取，与弹种槽一致。多发射管且弹种不同的型号如需逐管分划，应在 `weapon.*` 中增加显式管索引查询，不能隐式借用座椅的控制器顺序。弹道查表的缓存键须包含影响结果的弹药与发射/归零参数，实际输入由 BallisticsFramework 接口和武器数据确定。

弹道缓存按控制器实际使用的弹药及弹道参数生成距离到 hold-over 角的表；相邻采样点之间插值，避免刻度在每个采样距离处跳动。`weapon.bdc_hold_rad` 对非有限、负距离或无可用弹道数据返回 0；若样式必须区分“无需修正”与“没有数据”，再增加独立的可用性查询。缓存的建立和失效发生在内容包重载或影响弹道的参数变化时，不在逐帧表达式里运行弹道求解。首版采用海平面、静止平台、同高目标和零归零角的标准标定；制导/推进弹返回 0，完整边界见实现记录。

`weapon.bdc_hold_rad` 返回弹道角，具体样式如何把角度标定为分划模型的骨骼位置由该样式决定；通用 `hud.*` 不提供模型坐标换算查询。

同一武器元素也可以在炮镜视角显示弹药文本；相机边框元素只有 `hud.*`，不读取 `weapon.*`。若两台控制器都为 `scope` 组配置分划，它们各自显示自己的元素；是否提交绘制由当前座椅的候选集合、当前视角布局及条目的 `selection_visibility` 决定；控制器启用状态不是隐含的显示过滤条件。

### 3.2 观察者选中状态与条目显隐

`weapon.selected` 返回所属控制器是否属于当前观察座椅 base + 当前子组选中集合，取 0/1。它与启用、开火、独占控制权及 `belt.selected` 无关。观察者选中状态与本轮业务快照形成元素的不可变求值帧，不写入控制器的全局字段；多座椅引用同一控制器时分别取值。

`selection_visibility` 属于 `HudElement`，取 `selected_only`（缺省）或 `always`，适用于同一候选控制器在当前视角的各个布局条目。公共 HUD 调度在模型与 AUI 的绘制入口之前统一判定显隐，不以 DOM、CSS 或表达式绑定作为必要条件。未选中常驻条目可以继续读取 `weapon.selected` 来调整样式。候选并集来自当前座椅的全部控制组，离开座椅不继续显示；切组不销毁候选元素及其 Document。完整条件与生命周期见框架文档 §4.2、§4.3、§6.1。

### 3.3 Java 无参查询的当前项作用域

无参查询不从调用次数、DOM 顺序或全局计数器推断管序号。绑定器为每个数据槽登记所属重复项的集合类型与稳定身份键；条目根和后代的数据槽都继承该项身份。每次求值先固定元素求值帧，再从该帧的不可变业务快照按身份键解析当前项及其下标，安装到本元素、本消费线程的上下文。以下是作用域机制示意；代码中的实际入口为 `WeaponHudMolangContext#enterItem`，接收集合标识、稳定身份键及可选下标别名：

```java
context.reset(element, animationTime);
try (var scope = context.enterItem("weapon.tubes", tubeKey, null)) {
    value = expression.evaluate(context);
}

@QueryBinding(value = "fill", namespace = "tube")
public double tubeFill() {
    return requireTube().fill();
}
```

同一编译表达式 `tube.fill` 可复用于所有管；每次上下文安装的成员不同，Java 查询读取的对象就不同。绑定器缓存身份，不永久缓存创建节点时的序号或 `TubeSnapshot`；快照排序或数据变化后须解析本轮成员，`tube.index` / `belt.index` 返回本轮下标。`weapon.*` 的整台控制器含义在作用域内保持一致。

作用域结束或表达式异常时必须通过 `finally` / `AutoCloseable` 恢复先前状态及可选下标变量。下标别名由上下文局部覆盖层提供，读取优先于原变量容器，不能修改 Part 或动画体共享变量容器。`reset` 在安装当前项前执行，作用域不跨线程、元素、数据槽或刷新代数保留。同轮列表求值固定同一份武器与观察者快照，不在槽位之间切换。首版不支持嵌套重复区域；保存/恢复仍是异常隔离的必要条件。

区域外或类型不匹配时访问 `tube.*` / `belt.*`，绑定层须给出页面/模板/槽位坐标的诊断并使该槽位降级，不默认第零项。模型 HUD 的骨骼与文本槽没有 DOM 重复作用域，使用 `weapon.tube_fill(i)` 等显式接口；未来若提供模型槽位的成员绑定声明，可复用相同作用域机制，首版不根据骨骼名隐式绑定。

**编译器边界**：`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java#compileQueryBindingCall` 对省略实参填零，`MolangCompilingVisitor.java#findQueryCallMethodOnType` / `#findStringQueryCallMethodOnType` 按命名空间和名字查找而不按实参数量分派。因此当前项查询使用 `tube.fill` 等独立无参名字，不能靠省略 `weapon.tube_fill(i)` 的参数或注册同名多签名绑定实现。属性访问与无参 Java 方法绑定的路径见 `MolangCompilingVisitor.java#findQueryMethodOnType` / `#findStringQueryMethodOnType`；编译缓存依据见 `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/MolangContextRegistry.java#compile`。

页面语法中 `data-mm-index` 可选；声明时提供与当前项下标一致的 `variable.<名字>` / `v.<名字>`，并按本节的作用域恢复规则处理。默认无参写法不需要声明下标变量，完整作者语法见框架文档 §8.5、§8.6。

## 四、填充与求值时机

| 时机 | 动作 |
| --- | --- |
| 创建运行时元素 | 确定宿主和有效元素，创建当前消费线程的上下文；绑定所属 Part/装配体 |
| 每客户端 tick | 发布候选、选中状态和本控制器业务快照；元素求值帧固定观察者数据，AUI 页面按对应上下文更新数据槽 |
| 每个重复项数据槽求值 | 按身份键从本轮快照解析当前项，安装类型作用域和可选下标变量；求值结束或异常时恢复 |
| 各载体提交绘制前 | 公共 HUD 调度依据当前视角、候选成员及 `selection_visibility` 判定显隐，覆盖模型和 AUI 路径 |
| 每渲染帧采样后、消费 `hud.*` 的表达式求值前 | 发布该帧的相机视角与视口快照；当前线程上下文按 Spark 契约 `reset` 后读取已发布快照 |
| 每次模型文本渲染前 | 将客户端主线程上下文 `reset` 到当前元素，读取该渲染帧的视角快照，再计算 `molang_args` |
| 玩家布局修改、视角切换或内容包热重载 | 更新有效布局或重建受影响元素，再按新的上下文/样式编译或取缓存 |

渲染阶段发布视角快照，主线程文本与 AUI 消费本帧数据；物理动画读取最近已发布的快照。骨骼结果经过物理步混合、客户端 tick 发布与渲染插值，有采样/发布延迟，不承诺与本次渲染相机严格同帧。AUI 的“按发射管数量自动生成多段界面”属于页面绑定层，当前项作用域按 §3.3 安装；显式索引查询不依赖 DOM。两种取值方式共享业务快照，不要求每个重复项创建上下文类或 HUD 元素。

## 五、实施与验收

1. 建立通用/武器 HUD 元素和对应上下文类，先让现有模型 HUD 的 `molang_args` 使用每元素上下文；让 `local.*` / `global.*` 在 HUD 上下文中保持可用。
2. 建立武器业务快照、观察者选中状态、`weapon.*` 与当前项查询；候选控制器来自座椅全部组，公共调度以条目策略控制模型和 AUI 显隐。炮镜边框由相机布局提供，分划由武器控制器的 `scope` 布局提供。
3. AUI 绑定层按元素关联上下文，以 `data-mm-repeat="weapon.tubes"` / `"weapon.belts"` 安装当前项类型作用域，默认使用 `tube.*` / `belt.*` 无参查询；`data-mm-index` 为可选别名。每次槽位求值按 §3.3 解析身份、安装并恢复作用域，不写入动画体共享变量。完整语法、CSS 内部排布和生命周期见《[HUD 样式、布局与元素设计](../进行中/界面-HUD样式挂载与布局设计.md)》§8.5、§8.6。
4. 采集并发布渲染帧视角快照，校验模型与 AUI 两种载体、视角切换和热重载。

验收至少覆盖：模型与 AUI 的条目显隐一致；同一 WC 的常驻面板和仅选中分划分别显隐；切组保留元素/Document；多观察者选中状态隔离；当前项在数据槽之间及异常后不泄漏，排序后按身份取值；无作用域/类型错误被诊断；模型显式索引查询不依赖 DOM；同一座位下两台控制器的分划/弹药互不串值；炮镜边框无武器查询；电子罗盘随视角快照及骨骼插值平滑转动，允许采样/发布延迟且不误读车体朝向；第三人称坐标取实际镜头位置；模型骨骼和文字各在线程实例上读取同一元素发布的快照；现有官方 HUD 的 `global.get('vehicle_speed')` 仍能求值。代码及自动化测试的完成范围见实现记录；这些场景中的画面和游戏交互部分尚待进世界验收。

## 六、相关代码

| 当前代码 | 作用 |
| --- | --- |
| `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/animation/IAnimatable.kt#getMolangContext` | 动画体提供自己的上下文 |
| `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/SparkMolangContext.java#reset` | 上下文关联动画体 |
| `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/MolangContextRegistry.java#getOrCreateEngine` | 无参原型与按上下文类缓存编译结果 |
| `src/main/java/io/github/sweetzonzi/machine_max/common/mech/molang/MechMolangContext.java#localGet` | Part 上下文的现有 `local.*` / `global.*` 查询 |
| `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/GuiAnimatable.java#getMolangContext` | 模型载体读取自身运行时元素上下文的路径 |
| `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick` | 当前 HUD 宿主集合的选择、布局比较与元素重建调度 |

## 七、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-06-15 | 提出 HUD 渲染参数、弹道分划查询与模型 HUD 接线 |
| 1.1 | 2026-10-16 | 补充模型 HUD 与武器 HUD 计划之间的引用关系 |
| 1.2 | 2026-10-16 | 定义通用/武器元素及上下文继承、`hud.*` / `weapon.*` 词表、武器控制器持有的分划和逐元素求值时机 |
| 1.3 | 2026-10-17 | 按实际显示需求收缩 `hud.*` 至视角与视口查询；载具仪表沿用 `global.*`，分划骨骼换算由样式负责 |
| 1.4 | 2026-10-17 | 运行时对象表述改为布局条目；布局与元素的术语对齐框架文档 |
| 1.5 | 2026-10-18 | 武器分划的可见性补座椅选中集合；与无主/副的 HUD 可见性规则对齐 |
| 1.6 | 2026-10-20 | 关联已定的重复项索引语法与作用域保存/恢复规则；绑定层尚未实现 |
| 1.7 | 2026-10-21 | 定义观察者选中状态、载体通用条目显隐及 Java 当前项作用域；说明无参方法取数、身份解析、异常恢复和显式索引接口边界 |

| 1.8 | 2026-10-22 | 接入元素上下文、快照、显隐与 AUI 作用域，采用主线程 HUD 动画采样；自动化验证与实机待验收清单见实现记录 |

| 1.9 | 2026-10-22 | 模型 HUD 使用宿主客户端 Level 与标准物理动画管线，按步固定输入快照，主线程发布骨骼并在渲染时插值；文本/AUI 使用当前渲染帧视角 |
