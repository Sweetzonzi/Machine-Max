# subsystem/ — 子系统实现

**范围**: 22 种已开放子系统类型（另有 3 个开发中未登记类型、1 个未登记占位）、属性体系、基类层级。

## 结构

```
subsystem/
├── AbstractSubsystem.java              # 基类：耐久度、信号、能源、保存/加载
├── AbstractControllableSubsystem.java  # 增加玩家控制输入处理
├── BasicSubsystem.java                 # 最小实现（仅可破坏）
├── SubsystemController.java            # 驱动所有子系统的 tick，管理 EnergyGrid
├── 接口
│   ├── ISubsystemHost.java             # 子系统宿主接口
│   ├── IAmmoConsumer.java              # 弹药接收者：作业、发现与路由
│   ├── IAmmoSupplier.java              # 弹药提供者：库存、预留、交付
│   ├── IAmmoTransporter.java           # 弹药运输者：本段时长、执行资源、推进量
│   └── ITorqueProvider.java            # 扭矩提供接口
├── attr/
│   ├── SubsystemKinds.java             # 类型登记表：22 条已开放登记、下游登记入口、封表
│   ├── SubsystemCodecs.java            # 安装属性与静态属性的分派 Codec
│   ├── WorkingState.java               # 工作状态枚举
│   ├── static_attr/                    # 型号 JSON 驱动静态属性（每个已登记类型一份）
│   └── dynamic_attr/                   # 运行时动态属性（与静态属性一一配对）
│
├── 动力与传动
│   ├── EngineSubsystem.java            # 内燃机，RPM 扭矩曲线
│   ├── MotorSubsystem.java             # 电动机（coupleTorque=0，暂禁用）
│   ├── GearboxSubsystem.java           # 多级变速箱
│   ├── TransmissionSubsystem.java      # 分动箱/差速器
│   └── BatterySubsystem.java           # 电池储能
│
├── 驱动与执行
│   ├── WheelDriverSubsystem.java       # 车轮驱动 + 转向伺服
│   ├── JointDriverSubsystem.java       # 通用关节作动器
│   └── TurretDriverSubsystem.java      # 方位/俯仰伺服
│
├── 控制与交互
│   ├── CarControllerSubsystem.java     # 车辆控制输入解释
│   ├── MotorbikeControllerSubsystem.java # 摩托车控制
│   ├── WeaponControllerSubsystem.java  # 目标锁定 + 开火指令
│   ├── SeatSubsystem.java              # 乘客座位，输入路由
│   └── CameraSubsystem.java            # 车载摄像头
│     └── SightSubsystem.java           # 瞄准镜（继承 CameraSubsystem）
│
├── 武器系统
│   ├── LauncherSubsystem.java          # 投射物发射器（炮闩/导弹架/火箭管），接收者：作业与路由
│   ├── ReloadJob.java                  # 一条装填作业的身份、冻结分配、阶段与进度，归 Launcher
│   ├── ReloadJobKey.java               # 作业身份：(宿主 SubPart ID, 发射器序号, jobId)
│   ├── AmmoRef.java                    # 弹药端身份：宿主 SubPart ID + 子系统声明序序号
│   ├── AmmoPath.java                   # 一条路：运输者 + 提供者
│   ├── ReloadCrewSelection.java        # 人工段选人的纯比较（取最小、三层决胜）
│   ├── LoaderRoster.java               # 人力运输者与用人：座位发现、认领与释放、时间倍率
│   ├── ReloadSoundMarkers.java         # 进度音效表的纯计算：阈值解析、区间越过、固定阈值上的标记
│   ├── AmmoLoaderSubsystem.java        # 自动装弹机：机械运输者（放行条件、占用、人工准备段）
│   ├── RegenAmmoRackSubsystem.java     # 再生弹药架：提供者（数字库存、弹药预留与自身再生）
│   └── AmmoRackSubsystem.java          # 物品弹药架：物品容器（往返 WIP）
│
├── 功能与存储
│   ├── ItemStorageSubsystem.java       # 物品存储容器
│   ├── LightingSubsystem.java          # 客户端体积光
│   └── ScriptableSubsystem.java        # JavaScript 驱动逻辑
│
└── 注意：SignalConvertSubsystem 没有登记为类型（只有 SignalConvertSubsystemStaticAttr.java
    与 SignalConvertSubsystemAttr.java，两侧 codec() 返回 null），内容包引用不到它
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增子系统类型 | `attr/SubsystemKinds.java` | 继承 AbstractSubsystem，模组初始化期登记安装与静态两个 Codec |
| 修复引擎行为 | `EngineSubsystem.java` | RPM 扭矩曲线、声音状态 |
| 修复电动机 | `MotorSubsystem.java` | coupleTorque=0（已知 Bug） |
| 修复变速箱 | `GearboxSubsystem.java` | 自动换挡、减速比逻辑 |
| 修复载具控制 | `CarControllerSubsystem.java` | 油门、转向、制动 PID |
| 修复座位交互 | `SeatSubsystem.java` | 乘客登乘、输入信号 |
| 修复电池 | `BatterySubsystem.java` | 充放电、网格集成 |
| 修复发射器 | `LauncherSubsystem.java` | 弹药消耗、投射物类型 |
| 修复装弹机（机械运输者） | `AmmoLoaderSubsystem.java` | 执行资源、放行条件与人工准备段；计时与交付不在这里 |
| 修复人力直取（没有装弹机的手工装填） | `LauncherSubsystem.java` | 发射器型号的 `human_reload_time`、`loader_*` 用人表与人工段音效 |
| 修复弹药架（提供者） | `RegenAmmoRackSubsystem.java` | 库存、预留、交付与再生、库存殉爆 |
| 新增静态属性 | `attr/static_attr/` | JSON 编解码，按类型定义 |
| 新增动态属性 | `attr/dynamic_attr/` | 运行时可变状态 |

## 约定

- **类型登记在模组初始化期，内容包装载开始时封表**：一条 `SubsystemKinds.Kind` 同时给出该类型的安装属性 Codec 与静态属性 Codec，两侧配对由登记项本身保证。内建 22 条随 `SubsystemKinds` 类初始化完成；下游在自己的模组初始化阶段调用 `SubsystemKinds.register(id, new SubsystemKinds.Kind(安装Codec, 静态Codec))`。`SubsystemModule.onStart` 首次执行时封表，此后的登记抛异常。
- **开发中的类型不登记**：`joint_driver`、`scriptable`、`battery` 的字段契约仍在定稿（属性类标 `@ApiStatus.Experimental`），`SubsystemKinds` 里对应的 `register` 被注释掉，解码按「未知类型」拒绝；schema 也不提供这三个类型。契约定稿后取消注释即可，两侧 Codec 与登记结构不用改。
- **类型身份由登记项派生**：`getType()` 是基类的 final 方法，按 `codec()` 的实例身份反查登记 ID；具体属性类只声明自己的 `CODEC` 单例与 `createSubsystem` 工厂，不声明类型常量、也不覆写 `getType`。型号绑定比较两侧派生 ID 的值，发生在安装属性的解码构造器里（`getStaticAttr`）。
- **继承链**：`AbstractSubsystem` → `AbstractControllableSubsystem` → 具体类型。
- **信号 I/O**：全部实现 `ISignalReceiver`/`ISignalSender`。通道为 `ConcurrentMap<String, SignalChannel>`；寻址名由 `getSignalAddress()` 提供（子系统名），保留目标名 `"local"` = Part、`"global"` = 装配体。默认输出目标（`*_outputs`）应写 `["local","global"]` 或 `["global"]`。
- **武器开火控制频道命名**：发射器 `control_inputs` 遵循「类型 + 可选型号」——基础频道 `weapon.<class>`（如 `weapon.mg` 机枪、`weapon.autocannon` 机关炮、`weapon.gun` 直射火炮），可选型号频道 `weapon.<class>.<model>`（如 `weapon.autocannon.2a42`），用于专用火控计算机；列表顺序即优先级。主/副武器角色由座位控制组 `mainWeaponTargets`/`secondaryWeaponTargets` 布线决定，不在频道名中体现。weapon_controller 的 `control_outputs` 采用「分裂频道」：保留 turret_driver 瞄准频道，另加类型开火频道（避免和炮塔瞄准绑定耦合）。
- **开火信号按语义判定**：主/副武器开火指令分别发到控制组 `main_weapon_targets`/`secondary_weapon_targets` 声明的频道（岗位频道 `crew_<岗位>_fire` 与 `crew_<岗位>_secondary_fire`，设备频道再加设备词），弹种切换指令（`NEXT_AMMO_TYPE`/`PREV_AMMO_TYPE`）也走主武器那一组，三者共用同一批线路。`WeaponControllerSubsystem.readInputSignals()` 因此按信号语义收集开火：主/副武器开火指令、值为 `true` 的 Boolean、非 0 的数值算开火；弹种指令、非开火语义的 String、`EmptySignal` 不算。按"频道非空"收集会让按一次切换弹种键就打出一发。
- **瞄准与开火按三键排序取一个来源**：`selectInputSlot` 扫完全部 `aim_inputs`／`fire_inputs` 频道，按"越级量（`Signal#priority`）降序 → 档位（列表长度 − 下标）降序 → `ISignalSender#compareSenders` 升序"选唯一生效的来源；标准化指令读取（`updateRegularInputs`，弹种切换与 hold 语义开火）走同一把尺子，否则低档频道里的一条松开会盖掉越级频道里的按下。列表顺序是**默认归属**（默认操作者在最前、接管方在后、`crew_any_*` 兜底），临时抢占靠越级量。全部越级量为 0 时与"按列表顺序取第一个有效信号"逐位等价。
- **开火归属**：`WeaponControllerSubsystem.operator` 在读开火信号时按发送者解析——发送者是 `SeatSubsystem` 就取它的 `passenger`，与载具控制器 `AbstractGroundControllerSubsystem.controller` 同一口径；松开开火键、无开火信号时清空。它随 `LauncherSubsystem.FireCommand#shooter` 下发，成为每发弹丸的 owner（见 `common/mech/projectile/AGENTS.md` 的「伤害归属」）。客户端恒为 null：`passenger` 只有服务端有值，归属只在服务端使用。
- **殉爆归属**：起爆定位、穿透豁免与归属共用一个事件来源 `AbstractSubsystem#getCookoffDamageEvent()`（基类读子系统级 `lastDamageEvent`；`LauncherSubsystem` 读炮闩模块、`RegenAmmoRackSubsystem` 与 `AmmoRackSubsystem` 沿用基类的子系统级记录），归属取该事件上下文里的 causing entity（`getLastDamageAttacker()`）。殉爆因此记在"打坏弹药架的那一击"头上，并沿伤害链继续传递。不要为殉爆另存一份攻击者字段——伤害上下文早已记录完整 `BFDamageContext`。
- **功率流**：`Engine/Motor` (IMechPowerProducer) → `Gearbox` → `Transmission` → `WheelDriver/JointDriver`，通过连接器的 MechPowerPort 传递。
- **能源网格**：`BatterySubsystem` 同时实现 `IEnergyProducer` 和 `IEnergyConsumer`。`EnergyGrid` 每 tick 平衡。
- **保存/加载**：在 `AbstractSubsystem` 中重写 `saveData()`/`loadData()` 进行 NBT 持久化。
- **命中钩子分两级**：`SubPart#settleAccumulatedDamage` 对每笔未被入口取消、未被履带吸收的命中先调 `AbstractSubsystem#onHit`，再在金额为正时调 `onHurt`。`onHit` 与金额无关，只表达"被命中了"，是"被打到就失效"这类判定的唯一入口（`ReactiveArmorSubsystem` 的阈值引爆）；金额、取消与耐久记账仍归 `onHurt`。`onHit` 默认空实现，不要在里面扣耐久——那会让零伤害与碰撞冲击变成磨损源。
- **爆反的参考实现**：`Machine-Max_Builtin` 的 `machine_max:reactive_armor_brick`（`subsystems/reactive_armor/reactive_armor_brick.json`）配材质 `machine_max:reactive_armor`（`materials/reactive_armor.json`）是这一机制的可抄写样例：子系统负责"进砖穿深达到 `trigger_penetration` 即失效"，材质把 `decay_rha` 写成与 `rha` 相同、让等效厚度恒定，并用加性两段（动能 −20、化学能再 −230）表达早期爆反的拦截；零件侧每块砖一个带 `condition` 与 `subsystem` 的命中箱。美系基础包的 `machine_max:arat_ii`（材质，动能 −30、化学能合计 −350）与 `machine_max:arat_ii_tile`（型号）是同形状的真实型号样例，M1A2 的 26 个框只引用这两个 id。
- **线程**：`onCollideWithBlock()` 在物理线程调用；`onInteract()` 在主线程调用。
- **结构变化两阶段**：`SubsystemController.onVehicleStructureChanged()` 先对所有子系统调用 `onVehicleStructurePreChanged()`，再统一调用 `onVehicleStructureChanged()`。**清空拓扑缓存写第一阶段，重新发现写第二阶段**——两者挤在同一趟里时，后清空的子系统会把先发现的结果抹掉（发射器清两张发现表、装弹机重新给出喂弹线就属于这一对）。
- **弹药三角色**：弹药侧分三个角色——接收者 `IAmmoConsumer`（`LauncherSubsystem`：作业、两张发现表、路由、终态收尾）、提供者 `IAmmoSupplier`（库存、可分配数量、整次作业预留、交付规格、兑现出的弹链、退弹；`RegenAmmoRackSubsystem` 是唯一实现，物品架随物品往返接入）、运输者 `IAmmoTransporter`（本段时长、执行资源、本 tick 推进量；`AmmoLoaderSubsystem` 机械、`LoaderRoster` 人力）。一条作业对应一条路 `AmmoPath(transporter, provider)`；路由两张发现表在路由时现算，不缓存。
- **身份按结构序号寻址**：`AmmoRef` = 宿主 SubPart ID + 子系统声明序序号（`SubPart#subsystemIndex`），与其它网络载荷的子系统寻址同一口径；`NONE` 在运输者位表示人力运输者、在提供者位表示没有提供者。作业身份 `ReloadJobKey` = 宿主 SubPart ID + 发射器序号 + `jobId`。名字仍是存档键与日志身份，不进这两个引用。`ofProvider`／`ofTransporter` 两个具名工厂各断言自己那一侧。
- **受理两步，都由接收者发起**：先向运输者取执行资源（`claim`：人力运输者认领乘员；无人工段的机械运输者取机械槽；有人工段的机械运输者在人工段里认领乘员，结束时 `claimForMachineStage` 补槽），再向提供者取整次作业的预留（`reserve(key)` → `Grant(spec, q)`）。任一步失败撤销先取得的那一份（`releaseClaim`），不建作业、不开始计时。提供者不认识接收者，预留与兑现只按作业身份记账。
- **撤销归一**：正常收尾只有接收者在写提供者的预留表——`finishReloadJob` 先 `transporter.releaseClaim`（乘员与机械槽）再 `provider.releaseReservation`。运输者的 `releaseClaim` 不碰预留；唯一例外是它的失效清理：消费者已被摧毁或停用、再没有机会收尾时按身份代撤销，否则那份预留会永久占住库存。
- **承诺分两半**：接收者每 tick 问两侧——运输者的 `holdsCommitment`（占用或乘员资源还在吗）与提供者的 `holdsReservation(key, q)`（那份预留还在吗），任一为假立刻按失效收尾，不等暂停上限。换人不属于承诺消失（人工段换人只换执行者，进度与预留保持）；占用不可抢占（独占运输者在受理时就拒绝别的消费者）。人员离场只释放认领并暂停，200 tick 未交付才收尾。
- **代价只有一个读口**：`IAmmoTransporter#estimateTicks(consumer, provider)` 回答"沿这条路还要多久拿到下一份"——人工段乘岗位倍率 `P` 与个人倍率 `A`、机械段按基准；作业已在本路上时报本阶段剩余（人工段再加整段机械时长）。接收者的 `ticksToNextRound(AmmoPath)` 只是转发，武器控制器的候选排序与换路判定都读它。估价是一次只读选人，同一次选路内同一条路只算一次（调用方的局部缓存，随调用丢弃）。
- **路由硬条件与换路**：候选必须同时满足四条——运输者可工作、提供者可工作、炮闩接受提供者的交付规格、运输者没被别人的作业占着；缺一不是"更差的候选"而是"不是候选"。当前路每 tick 单独判一次有效性（仍能由两张表合成、两者都可工作），不成立就统一收尾再丢指认。换路只在候选严格更早、或当前路已不合法时发生；缺弹等待只按本 tick 的可分配库存判定（`estimateTicks` 报 `Integer.MAX_VALUE`），再生时间不折算。
- **路由单位是路，不是机器**：退膛、换装旧份与当前弹种都只认当前路的提供者；去处两级——当前路的提供者 → 本装配体其它提供者（按 `getRoutes()` 逐台询问一次）→ 记警告并丢弃。弹链只有提供者能收，去处不经运输者转发，也没有第三级兜底。
- **双端同步与整对解析**：发射器下发两个引用 `ROUTE_TRANSPORTER`（`NONE` = 人力运输者）与 `ROUTE_PROVIDER`（`NONE` = 没有路由），客户端按整对解析与替换：任一解析失败就保留上一帧的整对，不产生"新运输者 + 旧提供者"的混合路。作业快照 `ReloadJobSync` 里同样是一对 `AmmoRef`（运输者、提供者）；作业上冻结的那一对是权威，路只是指认。
- **双端字段与 tick 收尾边界**：发射器把作业的编号、运输者、提供者、冻结规格、预留份额、时长与进度基线作为一个可选快照整包下发（`JOB_SYNC`，空值即没有作业），结束结果另占两项；客户端的同步回调只记"哪几项变了"，由 `AbstractSubsystem#onPostTick()` 按"路由 → 作业 → 弹药内容"的顺序落地，因此一次收到的多项变化不会在模拟中途被逐项应用。服务端在 `pushAllTo` 前调用 `refreshSyncBaseline()` 把只在离散时刻写过的值刷成当前值。
- **交付结果的先后顺序**：结束结果保留原 `jobId` 及已交付／未交付结论（`JOB_RESULT_ID`／`JOB_RESULT_DELIVERED_ID`），在 tick 收尾先于作业字段应用；终态副作用只影响身份匹配的本地作业——编号等于当前本地作业，或等于刚才本地交付过的那一条。取消、200 tick 超时、库存撤销及失效终止统一下发未交付结束。客户端不预测架上库存：`N` 与 `R` 跟随权威值。强制外部减库存时按 `ReloadJobKey` 元组升序从最大键起整笔撤销预留（`trimReservationsToStock`），保留份额不静默缩小。
- **弹药池是 HUD 索引**：`WeaponControllerSubsystem` 的池按交付规格分组，条目是"一条路 + 规格 + 实时数量"（`RouteEntry`），不含逐消费者的代价——池是控制器级共享的，存进去等于把某根管的估计贴到所有管上。路由不读池，它从 `launcher.getRoutes()` 取候选并现算代价。`selectedBelt` 只在服务端改选，经同步值下发**交付规格本身**（不是池内下标）；客户端收到时池可能还没建好，因此先暂存、建池后再落地。
- **同步只表达变化，初值靠补推**：`SynchedEntityData` 的变化广播（`packDirty()`）只在值真的变过时发得出去，且发给维度内所有玩家。**载入后不再变化的值（如弹药指认）走不到这条路**，因此载具在客户端建好时必须补一份全量快照：`SubsystemController.pushAllTo(UUID assemblyId, ServerPlayer)`，由 `ObjectManager` 在每条载具创建包之后**紧跟着**调用（`pushVehicleRuntimeState`）。顺序由同一条连接保证，客户端在创建包的处理器内就建好了载具，因此补推包必然落在目标已存在的时刻。
- **发送单元是控制器的一轮批次**：`SubsystemSyncPayload` 的头部是装配体身份、同步代次、递增批次序号、服务端采样步与 `full` 标记，条目仍按 `(子零件 ID, 声明序序号)` 寻址。服务端在两趟收尾（全部 `onPostTick`）之后由 `SubsystemController#flushSync(UUID)` 统一采样并一次发出——`AbstractSubsystem#packDirtyValues()` 只取脏值，不再各子系统各发一份包，因此靠后的收尾写进的值同样进入本轮批次。客户端把整包排进控制器的待应用队列，由 `postTick()` 开头按批次序 `assignValues` 落地：同一客户端 tick 收到多份批次仍然有序，且不会在业务模拟中途被逐项写入。代次在结构迁移时递增，客户端只记最新代次，换代次即作废已排队的批次；队列有界，超限丢空本域队列并等下一份完整样本。
- **全量补推不得改动同步状态**：快照取自 `AbstractSubsystem.forEachSyncedValue()`（只读遍历 `SynchedEntityData.itemsById`，该字段由 `META-INF/accesstransformer.cfg` 放宽为 public），经 `PacketDistributor.sendToPlayer` **定向**发送。不得改用 `SynchedEntityData.set(..., force=true)` 强制标脏：那会把脏标记留给下一次 `packDirty()`，让维度内其他玩家收到无谓的包。
- **自定义同步数据序列化器**：注册在 `MMEntityDataSerializers`（NeoForge 的 `ENTITY_DATA_SERIALIZERS` 注册表，线号为 `256 + 注册表内 id`）。`SynchedEntityData.defineId` 在子系统静态初始化里执行，因此注册文件必须持有序列化器**实例**、注册时交出同一实例，不能从 `DeferredHolder` 取。装填作业的整包快照走的就是这条路（`optional_reload_job_sync`，值为 `Optional<ReloadJobSync>`）。
- **武器链路插桩**：`WeaponTrace` 默认关闭，`/mm debug weaponlog start|stop`（权限级 4）切换。打开后每条记录形如 `[WDBG][S|C][t=<游戏时间>] 事件 字段=值`，端别与游戏时间由 `WeaponTrace.log` 补齐，事件文本由调用点给出。覆盖的事件：开火 `fire-begin`／`fire-shot`／`fire-end`／`projectile`，路由 `route`，装填 `reload-start`／`reload-stage`／`reload-retime`／`reload-pause`／`reload-stall`／`reload-deliver`／`reload-end`，客户端镜像 `reload-mirror`／`reload-mirror-stage`／`reload-mirror-clear`／`reload-local-deliver`／`reload-result`／`sync-belt`／`sync-chamber`，客户端表现 `fx-fire`，以及武器控制器的扳机门 `trigger-down`／`trigger-up`／`trigger-aim`／`trigger-noaim`。开关是进程内的静态量：单人游戏里客户端与服务端同进程，一条命令同时打开两端；专用服务器接独立客户端时，每一侧各自打开。关闭时不写日志，逐发路径上只多一次 volatile 读。

## 反模式

- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **严禁忽略 `coupleTorque` 禁用**：MotorSubsystem 硬编码为 0（振荡问题）。未修复根因前不得重新启用。
- **严禁子系统缺少 static_attr**：每个子系统类型都需要对应的 JSON 加载用静态属性类。
- **严禁直接改 `allSubsystems`**：它是控制器持有的 volatile 只读有序快照，成员变化只能经 `addSubsystems`／`removeSubsystems`／`discardUninitialized` 这些统一发布点整份替换。遍历方拿到的是调用时刻那一份，因此在遍历中新增或删除成员不会生效，也不会看到发布到一半的集合。
