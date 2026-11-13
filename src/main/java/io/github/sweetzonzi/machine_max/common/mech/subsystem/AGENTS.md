# subsystem/ — 子系统实现

**范围**: 22 种子系统、属性体系、基类层级。

## 结构

```
subsystem/
├── AbstractSubsystem.java              # 基类：耐久度、信号、能源、保存/加载
├── AbstractControllableSubsystem.java  # 增加玩家控制输入处理
├── BasicSubsystem.java                 # 最小实现（仅可破坏）
├── SubsystemController.java            # 驱动所有子系统的 tick，管理 EnergyGrid
├── 接口
│   ├── ISubsystemHost.java             # 子系统宿主接口
│   ├── IAmmoConsumer.java              # 弹药消耗接口
│   ├── IAmmoSupplier.java              # 弹药供给接口
│   └── ITorqueProvider.java            # 扭矩提供接口
├── attr/
│   ├── SubsystemTypes.java             # 22 种类型枚举
│   ├── WorkingState.java               # 工作状态枚举
│   ├── static_attr/                    # 23 个 JSON 驱动静态属性
│   └── dynamic_attr/                   # 22 个运行时可变属性
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
│   ├── LauncherSubsystem.java          # 投射物发射器（炮闩/导弹架/火箭管），持有装填作业
│   ├── ReloadJob.java                  # 一条装填作业的身份、冻结分配与进度，归 Launcher
│   ├── ReloadJobKey.java               # 作业身份：(宿主 SubPart ID, 发射器名, jobId)
│   ├── AmmoLoaderSubsystem.java        # 自动装弹机：放行条件、占用、受理与交付
│   ├── RegenAmmoRackSubsystem.java     # 再生弹药架：数字库存、弹药预留与自身再生
│   └── AmmoRackSubsystem.java          # 物品弹药架：物品容器（往返 WIP）
│
├── 功能与存储
│   ├── ItemStorageSubsystem.java       # 物品存储容器
│   ├── LightingSubsystem.java          # 客户端体积光
│   └── ScriptableSubsystem.java        # JavaScript 驱动逻辑
│
└── 注意：SignalConvertSubsystem 已在 SubsystemTypes 中枚举，但尚无实现文件
    （只有 SignalConvertSubsystemStaticAttr.java 和 SignalConvertSubsystemAttr.java）
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增子系统类型 | `AbstractSubsystem.java` | 继承，注册到 SubsystemTypes |
| 修复引擎行为 | `EngineSubsystem.java` | RPM 扭矩曲线、声音状态 |
| 修复电动机 | `MotorSubsystem.java` | coupleTorque=0（已知 Bug） |
| 修复变速箱 | `GearboxSubsystem.java` | 自动换挡、减速比逻辑 |
| 修复载具控制 | `CarControllerSubsystem.java` | 油门、转向、制动 PID |
| 修复座位交互 | `SeatSubsystem.java` | 乘客登乘、输入信号 |
| 修复电池 | `BatterySubsystem.java` | 充放电、网格集成 |
| 修复发射器 | `LauncherSubsystem.java` | 弹药消耗、投射物类型 |
| 修复装弹机 | `AmmoLoaderSubsystem.java` | 放行条件、占用、受理与交付；计时不在这里 |
| 修复弹药架 | `RegenAmmoRackSubsystem.java` | 库存、预留、再生与库存殉爆 |
| 新增静态属性 | `attr/static_attr/` | JSON 编解码，按类型定义 |
| 新增动态属性 | `attr/dynamic_attr/` | 运行时可变状态 |

## 约定

- **继承链**：`AbstractSubsystem` → `AbstractControllableSubsystem` → 具体类型。
- **信号 I/O**：全部实现 `ISignalReceiver`/`ISignalSender`。通道为 `ConcurrentMap<String, SignalChannel>`；寻址名由 `getSignalAddress()` 提供（子系统名），保留目标名 `"local"` = Part、`"global"` = 装配体。默认输出目标（`*_outputs`）应写 `["local","global"]` 或 `["global"]`。
- **武器开火控制频道命名**：发射器 `control_inputs` 遵循「类型 + 可选型号」——基础频道 `weapon.<class>`（如 `weapon.mg` 机枪、`weapon.autocannon` 机关炮、`weapon.gun` 直射火炮），可选型号频道 `weapon.<class>.<model>`（如 `weapon.autocannon.2a42`），用于专用火控计算机；列表顺序即优先级。主/副武器角色由座位控制组 `mainWeaponTargets`/`secondaryWeaponTargets` 布线决定，不在频道名中体现。weapon_controller 的 `control_outputs` 采用「分裂频道」：保留 turret_driver 瞄准频道，另加类型开火频道（避免和炮塔瞄准绑定耦合）。
- **开火信号按语义判定**：主/副武器开火指令分别发到控制组 `main_weapon_targets`/`secondary_weapon_targets` 声明的频道（内容包里是 `fire_input_*`），弹种切换指令（`NEXT_AMMO_TYPE`/`PREV_AMMO_TYPE`）也走主武器那一组，三者共用同一批线路。`WeaponControllerSubsystem.readInputSignals()` 因此按信号语义收集开火：主/副武器开火指令、值为 `true` 的 Boolean、非 0 的数值算开火；弹种指令、非开火语义的 String、`EmptySignal` 不算。按"频道非空"收集会让按一次切换弹种键就打出一发。
- **开火归属**：`WeaponControllerSubsystem.operator` 在读开火信号时按发送者解析——发送者是 `SeatSubsystem` 就取它的 `passenger`，与载具控制器 `AbstractGroundControllerSubsystem.controller` 同一口径；松开开火键、无开火信号时清空。它随 `LauncherSubsystem.FireCommand#shooter` 下发，成为每发弹丸的 owner（见 `common/mech/projectile/AGENTS.md` 的「伤害归属」）。客户端恒为 null：`passenger` 只有服务端有值，归属只在服务端使用。
- **殉爆归属**：起爆定位、穿透豁免与归属共用一个事件来源 `AbstractSubsystem#getCookoffDamageEvent()`（基类读子系统级 `lastDamageEvent`；`LauncherSubsystem` 读炮闩模块、`RegenAmmoRackSubsystem` 与 `AmmoRackSubsystem` 沿用基类的子系统级记录），归属取该事件上下文里的 causing entity（`getLastDamageAttacker()`）。殉爆因此记在"打坏弹药架的那一击"头上，并沿伤害链继续传递。不要为殉爆另存一份攻击者字段——伤害上下文早已记录完整 `BFDamageContext`。
- **功率流**：`Engine/Motor` (IMechPowerProducer) → `Gearbox` → `Transmission` → `WheelDriver/JointDriver`，通过连接器的 MechPowerPort 传递。
- **能源网格**：`BatterySubsystem` 同时实现 `IEnergyProducer` 和 `IEnergyConsumer`。`EnergyGrid` 每 tick 平衡。
- **保存/加载**：在 `AbstractSubsystem` 中重写 `saveData()`/`loadData()` 进行 NBT 持久化。
- **线程**：`onCollideWithBlock()` 在物理线程调用；`onInteract()` 在主线程调用。
- **结构变化两阶段**：`SubsystemController.onVehicleStructureChanged()` 先对所有子系统调用 `onVehicleStructurePreChanged()`，再统一调用 `onVehicleStructureChanged()`。**清空拓扑缓存写第一阶段，重新发现写第二阶段**——`allSubsystems` 是集合，遍历顺序不确定；两者挤在同一趟里时，后清空的子系统会把先发现的结果抹掉（发射器清供给者登记表、装弹机重新登记发现就属于这一对）。
- **装填重构实施边界**：下列弹药指认、取弹与换装的现状描述以 P4 双端闭环（作业身份、实际架预留、双端本地推进与本地交付、结束结果，见主计划 §10.1 的 S19／S20）为基线；主计划 §3.4—§3.6、§5 与 §10.1 的 S13—S20 是这些契约的出处：服务端分配并预留，两端本地推进计时与弹药内容，字段按最新绝对值下发，客户端在 tick 收尾统一应用。仍欠：人物占用与人工段、物品架预留。
- **弹药指认与库存服务端独占**：`WeaponControllerSubsystem` 的弹药路由只在服务端执行，客户端经发射器上的供给者身份同步值跟随（`IAmmoSupplier.SupplierRef` = 宿主 SubPart 的 int ID + 子系统名，两端取服务端创建数据里的同一个 ID）。分配、占用与预留只在服务端决定：`LauncherSubsystem#tickReload()` 推进自己的 `ReloadJob`，走完后 `consumeReadyBelt()` 兑现预留；客户端按同一份已发布分配自行推进计时、走完后用冻结的规格与份额生成本地交付并本地扣弹，但不扣实际库存、不建预留、不生成投射物。
- **装填计时归消费者**：进度记在 `LauncherSubsystem` 持有的 `ReloadJob`（身份、交付目标、实际来源、冻结规格、预留份额、已推进 tick、本阶段时长），身份由 `ReloadJobKey` 承载，`jobId` 在受理成功时自增（等待重试不消耗编号）；时长在开工时取自供给者的 `getReloadTimeTicks()` 并定死。供给者只回答 `getAdvanceTicks(consumer)`（本 tick 能推进多少，机构不可工作、未受理或预留失效时是 `0`）并在作业走完后兑现一份；它不持有针对任何消费者的计时器，音效游标是客户端本地量（`AmmoLoaderSubsystem#tickStageSounds`），既不下行也不存档。`JOB_ELAPSED_ID` 是进度基线而不是进度镜像：服务端只在作业开始、离散重建与全量补推时写一次，稳定装填期间不写。
- **独占绑定与实际占用**：型号顶层 `can_supply_multiple=false` 的机构同一时刻最多服务一条作业，`true` 允许并行。占用是机构自己的 `Map<IAmmoConsumer, ReloadJobKey>`，只有受理点 `claimFor(consumer, key)` 写入，并在同一次调用里取得实际架的整次作业预留（返回 `Grant(origin, spec, q)`）：路由指认只提供目标提示，未受理的请求不入表、不占槽、不占预留、不开始计时。`releaseClaim(consumer, key)` 只按身份匹配删除占用，并按身份撤销来源架上的预留，旧作业的回调因此摘不掉新作业的占用与份额；路由候选过滤（`isClaimedByOther`）与执行者放行（`getAdvanceTicks`／`consumeReadyBelt`）读同一份记录。仅独占机构把当前占用编码成 `(launcherSubPartId, launcherName, jobId)` 拥有者下行，客户端据此建立占用镜像；并发机构没有独占槽位，该字段不下发。占用、预留与作业身份都不持久化。
- **实际架预留**：`RegenAmmoRackSubsystem` 维护 `Map<ReloadJobKey, Integer>` 与已预留总量 `R`（同步值）。可分配库存是 `N - R`，`reserve` 取整次作业的 `q = min(可分配, 规格长度)`，`deliverReserved` 按身份扣 `N` 并交出实例（一次兑现、不重复扣），`releaseReservation` 只减承诺、不补弹；非交付原因减少 `N` 时按 `ReloadJobKey` 元组升序排列预留键、再从最大键起整笔撤销，维持 `R ≤ N`。`R` 已含本作业份额，显示只算 `N - R`。
- **失效占用与预留兜底**：服务端主线程每次读取／受理前先剔除失效占用，按不存在处理：消费者已摧毁或失活、它的指认已不指向本机、当前作业缺失或身份对不上；剔除时同一轮撤销该作业的预留。合法暂停（机构受阻、供能不足）不影响占用——作业与身份都还在，恢复后接着推进。清理只删承诺，不改库存、不补弹。整份承诺消失（占用或预留被撤销）时 `holdsCommitment` 返回 false，消费者按统一收尾结束作业，不等暂停超时。
- **作业读口与统一收尾**：`IAmmoConsumer#getReloadJob()` 是对 Launcher 当前唯一作业的只读口，机构放行／交付／占用校验与人员附件都经由它读同一份作业，不强转 Launcher、不另存分配记录；`ReloadJob` 直接承载身份、冻结分配与执行状态，不另造分配快照。资源结束走 `LauncherSubsystem#finishReloadJob(delivered)`：先按身份释放机构占用与实际架预留（人物占用待人员附件接入），再发布带原 `jobId` 的已交付／未交付结束结果并清作业；成功交付、取消／换人、摧毁、停用（经失效占用惰性清理）、暂停超时与拓扑转移都归这一口，重复收尾无效果，`onDestroyed` 同一路径因此不存在"清进度但承诺还在"的窗口。
- **保持与换人只有一条判据**：候选必须严格更早拿到下一份交付（`LauncherSubsystem#ticksToNextRound(IAmmoSupplier)`；已备好一份是 `0`，弹仓为空是 `Integer.MAX_VALUE`），或者当前供给者已不合法（机构坏了、交付内容不被炮闩接受、不在选中弹链上）才当场换人。打空但场上没有更好候选时保持绑定，由它回充后继续供弹。
- **交付单位是一份 `AmmoBelt`**：供给者用 `getDeliverySpec()` 预览下一份的满额规格，用 `consumeReadyBelt()` 兑现作业预留并交出独立实例——发数是该作业的预留份额 `q = min(可分配库存, 规格长度)`，所以实例余量可以小于规格，规格本身（模式、长度、标签、回充）不变，发射器据此仍认得出是同一份弹链；用 `returnBelt()` 全收或全拒地回收。一份付一次机械段的 `reload_time`（作业时长），装几发由消费者的 `single_round_loading` 与本次预留共同决定，与供给者类型无关；没有对接状态，也没有逐发／整匣两种交付模式。换装同样没有独立状态：已装规格与当前供给者的 `getDeliverySpec()` 不一致即在途，它与首次装填共用同一条作业，走完后发射器先取新的一份，再把旧的一份交给当前合法且接受它的机器（无人接收则丢弃）。
- **双端字段与 tick 收尾边界**：发射器经同一张同步表下发作业的编号、交付目标、实际来源、冻结规格、预留份额、时长与进度基线；客户端的同步回调只记"哪几项变了"，由 `AbstractSubsystem#onPostTick()`（`SubsystemController#postTick()` 在 `VehicleCore#preTick()` 末尾统一调用）按"指认 → 作业 → 弹药内容"的顺序落地，因此一次收到的多项变化不会在模拟中途被逐项应用。服务端在 `pushAllTo` 前调用 `refreshSyncBaseline()` 把只在离散时刻写过的值刷成当前值，新加入的客户端因此从"现在"开始模拟。
- **交付结果的先后顺序**：结束结果保留原 `jobId` 及已交付／未交付结论（`JOB_RESULT_ID`／`JOB_RESULT_DELIVERED_ID`），在 tick 收尾先于作业字段应用；终态副作用只影响身份匹配的本地作业——编号等于当前本地作业，或等于刚才本地交付过的那一条——旧结果不能结束或释放新作业，库存／预留仍按最新值校准。取消、200 tick 超时、库存撤销及失效终止统一下发未交付结束，不只清资源。本地交付照常发生：客户端走完作业后按冻结规格与份额生成内容并本地扣弹、补膛，终态到达时再按权威的 `BELT_ID`／`CHAMBERED_ID` 收口，因此被撤销的作业不会在本机留下一份多出来的弹。客户端不预测架上库存：`N` 与 `R` 跟随权威值（两者同减 `q`，`N - R` 不变）。预留在首个阶段（含人工段）前为整次作业取得一次，执行者按阶段切换；强制外部减库存时，把预留键 `(subPartId, launcherName, jobId)` 按整数／`String.compareTo` 元组升序排列，再从最大键起降序撤销整份预留，保留份额不静默缩小（`RegenAmmoRackSubsystem#trimReservationsToStock` 已按此实现）。
- **弹链选择随服务端同步**：`WeaponControllerSubsystem` 的 `selectedBelt` 只在服务端改选，经同步值 `SELECTED_BELT` 推给客户端；值是**交付规格本身**（模式、长度、标签与是否回充），不是池内下标——结构变化会改变弹链排序，规格只随弹链本身变化。客户端收到时池里可能还没有这条链（装弹机发现未完成），因此先暂存、建池后再落地，不因一次先到的同步值丢掉选择。
- **同步只表达变化，初值靠补推**：`SynchedEntityData` 的变化广播（`packDirty()`）只在值真的变过时发得出去，且发给维度内所有玩家。**载入后不再变化的值（如弹药指认）走不到这条路**，因此载具在客户端建好时必须补一份全量快照：`SubsystemController.pushAllTo(ServerPlayer)`，由 `ObjectManager` 在每条载具创建包之后**紧跟着**调用（`pushSubsystemState`）。顺序由同一条连接保证，客户端在创建包的处理器内就建好了载具，因此补推包必然落在目标已存在的时刻。
- **全量补推不得改动同步状态**：快照取自 `AbstractSubsystem.forEachSyncedValue()`（只读遍历 `SynchedEntityData.itemsById`，该字段由 `META-INF/accesstransformer.cfg` 放宽为 public），经 `PacketDistributor.sendToPlayer` **定向**发送。不得改用 `SynchedEntityData.set(..., force=true)` 强制标脏：那会把脏标记留给下一次 `packDirty()`，让维度内其他玩家收到无谓的包。
- **自定义同步数据序列化器**：注册在 `MMEntityDataSerializers`（NeoForge 的 `ENTITY_DATA_SERIALIZERS` 注册表，线号为 `256 + 注册表内 id`）。`SynchedEntityData.defineId` 在子系统静态初始化里执行，因此注册文件必须持有序列化器**实例**、注册时交出同一实例，不能从 `DeferredHolder` 取。

## 反模式

- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **严禁忽略 `coupleTorque` 禁用**：MotorSubsystem 硬编码为 0（振荡问题）。未修复根因前不得重新启用。
- **严禁子系统缺少 static_attr**：每个子系统类型都需要对应的 JSON 加载用静态属性类。
- **严禁在遍历中修改 `allSubsystems`**：它是 `CopyOnWriteArraySet` — 修改安全但开销大。
