# HUD 上下文实现与验收

> **状态**：已实现。元素上下文、武器快照、公共显隐和 AUI 绑定已经进入代码，仅剩进世界的画面与控制组交互验收。

2026-10-22。离线完整构建通过，81 项单元测试通过。

## 数据与执行归属

- `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudRuntimeElement.java#HudRuntimeElement` 是一个布局条目的动画和求值身份，实现 `IAnimatable<HudElement>`；`HudElement` 是布局配置 record。模型与 AUI 载体各自持有一个运行时身份，AUI Document 本身不是动画体。
- `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudMolangContext.java#reset` 固定本轮不可变求值帧。上下文按元素、消费线程缓存；`HudPartSnapshot` 提供所属宿主 Part 的 `local.*` 与其装配体的 `global.*`，包含官方仪表使用的 `global.get('vehicle_speed')`。
- `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudWeaponSnapshot.java#capture` 在客户端 tick 采集单控制器业务数据，按实例去重弹药供给者。发射管使用 Part UUID、SubPart 名、子系统名作为身份；弹链使用完整有序序列作为身份。快照列表与键列表防御性复制。
- `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick` 按座椅全部控制组发现的控制器建立候选。base + 当前组形成独立选中集合；切组更新元素帧中的 `weapon.selected`，相同候选与布局保留元素及 Document。模型和 AUI 的绘制入口均检查 `HudRuntimeElement.java#visible`，普通座椅/相机元素不受选中条件影响。

HUD 模型沿用 Spark 的物理动画管线。`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudRuntimeElement.java#getAnimLevel` 返回宿主所属客户端 Level，Spark 由该 Level 取得对应 PhysicsLevel，并安排动画进入/退出任务。`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/MMGuiManager.java#onPhysicsTick` 只处理客户端物理事件，模型元素还须与事件所属 Level 一致；`GuiAnimatable.java#animTick` 在物理级别就绪后推进主线程相位。

物理步由 `GuiAnimatable.java#physicsTick` 调用 `AnimController#physTick`，骨骼混合结果经 `BonePose#updateInternal` 写入内部姿态；客户端 tick 的 `AnimController#tick` 调用 `BonePose#setChanged` 发布上一姿态与当前姿态；模型与骨骼文本位置使用渲染 partialTick 插值。引擎入口为 `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/animation/anim/AnimController.kt#physTick` 和 `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/animation/model/BonePose.kt#setChanged`。HUD 的 LOD 参考点取视角快照中的相机位置。

`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudRuntimeElement.java#pinFrame` 为一次物理步固定当前消费线程的输入快照，使同一轮骨骼混合中的多次 MoLang reset 读到同一帧；作用域退出或异常时恢复。主线程可以同时发布下一份快照，主/物理线程各自使用独立上下文实例。

`CustomHud.java#render` 发布当前相机位置、航向、俯仰、GUI 宽高及倍率。主线程文本与 AUI 读取本帧视角；物理动画读取最近已发布的视角，骨骼经物理步和客户端 tick 发布后插值，因此具有采样/发布延迟，不要求与相机严格同帧。FOV 取 `HudCameraEvents.java#captureFov` 收到的世界投影事件，排除手部投影；横滚取 `HudCameraEvents.java#captureRoll`，因为 GameRenderer 把该角施加在视图矩阵上。位置来自实际渲染相机，覆盖第三人称镜头。

## 页面绑定

`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudPageBinding.java#refresh` 支持 `data-mm-text`、`data-mm-num` / `data-mm-var` / `data-mm-unit`、`data-mm-class`，以及 `weapon.tubes` / `weapon.belts` 重复区域。

模板通过 `Document.createHTML` 使用 AUI 的 HTML 解析顺序实例化，属性与子树就绪后执行 DOM 初始化；不调用 `cloneNode`。注释节点标记重复区域边界，按稳定键增删和重排条目，同一身份复用节点。当前项由 `WeaponHudMolangContext.java#enterItem` 安装，退出及异常时恢复；下标别名位于上下文覆盖层，不写入 Part 或共享变量表。普通元素、错误集合类型、缺少当前项和省略显式索引得到诊断。

绑定器监听外部节点和数据标记变化；页面刷新代数变化时释放槽位、模板及观察器，并在测量前重新绑定。数值、文本和 class 只在结果变化时写入。重复模板不能含固定 ID，首版不支持嵌套重复区域；这些错误停止对应区域，其他槽位继续运行。

## 分划标定范围

`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudBallisticTable.java#update` 为当前显示弹种生成距离—角度缓存。首版明确采用：海平面 Y=62、静止平台、同高目标、相对水平出射轴、归零角为 0。初速包含所选显示发射管的速度倍率和加值，重力及二次阻力参数来自弹种，求解使用 BallisticsFramework 的低弧解。

表有 128 个距离区间，上限为弹种初速乘寿命与 10000 米的较小值，区间内线性插值。弹种对象或初速变化时重建；内容重载生成新弹种对象可使缓存失效。非有限/负距离、寿命外或无解区间、制导/推进弹及无弹药均返回 0。查询本身只读固定求值帧里的表。该接口不提供当前瞄具归零距离、移动平台提前量或目标高差解算。

## 内容包接入

内置布局 `machine_max:weapon_live` 使用样式 `machine_max:aui_weapon_live`，页面位于 `src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/documents/weapon_hud_live/index.html`。在武器控制器动态属性中配置：

```json
{
  "first_hud_layout": "machine_max:weapon_live",
  "scope_hud_layout": "machine_max:weapon_live"
}
```

这两项是动态属性的片段；同一对象还需要既有的 `type`、`definition` 和控制布线。没有布局引用的控制器不生成 HUD。多台武器共用该示例布局时位置会重叠，内容包应各自定义投放位置。运行期武器 HUD 由 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick` 统一调度，各 WC 的三个视角布局是武器读数的接入入口。

K17 使用三种内置动态组件分别显示弹链、逐管与紧凑读数，挂载与静态对照方法见 [K17 动态武器 HUD 组件接入](K17动态武器HUD组件接入.md)。

`hud/hud_element.schema.json` 与 `hud/hud_layout.schema.json` 位于 builtin 的双语 schema 源目录；武器控制器 schema 带接入示例。副本与技能快照通过 `gradlew --offline syncSchemas` 生成。

## 已执行验证与待验收项

`gradlew --offline build` 通过，包含 81 项单元测试和 `checkSchemas`。本轮测试覆盖编译表达式的继承绑定与多元素/多观察者/多线程隔离、显式索引降级、只读别名和异常恢复、稳定身份重排与同数量替换、空集合、外部 DOM 变更、页面换代、Canvas 尺寸与行内事件监听初始化、模板占位文字的渲染文本替换、物理混合结果在主线程 tick 发布及渲染插值、物理轮次内快照固定及异常恢复，以及弹道插值、无阻力解析解误差和有阻力有限性。已有 AUI 投放、布局、透明背景与 K-17 样式测试一并运行。

骨骼发布测试使用 JUnit 工作线程调用动画层推进、骨骼混合和 BonePose 内部写入口，不构造真实客户端 Level；真实物理事件与动画任务队列的接线需在游戏内验证。

实机待验收：集成服务器中仅客户端物理事件驱动 HUD；上下车和第一/第三/炮镜视角切换；同一 WC 的常驻面板与仅选中分划分别显隐；切组时 Document 身份保持；两台控制器同时显示时弹药和装填来源正确；相机摇晃/横滚/FOV 和罗盘观感；`/reload` 后页面及内容包资源更新。玩家布局覆盖与编辑页属于框架 V4，未在本轮实现。

## 与设计文档的关系

- [HUD 样式、布局与元素设计](../进行中/界面-HUD样式挂载与布局设计.md) 定义宿主、布局和绑定语法。
- [HUD 专用 MoLang 上下文设计](./观瞄系统-HUD专用MoLang上下文设计.md) 定义查询口径；本文件明确运行时类名、物理步推进和主线程发布的模型动画方案。
- [武器控制器 HUD 设计](./武器系统-武器控制器HUD样式与布局设计.md) 定义武器业务口径；本文件明确首版分划的标准标定环境。
