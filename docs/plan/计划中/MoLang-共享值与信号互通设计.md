# MoLang 共享值与信号互通设计

> 状态：源码调查与存储探针已完成；变量桥接修复和拟议接口尚未实施。本文中的 `set`、`has`、`unset` 与履带查询为设计语法，不代表当前内容包可直接调用。

具体修复、文件级批次和扩展探针结果见 [MoLang 变量桥接与共享值修复实施方案](MoLang-变量桥接与共享值修复实施方案.md)。本文定义通用能力边界，实施方案给出绑定、类型、线程、大小写和副作用次数的落地契约。

## 1. 目标与范围

为动画、hitbox 条件、HUD 和使用 MoLang 计算输出值的子系统提供一致的数据查询与共享值通路。消费者可以与状态来源分属不同 Part。能力复用 Spark 的变量绑定、Machine-Max 的 Part／装配体存储和现有信号路由，不为一种消费者建立独立的数据导出体系。

MoLang 的局部状态查询、共享值存取和信号投递是三种操作。查询负责读真实状态；共享值保存已计算的值；投递按既有线路写来源槽并通知接收者。显示名称、图标和版式属于各消费者的资源。

## 2. 现有存储及调用路径

| 入口或容器 | 实际落点与作用 |
| --- | --- |
| `AbstractPart.getVariables()` | 返回该 Part 的 `signalStorage`，不是独立 Map |
| `AbstractPart.getSignalStorage()` | 返回同一个 `signalStorage` |
| `MechMolangContext` 的 `local.get/get_str` | 读取求值宿主 Part 的 `signalStorage` |
| `MechMolangContext` 的 `global.get/get_str` | 读取宿主装配体 `SubsystemController.signalStorage` |
| `SignalChannel` | `ConcurrentHashMap<ISignalSender, Object>`；每个频道按发送者保留输入槽 |
| `ISignalSender.deliverSignal` | 写接收者来源槽；接收者提供存储时写其扁平存储；调用 `onSignalUpdated` |
| Part／装配体总线 | 复用 `ISignalBus` 的订阅与广播，保留原始发送者身份 |
| `AbstractSubsystem.publishFeedback` | 物理线程发布最新样本，主线程 `onTick` 经配置目标发送 |
| HUD 的 `HudPartSnapshot` | 从相同的 local／global 存储复制数值与字符串，发布给 HUD 求值线程 |

因此 Java 对象层面，Part 的动画变量表与 Part 信号值容器确实共用。两级 `signalStorage` 提供按键读取的最近值；`SignalChannel` 额外保留来源身份，两者不是同一种容器。普通接收者可以没有扁平存储，投递仍能写输入槽并通知。

Part 键值表与装配体键值表属于当前逻辑端的对象。`global` 的作用域是宿主装配体，不是整个进程，也不是客户端／服务端之间的一份共享内存。

## 3. 已确认的变量桥接断层

`SparkMolangContext` 构造了 `AnimatableVariableBinding`，但只将构造时的变量复制到父类 `MolangContext` 的独立 `MutableObjectBinding`。`reset` 更新 bridge 的动画体，却没有把这份独立存储切换成 bridge 的实时视图。编译后的 `v.*`／`variable.*` 读写调用 `context.getVariableStorage()`。

这意味着不能从 `AbstractPart.getVariables() == getSignalStorage()` 推断当前所有 MoLang 变量读写已贯通。

### 3.1 存储探针

2026-11-14 使用当前 Spark 已编译类运行探针，动画体由 `IAnimatable` 代理提供一个共享 Map，`putVariable` 在探针中直接写该 Map；不创建世界或 native 物理体。

| 操作 | 实测结果 |
| --- | --- |
| Map 写入 `from_signal = 7`，创建 Spark 上下文，编译读取 `v.from_signal` | 7 |
| Map 更新为 9，调用同一上下文的 `reset` 后读取 | 7 |
| 直接通过该上下文的 `getVariableBridge()` 读取 | 9 |
| 编译执行 `v.from_script = 13` | 返回 13；上下文内部存储为 13，源 Map 中没有该键 |
| 直接通过 bridge 写入 `bridge_write = 21` | 源 Map 为 21 |
| 使用同一动画体创建第二个上下文，读取 `v.from_script` | 0 |

探针在 Machine-Max 的临时构建任务 `molangStorageProbe` 中通过，命令使用 `--offline`，复用 Spark 的 Java／Kotlin classes 与本地依赖。它验证 Spark 基类的编译路径，没有代替真实 Part、hitbox、HUD 或网络生命周期的回归。探针源码位于本次任务的临时工作目录，正式修复需提交可重复的契约测试。

### 3.2 修复目标

沿现有 `AnimatableVariableBinding` 恢复动画体变量表的实时读写，保持 `getVariableStorage` 的公开返回形状与子类自定义绑定能力。不以每 tick 复制变量表代替实时视图，不改变通用 `MolangContext` 可以持有独立变量表的能力。

编译、解释、数字、字符串和嵌套对象访问需要成对核对。HUD 对重复项下标提供的只读覆盖继续由其专用上下文管理，不能强行使 HUD 的 `v.*` 指向座位 Part。`v.*` 归属哪个动画体由 `IAnimatable` 决定；对公共 Part 动画体才是 Part 的共享表。

变量属性的大小写规则与字符串频道键的规则也要明确：修复不能擅自重命名现有信号频道。复杂信号对象可以存在于 Java 容器中，不表示 MoLang 已能将它当成数值或任意可访问对象。

## 4. 最小通用扩展

### 4.1 局部状态查询

在来源 Part 内补按 SubPart 键访问履带的原始状态。拟议接口为 `subpart.has_track(name)`、`subpart.track_durability(name)`、`subpart.track_max_durability(name)`、`subpart.track_broken(name)`。同条履带的成员轮返回相同状态，未安装、断裂与解散有不同语义。

现有 `local.subsystem_durability(name)` 等查询继续服务该 Part 的子系统。局部查询并不隐式遍历其他 Part；跨 Part 共享由以下通用值通路或既有信号线路完成。

### 4.2 两级共享值读写

`local.get`／`global.get` 及字符串读取沿用现有容器。补数值 `local.set(key, value)`／`global.set(key, value)`，写入各自的现有 `signalStorage`，返回写入值。存在判断与清理使用 `has(key)`／`unset(key)`，使合法 0 与缺失可区分。字符串使用独立的 `set_str(key, text)`，在赋值类型和单次求值契约通过验证后开放，不用数值 setter 隐式承接任意 Java 对象。精确的空值、无宿主与返回值规则见实施方案 §5.1。

这些能力放在公共的机件 MoLang 层，供动画、条件和其他以 Part 为宿主的程序求值使用。具体实现不应将装配体硬编码为 VehicleCore；已有 `IPartAssembly.getSubsystemController()` 能提供装配体存储。

**setter 只改值，不发送信号、不触发订阅者、不自动同步网络。** 同一键的平坦存储保持最近一次写入语义，不自动汇总多个来源。需要区分多台发动机等多来源时，使用既有来源槽或作者明确的不同频道，不用翻译名猜身份。

普通 Map 的逐键线程安全不提供多个键的事务，也不规定不同 Part 的求值先后。消费者需要“本步已更新”或“一组值同代”时，应有明确的发布／读取相位或完整样本契约，不能仅凭容器类型推断。

### 4.3 示例：同一读数供不同消费者使用

来源 Part 的常驻程序动画或其他程序入口可使用以下拟议写法：

```text
global.set('status.engine.hp', local.subsystem_durability('engine'));
global.set('status.left_track.hp', subpart.track_durability('left_sprocket'));
```

其他 Part 的动画、hitbox 条件以及其他程序求值器均可读取同一装配体域：

```text
global.get('status.engine.hp')
global.get('status.left_track.hp')
```

示例中的 `engine`、`left_sprocket` 是说明用局部键，内容作者使用真实键。值在来源 Part 的上下文中计算，写入后的其他消费者无需查找那个 Part。键名和发布者由内容包约定。

HUD 已有相同 `global.get` 的快照读取语义，数据发布后可沿现有 `CustomHud.tick` → `HudPartSnapshot` 消费，不需要为此建立 `hud_readouts` 资源字段、独立导出注册表或第二套全车值容器。标题、图标、布局和所选键留在页面资源。

## 5. 程序入口与信号复用

常驻 `parallel` 动画可以发布共享值。它是已有程序入口之一，能够服务客户端动画与 HUD 等消费者。需要逻辑判定或输出信号的程序入口则按其业务相位与运行端执行。

使用 MoLang 计算信号的子系统可复用 `ISignalSender.getTargetNames/getTargets`、`sendSignalToAllTargets`、端口转译及 local／global 总线，表达式只负责产生信号值。无需建立专属的“HUD 来源”协议。

来源在主线程求值时，可按正常输出路径投递。来源在物理线程计算时，复用已有 `publishFeedback` → 主线程 flush 模式；物理步只向已解析实例发送的既有控制命令继续服从原执行器绑定契约。不要在任意骨骼或条件求值中顺便走主线程专属的按名路由。

未来独立于动画的 MoLang 输出子系统可以作为求值入口接入上述能力。是否创建该子系统、其配置字段和执行频率由实际的程序输出需求确定；本轮不预建新的脚本调度框架或数据声明接口。

## 6. 运行端、生命周期和时序

- 服务端 hitbox 条件在服务端求值，需要服务端存在相应状态或发布者。仅由客户端常驻动画写出的值不能驱动服务端判定。
- HUD 在客户端采样。如果发布值完全可由已同步的真实状态推导，可在客户端独立计算；只在服务端产生且客户端无法重建的值，需要明确的同步字段或载荷。
- 同一个表达式在两端执行不表示值天然相同，特别是依赖动画时间、随机数、玩家视角或本端输入的表达式。
- Part 和装配体的共享值复用现有结构变化清理。合并、分裂、拆件或重载时，发布者应按当前归属继续更新；在途物理任务不能把旧装配体引用当作永久目的地。
- 共享值保留最近写入，不携带自动有效期。动态来源失效时用显式存在状态或清理表示；键的来源和重新发布时机需要纳入测试。
- `has` 表示当前容器有键，不表示网络样本已足够新。`unset` 清值也不表示释放执行器的某个输入来源；输入来源的释放沿现有信号协议执行。
- HUD 保留不可变消费快照和重复项的观察者作用域；它复用共享存储的来源与语义，不直接操作物理线程的活动上下文。

## 7. 实施顺序与验证

1. Spark 变量桥接：固定实时读取、写回源 Map、上下文隔离／重绑、变量大小写、编译／解释一致性及 HUD 自定义变量绑定等契约。先明确值类型和子类覆盖边界。
2. Machine-Max 通用读写与履带查询：`local`／`global` 容器作用域、缺失与 0、赋值和清理、同轨成员一致、断裂与解散。验证赋值不触发信号回调。
3. 跨 Part 与双端验证：来源 Part A、动画 Part B、hitbox Part C、HUD 座位 Part D；再用 BodyPart／其他 `IPartAssembly` 检查公共接口。服务端和客户端分别验证，不将客户端结果作为服务端证据。
4. 实际信号输出验证：使用现有发送者和线路覆盖来源身份、端口转译、local／global 订阅、物理反馈到主线程，以及结构变化重绑。
5. HUD 接入：页面绑定通用共享读数，名单补占用者数据和座位重复集合，图形标签补实际履带渲染。具体样式与通用 MoLang 能力分别验收。

调查完成了第 3 节的基础探针和实施方案 §3 的隔离候选对照。候选验证了实时读写、重绑与独立粒子变量，同时暴露字符串赋值、不可写宿主表和多态入口重复执行等配套问题；这些生产缺陷尚未修复。探针直接复用本地 classes，未关闭现存游戏进程或重打包依赖。

## 8. 代码定位

- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/SparkMolangContext.java`：构造期复制与 reset。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/AnimatableVariableBinding.java`：动画体变量桥接。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java`：`v.*` 的编译读写入口。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/part/AbstractPart.java`：共用存储、Part 总线与清理。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/molang/MechMolangContext.java`：局部与装配体查询。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/signal/ISignalSender.java`、`ISignalBus.java`、`SignalChannel.java`：实际投递、订阅与来源槽。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractSubsystem.java`：物理反馈与主线程发送。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/interact/HitBox.java`：服务端条件的 Part 上下文。
- `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudPartSnapshot.java`：HUD 共享读数采样。
