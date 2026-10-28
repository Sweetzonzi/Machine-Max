# 计划与设计文档索引

本目录存放 Machine-Max 的计划与设计文档，按**实现状态**分为三个子目录。每份文档的头部都有一行 `**状态**`，与本索引一致；判定依据写在每行的「判定依据」列，可逐条回代码复核。

## 判据

| 子目录 | 含义 | 判定依据 |
| --- | --- | --- |
| `已实现/` | 文档描述的能力已在代码中落地 | 仓库内存在对应类/文件，且入口在运行路径上 |
| `进行中/` | 部分落地，或只到原型/骨架阶段 | 一半的类已存在，或只有资源原型而无逻辑接入 |
| `计划中/` | 尚未实现，或为分析提案、上游问题记录 | 代码中不存在文档描述的类型 |

三类的划分只看代码现状，与文档的写作完备程度无关：`已实现/` 里的文档同样可能留有待办小节。实机画面与玩法的验收结论不进本判据，`已实现/` 的文档可以在状态行里单独标注「实机验收待完成」。

## 已实现

| 文档 | 判定依据 |
| --- | --- |
| [PDA蓝图终端-详细设计文档](已实现/PDA蓝图终端-详细设计文档.md) | 提交 `271bb934`；`client/render/gui/pda/` 与 `PdaItem` 已落地 |
| [PDA蓝图终端-接口设计文档](已实现/PDA蓝图终端-接口设计文档.md) | 与详细设计同批落地（提交 `271bb934`）；注册项、数据组件、网络包字段均按本文口径实现 |
| [伤害结算相位与装配体投递修改计划](已实现/伤害结算相位与装配体投递修改计划.md) | 提交 `20355f6a`；`settleAccumulatedDamage` 移至 `postTick`，`SubPartHitDamage` 携带完整批次 |
| [制造蓝图-配方与物品拆分设计](已实现/制造蓝图-配方与物品拆分设计.md) | 零件配方与通用制造配方已拆分，两类蓝图物品与装配侧清单均已落地 |
| [动画系统-Part作为动画体重构设计](已实现/动画系统-Part作为动画体重构设计.md) | `Part implements IAnimatable<Part>`；模型/动画/Pose 控制器已上移到 `Part` |
| [动画系统-事件触发动画设计](已实现/动画系统-事件触发动画设计.md) | 事件触发动画的第一版接口已落地，Spark-Core 状态机侧已接入 |
| [子系统-模块化损坏与子模块设计](已实现/子系统-模块化损坏与子模块设计.md) | `ModularSubsystem`、`SubModule`、`AbstractModuleAttr` 已落地；首期接入火炮的 `BarrelModuleAttr` / `BreechModuleAttr` / `CapacitorModuleAttr` |
| [抓取系统-详细设计文档](已实现/抓取系统-详细设计文档.md) | `common/mech/grab/` 下 `GrabManager`、`GrabConstraint`、`GrabPhysics` 已落地 |
| [抓取系统-接口设计文档](已实现/抓取系统-接口设计文档.md) | `common/mech/grab/` 已落地；分层与职责边界按本文实现，方法签名见代码 |
| [手动零件组装客户端权威重构计划](已实现/手动零件组装客户端权威重构计划.md) | 装配预览与选择状态存于客户端；服务端只在放置时收包校验 |
| [相机系统-镜头抖动重构设计](已实现/相机系统-镜头抖动重构设计.md) | `CameraShakeController` 承担抖动求解，`DesaturateEffect` 走后期处理管线 |
| [武器系统-投射物拦截与受击判定计划](已实现/武器系统-投射物拦截与受击判定计划.md) | 四阶段各自一个提交（`cbe211dc`、`1467d475`、`f67044a3`、`a7153e82`） |
| [武器系统-爆炸波前视觉设计](已实现/武器系统-爆炸波前视觉设计.md) | 第一期的屏幕空间折射已落地（`blast_distortion` 着色器 + `BlastDistortionEffect`）；第二期的世界内薄壳辉光未实现 |
| [武器系统-爆炸系统接口设计](已实现/武器系统-爆炸系统接口设计.md) | `common/mech/explosion/` 的类与签名按本文口径实现 |
| [武器系统-爆炸系统详细设计](已实现/武器系统-爆炸系统详细设计.md) | §十七 阶段 A–J 已落地；阶段 K/L 待上游通用 Effect 体系 |
| [武器系统-碰炸引信与爆炸战斗部实现备忘](已实现/武器系统-碰炸引信与爆炸战斗部实现备忘.md) | 提交 `90fd8fef`；`warheads` 字段与固定碰炸引信已落地 |
| [武器系统-制导组件实现备忘](已实现/武器系统-制导组件实现备忘.md) | 提交 `c1c0db71`；`GuidanceLaw`、`PurePursuitGuidance`、`AuthorityLimits` 已落地 |
| [研究台界面-研发分组与蓝图库统一重构计划](已实现/研究台界面-研发分组与蓝图库统一重构计划.md) | 提交 `87615e1e`；研究台树形界面与研发分组已在 AUI 上实现 |
| [蓝图库-玩家设计保存与取用设计](已实现/蓝图库-玩家设计保存与取用设计.md) | `BlueprintLibraryClient` 与客户端 `saved_blueprints/*.json` 已落地 |
| [观瞄系统-炮镜HUD自定义分划设计](已实现/观瞄系统-炮镜HUD自定义分划设计.md) | 分划行为与炮镜跟随语义已落地；资源形态为 `huds/*.json` 样式 + `hud_layouts/*.json` 布局，由 `HudModule` / `HudLayoutModule` 载入，客户端 `CustomHud` 渲染 |
| [载具下车位置选择-实现计划](已实现/载具下车位置选择-实现计划.md) | 零件 JSON 的下车 locator 与上车位置缓存均已接入乘客落点选择 |
| [内容包诊断-SparkCore适配实现](已实现/内容包诊断-SparkCore适配实现.md) | Spark-Core `e526385d`（v1.4）；`MMDynamicRes` 只留注册表与索引，14 个模块去 catch、JSON 解码走 `PackCodec`，配方/研发经 `SparkPackIssues.report` 上报，领域层抛 `PackContentException`；构建、`runData`、`runServer` 与坏文件探针已验证 |
| [内容包分层与命名](已实现/内容包分层与命名.md) | `spark_modules/` 下九个第一方包按 `builtin → kit → 车包` 三层划分并统一 `Machine-Max_` 目录名；`sdkfz` 拆成六个包、`official` 改名 `civilian`、5 件独立道具并入 builtin、每个包自带 schema 副本；`runServer` 加载的部件与子系统数量与拆分前一致 |
| [武器系统-投射物弹道预测与区块预载设计](已实现/武器系统-投射物弹道预测与区块预载设计.md) | 提交 `836194ea`、`db503f23`；`common/mech/trajectory/` 下 `TrajectoryStep`（唯一步进，趟一与客户端外推共用）、`ChunkCoverage`（按到格子的距离判，含角点与外扩）、`ElevationFilter`、`TerrainPreload`（视界 `W = v(L+Δ)+16`、逐区块 Y 并集、按区块 `hold`），接进 `ProjectileManager#preTick` 与 `LauncherSubsystem#onTick` 的首段；物理线程每步末发布快照表；四组纯 JUnit。未做 `needsReplan`（Δ = 1 tick 下空转），`reactivations` 与两条 GameTest 待验证 |
| [观瞄系统-HUD专用MoLang上下文设计](已实现/观瞄系统-HUD专用MoLang上下文设计.md) | `client/render/gui/hud/molang/` 下 `HudMolangContext`、`WeaponHudMolangContext`、`HudRuntimeElement`、`HudBallisticTable`、`HudPageBinding` 与 `HudAnimationExpressions.kt` 均已落地，由 `CustomHud#tick` 调度 |
| [武器系统-武器控制器HUD样式与布局设计](已实现/武器系统-武器控制器HUD样式与布局设计.md) | `HudWeaponSnapshot`、`WeaponHudElement` 已落地；控制器宿主、候选并集与 `selection_visibility` 显隐在 `CustomHud` |
| [部件系统-公共基础抽象实施计划](已实现/部件系统-公共基础抽象实施计划.md) | `common/mech/part/` 下 `AbstractPart`、`AbstractPartType`、`PartKind(s)`、`PartFactory`、`PartDefinitions`、`PartCreationContext` 与 `vehicle/RegularPart`、`RegularPartType`、`RegularPartData` 齐备；现状 API 见 `docs/部件公共基础接入.md`；§1.3 的 BodyPart 等条目属 ARMS-Core 后续任务 |
| [履带系统-内容包模块与类型注册设计](已实现/履带系统-内容包模块与类型注册设计.md) | `FittingModule` / `FittingType` / `TrackType`、`MMDataComponents.FITTING_TYPE`、`TrackLinkData.track_type`、`MMDynamicRes.FITTING_TYPES` 双端表与协议版本均已落地；单节模型渲染与履带口径的接触/装甲在 `TrackRenderer`、`TrackAttr` |
| [履带系统-数据模型设计](已实现/履带系统-数据模型设计.md) | `TrackAttr`、`FittingVisual` 的字段与 Codec 已落地，材料复用 `track_steel`，接触与装甲口径、单节模型渲染同前条 |
| [渲染-屏幕空间光照物理化设计](已实现/渲染-屏幕空间光照物理化设计.md) | `screen_space_light.fsh` 含线性空间 `1/d²` + 四窗 + 朝向响应 + 有界合成曲线、沿视线积分的空气散射光晕与屏幕空间星芒；`LightMinDist[16]`、`LightGlow[32]`、`LightGlare[16]` 逐槽上传；标定值为首轮锚点，实机观感待按 §4.4 复核 |
| [武器系统-弹药标签与弹链显示设计](已实现/武器系统-弹药标签与弹链显示设计.md) | 历史快照（基线 `4c74444b`）；弹药标签判定在 `BreechModuleAttr.isAmmoCompatible`，弹链在 `WeaponControllerSubsystem#beltOf`；运行期读取由 `CustomHud` 接管 |
| [载具系统-部件摧毁状态与组装重力重构计划](已实现/载具系统-部件摧毁状态与组装重力重构计划.md) | 提交 `e90f2e42`、`2d7c4da9`、`b1d667e4`；`AbstractPart` 持有 `destroyTime` / `tickDestroyState` / `applyGravity`，`RegularPart` 以 `assembling` 命名，`RegularPartData` 含 `destroyed` / `destroy_time`，协议升到 `sync:2.7.0` / `misc:1.3.0`；24 个 GameTest 全绿，实机画面与玩法验收待完成 |
| [HUD上下文实现与验收](已实现/HUD上下文实现与验收.md) | 上下文、快照、绑定与物理步推进已进入代码，`gradlew --offline build` 含 81 项单测通过；进世界画面与控制组交互的实机验收另行跟踪 |
| [K17动态武器HUD组件接入](已实现/K17动态武器HUD组件接入.md) | 三个 WC 的样式、布局与 `documents/weapon_hud_components/` 页面均就位；`WeaponHudComponentsTest`、`K17WeaponHudLayoutTest` 覆盖绑定与七档 GUI 尺寸；实机验收另行跟踪 |
| [界面-车内换座与座位布局HUD调研](已实现/界面-车内换座与座位布局HUD调研.md) | 提交 `e0fb8a49`；`SeatMapHud`、`SeatMapLayout`、`SubsystemDurabilityColors`、`SeatSwitchHelper`、`SeatOrder` 与 `SeatSwitchPayload` / `SeatSwitchResultPayload` 均已落地，`MMGuiManager` 用 `registerAboveAll` 常驻注册；实机验收通过，P3–P4 未实现 |

## 进行中

| 文档 | 当前进度 |
| --- | --- |
| [控制分配-作动器wrench分配设计](进行中/控制分配-作动器wrench分配设计.md) | `util/WrenchAllocator` 批量阻尼伪逆／批量钉界基线已实现，14 项数学、8 项上下文与 4 项裸 Bullet 测试、86 组 NumPy SVD 对照通过；内核微基准覆盖 16／32／64 器件，输入与求解缓冲统一在 Workspace，动态更新方向后的填充求解实测 0 B/步。完整飞控、响应与游戏接线未完成，结果与范围见该文 §11.5 |
| [履带系统-物理实现设计文档](进行中/履带系统-物理实现设计文档.md) | `TrackLink` / `TrackAttr` / `TrackEnvelope2D` / `TrackMemberRef` / `TrackLinkData` 已落地；纯物理 JUnit（ratio/轴/启停/长跑/拆约束）、`VehicleData` 序列化、生命周期、履带列表（无名字）、`FittingItem` / `TrackItem` 的铺设闭合流程、摩擦/装甲/张力、滚动/命中音效覆盖已接入；长度耐久（`durabilityPerLink` / `lengthPerLink` + 包络推导 N 与最大耐久）、关键事件同步、客户端履带节渲染与 X 角速度相位已落地；履带型号已数据驱动（`fittings/` + `FittingModule` / `FittingType` / `TrackType`），近处逐节绘制型号的单节模型、远处与模型缺失回退程序化；型号 → 物品由 `FittingItems` 派发，物品栏优先 `visual.icon` 的 2D 图标、缺图标回退单节 3D 模型，独立配件创造栏按型号生成条目；用料按节数记录（`TrackLink.linkCount()`），暂不消耗物品；履带控制阶段 A / B（`AbstractGroundControllerSubsystem` + `TrackControllerSubsystem` + `TrackDriveSubsystem`，单流）已落地，阶段 C / D 未实现；纯 2D 原型：凸包基线 9/9 场景 + 157/157 断言 + 300/300 随机，有序非凸 7/7 场景 + 17/17 用例 + 270/270 断言（`prototype/track-envelope-2d/`）；`gradlew check` 通过（含 `checkSchemas` 与全量 JUnit） |
| [履带系统-控制与驱动子系统设计](进行中/履带系统-控制与驱动子系统设计.md) | 实施中（v5）；单流八方向意图、外侧锚定的带速上限、驱动/制动预算选择与正反向关节物理回归已接入。共享基类、属性注册与内容包 schema 已落地；阶段 C / D（双流转向、多履带实机验收、HUD）未实现 |
| [武器系统-组件化投射物与类型体系设计](进行中/武器系统-组件化投射物与类型体系设计.md) | 作为投射物体系总纲：类型派发（阶段 A）、制导组件、碰炸引信与战斗部均已落地；光束武器与引信多样化未实现 |
| [AUI载具控制面板实现计划](进行中/AUI载具控制面板实现计划.md) | `vehicle_control.html` 已就位；面板数据源 `ControlDataAccessor` 当前走 `FOR_GUI_TEST` 测试数据，未接真实控制组 |
| [渲染-屏幕空间光照通用化设计](进行中/渲染-屏幕空间光照通用化设计.md) | P1（通用通路、光源表、曳光来源与 `screen_space_light` 链）、P2（内容包 `light` 火光参数、`sync:2.2.0` 同步、客户端火光状态与 `ExplosionLightSource`、光源表影响体剔除）、枪口火光（内容包 `visual.muzzle_flash`、`MuzzleFlashLightSource`、优先级 15）已落地；P4 车灯球光、P5 锥光与 S3 未实现 |
| [界面-HUD样式挂载与布局设计](进行中/界面-HUD样式挂载与布局设计.md) | V1 已落地并实机验收：`HudStyle`/`ModelHudStyle`/`HudElement`/`HudLayout`、`HUD_STYLES`/`HUD_LAYOUTS`、`huds` 与 `hud_layouts` 两个内容包模块；宿主分组 `HudView`/`IHudHost` 覆盖座椅、相机/瞄具与武器控制器。V2-b AUI 载体已落地：`AuiHudStyle`（`aui_hud`）、`HudAnchor` 与元素 `anchor`、每元素独立 `Document`、`CustomHud` 按布局顺序手动绘制，待实机验收。V2-a（`hud.*` 上下文、`data-mm-*` 绑定层）、V3（`WeaponHudElement`、`weapon.*`）、V4（玩家覆盖）未实现 |
| [观瞄系统-视点与瞄准控制实施记录](进行中/观瞄系统-视点与瞄准控制实施记录.md) | P0–P6 的实现面均已接入并跑通自动化用例：实体视点、第三人称跟随/自由、固定炮镜期望目标、完整目标上报、WC 持久目标、机构适配与相机路由。剩余：设备模型骨骼读取观察角的接线、多人一致观察角同步（明确为后续 TODO）、客户端与多人实机验收 |
| [观瞄系统-视点与瞄准控制重构方案](进行中/观瞄系统-视点与瞄准控制重构方案.md) | `AbstractViewpointSubsystem`、`ThirdPersonViewpoint`、`SightSubsystem`/`CameraSubsystem` 平级重构与第一方内容接线均已落地；模型骨骼接线与客户端动态体验未完成，进度细节见上一行的实施记录 |
| [武器系统-投射物曳光光照设计](进行中/武器系统-投射物曳光光照设计.md) | S1（逐像素光照）、S2（光源级遮挡）与 S4（法线响应，随共享着色器）已落地；S3 未实现，待游戏内实测标定。F1（爆炸波前折射的遮挡修复）已落地并实测通过；曳光链已并入通用 `screen_space_light` 通路 |
| [武器系统-投射物模型渲染设计](进行中/武器系统-投射物模型渲染设计.md) | `visual.model` / `visual.texture` 两个字段已落地（`9m133_kornet` 可解析到 sdkfz 包的弹体模型，待游戏内验收）；朝向修正、缩放（`model_scale` / `model_rotation` / `model_lod_distance`）与投射物动画驱动未实现 |
| [界面-内容包AUI页面加载可行性调查](进行中/界面-内容包AUI页面加载可行性调查.md) | 结论已落地：`DocumentModule` 在客户端把内容包 `documents/` 写入 Spark `CLIENT_PACK`（`apricityui:apricity/<key>`），HUD/手册/PDA 共用，`LOCAL_ONLY` 不参与服务端同步；AUI 自动重载并在 `onFinish` 补一次 `HTML.scan()`。待客户端实机验收；`AuiResourceService` 包装器为备选 |
| [碰撞系统-撞碎与压溃实现计划](进行中/碰撞系统-撞碎与压溃实现计划.md) | P0（纯函数/材质门/服务端系数）、P1（Level 收集与诊断）、P2（即时与聚合判定、共享耐久、部件伤害投递、删块与快照补偿）代码已接入并通过离线测试，等待游戏内验收；P3 标定与兼容待做。每 Level 每子步一轮 Post 接触聚合，撞击资格取材质 `impact_min_speed`，`C_J/C_D/approachEpsilon` 在服务端配置；压溃与零件间、生物路径迁移不排期 |

## 计划中

| 文档 | 现状 |
| --- | --- |
| [武器系统-光束系统详细设计](计划中/武器系统-光束系统详细设计.md) | 设计阶段；代码中尚无 `RayProjectileType` / `RayManager` / `RayLauncherSubsystem` |
| [渲染-屏幕空间遮挡S3设计](计划中/渲染-屏幕空间遮挡S3设计.md) | 设计完成、未实现。表面项改用屏幕空间阴影（`P→C` 沿线 8 步、`raw` 深度仿射插值），星芒继续用光源级判定、光晕继续用逐像素 `t_hit`；含性能口径与失败模式清单 |
| [感知系统-Reading与目标估计设计](计划中/感知系统-Reading与目标估计设计.md) | 初步设计；代码中尚无 `Reading` 相关类型，目标目前仍以裸坐标点传递 |
| [武器系统-武器HUD信息分级与视角形态需求分析](计划中/武器系统-武器HUD信息分级与视角形态需求分析.md) | 需求分析；定义 `minimal` / `standard` / `scope` / `full` 四款呈现、武器类型画像、第三人称/炮镜分工与装填环语义；内容包中尚无对应的四款布局 |
| [武器系统-爆炸系统破坏期性能与优化建议](计划中/武器系统-爆炸系统破坏期性能与优化建议.md) | 建议稿；破坏期成本模型与优化候选项均未实施 |
| [履带系统-重力下垂渲染实现方案](计划中/履带系统-重力下垂渲染实现方案.md) | 未实现；`TrackRenderer` 中没有下垂/悬链求解代码 |
| [物理-运动学刚体接约束入世后步进崩溃](计划中/物理-运动学刚体接约束入世后步进崩溃.md) | 上游 issue 草稿 + 验证记录；项目侧已用平动/转动系数归零规避（提交 `00e3697d`），提交上游一事未完成 |

## 未纳入本目录

| 文件 | 说明 |
| --- | --- |
| `docs/武器系统-弹药命名规范.md` | 内容包作者的**推荐标准**，不是计划或设计文档，因此不参与三态划分 |
| `docs/部件公共基础接入.md` | 公共部件基础与模板的现行 API 说明，是 `部件系统-公共基础抽象实施计划` 的落地侧文档 |
| `docs/AUI性能边界.md` | AUI 框架能力与性能调研报告，不是计划或设计文档。调研对象为 AUI v1.1.6-dev，项目当前依赖 1.2.8，结论需复核（§8.5 的 #3、#8、#10 在 1.2.7 上已不成立；这些核对尚未按 1.2.8 重做） |
| `docs/glossary.md` | 自动生成的术语表 |
| `docs/wiki/` | 用户向 Wiki 站点，由 `docs/mkdocs.yml` 的 `docs_dir: wiki` 驱动，与本目录无关 |

## 已知待办

- `AbstractModuleAttr` 的类 Javadoc 含「不再使用 Codec dispatch」这类以旧实现为前提的表述，缺少同一文档内的基线，违反仓库的文档自包含规范，需要改写为对当前事实的绝对陈述。
