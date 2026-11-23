# 动画系统-Part动画资源绑定实施计划

> **状态**：实施完成，实机验收进行中。§2–§4 的资源键、绑定生命周期、Variant 配置与循环片段归属已落地；离线编译、单元测试、`checkSchemas` 与内容引用检查（含动画集/控制器集的目录核对）通过，§7 的客户端验收由用户在开发环境执行。绑定重建必须遵守的顺序约束见 §6 末段，循环片段按引用关系归控制器或客户端常驻播放，见 §4.2。
> **日期**：2026-11-24
> **范围**：Spark-Core 动画资源选择与 Machine-Max Part 接入。内容交付和实机验收仅覆盖桑塔纳（Santana）。

## 1. 基线与目标

桑塔纳车体是动画体，车门是通过高级连接器约束的刚体。玩家点击交互区，车体收到一次动画事件；服务端播放动画，`JointDriverSubsystem` 在物理步取动画后的 locator 位姿，写入关节电机目标。刚体求解结果决定门体和碰撞体的位置，通过现有位姿同步交给客户端。原型验证了这条链路，包括右门轴向；本期不重做关节坐标变换或门体模型。

原型使用同名资源：车体模型、动画集和控制器集都用 `machine_max:santana.geo`。动手前几个入口的资源选择并不统一（下表是本文的起点，不是现状）：

| 入口 | 统一前的行为 |
| --- | --- |
| `AbstractPart#getDefaultModelIndex()` | `part` 类型 + `VariantAttr.model`。 |
| `AbstractPart#playAnim`、`#hasAnim`、客户端循环自动播放 | `part` 类型 + `VariantAttr.animations`；字段缺省用 `machine_max:empty`。 |
| `AnimController.originAnimations`、`animInstance(holder, name)` | 分别按当前模型索引查动画；本地控制器的 `PlayAnimAction` 经后者播放。 |
| `AnimController.originStateMachines` | 按当前模型的 `ResourceLocation` 查；加载器读到的 `part`、`entity` 等类型不参与查找。 |
| `AnimController.stateMachines` | 首次访问时构建运行时状态机，此后不检查资源绑定。 |

交付目标：**模型决定骨骼与 locator，动画集提供片段，控制器集决定何时播放。** 三者默认同名，允许 Variant 独立指定。内容作者按车型设计动画、控制器、事件名和电机参数。本期不制作跨车型通用开门控制器，不给其他车型补门或座舱动画，不增加动画事件网络包、全局事件路由、控制状态持久化或新的关节驱动类型。

## 2. Spark-Core：资源键与加载

### 2.1 动画体接口

给 `IAnimatable` 增加两个具备默认实现的属性。实现时使用中文注释与 KDoc。

```kotlin
data class AnimResourceKey(val type: String, val location: ResourceLocation)

// 以下两个属性声明在 IAnimatable 内。
val animationSetKey: AnimResourceKey?
    get() = modelController.model?.index?.let { AnimResourceKey(it.type, it.location) }

val controllerSetKey: AnimResourceKey?
    get() = modelController.model?.index?.let { AnimResourceKey(it.type, it.location) }
```

- 默认从 `modelController.model` 的**当前**索引派生。`defaultModelIndex` 负责初始模型选择，不决定运行时换模后的资源。无当前模型时默认返回 `null`；空键表示没有绑定。
- 两个键在各自的资源表解析。控制器键不从动画键推导。键不含 `fullDetailDistance`，LOD 不是资源身份的一部分。
- Java 实现可覆写 `getAnimationSetKey()` 与 `getControllerSetKey()`。只提供模型索引的动画体可使用默认实现；SubPart、交互区及子系统无需实现这两个接口。显式传 `AnimIndex` 的调用按传入索引查找，不受默认动画集选择影响。
- 资源定义可由多个动画体共享；运行时状态、活动动画和事件消费归每个 `AnimController` 实例。`IAnimatable` 只暴露资源键，不暴露可变注册表；Variant 不持有运行时控制器。

### 2.2 目录和注册表

| 模块 | 包内路径 | 键示例 |
| --- | --- | --- |
| 模型 | `<namespace>/models/<type>/…/<name>.json` | `part + machine_max:santana.geo` |
| 动画集 | `<namespace>/animations/<type>/<set_name>/<file>.json` | `part + machine_max:santana.geo` |
| 控制器集 | `<namespace>/controllers/<type>/<set_name>/<file>.json` | `part + machine_max:santana.geo` |

`<set_name>` 是集合 ID 的路径段，`<file>` 是集合成员文件名，不是 ID。一个集合可以含多个文件；文件内的动画名或控制器名才是成员键。同集合的多个文件按现有内容包覆盖顺序合并。不增加单数 `controller/` 别名。加载器按照首两段目录取 `<type>` 和 `<set_name>`，不把额外嵌套目录解释成其他集合 ID。

| 文件或类型 | 工作项 |
| --- | --- |
| `AnimStateModule` / `OAnimStateMachineSet` | 控制器表用 `AnimResourceKey`，加载时纳入类型。用 `PackCodec` 和内容包诊断报告无效路径或 JSON，沿用服务端来源的加载与分发机制。 |
| `OAnimationSet` | 提供按新资源键查找的入口。在查找边界转成对应类型和位置的标准 `ModelIndex`，使用现有存储；本期不调整 `ModelIndex`、`AnimIndex` 的序列化。 |
| `AnimController` / `AnimInstanceBuilder` | `originAnimations` 和 `originStateMachines` 按动画体的键查找。`animInstance(holder, name)` 从 `originAnimations` 解析片段后直接实例化，不重复按模型查找。 |
| `PlayAnimAction` / `MultiAnimStateMachine` | 单目标控制器经上述按名入口取动画；多目标控制器保留「目标动画集 → 素体 → 内置」查找顺序。本期不把 ArmsCore 的程序控制器迁移到 JSON。 |

Blockbench 导出的 `animation_controllers`、`states`、`animations`、`transitions` 结构不变。`initial_state` 缺省取 `states` 声明的第一个状态；`event.<事件名>` 走 Spark-Core 的事件转移入口，普通转移表达式走 MoLang 编译与求值。本期不添加事件条件和 MoLang 条件拼接语法。

## 3. Spark-Core：绑定生命周期

由 `AnimController` 持有当前绑定和根状态机，在所属逻辑端的游戏线程管理。`tick()` 推进状态机与 `triggerEvent()` 投递事件前，共用一次绑定检查；已可用的资源可以当场接住第一次点击，无需先等一个 tick。

1. 读取实际模型与两个资源键，解析已发布的定义。比较模型索引的类型和位置、两个键的值，以及两个已解析定义的引用。空资源用 `null` 或稳定的空值表达，不拿 `getOrEmpty()` 每次临时构造的空集作变化判据。仅更换贴图或 LOD 值不重启状态机。
2. 首次可用或绑定变化时，先停止旧根状态机，退出它所属的活动片段，再按当前控制器集启动新根状态机。使用现有的状态退出及动画回收路径，不调用 `stopAllAnimation()` 清掉手动动作或外部控制的层。绑定相同时只推进实例，不重建图；模型或控制器集不可用时不启动根状态机。
3. 加载阶段查找失败不能锁死实例，同一个键加载出新定义也要重新绑定。核对 `SparkPackLoader` 的读取和完成相位：若重载可与运行期重叠，先暂存并合并，完成后发布集合；不能让运行时读取半成品，也不能原地改写已发布集合导致引用变化不可见。相同逻辑端使用同一批已发布资源，不以客户端读包时序驱动服务端重绑。
4. 重绑从配置的初态启动，不保留旧控制节点，也不回放缺资源时的事件。物理线程只消费动画层并混合姿态，不构建、替换或推进 JSON 状态机。跨重载保留门开闭状态不在本期范围。

## 4. Machine-Max：Variant 配置与播放入口

### 4.1 配置规则

动画绑定放在 [VariantAttr](../../../src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/attr/VariantAttr.java)。同一 Part 类型的不同 Variant 可以共用模型，各自指定动画集和控制器集。单变体简写与多变体映射均适用。

| 属性 | 类型 | 缺省与约束 |
| --- | --- | --- |
| `model` | 资源 ID，必填 | 指定 `part` 模型。 |
| `animations` | 资源 ID，可选 | 缺省取模型 ID。显式 `machine_max:empty` 表示无动画集，这是现有内容包使用的空动画占位值，不报告缺失。 |
| `controllers` | 资源 ID，可选 | 缺省取模型 ID。显式值选中一整套控制器，不是文件名、单个控制器名或事件名。 |

`AbstractPart` 将 Variant 的显式值映射到 `part` 类型的资源键，缺省委托 Spark-Core 的模型派生逻辑。Codec 保留缺省与显式配置的区别。同步更新内置 Variant schema、内容包随附 schema 和作者说明；不在 Part 顶层、SubPart 或 connector 重复引入相同字段。

隐式同名绑定找不到集合是正常情况。显式指定却找不到集合是配置错误：沿用 `PartModule` / `PartDefinitions` 的晚校验，报告 Part、Variant、字段、资源类型和 ID，按无效候选处理，不静默回退，也不在 tick 中反复报错。实现时确保动画和控制器资源发布早于 Part 晚校验；若模块注册顺序不能保证，用已有的完成事件安排校验。内容引用检查采用相同的目录口径，不固定桑塔纳的片段数、片段名称或控制器集合成员。

实施阶段已核对现有 Variant：省略 `animations` 的配置都没有与模型同名的动画集，缺省同名绑定因此不会让静态部件开始播放；需要保持静态的部件仍显式写 `machine_max:empty`。这一步只处理缺省语义，不给其他车型配置门控制器。

### 4.2 播放所有权

- `AbstractPart#playAnim`、`#hasAnim`、循环自动播放统一读取 `AnimController` 选定的动画集。`playAnim` 保留客户端动作层和同名重触发语义，供后座等显式播放者使用；它不是服务端车门交互入口。
- 循环片段的归属按片段名划分：被控制器集任一状态的 `animations` 引用、且解析不成子控制器的片段归控制器，进入状态时播放、退出状态时结束；其余 `loop: true` 片段由客户端持续播放，每 tick 按名补齐缺失的一份，与其他片段互不打断。归属只看名字，不看当前有没有控制器在播，也不看 `isPlayingAnim`。
- 桑塔纳交付时保持模型、动画集和控制器集的 `santana.geo` 同名配置，保留已验证的门动作与事件。`parallel0` 驱动方向盘和踏板，不写进任何控制器状态——它未被控制器引用，因此由客户端常驻播放。该片段不写车门铰链骨骼，舱内表现与门的开闭互不影响。无需车型白名单。

## 5. 交互、物理与同步边界

1. `InteractBox` 在服务端通过已有的 MoLang 门控校验点击。`signal_targets` 中指向 `local` 的频道投递给所属 Part 的动画控制器，匹配 `event.<频道>`；指向 `global` 的频道交给装配体广播给全车部件，由各部件自己的控制器决定是否响应；随后按顺序处理子系统目标。座椅的 `CONSUME` 只结束子系统遍历，不影响已经投递的动画事件。桑塔纳车门区投 `local`，引擎盖与后备箱盖区投 `global`；上车使用独立座位交互区。
2. `AbstractPart` 推进状态机。物理步先推进与混合动画，再由 `VehicleCore` 调用 `JointDriverSubsystem.onPostAnimationPhysicsTick()`。`JointDriver` 只看绑定的高级连接器、动画 locator 和已有电机参数，不读取控制器键、动画名或交互区。
3. 动画给有限力伺服提供目标；碰撞和限位决定实际角度，不直接把动画位姿写到刚体。连接器另一端可以是同 Part 的 SubPart，也可以是独立 Part，两者使用相同绑定规则。一个关节由一个驱动控制是内容包约定，本期不做电机仲裁。
4. 自动释放依动画模式：`ONCE` 到片段时长后松手，不等门到目标；保持末帧或循环只要还有有效动画贡献就继续给目标，退出到无贡献才松手。释放恢复接管前的电机、伺服、目标及驱动写过的设置，弹簧、阻尼、限位保持原配置。桑塔纳可用保持末帧表达位置保持；其他车型自行选择动作模式、时长和力值。
5. 服务端刚体解算决定门与碰撞的权威位置，客户端使用现有刚体位姿同步显示结果；不发客户端事件包来复现开门物理。双端资源选择遵守相同规则，客户端渲染 LOD 不参与服务端物理采样。本期不调整全局物理线程模型、协议或存档字段。

## 6. 实施顺序

| 阶段 | 修改范围 | 完成标志 |
| --- | --- | --- |
| P1：资源解析 | Spark-Core 的 `IAnimatable`、资源键、控制器加载器和按名播放入口 | 当前模型可提供默认键，显式动画集不被单目标播放路径绕过。同名不同类型不串集。 |
| P2：运行时绑定 | Spark-Core 的 `AnimController`、资源发布接线 | 首次 tick 或事件按已发布定义绑定；资源变化重建控制器，退出只清理控制器拥有的动画。 |
| P3：部件接入 | Machine-Max 的 `VariantAttr`、`AbstractPart`、晚校验和 Variant schema | 三类资源按约定独立解析；完成缺省 `animations` 的影响核对；循环片段按引用关系分归控制器与客户端常驻播放。 |
| P4：桑塔纳落地 | `Machine-Max_Civilian` 内的桑塔纳与作者说明 | 只接入桑塔纳，保留已验证的门配置；`parallel0` 由客户端常驻播放，不由控制器播放。交给用户启动客户端验收。 |

四个阶段均已落地。Spark-Core 侧提供 `AnimResourceKey`、`IAnimatable` 的两个默认资源键、加载器的暂存发布，以及 `AnimController` 在 `tick()` 与事件投递前的绑定检查，并按控制器集算出它引用的片段名（`controllerAnimations`）。Machine-Max 侧 `VariantAttr` 提供 `animations` / `controllers` 与显式绑定校验，`AbstractPart` 按变体算定两个键并在绑定变化时重建状态机；`autoPlayLoopAnimations()` 跳过控制器引用的片段，只常驻播放其余 `loop: true` 片段。桑塔纳保持 `santana.geo` 同名绑定，`parallel0` 不进入任何控制器状态，由客户端常驻播放。

P2 的重建遵守一条顺序约束：先停旧根状态机，再以空状态机表发布新绑定，最后构建并启动新的根状态机，构建完成后补上状态机表。根状态机的 `start()` 会同步进入初态并执行入口动作，入口动作播放动画时会回读本控制器的动画集；若那时对外可见的绑定仍是旧值，这次回读会判定需要重建并再启动一套根状态机，递归没有出口（表现为 `StackOverflowError`）。以空表先行发布后，构建期间的任何读取都命中本次绑定。任何在初态播放动画的控制器都走这条路径（桑塔纳的六台门/盖控制器在初态播放 `*_close`），新增控制器同样按此约束检查。

Spark-Core 与 Machine-Max 使用同一组合版本交付。实现阶段可以用离线编译与现有 schema 检查确认接口接通；**不增加 JUnit、GameTest 或验收夹具**。仓库已有的桑塔纳资源测试仅作既有解码检查，不扩展，也不用它代替实机验收。

## 7. 桑塔纳客户端手动验收

由用户在 Machine-Max 开发环境执行 `.\gradlew --offline runClient`（或使用等效 IDE 配置），进入单人测试世界，放置现有桑塔纳整车。单人世界同时覆盖客户端呈现和集成服务端物理；不要求专用服务端、第二车型或另造测试内容包。

| 步骤 | 操作 | 通过标准 |
| --- | --- | --- |
| S1：同名绑定与交互 | 用交付配置放入一台新车，依次操作四扇侧门，含左、右前门各一次开关；再试机盖及尾门已有交互。 | 点击只影响对应机构，左右门沿各自铰链向外打开，侧门点击不会自动上车。门碰撞随刚体改变，不是只有渲染模型转动。 |
| S2：碰撞与保持 | 把障碍放在一扇侧门的扫掠路径上，触发开门，等片段到末帧后清除障碍。 | 有障碍时，门停在实际可达角度，不穿过障碍。当前侧门开门片段设置了保持末帧，清障后可以继续趋近目标。 |
| S3：座舱表现 | 经独立座位区上车，从能观察座舱的视角转向并操作踏板。 | 方向盘和踏板有 `parallel0` 动画反馈，门的开闭状态不受其影响；独立座位交互可用。 |

可**选做**同一台桑塔纳的资源绑定检查：省略 `animations` 和 `controllers`，预期两者都按模型同名查找；测显式绑定时，把动画集目录临时改名为 `animations/part/santana_motion/`，控制器集目录临时改名为 `controllers/part/santana_controls/`，在车体 Variant 分别填 `animations: machine_max:santana_motion` 和 `controllers: machine_max:santana_controls`，保持 `model: machine_max:santana.geo`。每次改配置后重启客户端、放置新车，左右门应正常动作。若需确认 `ONCE` 松手，可以仅将一侧门的开门片段设为 `loop: false`：等待超过片段时长再移走障碍，这次点击的伺服不应继续拉门去原目标。验证完恢复交付配置，不留第二套车或验收资源。

实机反馈只需记录操作部位、是否受阻、动作阶段和症状。不触发时，先对照内容包诊断里的 Variant 资源键和交互频道；确认已进入动画状态但刚体不动时，再核对 locator、关节和轴限位。本期不恢复原型调试插桩。
