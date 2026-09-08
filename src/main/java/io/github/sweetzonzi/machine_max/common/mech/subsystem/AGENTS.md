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
- **功率流**：`Engine/Motor` (IMechPowerProducer) → `Gearbox` → `Transmission` → `WheelDriver/JointDriver`，通过连接器的 MechPowerPort 传递。
- **能源网格**：`BatterySubsystem` 同时实现 `IEnergyProducer` 和 `IEnergyConsumer`。`EnergyGrid` 每 tick 平衡。
- **保存/加载**：在 `AbstractSubsystem` 中重写 `saveData()`/`loadData()` 进行 NBT 持久化。
- **线程**：`onCollideWithBlock()` 在物理线程调用；`onInteract()` 在主线程调用。

## 反模式

- **严禁绕过 AbstractSubsystem 生命周期**：始终在 tick/prePhysicsTick/postPhysicsTick 中调用 super。
- **严禁忽略 `coupleTorque` 禁用**：MotorSubsystem 硬编码为 0（振荡问题）。未修复根因前不得重新启用。
- **严禁子系统缺少 static_attr**：每个子系统类型都需要对应的 JSON 加载用静态属性类。
- **严禁在遍历中修改 `allSubsystems`**：它是 `CopyOnWriteArraySet` — 修改安全但开销大。
