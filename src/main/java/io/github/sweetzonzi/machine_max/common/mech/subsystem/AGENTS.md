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
│   ├── AmmoLoaderSubsystem.java        # 弹药装填机（装填时序、弹序循环）
│   └── RegenLoaderSubsystem.java       # 再生装弹机（能量武器弹药）
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
| 修复装弹机 | `AmmoLoaderSubsystem.java` | 弹序循环、装填时序 |
| 新增静态属性 | `attr/static_attr/` | JSON 编解码，按类型定义 |
| 新增动态属性 | `attr/dynamic_attr/` | 运行时可变状态 |

## 约定

- **继承链**：`AbstractSubsystem` → `AbstractControllableSubsystem` → 具体类型。
- **信号 I/O**：全部实现 `ISignalReceiver`/`ISignalSender`。通道为 `ConcurrentMap<String, SignalChannel>`；寻址名由 `getSignalAddress()` 提供（子系统名），保留目标名 `"local"` = Part、`"global"` = 装配体。默认输出目标（`*_outputs`）应写 `["local","global"]` 或 `["global"]`。
- **武器开火控制频道命名**：发射器 `control_inputs` 遵循「类型 + 可选型号」——基础频道 `weapon.<class>`（如 `weapon.mg` 机枪、`weapon.autocannon` 机关炮、`weapon.gun` 直射火炮），可选型号频道 `weapon.<class>.<model>`（如 `weapon.autocannon.2a42`），用于专用火控计算机；列表顺序即优先级。主/副武器角色由座位控制组 `mainWeaponTargets`/`secondaryWeaponTargets` 布线决定，不在频道名中体现。weapon_controller 的 `control_outputs` 采用「分裂频道」：保留 turret_driver 瞄准频道，另加类型开火频道（避免和炮塔瞄准握手耦合）。
- **开火信号按语义判定**：主/副武器开火指令分别发到控制组 `main_weapon_targets`/`secondary_weapon_targets` 声明的频道（内容包里是 `fire_input_*`），弹种切换指令（`NEXT_AMMO_TYPE`/`PREV_AMMO_TYPE`）也走主武器那一组，三者共用同一批线路。`WeaponControllerSubsystem.readInputSignals()` 因此按信号语义收集开火：主/副武器开火指令、值为 `true` 的 Boolean、非 0 的数值算开火；弹种指令、握手用的 String、`EmptySignal` 不算。按"频道非空"收集会让按一次切换弹种键就打出一发。
- **功率流**：`Engine/Motor` (IMechPowerProducer) → `Gearbox` → `Transmission` → `WheelDriver/JointDriver`，通过连接器的 MechPowerPort 传递。
- **能源网格**：`BatterySubsystem` 同时实现 `IEnergyProducer` 和 `IEnergyConsumer`。`EnergyGrid` 每 tick 平衡。
- **保存/加载**：在 `AbstractSubsystem` 中重写 `saveData()`/`loadData()` 进行 NBT 持久化。
- **线程**：`onCollideWithBlock()` 在物理线程调用；`onInteract()` 在主线程调用。
- **结构变化两阶段**：`SubsystemController.onVehicleStructureChanged()` 先对所有子系统调用 `onVehicleStructurePreChanged()`，再统一调用 `onVehicleStructureChanged()`。**清空拓扑缓存写第一阶段，重新握手写第二阶段**——`allSubsystems` 是集合，遍历顺序不确定；两者挤在同一趟里时，后清空的子系统会把先握手的结果抹掉（发射器清供给者登记表、装弹机重新广播发现就属于这一对）。
- **弹药指认服务端独占**：`WeaponControllerSubsystem` 的弹药路由只在服务端执行，客户端经发射器上的供给者身份同步值跟随（`IAmmoSupplier.SupplierRef` = 宿主 Part uuid + SubPart 名 + 子系统名，三者都随载具数据在两端的同一次会话内一致）。客户端的 `requestRound → consumeReadyRound` 链只驱动本机表现（装填进度、装填音效、开火特效），库存计数与弹链指针只有服务端写。
- **弹种选择随服务端同步**：`WeaponControllerSubsystem` 的 `selectedBelt` 只在服务端改选，经同步值 `SELECTED_BELT` 推给客户端；值是**有序拼接的弹种注册键串**（`id1,id2`），不是池内下标——结构变化会改变弹链排序，键串只随弹链本身变化。客户端收到时池里可能还没有这条链（装弹机握手未完成），因此先暂存、建池后再落地，不因一次先到的同步值丢掉选择。
- **同步只表达变化，初值靠补推**：`SynchedEntityData` 的变化广播（`packDirty()`）只在值真的变过时发得出去，且发给维度内所有玩家。**载入后不再变化的值（如弹药指认）走不到这条路**，因此载具在客户端建好时必须补一份全量快照：`SubsystemController.pushAllTo(ServerPlayer)`，由 `ObjectManager` 在每条载具创建包之后**紧跟着**调用（`pushSubsystemState`）。顺序由同一条连接保证，客户端在创建包的处理器内就建好了载具，因此补推包必然落在目标已存在的时刻。
- **全量补推不得改动同步状态**：快照取自 `AbstractSubsystem.forEachSyncedValue()`（只读遍历 `SynchedEntityData.itemsById`，该字段由 `META-INF/accesstransformer.cfg` 放宽为 public），经 `PacketDistributor.sendToPlayer` **定向**发送。不得改用 `SynchedEntityData.set(..., force=true)` 强制标脏：那会把脏标记留给下一次 `packDirty()`，让维度内其他玩家收到无谓的包。
- **自定义同步数据序列化器**：注册在 `MMEntityDataSerializers`（NeoForge 的 `ENTITY_DATA_SERIALIZERS` 注册表，线号为 `256 + 注册表内 id`）。`SynchedEntityData.defineId` 在子系统静态初始化里执行，因此注册文件必须持有序列化器**实例**、注册时交出同一实例，不能从 `DeferredHolder` 取。

## 反模式

- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **严禁忽略 `coupleTorque` 禁用**：MotorSubsystem 硬编码为 0（振荡问题）。未修复根因前不得重新启用。
- **严禁子系统缺少 static_attr**：每个子系统类型都需要对应的 JSON 加载用静态属性类。
- **严禁在遍历中修改 `allSubsystems`**：它是 `CopyOnWriteArraySet` — 修改安全但开销大。
