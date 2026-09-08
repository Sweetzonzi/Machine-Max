# Machine-Max — 智能体指南

**生成日期:** 2026-06-08 · **分支:** 1.21.1 · **版本:** 1.0.3-beta.6

## 概览

基于 NeoForge 1.21.1 的 Minecraft 载具模组。Kotlin + Java 混合，Gradle 8.9（Groovy DSL），JDK 21。
采用数据驱动的部件化载具组装、实时物理模拟（Bullet 物理引擎，通过 Spark-Core 集成）、22 种子系统类型、内容包扩展机制。

## 依赖项目

| 项目                                                                       | 版本            | 用途                   |
| ------------------------------------------------------------------------ | ------------- | -------------------- |
| [Spark-Core](https://github.com/SolarMoonQAQ/Spark-Core)                 | 1.0.1029      | Bullet 物理引擎封装、实体注册框架 |
| [BallisticsFramework](https://github.com/Sweetzonzi/BallisticsFramework) | 1.0.0.alpha.6 | 终端/外部弹道计算 API        |
| [ApricityUI (AUI)](https://github.com/Sweetzonzi/AUI)                    | 1.1.3-dev     | 载具控制面板 Web UI 框架     |

构建时也依赖 GraalVM 24.2.2（JS 引擎）、JEI、Jade、KotlinForForge、AzureLib、GeckoLib、Create（兼容层）。

## 项目结构

```
io.github.sweetzonzi.machine_max/
├── MachineMax.java                  # Mod 入口，ObjectRegister 中枢（21 个 TODO）
├── common/                          # # 服务端/共通逻辑
│   ├── mech/                        # ★ 核心域 — 见 common/mech/AGENTS.md
│   │   ├── vehicle/                 # ★ 载具聚合根 — 见 vehicle/AGENTS.md
│   │   │   ├── attr/                # SubPartAttr, VariantAttr, ConnectorAttr ...
│   │   │   ├── collision/           # CollisionHandler, CollisionEffectManager
│   │   │   ├── connector/           # AbstractConnector, AdvancedConnector, SimpleConnector
│   │   │   ├── data/                # VehicleData, PartData, SubPartData 序列化
│   │   │   ├── event/               # ConnectorAttachEvent, VehicleSpiltEvent ...
│   │   │   └── interact/            # HitBox, InteractBox, InteractBoxes
│   │   ├── subsystem/               # 22 种子系统实现 — 见 subsystem/AGENTS.md
│   │   │   └── attr/                # SubsystemTypes（枚举）、WorkingState
│   │   │       ├── dynamic_attr/    # 22 个运行时动态属性类
│   │   │       └── static_attr/     # 23 个 JSON 驱动静态属性类
│   │   ├── projectile/              # 数据驱动投射物系统（SoA 数组）— 见 projectile/AGENTS.md
│   │   ├── signal/                  # 信号系统：Signal, SignalChannel, ISignalBus ... — 见 signal/AGENTS.md
│   │   ├── control/                 # 控制绑定系统：ControlBinding, ControlGroup, GuiAction ...
│   │   ├── energy/                  # 能源系统：EnergyGrid, MechPower, IEnergyStorage ...
│   │   └── molang/                  # MoLang 表达式上下文：MechMolangContext
│   ├── item/                        # PartItem, AssemblyItem, WeldingTorch, Crowbar, Blueprint ...
│   │   └── prop/                    # 物品属性类
│   ├── block/                       # Fabricator, ResearchTable, TotalStation, RoadBase
│   ├── entity/                      # MMPartEntity, MMProjectileEntity, PartHitHandler ...
│   ├── menu/                        # FabricatingMenu, BlueprintResearchMenu, VehicleNamingMenu ...
│   ├── recipe/                      # ResearchRecipe, FabricatingRecipe
│   ├── attachment/                  # VehicleAssemblyAttachment, BlueprintAttachment, ControlPreference ...
│   ├── registry/ (Java)             # MMDamageTypes, MMMenus, MMTags
│   └── visual/                      # PartAnimatable, VehicleAnimatable, AnimatableParams,
│                                    # RenderableBoundingBox, SubPartAnimatable, VisualEffectHelper
├── client/                          # # 客户端渲染、输入、GUI — 见 client/AGENTS.md
│   ├── render/
│   │   ├── renderer/                # PartEntityRenderer（主渲染器）, block 渲染器, 投射物渲染器
│   │   ├── gui/                     # Screen、HUD、3D HUD、动画、面板、控件
│   │   │   ├── screen/              # VehicleControlScreen, FabricatingScreen 等 7 个屏幕
│   │   │   ├── hud/                 # AssemblyHud, CustomHud, InteractHud
│   │   │   ├── hud3d/               # AssemblyHud3D 等 3D HUD
│   │   │   ├── panel/               # 载具信息面板等
│   │   │   └── animation/           # GUI 动画工具
│   │   ├── renderable/              # ModelAnimatable, GuiAnimatable
│   │   └── MMRenderTypes.java       # 自定义渲染类型
│   ├── input/                       # KeyBinding, RawInputHandler, CameraController
│   ├── compat/                      # Jade / JEI 客户端兼容
│   │   ├── jade/                    # Jade 插件
│   │   └── jei/                     # JEI 插件
│   ├── event/                       # 客户端事件
│   ├── network/                     # ClientResearchHandler
│   ├── MachineMaxClient.java        # 客户端入口
│   ├── ClientSetup.java             # 视觉特效初始化
│   └── MMClientConfig.java          # 客户端设置
├── network/
│   ├── payload/                     # 39 个网络载荷
│   │   ├── assembly/                # 14 个组装相关载荷
│   │   ├── fabrication/             # 4 个制造相关载荷
│   │   ├── projectile/              # 2 个投射物载荷
│   │   └── research/                # 7 个研究系统载荷
│   ├── handler/research/            # 7 个研究系统处理器
│   ├── AGENTS.md                    # 子模块智能体指南
│   └── MMPayloadRegistry.java       # 载荷注册中心
├── mixin/                           # 12 个 Mixin（6 服务端 + 6 客户端）
├── mixin_native/                    # 针对本项目自身类的 AOP Mixin（machine_max.native.mixins.json 注册）
├── mixin_interface/                  # 3 个 Mixin 接口（IClientLevelMixin, IEntityMixin, IProjectileMixin）
├── util/                            # PD/PID 控制器、地形（LocalHeightField）、MMMath
├── external/                        # 外部资源与嵌入式引擎
│   ├── html/                        # HTML 解析器（HtNode, HtmlLikeParser）
│   ├── js/                          # GraalVM JS 引擎集成（InputSignalProvider, JSUtils）
│   │   └── hook/                    # AxisHook, EventToJS, KeyHooks
│   ├── style/                       # 样式/色彩系统（ColorPalette, StyleProvider）
│   ├── MMDynamicRes.java            # Spark-Core 资源加载
│   └── CommentRemover.java          # 注释移除工具
├── datagen/                         # 数据生成器
│   ├── MMGenerator.kt               # 入口（Kotlin）
│   ├── MMLanguageProviderEN_US.java # 英文语言
│   └── MMLanguageProviderZH_CN.java # 中文语言
└── compat/create/                   # Create mod 兼容层
    ├── CreateCompat.java
    ├── CreateCollisionInfo.java
    └── CreateCollisionResolver.java
```

### Kotlin 源码树（`src/main/kotlin/` — 独立于 Java 源）

```
io.github.sweetzonzi.machine_max/
├── common/
│   ├── registry/                    # 14 个注册文件
│   │   ├── MMItems.kt              # 物品注册
│   │   ├── MMBlocks.kt             # 方块注册
│   │   ├── MMEntities.kt           # 实体注册
│   │   ├── MMAttachments.kt        # 实体附件注册
│   │   ├── MMBlockEntities.kt      # 方块实体注册
│   │   ├── MMDataComponents.kt     # 数据组件注册
│   │   ├── MMDataRegistries.kt     # 数据注册表
│   │   ├── MMCodecs.kt             # 编解码器
│   │   ├── MMCreativeTabs.kt       # 创造模式标签页
│   │   ├── MMCommands.kt           # 命令注册
│   │   ├── MMPackModuleRegistries.kt # 内容包模块注册
│   │   ├── MMResources.kt          # 资源注册
│   │   ├── MMSounds.kt             # 音效注册
│   │   └── MMVisualEffects.kt      # 视觉特效注册
│   ├── resource/modules/            # 12 个内容包模块（数据驱动加载）
│   │   ├── PartModule.kt           # 部件模块
│   │   ├── SubsystemModule.kt      # 子系统模块
│   │   ├── ConnectorModule.kt      # 连接器模块
│   │   ├── AssemblyModule.kt       # 装配体模块
│   │   ├── BlueprintModule.kt      # 蓝图模块
│   │   ├── MaterialModule.kt       # 材质模块
│   │   ├── ColorModule.kt          # 颜色模块
│   │   ├── ControlGroupModule.kt   # 控制组模块
│   │   ├── HudModule.kt            # HUD 模块
│   │   ├── ProjectileModule.kt     # 投射物模块
│   │   ├── TemplateModule.kt       # 模板模块
│   │   └── TooltipModule.kt        # 工具提示模块
│   ├── command/                     # 2 个命令文件
│   │   ├── ResearchCommand.kt
│   │   └── VehicleCommand.kt
│   └── util/sound/                  # 声音合成器
│       ├── MotorSoundSynthesizer.kt
│       └── PistonEngineSoundSynthesizer.kt
└── datagen/
    └── MMGenerator.kt              # 数据生成入口
```

## 快速定位

| 任务         | 位置                                             | 说明                                        |
| ---------- | ---------------------------------------------- | ----------------------------------------- |
| 修改载具物理     | `common/mech/vehicle/`                         | VehicleCore（聚合根）、SubPart、Part、连接器         |
| 新增子系统类型    | `common/mech/subsystem/`                       | 继承 AbstractSubsystem，注册到 SubsystemTypes   |
| 新增网络包      | `network/payload/` + `MMPayloadRegistry.java`  | 按功能子目录归类                                  |
| 新增 GUI/HUD | `client/render/gui/`                           | Screen、HUD、hud3d、panel                    |
| 新增物品/方块/实体 | `common/registry/`（Kotlin）                     | MMItems.kt, MMBlocks.kt, MMEntities.kt    |
| 新增内容包数据    | `src/main/resources/spark_modules/`            | Official\_Pack / builtin / sdkfz          |
| 新增配方       | `common/recipe/` + `spark_modules/.../recipe/` | 两部分配合                                     |
| 修复渲染       | `client/render/renderer/`                      | PartEntityRenderer 是主载具渲染器                |
| 修复碰撞       | `common/mech/vehicle/collision/`               | CollisionHandler + CollisionEffectManager |
| 修复输入/控制    | `client/input/` + `common/mech/control/`       | 键盘 → 网络包 → 信号系统                           |
| 修复能源系统     | `common/mech/energy/`                          | EnergyGrid 直流总线                           |
| 修改信号系统     | `common/mech/signal/`                          | SignalChannel, ISignalReceiver/Sender     |
| 修改投射物      | `common/mech/projectile/`                      | ProjectileManager（SoA）、BFDamageApi        |
| 修改 molang  | `common/mech/molang/`                          | MechMolangContext                         |
| 修改方块实体     | `client/render/renderer/block/`                | Fabricator、ResearchTable、TotalStation     |

## 命令

```bash
./gradlew build              # 构建模组 JAR
./gradlew runClient          # 启动客户端
./gradlew runServer          # 启动专用服务器
./gradlew runGameTestServer  # 运行 NeoForge 游戏测试
./gradlew runData            # 重新生成 src/generated/resources/
```

## 约定

- **Kotlin 声明，Java 逻辑**：注册表文件（MM\*.kt）、数据生成器、资源模块用 Kotlin；载具核心、物理、网络、渲染用 Java。
- **ObjectRegister 模式**：单一 `MachineMax.REGISTER` 字段通过 Spark-Core 处理所有 NeoForge 注册。
- **线程标注**：方法 Javadoc 标注调用线程 — `主线程`（20tps）vs `物理线程`（Bullet 物理步进）。
- **内容包一切**：部件、子系统、连接器、配方、蓝图全在 JSON 中定义，位于 `spark_modules/` 下。
- **双数学库**：物理使用 JME（`com.jme3.math.*`），渲染使用 JOML（`org.joml.*`）。通过 `SparkMathKt.*` 转换。
- **控制绑定管线**：RawInputHandler → KeyBinding → 网络载荷 → 服务端信号系统。
- **动画体在 Part**：`Part implements IAnimatable<Part>`（共享 `ModelPose`、`local.*` Molang、涂装）；`SubPart` 只负责刚体与骨骼子树渲染，`MMPartEntity` 仅作渲染入口/原版交互点。
- **信号寻址**：路由用 `ISignalReceiver.getSignalAddress()`，保留地址 `"local"` = Part、`"global"` = 装配体；`getName()` 仅身份/日志。

## 反模式（本项目特有）

- **严禁混用 JME 和 JOML 数学**：物理用 `com.jme3.math.*`，渲染用 `org.joml.*`。始终通过 `SparkMathKt.*` 转换。
- **严禁在** **`partNet`** **之外使用** **`synchronized`**：仅 VehicleCore.java 中存在 2 处 `synchronized` 块（保护 Guava `MutableNetwork`）。其他所有共享状态使用 `ConcurrentHashMap`、`ConcurrentLinkedQueue`、`volatile` 或 `CopyOnWriteArraySet`。
- **严禁在主线程直接操作物理体**：使用 `getPhysicsLevel().submitImmediateTask(PPhase.ALL/PRE, ...)`。
- **严禁忽略** **`DestroyableRigidObject.updateLock`**：服务端→同步数据期间置为 `true`，防止反馈循环。
- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **子系统必须配套 static\_attr**：每种子系统类型都需要对应的 JSON 加载用静态属性类。

## 独特风格

- **ConcurrentLinkedQueue 累加器模式**：物理线程入队伤害/冲击/完整性变更；主线程在 `handleAccumulated*()` 中清空。
- **Volatile 快照模式**：`CollisionEffectManager.latestWheelSnapshot` — 物理线程写入，主线程读取，仅取最新值。
- **SoA 投射物数组**：`ProjectileManager` 使用原始类型数组（`posX[]`、`velX[]` 等），swap-remove O(1) 删除。
- **双数学转换文件**：9 个文件同时导入 JME 和 JOML（MMMath, PosRot, VehicleAnimatable 等）。
- **信号回调重构**：直送模式代替绕圈回调，支持多跳传播。
- **对象管理器模式**：`ObjectManager` 按维度注册 VehicleCore，支持 `getAllObjects()` / `getVehicleByUUID()`。

## 补充说明

- **复合构建**：`settings.gradle` 条件性 include `../Spark-Core`、`../BallisticsFramework`、`../AUI` 三个本地源码项目。本地目录不存在时回退到 Maven jar。
- **无单元测试**：仅 NeoForge 运行时游戏测试（`runGameTestServer`）。无 `src/test/` 目录。
- **CI 使用 JDK 17**，构建目标 Java 21 字节码。
- **21 个 TODO 在 MachineMax.java** — 包含蓝图存储、网络包重构、炮塔控制、机甲外骨骼、通用分层作动器控制等完整路线图。
- **已知崩溃（已探明）**：多线程物理 + 关节 = 崩溃。**根因**：关节连接的两个刚体均为运动学模式（Bullet 不支持两运动学体间的 Joint 约束）。临时方案：禁止将刚体设为运动学以停止其外力影响，使用speedFactor。
- **耦合扭矩禁用**：`MotorSubsystem.coupleTorque = 0`，因轮子停止时振荡。
- **CI/CD**：GitHub Actions（`build.yml` — push/PR 自动构建；`pages.yml` — 文档发布到 GitHub Pages）。
- **打包说明**：部分内容包（如 sdkfz/）属于外部项目示例，打包时可能需要分离。

## 参考文件

- `docs/glossary.md` — 自动生成的术语表（287 行，中英对照）
- `docs/武器系统-数据驱动投射物设计文档.md` — 投射物系统设计文档
- `docs/武器系统-弹药供给与装填系统设计文档.md` — 弹药系统设计文档
- `docs/观瞄系统-CameraSubsystem详细设计.md` — 摄像机子系统设计文档
- `docs/信号回调机制重构设计.md` — 信号系统重构设计
- `docs/AUI载具控制面板实现计划.md` — AUI 控制面板实现计划
- `docs/AUI性能边界.md` — AUI 性能分析
- `docs/机娘模组企划.md` — 机娘模组企划
- `docs/wiki/` — MkDocs Wiki 文档站点（快速上手、载具系统完全指南、子系统详解等）

