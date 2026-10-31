# Machine-Max — 智能体指南

**生成日期:** 2026-06-08 · **分支:** 1.21.1 · **版本:** 1.0.3-beta.6

## 概览

基于 NeoForge 1.21.1 的 Minecraft 载具模组。Kotlin + Java 混合，Gradle 8.9（Groovy DSL），JDK 21。
采用数据驱动的部件化载具组装、实时物理模拟（Bullet 物理引擎，通过 Spark-Core 集成）、22 种子系统类型、内容包扩展机制。

## 依赖项目

| 项目                                                                       | 版本            | 用途                   |
| ------------------------------------------------------------------------ | ------------- | -------------------- |
| [Spark-Core](https://github.com/SolarMoonQAQ/Spark-Core)                 | 1.0.1036      | Bullet 物理引擎封装、实体注册框架 |
| [BallisticsFramework](https://github.com/Sweetzonzi/BallisticsFramework) | 1.0.0.alpha.14 | 终端/外部弹道计算 API        |
| [ApricityUI (AUI)](https://github.com/Sweetzonzi/AUI)                    | 1.2.8         | 载具控制面板 Web UI 框架，经 `jarJar` 内嵌进发布 jar |

构建时也依赖 JEI、Jade、KotlinForForge、AzureLib、GeckoLib、Create（兼容层）。

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
│                                    # HudStyle, ModelHudStyle, AuiHudStyle, HudElement,
│                                    # HudLayout, HudAnchor, HudView, IHudHost,
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
│   ├── js/                          # JS 桥接（早期 Rhino 实现，当前整体注释停用）
│   │   └── hook/                    # AxisHook, EventToJS, KeyHooks
│   ├── style/                       # 样式/色彩系统（ColorPalette, StyleProvider）
│   ├── MMDynamicRes.java            # Spark-Core 资源加载
│   └── CommentRemover.java          # 注释移除工具
├── datagen/                         # 数据生成器
│   ├── MMGenerator.kt               # 入口（Kotlin）
│   ├── MMLanguageRegistry.java      # 语言翻译器总线
│   └── AGENTS.md                    # 翻译键智能体指南
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
│   ├── resource/modules/            # 14 个内容包模块（数据驱动加载）
│   │   ├── PartModule.kt           # 部件模块
│   │   ├── SubsystemModule.kt      # 子系统模块
│   │   ├── ConnectorModule.kt      # 连接器模块
│   │   ├── AssemblyModule.kt       # 装配体模块
│   │   ├── BlueprintModule.kt      # 蓝图模块
│   │   ├── MaterialModule.kt       # 材质模块
│   │   ├── ColorModule.kt          # 颜色模块
│   │   ├── ControlGroupModule.kt   # 控制组模块
│   │   ├── HudModule.kt            # HUD 样式模块
│   │   ├── HudLayoutModule.kt      # HUD 布局模块
│   │   ├── DocumentModule.kt       # AUI 文档资源模块
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
| 新增内容包数据    | `src/main/resources/spark_modules/`            | `Machine-Max_*` 九个第一方包，见「内容包分层」 |
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
./gradlew syncSchemas        # 从 builtin 同步内容包 schema 副本（见「内容包 schema 同步」）
./gradlew checkSchemas       # 校验内容包 schema 副本（check 依赖它）
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
  GeckoLib 已由 Spark-Core 的 Maven 依赖 `software.bernie.geckolib:geckolib-neoforge-<mc>:<version>` 提供（版本见 Spark-Core 的 `gradle.properties`），经复合构建出现在本项目的运行期类路径上，不需要 jar。Spark 的模块来自 Spark-Core：那份构建脚本用 `implementation(files(fileTree("mods")))` 声明 `mods/spark-1.10.124-neoforge.jar`，它作为 root component 进入 Spark-Core 的 `runtimeElements`，于是出现在每个消费方的 `runtimeClasspath` 上（本项目 `gradlew runClient` 的 Mod List 里就有 `spark 1.10.124`）；本地再放一份同名模块就会撞包。两份 jar 都放在 `mods-disabled/`（`mods-disabled/` 不参与任何 run）。
- 顺序要求：两个私有配置必须在 `build.gradle` 里先声明（`repositories.gradle` 由 `apply from` 在本文件之后执行，那里只填内容）。
- 核对办法：`run/logs/latest.log` 里 `ModDiscoverer` 打出的 Mod List 是最终生效的 mod 集合；`build/moddev/{client,server,data,gameTestServer}LegacyClasspath.txt` 只是 MC-BOOTSTRAP 模块层的清单（由 `gradlew write<Run>LegacyClasspath` 刷新），其中出现某个 jar 不代表它会被当成 mod 载入。

## 内容包分层

`src/main/resources/spark_modules/` 下是九个第一方内容包。目录名统一 `Machine-Max_` 前缀，包内内容目录统一 `machine_max/`，包 id 是 `machine_max:<小写名>`：

| 层 | 目录 | id | 承担 |
| --- | --- | --- | --- |
| L0 | `Machine-Max_Builtin` | `machine_max:builtin` | 引擎基元、子系统 / 连接器 / 材质 / 音效 / 粒子 / HUD、新人货架，以及货箱、黄金马桶等题材无关的独立成品件 |
| L1 | `Machine-Max_Soviet_Basic_Kit` | `machine_max:soviet_basic_kit` | 苏系共用型谱：2A42 / 2A90 机关炮、9M133 反坦克导弹、PKTM 机枪与配套弹种 |
| L1 | `Machine-Max_German_Basic_Kit` | `machine_max:german_basic_kit` | 德系共用型谱：KwK39 50mm 火炮、MG34 机枪与配套弹种 |
| L1 | `Machine-Max_Civilian` | `machine_max:civilian` | 13 款民用载具（集合包，不单独建 kit） |
| L2 | `Machine-Max_2S38`、`_K17`、`_MTLB`、`_Sdkfz234` | `machine_max:2s38` 等 | 单车 / 车族：几何、挂点、接线、装配体、蓝图、模板、配方、HUD |
| — | `Machine-Max_Pack_Template` | `machine_max:template` | 空骨架，供作者复制 |

依赖链是 `车包 → kit → builtin`：载具包只声明最具体的那一册，链上的定义自动带入。跨包引用（车包直接用 builtin 的座椅、某个车包用兄弟车包的加装件）靠「所有包先读完再统一注册」成立，`dependencies` 只决定加载顺序与同 id 覆盖。

内容 id 由 `<namespace>/<文件名>` 决定，与所在包无关，所以搬动文件不改 id，存档、蓝图、研发进度都不受影响。

目录名不带版本号：`SparkPackResourceLoader` 用目录名生成 `spark_modules/<目录名>.zip`，一旦改名，旧 zip 会留下并与新 zip 指向同一个 id，实际生效的那份取决于目录遍历顺序。退役名的副本由 `MMRetiredPackCleanup` 在启动时删除。

## 内容包 schema 同步

内容包的 `docs/<lang>/schemas/` 带部件、子系统、配方的 JSON Schema，供作者在编辑器里补全与校验。**手写的只有 `Machine-Max_Builtin` 那一份**，其余由 `./gradlew syncSchemas` 生成：

| 角色 | 路径 | 内容 |
|------|------|------|
| 源 | `src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/docs/{en_us,zh_cn}/schemas/` | 手写 |
| 副本 | 其余第一方包的同名路径（`Machine-Max_Pack_Template` 的 namespace 是 `namespace`） | 逐语言字节复制 |
| 技能快照 | `skills/blockbench-content-pack/references/schemas/` | 取 builtin 的 `zh_cn`，只含 `.json` |
| 清单 | `skills/blockbench-content-pack/bundle-manifest.json` 的 `schemas` 块 | 文件清单、字节数、`sha256` |

```bash
./gradlew syncSchemas            # 写盘：副本 + 技能快照 + 清单
./gradlew syncSchemas -PdryRun   # 只列出将变更的文件
./gradlew checkSchemas           # 只读校验；check 依赖它，CI 的 build 因此也会跑
```

- **改 schema 只改 builtin**，然后跑 `./gradlew syncSchemas`，把生成的副本与自己的改动放在同一个提交里。副本与 builtin 不一致时 `checkSchemas` 让 `check` 失败，漏同步会在本地构建阶段暴露。
- **写盘统一 LF**：`.gitattributes` 对 `src/main/resources/spark_modules/**/docs/**/schemas/**` 与技能快照声明 `text eol=lf`；比较前把 CRLF 归一化，所以判定结果与 `core.autocrlf` 无关。技能快照的 `sha256` 按 LF 字节计算。
- **只同步 `*.json`**。`schemas/` 下的 `.md`（`How_to_Use_JSON_Schema.md`、`如何使用JSON模板.md`）是各内容包自己的示例文档，按包保留。
- **每个第一方包自带一份副本**：包内 JSON 的 `$schema` 是指向本包 `docs/` 的相对路径（`parts/x.json` 用 `../docs/…`，`subsystems/<组>/x.json` 用 `../../docs/…`），所以只要包内层级不变，迁移文件时不必改写 `$schema`。同步范围写在 `schema-sync.gradle` 顶部的 `syncTargets`。
- **新内容包自带 `docs/<lang>/schemas` 时**，把它登记进 `syncTargets`；漏登记的副本由 `checkSchemas` 打提示，不判失败。

### 随包 Blockbench 插件

`skills/blockbench-content-pack/assets/plugins/machine_max_bb_plugin.js` 是插件仓库
`dist/machine_max_bb_plugin.js` 的随包副本。`bundle-manifest.json` 的 `plugin` 块记录来源
revision / version / bytes / sha256，`SKILL.md` 的“版本”与“全部 N 个 JSON”文案需与清单一致。

- **该资产不由 `syncSchemas` 生成**。插件发布后，在插件仓库运行
  `node scripts/sync-skill-bundle.js --apply`（默认写本仓库的
  `skills/blockbench-content-pack`），或对照插件 `dist` 更新同样字段。
- **提交固定只含三个文件**：`assets/plugins/machine_max_bb_plugin.js`、
  `bundle-manifest.json`、`SKILL.md`；提交信息沿用
  `chore(skill): 随包 Blockbench 插件更新至 <version>`。
- **与 schema 快照是两条独立链路**：插件资产跟随插件发布，`references/schemas/` 跟随
  `./gradlew syncSchemas`；不要用一次提交顺手覆盖另一边来掩盖漏跑。
- 上游存在他人未提交改动时先与插件维护者确认，不要把无关改动卷进该提交。

## 技能包（`skills/`）

`skills/` 下的四个技能同时服务两类使用者——内容包作者与项目开发本身：`blockbench-content-pack`（模型适配与导出校验）、`machine-max-aui-hud`（AUI HUD 与 MoLang）、`machine-max-powertrain-calibration`（动力链标定与性能回归）、`spark-profiler`（sparkprofile 解析与热点定位）。每个技能一个目录，入口是该目录的 `SKILL.md`，frontmatter 的 `name` 必须与目录名一致；脚本、引用文档都放在同一目录下。

- **唯一来源是 `skills/<name>/`**，随仓库入库。仓库内外都不放第二份副本——副本必然在下一次改进后漂移。
- **注册是本地目录联接，不入库**。DSH 扫描的项目技能根是 `<项目根>/.dsh/skills` 与 `<项目根>/.agents/skills`，本仓库用后者，一条联接把整个 `skills/` 挂上去：

  ```powershell
  New-Item -ItemType Directory .agents -Force
  New-Item -ItemType Junction -Path .agents\skills -Target .\skills
  ```

  ```bash
  # 等效的 cmd 写法（Git Bash 里用双斜杠转义）
  cmd //c "mkdir .agents 2>nul & mklink /J .agents\skills .\skills"
  ```

  目录联接不需要管理员权限或开发者模式，`.gitignore` 忽略 `/.agents/`。克隆之后重建这一条即可；`git clean -xdf` 会把它一并删掉，删掉后技能只是暂时不可见，内容不受影响。
- **不提交链接**：目录联接是机器本地的重解析点，git 存不成链接，提交 `.agents/skills` 只会把 `skills/` 的每个文件再复制进索引。改用文件符号链接能入库，但要求 `core.symlinks=true` 与符号链接权限，未开开发者模式的 Windows 会把它检出成普通文本文件，整棵树静默失效。内容已经全部在 `skills/` 里，需要借仓库传递的只有这一步注册动作，因此写在这里。
- **新增技能**：建 `skills/<name>/SKILL.md` 并写好 frontmatter，联接会自动列出它，不需要改其它配置。
- **别的 agent 目录不是来源**：`~/.codex/skills`、`~/.workbuddy/skills` 之类的个人链接只服务那些 agent。DSH 侧能否看到它们取决于本机 `@michengai/dsh-skills-manager` 的来源开关，不是本仓库的注册方式。
- 核对：`ls .agents/skills` 列出的目录应与 `skills/` 一致，且 `git check-ignore -v .agents/skills` 有命中。

## 约定

- **Kotlin 声明，Java 逻辑**：注册表文件（MM\*.kt）、数据生成器、资源模块用 Kotlin；载具核心、物理、网络、渲染用 Java。
- **ObjectRegister 模式**：单一 `MachineMax.REGISTER` 字段通过 Spark-Core 处理所有 NeoForge 注册。
- **线程标注**：方法 Javadoc 标注调用线程 — `主线程`（20tps）vs `物理线程`（Bullet 物理步进）。
- **内容包一切**：部件、子系统、连接器、配方、蓝图全在 JSON 中定义，位于 `spark_modules/` 下。
- **双数学库**：物理使用 JME（`com.jme3.math.*`），渲染使用 JOML（`org.joml.*`）。通过 `SparkMathKt.*` 转换。
- **控制绑定管线**：RawInputHandler → KeyBinding → 网络载荷 → 服务端信号系统。
- **动画体在公共部件**：`AbstractPart<T> implements IAnimatable<AbstractPart<?>>`（共享 `ModelPose`、`local.*` Molang、涂装）；`SubPart` 只负责刚体与骨骼子树渲染，`MMPartEntity` 仅作渲染入口/原版交互点。
- **信号寻址**：路由用 `ISignalReceiver.getSignalAddress()`，保留地址 `"local"` = Part、`"global"` = 装配体；`getName()` 仅身份/日志。

## 文档编辑规范

### 自包含性检查（编辑任何文档后、报告完成前必做）

**适用范围**：`docs/`、`docs/wiki/`、各 `AGENTS.md`、`src/main/resources/spark_modules/*/docs/`，以及代码注释与 Javadoc。
`docs/plan/已实现/武器系统-爆炸系统详细设计.md` 首部的「**本文自包含**」声明只覆盖了「不要求先读别的文档」；本节补齐另一半——**也不要求读过本文的旧版本**。

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
- **没有消费方的抽象不进入代码**：接口、record/状态类与回退分支的准入见「设计节制（反过度包装）」。

## 设计节制（反过度包装）

代码生成模型倾向于预建抽象、包装中间对象、为内部不变量加防御分支。本项目对这类负债的准入如下，代码与 `docs/plan/` 设计稿同样适用。

### 准入规则

1. **一个实现不配接口。** 接口或抽象类至少要有两个真实实现，或两个调用方对同一操作有不同实现需求。只有一个实现时直接用类；"以后可能替换"不构成理由。
2. **重复两次可以忍，第三次再抽。** 两处相似先保留；第三处出现再提取共享类型或方法。单次调用不包装成"共享核心"。
3. **中间对象只在跨边界冻结数据时出现。** 记录/状态类的字段若只是从已有对象搬一遍，删掉该类型，把方法放回数据所有者。例如可拾取判定属于 `SubPart.findPickableHitBox`，不另建 `(hitBox, active, auxiliary)` 三字段状态。
4. **静态工具类只放无领域所有者的纯函数。** 子形状索引→HitBox、交互区映射属于 `SubPart` / `InteractBoxes`；数学、编码、字符串处理才进 `util`。只有 static 方法、参数全是领域对象的类，拆回领域对象。
5. **事件本身就是投递单元。** 跨线程队列直接放事件（如 `SubPartDamageEvent.Pre`），需要的新标记加在事件上；不套命中 record。确实需要冻结跨 tick 数据时才复制，并在复制处注释冻结理由。
6. **防御检查留给信任边界。** 构造器与框架已保证的非空、已校验的索引、同线程已同步的数据，不在每层重复 `requireNonNull`、越界回退或空集合兜底。需要防御的是：JSON/Codec、网络包、native 返回值、跨线程发布、外部 mod 输入，以及物理/坐标数值的有限性。
7. **回退只保留一条。** 同一查询不要叠"取不到扩展→用最强碰撞箱""索引不合法→取第一个元素""无选择→用默认对象"多级兜底；每级写清触发条件与日志，否则删掉。
8. **没有消费方的类型与分支不写。** 计划文档已承诺的公共结果层级按契约保留；新类型、新 `else` 分支必须有调用方或验收项对应。
9. **同一份数据在同一调用链上不出现第三个同义对象。** 输入→事件/结果→消费三段足够；出现第三层时停下来确认中间层是否真被读取。

### 提交前自检

- 我新建的接口有几个实现？只有一个 → 直接用类或方法。
- 新建的 record/状态类，字段是否从已有对象抄来？是 → 删掉，方法放回原对象。
- 这段防御的失败条件，入口或框架是否已排除？是 → 删掉。
- 这个抽象是否有已排期的第二调用方？没有 → 不建。
- 删掉它要改几处？不超过两处且无排期需求 → 删掉比保留便宜。

设计文档提出新类型时同样按本节准入；没有第二实现/消费方的抽象写成"待定"，不列为必然交付。节制不降低信任边界与运行期验收的强度：外部输入、线程安全、物理数值与存档兼容仍须防御，并尽量用测试或 GameTest 固定。

## 独特风格

- **ConcurrentLinkedQueue 累加器模式**：物理线程入队伤害/冲击/完整性变更；主线程在 `settleAccumulatedDamage()`（零件在 `postTick`、投射物在 `preTick`）与连接点的 `handleAccumulated*()` 中清空。
- **Volatile 快照模式**：`CollisionEffectManager.latestWheelSnapshot` — 物理线程写入，主线程读取，仅取最新值。
- **SoA 投射物数组**：`ProjectileManager` 使用原始类型数组（`posX[]`、`velX[]` 等），swap-remove O(1) 删除。
- **双数学转换文件**：9 个文件同时导入 JME 和 JOML（MMMath, PosRot, VehicleAnimatable 等）。
- **信号回调重构**：直送模式代替绕圈回调，支持多跳传播。
- **对象管理器模式**：`ObjectManager` 按维度注册 VehicleCore，支持 `getAllObjects()` / `getVehicleByUUID()`。

## 补充说明

- **复合构建**：`settings.gradle` 条件性 include `../Spark-Core`、`../BallisticsFramework`，本地缺失时回退 Maven jar。CI 里没有这两个兄弟目录，workflow 会把两个仓库检出到 `_deps/` 并用 `ORG_GRADLE_PROJECT_spark_core_dir` / `ORG_GRADLE_PROJECT_ballistics_framework_dir` 指过去，所以 CI 编的也是源码而不是钉住的发布版；分支名写死在 workflow 里，改分支需同步。AUI 走 `maven.sighs.cc` 的 `com.sighs:ApricityUI-neoforge-1.21.1` 并以 `jarJar` 内嵌进发布 jar，`metadata.json` 声明 `[1.2.8,)`；玩家另装独立 AUI 时 FML 直接采用已装那份（只按 mod id 判断、不比较版本），低于 1.2.8 会让内嵌版失效。
- **自动化测试**：`.\gradlew --offline test` 跑 JUnit；`.\gradlew --offline runGameTestServer` 跑 `common/gametest/` 下的 `PartFoundationGameTest`、`PartAssemblyProgressGameTest`（服务端的构造、恢复、类型边界与对象表路由）。客户端画面走 runClient 验收；公共 API 与覆盖边界见 `docs/部件公共基础接入.md`。
- **`runServer` 只加载 `mods/`**：客户端专属 jar 放在 `mods-client/`，只有 `runClient` 加载（分工见「命令」一节）。专用服务器仍会连带载入不分端的 `implementation` 声明依赖（JEI、AUI、加速渲染、KubeJS 等），这些是编译期就需要的东西，不影响启动。
- **CI 使用 JDK 17**，构建目标 Java 21 字节码。
- **21 个 TODO 在 MachineMax.java** — 包含蓝图存储、网络包重构、炮塔控制、机甲外骨骼、通用分层作动器控制等完整路线图。
- **运动学刚体接约束入世后步进 = 物理线程崩溃（已定位）**：已入碰撞世界的刚体被切成运动学模式后若被关节连接，多线程构建的下一次步进即崩在 `btSimulationIslandManagerMt` 的归岛阶段（`EXCEPTION_ACCESS_VIOLATION` in `bulletjme.dll`，线程 `Server PhysicsThread`）。根因是归岛用并查集按定义跳过静态/运动学刚体，其 island tag 为 `-1` 而被无保护地当作下标。**纪律**：不要用运动学模式冻结参与关节的刚体，改用 `VehicleCore.freezePhysicsForTerrainWait()` / `unfreezePhysicsForTerrainWait()` 或平动/转动系数归零；加约束时两端刚体应已在碰撞世界中，移出刚体前先摘掉引用它的约束（Spark-Core 的 `addJoint()` 只打 WARNING、`removePhysicsBody()` 不摘约束）。症状、根因推导、其他触发路径与上游 issue 草稿见 [`docs/plan/计划中/物理-运动学刚体接约束入世后步进崩溃.md`](docs/plan/计划中/物理-运动学刚体接约束入世后步进崩溃.md)。
- **耦合扭矩禁用**：`MotorSubsystem.coupleTorque = 0`，因轮子停止时振荡。
- **CI/CD**：GitHub Actions（`build.yml` — push/PR 自动构建；`pages.yml` — 文档发布到 GitHub Pages）。
- **打包说明**：内容包随 jar 发布——`SparkPackResourceLoader` 启动时把 `src/main/resources/spark_modules/` 下的包目录打包成 `<gamedir>/spark_modules/<目录名>.zip`。九个第一方包整目录带上即可；目录改名的旧 zip 由 `MMRetiredPackCleanup` 清理。

## 参考文件

- `docs/plan/README.md` — 计划与设计文档索引，按已实现／进行中／计划中三态划分
- `docs/部件公共基础接入.md` — 公共部件 API、类型注册、恢复与验证方式
- `docs/武器系统-弹药命名规范.md` — 内容包弹种的推荐命名标准
- `docs/glossary.md` — 自动生成的术语表
- `docs/wiki/` — MkDocs Wiki 站点（快速上手、载具系统指南、子系统详解、物理与战斗机制、内容包制作）
- `docs/AUI性能边界.md` — AUI 能力与性能调研（基于 1.1.6-dev，结论需按当前 1.2.8 复核）
- `src/main/resources/spark_modules/Machine-Max_Builtin/machine_max/docs/zh_cn/Blockbench-MCP.md` — Blockbench MCP 服务器的安装与连接参数（英文版在同级 `en_us/`）


## 公共部件 API

公共机械基础位于 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/part/AbstractPart.java#AbstractPart`；RegularPart 持有普通部件的材料、制造和载具专属规则。VehicleCore 的拓扑只接受 RegularPart，SubPart 与公共子系统通过公共行为契约访问部件。

类型注册、完整／骨架恢复、暂存校验发布、宿主代理及测试方式见 `docs/部件公共基础接入.md`。该路径以仓库根为基准。动画由装配体安排，每个主线程发布相位和物理混合步各执行一次。
