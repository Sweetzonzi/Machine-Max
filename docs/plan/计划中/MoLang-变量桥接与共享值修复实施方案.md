# MoLang 变量桥接与共享值修复实施方案

> 状态：2026-11-14 源码调查与隔离对照探针完成，生产代码未实施。范围包含 Spark-Core 的变量绑定和求值入口、Machine-Max 的共享值与履带查询。探针中的候选实现只验证部分基础机制，不是可直接合入的补丁。

调查基线：Spark-Core `8fa3e9e9`、Machine-Max `444f200c`；两仓库 `src/main` 无工作区改动。探针使用的 SparkMolangContext.class 时间晚于其源码，且实测行为与本文定位的构造／reset 路径一致。

## 1. 决策与边界

建议沿现有能力完成四件事：

1. Spark 的 `v.*` 使用动画体变量表的实时视图，保留无动画体上下文的独立变量。
2. 修正赋值类型、对象绑定和多态求值的副作用次数，使共享 setter 有可靠的执行基础。
3. Machine-Max 的 `local`／`global` 直接操作既有 Part／装配体键值表；履带提供来源 Part 内的原始查询。
4. 真正的信号输出使用现有发送者、目标表、端口、来源槽及物理反馈路径。

HUD、动画、hitbox 条件都是消费者。跨 Part 读数不通过 HUD 专用声明、全车逐帧扫描或专用导出注册表实现。本文的拟议 MoLang 语法尚不能在内容包中直接调用。

## 2. 已核实的数据路径

### 2.1 容器确实共用，MoLang 接线存在断层

```text
AbstractPart.signalStorage ── 同一个 Map ── IAnimatable.getVariables()
        ↑ local.get                              ↑ 应由 v.* 实时访问

SubsystemController.signalStorage ← global.get

SignalChannel：一个输入频道内，按 ISignalSender 身份保存来源槽
```

`SparkMolangContext` 的现状：父类构造独立 `MutableObjectBinding`；子类再构造 `AnimatableVariableBinding`，把当时的内容复制进父类。`reset` 只给 bridge 换宿主。编译器的 `v.*` 则调用传入 context 的 `getVariableStorage()`，访问的是父类副本。

解释器通过 engine scope 查 `v`；`MolangEngineHelper.bindContext` 同样绑定父类副本。直接把现有 bridge 放进 scope 也不能修好解释赋值，因为解释器用 `instanceof MutableObjectBinding` 判断可写性，而 bridge 当前只实现 `ObjectValue`。

`local.get/global.get` 的直接存储读取与此断层不同；不能把所有 MoLang 查询一概视为失效。

### 2.2 写 Map 与投递信号的区别

`ISignalSender.deliverSignal` 会写接收者的来源槽，按其存储接口写扁平表，再调用 `onSignalUpdated`；总线据此向订阅者分发。普通赋值只有键值表变化，没有这些投递行为。

因此 `v.x = 1`、拟议 `local.set('x', 1)` 只表示发布最近值。需要驱动执行器或通知订阅者时，发送者通过既有线路发送。不能让任意变量赋值隐式触发一整条控制链。

### 2.3 运行端和程序动画

`AbstractPart.onTick` 在客户端自动播放循环动画；物理步通过 `AnimController.physTick` 推进动画。常驻 parallel 动画可用作客户端状态发布入口。

骨骼表达式位于混合采样路径，受 `AnimController` 的 LOD 跳帧影响；timeline／notify 在 `AnimInstance.tick` 的主线程路径检查。常驻存在不等于每个骨骼表达式每物理步都执行。只需最近读数的表现消费者可以接受相应刷新频率；严格的物理判定应使用确定相位的发布入口。

服务端 hitbox 条件读取服务端状态。客户端循环动画写出的 global 值不会自然到达服务端。需要服务端读数时，来源必须在服务端执行，或直接使用服务端已存在的子系统反馈／业务 tick。客户端 HUD 可以从已同步的真实状态独立计算。

## 3. 探针结果与证据边界

探针复用本地 Spark Java／Kotlin classes 和依赖，用 `IAnimatable` 代理提供变量 Map。代理的 `putVariable` 故意入队，用于检查候选是否依赖延迟写入。没有启动 Minecraft 或创建 native 物理世界。

候选只在任务目录生成同名影子类，优先加载至探针 JVM：bridge 继承 `MutableObjectBinding`；父类注入 bridge；bound 写入同步操作源 Map；unbound 使用独立存储；补 `entries` 与批量写入派发；多态入口只执行现有字符串通道一次。生产源码和发布 jar 未改动。

| 判据 | 当前类 | 隔离候选 |
| --- | --- | --- |
| 源 Map 的 hp 从 7 变为 9，reset 后执行同一已编译读取 | 7 | 9 |
| 同一 context 重绑到 hp=31 的动画体 | 7 | 31 |
| 执行 `v.x=13; return v.x;` 后检查源 Map | 表达式为 13，源 Map 无 x | 表达式和源 Map 均为 13，无排队写入 |
| 解释器给直接绑定的 bridge 赋值 | 读回 0，源 Map 无值 | 读回 19，源 Map 为 19 |
| 编译 `v.tag='pilot'; return v.tag == 'pilot';` | 0，存储为数值 0 | 同样失败，证明需要编译器配套修复 |
| 解释执行相同字符串代码 | 表达式为 1，源 Map 无值 | 表达式为 1，源 Map 为字符串 pilot |
| bridge.set 写 ObjectValue／ArrayValue，再读取类型 | 延迟写入尚未发生，读取 NumberValue | 保留 MutableObjectBinding／ArrayValue |
| 缺失键的 getProperty | 非空的零值 property | null |
| 有一个键的普通 MutableObjectBinding.entries | 空集合，布尔值 false | 一个键，布尔值 true |
| bridge.set(name, null) | 返回 false，键存在 | 移除键 |
| 无宿主上下文独立变量／粒子 emitter_age | 29／2 | 29／2 |
| 土耳其区域设置下写 INPUT、读 input | 0 | 7 |
| 动画体返回 Map.of()，执行赋值 | 写在副本，表面成功 | UnsupportedOperationException，暴露宿主契约问题 |
| evalAsObject 执行 `v.count=v.count+1; return q.nullable;`，nullable 返回 null | count=2 | 第一轮仅修基础绑定时 count=2；加入 §4.6 单通道修复后 count=1 |

对象／数组那一行直接证明的是排队写入尚未落地。其最终类型损失另有源码证据：当前 bridge.set 对非 StringValue 一律调用 `getAsNumber()`，对象／数组因此会被转换为 0。候选直接存 Value，类型得以保留。

工作目录：`C:/Users/13680/.codex/visualizations/2026/11/13/01a1d589-199e-7633-a8da-b604e4a1e490`。文件为 `MolangStorageDeepProbe.java`、`molang-deep-probe.gradle`、`prepare-molang-candidate.ps1` 和 `candidate/`。复现命令在 Machine-Max 根执行：

```powershell
$probeDir = 'C:\Users\13680\.codex\visualizations\2026\11\13\01a1d589-199e-7633-a8da-b604e4a1e490'
.\gradlew.bat --offline --gradle-user-home 'C:\Users\13680\.gradle' -I "$probeDir\molang-deep-probe.gradle" molangDeepProbe
.\gradlew.bat --offline --gradle-user-home 'C:\Users\13680\.gradle' -I "$probeDir\molang-deep-probe.gradle" -Pcandidate molangDeepProbe
```

基线与候选任务均成功运行。探针会打印预期暴露的错误，不用任务退出码代表功能全部正确。混合大小写兼容、完整类型编译、真实线程交错、信号回调、结构变化及网络行为尚无候选验证。

## 4. Spark：具体修复

### 4.1 用现有父类注入能力接 bridge

`MolangContext(T, MutableObjectBinding)` 已支持注入变量绑定。保留公开 getter 的 `MutableObjectBinding` 返回类型，让 `AnimatableVariableBinding extends MutableObjectBinding`，通过构造器链传给父类：

```java
public SparkMolangContext(@Nullable T animatable) {
    this(animatable, new AnimatableVariableBinding(animatable));
}

private SparkMolangContext(@Nullable T animatable, AnimatableVariableBinding bridge) {
    super(animatable, bridge);
    this.variableBridge = bridge;
}
```

构造过程不复制 Map，也不调用可被子类覆盖的 `getVariableStorage`。`reset` 和直接 `setEntity` 的绑定变更集中在同一入口，保证 entity 与 bridge 指向同一宿主；`prepareEvaluation` 只设置本次时间。getter 返回的绑定对象身份在 context 生命周期内保持稳定。

无宿主时，bridge 的 `getProperty/set/entries` 使用父类独立存储；绑定宿主时使用宿主 Map。两份数据不互相搬运，解绑不泄漏前一个宿主的值。当前粒子一直无宿主，原有独立变量用途由此保留。

`HudMolangContext` 已覆盖 getter，并提供只读重复项下标和运行时变量的查找层。该覆盖保持有效，HUD 的 v 不绑定到座位 Part。基类构造器避免虚调用也消除了子类字段初始化顺序的隐患。

### 4.2 同步赋值与宿主 Map 契约

bound `set` 必须当场写源 Map，保证同一表达式随后的读操作可见，其他 context 也能读取。它不能调用 `IAnimatable.putVariable`：该方法目前经 `TaskSubmitOffice.submitImmediateTask` 无条件入队。

Java 侧的 `putVariable` 保留现有排队语义，作为外部调用入口。MoLang 的同步赋值与外部任务提交在文档中分别说明，不修改全局物理任务调度，也不通过等待队列完成来求值。

所有绑定变量表必须是生命周期稳定、支持写入的 Map。允许主线程和物理线程访问的实现使用 `ConcurrentHashMap`；null 表示删除操作，不能把 null 值存入该 Map。明确需调整的实现：

| 仓库／实现 | 当前问题 | 处理 |
| --- | --- | --- |
| Spark `ItemAnimatable` | mutableMapOf | 使用并发变量表，检查调用端存取约定 |
| Machine-Max `PartAnimatable`、`SubPartAnimatable`、`VehicleAnimatable` | getVariables 返回 Map.of() | 各实例持有可写变量表；它们是预览对象，不自动借用真实 Part 的信号表 |
| Machine-Max `BallisticProjectile` | HashMap | 使用并发变量表 |
| Machine-Max `FabricatorBlockEntity`、`ResearchTableBlockEntity`、`RoadBaseBlockEntity`、`TotalStationBlockEntity` | HashMap | 使用并发变量表 |
| Spark Player、Machine-Max AbstractPart／HudRuntimeElement、ARMS MechaBodyAnimation | 已使用并发表 | 保持归属，验证读写兼容 |

测试夹具也应满足其声明的并发用途。单线程夹具可以使用普通可写 Map。接口对下游明确：共享求值需要并发 Map；只在单线程使用时普通可写 Map 仍可用。不能捕获不可写表异常后静默退回 context 副本，否则数据断层会重新出现。

并发 Map 只保障逐键操作，不使 `v.count=v.count+1` 成为跨线程原子操作，也不使内嵌可变对象安全。共享发布使用数值、字符串或不可变样本；多个来源不得把同一个普通计数键当成原子累加器。

### 4.3 补全绑定对象行为

在既有类内完成，不增加第二套存储接口：

- `AnimatableVariableBinding.getProperty` 对缺失键返回 null，存在值的 property 标记 `constant=false`，避免编译期折叠活动状态。
- `set(name, null)` 删除对应键；`blocked()` 时拒绝写入，继承的 block 行为有效。
- `MutableObjectBinding.entries()` 返回属性快照，反映实际变量；这也修正普通对象布尔判定始终为空的问题。
- `setAllFrom` 从来源的公开 entries 读取，经过目标 set 写入；不直接访问两端私有 properties，确保 bridge 的动态读写与目标的只读检查有效。
- 数值存 Double、字符串存 String；已有 ObjectValue／ArrayValue 保留 Value，不用 getAsNumber 抹成 0。Java 任意 Map、任意信号对象不自动变成 MoLang 对象，继续使用 Value.of 的明确支持范围。

复杂可变 Value 只在所属求值线程使用；跨线程及 HUD 快照承诺的共享类型先限于标量。绑定修复不等于为任意对象增加复制、序列化和网络同步协议。

### 4.4 属性大小写与信号键兼容

MoLang 属性名不区分大小写；`local.get('Key')` 等函数的字符串键与既有信号频道精确匹配。不能为了 v 属性匹配，把整个 Part／装配体 Map 的键统一改写。

桥接推荐采用以下兼容规则，全部使用 `Locale.ROOT`：

1. v 属性先归约成小写，优先直接查小写键，常用路径为 O(1)。
2. 小写键不存在时，为 Java／信号写入的混合大小写键查找同名项。若有多个仅大小写不同的候选，取原始字符串字典序最小项，使结果稳定；作者应通过精确字符串键访问这类冲突数据。
3. v 写入／删除操作使用同一解析规则，保留已存在键的实际拼写；无对应项时创建小写键。entries 使用一致的属性视图。
4. `local.set/global.set/get/has/unset` 保持字符串键精确匹配。`v.status_engine` 与 `local.get('status_engine')` 可互通；`v.a.b` 是嵌套访问，不代表字符串键 `'a.b'`。

兼容扫描仅在直接命中失败时发生，不维护会被信号写入、清表和拆装弄失效的键索引。基准覆盖大量变量缺失时的扫描开销；内容包通常使用小写键，作者文档推荐此写法。`CaseInsensitiveStringHashMap` 的默认区域小写转换也使用 `Locale.ROOT`。

### 4.5 赋值保留值类型

`MolangCompilingVisitor` 当前把 v 赋值 RHS 强制编译为 double，再包装 NumberValue；嵌套赋值同样偏向数值。bridge 接通后，这会把字符串错误传播进共享表。

在现有 visitor 内增加存储 Value 的发码路径，供赋值 RHS 使用；骨骼数值算式保留 double 运算：

| RHS 形状 | 存储用值 |
| --- | --- |
| 数字、数值算术、数值 QueryBinding | NumberValue |
| 字符串字面量、StringQueryBinding | StringValue；查询空值按该入口既定规则处理 |
| v 属性、嵌套属性、数组元素 | 原始 Value，不先转成 double |
| 条件分支、空值合并、连续赋值 | 仅执行被选分支，传递实际产生的值 |

RHS 只求值一次，写入后保留该结果作为赋值表达式结果，按外层数值／字符串目标转换。目标 set 的拒写或异常策略与现有只读 HUD 契约配对验证。`t.*` 的数值快路径保留，字符串及值复制使用相应槽位，避免 `t.x='a'; v.y=t.x` 又把类型抹掉。动态变量之间的相等比较按实际 Value 类型判断，不能因两侧都没有字符串字面量而把两个字符串当作数值 0 比较。

字节码中通过 NumberValue／StringValue 的构造或工厂完成 Value 转换。不能依靠当前 AsmUtil 的一般装箱操作把 Double 强制当成 Value；方法末尾转换、临时槽位宽度和分支汇合必须通过字节码校验。

解释器的赋值和 for_each 可写性判断统一使用 `ObjectValue.set`，与接口本身及编译后的嵌套赋值一致；只读对象的默认 set 返回 false。编译期常量判定不能执行赋值或有副作用的函数，即使其参数全是字面量。

空值合并有独立兼容风险：解释器目前按 `Value.getAsBoolean` 决定是否取右值；字符串编译通道又支持 Java null。本轮不把整个语言的缺失值定义改成新的类型体系。必须以现有数字／字符串回归为基线，给值目标写清 null、0、空字符串的转换，并对两个入口做成对测试；未覆盖的形状不能静默写 0。

### 4.6 evalAsObject 只执行一次

当前入口先执行 StringExpression，返回 null 时又执行数值表达式。探针已证明一个赋值发生两次。开放 set 后不能保留此路径。

推荐直接收敛当前入口：执行一次 `compileString(...).evaluate(context)`；结果非空时按现有规则解析数字文本或返回 String；结果为 null 时返回 Double 0，不再次求值。

现有编译器已经支持数值到 String 的返回转换。补充探针验证了数字常量 `2+3`、数值查询 `q.numeric`、数值查询参与乘法、变量加法、null 查询、文本空值兜底和文本条件分支，字符串通道分别产生预期数值文本／null／文本。该证据支持复用现有入口，无需增加返回接口、额外缓存或解释器回退。

兼容转换要求：保留 String／Double 调用约定及数字文本解析规则；StringQueryBinding 的 null 按当前 fallback 最终结果映射为数值 0，compileString 的 null／`??` 用途独立保留。`evalAsObject` 当前没有承诺任意 ObjectValue／ArrayValue 的 Java 返回，桥接修复也不扩张该标量 API。若某个现有表达式形状不能在字符串返回通道正确编译，应修该形状或明确报编译错误，不能靠第二次执行来补救。

### 4.7 scope、缓存与活动 context

Registry 的 engine 按 context 类共享，原型没有真实宿主。编译函数必须通过 evaluate 传入的 context 访问变量和查询；不能为了修解释器，把缓存 engine 的 v 反复绑定到某一个活动 Part。

解释求值使用 `MolangEngineHelper.createEngine(actualContext)` 这类实例绑定入口。Registry 的 engine 是编译入口；若要支持它解释执行某个活动实例，应由单次求值提供局部 scope，不修改全局共享 scope。

表达式可跨实例复用，活动 context 不具备并发重绑契约。`AbstractPart` 当前缓存一份 context，动画的主线程 notify 和物理采样存在静态可见的共享路径；这次探针未复现真实线程竞争。

实施时给这些入口加可控的双线程测试，分别改变动画时间并读值。若同一 context 确实跨线程，按调用入口拆开其求值实例：Part 保留物理采样上下文，timeline 的主线程调用持有独立上下文，两者绑定同一变量表。专用查询上下文通过其已有构造与状态注入创建；不要在热路径逐骨骼创建 context，也不要用每 Part 一个 ThreadLocal 长期把 Part 挂在世界线程上。此项与同步 setter 一同验收，不能把 ConcurrentHashMap 当成 context 线程安全的证明。

## 5. Machine-Max：共享读写与履带

### 5.1 API 与精确语义

方法放在 `MechMolangContext`，使用现有 `@QueryBinding`／`@StringQueryBinding`。两个 scope 分别使用 `part().getSignalStorage()` 和 `IPartAssembly.getSubsystemController().getSignalStorage()`，不限定 VehicleCore。

| 拟议接口；local/global 对称 | 语义 |
| --- | --- |
| get(key)、get_str(key) | 现有读取语义：数字缺失为 0，字符串缺失为 null |
| set(key, number) | 同步写 Double，成功返回写入值；无宿主时无操作并返回 0 |
| set_str(key, text) | 写 String，返回写入文本；文本为 null 时清该键，无宿主返回 null |
| has(key) | 精确判断键是否存在，返回 1／0；0 值和缺失可区分 |
| unset(key) | 删除扁平存储中的键，实际删除返回 1，否则 0 |

数值 setter 与现有 NumberValue 一致地处理非有限值，不能把 NaN 传入耐久比例或动画矩阵。空字符串是合法文本，不等同于 unset。无宿主回退主要服务编译原型及未绑定场景，有效发布者应由其运行入口保证宿主存在。

getter／setter 每次解析当前所属装配体，不捕获初始化时的 global Map。单次方法内部取一个容器引用完成操作；结构清理和物理发布的相位边界见 §6，不能承诺调用与拆装形成事务。

`v.*` 的归属按 IAnimatable 本身决定。真实 AbstractPart 的 v 与 local 表相同；预览动画体、HUD 和实体拥有自己的变量，不能从 MechMolangContext 可解析乘坐 Part 推断其 v 就是那份 Part 表。

### 5.2 履带查询

在 `MechMolangContext` 中声明拟议 `subpart` 命名空间，参数为当前 Part 内的真实 SubPart key。当前文件主要提供 local/global；不能将拟议接口当成已经存在的绑定。

| 查询 | 有有效 TrackLink | 无成员／无履带／已解散 |
| --- | --- | --- |
| subpart.has_track(name) | 1，包括 broken 履带 | 0 |
| subpart.track_durability(name) | TrackLink 当前耐久 | 0 |
| subpart.track_max_durability(name) | TrackLink 当前最大耐久 | 0 |
| subpart.track_broken(name) | broken 为 1，否则 0 | 0；用 has_track 区分缺失 |

实现只做 `part -> subParts.get(name) -> trackLink`，读取一次 link 引用，使用现有 `durability()`、`maxDurability()`、`isBroken()`、`isDisposed()` 访问器；不扫描整车或按显示名猜轮子。同一 link 的多个成员返回同一履带状态。履带耐久独立于车轮耐久；broken 与 disposed 是不同状态。现有 TrackLink 同步承载客户端真实状态，不为 MoLang 复制一套耐久网络字段。

### 5.3 跨 Part 使用示例

来源 Part A 的常驻程序入口发布；键名由内容包约定：

```text
global.set('status.engine.hp', local.subsystem_durability('engine'));
global.set('status.engine.max_hp', local.subsystem_max_durability('engine'));
global.set('status.left_track.present', subpart.has_track('left_sprocket'));
global.set('status.left_track.hp', subpart.track_durability('left_sprocket'));
global.set('status.left_track.max_hp', subpart.track_max_durability('left_sprocket'));
```

动画 Part B、hitbox Part C 和座位 Part D 对同一装配体读取 `global.get('status.left_track.hp')`。hitbox 的逻辑端必须有相应发布者。HUD 从 `HudPartSnapshot` 的 global 快照读取；需要 has 的 HUD 页面同时给快照及 HudMolangContext 提供存在性查询，保持只读消费。

多个发布键没有原子批次承诺，多个来源写同一键采用最近写入值。对于 hp/max 这类显示读数，消费者用 present 和 max>0 处理缺失；若业务要求一组严格同代数据，应该在已有对象的业务相位发布完整不可变样本，而不是把写入若干 Map 键当作事务。

## 6. 信号、生命周期与边界

未来 MoLang 输出子系统可以把表达式结果交给已有 `ISignalSender`。主线程求值后走 `sendSignalToAllTargets`；物理线程输出先通过 `AbstractSubsystem.publishFeedback`，由主线程 flush 按已配置目标投递。该反馈容器是最近值，不是保证逐条交付的事件队列。

来源身份用子系统实例；路径沿现有 `getTargetNames/getTargets`、SignalPort 转译和 local/global 订阅。查询、共享 setter、hitbox 条件不替调用方捏造发送者身份。物理步的已绑定执行器实例直送继续服从既有控制契约。

结构变化由 VehicleCore 清理装配体、Part、子系统的相关容器并重建接线。同步 setter 不留下捕获旧装配体的排队任务；`publishFeedback` 则服从现有结构清理。必须在实际拆装测试中确认物理步与结构提交的边界：已经开始的求值不能在结构提交完成后把旧归属数据写回活动容器。若存在该交错，应在已有结构提交／物理调度处消除，避免只给 MoLang 再叠一份生命周期管理器。

共享值无自动有效期、无网络同步、无多来源汇总。动态来源消失时用 present 或 unset 表达；拆装后等待相应运行端的发布者重新发布。unset 只清扁平表，不清 SignalChannel 的发送者槽，也不等同于释放控制输入。需要释放来源时使用既有 EmptySignal 约定。

普通共享键可供动画、条件及 HUD 使用，但零件几何着色可直接读真实几何对象，座位名单继续使用专用座位快照。占用者身份同步和真实履带模型绘制属于 HUD 接入任务，不要求把它们压成数值信号键。

## 7. 文件级实施顺序

| 批次 | 文件／位置 | 可审查交付 |
| --- | --- | --- |
| A 基础存储 | Spark `SparkMolangContext`、`AnimatableVariableBinding`、`runtime/value/MutableObjectBinding`、`util/CaseInsensitiveStringHashMap` | 注入实时绑定，独立无宿主存储，完整可写对象行为，大小写兼容 |
| A 宿主配套 | Spark `IAnimatable`、`ItemAnimatable`；Machine-Max §4.2 所列变量表 | 同步表达式写入契约与可写并发表；核对 Part 主／物理 context 交错 |
| B 求值正确性 | Spark `MolangCompilingVisitor`、`ExpressionInterpreter`、`IsConstantExpression`／`ExpressionInliner`、`MolangContextRegistry`，核对 `AsmUtil` 转换 | RHS 类型、短路与单次副作用、多态单次执行；保留数值快路径 |
| C 通用机件接口 | Machine-Max `MechMolangContext`，调用 TrackLink 现有访问器 | local/global 读写、has/unset、履带原始状态；公共 IPartAssembly 作用域 |
| C 消费快照 | `HudPartSnapshot`、`HudMolangContext` 及其测试 | has 与真实键存在性匹配，HUD 自定义 v 与只读重复项保持有效 |
| D 资源与文档 | 实际来源 Part 的常驻程序资源、MoLang 查询词表、跨 Part 示例 | 按逻辑端和刷新相位发布；运行端缺失有明确表现 |

依赖关系为 A → B → C → D。A 和宿主配套作为一个可运行批次交付；B 完成后才开放通用 setter，避免重复执行副作用。每批只验证其承诺，不先建立没有实际消费者的 MoLang 输出子系统、脚本调度器或同步协议。

getter 返回描述符保持稳定，有利于现有下游字节码兼容；bridge 的类继承关系和赋值行为有变化，需要重新编译并检查当前复合构建消费者。该变更不涉及已有网络载荷或持久化格式；未来若增加网络同步字段，应按各模组协议规则另行升级。

## 8. 验证与完成判据

### 8.1 纯逻辑回归

在 Spark MoLang 现有测试目录建立契约测试，直接验证行为，避免断言私有字段布局：

- 实时读取、同宿主多 context 共享、不同宿主隔离、reset／setEntity 重绑、解绑、绑定对象身份稳定。
- 编译／解释数值与文本赋值、变量复制、嵌套对象、数组元素、临时变量、连续赋值、条件分支、短路、null／0／空文本；副作用每次调用恰好一次。
- evalAsObject 数字、数字文本、普通文本、null 查询、带 setter 的表达式；不会第二次执行。
- entries、blocked、批量写入与 null 删除；缺失 property 不会被常量折叠成固定 0；对象布尔行为有明确判据。
- 小写快路径、Java 混合大小写键、仅大小写冲突键、精确字符串键与土耳其 Locale；dot 字符串键和嵌套访问分开覆盖。
- ParticleMolangEnvironment 缓存的 binding 身份、emitter／particle 写入，以及 HUD 子类 getter 构造安全和只读 alias。
- 同一缓存编译式交替用于两个 context，不捕获原型或上一次 Part；主线程／物理入口并发时不串时间与绑定。

Machine-Max 的契约测试覆盖 scope 对应的真实 Map、缺失与 0、清理返回值、无宿主回退、同轨多成员及 broken／disposed；并用观察者确认 set 不创建来源槽、不触发 onSignalUpdated。

在 Spark、Machine-Max、ARMS-Core 分别执行适用的 `gradlew --offline test`。ARMS 已有 MechaMolangContext、内容包表达式和表现层测试，用于确认专用查询状态与变量归属；不为此次修复编写与耐久内容数值一一对应的脆弱断言。

### 8.2 真实运行验证

用来源 A、动画 B、hitbox C、座位 D 分属不同 Part 的固定小场景，分别验证两端：受损、修复、履带断裂／解散、拆装／合并／分裂、清表后发布、迟加入及维度变化。再用其他 IPartAssembly 宿主核对 global 没有依赖 VehicleCore。

信号用既有发送者覆盖同频道多来源、端口转译、local/global 订阅、物理反馈到主线程、结构变化重绑。游戏侧验证不把 KV setter 测试冒充信号路由测试。

客户端检查循环动画启动、LOD 刷新、暂停／恢复、内容包重载，以及 HUD 快照的稳定读取。微基准只覆盖本次引入的大小写兼容路径与值目标分配，不以未运行的 FPS 估计作为性能结论。

完成条件：A–C 契约测试与复合构建回归通过；跨 Part 和两端场景行为符合发布相位；现有粒子和 HUD 能正常运行；作者能从一份示例明确知道值由谁、在哪端、何时发布。浏览器原型和本次隔离探针都不能代替这组验收。

## 9. 关键代码位置

以下路径以 Machine-Max 根为基准，Spark 路径显式指向同级仓库：

- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/SparkMolangContext.java`：复制式初始化、reset 和 bridge。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/AnimatableVariableBinding.java`：宿主变量转换与写入。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangContext.java`：已有绑定注入构造器。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java`：v 和嵌套赋值、类型目标。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/ExpressionInterpreter.java`：可写对象判断。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/MolangContextRegistry.java`：类级缓存及 evalAsObject 双次执行路径。
- `../Spark-Core/src/main/java/cn/solarmoon/spark_core/particle/client/ParticleMolangContext.java`、`ParticleMolangEnvironment.java`：无宿主变量与绑定引用缓存。
- `../Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/animation/IAnimatable.kt`、`util/TaskSubmitOffice.kt`：变量入口和实际入队行为。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/part/AbstractPart.java`：变量／信号表共用、活动 context、循环动画启动。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/molang/MechMolangContext.java`：Part／装配体作用域及拟议通用接口。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/signal/ISignalSender.java`、`SignalChannel.java`：真实投递与来源槽。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractSubsystem.java`：最近反馈跨线程发布。
- `src/main/java/io/github/sweetzonzi/machine_max/common/mech/vehicle/track/TrackLink.java`：履带独立耐久、broken、disposed 与同步。
- `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudMolangContext.java`、`HudPartSnapshot.java`：专用变量绑定与消费快照。
