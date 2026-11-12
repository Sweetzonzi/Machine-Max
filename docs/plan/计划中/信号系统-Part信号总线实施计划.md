# 信号系统 — Part 信号总线实施计划

**状态**：计划中；设计已明确，P1—P5 尚未实施。本文定义公共 Part 的信号总线契约、代码改动范围和验收场景，不表示功能或验证已经完成。

**核对日期**：2026-11-12。

**关联需求**：[人工与机械装填及操作员接入计划](武器系统-人工与机械装填及操作员接入计划.md) §4 要求武器／机构在当前 Part 范围内发现座位。同一 Part 的多个 SubPart 应互通，两个炮塔 Part 应隔离；作者可以在独立炮塔上复用同一发现频道。

## 1. 目标与交付边界

让公共基类 `AbstractPart` 实现已有 `ISignalBus`，提供真实的 Part 内广播，并让现有只读终端查询通过同一总线订阅集合展开 `local`。不建立装填专用的 Part 座位扫描器，也不另设一套仅供发现使用的范围接口。

接入至少包含成员管理、到达通知、部件存储和路由接收策略；只在类声明追加接口无法使现有 `local` 线路具备完整广播行为。只读发现的接口分派已经具备：`ISignalSender#resolveSignalLine` 识别的是 `ISignalBus`，没有要求接收者必须是 `SubsystemController`。

本轮采用状态信号语义：接收者按“频道、原始发送者”保存最新值，允许相同值经 `local` 与 `global` 覆盖同一槽，不增加跨线路发送去重。只读握手使用查询自带的对象／频道去重。即时交互与状态读取的区别见 §5.1；发送去重不是 Part 总线实施的前置工作。

交付项包括：Part 的独立订阅集合及三个现有订阅方法；构造／销毁／失败回收的自动订退；真实广播和同范围只读发现；连接器进入 local 的路由许可；Part 输入频道与键值存储的完整清理；局部隔离、退订独立性和实际投递的契约验证。

本轮不实现装填人员属性、座位发现字段、机械装弹机拆分、普通名字的跨 SubPart 寻址、事件队列、跨线路发送去重或整套内容包改接线。装填系统通过关联计划接入本轮交付的通用 Part 总线能力。

## 2. 可定位的源码依据

下列 Java 路径以仓库的 `src/main/java/io/github/sweetzonzi/machine_max/` 为前缀。表内为核对日期的当前代码，目标行为在 §3—§7 定义。

| 编号 | 源码坐标 | 当前实现 |
| --- | --- | --- |
| R1 | `common/mech/part/AbstractPart.java#AbstractPart`、`#getSignalAddress`、`#getSignalStorage` | 公共基类实现 `ISignalReceiver`，地址是 `local`；提供部件级存储，没有广播回调 |
| R2 | `common/mech/signal/ISignalSender.java#getReceiversFromNames`、`#resolveSignalLine` | `local` 首跳是发送者所属 Part；总线按成员声明筛选，普通接收者作为终端结束查询 |
| R3 | `common/mech/signal/ISignalBus.java#broadcast`、`#getAllSubscribers`、`#subscribe`、`#unsubscribe` | 现有总线接口继承发送和接收契约；广播按原始发送者分槽，通知符合频道条件的成员；成员管理方法需要实现 |
| R4 | `common/mech/subsystem/SubsystemController.java#busSubscribers`、`#onSignalUpdated`、`#rebuildSubscriptions` | 全车总线只订阅子系统；收到通知时遍历该频道内各来源槽，更新总线存储并广播 |
| R5 | `common/mech/part/AbstractPart.java#createSubsystems`、`#getAllSubsystems`、`#subsystemsByName` | 构造时填写 SubPart 容器与 Part 按名索引；`getAllSubsystems` 从各 SubPart 当前容器汇总去重集合 |
| R6 | `common/mech/vehicle/SubPart.java#destroy`、`#abortConstruction`；`common/mech/part/AbstractPart.java#restoreState`、`#abortConstruction` | 正常销毁和构造失败都会清空 SubPart 的子系统容器；完整恢复也可能销毁保存状态中缺少的子结构；上述路径没有对应清空 Part 按名索引的动作 |
| R7 | `common/mech/signal/ISignalSender.java#deliverSignal`；`common/mech/signal/SignalPort.java#onSignalUpdated` | 直送会写接收者提供的存储并通知；端口转发写来源槽后直接通知，未统一调用 `deliverSignal` |
| R8 | `common/mech/signal/ISignalReceiver.java#acceptAllRoutingInput`、`#acceptAllBroadcastInput`、`#getAcceptedChannels` | 三个默认值分别为 false、false、空列表；端口路由和总线广播使用不同接收开关 |
| R9 | `common/mech/vehicle/VehicleCore.java#clearSignalStorage`、`#rebuildSignalTargets`；`common/mech/subsystem/SubsystemController.java#clearSignalStorage` | 结构变化会清输入槽并重建线路；车辆清理逐 Part 清输入频道，但没有清 Part 的 `signalStorage`，全车总线的两份容器都有清理 |
| R10 | `common/gametest/SignalPipelineGameTest.java#structureChangeClearsSignalStorageBeforeDiscovery` | 既有用例检查 Part 输入频道、全车总线和端口缓冲的清空，没有断言 Part 的键值存储清空，也没有覆盖 Part 内广播 |
| R11 | `common/mech/subsystem/AbstractSubsystem.java#onSignalUpdated`；`common/mech/vehicle/interact/InteractBox.java#interact` | 子系统可立即处理 `InteractSignal`；交互区按显式目标顺序尝试，收到 `CONSUME` 时结束 |

现行发现与投递规则见 [子系统发现与投递重构实施计划](../已实现/信号系统-子系统发现与投递重构实施计划.md) §2—§4。该文中的 `global` 展开是已实现基线；本文定义 `local` 的总线实施契约。

## 3. 运行契约

### 3.1 两级范围

| 地址 | 总线对象 | 默认自动订阅范围 |
| --- | --- | --- |
| `local` | 当前 `AbstractPart` 实例 | 该 Part 全部 SubPart 当前持有的子系统 |
| `global` | 当前装配体的 `SubsystemController` | 该装配体全部子系统 |

Part 的订阅范围不包含自己、全车总线、其他 Part 或连接器端口。全车总线保持平铺的子系统订阅集合，不将 Part 总线作为额外成员嵌套展开。实际参与广播和发现的对象以各总线当前订阅集合为准，显式退订的子系统按 §4 排除。这样可以保留两种明确范围，避免一个 Part 的广播沿端口自动扩散到其他炮塔。

“Part 内”是对象归属范围，不计算空间距离，也不等于 SubPart。跨 Part 服务通过作者配置的连接器线路表达；两个炮塔在同一 Part 内时，频道或显式接线负责进一步区分。

### 3.2 只读查询

Part 成为 `ISignalBus` 后，R2 的现有总线分支可以取得其成员，按 `acceptAllBroadcastInput` 或声明频道筛选，使用现有稳定排序、对象身份／频道去重、端口转译及环路访问集合。

查询不写任何来源槽或 MoLang 存储，不通知接收者，也不发送空信号。成员集合覆盖全部 SubPart，因此可以从炮身子零件发现同一炮塔 Part 的座位，不需要同时完成跨 SubPart 的按名字面寻址。

R2 的 `getReceiversFromNames` 对普通名称的解析当前仅查发送者所在 SubPart。把这个解析提升到 Part 全域是 [动画系统 Part 设计](../已实现/动画系统-Part作为动画体重构设计.md) §6.5.3、§10 的独立待办；`local` 总线接入不以该工作作为前提。

### 3.3 真实信号到达

Part 接收一次信号后，要同时完成本 Part 的键值存储更新和符合条件的成员通知；原始发送者身份在来源槽及回调中保持一致。普通子系统不会因为多了一层总线而把 Part 当作实际操作员。

应由 Part 的到达处理保证存储更新，覆盖直送与端口转发两条 R7 路径。仅依靠 `deliverSignal` 写存储会漏掉端口送达。已有 `local.get` 对当前部件键值的读取契约需保留。

R4 的全车实现按频道遍历所有来源槽。Part 沿用该状态广播方式，更新自己的频道键值存储，并把各来源的当前值写入订阅者的对应槽；本轮不改造全车通知规则，也不建立历史事件队列。端口转发保留原始发送者，不能用 Part 身份覆盖这些来源。同一频道的不同发送者继续独立分槽，不增加多源仲裁规则。

### 3.4 连接器入口与筛选

Part 覆写 `acceptAllRoutingInput` 并返回 true，允许经过连接器的频道到达总线，再由订阅者的接收声明筛选。Part 的 `acceptAllBroadcastInput` 保持默认 false，不将自己作为另一条总线的无条件接收者；不能通过让座位或全部成员接受任意频道实现。

R8 表明 Part 当前继承的路由接收条件会拒绝未声明频道，因此“端口转译后指向 local”必须显式验证。全车总线经端口进入时也受到同一默认条件影响；其既有行为应独立核对，不能把发现直连 `global` 成功等同于所有跨端口总线路径都可用。

## 4. 订阅状态与生命周期

`subscribe`、`unsubscribe`、`getAllSubscribers` 保留在现有 `ISignalBus` 接口中，每个调用都作用于接收调用的总线实例。Part 维护自己的 `Set<ISignalReceiver>`，采用与 R4 相同的并发集合形态；`SubsystemController` 维护全车总线的订阅集合。`subscribe` 添加、`unsubscribe` 移出当前总线的集合，重复调用按集合语义幂等；两个总线中的同一接收者可以分别订阅或退订。

物理归属与订阅是两种状态。R5 的 `getAllSubsystems` 可以提供初始自动订阅的候选，但不能代替 `getAllSubscribers` 的结果：子系统仍属于 Part 时，也可以已经退订这个 Part 的总线。广播和只读发现都读取该总线的实际订阅集合，不能在查询时重新把全部物理成员填回去。

以下为目标语义示例，Part 方法尚待实现：

```text
part.unsubscribe(seat)             // 座位退出这个 Part 的 local 总线
controller.getAllSubscribers()     // 座位在 global 的订阅不受上述调用影响
part.getAllSubsystems()            // 座位的物理归属也没有改变
part.subscribe(seat)               // 恢复该座位的 local 订阅
```

默认规则是本 Part 的子系统构造完成后自动订阅本地总线，退出本 Part 的实际子系统结构时退订。初始化／销毁代码可以调用这些方法，但订阅状态归各自总线所有，不由全局控制器统一代管 local。Part 的局部能力不以存在全车控制器为前提。

自动订阅接在 R5 的 `createSubsystems`：子系统成功构造并进入 SubPart 容器后，由所属 Part 注册到本地总线。正常销毁与失败回收共用 R6 的 SubPart 清理入口执行本地退订；是否调用正常 `onDetach` 继续由现有 `notifySubsystems` 条件决定。本地退订不依赖正常拆离通知，避免构造失败分支遗漏清理。

| 时点 | 需要保证的状态 |
| --- | --- |
| 子系统构造完成 | 对象进入所属 SubPart 容器并默认订阅所属 Part 的总线 |
| 初次 attach 或装配体转移 | Part 内成员按自身归属有效，不要求先依赖全车总线订阅 |
| SubPart 正常销毁 | 子系统退出 Part 订阅集合，再清空容器；拆离通知按正常路径执行 |
| 构造失败／未入世回收 | 清理 Part 订阅集合，保持构造失败路径不产生库存掉落 |
| 完整恢复销毁子结构 | 退出的对象不作为广播或查询候选 |
| 装配体合并／分裂 | Part 内部结构未变时保留其 local 订阅，global 按装配体归属重建；业务发现按结构变化两相位重建 |

R6 表明 `subsystemsByName` 存在保留已销毁对象的可能，因此不能把该按名索引作为订阅表，也不能只清空 SubPart 容器而保留总线订阅。实际销毁和失败回收都要覆盖退订，但失败回收不走正常库存掉落。按名索引的独立清理问题不属于本轮总线接入的前提。

在 `AbstractPart` 提供清理本 Part 两份临时信号容器的方法，车辆的 R9 清理入口调用该方法。清空对象为 `signalInputChannels` 与 `signalStorage`，不包含订阅集合；仅线路变化不能覆盖显式退订。Part 正常销毁和构造失败收尾则同时清理订阅集合及临时信号，避免已移除对象被缓存引用继续通知。公共清理方法不依赖车辆种类，其他宿主可以使用同一入口。

## 5. 兼容性重点

### 5.1 同时输出 local 与 global

第一方 AE86 部件的 `subsystem.machine_max.car_controller.speed_outputs.vehicle_speed` 配置为 `["local", "global"]`，文件为 [ae86_chassis.json](../../../src/main/resources/spark_modules/Machine-Max_Civilian/machine_max/parts/ae86/ae86_chassis.json)。同一对象还将油门、制动、转向和手刹写往两个地址。

当前两个目标分别保存 Part／装配体的值；Part 具有广播后，位于当前 Part 且订阅该频道的子系统可经两条路径收到通知。对于读取最新值的消费者，两次写入相同原始发送者的同一槽只有一个最终值，不累加次数，也不产生两份输入。现有双目标状态输出可以保留。

握手本身使用只读查询，没有 `onSignalUpdated` 通知；同次查询已按对象身份和到达频道去重。本轮不增加发送事件标识、跨线路去重上下文或信号计数，也不因双目标状态输出迁移全部内容接线。

R11 是“所有消费者都只读值”的实际例外：即时交互可以在回调中执行动作。交互区当前走显式目标顺序及 `CONSUME` 返回链，Part 广播不承担多目标交互调度；本轮不为将来可能广播的交互事件建立全局去重机制。若后续有实际的非幂等广播消费者，再按其业务契约处理。

本次在排除 `docs` 的第一方内容 JSON 中做文本扫描，发现 17 个文件、119 行包含字面量 `"local"`。这只是审查范围统计，不代表 119 条实际广播、订阅命中或已发生错误，也不是验收基线。实施应按完整目标列表及实际频道逐项分类。

### 5.2 广播与环路

只读查询已有访问集合。真实广播的 Part 成员仅含当前子系统，不包含自己、其他总线和端口，因此局部总线没有自动向其他总线或端口递归广播的成员关系。保持 §3.1 的成员边界即可，本轮不设计任意消息环路处理器。

业务回调主动重新发送信号时，按现有信号接线规则处理；不把这种业务逻辑当作总线自动转发。状态写入允许重复，也不能用永久记住上次值的办法吞掉后续合法输入。

### 5.3 公共基类与下游

`RegularPart` 的继承入口为 `common/mech/vehicle/RegularPart.java#RegularPart`；ARMS-Core 的 `../ARMS-Core/src/main/java/io/github/sweetzonzi/arms_core/common/part/BodyPart.java#BodyPart` 也继承公共基类。因此能力应落在 `AbstractPart`，局部总线不能硬编码为依赖 `VehicleCore` 或仅支持 RegularPart。

`ISignalBus` 还继承 `ISignalSender`，其目标表默认空、`getSubPart` 默认空值。Part 不需要伪造一个 SubPart 或配置输出线路才能广播；现有来源身份、排序和 `instanceof ISignalSender` 的使用点需要一起复核。当前两个 Part 子类没有自定义这些发送方法，源码层面未发现直接方法冲突；本结论没有经过编译验证。

## 6. 实施步骤

| 步骤 | 状态 | 主要落点 | 完成条件 |
| --- | --- | --- | --- |
| P1 | 待实施 | `AbstractPart` | 实现现有 `ISignalBus`；独立集合及三个订阅方法可用，local 退订不改变物理归属或 global 订阅 |
| P2 | 待实施 | `AbstractPart#createSubsystems`、`SubPart#destroy` 及 Part 回收入口 | 默认订阅、正常退订、失败退订完整接入；Part 转移到另一装配体时保留自身订阅 |
| P3 | 待实施 | `AbstractPart`、信号接口说明 | 到达处理更新 Part 存储并实际广播；原始发送者保留；允许路由进入 local，成员按声明筛选；现有查询自动按总线展开 |
| P4 | 待实施 | `AbstractPart`、`VehicleCore#clearSignalStorage` | 公共信号清理方法覆盖两份容器；结构变化保持 local 订阅，销毁／失败收尾释放全部本地状态 |
| P5 | 待实施 | 既有信号测试、内容说明及下游编译 | §7 契约验证有结果；local 与 global 范围、订阅和配置说明与实际实现一致 |

`ISignalBus` 的方法形状保持现有契约，广播继续复用其默认实现；公共发送接口不承载新的事件 ID 或投递上下文。只读查询的总线分支复用 R2，必要修改以接口通用注释和直接相关契约为限。

Part 没有主动配置首跳的需求，沿用 `ISignalBus` 的空目标表；不为满足发送者接口伪造所属 SubPart。既有普通名称解析范围、全车订阅重建和消息通知策略都保持 §2 所述基线。

## 7. 验收契约与验证安排

| 编号 | 场景 | 必须成立的结果 |
| --- | --- | --- |
| V1 | 两个 Part 配置相同频道 | local 的发现与实际广播只命中所属 Part 的订阅者；global 保持全装配体范围 |
| V2 | 一个 Part 中的不同 SubPart | local 可发现并通知另一 SubPart 的订阅者；未声明频道的对象被排除 |
| V3 | 执行只读发现 | 不创建输入槽、不写两级键值存储、不触发 `onSignalUpdated`；现有查询的环路终止与去重成立 |
| V4 | 真实送达及端口转译 | Part 存储更新；订阅者在实际到达频道按原始发送者分槽，回调来源不是 Part |
| V5 | local 退订与重新订阅 | 退订后查询和广播都排除该对象，物理归属及 global 订阅保持；重新订阅后恢复；重复订退没有额外效果 |
| V6 | 子结构销毁、完整恢复、构造失败回收 | 已退出对象不能留在 local 订阅中；失败回收不触发正常库存掉落 |
| V7 | 信号清理与装配体结构变化 | 输入频道和 Part 键值存储均清空；显式 local 退订状态保留；global 按当前装配体归属重建 |
| V8 | 同次状态输出指向 local 与 global | 相同来源仍只有一个状态槽，独立来源各自保留；不要求回调只发生一次 |
| V9 | 公共基类在 RegularPart／BodyPart 中使用 | 不依赖 `VehicleCore` 才能订阅或本地广播；两个项目的公共接口调用均可编译 |

逻辑查询沿用 `src/test/java/io/github/sweetzonzi/machine_max/mech/signal/SignalTargetResolutionTest.java#SignalTargetResolutionTest` 的契约；真实 Part 的构造、销毁和结构变化在 R10 的 `SignalPipelineGameTest` 等既有 GameTest 夹具中验证，实施时只增加覆盖本轮行为的必要用例。具体测试编号与运行结果写在本节，不能用编译成功代替生命周期和广播验证。

实施时从各自仓库根使用离线 wrapper。Machine-Max 至少编译主源码并执行选定的信号契约用例；公共基类的源码兼容同时编译 ARMS-Core。可使用的入口如下，GameTest 采用仓库现有服务端任务：

```powershell
# Machine-Max
.\gradlew --offline classes
.\gradlew --offline test --tests '*SignalTargetResolutionTest'
.\gradlew --offline runGameTestServer

# ARMS-Core
.\gradlew --offline classes
```

验证安排属于独立信号系统工作，不扩展 [AmmoBelt 第一阶段计划](武器系统-AmmoBelt与发射器装填第一阶段重构计划.md) §9.2 的仅编译门槛。本次交付只整理实施计划、更新引用并检查文档，不修改生产代码，不运行编译或游戏；P1—P5 均无实施或验证结果。
