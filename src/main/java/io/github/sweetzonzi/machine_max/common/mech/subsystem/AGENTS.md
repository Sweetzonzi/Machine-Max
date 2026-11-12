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
│   ├── LauncherSubsystem.java          # 投射物发射器（炮闩/导弹架/火箭管）
│   ├── AmmoLoaderSubsystem.java        # 自动装弹机：机械段计时、槽位、上游查询
│   ├── RegenAmmoRackSubsystem.java     # 再生弹药架：数字库存与自身再生
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
| 修复装弹机 | `AmmoLoaderSubsystem.java` | 机械段计时与上游查询 |
| 修复弹药架 | `RegenAmmoRackSubsystem.java` | 库存、再生与库存殉爆 |
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
- **弹药指认与库存服务端独占**：`WeaponControllerSubsystem` 的弹药路由只在服务端执行，客户端经发射器上的供给者身份同步值跟随（`IAmmoSupplier.SupplierRef` = 宿主 Part uuid + SubPart 名 + 子系统名，三者都随载具数据在两端的同一次会话内一致）。取弹链（`requestRound → isRoundReady → consumeReadyBelt`）只在服务端执行；客户端从发射器同步的膛内弹与已装 `AmmoBelt` 读取表现，库存计数只有服务端写。
- **独占绑定与认领**：`feed.can_supply_multiple=false` 的装弹机同一时刻最多服务一根活跃发射器。认领记在供给者上（`IAmmoSupplier#getBoundConsumers()`，只在主线程由服务端路由写入，不入存档也不同步）：路由为某根管选中机器时把该管加进去，`LauncherSubsystem#assignSupplier` 释放旧绑定时把自己摘出来。候选过滤直接排除"已被别的活跃管认领"的机器，而不是把它排到末位——摊不开时那根管如实显示无供给者。认领有效 = 持有者未摧毁未失能且它的 `currentSupplier` 仍指向本机；读档后认领为空，第一次路由按名字序重建，因此旧存档里两根管指向同一台机器会在这一 tick 收敛成一条。
- **保持与换人只有一条判据**：候选必须严格更早拿到下一份交付（`IAmmoSupplier#getTicksToNextRound`；已备好一份是 `0`，弹仓为空是 `Integer.MAX_VALUE`），或者当前供给者已不合法（机构坏了、交付内容不被炮闩接受、不在选中弹链上）才当场换人。打空但场上没有更好候选时保持绑定，由它回充后继续供弹。
- **交付单位是一份 `AmmoBelt`**：供给者用 `getDeliverySpec()` 预览下一份的满额规格，用 `consumeReadyBelt()` 交付一份独立实例——发数是 `min(库存, 规格长度)`，所以实例余量可以小于规格，规格本身（模式、长度、标签、回充）不变，发射器据此仍认得出是同一份弹链；用 `returnBelt()` 全收或全拒地回收。一份付一次 `feed.reload_time`，装几发由消费者的 `single_round_loading` 与源的实际库存共同决定，与供给者类型无关；没有对接状态，也没有逐发／整匣两种交付模式。换装同样没有独立状态：已装规格与当前供给者的 `getDeliverySpec()` 不一致即在途，供给者按自己的 `reload_time` 备货，备好后发射器先取新的一份再 `returnBelt()` 还回旧的那一份，任一步失败整笔回滚。发射器不排自己的换装计时。
- **弹链选择随服务端同步**：`WeaponControllerSubsystem` 的 `selectedBelt` 只在服务端改选，经同步值 `SELECTED_BELT` 推给客户端；值是**交付规格本身**（模式、长度、标签与是否回充），不是池内下标——结构变化会改变弹链排序，规格只随弹链本身变化。客户端收到时池里可能还没有这条链（装弹机发现未完成），因此先暂存、建池后再落地，不因一次先到的同步值丢掉选择。
- **同步只表达变化，初值靠补推**：`SynchedEntityData` 的变化广播（`packDirty()`）只在值真的变过时发得出去，且发给维度内所有玩家。**载入后不再变化的值（如弹药指认）走不到这条路**，因此载具在客户端建好时必须补一份全量快照：`SubsystemController.pushAllTo(ServerPlayer)`，由 `ObjectManager` 在每条载具创建包之后**紧跟着**调用（`pushSubsystemState`）。顺序由同一条连接保证，客户端在创建包的处理器内就建好了载具，因此补推包必然落在目标已存在的时刻。
- **全量补推不得改动同步状态**：快照取自 `AbstractSubsystem.forEachSyncedValue()`（只读遍历 `SynchedEntityData.itemsById`，该字段由 `META-INF/accesstransformer.cfg` 放宽为 public），经 `PacketDistributor.sendToPlayer` **定向**发送。不得改用 `SynchedEntityData.set(..., force=true)` 强制标脏：那会把脏标记留给下一次 `packDirty()`，让维度内其他玩家收到无谓的包。
- **自定义同步数据序列化器**：注册在 `MMEntityDataSerializers`（NeoForge 的 `ENTITY_DATA_SERIALIZERS` 注册表，线号为 `256 + 注册表内 id`）。`SynchedEntityData.defineId` 在子系统静态初始化里执行，因此注册文件必须持有序列化器**实例**、注册时交出同一实例，不能从 `DeferredHolder` 取。

## 反模式

- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **严禁忽略 `coupleTorque` 禁用**：MotorSubsystem 硬编码为 0（振荡问题）。未修复根因前不得重新启用。
- **严禁子系统缺少 static_attr**：每个子系统类型都需要对应的 JSON 加载用静态属性类。
- **严禁在遍历中修改 `allSubsystems`**：它是 `CopyOnWriteArraySet` — 修改安全但开销大。
