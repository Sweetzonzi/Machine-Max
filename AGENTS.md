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
| [ApricityUI (AUI)](https://github.com/Sweetzonzi/AUI)                    | 1.2.5         | 载具控制面板 Web UI 框架     |

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
│   ├── menu/                        # FabricatingMenu, ItemStorageSubsystemMenu, VehicleNamingMenu ...
│   ├── recipe/                      # ResearchRecipe, FabricatingRecipe
│   ├── attachment/                  # VehicleAssemblyAttachment, BlueprintAttachment, ControlPreference ...
│   ├── registry/ (Java)             # MMDamageTypes, MMMenus, MMTags
│   └── visual/                      # PartAnimatable, VehicleAnimatable, AnimatableParams,
│                                    # RenderableBoundingBox, SubPartAnimatable, VisualEffectHelper
├── client/                          # # 客户端渲染、输入、GUI — 见 client/AGENTS.md
│   ├── render/
│   │   ├── renderer/                # PartEntityRenderer（主渲染器）, block 渲染器, 投射物渲染器
│   │   ├── gui/                     # Screen、HUD、3D HUD、动画、面板、控件
│   │   │   ├── screen/              # VehicleControlScreen, FabricatingScreen 等 6 个屏幕
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
│   │   └── research/                # 6 个研究系统载荷
│   ├── handler/research/            # 6 个研究系统处理器
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

### 开发期 mod 目录（两个）

开发期用的第三方 jar 放在仓库根，按「哪些 run 需要它」分两个目录，各自是一个私有配置（`canBeConsumed = false`，不属于任何 variant）：

| 目录 | 内容 | 落点 | 哪些 run 加载 |
|------|------|------|---------------|
| `mods/` | 双端都要：Curios、SuperbWarfare | 私有配置 `devServerMods` → main 的 `runtimeClasspath` | `runClient`、`runServer`、`runGameTestServer`、`runData` |
| `mods-client/` | 仅客户端：DistantHorizons、Iris、Sodium | 私有配置 `devClientMods` → `runClient` 任务的 `classpathProvider` | 只有 `runClient` |

- **jar 不入库**：三份目录都只提交各自的 `README.md`，`.gitignore` 忽略其中的 `*.jar`；clone 之后按 README 里的清单自行下载放置（版本以 README 为准）。目录为空或整个不存在时，对应的 run 只是少加载几个 mod。ARMS-Core 用的是同一套目录布局与同两条挂载规则。
- **判据是 jar 在不在该 run 的 JVM 类路径上**。run 的类路径就是 main 的 `runtimeClasspath`：moddev 的 `RunGameTask.exec()` 执行 `classpath(getClasspathProvider())`，`classpathProvider` 由 `ModDevRunWorkflow#setupRunInGradle` 用 `sourceSet.runtimeClasspath` 填充。FML 的开发期 mod 发现读的也是这条类路径——`UserdevLocator` 用 `DevEnvUtils` 在系统类加载器上枚举 `META-INF/neoforge.mods.toml`，即 `java.class.path`。
- **不能挂 `<run>AdditionalRuntimeClasspath`**：那条配置（`RunModel` 为每个 run 建的同名配置）只被 `WriteLegacyClasspath` 写进 `build/moddev/<run>LegacyClasspath.txt`，由 `BootstrapLauncher` 读取后建 MC-BOOTSTRAP 模块层，并用它取代 `java.class.path`。写在那里的 jar 不属于任何 run 的类路径，因此不会被当作 mod 扫描；同一个 jar 若同时出现在模块层与 mod 列表里，还会让同一个包被两层导出。
- **两个目录都不能写进 `runtimeOnly`**：文件依赖没有坐标，声明在 `runtimeOnly` 上会作为 root component 进入 `runtimeElements`，随复合构建原样传给下游项目（如 ARMS-Core）的每一个 run。DistantHorizons 会在 `ServerAboutToStart` 里把服务器强转 `DedicatedServer`，让下游的 `GameTestServer` 在启动阶段以 `ClassCastException` 退出。`runtimeClasspath` 只用于解析、不属于任何 variant，挂它不受此影响。
- **仅客户端模组只能挂 run 任务**：`runServer`、`runData`、`runGameTestServer` 与 `runClient` 共用 main 的 `runtimeClasspath`，所以 `mods-client/` 加在 `runClient` 的 `classpathProvider` 上。这条挂法只覆盖 Gradle 发起的 run：从 IDE 发起时类路径由 IDE 模块给出（四个 run 共用一份），`gradlew createClientLaunchScript` 生成的启动脚本也取 source set 的 `runtimeClasspath`，两者都只带 `mods/`。
- **GeckoLib 与 Spark 不放 `mods/`**：两者的 jar 与其它来源的同名模块同时在类路径上时，ModLauncher 在模块解析阶段中止，服务端起不来——
  `java.lang.module.ResolutionException: Modules geckolib and geckolib.neoforge export package … to module mixinextras.neoforge`。
  GeckoLib 已由本项目与 Spark-Core 的 Maven 依赖 `software.bernie.geckolib:geckolib-neoforge-<mc>:<geckolib_version>` 提供，不需要 jar。Spark 的模块来自 Spark-Core：那份构建脚本用 `implementation(files(fileTree("mods")))` 声明 `mods/spark-1.10.124-neoforge.jar`，它作为 root component 进入 Spark-Core 的 `runtimeElements`，于是出现在每个消费方的 `runtimeClasspath` 上（本项目 `gradlew runClient` 的 Mod List 里就有 `spark 1.10.124`）；本地再放一份同名模块就会撞包。两份 jar 都放在 `mods-disabled/`（`mods-disabled/` 不参与任何 run）。
- 顺序要求：两个私有配置必须在 `build.gradle` 里先声明（`repositories.gradle` 由 `apply from` 在本文件之后执行，那里只填内容）。
- 核对办法：`run/logs/latest.log` 里 `ModDiscoverer` 打出的 Mod List 是最终生效的 mod 集合；`build/moddev/{client,server,data,gameTestServer}LegacyClasspath.txt` 只是 MC-BOOTSTRAP 模块层的清单（由 `gradlew write<Run>LegacyClasspath` 刷新），其中出现某个 jar 不代表它会被当成 mod 载入。

## 约定

- **Kotlin 声明，Java 逻辑**：注册表文件（MM\*.kt）、数据生成器、资源模块用 Kotlin；载具核心、物理、网络、渲染用 Java。
- **ObjectRegister 模式**：单一 `MachineMax.REGISTER` 字段通过 Spark-Core 处理所有 NeoForge 注册。
- **线程标注**：方法 Javadoc 标注调用线程 — `主线程`（20tps）vs `物理线程`（Bullet 物理步进）。
- **内容包一切**：部件、子系统、连接器、配方、蓝图全在 JSON 中定义，位于 `spark_modules/` 下。
- **双数学库**：物理使用 JME（`com.jme3.math.*`），渲染使用 JOML（`org.joml.*`）。通过 `SparkMathKt.*` 转换。
- **控制绑定管线**：RawInputHandler → KeyBinding → 网络载荷 → 服务端信号系统。
- **动画体在 Part**：`Part implements IAnimatable<Part>`（共享 `ModelPose`、`local.*` Molang、涂装）；`SubPart` 只负责刚体与骨骼子树渲染，`MMPartEntity` 仅作渲染入口/原版交互点。
- **信号寻址**：路由用 `ISignalReceiver.getSignalAddress()`，保留地址 `"local"` = Part、`"global"` = 装配体；`getName()` 仅身份/日志。

## 文档编辑规范

### 自包含性检查（编辑任何文档后、报告完成前必做）

**适用范围**：`docs/`、`docs/wiki/`、各 `AGENTS.md`、`src/main/resources/spark_modules/*/docs/`，以及代码注释与 Javadoc。
`docs/武器系统-爆炸系统详细设计.md` 首部的「**本文自包含**」声明只覆盖了「不要求先读别的文档」；本节补齐另一半——**也不要求读过本文的旧版本**。

**失效模式**：编辑留下的「差分残留」——句子的成立以读者知道修改前的内容为前提。
典型：`文件名不再使用 X`、`掩体仍然生效`、`A 取消，改为 B`、`（原 foo()）`、`比之前更简单`。

**唯一判据**：假设读者只拿到当前文件的最终版本，从未看过历史版本、git diff、PR 描述或本次对话，该句是否仍能被无歧义地理解与验证？不能 → 违规，必须修复。

**第一步：机械扫描（不要凭记忆）** 对本次编辑的文件检索触发词，形成候选清单再逐条判断：

```bash
rg -n '不再|不再需要|不再依赖|仍然|依旧|仍旧|照旧|还是|取消|移除|删除|改成|改为|换成|替换为|新增|补充|之前|原先|原来|本来|以前|原有|旧版|过去|同上|如前所述|见上文|（原|曾用名|变更前|变更后' <文件>
```

结构性触发（无触发词也要查）：

- 无基线的比较级（更快、更简单、更稳）——比什么？
- 悬空代词回指（它／该方案／上述做法）指向已删除内容
- 章节交叉引用（§9.2、见 2.6）指向已删除或已重编号的章节
- 表格中的「变更前/变更后」列、"建议改为…"（隐含最初的做法）
- 代码注释与 Javadoc 中的 `现在不再…`／`改为…`

**第二步：判定分级**

| 级别        | 特征                                             | 处理                                    |
| --------- | ---------------------------------------------- | ------------------------------------- |
| A 必须改     | 以旧状态为基线描述新状态                                   | 改成对当前事实的绝对陈述                          |
| B 需重写或搬家   | 版本对比对「从旧版升级」的读者有价值                             | 移入文末「修订记录 / 迁移说明」小节，正文只留当前事实          |
| C 合法保留    | 基线在同一文档内已给出（同句或前文）；或「之前/之后」指**系统运行时**时序而非文档版本；或该节主题就是版本差异（修订记录、迁移指南、对比章节） | 保留，并在汇报中注明基线在哪                        |

词表命中 ≠ 违规：`在 tick 之前`、`取消订阅`、`命中后不再判定` 都是合法用法；本规范自身反引号内的引例同理不计入命中。
词表未命中 ≠ 安全：先看结构，再看用词。

**第三步：修复写法（按优先级）**

1. **绝对化** —— 删掉对比，只陈述当前事实及其成立理由
   `文件名不再使用 X` → `文件名使用随机 UUID（<uuid>.json，与 VehicleData.uuid 无关）`
2. **就地补基线** —— 把被对比项写进同一句，让对比自足
   `掩体仍然生效` → `管理员关闭地形破坏后，掩体削弱仍按穿透折减生效`
3. **搬家** —— 对比信息只在「版本演进」语境下有意义时，移入文末修订记录

**检查范围**：扫描全文触发词（成本低），不止本次改动的段落——自包含声明、简介、章节编号经常在离改动很远处失效。

**禁止**：

- 只删触发词不管语义（`不再使用 X` → `使用 X`，而现状其实是 Y）
- 把 A 类"保留信息"改写成 B 类（把 diff 抄进正文）
- 顺手扩写无关章节——除修复点外不动内容
- 汇报「已检查无问题」却不给证据

**完成条件（汇报格式）**

1. 命中清单：`文件:行` + 原句
2. 逐条处理：改写后的句子 / 已移入修订记录 / 判 C 类并说明基线出处
3. C 类判定有争议时，一律改写为自足表述

**一句话版**（塞进简短 prompt 时用）：文档不得以自身的旧版本为前提。写完通读一遍：凡出现「不再/仍然/改为/之前/取消/（原…）」等对照词，判断该对照的基线是否在同一份文档里给出；没有就给基线、改成绝对陈述、或移到文末修订记录。

## 反模式（本项目特有）

- **严禁混用 JME 和 JOML 数学**：物理用 `com.jme3.math.*`，渲染用 `org.joml.*`。始终通过 `SparkMathKt.*` 转换。
- **严禁在** **`partNet`** **之外使用** **`synchronized`**：仅 VehicleCore.java 中存在 2 处 `synchronized` 块（保护 Guava `MutableNetwork`）。其他所有共享状态使用 `ConcurrentHashMap`、`ConcurrentLinkedQueue`、`volatile` 或 `CopyOnWriteArraySet`。
- **严禁在主线程直接操作物理体**：使用 `getPhysicsLevel().submitImmediateTask(PPhase.ALL/PRE, ...)`。
- **严禁忽略** **`DestroyableRigidObject.updateLock`**：服务端→同步数据期间置为 `true`，防止反馈循环。
- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **子系统必须配套 static\_attr**：每种子系统类型都需要对应的 JSON 加载用静态属性类。
- **严禁共通代码引用客户端类**：`net.minecraft.client.*`、`com.mojang.blaze3d.*` 以及本项目 `client/` 包下的类，都不得出现在共通类（`common/`、`network/`、`external/`、`util/` 等）的方法描述符、字段描述符、`new`/`checkcast` 目标里。专用服务器加载共通类时，类校验会解析描述符里的类型；一旦命中就抛 `Attempted to load class ... for invalid dist DEDICATED_SERVER` 并中止启动，与那段代码是否真的执行过无关——形如 `Minecraft.setScreen(Screen)` 的形参类型、或 `AttachmentType.factory` 里把 `LocalPlayer` 传给 `LivingEntity` 形参，都会命中。客户端行为要么放在 `client/` 包的处理器里由共通类转发（`ClientResearchHandler`），要么由客户端入口注入钩子（`PdaItem.setScreenOpener`）。

## 独特风格

- **ConcurrentLinkedQueue 累加器模式**：物理线程入队伤害/冲击/完整性变更；主线程在 `settleAccumulatedDamage()`（零件在 `postTick`、投射物在 `preTick`）与连接点的 `handleAccumulated*()` 中清空。
- **Volatile 快照模式**：`CollisionEffectManager.latestWheelSnapshot` — 物理线程写入，主线程读取，仅取最新值。
- **SoA 投射物数组**：`ProjectileManager` 使用原始类型数组（`posX[]`、`velX[]` 等），swap-remove O(1) 删除。
- **双数学转换文件**：9 个文件同时导入 JME 和 JOML（MMMath, PosRot, VehicleAnimatable 等）。
- **信号回调重构**：直送模式代替绕圈回调，支持多跳传播。
- **对象管理器模式**：`ObjectManager` 按维度注册 VehicleCore，支持 `getAllObjects()` / `getVehicleByUUID()`。

## 补充说明

- **复合构建**：`settings.gradle` 条件性 include `../Spark-Core`、`../BallisticsFramework` 两个本地源码项目。本地目录不存在时回退到 Maven jar。AUI 由 Maven 坐标 `com.sighs:ApricityUI-neoforge-1.21.1` 提供（`maven.sighs.cc`）。
- **无自动化测试**：无 `src/test/` 目录，也没有游戏测试函数——`runGameTestServer` 因此会走到专用服务器启动的最后一刻，再由 `GameTestServer.create` 抛 `IllegalArgumentException: No test functions were given!` 中止。可用的运行期验证通道是 `runServer`（启动到 `Done`，可验证注册、内容包解码、配方加载）与 `runClient`（进世界验证玩法）。
- **`runServer` 只加载 `mods/`**：客户端专属 jar 放在 `mods-client/`，只有 `runClient` 加载（分工见「命令」一节）。专用服务器仍会连带载入不分端的 `implementation` 声明依赖（JEI、AUI、加速渲染、KubeJS 等），这些是编译期就需要的东西，不影响启动。
- **CI 使用 JDK 17**，构建目标 Java 21 字节码。
- **21 个 TODO 在 MachineMax.java** — 包含蓝图存储、网络包重构、炮塔控制、机甲外骨骼、通用分层作动器控制等完整路线图。
- **已知崩溃（已探明）**：多线程物理 + 关节 = 崩溃。**根因**：关节连接的两个刚体均为运动学模式（Bullet 不支持两运动学体间的 Joint 约束）。临时方案：禁止将刚体设为运动学以停止其外力影响，使用speedFactor。
- **耦合扭矩禁用**：`MotorSubsystem.coupleTorque = 0`，因轮子停止时振荡。
- **CI/CD**：GitHub Actions（`build.yml` — push/PR 自动构建；`pages.yml` — 文档发布到 GitHub Pages）。
- **打包说明**：部分内容包（如 sdkfz/）属于外部项目示例，打包时可能需要分离。

## 参考文件

- `docs/glossary.md` — 自动生成的术语表（287 行，中英对照）
- `docs/wiki/4-物理与战斗机制/4.7-投射物.md` — 投射物系统现行说明（阶段一/二已实现）
- `docs/wiki/3-子系统详解/3.8-武器系统.md` — 武器子系统与弹药供给/装填
- `docs/观瞄系统-CameraSubsystem详细设计.md` — 摄像机子系统设计文档
- `docs/信号回调机制重构设计.md` — 信号系统重构设计
- `docs/AUI载具控制面板实现计划.md` — AUI 控制面板实现计划
- `docs/AUI性能边界.md` — AUI 性能分析
- `docs/机娘模组企划.md` — 机娘模组企划
- `docs/wiki/` — MkDocs Wiki 文档站点（快速上手、载具系统完全指南、子系统详解等）

