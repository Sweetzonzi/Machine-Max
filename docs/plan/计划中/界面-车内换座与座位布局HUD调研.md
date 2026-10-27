# 车内换座与座位布局 HUD 调研

> **版本** 2.11 · 2026-10-27
> **状态**：P0–P2 已实现（客户端画面待实机验收）；P3–P4 未实现。
> **基线**：NeoForge 21.1.219 / Minecraft 1.21.1；内容包 `Machine-Max_K17`、`Machine-Max_MTLB`、`Machine-Max_2S38`、`Machine-Max_Civilian`。
> **结论**：不建议给每个座位补交互区。推荐**一个循环换座键**加**常驻的俯视座位图 HUD**：换座只按一个键，跳到排序中的下一个空位；座位图在车内常驻，画出载具模型与座位位置，标出自己在哪、哪些被占、下一个是谁。客户端算出下一个目标座位后发给服务端，服务端只校验目标是否可用；同一条载荷也能承载将来的点击选座。座位图可以顺带按耐久给车体零件着色，作为现有 O 键内构查看的总览补充。

***

## 目录

1. [结论先行](#一结论先行)
2. [现状](#二现状)
3. [需求与约束](#三需求与约束)
4. [方案空间](#四方案空间)
5. [推荐方案](#五推荐方案)
6. [实施计划](#六实施计划)
7. [验收](#七验收)
8. [风险与开放问题](#八风险与开放问题)
9. [证据索引](#九证据索引)
10. [修订记录](#十修订记录)

***

## 一、结论先行

| 问题 | 结论 |
| --- | --- |
| 是否给每个座位加交互区 | **不作为主方案**。交互区适合“从车外进入”，不适合“在车内选座”：它要求内容作者补骨骼/碰撞体，受车体遮挡、第三人称镜头与眼睛射线错位、交互距离和同区多目标的限制，而且无法展示“一共有哪些座位、哪些空着” |
| 换座需要几个键 | **一个循环键**。座位顺序由几何排序确定，按键跳到下一个空位；不需要数字键，也不需要先打开任何界面 |
| 是否需要换位菜单 | **不需要**。模态 Screen 会释放鼠标、打断镜头与操作；常驻 HUD 已经能回答“有哪些座位、我在哪、下一个是谁” |
| 座位图何时显示 | **常驻**：只要玩家由座位控制就显示，不按住、不弹窗；配置可切到“仅换座后短暂显示”或关闭 |
| 是否展示俯视载具 | **是**。用载具模型本体画俯视图，在模型上标出每个座位的位置 |
| 是否同时显示零件状态 | **可以，但作为次要层**。GUI 内逐 SubPart 渲染时，耐久色就是一项渲染参数；详细的子系统/连接点状态仍由 O 键内构查看承担，避免两套损伤可视化互相竞争 |
| `VehicleAnimatable` 是否适合 | **座位图不需要它**：GUI 图层直接用实时 `SubPart` 的骨骼与渲染矩阵画车体，省掉 `VehicleData` 拷贝。它仍然适合斜视角 3D 预览（控制面板/蓝图/物品图标），和座位图是两条线 |
| 后端缺什么 | 座位列表、座位 locator、同装配体换座语义已经存在；缺的是**占用状态同步、座位显示名、座位几何排序与“下一个”计算（客户端）、带目标座位的换座协议、专用切换路径（不触发下车搜索/冲量）、常驻 HUD** |

***

## 二、现状

### 2.1 换座的唯一入口是交互区

当前玩家在车内换座，只能靠“把视线对准另一个座位的交互区，按交互键”。完整链路如下：

```text
F 键
  → LivingEntityEyesightAttachment.clientInteract()
  → SubsystemInteractPayload(subPartId, interactBoxName)
  → 服务端 InteractBox.interact(entity)
  → 按 signal_targets 顺序逐个尝试目标
  → SeatSubsystem.onInteract(entity)
  → SeatSubsystem.setPassenger(entity)
```

证据：

- `LivingEntityEyesightAttachment.clientInteract()` 先取准星命中的 `ACCURATE` 交互区，没有再取玩家碰撞箱接触到的 `FAST` 交互区（`src/main/java/io/github/sweetzonzi/machine_max/common/attachment/LivingEntityEyesightAttachment.java:348-354`）。
- `InteractBox.interact` 按 `targets` 的声明顺序逐个尝试，只有 `CONSUME` 结束遍历；`FAIL`（座位已占用）和 `PASS` 都继续下一个目标（`src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/interact/InteractBox.java:146-166`）。
- `SeatSubsystem.onInteract` 对空座位调用 `setPassenger` 并返回 `CONSUME`（`src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/SeatSubsystem.java:87-94`）。

这条链路的**交互语义**本身没有问题，问题在于它把“选哪个座位”完全交给了“瞄准哪个交互区”。对小型有内构的载具，这恰好可行；对封闭军用载具，则不成立。

### 2.2 后端已经具备“同一装配体内换座”的语义

`SeatSubsystem.setPassenger` 已经处理了从旧座位切换到新座位的多数状态：

- 乘客已在一个座位上时，`updateBoardingOffset` 会把旧座位的上车点缓存经**世界坐标**换算到新座位；判据是“旧控制子系统是同一装配体上的另一个 `SeatSubsystem`”（`SeatSubsystem.java:136-155`）。
- 旧座位会被 `removePassenger()` 清空：清控制子系统、停止骑乘、清额外质量、把 `passenger_num` 信号归零、重置信号输出（`SeatSubsystem.java:157-176`）。
- 新座位会设置占用、`passenger_num`、控制子系统、额外质量，并激活物理、重算相机距离（`SeatSubsystem.java:96-122`）。
- `CameraController` 已经支持“同一装配体内换座继承操作者目标”：它比较 `seat != controllingSeat`，同装配体时继承 `expectedPoint` 与瞄准目标状态（`src/main/java/io/github/sweetzonzi/machine_max/client/input/CameraController.java:143-157`）。
- 相机在换座后会按新座位的 `views` 重新执行第一/第三人称约束（`CameraController.java:422-437`）。

也就是说，**“换到另一个座位”这个动作在状态层已经跑通过**（目前是通过交互区触发的）；缺的是一个不依赖交互区的“选择目标座位”的入口，以及一条不产生下车副作用的专用切换路径（见 §5.5）。

### 2.3 交互区方案在军用载具上的四个硬约束

#### 约束一：交互区只在车外，且通常只服务于“进入”

官方军用载具（K17/MTLB/2S38）的交互区全部挂在车体外部舱盖/车门上，没有“每个座位一个车内交互区”；下表的 Van 作为民用对照，它的每个座位是独立零件、自带交互区：

| 载具 | 座位数 | 交互区 | 目标 |
| --- | --- | --- | --- |
| K17 | 3（驾驶员/车长/炮手） | 驾驶员舱盖、后舱盖 | 驾驶员舱盖 → `driver_seat`；后舱盖 → `gunner_seat`、`commander_seat` |
| MTLB | 10（驾驶员/车长/8 名载员） | 驾驶员舱盖、车长舱盖、后门、载员顶盖 | 后门/顶盖的目标列表都是 `subsystem.machine_max.passenger_seat_1`…`8` |
| 2S38 | 3（驾驶员/炮手/车长） | 三个舱盖 | 每个舱盖只指向自己的座位 |
| Van | 4（前排 2、后排 2） | 四个座位各一个 `ACCURATE` 交互区 | 每个交互区只指向自己的座位 |

证据：`Machine-Max_K17/.../parts/k17_hull.json:73-91`、`:268-317`；`Machine-Max_MTLB/.../parts/mtlb.json:614-658`、`:784-921`；`Machine-Max_2S38/.../parts/2s38_hull.json:79-104`、`:362-411`；`Machine-Max_Civilian/.../parts/van/van_seat.json:27-35` 等四处变体。

K17 只有驾驶员舱盖与后舱盖两个交互区：驾驶员舱盖只指向驾驶员，后舱盖指向“炮手 → 车长”。后舱盖不区分“谁在操作”，任何能命中它的人都会按列表顺序坐到第一个空位；驾驶员要换座也只能借这个后舱盖，而它挂在车外，能否命中取决于射线是否被车体挡住。2S38 的三个舱盖各自只指向自己的座位，内容上没有跨座目标；玩家只能靠“够到另一个舱盖”来跨座，而舱盖都在车外。MTLB 的后门/顶盖把 8 个载员位写成一个**顺序列表**：`InteractBox.interact` 从第一个空位开始坐，玩家无法指定“我要第 5 个载员位”。Van 是反例：每个座位自己就是一个零件、带自己的 `ACCURATE` 交互区，所以“转头看旁边座位再按 F”能成立——这正是用户观察到的差异。

#### 约束二：车体会先挡住射线

`getAccurateInteractBox` 按射线命中顺序遍历目标，遇到第一个不是 `InteractBox` 的目标（例如车体 `SubPart`）就返回 `null`（`LivingEntityEyesightAttachment.java:319-331`）。而 Spark/JME 的 `rayTest` 结果按命中比例升序排序（`CollisionSpace.rayTest`，Spark-Core 源码）。因此，**封闭车体内的座位交互区即使存在，也会被前面的车体表面挡掉**；只有座位交互区凸出车体或在开放车体里，才可能被准星命中。

#### 约束三：第三人称镜头与眼睛射线不是同一条线

军用座位的 `default_3rd_*` 只开第三人称（`Machine-Max_Builtin/.../subsystems/seat/default_3rd_seat.json:6-16`、`default_3rd_driver_seat.json:6-16`）。第三人称镜头在车体后上方（`CameraController.seatOrigin`，`CameraController.java:396-418`），但交互射线从**玩家眼睛**（座位点）出发（`LivingEntityEyesightAttachment.java:91-93`）。方向虽然由逻辑视角同步给玩家，但相机经过环绕与平滑，**射线的起点与画面准星对应的射线并不重合**：瞄准一个屏幕上的舱盖，射线从座位出发可能先打到别处；反之亦然。这是“只能靠屏幕文字提示”的根因。

#### 约束四：交互距离与多目标歧义

- 交互射线长度取 `Attributes.ENTITY_INTERACTION_RANGE`（原版默认 3 格，`LivingEntityEyesightAttachment.java:89-93`）。大型载具里，远端座位可能超出范围。
- `FAST` 交互区依赖玩家碰撞箱与交互区接触（`LivingEntityEyesightAttachment.java:131-163`）。如果给每个座位加一个 `FAST` 区，玩家坐在座位上时身体会同时接触多个区，按 F 时选哪个取决于集合迭代顺序，不可控。
- 一个交互区可以列多个座位目标，但顺序固定；它表达的是“从这扇门进去，优先坐前排”，不是“让玩家选”。

**结论**：交互区是“从车外进入”的优秀入口，不是“车内选座”的合适 UI。保留车外交互区，不要在车体内为每个座位补交互区。

### 2.4 官方载具的座位拓扑

座位 locator 都定义在模型里，服务端/客户端都能读到：

| 载具 | 座位 locator（模型空间，示意） | 拓扑特征 |
| --- | --- | --- |
| K17 | `driver_seat [12.5, 9, -48]`、`commander_seat [6.5, 9, 31]`、`gunner_seat [-6.5, 9, 31]`（`Machine-Max_K17/.../models/part/k17.geo.json:23703-23706`） | 3 个座位都在同一个车体 SubPart 上；驾驶位在前，车长/炮手并排在后 |
| MTLB | `driver_seat [13.5, 13, -23]`、`commander_seat [-12, 13, -21]`、`passenger_seat_1..8` 位于 `x=±17`、`z=20/32/44/56`（`Machine-Max_MTLB/.../models/part/mtlb.geo.json:15728-15737`） | 10 个座位全在同一个车体 SubPart 上；典型 2 列 × 5 排 |
| 2S38 | `driver_seat [14.14, 14, -34.58]`、`gunner_seat [-1.82, 14, -34.29]`、`commander_seat [-15.6, 14, -34.29]`（`Machine-Max_2S38/.../models/part/2s38.geo.json:12844-12846`） | 3 个座位并排在同一排，各自一个外部舱盖 |
| Van | 四个变体各自的 `*_seat_seatpoint`（`Machine-Max_Civilian/.../parts/van/van_seat.json`） | 每个座位是独立零件/独立 SubPart；跨 SubPart 换座 |

这些 locator 就是座位图的天然数据源：**不需要内容作者额外声明顺序，也不需要在模型里补交互区**。座位数量、顺序、宿主 SubPart 都由装配结果在运行时决定。

### 2.5 数据基础与缺口

| 数据 | 现有来源 | 客户端可用 | 缺口 |
| --- | --- | --- | --- |
| 座位列表 | `VehicleCore.getAllSeats()`（`src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/VehicleCore.java:1521-1526`） | 是 | 方法已存在，但目前没有 UI 使用；顺序是集合顺序，需要自行排序 |
| 座位世界位置 | `SeatSubsystem.getSeatPointWorldTransform()` / `SubPart.getLerpedLocatorWorldTransform()`（`SeatSubsystem.java:190-198`） | 是 | 无 |
| 占用状态 | `SeatSubsystem.occupied` / `passenger` | **否** | 只有服务端字段；客户端只在交互广播时被动设置，没有同步 |
| 座位显示名 | `SeatSubsystem.name`、`AbstractPart.variantName`（变体名字符串，`variant` 字段是 `VariantAttr`）、`attr.locator` | 是 | 没有统一的显示名；官方 lang 键不完整（见 §5.8） |
| 车体零件几何 | `SubPart.getBones()`、`part.getModelController().getModel().getPose()`、`OCube` | 是（客户端） | 不需要逐方块投影：GUI 内直接渲染整车，座位标记用同一矩阵（见 §5.2.4） |
| 零件耐久 | `SubPart.getDurability()`、`AbstractSubsystem.getDurability()` | 是（已同步） | 无 |
| 车体参考系 | 参考 SubPart 的 `getRotation()` / `getPosition()`（选法见 §5.2.2）；`VehicleCore.getPosition()` 是各零件 `rootSubPart` 位置的平均值（`VehicleCore.java:441-459`），随零件增删移动 | 是 | 需要选一个“车体参考系”（见 §5.2.2） |

### 2.6 渲染与 HUD 基础设施

- **2D HUD**：`CustomHud` 每个客户端 tick 解析当前座位的 `IHudHost`，按 `HudLayout` 创建运行时元素（`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java:104-163`、`:190-219`）。`HudLayout` 是内容包里的静态元素数组，适合“作者写死的仪表盘”，不适合“按装配结果动态生成 N 个座位标记”的座位图。
- **HUD 图层注册**：`MMGuiManager.registerHud` 用 `RegisterGuiLayersEvent` 注册 `CustomHud`、`InteractHud` 等（`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/MMGuiManager.java:49-57`）。新增一个独立 `LayeredDraw.Layer` 是成本最低的入口。
- **2D 绘制**：`GuiGraphics` 的 `fill`/`drawString`/`enableScissor` 足够画面板和座位标记；标记的屏幕坐标由 §5.2.4 的同一个 `PoseStack` 矩阵给出，不需要自己的相机或投影矩阵。
- **整车 3D**：`VehicleAnimatable` 从 `VehicleData` 构造一套 `SubPartAnimatable`，用于蓝图/物品/GUI 预览（`src/main/java/io/github/sweetzonzi/machine_max/common/visual/VehicleAnimatable.java:70-111`、`:172-191`）。**GUI 内渲染整车是现成能力**：`CustomModelItemRenderer.renderByItem` 在 `ItemDisplayContext.GUI` 下就用它画装配体物品图标（`CustomModelItemRenderer.java:42-58` → `:208` 的 `renderVehicleAnimatable`），`PartAssemblyRenderer.renderVehicleProjection` 给出逐 `SubPartAnimatable`、带颜色参数的画法（`PartAssemblyRenderer.java:176-211`）。注意它是**按数据重建的预览体**，不是实时载具的渲染状态；座位图不依赖它，直接画实时 `SubPart`（§5.2.4）。
- **实时车体渲染**：`PartEntityRenderer` 用 `SubPart.getRenderWorldPositionMatrix(partialTick)` + `SubPart.getBones()` + `ModelPose` 渲染实时车体（`src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartEntityRenderer.java:75-114`、`:142-150`）；`SubPart.getBones()` 在 `SubPart.java:1351-1352`，渲染矩阵在 `:1389-1393`。世界空间的渲染链路；座位图复用其中的“矩阵 + 骨骼”数据，但画在 2D HUD 图层里（§5.2.4）。
- **3D 损伤可视化**：`VehicleInspectorRenderer` 按住 O 键渲染 SubPart 剪影、子系统 HitBox 耐久色和连接点状态（`src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/VehicleInspectorRenderer.java:56-58`、`:75-82`）。座位图的零件着色应与它分工，而不是重复造一套 3D 视图。
- **3D HUD**：`Hud3DRenderer` 注册 `IHud3DElement`，在 `RenderLevelLastEvent` 里按相机空间绘制（`src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/Hud3DRenderer.java:25-136`）；`AssemblyHud3D` 是装配阶段的 3D HUD。它们面向世界空间叠加，不适合需要屏幕固定、逐帧几何的座位图；座位图仍走 2D `GuiGraphics`。
- **控制面板**：`VehicleControlScreen.render3dPreview` 目前是空实现（`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/screen/VehicleControlScreen.java:197-198`）；控制面板的设计文档里已有基于 `VehicleAnimatable` 的斜视角 3D 预览方案（`docs/plan/进行中/AUI载具控制面板实现计划.md` 附录 A）。这适合“静止时看车”，不适合“行驶中快速换座”。

***

## 三、需求与约束

| 编号 | 需求 | 判据 |
| --- | --- | --- |
| R1 | 对任意装配体成立 | 座位数、顺序、宿主 SubPart 都不预设；只依赖运行时 `SeatSubsystem` 与 locator |
| R2 | 不要求内容作者补交互区 | 新载具/玩家组装的载具不改模型也能换座 |
| R3 | 第三人称/封闭车体可用 | 不依赖“看见座位”或“射线到达座位” |
| R4 | 展示占用与可用性 | 空位、已占用、不可用、当前座位、下一个目标可区分 |
| R5 | 服务端权威 | 客户端用本地顺序算出目标座位，只发目标（`subPartId + seatName`）；服务端不排序，只复核目标存在、同装配体、未占用、可用（§5.4.2） |
| R6 | 尽量不打断驾驶 | 换座与查看座位图时不释放鼠标、不停车、不暂停 |
| R7 | 兼容现有进入流程 | 车外舱盖/车门交互区保留，不变更语义 |
| R8 | 可扩展到零件状态 | 逐 SubPart 的渲染颜色可按耐久给色 |
| N1 | 非目标 | 跨载具传送、远程换座、改变座位控制逻辑、替代 O 键内构查看 |

***

## 四、方案空间

### A. 给车体加交互区

| 维度 | 评价 |
| --- | --- |
| 实现成本 | 中：每个座位在模型里加骨骼/方块，写 `interact_boxes`，可能还要配 `interact_mode` |
| 内容成本 | 高：所有现存和未来的载具都要补；玩家组装的载具无法自动获得 |
| 封闭车体 | 差：车体表面先挡住射线；`ACCURATE` 命中不到座位 |
| 第三人称 | 差：镜头与眼睛射线错位，准星不可信 |
| 多座位 | 差：同区多目标只能按顺序取第一个空位；`FAST` 区会重叠 |
| 可见性 | 差：不知道“一共有哪些座位、哪些空着” |
| 结论 | 只适合作为内容作者的沉浸式补充；不作为核心换座方案 |

### B. 世界空间座位标记 + 看选

在座位位置画世界空间标记（billboard/图标），玩家把准星移到标记上按 F 切换。

| 维度 | 评价 |
| --- | --- |
| 可见性 | 好（标记可见时）；差（标记被车体挡住时需要 X-ray） |
| 空间感 | 好：直接对应“转头看” |
| 多座位 | 差：MTLB 的 10 个座位在纵深上堆叠，准星很难选到第 7 个 |
| 占用展示 | 一般：标记可以变色，但没有全局视图 |
| 实现成本 | 中：需要新的座位射线/目标选择、标记渲染、遮挡策略 |
| 结论 | 可作为“快速指向”补充；不作为唯一方案 |

### C. 循环换座键

按一个键循环到下一个空座位。

| 维度 | 评价 |
| --- | --- |
| 实现成本 | 极低：一个键位 + 换座协议 + 几何排序 |
| 驾驶中可用 | 好：不打开任何界面，不占数字键 |
| 多座位 | 好：座位数不设上限，按到目标为止 |
| 可见性 | 差：只看 actionbar 的话，“下一个是谁、我在车里哪个位置”都无从判断 |
| 结论 | **唯一的换座动作**；可见性的缺口由 D 的常驻 HUD 补上 |

### D. 俯视座位图 HUD

在 HUD 里常驻画出载具的俯视图，并在模型上标出座位位置；它可以是常驻，也可以是“仅在换座后短暂显示”。本方案取常驻。

| 维度 | 评价 |
| --- | --- |
| 实现成本 | 中：布局计算 + 座位图 HUD（含 GUI 内车体渲染）+ 占用同步 |
| 任意装配体 | 好：座位图由运行时座位 locator 生成 |
| 封闭车体/第三人称 | 好：不依赖射线、不被车顶遮挡 |
| 占用展示 | 好：空位/占用/不可用/当前座位/下一个目标一目了然 |
| 驾驶中可用 | 好：不释放鼠标、不暂停；WASD 仍可用 |
| 多座位 | 好：10 个座位仍可读；更多时标记变密但不是功能上限 |
| 内容成本 | 零（除可选的显示名 lang 键） |
| 结论 | **主方案，且只当显示层**：能不能换由 C 决定，具体座位由客户端按几何顺序（或将来的点击）选出，服务端只复核 |

### E. 模态菜单 / Screen

按一个键打开独立 Screen，用鼠标点座位。

| 维度 | 评价 |
| --- | --- |
| 实现成本 | 中高：Screen 生命周期、鼠标命中、与 AUI/3D 预览的协调 |
| 可读性 | 好：可以放详情、图例、滚动 |
| 驾驶中可用 | 差：Screen 释放鼠标，镜头/瞄准被打断；车辆仍在动 |
| 适用 | 静止时配置、查看详情、大编制载具 |
| 结论 | 后续可选；不作为 v1 |

### F. 文字列表

按一个键显示座位列表，用上下键/数字选择。

| 维度 | 评价 |
| --- | --- |
| 实现成本 | 低 |
| 空间感 | 差：不知道座位在车里哪个位置 |
| 多座位 | 好：可以滚动 |
| 结论 | 座位图失败时的降级方案；不是首选 |

### 方案对比

| 维度 | A 交互区 | B 世界标记 | C 循环键 | D 座位图 HUD | E Screen | F 列表 |
| --- | --- | --- | --- | --- | --- | --- |
| 任意装配体 | 差 | 好 | 好 | 好 | 好 | 好 |
| 内容零成本 | 差 | 好 | 好 | 好 | 好 | 好 |
| 封闭车体可用 | 差 | 差（需 X-ray） | 好 | 好 | 好 | 好 |
| 占用/全局视图 | 差 | 一般 | 差 | 好 | 好 | 一般 |
| 驾驶中可用 | 一般 | 好 | 好 | 好 | 差 | 好 |
| 多座位选择 | 差 | 差 | 好（按键次数多） | 好（只读） | 好 | 好 |
| 实现成本 | 中 | 中 | 极低 | 中 | 中高 | 低 |
| 结论 | 保留为车外入口 | 可选补充 | **唯一的换座动作** | **常驻显示层** | 不做 | 降级 |

**推荐组合：C + D。** C 负责“能不能换、换到哪”——一个键、一次按下，目标由客户端按几何顺序算出，服务端只复核目标是否可用；D 负责“看得见”——常驻显示座位位置、占用、自己与下一个目标，不承担输入。A 保持现状（车外进入），B/E/F 不采用或留作后续可选。

***

## 五、推荐方案

### 5.1 总体交互

```text
进入载具（交互区 F，保持现状）
  → 座位图 HUD 常驻显示：载具模型 + 座位位置 + 占用 + 自己在哪 + 下一个目标

按 X（循环换座键）
  → 客户端按几何顺序算出“下一个可用座位”，HUD 高亮它
  → 一个可用的都没有 → 本地提示“满员”，不发包
  → SeatSwitchPayload(targetSubPartId, targetSeatName)
  → 服务端解析目标、校验、切换
  → SeatSwitchResultPayload(targetSubPartId, targetSeatName, success, reason)
  → 客户端应用；HUD 的当前位置移到新座位
```

要点：

- **不打开 Screen**：座位图是 `LayeredDraw.Layer`，常驻绘制，不释放鼠标、不暂停、不阻断 WASD。
- **不需要数字键**：换座只有循环键，不占用 `1`–`9`/`0`，也不需要在热栏上做拦截。
- **客户端算目标，服务端校验目标**：循环键在本地算出下一个可用座位，把座位名发给服务端；服务端不排序，只确认这个座位存在、同装配体、可用、未被占。
- **一条载荷覆盖两种入口**：循环键与将来的选座界面（点击/数字键/径向菜单）都发同一个 `SeatSwitchPayload`，只是前者由排序算出目标、后者由玩家直接指定。
- **座位图只回答“在哪”**：它在模型上标出座位位置，不承担选择；即使座位图被配置为关闭，循环键仍然可用。
- **车外进入继续走 §2.1 的交互区路径**：舱盖/车门交互区调用 `SeatSubsystem.onInteract`；座位图只负责“已经在车里”的换座。

### 5.2 座位数据与布局

#### 5.2.1 数据收集

客户端从当前控制座位回溯到装配体：

```java
SeatSubsystem current = (SeatSubsystem) ((IEntityMixin) player).machine_Max$getControllingSubsystem();
VehicleCore vehicle = (VehicleCore) current.getSubPart().getPart().assembly;
List<SeatSubsystem> seats = vehicle.getAllSeats().stream()
        .filter(s -> s.getSubPart() != null && s.getSubPart().getPart() != null)
        .toList();
```

每个座位取世界位置，按用途分两种取法：

```java
// 排序/顺序：非插值，同一 tick 内取到稳定值，不受渲染插值影响
Vector3f sortPos = seat.getSubPart()
        .getLocatorWorldTransform(seat.getAttr().getLocator()).getTranslation();

// 渲染标记：插值，逐帧跟随物理更平滑
Vector3f drawPos = seat.getSubPart()
        .getLerpedLocatorWorldTransform(seat.getAttr().getLocator(), partialTick).getTranslation();
```

#### 5.2.2 车体参考系

座位图只需要一个偏航角：把载具摆正、车头朝上。参考零件要满足三件事——**稳定**（不随炮塔转动、不随部件增删换人）、**和车体同一个刚体**、**朝向可解释**（它的局部轴就是车头轴）。

“用面积还是用质量”都是在猜哪个像车体；更直接的是先回答“哪些零件和车体焊死在一起”。连接点属性已经给了答案，但**只有 Advanced 一侧的配置有效**。

配对永远是 Advanced + Simple（`AbstractConnector.attach` 的形参就是 `SimpleConnector`，`AbstractConnector.java:225`、`:264`）；关节由 Advanced 侧建立并挂在两端（`:268-278`），而 `adjustJoint()` 只读自己的 `attr.getJointAttrs()`，且只对 `AdvancedConnector` 生效（`:296-299`）。写在 simple 连接点上的 `joint_attrs` 是无操作配置，装载期只打一条警告（`ConnectorAttr.java:219-233`）。

所以刚性边的判据只看 Advanced 一侧；把 simple 侧也算进来，会让一处残留配置把刚性连接误判成可动：

```java
/** 刚性边：Advanced 连接点没有配置任何关节轴；simple 侧的 joint_attrs 不参与 */
static boolean rigid(AbstractConnector connector) {
    return connector instanceof AdvancedConnector && connector.attr.getJointAttrs().isEmpty();
}

// 刚性图的边从实际连接点关系建立，而不是遍历 vehicle.partNet.edges()：
// partNet 是 MutableNetwork<RegularPart, Pair<AbstractConnector, SimpleConnector>>（VehicleCore.java:71），
// 节点是部件、一条边是一次部件间对接；部件内部的 SubPart 连接由
// AbstractPart.autoAttachInternalConnectors 单独建立（AbstractPart.java:590-598，在 createSubParts
// 里由 :750 调用），从不进 partNet。
for (RegularPart part : vehicle.partNet.nodes())
    for (SubPart sub : part.subParts.values())
        for (AbstractConnector c : sub.connectors.values())
            if (rigid(c) && c.attachedConnector != null)
                union(c.subPart, c.attachedConnector.subPart);

// 配对永远是 Advanced + Simple（AbstractConnector.attach(SimpleConnector)），
// 对接时两端各存一份 attachedConnector（AbstractConnector.java:239-240）；
// “只认 Advanced 侧”同时完成去重，每条边只被计入一次。
// 1) 并查集合并后，每个簇累计质量、记录是否含驾驶员座位；跳过轮子簇（getMaxWheelRadius() > 0）
// 2) 选“含驾驶员座位”的簇，否则选总质量最大的簇
// 3) 簇内按质量、projectedArea.y、subPartId 依次排序取第一个
```

- **边必须从连接点建，不能只用 `partNet.edges()`**：一个部件可以包含多个 SubPart（车身、炮塔、整排座椅都在同一个部件里时），它们之间的刚性连接只存在于 `connector.attachedConnector` 上——`createSubParts` 建完 SubPart 就调 `autoAttachInternalConnectors` 把它们连起来（`AbstractPart.java:703`、`:750`），这条路径不经过 `partNet`。只看 `partNet` 会把这些 SubPart 当作互不相连的孤立节点，刚性簇全部退化成单零件，参考零件就可能选到一条座椅腿，`front` 也跟着歪。

选择顺序：

1. 含驾驶员座位的刚体簇；
2. 总质量最大的刚体簇（轮子簇除外）；
3. 当前座位 → 世界 `-Z`。

簇内取质量最大的零件，`SubPart.projectedArea.y` 作第二排序键。

```java
// 参考系每帧求一次：ref 是缓存下来的参考 SubPart，frontLocal 是它在自身刚体坐标系下的前向轴
// （符号在缓存时按驾驶员偏移定好），这里只做“采样 + 只取偏航”。
Matrix4f refM = ref.getWorldPositionMatrix(partialTick);           // 平移与旋转都插值
Vector3f origin = refM.getTranslation(new Vector3f());
Vector3f front = refM.transformDirection(new Vector3f(frontLocal));
front.y = 0;                                                        // 爬坡/翻车不影响地图
if (front.lengthSquared() < 1e-4) front.set(0, 0, -1); else front.normalize();

Vector3f right = new Vector3f(front).cross(new Vector3f(0, 1, 0)).normalize();
// 参考系里的相对坐标对平移与偏航都不变，所以排序、重叠判定与 fit 都能缓存
Vector3f rel = SparkMathKt.toVector3f(sortWorld).sub(origin);       // sortWorld 取 locator 的插值世界坐标
float sx = rel.dot(right);
float sy = -rel.dot(front);
```

说明：

- **簇内选谁都行**：同一刚体簇里的零件是刚性固定，朝向只差一个常量；质量只用来挑“最像主体”的那个，不会改变车头轴。
- **轮子另算**：轮子通过带 `joint_attrs` 的轮轴连接，本来就单独成簇；它的世界旋转还含滚动，直接排除（`getMaxWheelRadius() > 0`），不参与簇竞争。
- **质量是主键**：质量是静态内容数据，不随耐久、姿态、炮塔转动变化。炮塔比车体重意味着重心过高，现实设计不会这么配；即使配了，簇规则也会先把它排除，不依赖这个物理假设。
- **`projectedArea.y` 是什么**：`SubPart.projectedArea` 在零件构造时算好（`AbstractPart.java:718`），三轴分别是 X/Y/Z 方向的投影面积，`.y` 就是竖直方向看下去的面积；内容是 `-1` 时用局部碰撞盒的 `4 * xExtent * zExtent` 回退（`AbstractPart.java:562-575`）。
- **它不是实时投影**：数值来自零件**自身**的局部包围盒（或内容手写的 `projected_area`），不随炮塔转动、炮管俯仰、载具姿态变化。细长炮管斜着摆时，局部 AABB 仍是“长 × 直径”，不会被世界 AABB 的 √2 膨胀放大。内容若手写 `projected_area` 调气动，数值不一定等于真实占地，所以只做 tie-break。
- **缓存与重算**：按装配体 uuid 缓存**参考 SubPart、局部前向轴 `frontLocal`（含符号）、`fit`、排好序的座位引用**；进入座位时算一次，常驻期间只等 `VehicleStructureChangeEvent`（§5.7.6），收到才重算。**原点与偏航不进缓存**：它们随车辆平移与转向每帧变化，绘制时按同一个 `partialTick` 从参考 SubPart 的 `getWorldPositionMatrix(partialTick)` 现取。选中的零件被打爆也继续用它（位姿仍在），否则地图会在战斗中突然转 90°。
- **只取偏航**：参考零件每帧的世界矩阵把局部前向轴转到世界，再把 Y 归零；载具爬坡/翻车不影响地图。原点取参考零件自身的**插值世界位置**（`getWorldPositionMatrix(partialTick)` 的平移分量）：同一刚性簇内相对位置固定，炮塔转动、悬挂行程、加装拆件都不会让地图平移——但车辆行驶会，所以它必须逐帧取。`vehicle.getPosition()` 不用作原点——它是全部零件 `rootSubPart` 位置的平均值（`VehicleCore.java:441-459`），不是质心，且零件增删会移动它。
- **正负号**：前向轴指向 +Z 还是 -Z 取决于模型作者；用驾驶员座位的纵向偏移判断（驾驶员座位按“名字含 driver → 控制组最多的座位 → 当前座位”找），`|along| ≤ 0.5` 时视为居中，退回当前座位，再退回世界 `-Z`。**符号只在缓存参考系时定一次**，结果保存为 `frontLocal`，之后每帧只做旋转采样。客户端 `seat_map_flip_front` 与内容 `map_front` 可覆盖。座椅 locator 自带旋转时也能用它判断正负，但不同内容的朝向约定不一致，位置偏移更稳。
- **退化**：全可动载具（机械臂、全关节结构）没有刚性簇，退化成单零件簇，按质量选；再退到当前座位。
- **不用主轴拟合 / 速度 / 相机**：PCA 在近方形足迹上轴不稳定，且只给轴不给前后；速度在静止与倒车时退化；相机朝向会让地图随视线转，失去“车头朝上”的意义。
- `right = front × up`：车头朝北（`-Z`）时，`right` 是东（`+X`），符合“屏幕右侧 = 车体右侧”。
- 屏幕 `y = -dot(rel, front)`，使车头在屏幕上方。
- 这条链整段用 JOML：`getWorldPositionMatrix` 返回的已经是 `org.joml.Matrix4f`，与绘制侧的 `PoseStack`、`Matrix4f` 同库，只有座位 locator 的世界位置需要从 JME `Transform` 转一次（`SparkMathKt.toVector3f`）。布局与绘制因此共用同一套向量，不存在 JME/JOML 混算。
- §5.2.4 的推荐路径把同一个偏航直接烘进 `PoseStack`（先 `Axis.XP.rotationDegrees(-90)` 再 `Axis.YP.rotationDegrees(180 - headingDeg)`，`heading = atan2(front.x, front.z)`）；这里的 `(right, front)` 用于排序、重叠判定和兜底矩形。两条路径读同一个 `frontLocal`、同一个 `partialTick`，否则标记与模型会对不齐。

#### 5.2.3 排序、编号与“下一个”

排序键：`(sy, sx, seatName, subPartId)` 升序——先上后下、先左后右；名字/宿主 id 作为稳定 tie-breaker。Van 的四个座位子系统名都叫 `subsystem.machine_max.seat`，必须靠宿主 SubPart id 或 locator 兜底，不能只按名字排。坐标先四舍五入到 0.05 格，减少两端浮点误差导致的顺序翻转。

编号 `1..N` 是**显示顺序**，画在座位标记上（同时在面板标题的 `当前/总数` 与 actionbar 里出现），帮助玩家理解“按键会按什么次序走”；它不映射任何按键。

**“下一个”由客户端算出，作为发送的目标**：

```java
// SeatOrder 放 common 便于其它调用方复用；服务端不依赖它排序
static SeatSubsystem next(VehicleCore vehicle, SeatSubsystem from, boolean forward) {
    List<SeatSubsystem> order = order(vehicle);   // 已按 (sy, sx, seatName, subPartId) 排好
    int i = order.indexOf(from);
    if (i < 0 || order.size() < 2) return null;
    int dir = forward ? 1 : -1;
    for (int step = 1; step < order.size(); step++) {
        SeatSubsystem s = order.get(Math.floorMod(i + step * dir, order.size()));
        if (s != from && available(s)) return s;
    }
    return null;
}

static boolean available(SeatSubsystem s) {
    return s.isActive() && !s.isDestroyed()
            && !s.getSubPart().isDestroyed()
            && s.getSubPart().getEntity() != null
            && !s.isOccupied();          // 两端都有的同步值（§5.3），不读客户端没有的 passenger
}
```

- 算出目标后发 `SeatSwitchPayload(targetSubPartId, targetSeatName)`：服务端不重算顺序，只校验这个座位。两端的顺序**不需要一致**——服务端收到的已经是具体座位名，即使它自己有另一套排法也不影响结果。
- 排序用**非插值**的 `getLocatorWorldTransform`（`SubPart.java:1197`）；插值版本只用于标记的逐帧位置。排序只影响“按几次键到哪里”和编号显示，不影响换座的正确性。
- 可用性只读 `isOccupied()`，不读 `passenger`：`passenger` 只有服务端有值，客户端读它会把所有座位当空位。服务端在 `SeatSubsystem` 里同步维护两者，语义一致。
- 当前座位也参与排序；它自己永远不是“下一个”，所以只有 1 个座位时按键无操作。
- 没有别的可用座位时返回 `null`，客户端本地提示“满员”，不发包。
- 炮塔旋转会让炮塔上的座位在车体参考系里移动；顺序在**进入座位 / 结构变化 / 座位集合变化**时重算并缓存，不会转到一半跳变。标记位置仍逐帧更新。
- 重叠座位（同一 XZ、不同高度）：检测屏幕距离小于标记尺寸的座位，沿 X 方向错开几像素，并画一条细引线指回真实位置；高度差用一个小 `↑`/`↓` 角标表示（可选）。

#### 5.2.4 车体形状与座位标记的屏幕坐标

座位图需要两样东西：车体形状，和座位标记的屏幕坐标。两者可以来自同一个 3D 变换，不需要逐方块 2D 投影。

**推荐：在常规 2D HUD 图层里渲染载具模型。**

`LayeredDraw.Layer` 的 `render(GuiGraphics, DeltaTracker)` 跑在正交投影下，1 个 pose 单位就是 1 个 GUI 像素；`GuiGraphics` 自带 `bufferSource()`，所以模型可以直接画进这一层，不需要世界空间、相机或额外投影：

- `GuiAnimatable` 就是这样在 HUD 里画 3D 元素的：`guiGraphics.pose()` + `Lighting.setupForEntityInInventory` + `bufferSource`，正交分支只做 `translate(屏幕中心)`（`GuiAnimatable.java:159-189`、`:219-238`）。
- `CustomModelItemRenderer` 已经用同一套方式把**整台装配体**画进 GUI 物品槽（`CustomModelItemRenderer.java:42-58` → `:208`）。

座位图沿用这两条先例，只换外层变换：

```java
public class SeatMapHud implements LayeredDraw.Layer {
    @Override
    public void render(GuiGraphics g, DeltaTracker delta) {
        if (!seatMapVisible()) return;
        SeatMapSnapshot snap = snapshot();                    // 进入座位/结构变化后重建
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int x = ..., y = ...;                                // 面板位置（屏幕左下）
        g.fill(x, y, x + PANEL_W, y + PANEL_H, BG);
        // 标题、图例、键位提示：g.drawString(font, ...)

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.mulPose(SeatMapLayout.mapMatrix(snap.fit(), snap.centerSx(), snap.centerSy(),
                mapCenterX, mapCenterY, origin, headingDeg));  // 见下方“变换顺序”
        // 无纹理、不测深度的纯色几何：外壳与内构都走这一条，靠半透明叠加出透视
        var buffer = g.bufferSource().getBuffer(MMRenderTypes.solidXray());
        // 第一遍：外壳（很淡）
        for (SubPart sub : snap.subParts()) {
            Object model = sub.getPart().getModelController().getModel();
            if (model == null) continue;                      // 资源重载窗口：getBones 会 NPE
            ps.pushPose();
            ps.mulPose(sub.getRenderWorldPositionMatrix(partialTick));
            for (OBone bone : sub.getBones().values()) {
                ModelRenderHelperKt.render(bone, model.getPose(), ps, buffer,
                        Brightness.FULL_BRIGHT.pack(), OverlayTexture.NO_OVERLAY,
                        modelColor(sub), partialTick, false);
            }
            ps.popPose();
        }
        // 第二遍：带 hitbox 的子系统的骨骼，按子系统耐久染色，压在全部外壳之上
        for (SubPart sub : snap.subParts()) {
            Object model = sub.getPart().getModelController().getModel();
            if (model == null) continue;
            ps.pushPose();
            ps.mulPose(sub.getRenderWorldPositionMatrix(partialTick));
            Map<String, OBone> bones = sub.getBones();
            for (Map.Entry<String, HitBoxAttr> hitBox : sub.getAttr().getHitBoxes().entrySet()) {
                String subsystemName = hitBox.getValue().subsystem;
                if (subsystemName.isEmpty() || !bones.containsKey(hitBox.getKey())) continue;
                if (!(sub.subsystems.get(subsystemName) instanceof BasicSubsystem subsystem)
                        || subsystem.isHidden()) continue;
                ModelRenderHelperKt.render(bones.get(hitBox.getKey()), model.getPose(), ps, buffer,
                        Brightness.FULL_BRIGHT.pack(), OverlayTexture.NO_OVERLAY,
                        SubsystemDurabilityColors.withAlpha(      // 与内构 HUD 同一条色带
                                SubsystemDurabilityColors.colorFor(
                                        subsystem.getDurability(), subsystem.getMaxDurability()),
                                0xE6),
                        partialTick, true);
            }
            ps.popPose();
        }
        ps.popPose();
        g.flush();                                            // 半透明按提交顺序叠加：模型先提交，标记再画

        for (SeatMarker seat : snap.seats()) {
            Transform locator = seat.seat().getSubPart()
                    .getLerpedLocatorWorldTransform(seat.seat().getAttr().getLocator(), partialTick);
            // JME Transform → JOML 的副本：本步就地变换，绝不能改写 locator 内部的那个向量
            Vector3f p = mapMatrix.transformPosition(SparkMathKt.toVector3f(locator.getTranslation()));
            g.fill((int) p.x - 4, (int) p.y - 4, (int) p.x + 4, (int) p.y + 4, seat.color());
        }
    }
}
```

- **变换顺序**：`PoseStack.mulPose` 右乘，后调用的先作用到顶点。俯视必须先 `X(-90)`（把世界纵轴换成屏幕纵轴），再绕世界 Y 偏航 `180 - heading`。写成“先偏航再俯视”时，偏航作用的是一张已经躺倒的图：屏幕纵轴恒等于世界 z，偏航只把**世界高度**混进横轴——`heading = 45°` 时车头落在 `(0.50, 0.71)`，`heading = 90°` 时退化到 `(0, 0)`，只有正南正北碰巧正确。验收判据：任意朝向都应有 `front → (0, -1)`、`right → (+1, 0)`（GUI 坐标 y 向下）。
- **深度取反**：GUI 的 `+z` 朝向观察者，而 `X(-90)` 把世界 `+y` 映到 `-z`。透视渲染不测也不写深度，所以这个取反对当前画面没有可见影响（`NO_CULL` 下绕向也看不见）；保留它是为了矩阵本身仍然表达“从上看下去”的语义——将来换回测深度的 render type 时不会又变成仰视图。
- **居中偏移按像素算**：包围盒中心的平移必须叠在缩放之后（矩阵链更左侧的 `translate(-centerSx * fit, -centerSy * fit)`）。写在旋转之后时它是世界坐标里的位移，与车体朝向无关，转向后车体就偏出面板中心。
- **没有相机偏航**：GUI 图层是屏幕固定的正交空间，车头朝上只差一个偏航角，不需要 3D HUD 那套 `cameraYaw` 抵消、`PIXEL_SCALE` 换算或 Z 压扁。
- **模型用实时的 `SubPart`**：`getRenderWorldPositionMatrix`、`getBones`、`getDurability` 都是现成的，不需要 `VehicleAnimatable`，也没有 `new VehicleData(vehicle)` 的拷贝成本。取骨骼前必须先判 `getModel() == null`：`SubPartAttr.getBones` 直接解引用 `OModel.getORIGINS()`（`SubPartAttr.java:357-363`），资源重载期间会 NPE；`PartEntityRenderer` 也是在取到模型后立刻返回（`PartEntityRenderer.java:77-79`）。
- **两层 + 无纹理**：外壳用 `MMRenderTypes.solidXray()`——`POSITION_COLOR`、双面、不测也不写深度、输出主目标。所有面都参与混合，外壳越淡越能看穿；内构层随后用同一批顶点再画一遍，接近不透明，于是“外壳半透明 + 内构实色”。纯色 shader 不读 `Lighting`，不必像 `GuiAnimatable` 那样成对调用 `setupForEntityInInventory` / `setupFor3DItems`。双面是必需的：俯视图的几何朝向取决于载具姿态，不能依赖绕向。
- **两遍而不是一遍**：内构骨骼必须在**全部**外壳之后提交。按零件混在一遍里会被后面零件的外壳压暗，于是“靠近镜头的那几个零件的子系统特别亮”这种与结构无关的亮度差就出现了。
- **标记位置逐帧算**：快照只存座位引用与布局，世界坐标每帧用 `getLerpedLocatorWorldTransform(..., partialTick)` 现取。`getTranslation()` 返回的是变换内部的向量，`mapMatrix.transformPosition` 会就地改写它，必须传副本；把世界坐标写进只在结构变化时重建的快照，会让标记在行驶时留在原地。
- **原点与偏航也逐帧**：快照只存参考 SubPart 引用与局部前向轴 `frontLocal`（含符号），不存 `origin`/`headingDeg`。它们随车辆平移与转向每帧变化；缓存住会出现两种错位：车开走时地图按旧原点平移，而座位点用新的世界坐标，标记整体漂出面板；转向后车头不再朝上，模型转了而标记没转。
- **`fit` 在座位快照重建时算一次**：先用 `fit = 1` 投出各 SubPart 包围盒的 8 个角点，投影时按车体坐标（偏航归零）做，所以所得缩放对行驶与转向都不变；再按面板可用尺寸求 `min(panelW / 宽, panelH / 高)`。炮塔转动只改变角点在车体坐标里的位置，需要时跟着结构事件一起重算。
- **批次与顺序**：模型提交后 `g.flush()` 再画标记。透视渲染不写深度，所以不需要关深度测试、也不需要清深度缓冲；要守的只有提交顺序——外壳 → 内构 → 标记。
- **外壳色**：`modelColor(sub)` 是低透明度基色，健康零件不传白色（纯色几何里白色就是一块亮斑）；零件低于 50% 或已毁坏时同一层偏红。
- **裁剪与位置**：面板外 `g.enableScissor(...)`；面板放屏幕左下，避开准星、热栏和右下弹药 HUD；常驻显示时面板尺寸收小，默认不覆盖驾驶视线中心与准星。

**不采用：把座位图做成 3D HUD 元素。**

`AssemblyHud3D` 那套（`IHud3DElement` + `Hud3DContext` + `RenderLevelLastEvent`）能画模型，也能画面板、文字和引线，但它为了在相机空间里工作额外背了三件事：`PIXEL_SCALE` 的像素换算、`cameraYaw` 抵消（锚点跟着相机，车头朝上要减掉相机偏航）、以及 `alwaysVisibleSolid` 这类关深度的自定义 RenderType。座位图是屏幕固定、纯俯视的面板，这三样都用不上；而且 3D HUD 在原生 HUD 之前绘制，面板会被热栏/准星那一层盖住。

那套仍然适合“在世界里指着某个零件”的装配 HUD；座位图留在 2D 图层更直接。

**兜底：不渲染模型，只画座位点和一个车体矩形。**

- 车体矩形取车体 SubPart 的本地碰撞包围盒：`subPart.collisionShape.boundingBox(new Vector3f(), Quaternion.IDENTITY, null)`（`AbstractPart.calculateProjectedArea` 已在用，`AbstractPart.java:562-564`）。这个盒子在 SubPart 本地空间，8 个角点要先乘 `getRenderWorldPositionMatrix(1f)` 再按 §5.2.2 投影，否则整个矩形会落在车体原点。
- 该矩形在斜向时比真实车体宽，但座位点位置不受影响；用于模型渲染不可用或性能吃紧时。
- 不引入新渲染路径，只多 8 个点变换。

**不采用：逐模型方块的 2D 投影。**

要遍历骨骼与 `OCube`、变换全部顶点、按 SubPart 排序、合并矩形并维护缓存失效；K17 约 2028 个方块、MTLB 约 1160 个，单次重建是万级顶点变换，逐块 `fill` 还会带来上千次 GUI 绘制调用。它只为得到一层剪影，收益不抵成本；若以后需要“线框/工程图”风格，再单独立项。

四条路线的量级对比：

| 路线 | 新增代码 | 主要风险 |
| --- | --- | --- |
| 2D HUD 图层 + 实时 SubPart | 面板/标记/文字约 120–200 行 + 俯视变换约 40 行 | GUI 层的光照与批次；标记深度 |
| 3D HUD 元素（`IHud3DElement`） | 约 300–400 行，复用 `Hud3DContext` | 相机空间偏航抵消、像素缩放、绘制早于原生 HUD |
| AABB 矩形 + 座位点（兜底） | 约 100 行 | 斜向时车体矩形偏大，看不出炮塔/车轮 |
| 逐模型方块 2D 投影 | 约 300–400 行，另加矩形合并与缓存 | 排序、缓存失效、上千次 `fill`，且是一条新几何管线 |

**常驻渲染的代价。**

座位图在车内每帧渲染，模型渲染是持续开销：K17 约 2028 个方块、MTLB 约 1160 个，等于在 GUI 层每帧再提交一遍整台载具。参照物是物品栏里的装配体图标——`CustomModelItemRenderer.renderByItem` 在 `ItemDisplayContext.GUI` 下就把整台装配体画进物品槽（`CustomModelItemRenderer.java:42-58` → `:208` 的 `renderVehicleAnimatable`），说明这条绘制路径本身可以承受整车规模；差别只在 HUD 面板常年可见。物品图标那边还把 `VehicleAnimatable` 按数据组件缓存起来（`ICustomModelItem`），座位图没有这层缓存 —— 它画的是实时 `SubPart`，每帧重新提交，这正是“常驻”的主要成本。

按这个量级，默认常驻可以接受，但要保留三条退路：

1. **只在需要时显示**：座位数 ≤ 1 不画；`MMClientConfig` 提供“常驻 / 仅换座后显示 / 关闭”三档，默认常驻。
2. **降级形态**：换成 §5.2.4 末尾的“AABB 矩形 + 座位点”，把每帧的整车提交换成 8 个点变换；配置或检测到帧时间异常时切换。
3. **纹理缓存**：若实测仍重，用已有的 `OffscreenFbo`（`client/fbo/OffscreenFbo.java`）把车体渲染成一张纹理，只在结构变化/座位变化时重绘，逐帧只画一个 quad 与座位标记。这一档留作性能兜底，不进入 v1。

### 5.3 占用同步

`SeatSubsystem.occupied` 目前只是服务端字段，客户端看不到其他座位的占用。推荐增加一个同步布尔：

```java
// SeatSubsystem
private static final EntityDataAccessor<Boolean> DATA_OCCUPIED_ID =
        SynchedEntityData.defineId(SeatSubsystem.class, EntityDataSerializers.BOOLEAN);

@Override
protected void defineSynchedData(SynchedEntityData.Builder builder) {
    super.defineSynchedData(builder);
    builder.define(DATA_OCCUPIED_ID, false);
}

private void setOccupied(boolean value) {
    this.occupied = value;
    if (!getLevel().isClientSide()) synchedData.set(DATA_OCCUPIED_ID, value);
}

@Override
public void onSyncedDataUpdated(EntityDataAccessor<?> accessor) {
    super.onSyncedDataUpdated(accessor);
    if (accessor == DATA_OCCUPIED_ID) this.occupied = synchedData.get(DATA_OCCUPIED_ID);
}
```

- **所有写点都走 `setOccupied`**：`setPassenger`（`SeatSubsystem.java:108`）、`removePassenger`（`:168`）这两处现在直接写 `occupied` 字段，再加上 §5.5 新增的 `detachForSwitch`/`attachForSwitch`，四处统一调 `setOccupied`。只加同步值而不改这些写点，`DATA_OCCUPIED_ID` 永远不会变，HUD 会把所有座位当成空位——不报编译错，只在运行期表现为“占用永远为空”。验收方式：`rg 'occupied\s*=' src/main/java` 只剩字段声明与 `setOccupied` 内部一处。
- `AbstractSubsystem` 已有的 `syncToClient()` 会在服务端每 tick 发送脏值（`AbstractSubsystem.java:310-318`），`forEachSyncedValue` 被 `SubsystemController.pushAllTo` 用来给刚建好载具的玩家补全量（`SubsystemController.java:127-134`）；晚加入的玩家因此能拿到当前占用。
- `SynchedEntityData.set` 在服务端也会回调 `onSyncedDataUpdated`，客户端则由 `assignValues` 同时回调单 accessor 与列表两个版本；上面的覆写两边都会走，读回值恒等于刚写入的值。
- 占用变化很少，同步成本可忽略；不需要额外的座位图请求包。
- **“谁在哪个座位”不在 v1**：`occupied` 只回答空/占。其他玩家的 `controllingSubsystem` 只在上下车那一刻由交互包/输入包的重放写入（§5.4.1），晚加入的客户端拿不到，`MMPartEntity.getPassengerAttachmentPoint` 对那些乘客返回 `(0,0,0)`——这是既有行为，座位图不负责修。要显示名字或修好晚加入的坐姿，需要再加一个 `DATA_PASSENGER_ID` 之类的权威同步值并据此维护 `controllingSubsystem`，列为 P4。

### 5.4 换座协议与服务端校验

#### 5.4.1 载荷

```java
// C2S：目标座位；循环键与将来的选座界面共用
public record SeatSwitchPayload(int subPartId, String seatName) implements CustomPacketPayload { ... }

// S2C：纯状态包，成功时广播给维度内所有玩家，失败只回发起者
public record SeatSwitchResultPayload(int passengerEntityId, int subPartId, String seatName,
                                      int controlGroup, boolean success, Reason reason)
        implements CustomPacketPayload {
    public enum Reason { OK, NOT_SEATED, SAME_SEAT, OCCUPIED, UNAVAILABLE,
                         DIFFERENT_ASSEMBLY, NO_ENTITY, MOUNT_FAILED, RATE_LIMITED }
}
```

- 注册到 `input` 协议组（`MMPayloadRegistry.java:35`），新增载荷时按项目约定提升该组版本（当前 `input:2.2.0`）。
- **载荷带具体目标，不带方向**：循环键先在本地算出下一个可用座位，再把座位名发出去。将来的点击/数字键/径向菜单直接填被点的座位，不需要新增第二个载荷。
- **服务端不排序**：它只解析 `subPartId + seatName`，确认座位存在、同装配体、可用、未被占。两端的顺序即使不同也不影响结果。
- **编号不进协议**：`1/3` 里的序号由客户端按自己的顺序算，actionbar 与面板标题都读本地排序；服务端既不生成也不回带 `index`/`total`——它与“服务端不排序”是同一条约束的两面。两端的编号在极端情况下可能不同（客户端晚一个网络 tick 的占用），但编号只是显示顺序，不影响换到哪个座位。
- **`controlGroup` 是状态，不是座位序号**：结果包带上目标座位复位后的活动控制组索引，客户端照抄写入本地副本（§5.11）。上一条“编号不进协议”只针对**座位序号**——服务端不排序，生成不了；控制组索引是服务端在 ⑤ 里刚写下的真实状态，它本来就有值，而且两端必须相等才不会让视角包与绑定索引失配。
- **结果包必须是纯状态包**：只写 `passenger`/`occupied`/`controllingSubsystem` 与相机目标，不调 `interactBox.interact`、`setPassenger`、`removePassenger`，注册为 `playToClient`。这些字段没有原版包承载（`ClientboundSetPassengersPacket` 只带骑乘关系），骑乘实体本身也不是可靠信号——同一 SubPart 换座时 `getVehicle()` 根本不变。
- **不能照抄交互包的客户端处理**：`SubsystemInteractPayload` 的 `clientHandler` 是在客户端**重放** `interactBox.interact`（`SubsystemInteractPayload.java:55-57` → `onInteract` → `setPassenger`），服务端把同一个包转发给维度内所有玩家（`:59-65`）。照搬到换座会在客户端跑一遍 `setPassenger`/`removePassenger`：重采样上车点、`stopRiding`、给本地玩家加一次冲量（`SeatSubsystem.java:161-164`），正是 §5.5 要消除的副作用，跨实体时还会与原版 riders 包抢骑乘关系。换座只沿用它的**广播范围**（`PacketDistributor.sendToPlayersInDimension`），不沿用它“收到就重放”的处理器写法。
- **这些字段平时由谁写**：客户端 `controllingSubsystem` 只有两个写入点（`SeatSubsystem.java:114`、`:160`），靠“上车走交互包重放、下车走输入包重放”触达（`RegularInputPayload` 的 `LEAVE_VEHICLE` 分支）。换座是一条新的状态迁移，没有现成载荷承载，所以必须自带这个 S2C；只回发起者会让其他客户端上的乘客位置退回实体原点。
- 失败不广播：其他客户端不需要知道谁按键失败，避免无谓流量。
- 不做成 `GuiAction`：`GuiAction` 的输出是信号频道，而换座需要校验乘客关系并切换骑乘实体，用独立 C2S 载荷更直接。

#### 5.4.2 服务端校验清单

顺序执行，任一失败返回对应 `Reason`：

1. `ObjectManager.getDestroyableObject(level, subPartId)` 是 `SubPart`；
2. 该 SubPart 的 `subsystems.get(seatName)` 是 `SeatSubsystem to`；
3. 玩家当前 `controllingSubsystem` 是 `SeatSubsystem from`；
4. `from.passenger == player`，且 `player.getVehicle() == from.getSubPart().getEntity()`；
5. `from != to`；
6. `from.getSubPart().getPart().assembly == to.getSubPart().getPart().assembly`；
7. `to.isActive() && !to.isDestroyed()`，且 `!to.getSubPart().isDestroyed()`；
8. `to.passenger == null && !to.isOccupied()`；
9. `to.getSubPart().getEntity() != null`；
10. 节流：同一玩家 10 tick 内只接受一次（超限回 `RATE_LIMITED`，不再往下走）。

这套校验与 `ViewInputPayload` 的“来源座椅/当前乘客/实际控制对象必须同时匹配”是同一风格（`ViewInputPayload.java:80-118`）；`SeatSwitchPayload` 的额外约束是目标座位与当前座位同属一个装配体。

第 10 步不能省：一次成功换座要跑上车点交接、两组信号切换、额外质量与物理激活、`recalculateCameraDistance()`（按子零件数线性扫）。服务端没有别的节流，客户端那侧的重按抑制只是本地约束，挡不住改过的客户端或连点。

### 5.5 专用切换路径

不要直接复用 `setPassenger` 的完整下车/上车流程：它会在换座时触发 `stopRiding` → `getDismountLocationForPassenger` → `dismountTo`，还会给客户端玩家加一次冲量（`SeatSubsystem.java:157-176`）。对同 SubPart 的 K17/MTLB/2S38，这等于“先下车再上车”；对跨 SubPart 的 Van，还可能把玩家短暂放到车外。

整条路径都在服务端主线程完成。**客户端不做预测**：在结果包到达之前，它不改任何座位状态，也不改骑乘关系（§5.4.1），所以不存在“客户端先设置、失败再撤回”这种流程。下面讨论的失败路径全部是服务端事务：服务端在一次调用里依次改座位与骑乘，唯一可能失败的是挂载，而它被排在最前面。

推荐新增 `SeatSwitchHelper`，区分两种情况：

#### 情况一：新旧座位在同一个 SubPart 实体上（K17/MTLB/2S38）

```java
// 1. 先交接上车点缓存（此时 player 仍骑在旧实体上、控制子系统仍是 from）
to.updateBoardingOffset(player);

// 2. 清旧座位状态，但不 stopRiding
from.detachForSwitch(player);   // 清 controllingSubsystem / passenger / occupied / 信号 / 额外质量

// 3. 接管新座位状态，但不 startRiding（玩家已在同一实体上）
to.attachForSwitch(player);     // 设置 passenger / occupied / 信号 / 额外质量 / 控制子系统

// 4. 玩家的 attachment point 在下一 tick 由 getPassengerAttachmentPoint 按新座位计算
```

- 不调用 `stopRiding`/`startRiding`，不触发下车搜索、不触发冲量、不设置 `boardingCooldown`。
- `MMPartEntity.getPassengerAttachmentPoint` 每 tick 读取 `controllingSubsystem` 的座位 locator（`MMPartEntity.java:365-374`），所以玩家会平滑地“坐”到新座位。
- 相机在客户端下一 tick 检测到 `seat != controllingSeat`，同装配体继承瞄准目标（`CameraController.java:143-157`）。

#### 情况二：新旧座位在不同 SubPart 实体上（Van 等）

先挂载，再改座位状态：挂载是唯一会失败的一步，把它排在所有座位字段之前，失败路径就什么都不用撤：

```java
to.updateBoardingOffset(player);   // ① 仍在旧实体上、旧座位仍是控制座位，能取到旧座位的上车点

SeatSwitchHelper.beginSwitch(player);   // ② 标记“本次 stopRiding 是换座，不要找下车点”
boolean mounted;
try {
    mounted = player.startRiding(newEntity, true); // force 绕过 canAddPassenger 与 boardingCooldown
} finally {
    SeatSwitchHelper.endSwitch(player);
}
if (!mounted) return MOUNT_FAILED;  // ③ 座位状态一个字段都没动，直接返回

from.detachForSwitch(player);      // ④ 清旧座位（不 stopRiding、不加冲量）
to.attachForSwitch(player);        // ⑤ 接管新座位：passenger / occupied / 信号 / 额外质量 / 控制组
```

`startRiding` 内部会 `stopRiding`，服务端会走 `dismountVehicle` → `getDismountLocationForPassenger`（`LivingEntity.java` 1.21.1）。此时旧座位仍宣称着这名乘客——`findOccupiedSeat` 按 `seat.passenger == passenger` 匹配（`MMPartEntity.java:554-560`），所以搜索会真的去算一个车外落点并把玩家挪过去。在 `MMPartEntity.getDismountLocationForPassenger` 开头加一个判断：

```java
if (SeatSwitchHelper.isSwitching(passenger)) return passenger.position();
```

这样“下车点搜索”直接返回当前位置，挂载过程不会把玩家放到车外。这个标记只需在服务端主线程的这一次调用期间存在，用 `Set<UUID>` 或 `ThreadLocal` 即可；`finally` 必须清理。

挂载期间旧座位短暂仍宣称着这名乘客（④ 才清），但 ②–⑤ 全在同一次方法调用里完成，中间没有 tick 边界，`SeatSubsystem.onTick` 观察不到；反过来先清旧座位再挂载，就会在挂载失败时多出一个必须手动撤销的中间态。

#### 状态方法与现有 `setPassenger` 的关系

- `detachForSwitch` 的内容与 `removePassenger` 相同，但**不调用 `stopRiding`、不加冲量**。
- `attachForSwitch` 的内容与 `setPassenger` 相同，但**不重复移除旧座位**，也不调用 `startRiding`：同实体路径本来就不需要挂载，跨实体路径由调用方先挂载成功再调它。
- `updateBoardingOffset` 已经处理“同一装配体换座”的世界坐标交接（`SeatSubsystem.java:136-155`）；只要在清 `controllingSubsystem` 之前调用，就能复用。它目前是 `private`，所以 `SeatSwitchHelper` 必须和 `SeatSubsystem` 同包（`common/mech/subsystem/`），或者由 `SeatSubsystem` 暴露 `transferBoardingOffsetFrom(from, passenger)`。`SeatOrder` 放在子包 `common/mech/subsystem/seat/` 时，`SeatSwitchHelper` 不能跟着搬进去——跨子包调用不到包内可见方法。
- 新座位接管时把活动控制组复位到该座位预设的默认值（`resetControlGroupToPreset()`，§5.11），避免继承上一个乘客留下的子组；旧座位的子组保持不变，留给下一次进入。
- 服务端切换成功后广播 `SeatSwitchResultPayload`；每个客户端按 `passengerEntityId` 找到本地实体，对目标座位应用：
  - **同 SubPart**：骑乘关系没变，不调用 `stopRiding`/`startRiding`；只清旧座位的客户端字段，再按结果包里的 `passengerEntityId` 设置新座位的 `occupied` 与 `controllingSubsystem`。`passenger` 字段的权威值在服务端，客户端写它只用于显示，不参与 §5.2.3 的 `available()` 判定。
  - **跨 SubPart 且乘客是本地玩家**：主动 `player.startRiding(newEntity, true)`，不等原版包。服务端 `startRiding` 触发的 `ClientboundSetPassengersPacket` 由 `ServerEntity.sendChanges()` 到**下一 tick**才发，广播包会先到；客户端自己对齐后，原版包到达时重建 passengers 列表，是幂等的。
  - **跨 SubPart 且乘客是其他玩家**：不动骑乘关系，交给原版 riders 包；只更新该实体的 `controllingSubsystem` 与两个座位的字段。其他实体由客户端位置同步驱动，提前 `startRiding` 反而可能和原版包打架。
  - 客户端 `stopRiding` 不调用 `dismountVehicle`，没有下车点搜索，也没有冲量。
  - 必须更新 `controllingSubsystem`：`MMPartEntity.getPassengerAttachmentPoint`（`MMPartEntity.java:366-374`）在每个客户端都读它，漏掉就会让那个乘客挂在实体原点。

#### 挂载失败：没有需要回滚的东西

- 跨实体路径把挂载排在所有座位字段之前（上一节的 ③ 在 ④⑤ 之前），失败时 `from`、`to` 的 `passenger`、`occupied`、`controllingSubsystem`、信号、额外质量全是原值，直接回 `MOUNT_FAILED`。
- 同实体路径不改变骑乘实体，根本没有可失败的挂载。
- 唯一可能的残留是“玩家被摘了骑乘却没挂上新实体”。`startRiding` 的前置判定（`canRide`、`canAddPassenger`、模组的挂载事件）都在摘除骑乘之前，正常不会留下这种状态；万一留下，`SeatSubsystem.onTick` 的 `passenger.getVehicle() != part.entity` 重试（`SeatSubsystem.java:69-84`）会把玩家挂回旧实体——此时旧座位仍宣称着他，重试目标正好正确。
- 反过来把 `to.attachForSwitch` 放在挂载之前，失败时目标座位已经宣称了这名乘客，就得手动撤销；漏掉那一步会让两个座位同时宣称同一乘客，`onTick` 每 tick 让两边各 `startRiding` 一次，`occupied` 永久为 `true`，控制关系与信号反复覆盖。这也是“先挂载”的理由。
- 靠 `onTick` 重试来完成“正向切换”（座位已易主、等挂载成功）不可行：取消若长期生效，座位会永久占用而玩家并不在车上，`controllingSubsystem` 仍指向它，相机与位置都会错。
- GameTest：让挂载必定被拒 → 返回 `MOUNT_FAILED`；两个座位的 `occupied` 与 `controllingSubsystem` 都保持原值。

#### 换座期间在途的旧座位输入

换座请求发出到结果包到达之间，客户端仍按旧座位注册着按键：`handleNormalInputs` 每 tick 用当前 `controllingSubsystem` 的 `getMergedBindings()` 重建一次（`RawInputHandler.java:516-538`），所以这段时间按下的键会带着旧座位的 `subPartId + subSystemName + bindingIndex` 发出。服务端要是在处理完换座之后才收到它们，就会把 `detachForSwitch` 刚清掉的信号重新写回旧座位——玩家的手还按在油门/扳机上，旧座位的线路被重新点亮一次。

两个载荷缺这层校验：

- `ControlBindingPayload.serverHandler` 只校验子系统存在且 `isActive()`（`ControlBindingPayload.java:55-70`）。
- `MovementInputPayload.serverHandler` 更宽松：只校验子系统存在，成功后还把整个载荷广播给维度内所有玩家（`MovementInputPayload.java:64-83`），`clientHandler` 收到就重放 `setMoveInputSignal`（`:57-62`）。换座后在途的一包油门因此会同时做三件事：把旧座位刚清掉的移动信号写回去、让载具按这条指令继续行驶（信号保持到下一次写入才被覆盖）、并在所有客户端上重放一遍。

按 `ViewInputPayload` 的判据补上来源校验（`ViewInputPayload.java:88-94`），两个 handler 共用一个判断：

```java
// 来源必须仍是“这个子系统 + 这名玩家在座”，与 ViewInputPayload 的第 1–3 条一致
static boolean isControlledBy(AbstractControllableSubsystem sub, Player player) {
    if (!sub.isActive()
            || ((IEntityMixin) player).machine_Max$getControllingSubsystem() != sub) return false;
    if (sub instanceof SeatSubsystem seat
            && (seat.passenger != player
                || seat.getSubPart().getEntity() == null
                || player.getVehicle() != seat.getSubPart().getEntity())) return false;
    return true;
}
```

```java
// ControlBindingPayload.serverHandler
if (!(subsystem instanceof AbstractControllableSubsystem controllable) || !isControlledBy(controllable, context.player())) {
    return;
}
controllable.sendBindingSignal(payload.bindingIndex(), payload.eventType());
```

```java
// MovementInputPayload.serverHandler
if (!isControlledBy(subSystem, player)) return;
subSystem.setMoveInputSignal(payload.input(), payload.inputConflict());
PacketDistributor.sendToPlayersInDimension((ServerLevel) player.level(), payload);
```

- 不会误伤正常按键：客户端只在控制关系仍指向该座位时才构造这些包，结果包一到，下一 tick 就换成新座位的绑定列表与移动目标。
- 与 §5.4.2 的服务端校验是两件事：那十条决定“能不能换”，这层校验保证“换完之后旧座位的输入不再生效”。
- `RegularInputPayload` 已经是这个形状（`:135`、`:144`、`:156` 三处都先取 `controllingSubsystem` 再分发），`ViewInputPayload` 自带完整校验；补完这两个之后，所有“客户端持有当前控制目标”的输入载荷判据一致。
- 用例：换座成功后立刻发旧座位的 `ControlBindingPayload` 与 `MovementInputPayload` → 都被拒绝，旧座位的按键信号与移动信号保持空、且不广播；同一 tick 内针对新座位的包仍然生效（§七）。

### 5.6 输入

| 输入 | 默认 | 行为 |
| --- | --- | --- |
| 循环换座键 | `X`（`KeyCategory.GENERAL`） | 按几何顺序算出下一个可用座位，发 `SeatSwitchPayload`；算不出时不发包；不打开界面 |
| 反向循环（可选） | `Shift+X` | 逆序找上一个可用座位；v1 只绑定顺向，逆序键位留空 |
| 车外交互 | `F` | 保持现有交互区流程 |

座位图没有专属按键：它由“是否在座位控制”决定显示，配置里可切到“仅换座后显示”或关闭。

实现要点：

- 用 `KeyHooks.EVENT(KeyBinding.cycleSeatKey).OnKeyDown(...)`，与 `generalCycleCameraKey` 同一条路径（`RawInputHandler.java:470-471`）。`OnKeyDown` 不是订阅，它每次调用时检查“按下刻度 == 1”（`KeyHooks.java:306-309`），所以必须放在 `RawInputHandler.handleNormalInputs` 里每 tick 调用的位置；写成“初始化时注册一次”将永远不会执行。`KeyHooks` 只在首次按下触发，不处理 `GLFW_REPEAT`。
- 只在 `controllingSubsystem instanceof SeatSubsystem` 时发包；`mc.screen != null`、`hideGui`、`CameraController.isCameraMode()`、非座位控制时不响应。
- 不消费热栏数字键，不需要在 `handleVanillaInputs` 里做额外拦截。
- 手柄：`KeyHooks` 支持手柄子绑定，单键即可覆盖全部座位数。
- `pending` 状态：发包后 20 tick 内忽略重复按键，避免连发；结果包到达后清除。

### 5.7 座位图 HUD 渲染

#### 5.7.1 为什么是独立图层，而不是 `HudLayout`

`HudLayout`/`HudElement` 是内容包作者写死的静态元素数组；座位图的元素数量、位置、颜色都来自运行时装配结果，且需要逐帧更新占用。把它塞进 `CustomHud` 需要新的 `HudStyle` 载体和动态元素机制，成本高于收益。

推荐新增 `client/render/gui/hud/SeatMapHud.java`，实现 `LayeredDraw.Layer`，在 `MMGuiManager.registerHud` 里 `registerAboveAll`（参考 `MMGuiManager.java:49-57`）。`registerAboveAll` 的图层每帧都会被调用，“常驻”只是 `render` 开头的条件判断，不产生额外的注册或事件成本。

显示条件（全部满足）：

- 玩家 `controllingSubsystem instanceof SeatSubsystem`；
- `mc.screen == null`、`!mc.options.hideGui`；
- `!CameraController.isCameraMode()`（炮镜/设备视角下让位）；
- 座位数 > 1；
- 配置里座位图不是“关闭”。

面板与标记用 `GuiGraphics` 的 `fill`/`drawString`；车体用 GUI 内的模型渲染（与物品图标同一路径，见 §5.2.4）。v1 不引入 Shader 或自建投影矩阵；`OffscreenFbo` 只作为性能兜底（§5.2.4），不进入 P0–P2。AUI 页面适合“静止的详情页/控制面板”，不适合“行驶中持续刷新的几何覆盖层”。

#### 5.7.2 面板结构

```text
┌────────────────────────────┐
│ Driver Seat           1/3  │  当前座位的名字 + 当前序号/总数
│ ┌────────────────────────┐ │
│ │       ▲                │ │  车头方向
│ │ [1]        [3]         │ │  [1] = 你（青色）  [3] = 空位
│ │              [2]       │ │  [2] = 下一个（绿色）
│ └────────────────────────┘ │
│ ● 你 ● 下一个 ● 空 ● 占用  │  图例
│ X · Gunner Seat            │  键位提示 + 下一个座位的名字
└────────────────────────────┘
```

- 常驻面板要小：约 156×128，地图区约占一半高度；贴屏幕左下角，底部留 24px 让开热栏（热栏高 22px），右边缘不越过准星与驾驶视线中心的横向范围。打开任何界面（包括聊天框）时 HUD 整体隐藏；不开界面时收到的聊天行与面板同处左下角，面板画在聊天层之上，要读历史聊天按 `T` 打开聊天框即可。位置、缩放、透明度进 `MMClientConfig`。
- 背景 `0xE610151C` 一类的深色半透明；1px 边框；顶部 2px 主题色。
- 地图区域内加 8px padding；车体按 §5.2.4 在面板内渲染；座位标记画在模型之上——车体不写深度，标记在 `flush()` 之后提交。
- 面板标题写当前座位的名字（§5.8），右侧固定显示 `当前序号 / 总座位数`；名字过长时按像素截断并补省略号，不挤压编号。
- 地图里只画图标与编号：标记在地图上互相重叠，再压文字既读不全也遮住车体。名字只在面板标题、键位提示与 actionbar 三处出现。
- 键位提示写成 `X · <下一个座位的名字>`；没有下一个可用座位时退回“没有其它可用座位”。
- 图例与标记同色：你（青）、下一个（绿）、空（白）、占用（琥珀），一眼对上地图上四种最常见的底色；灰色“不可用”与红色失败闪烁只出现在标记上，图例不列。
- 车头方向只用一个 `▲` 画在地图区域顶部中央，指向当前参考系的 front。
- 常驻形态允许收起：配置为“仅换座后显示 2 秒”时，面板在按键后淡入、随后淡出，其余时间不占屏幕。

#### 5.7.3 座位标记

| 状态 | 颜色 | 图形 | 编号 |
| --- | --- | --- | --- |
| 当前座位 | 青色 `0xFF4FD1FF` | 实心方块 + 外环 | 居中显示 |
| 下一个目标 | 亮绿 `0xFF6CFF6C` | 实心方块 + 外环（按键后短暂放大） | 居中显示 |
| 空位 | 白色 `0xFFF2F6FA` | 实心方块 | 居中显示 |
| 已占用 | 琥珀 `0xFFFFB020` | 实心方块 | 居中显示 |
| 不可用 | 灰色 `0xFF808080` | 实心方块 | 居中显示 |
| 本次失败的目标 | 红色 `0xFFFF4040` | 实心方块，随失败提示一起淡出 | 居中显示 |

- 颜色优先级：当前 > 下一个 > 已占用 > 空位 > 不可用；当前与下一个不会指向同一个座位。
- 标记 10px（一位数编号）或 12px（两位数编号），随 GUI scale 放大；编号用深色字居中画在方块上，与标记底色形成对比。
- 编号与“当前/总数”“actionbar”读同一份本地顺序，三处不会互相矛盾。
- “下一个”标记在顺序重算、占用变化、当前座位变化后刷新，不需要按键。
- 座位耐久不染标记色：它染在车体上——外壳层淡染零件耐久，内构层用子系统自己的耐久色（§5.7.5）。占用状态的颜色始终优先于耐久。
- 重叠座位按 §5.2.3 错开，并画细引线；高度差可加 `↑`/`↓` 角标。
- 座位数很多（MTLB 10 个）时标记会挤在一起；编号是唯一能区分相邻座位的文字，宁可让标记大一号也不要让它读不出来。

#### 5.7.4 交互反馈

- 按下循环键后，“下一个”标记短暂放大或脉冲；结果包到达后：
  - 成功：青色标记移到目标；旧座位变白（空）；“下一个”重新计算并高亮；
  - 失败：目标标记闪红，actionbar 显示原因（`OCCUPIED`/`UNAVAILABLE`/`DIFFERENT_ASSEMBLY`）。
- 只有 `passengerEntityId` 等于本地玩家时才移动青色标记并写 actionbar；其他玩家换座只更新对应标记的占用颜色。
- actionbar 显示 `1/3 · Driver Seat`（当前座位），与面板标题的编号一致。
- 没有可用座位时，HUD 底部提示“满员”；按键无操作，客户端不发包（本地顺序与占用已足够判断），服务端收到包时仍会复核。
- 座位图被配置关闭时，循环键仍然工作，只是没有可视反馈——actionbar 仍显示结果。

#### 5.7.5 零件与子系统状态层

车体在 GUI 内逐 SubPart 渲染，颜色就是一项渲染参数，状态层不需要第二套着色管线。分两层：

- **外壳层**：按 `SubPart.getDurability() / getMaxDurability()` 着色，满耐久用剪影基色，低于 50% 或已毁坏时偏红；整层透明度很低，作用是让外壳能被看穿。
- **内构层**：带 hitbox 的骨骼按所属**子系统**的耐久染色，接近不透明，所以能透过多层外壳读出来。色带与内构 HUD 共用 `SubsystemDurabilityColors`（满耐久绿 → 黄 → 橙 → 红 → 已毁坏深灰），两边读数不会打架。
- 内构层只画“hitbox 名字能对上骨骼、且该 hitbox 填了 `subsystem`”的骨骼；子系统 `is_hidden` 时跳过，与内构 HUD 的可见性规则一致。
- 内构层必须在全部外壳之后提交（§5.2.4 的“两遍”），否则会被后面零件的外壳压暗。
- `MMClientConfig` 提供开关：关掉时外壳仍透明，但内构不染色。
- 详细的连接点完整性、HitBox 边界仍由 O 键内构查看承担；座位图只回答“哪个子系统伤了”，两者互补，不要把座位图做成第二个内构查看器。

#### 5.7.6 缓存、线程与重算窗口

- 座位图快照只存**不随帧变化**的东西：参考 SubPart 引用、局部前向轴 `frontLocal`（含符号）、`fit`、排好序的座位引用、子零件列表。**世界坐标、原点、偏航都不进快照**：绘制时按同一个 `partialTick` 从参考 SubPart 的 `getWorldPositionMatrix(partialTick)` 取平移与旋转（§5.2.4）。模型逐帧直接用实时 `SubPart`，不做数据拷贝。
- 把原点或偏航缓存起来会立刻错位：车辆平移后地图仍按旧原点平移，而座位点用的是实时世界坐标，标记会整体漂出面板；车辆转向后模型转了、偏航还是旧值，车头不再朝上。
- 客户端 tick 与 HUD 渲染在同一个线程（都是主线程），不存在“发布给渲染线程”的问题；tick 里只更新占用与当前座位，重建快照走下面的事件窗口。
- 座位标记的屏幕坐标逐帧由 `mapMatrix` 变换：座位世界坐标 → 减去原点的相对坐标 → 屏幕坐标，只做 3–10 次点变换，不需要缓存。
- 渲染时不需要访问物理世界：`getLerpedLocatorWorldTransform` 与 `getRenderWorldPositionMatrix` 都是客户端可用的纯读取，模型渲染用 `-origin` 平移到载具相对空间。

**结构变化的重算窗口：**

- `VehicleCore` 在若干路径末尾各自调 `subSystemController.onVehicleStructureChanged()`，同时各自散落着 `updateTotalMass()`、`recalculateMaxHp(...)`、`recalculateCameraDistance()`。这四件事总是成组出现、且顺序敏感（见下面 `:895` 一条），应当收进一个方法；只在 `SubsystemController` 里加钩子不够——那一层的语义是“让子系统重新握手”。
- 包一个 `VehicleCore.onStructureChanged(Reason)`，按顺序做五件事：
  1. `updateTotalMass()`；
  2. `recalculateMaxHp(modeFor(reason))`；
  3. `recalculateCameraDistance()`；
  4. `subSystemController.onVehicleStructureChanged()`；
  5. 已入世时 post `VehicleStructureChangeEvent(this, reason)`。
- `modeFor(reason)`：`ADD` → `GROW_WITH_MAX_DELTA`，`SPLIT` → `NONE`（新增枚举值：仍重算 `maxHp`，不动当前 HP），其余 → `CLAMP_ONLY`。
- **六处汇点，原因按场景细分，但没有一处是 `RESTORE`**。实际映射：

| 位置 | 场景 | 原因 | 注意 |
| --- | --- | --- | --- |
| `:279` | 分裂出的新载具（构造器内） | `SPLIT` | `inLevel` 还没置位（`ObjectManager.addSpiltVehicle` 才置，`ObjectManager.java:166`），事件发不出去 |
| `:761` | `removePart` | `REMOVE` | 部件移除若同时触发分裂，`spiltPartNets.size() > 1`，这条路径不重算 `maxHp`（`:754-756` 已按此分支），新载具由 `:279` 负责 |
| `:868` | `attachConnectorInSameVehicle` | `ADD`（新部件）/`ATTACH`（已存在部件连线） | 整段在 `if (isInLevel())` 内，恢复存档时被跳过 |
| `:899` | `attachConnectorAcrossVehicles`（服务端合并） | `MERGE` | 客户端合并走 `:963` → `:868`，所以客户端收到的是 `ATTACH` |
| `:966` | `clientHandleMerge` 兜底 | `ATTACH` | 只在 `newConnections.isEmpty()` 时执行 |
| `:1109` | `detachConnections` | `DETACH` / `SPLIT` | 不分支，两种情况都调；分裂出的新载具另外由 `:279` 负责 |

- **恢复存档没有汇点**：完整/骨架恢复走构造器（`:151-214`），那里只有 `:198` 的 `INIT_TO_MAX` 与 `:200` 的相机距离，从不调 `onVehicleStructureChanged`；每条连接虽然经过 `:868`，但被 `:866` 的 `isInLevel()` 挡住。所以 `RESTORE` 没有调用点，不要为它编一个。
- **`:851` 保留在原处**：它在 `addPart` 之后、`attach` 之前（`:850-855`），`attach` 失败会直接 `return`。把它并进 `:868` 的包装后，连接失败（连接点被占、条件检查不通过、`ConnectorAttachEvent.Pre` 取消）或非入世 attach 时，部件已经进了 `partMap` 但 `maxHp` 不涨，且没有日志。`ADD` 的 `GROW_WITH_MAX_DELTA` 留在 `:851`。
- **`:895` 必须留在 `setHp(mergedHp)` 之前**：`setHp` 用当前 `maxHp` 夹紧（`:317-319`），而 `maxHp` 只由 `recalculateMaxHp` 更新（`:342-345`）。合并路径现在是 `:895` 重算上限 → `:896` 写合并后的 HP；把重算并进 `:899` 的包装（在 `setHp` 之后）会让 `mergedHp` 先被**旧上限**截断——两台满血 100 的车合体只剩 100。客户端那条（`:957`、`:958`）同理。
- `addPart` 自己已经调过 `updateTotalMass()`（`:720`），包装里的那次在 attach 路径是冗余的——可以保留（几十个零件求和，代价可忽略），也可以把 `addPart` 里的去掉、统一由包装更新。
- 三件事只在包装里做一次；`:851` 与 `:895` 是两处例外，各自留在原位。真正的副作用留在原处：`activate()`（物理激活）、合并前的 `setHp(mergedHp)`、分裂时的 `distributeSplitHp`、各路径的发包。`refreshTrackLinks()` 目前只在两条拆解路径调用，若确认它同样只是从拓扑重建，也可以一并收进这个方法。
- 不需要结构版本号：客户端订阅事件就够了，漏掉的旧事件由“进入座位时无条件重算”兜底。
- `HpRecalcMode` 目前是 `VehicleCore` 的私有枚举，包装方法也在同一个类里，`modeFor(reason)` 不需要对外暴露。
- **事件在双端都发**：`:279`/`:761`/`:868`/`:966`/`:1109` 客户端也会执行（`:899` 只有服务端合并走），写成 `if (!level.isClientSide()) post(...)` 或用只挂服务端总线的事件，专用服务器上客户端就永远收不到——单机同 JVM 又看起来正常，最容易漏。
- **“已入世才发”会挡掉分裂出的新载具**：`:279` 在构造器里执行，此时 `inLevel` 还是 `false`，所以新载具永远不发 `SPLIT`。坐在被分出去那部分的玩家靠“装配体 uuid 变了”兜底重建（下一节）；要让新载具也发事件，只能在 `ObjectManager.addSpiltVehicle` 置位后补发。
- **座位图**：客户端订阅 `VehicleStructureChangeEvent`，只置 `dirty = true`；下一个 tick 检查，脏了就重算参考系 + 座位列表 + 排序/编号。回调里不做重算，是为了让一次结构变化只重算一次，避免在子系统握手与物理激活中途递归进来。
- **进入座位/换车**：控制座位从无到有、或装配体 uuid 变了，直接重建快照，不等事件。
- **离开座位**：清空快照，HUD 停止显示；再次进入时重建。
- **不重算参考系与排序**：耐久、毁坏、炮塔转动、悬挂行程、占用变化都不触发结构重算；占用只刷新标记颜色与“下一个”。参考零件被打爆也继续沿用缓存，它的位姿仍然有效。
- `VehicleSpiltEvent` 有定义但**从未被发出**（全仓库没有 `post(new VehicleSpiltEvent`）。分裂实际由两条路径触发：`removePart` 与 `detachConnections` 各自在改完拓扑后调 `partNetSpiltCheck()`，服务端把“子网络里某个部件 uuid → 新载具 uuid”的映射随 `PartRemovePayload`/`ConnectorDetachPayload` 下发，客户端据此走 `clientHandleSpilt`。不要监听 `VehicleSpiltEvent`。

### 5.8 座位名与编号

文字里出现的座位名只有一个来源：`SeatSubsystem.name` 经 `Component.translatable` 的翻译结果。内容包把名字写成翻译键，玩家看到的就是作者给的本地化文本；键不存在时按 `Component.translatable` 的默认行为显示键本身，补键即可，不改代码。

- 名字的正确性由内容包负责：`SeatSubsystem.name` 是子系统在零件 JSON 里的 key，K17 用 `driver_seat` / `commander_seat` / `gunner_seat` 这类短键并在 lang 里给了同名键，MTLB 用 `subsystem.machine_max.passenger_seat_1` 这类完整键。
- 变体名、`locator`、子系统名的末段都不参与取名：这些字段能凑出一个像样的名字，但玩家自行组装的载具没有命名约定，凑出来的名字与实际座位对不上，比原样显示键更容易误导。

分工：

- 名字出现在三处：面板标题（当前座位）、键位提示（下一个座位）、actionbar（切换结果 `2/3 · Gunner Seat`）。
- 地图标记只写编号：标记在地图上互相重叠，名字压上去既读不全也遮住车体。编号来自本地几何顺序（§5.2.3），与地图位置一一对应。
- 名字可能撑满面板：绘制前按像素宽度截断并补省略号，标题右侧的 `当前/总数` 固定占据自己的宽度。

### 5.9 与现有系统的边界

| 系统 | 关系 |
| --- | --- |
| 车外交互区 | 保留不变；座位图不替代“从车外进入” |
| `SeatSubsystem.setPassenger` | 保留给交互区路径；换座走新的 `SeatSwitchHelper`，避免下车副作用 |
| `CameraController` | 已支持同装配体换座继承目标；无需改动，只需确保客户端在结果包到达后更新 `controllingSubsystem` |
| `CustomHud` | 不修改；座位图是独立图层，绘制在其上方 |
| `VehicleInspectorRenderer`（O） | 保留；座位图的损伤层是 2D 补充，不替代 3D 内构查看 |
| `VehicleControlScreen`（AUI 面板） | 打开面板时座位图隐藏（`mc.screen != null`）；面板的 3D 预览是另一条线 |
| 炮镜/摄像机模式 | 座位图隐藏；换座会由 `CameraController` 自动退出设备视角 |
| 热栏/物品栏 | 换座不占用数字键，热栏语义不受影响；军用座位的 `allow_use_items=false` 时热栏本身不显示 |
| 其他模组 | 新键位可配置；不新增物理体；不改原版骑乘语义 |

### 5.10 一次换座的数据流

从按下 `X` 到座位图刷新，只有五份状态在变。它们的权威端和下行通道各不相同，这也是“结果包为什么必须广播、且必须是纯状态包”的根据：

| 状态 | 存在哪 | 权威端 | 下行通道 | 谁读它 |
| --- | --- | --- | --- | --- |
| 占用 `occupied` | 每个 `SeatSubsystem` | 服务端 | `DATA_OCCUPIED_ID`（每 tick 脏值；`pushAllTo` 补全量） | `SeatOrder.available()`、座位图标记 |
| 乘客 `passenger` | 每个 `SeatSubsystem` | 服务端 | **无** | 服务端校验、`findOccupiedSeat` 的下车搜索 |
| 骑乘关系 | `Entity.vehicle` / `passengers` | 服务端 | `ClientboundSetPassengersPacket`（原版，下一 tick） | 原版移动与 `positionRider`、`onTick` 重试 |
| 控制关系 `controllingSubsystem` | 实体附件（Mixin） | 服务端 | **没有原版包**：换座结果包 / 交互包重放 / 输入包重放 | `getPassengerAttachmentPoint`、`CameraController`、座位输入路由 |
| 控制组 `activeIndex` | 每座位一份 `ControlGroupSet` | 客户端（切组时先改本地再上行发包）；换座时由服务端在 ⑤ 里改写 | 平时无下行通道；换座结果包的 `controlGroup` 字段（§5.11） | `ViewInputPayload` 的过期校验、`getMerged*` 的线路合并、本地按键集合 |

按 tick 展开：

```text
T     客户端   按下 X → SeatOrder.next(...) 用本地顺序 + 本地 occupied 算出目标
              → 发 SeatSwitchPayload(subPartId, seatName)，置 pending（20 tick 去重）
T     服务端   解析 → 十步校验 → ①交接上车点 ②startRiding ③失败即返回
              ④清旧座位：setOccupied(false)、passenger=null、controlling=null
              ⑤接管新座位：passenger=player、setOccupied(true)、controlling=to、信号、额外质量、控制组复位
              → 成功广播 SeatSwitchResultPayload（带复位后的 controlGroup）；失败只回发起者
T     客户端   收到结果包，按 passengerEntityId 找到本地实体：
              - 本地玩家 + 同 SubPart：只写两处 occupied + controlling + 目标座位的 activeIndex
              - 本地玩家 + 跨 SubPart：再加 startRiding(newEntity, true)
              - 其他玩家：只写 occupied + controlling，骑乘交给原版包
T+1   服务端   ServerEntity.sendChanges() 发 ClientboundSetPassengersPacket（跨 SubPart 时列表真的变了）
              本 tick 的 occupied 脏值随 SetEntityData 下发
T+1   客户端   原版包重建 passengers（幂等）；座位图 tick 读到新 occupied 与新的控制座位
              → “YOU” 换位、旧座位转白、“下一个”按新顺序重算
```

三条必须成对更新的不变量：

- **`occupied` 与 `passenger` 成对**：服务端两个都写；客户端只有 `occupied` 有权威值，`passenger` 不参与任何判定（§5.2.3）。
- **`controllingSubsystem` 每个客户端各一份**：结果包必须写到所有客户端，漏掉哪一份，那边的乘客就被 `getPassengerAttachmentPoint` 摆到实体原点。
- **`activeIndex` 两端各写一次同一个值**：它平时只有上行通道（客户端切组时发 `ControlGroupSetEditPayload`），服务端不回推；而 `ViewInputPayload` 用 `payload.controlGroup != seat.getControlGroupSet().getActiveIndex()` 丢掉过期输入（`ViewInputPayload.java:88-94`）。所以换座时服务端在 ⑤ 里复位，客户端按结果包的 `controlGroup` 字段照抄。只改一侧的后果是视角包被静默丢弃、绑定索引错位——具体写法与取值见 §5.11。

HUD 侧的数据依赖分三层：快照（参考 SubPart、局部前向轴、`fit`、排序）只在进入座位与结构事件后重建；原点与偏航逐帧从参考 SubPart 的插值世界矩阵取，座位世界坐标也逐帧取；占用与当前座位每 tick 读实时值。这样炮塔转动、悬挂起伏、占用变化都不触发重建，而行驶与转向时地图始终跟着车（§5.7.6）。

### 5.11 控制组复位：两端在同一刻落到同一个值

`activeIndex` 是 §5.10 五份状态里唯一“客户端是权威、服务端只是镜像、平时只有上行通道”的一份。换座要让它复位，就得两端各写一次，而且写进同一个值。这一节定下写什么值、谁写、什么顺序。

三个读者决定了不能只改一侧：

| 读者 | 读哪一侧 | 只有一侧被改时的症状 |
| --- | --- | --- |
| `ViewInputPayload` 的过期校验（`ViewInputPayload.java:88-94`） | 服务端 | 该玩家的视角包被丢弃：镜头与瞄准圈仍在本地动（`CameraController.java:246` 先写本地信号再发包），武器不跟 |
| `getMergedMoveTargets()` 等按活动组合并的发送路径（`AbstractControllableSubsystem.java:320-421`） | 服务端 | 新乘客的输入沿上一个乘客停下的子组线路走 |
| `getMergedBindings()` 决定本地注册哪些按键（`RawInputHandler.java:516-538`）、`CameraController.send()` 上报的组号（`:248`） | 客户端 | 按键集合与上报的组号都来自本地副本；两端不一致时 `ControlBindingPayload` 又不校验来源组（`ControlBindingPayload.java:55-70`），索引会被服务端按另一份合并列表解析，可能触发**另一条**绑定 |

#### 5.11.1 复位目标取座位预设的 `active_index`

K17 炮手座位的预设 `k17_gunner.json` 写的是 `"active_index": 0`，它的 `base_group` 只有一条 `view_targets`，`fire_input_p2` 与 `secondary_fire_input_p2` 都在第 0 组（自动炮）里。固定复位到 `-1` 会让炮手落座后按开火键没有任何线路可走。所以目标值是“该座位预设自身的默认组”，与 `restoreControlGroupPreset()` 同源（`AbstractControllableSubsystem.java:447-455`）：

- `attr.getControlGroupPresetRl()` 等于 `NO_CONTROL_GROUP_PRESET` → `-1`；
- 注册表里查不到该预设 → `-1`，并记一次 debug 日志；
- 否则取 `preset.getActiveIndex()`。

这是**默认值**，不是实时状态：它由内容包里的常量决定，两端读同一份数据，所以可以直接当作复位目标，不需要像座位占用那样同步。

#### 5.11.2 服务端：换一份新集合，而不是就地 `activate`

方法放在 `AbstractControllableSubsystem`，紧挨 `restoreControlGroupPreset()`：

```java
/** 把活动子组复位到该座位预设的默认值，返回复位后的索引。 */
public int resetControlGroupToPreset() {
    ControlGroupSet cur = getControlGroupSet();
    if (cur == null || cur == ControlGroupSet.EMPTY) return -1;

    int target = -1;
    ResourceLocation rl = attr.getControlGroupPresetRl();
    if (!rl.equals(AbstractSubsystemAttr.NO_CONTROL_GROUP_PRESET)) {
        ControlGroupSet preset = MMDynamicRes.CONTROL_GROUP_PRESETS.get(rl);
        if (preset != null) target = preset.getActiveIndex();
    }
    if (cur.getActiveIndex() == target) return target;   // 幂等：不重建、不重复清信号

    // 换新实例，而不是 cur.activate(target)，三个理由：
    // 1) 构造器把注册表里的预设实例直接存了进来（SeatSubsystem.java:49-52）。在 onAttach 的
    //    restoreControlGroupPreset() 换成私有副本（:452）之前，就地改会波及共用同一预设的其它座位；
    // 2) activate 拒绝越界索引（ControlGroupSet.java:69-74），而玩家用面板编辑过的组列表可能比预设短；
    // 3) setControlGroupSet 会先用旧集合的全部频道清一遍输入信号（:243-249），正是“别留着上一位乘客的信号”。
    setControlGroupSet(new ControlGroupSet(cur.baseGroup, cur.groups, cur.getGuiActions(), target));
    return target;
}
```

- 调用点两处：`attachForSwitch` 的 ⑤ 末尾，返回值放进结果包；`setPassenger` 的末尾。后者让“从车外登入”与“车内换座”行为一致——而且 `setPassenger` 是共通代码，客户端重放交互包时执行的是同一段，所以交互路径天然双端一致，不需要额外同步。
- 放在 ⑤ 末尾只是顺着“先状态、后配置”读：`clearInputSignals()` 遍历的是全部组的频道（`:306-318`），与当前 `activeIndex` 无关，先后顺序不影响正确性。
- `-1` 与“组列表为空”等价：`getActiveGroup()` 返回 null，`getMerged*` 回退 base 组。
- 旧座位不复位：服务端与客户端都保留它的原值，两端因此一致；下一个乘客坐进来时由同一段逻辑覆盖。

#### 5.11.3 客户端：结果包带一个 `int`，不查表

S2C 载荷加一个字段（§5.4.1），`controlGroup` 只在 `success` 时有效，失败填 `-1`。客户端处理器按顺序做三件事：

1. 按 `passengerEntityId` 找到本地实体，再定位目标座位（找不到就跳过，见下）；
2. 对目标座位写入 `payload.controlGroup()`——复用 5.11.2 末尾那段“换新实例”的写法，只是索引直接取载荷；
3. 写 `occupied`/`controllingSubsystem`，必要时 `startRiding`（§5.5）。

为什么带一个索引，而不是两端各查一次预设：

- 两端各自查表得到的相等，依赖“两个注册表都加载成功、且内容包一致”。服务端独占内容包、或客户端启动期还没填好 `CONTROL_GROUP_PRESETS` 时，会出现一边 `0`、一边 `-1`，症状与 §5.10 第三条不变量被破坏时相同：视角包静默丢弃、绑定索引错位。带上 4 字节，不变量就从“恰好相等”变成“照抄同一条消息”。
- 与 §5.4.1 的“编号不进协议”不冲突：那一条说的是**座位序号**，服务端不排序、生成不了；`activeIndex` 是服务端在 ⑤ 里刚写下的真实状态，不存在“生成不了”的问题。
- 不复用 `ControlGroupSetEditPayload` 来“纠正”客户端：那个包要求 `controllingSubsystem` 已等于目标子系统（`ControlGroupSetEditPayload.java:57-59`），而结果包到达时控制关系正要切过去，发早了会被服务端丢弃；它还会把整份集合（含面板编辑）覆盖一遍。

三种写入情形：

- 座位已存在 → 按载荷写，越界也照写：`getActiveGroup()` 返回 null 时退化为 base 组，与服务端同一份语义。
- 座位尚未在本地建好 → 跳过。它之后构造时走 `SeatSubsystem` 构造器与 `onAttach()` 的预设路径（`SeatSubsystem.java:49-56`、`AbstractControllableSubsystem.java:225-232`），`activeIndex` 自然等于预设值，也就是服务端刚写入的那个值。
- 重复处理同一条结果包 → 第二次进入时 `cur.getActiveIndex() == target` 直接返回，不产生副作用。

#### 5.11.4 时序与在途包

- `T` 按下 `X` 后、结果包到达前，`CameraController.send()` 仍用**旧座位**的 `activeIndex` 发包；但同一个包的 `subPartId`/`subSystemName` 指着旧座位，而服务端此时控制关系已经换到新座位，`ViewInputPayload` 的前两条（来源座椅、当前控制对象）就把它挡住了，与组值无关。丢一两帧视角包没有后果：`send()` 每 tick 重发完整目标（`CameraController.java:233-248`），不是增量。
- 结果包通过 `enqueueWork` 在主线程队列里执行，同一 tick 稍后才到 `handleNormalInputs`/`send()`，所以第一次用新值发包时两端已经一致。
- 结果包丢失（掉线、维度切换）：`activeIndex` 没有全量补推，客户端会停在旧值；玩家按一次切组键就会上行整份集合（`RawInputHandler.java:396-409`），两端重新对齐。要彻底消掉这个窗口，需要一条“座位全量状态”包，属于 P4 的范围。
- 复位只动该座位自己的 `ControlGroupSet` 与它的输入信号；乘客字段、骑乘关系、`occupied` 都不受影响。
- 与切组键并发时不会分叉：切组键只作用于那一刻 `controllingSubsystem` 指向的座位，而结果包到达之前它仍指向旧座位，所以切组改的是旧座位；`ControlGroupSetEditPayload` 与 `SeatSwitchPayload` 走同一条连接，服务端按发送顺序处理。两端的最终值都是同一条消息序列的结果。

***

## 六、实施计划

### P0：后端基础（可独立测试，无 UI）

| 项 | 文件 | 内容 |
| --- | --- | --- |
| 占用同步 | `common/mech/subsystem/SeatSubsystem.java` | `DATA_OCCUPIED_ID`、`defineSynchedData`、`setOccupied`、`onSyncedDataUpdated`；`setPassenger`/`removePassenger`/`detachForSwitch`/`attachForSwitch` 四处写点统一调 `setOccupied` |
| 座位顺序 | 新增 `common/mech/subsystem/seat/SeatOrder.java` | 刚性簇参考系、`(sy, sx, seatName, subPartId)` 排序、`available(...)`、`next(vehicle, from, forward)`；客户端用，服务端不依赖 |
| 专用切换 | `SeatSubsystem.java`、新增**同包** `SeatSwitchHelper.java` | `detachForSwitch`、`attachForSwitch`、同实体/跨实体切换、boarding offset 交接、先挂载再改座位（失败无需回滚） |
| 控制组复位 | `common/mech/subsystem/AbstractControllableSubsystem.java`、`SeatSubsystem.java` | `resetControlGroupToPreset()`：目标取预设的 `active_index`、换新实例而不是就地 `activate`、幂等；接入 `attachForSwitch` 与 `setPassenger`（§5.11） |
| 下车抑制 | `common/entity/MMPartEntity.java` | `getDismountLocationForPassenger` 在换座期间返回当前位置 |
| 输入来源校验 | `network/payload/ControlBindingPayload.java`、`MovementInputPayload.java` | 两个 handler 共用 `isControlledBy(sub, player)`（控制对象 + 座位乘客/骑乘），挡住换座后到达的旧座位按键包与移动包；移动包还要在广播前拦截（§5.5） |
| 协议 | 新增 `network/payload/SeatSwitchPayload.java`、`SeatSwitchResultPayload.java`；`network/MMPayloadRegistry.java` | C2S 带目标座位（`subPartId + seatName`）；S2C 是纯状态包（不带座位序号，带复位后的 `controlGroup`），成功时广播维度内所有玩家、失败只回发起者；版本提升、服务端校验与节流 |
| 测试 | `common/gametest/SeatSwitchGameTest.java` | 同实体、跨实体、占用/不可用/跨装配体/当前座位；挂载被拒时状态不变；控制组复位（炮手预设 `0`、旧座位保持不变、幂等）；换座后旧座位的绑定包与移动包被拒绝；`SeatOrder.next` 的纯函数用例 |
| 结构事件 | `common/mech/vehicle/VehicleCore.java`、新增 `event/VehicleStructureChangeEvent.java` | `onStructureChanged(Reason)` 包装：质量/最大耐久/相机距离、子系统握手、双端发事件；原因决定 `HpRecalcMode`（`ADD`→`GROW`，`SPLIT`→`NONE`，其余 `CLAMP`）；按 §5.7.6 的表逐点替换，`:851` 与 `:895` 保留在原处 |

验收：K17 三个座位可以用循环键依次到达；玩家不离开载具、不被传送到车外；旧座位信号归零；新座位信号接管；换到炮手座后活动组是预设的自动炮组（`0`）且开火有效；旧座位的活动组保持原值；换座后旧座位的绑定包与移动包被拒绝；占用同步到另一客户端；`rg 'occupied\s*='` 只剩声明与 `setOccupied`；两台满血载具合并后 HP 等于两者之和（`:895` 未被并入包装）；专用服务器（非单机）上客户端也收到结构事件。

### P1：循环换座键（最小可用）

| 项 | 文件 | 内容 |
| --- | --- | --- |
| 键位 | `client/input/KeyBinding.java`、`MMLanguageRegistry` | `cycle_seat`，默认 `X`；注册到 `RegisterKeyMappingsEvent` |
| 输入 | `client/input/RawInputHandler.java` | 在 `handleNormalInputs` 的每 tick 调用处执行 `KeyHooks.EVENT(...).OnKeyDown(...)`；`SeatOrder.next(...)` 算目标后发 `SeatSwitchPayload(subPartId, seatName)`；actionbar 反馈；`pending` 去重 |
| 客户端预测 | 复用 `SeatOrder.next(...)` | 同时用于 HUD 高亮、“下一个”标记与“满员”提示；算不出目标就不发包 |

验收：MTLB 10 个座位可用 `X` 依次到达；占用/不可用自动跳过；不打开界面。

### P2：常驻座位图 HUD（主交付）

| 项 | 文件 | 内容 |
| --- | --- | --- |
| 布局 | 新增 `client/render/gui/seat/SeatMapLayout.java` | 座位标记的屏幕布局：重叠错开、载具包围盒与适配缩放（`fit`）；参考系与排序来自 common 的 `SeatOrder`；标记世界坐标每帧现取 |
| 重算 | `SeatMapLayout.java` | 订阅 `VehicleStructureChangeEvent` 置脏；进入座位时算一次，脏了下一 tick 重算；装配体 uuid 变了无条件重建 |
| HUD | 新增 `client/render/gui/hud/SeatMapHud.java` | 常驻条件、面板、GUI 内半透明透视车体（先 `X(-90)` 再 `Y(180-heading)`、外壳 + 内构两遍、空模型守卫）、座位标记与“下一个”、标题与键位提示里的座位名、图例、反馈 |
| 注册 | `client/render/gui/MMGuiManager.java` | `registerAboveAll`；客户端 tick 更新占用与当前座位 |
| 配置 | `client/MMClientConfig.java` | 显示模式（常驻/仅换座后/关闭）、损伤层开关 |

验收：K17/MTLB/2S38/Van 四类载具都能显示正确的座位布局与占用；HUD 高亮的“下一个”与按键结果一致；行驶中可用；不遮挡准星。

### P3：零件状态与打磨

| 项 | 内容 |
| --- | --- |
| 损伤着色 | 逐 SubPart 传耐久色 + 图例 + 配置开关 |
| 降级形态 | 模型不可渲染/性能吃紧时退回“AABB 矩形 + 座位点”（§5.2.4 兜底） |
| 可读性 | 重叠引线、高度角标、当前座位朝向锥（可选） |
| 常驻性能 | 座位 ≤ 1 不渲染、面板裁剪、`OffscreenFbo` 纹理缓存（§5.2.4 常驻代价） |

### P4：可选扩展

- `SeatMapScreen`：鼠标点击、详情面板、滚动；复用 `SeatMapLayout` 与渲染器，适合静止时使用。
- 控制面板 3D 预览：按现有附录 A 用 `VehicleAnimatable` 做斜视角预览，和座位图分开。
- 乘客名字/角色同步：在座位上同步 UUID/名字，地图显示“谁在哪个座位”。
- 大编制载具：座位很多时标记聚合，或加“按住展开大图”。
- 反向循环键：`Shift+X` 把 `SeatOrder.next(...)` 的 `forward` 传成 `false`。
- 手柄径向选择：需要一个“按住 + 摇杆”的组合键，届时与会话内的座位图展开状态配合。
- 座位点击/数字键：命中哪张座位标记就发 `SeatSwitchPayload`，协议不变。

### 工作量估算

| 阶段 | 估算 | 说明 |
| --- | --- | --- |
| P0 | 3–4 天 | 协议、排序、状态机、GameTest 是大头；事件包装按逐点映射改，挂载被拒与合并 HP 顺序各要一条用例 |
| P1 | 0.5 天 | 键位 + actionbar |
| P2 | 2–3 天 | 布局、GUI 内整车渲染、标记投影、常驻条件、四类载具调参 |
| P3 | 1–1.5 天 | 损伤色、降级形态、常驻性能、可读性 |
| P4 | 3–5 天 | Screen/3D 预览，按需 |

v1（P0–P2）约 6–8 天；加上 P3 约两周。建议按 P0 → P1 → P2 的顺序推进，P1 可以先让玩家用起来，P2 再补上“看得见”的布局。

***

## 七、验收

### 自动化测试

| 用例 | 期望 |
| --- | --- |
| 循环换座（K17 驾驶 → 下一个空位） | 旧座位空、新座位占用；`controllingSubsystem` 指向新座位；玩家仍骑同一实体；boarding offset 交接；位置不跳变（不触发下车搜索/冲量） |
| 跨实体循环换座（Van 左前 → 下一个空位） | 玩家骑到新实体；旧实体释放；玩家位置不落到车外；`getDismountLocationForPassenger` 未被用作真实下车点 |
| `SeatOrder.next` 纯函数 | 给定坐标/占用/可用性 → 期望目标；回绕；只有一个座位 → `null`；满员 → `null` |
| 满员 | 客户端算不出目标，不发包；服务端不收包、不受影响 |
| 目标已占用 | 返回 `OCCUPIED`；两座位状态不变 |
| 目标已毁坏/未激活 | 返回 `UNAVAILABLE` |
| 跨装配体 | 返回 `DIFFERENT_ASSEMBLY` |
| 目标是当前座位 | 返回 `SAME_SEAT` |
| 非座位控制/未骑乘 | 返回 `NOT_SEATED` |
| 座位图排序 | 合成座位坐标 → 期望的编号顺序/屏幕坐标 |
| 参考零件 | 含驾驶位簇 / 无座位多簇 / 全可动三种输入 → 期望的参考零件与 front 方向或 fallback |
| 结构重算 | 无结构事件时进入两次 → 同一参考零件；收到 `ATTACH`/`DETACH`/`SPLIT` 后 → 下一个 tick 重算；零件被打爆（无结构事件）→ 沿用缓存 |
| 重叠座位 | 两个同 XZ 座位 → 标记错开，且循环键都能到达 |
| 占用同步 | 服务端设置 occupied → `forEachSyncedValue` 含该值；初始 `pushAllTo` 覆盖载具创建后的玩家 |
| 挂载被拒 | 让挂载必定被拒 → 返回 `MOUNT_FAILED`；两个座位的 `occupied` 与 `controllingSubsystem` 都保持原值 |
| 控制组复位 | K17 炮手预设（`active_index: 0`）登入后为 `0`；手动切到 `1` 再离座，第二人登入仍回到 `0`；换座后旧座位保持原值；连续调用两次不重建集合、不重复清信号；座位设成越界索引时 `getActiveGroup()` 为 null 且两端同值 |
| 换座后的旧座位输入 | 换座成功后发旧座位的 `ControlBindingPayload` 与 `MovementInputPayload` → 都被拒绝，旧座位的按键/移动信号保持空、移动包不广播；同一 tick 内发新座位的包 → 生效 |
| 合并 HP | 两台满血载具合并 → HP 为两者之和（覆盖 `:895` 在 `setHp(mergedHp)` 之前这条顺序约束） |
| 俯视变换 | 8 个朝向 → `front` 恒为 `(0, -1)`、`right` 恒为 `(+1, 0)`；`heading = 45°` 时不得出现 `(0.50, 0.71)` |

### 客户端实机

| 场景 | 检查 |
| --- | --- |
| K17 | 3 个座位图正确；驾驶员连按 `X` 依次到炮手/车长；第三人称下不用瞄准舱盖 |
| MTLB | 10 个座位 2×5 布局；`X` 按 9 次可到第 10 个座位；占用/不可用自动跳过 |
| 2S38 | 3 个并排座位；切换后 HUD/镜头/控制组正确 |
| 控制组 | K17 炮手落座即自动炮组，开火有效；切到反坦克组后离开再过座，回到自动炮组；驾驶员座位的组不受影响 |
| Van | 4 个独立 SubPart；跨 SubPart 切换无可见传送；座椅交互区仍可从车外进入 |
| 多人 | 两名玩家同车；占用状态实时更新；不能坐进已占座位；换座不产生重复乘客 |
| 常驻 | 进入座位后 HUD 自动出现；下车后消失；打开 AUI/炮镜/F1 时隐藏；不阻挡准星 |
| 行驶中 | HUD 常驻时 WASD/镜头仍可用；车辆不停车；帧时间可接受 |
| 行驶/转向对齐 | 直行一段距离，标记与模型不漂出面板；边转向边看，车头始终朝上、标记跟着车体转；斜向（约 45°）时车头也朝上 |
| GUI scale | 1x/2x/3x 下文字与标记不重叠、不溢出 |

### 性能

- 常驻时帧时间增量 < 1.5 ms；模型逐帧用实时 `SubPart` 渲染，不做数据拷贝；座位标记只做点变换。
- 进入/离开座位不产生明显 GC 峰值；座位状态更新与模型刷新不分配大对象。
- 服务端换座包处理在主线程，单次 < 0.1 ms；不直接操作物理体。

***

## 八、风险与开放问题

| 风险/问题 | 说明 | 处理 |
| --- | --- | --- |
| 参考零件启发式 | 全可动载具没有刚性簇；拖挂与牵引车各自成簇时得靠座位判定 | 含驾驶位的簇优先；客户端 `seat_map_flip_front`；官方四车实测 |
| 刚性簇漏掉部件内部连接 | 只用 `partNet.edges()` 时，同一部件内的多个 SubPart 互不相连，簇退化成单零件，参考零件可能选到座椅腿 | 从 `connector.attachedConnector` 建 SubPart 图（只认 Advanced 侧去重），覆盖部件内与部件间；自组装多 SubPart 部件实测 |
| HUD 参考系漂移 | 原点或偏航若跟快照一起缓存，行驶后地图按旧原点平移、转向后车头不再朝上，而座位点用的是实时世界坐标 | 快照只存参考 SubPart 与 `frontLocal`；原点/偏航逐帧从 `getWorldPositionMatrix(partialTick)` 取；实机用例覆盖直行与约 45° 转向 |
| 客户端目标过期 | 客户端算目标是基于已同步的占用；服务端数据更新时，那个座位可能刚被占 | 服务端 `OCCUPIED` 拒绝，客户端按结果包刷新占用；再按一次会跳过它 |
| 跨 SubPart 下车搜索 | `startRiding` 触发 `dismountVehicle`，可能把玩家放到车外 | 换座标记 + `getDismountLocationForPassenger` 返回当前位置；Van 实测 |
| 同实体多乘客 | 原版 `canAddPassenger` 默认只允许 1 人；`SeatSubsystem.setPassenger` 用 `force=true` 绕过 | 换座继续使用 `force=true`；非 force 的 `startRiding` 会受 `canAddPassenger`/`boardingCooldown` 限制；建议后续给 `MMPartEntity` 显式覆写 `canAddPassenger` 以增强健壮性 |
| 座位显示名 | 名字来自 `SeatSubsystem.name`，内容包没写翻译键时显示键本身（§5.8） | 直接 `Component.translatable(name)`，不解析变体名/locator；标记只用编号 |
| 协议版本 | 新增 `input` 载荷 | 按项目约定提升 `input` 版本；两端同版本发布 |
| 炮塔旋转 | 炮塔上的座位会相对车体移动 | 标记逐帧用 `mapMatrix` 投影；排序在进入/结构变化时缓存 |
| GUI 层渲染模型 | 模型与 GUI 的批次/光照/深度混用，可能盖住标记或发黑 | 照 `GuiAnimatable`/`CustomModelItemRenderer`：`g.flush()` 后再画标记（车体不写深度，标记天然在上）；异常时退回兜底形态 |
| 常驻模型渲染 | 只要在车内，每帧都多一遍整车模型（K17 约 2000 个方块） | 座位 ≤ 1 不渲染；面板小 + scissor；配置可切到“仅换座后”或关闭；兜底走 AABB 或 `OffscreenFbo` 缓存 |
| 与现有 HUD 重叠 | 屏幕左下角与热栏、聊天行共用同一块区域 | 面板贴左下、底部留 24px 让开热栏；配置位置/缩放/透明度；提供“仅换座后显示”模式；打开界面时整体隐藏 |
| 新键位冲突 | `X` 可能被其他模组或内容包控制绑定占用 | 键位可配置；只在座位控制时生效；不与热栏数字键冲突 |
| 控制组继承 | 新座位可能保留上一个乘客的子组 | 复位到座位预设的 `active_index`（K17 炮手预设是 `0`；固定 `-1` 会让炮手落座后没有开火线路）；用新实例替换而不是就地 `activate`，避开共享预设实例与越界索引（§5.11.2） |
| 控制组两端失配 | `activeIndex` 平时只有上行通道，服务端不回推；只改一侧会让该玩家的视角包被静默丢弃，`ControlBindingPayload` 的绑定索引还会错位到另一条绑定 | 结果包带 `controlGroup`，客户端照抄，不做第二次查表；目标座位尚未在本地建好时跳过（它构造时按预设值，恰好等于复位值）；结果包丢失的窗口靠切组键上行整份集合自愈，彻底消掉需要 P4 的座位全量状态包 |
| 客户端控制状态丢失 | 异常断线、维度切换或客户端重载后，`controllingSubsystem` 可能为空或仍指向旧座位 | 客户端 tick 检测它是否与 `player.getVehicle()` 一致，不一致就清空；后续可用“骑乘实体 + 座位占用”重建 |
| 分裂出的新载具不发结构事件 | `:279` 执行时 `inLevel` 仍为 `false`，“已入世才发”把它挡掉 | HUD 靠“装配体 uuid 变了”无条件重建；要让新载具也发事件，需在 `ObjectManager.addSpiltVehicle` 置位后补发 |
| 专用服务器漏发结构事件 | 包装若写成只在服务端 post，客户端只在进入座位时算一次；单机同 JVM 测不出来 | 包装双端都 post；补一轮多人专用服务器实测 |
| 晚加入客户端的乘客坐姿 | 其他玩家的 `controllingSubsystem` 只在上下车那一刻由载荷重放写入，晚加入的客户端缺失，乘客被画在实体原点 | 既有行为，v1 不修；`occupied` 仍能显示占用，P4 再加 `DATA_PASSENGER_ID` |

开放问题（已定项直接写结论）：

1. 座位图默认常驻、短暂显示还是关闭？已定：默认常驻，配置提供“常驻 / 仅换座后显示 / 关闭”三档。
2. 损伤层默认开还是关？已定：默认开，只显示“低于 50%/毁坏”，可关。
3. 是否需要“当前座位视角锥”？对第三人称理解朝向有帮助，但会增加绘制量；P3 可选。
4. 是否需要在座位图上显示其他玩家名字？需要额外同步 UUID/名字；v1 只显示占用。
5. 是否把座位图做成内容包可放置的 HUD 元素？不建议；它是核心 UI，位置由客户端配置。
6. 是否允许行驶中换座？建议允许；如果担心驾驶员切走导致失控，可后续加“仅在速度低于阈值时允许离开驾驶位”的服务器配置，但默认不限制。
7. 是否需要反向循环键？`Shift+X` 的成本只是把 `SeatOrder.next(...)` 的 `forward` 传成 `false`；v1 先不做，留作 P4。

***

## 九、证据索引

| 事实 | 位置 |
| --- | --- |
| 交互键 → 准星/接触交互区 | `src/main/java/io/github/sweetzonzi/machine_max/common/attachment/LivingEntityEyesightAttachment.java:319-354` |
| 交互区按目标顺序尝试，`CONSUME` 结束 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/interact/InteractBox.java:146-166` |
| 座位 `onInteract` → `setPassenger` | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/SeatSubsystem.java:87-122` |
| 交互包在客户端重放、服务端转发全维度（换座广播的先例） | `src/main/java/io/github/sweetzonzi/machine_max/network/payload/SubsystemInteractPayload.java:55-57`、`:62-64` |
| 客户端控制关系的唯一写入点 | `SeatSubsystem.java:114`、`:160`（只有 `setPassenger`/`removePassenger` 调用 `machine_Max$setControllingSubsystem`） |
| 同装配体换座的上车点交接 | `SeatSubsystem.java:136-155` |
| 旧座位清理（含 `stopRiding`/冲量） | `SeatSubsystem.java:157-176` |
| 同装配体换座的相机目标继承 | `src/main/java/io/github/sweetzonzi/machine_max/client/input/CameraController.java:143-157` |
| 第三人称相机在车后 | `CameraController.java:396-418` |
| 座位视角约束 | `CameraController.java:422-437` |
| `getAllSeats` | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/VehicleCore.java:1521-1526` |
| 座位世界坐标（非插值，排序用） | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java:1197-1210`（`getLocatorWorldTransform`） |
| 循环键按下路径 | `src/main/java/io/github/sweetzonzi/machine_max/client/input/RawInputHandler.java:470-471`（`KeyHooks.EVENT(...).OnKeyDown`） |
| 离屏渲染目标（性能兜底） | `src/main/java/io/github/sweetzonzi/machine_max/client/fbo/OffscreenFbo.java:23-46` |
| 座位排序与“下一个”（P0 新增） | `common/mech/subsystem/seat/SeatOrder.java`（目前不存在） |
| 换座协议（P0 新增） | `network/payload/SeatSwitchPayload.java`、`SeatSwitchResultPayload.java`（目前不存在） |
| 座位 attachment point | `src/main/java/io/github/sweetzonzi/machine_max/common/entity/MMPartEntity.java:365-374` |
| 下车位置搜索 | `MMPartEntity.java:554-595` |
| K17 座位/舱盖 | `src/main/resources/spark_modules/Machine-Max_K17/machine_max/parts/k17_hull.json:73-91`、`:268-317` |
| K17 座位 locator | `.../Machine-Max_K17/machine_max/models/part/k17.geo.json:23703-23706` |
| MTLB 座位/舱盖 | `src/main/resources/spark_modules/Machine-Max_MTLB/machine_max/parts/mtlb.json:614-658`、`:784-921` |
| MTLB 座位 locator | `.../Machine-Max_MTLB/machine_max/models/part/mtlb.geo.json:15728-15737` |
| 2S38 座位/舱盖 | `src/main/resources/spark_modules/Machine-Max_2S38/machine_max/parts/2s38_hull.json:79-104`、`:362-411` |
| 2S38 座位 locator | `.../Machine-Max_2S38/machine_max/models/part/2s38.geo.json:12844-12846` |
| 座位子系统 key 的命名差异 | K17 `parts/k17_hull.json:268-317`（短键）；MTLB `parts/mtlb.json:784-921`（完整键）；2S38 `parts/2s38_hull.json:362-411`（完整键）；Van `parts/van/van_seat.json`（四个座位共用 `subsystem.machine_max.seat`） |
| 官方座位 lang 键 | K17 `lang/zh_cn.json:58-60`（短键，存在）；MTLB `lang/zh_cn.json:77-84`（只有 passenger 键）；2S38 无座位键；Van `lang/zh_cn.json:218`（泛型“座椅”） |
| Van 每座位一个交互区 | `src/main/resources/spark_modules/Machine-Max_Civilian/machine_max/parts/van/van_seat.json:27-35` 等 |
| 军用座位只开第三人称 | `src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/subsystems/seat/default_3rd_seat.json:6-16`、`default_3rd_driver_seat.json:6-16` |
| 子系统同步值发送 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractSubsystem.java:310-318` |
| 全量补推 | `SubsystemController.java:127-134` |
| 同步包处理 | `src/main/java/io/github/sweetzonzi/machine_max/network/payload/SubsystemSyncPayload.java:54-61` |
| HUD 图层注册 | `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/MMGuiManager.java:49-57` |
| HUD 宿主解析/重建 | `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java:104-163`、`:190-219` |
| 实时车体骨骼/渲染矩阵 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/SubPart.java:1351-1352`、`:1389-1393` |
| GUI 内整车渲染（物品图标） | `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/CustomModelItemRenderer.java:42-58`、`:208` |
| GUI 内 3D 元素的渲染与光照 | `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/GuiAnimatable.java:159-189` |
| 刚性边只看 Advanced 侧 | `connector/AbstractConnector.java:225`、`:264`、`:268-278`、`:296-299`；`attr/connector/ConnectorAttr.java:219-233`（simple 侧的 `joint_attrs` 只打警告） |
| `partNet` 的节点是部件，不是 SubPart | `VehicleCore.java:71`（`MutableNetwork<RegularPart, Pair<AbstractConnector, SimpleConnector>>`）、`:857`、`:890`、`:1086-1088` |
| 部件内部 SubPart 连接独立建立 | `AbstractPart.java:590`（`autoAttachInternalConnectors`）、`:703`（`createSubParts`）、`:750`（调用点） |
| 连接点对偶与去重依据 | `connector/AbstractConnector.java:79`（`attachedConnector`）、`:239-240`（两端各存一份）、`:389-411`（拆开时置空） |
| 参考零件位姿的逐帧采样 | `common/visual/SubPartAnimatable.java:85-93`（`getWorldPositionMatrix` 对 `oldTransform`/`transform` 插值）；`vehicle/SubPart.java:1389-1393` |
| `ControlBindingPayload` / `MovementInputPayload` 没有来源校验 | `network/payload/ControlBindingPayload.java:55-70`；`MovementInputPayload.java:57-83`（成功后整包广播给维度）；对照 `ViewInputPayload.java:88-94`、`RegularInputPayload.java:135` |
| `KeyHooks.OnKeyDown` 是每 tick 轮询 | `src/main/java/io/github/sweetzonzi/machine_max/external/js/hook/KeyHooks.java:306-309` |
| 控制组切回 base | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/control/ControlGroupSet.java:69-74` |
| 座位预设可带非 `-1` 的默认组 | `src/main/resources/spark_modules/Machine-Max_K17/machine_max/control_groups/k17_gunner.json:1-28`（`"active_index": 0`，base 只有 `view_targets`，开火线路在第 0 组） |
| 控制组替换时先清旧集合的信号 | `AbstractControllableSubsystem.java:243-249`、`:306-318`（清全部组的频道） |
| 控制组平时只有上行通道 | `ControlGroupSetEditPayload.java:52-68`（要求 `controllingSubsystem` 已指向该子系统）、`RawInputHandler.java:396-409`（切组时本地先改再上行） |
| 视角包带活动组并由服务端校验 | `ViewInputPayload.java:88-94`；发送侧 `client/input/CameraController.java:244-248` |
| 绑定索引不校验来源组与活动组 | `ControlBindingPayload.java:55-70` |
| `getPosition()` 是零件位置平均值 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/VehicleCore.java:441-460` |
| 变体名是 `variantName` 字段 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/part/AbstractPart.java:88-89` |
| `getBones` 直接解引用模型，需先判空 | `attr/SubPartAttr.java:357-363`；`renderer/PartEntityRenderer.java:77-79` |
| 加件涨上限在 attach 之前 | `VehicleCore.java:848-855`（`:851` 重算、`:854` attach、`:855` 失败返回） |
| 合并路径的上限重算在写 HP 之前 | `VehicleCore.java:888-902`、`:955-959`；`setHp` 夹紧 `:317-319`；`recalculateMaxHp` `:342-345` |
| 分裂载具入世时机 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/ObjectManager.java:156-167`（`inLevel = true` 在构造器之后） |
| 逐 SubPart 渲染载具（含颜色参数） | `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartAssemblyRenderer.java:176-211` |
| 本地碰撞包围盒 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/part/AbstractPart.java:562-564` |
| 实时车体渲染参考 | `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartEntityRenderer.java:75-150` |
| 结构变更汇点 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/SubsystemController.java:104-113`；`VehicleCore.java:279`、`:761`、`:868`、`:899`、`:966`、`:1109` |
| 结构事件包装 | `common/mech/vehicle/VehicleCore.java#onStructureChanged` + `common/mech/vehicle/event/VehicleStructureChangeEvent.java` |
| `VehicleSpiltEvent` 从未发出 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/event/VehicleSpiltEvent.java:9-12`（全仓库无 `post(new VehicleSpiltEvent`） |
| 客户端载具创建/恢复路径 | `src/main/java/io/github/sweetzonzi/machine_max/network/payload/assembly/VehicleCreatePayload.java:38-41` |
| 连接点附着/脱开事件 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/connector/AbstractConnector.java:225-256`、`:391-412` |
| 载具数据预览（物品/蓝图，非座位图） | `src/main/java/io/github/sweetzonzi/machine_max/common/visual/VehicleAnimatable.java:70-111`、`:172-191` |
| 3D 装配 HUD（模型 + 面板 + 引线的先例） | `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud3d/AssemblyHud3D.java:371-450`、`:733-782`、`:801-815` |
| 3D HUD 上下文（`fill`/`drawText`/`drawScreenFacingLine`） | `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud3d/Hud3DContext.java:38-105` |
| 控制面板 3D 预览方案 | `docs/plan/进行中/AUI载具控制面板实现计划.md` 附录 A |
| 控制面板 3D 预览空实现 | `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/screen/VehicleControlScreen.java:197-198` |
| 内构查看（O） | `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/VehicleInspectorRenderer.java:56-58`、`:67-82` |
| 子系统耐久色带（内构 HUD 与座位图共用） | `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/SubsystemDurabilityColors.java` |
| HitBox 与骨骼、子系统的对应关系 | `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/attr/HitBoxAttr.java:26-45`；`client/render/gui/hud3d/AssemblyHud3D.java:429-449` |
| 3D HUD 渲染参考 | `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/Hud3DRenderer.java:25-136` |
| 原版 `canAddPassenger` 一人限制 | NeoForge 21.1.219 源码 `net/minecraft/world/entity/Entity.java` `canAddPassenger` |
| `startRiding(force)` 绕过检查 | 同上 `Entity.startRiding` |
| 服务端 `stopRiding` 会找下车点 | 同上 `LivingEntity.stopRiding` / `dismountVehicle` |
| 视图输入的服务端校验风格 | `src/main/java/io/github/sweetzonzi/machine_max/network/payload/ViewInputPayload.java:80-118` |

***

## 十、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-26 | 初版调研：现状链路、交互区在军用载具上的失效原因、官方载具座位拓扑、方案对比、推荐“循环键 + 俯视座位图 HUD”、后端同步/协议/切换路径、分阶段实施与验收 |
| 1.1 | 2026-10-26 | 车形定为在常规 2D HUD 图层里直接渲染载具模型：`GuiGraphics` + 实时 `SubPart`，座位标记复用同一个 `PoseStack` 矩阵；不采用逐模型方块的 2D 投影，也不把座位图放进 3D HUD（`IHud3DElement`）通道；只保留 AABB 矩形兜底。相应调整零件状态层、缓存/线程、P2/P3 工作量与风险 |
| 1.2 | 2026-10-26 | 参考零件改为按连接点 `joint_attrs` 划分刚性簇：含驾驶位的簇 → 最大质量簇（排除轮子簇），簇内按质量、`SubPart.projectedArea.y`、`subPartId` 排序；面积只做 tie-break，且取自零件局部包围盒而非实时投影 |
| 1.3 | 2026-10-26 | 结构变化重算：打开时算一次，收到结构事件后的下一 tick 重算；`VehicleSpiltEvent` 从未被发出，分裂由 `detachConnections` 路径覆盖 |
| 1.4 | 2026-10-26 | 结构通知包成 `VehicleCore.onStructureChanged(reason)`：重算质量/最大耐久/相机距离、通知子系统握手、入世后发 `VehicleStructureChangeEvent`；不引入结构版本号，客户端只订阅事件；去掉 `machine_max:hull` 标签，参考零件只按刚性簇与质量选 |
| 1.5 | 2026-10-26 | 派生量重算并入 `onStructureChanged` 单个方法（不再拆 derived/notify 两层）；`SPLIT` 传 `HpRecalcMode.NONE`，因为 HP 已由 `distributeSplitHp` 按比例分配 |
| 1.6 | 2026-10-26 | 换座动作收敛为单个循环键：数字键换座与座位图键取消，客户端只发“下一个”意图，目标由服务端按 common 侧的 `SeatOrder` 算出；座位图改为在车内常驻显示，新增“下一个目标”标记、常驻渲染成本与降级路径 |
| 1.7 | 2026-10-26 | 载荷改为带具体目标座位（`subPartId + seatName`）：循环键在客户端算出下一个后直接发目标，将来的选座界面复用同一条包；服务端只校验目标、不排序，两端顺序不需要一致 |
| 1.8 | 2026-10-26 | 明确结果包是客户端应用的唯一依据：同 SubPart 只更新控制关系，跨 SubPart 由客户端主动 `startRiding`（原版 riders 包下一 tick 才发）；补充控制关系与骑乘实体不一致时的兜底 |
| 1.9 | 2026-10-26 | 结果包改为成功时广播给维度内所有玩家（失败只回发起者）：每个客户端都维护该乘客的 `controllingSubsystem`，只回发起者会让其他客户端把乘客画在实体原点；其他玩家的跨 SubPart 骑乘仍交给原版包 |
| 2.0 | 2026-10-26 | 代码复核后修正：俯视变换改为先 `X(-90)` 再 `Y(180-heading)`（原顺序让偏航把世界高度混进横轴）；标记世界坐标改为逐帧现取、不再存进快照；刚性判据只看 Advanced 侧；地图原点改用参考零件位置；结果包去掉 `index`/`total`（服务端不排序，无从生成）并要求纯状态语义；结构事件按实际汇点重映射，`:851` 与 `:895` 保留在原处，双端都发；占用写点全部改走 `setOccupied`；`KeyHooks` 每 tick 轮询、`variantName`/`activate(-1)`/bbox 坐标空间等措辞修正；P0 估算改为 3–4 天 |
| 2.1 | 2026-10-26 | 跨实体切换改为“先挂载、后接管”：② 清旧座位 → ③ `startRiding` → ④ `attachForSwitch`。挂载失败时目标座位从未被占用，回滚只剩恢复来源座位一步；同时说明客户端在结果包里的 `startRiding` 只是提前对齐，骑乘关系仍由服务端权威持有 |
| 2.2 | 2026-10-26 | 挂载再提前到所有座位修改之前：① 交接上车点 → ② `startRiding` → ③ 失败即返回 → ④ 清旧座位 → ⑤ 接管新座位。失败时没有任何状态被改，回滚整段删除；“必须先清旧座位”的理由不成立（`startRiding` 不触发 `SeatSubsystem.removePassenger`，冲量只来自它），仍保留 `beginSwitch` 抑制下车点搜索 |
| 2.3 | 2026-10-26 | 新增 §5.10 数据流：五份状态（`occupied`/`passenger`/骑乘/`controllingSubsystem`/`activeIndex`）的权威端与下行通道、按 tick 的时序、三条必须成对更新的不变量；据此发现控制组只在服务端重置会让 `ViewInputPayload` 丢掉该玩家的视角输入，改为双端一起改写 |
| 2.4 | 2026-10-27 | 新增 §5.11 控制组复位的落地写法：复位目标是座位预设的 `active_index`（K17 炮手预设为 `0`，固定 `-1` 会让炮手没有开火线路）；服务端用新实例替换、接入 `attachForSwitch` 与 `setPassenger`；结果包加 `controlGroup` 字段，客户端照抄而不查表；补充三种写入情形、在途包时序、绑定索引错位的旁证与对应用例 |
| 2.5 | 2026-10-27 | 外部审阅后的三处契约修正：① 参考系改为逐帧采样——快照只存参考 SubPart 与局部前向轴，原点与偏航每帧从 `getWorldPositionMatrix(partialTick)` 取，`fit` 改在车体坐标下算；② 刚性簇改从 `connector.attachedConnector` 建 SubPart 图，覆盖 `autoAttachInternalConnectors` 建立的部件内部连接（`partNet` 的节点是部件）；③ §5.5 补“换座期间在途的旧座位输入”，`ControlBindingPayload` 与 `MovementInputPayload` 共用来源校验（控制对象 + 座位乘客/骑乘），移动包在广播前拦截；同时把 R5、方案 D 与推荐组合里“服务端算目标”的旧表述改成最终协议 |
| 2.6 | 2026-10-27 | 首版实机画面后的 HUD 调整：座位标记只写编号、不写座位名（名字压在地图上互相遮挡，还会被截成 `Driver Se`），座位名移到面板标题（当前座位）与键位提示（下一个座位），经 `Component` 解析翻译键后取纯文本；标记加大到 10/12px 以放得下编号，编号用深色字居中；车体改用无纹理纯色几何、不再设 `Lighting`；居中偏移改为按像素叠在缩放之后 |
| 2.7 | 2026-10-27 | 车体改为半透明透视：外壳用 `MMRenderTypes.solidXray()`（`POSITION_COLOR`、双面、不测也不写深度、主目标），所有面参与混合，透明度从 0x96 降到 0x26；新增内构层——带 hitbox 的骨骼按所属子系统的耐久染色（接近不透明），分两遍提交让内构压在全部外壳之上。子系统耐久色带抽到 `SubsystemDurabilityColors`，与内构 HUD 共用，避免两处色带漂移。透视渲染不写深度，画完标记清深度缓冲与关深度测试的收尾一并删除；`solidDepth` 因失去调用方而移除 |
| 2.8 | 2026-10-27 | 座位标识收敛为纯编号：删掉 `SeatLabels`（靠变体名/子系统名/locator 反推名字，对玩家自行组装的载具不可靠），面板标题、键位提示、actionbar 只写序号；`message.machine_max.seat.switched` 去掉第三个占位符，键位提示里的下一个座位复用 `message.machine_max.seat.indexed`；§5.8 从“标签解析器”改写为“只用编号”的依据 |
| 2.9 | 2026-10-27 | 图例补上“下一个”（绿）：图例四项与标记的四种常见底色一一对应；新增 `seat_map.legend.next` |
| 2.10 | 2026-10-27 | 座位名直取 `SeatSubsystem.name` 的翻译键：面板标题、键位提示、actionbar 都显示 `Component.translatable(name)`，名字按像素宽度截断；标记仍只写编号；不引入从变体名/locator 反推名字的解析（§5.8）；`message.machine_max.seat.indexed` 随解析器一起删除，`seat.switched` 恢复第三个占位符 |
| 2.11 | 2026-10-27 | 面板贴到屏幕左下角：底边距 24px（热栏高 22px，留 2px 间隙），右边缘停在准星与驾驶视线中心之外；聊天行与面板同处左下角，面板画在聊天层之上 |
