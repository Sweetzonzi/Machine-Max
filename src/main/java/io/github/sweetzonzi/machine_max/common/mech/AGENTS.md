# common/mech/ — 载具核心域

**范围**: 载具物理、组装、子系统、信号、控制绑定、能源、投射物。

## 结构

```
common/mech/
├── vehicle/                    # ★ 载具聚合根 — 见 vehicle/AGENTS.md
│   ├── connector/              # AbstractConnector, AdvancedConnector, SimpleConnector
│   ├── collision/              # CollisionHandler, CollisionEffectManager
│   ├── interact/               # HitBox, InteractBox, InteractBoxes
│   ├── attr/                   # SubPartAttr, VariantAttr, ConnectorAttr, HitBoxAttr ...
│   ├── data/                   # VehicleData, PartData, SubPartData 序列化
│   └── event/                  # ConnectorAttachEvent, VehicleSpiltEvent, SubPartEvent ...
├── subsystem/                  # 22 种子系统 — 见 subsystem/AGENTS.md
│   └── attr/                   # SubsystemTypes、WorkingState、动态/静态属性
├── signal/                     # 信号系统 — 见 signal/AGENTS.md
├── control/                    # 控制绑定：ControlBinding, ControlGroup, ControlGroupSet, GuiAction ...
├── energy/                     # 能源系统：EnergyGrid, MechPower, IEnergyStorage ...
├── projectile/                 # 数据驱动投射物系统 — 见 projectile/AGENTS.md
├── molang/                     # MoLang 表达式上下文：MechMolangContext
├── ObjectManager.java          # 按维度注册 VehicleCore
├── DestroyableObject.java      # 可破坏对象基类
└── DestroyableRigidObject.java # 可破坏物理对象基类
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 修改载具拓扑 | `vehicle/VehicleCore.java` | partNet（MutableNetwork），merge/split 逻辑 |
| 修改物理体 | `vehicle/SubPart.java` | 刚体、碰撞、空气动力学、BallisticsFramework |
| 修改组装/配方 | `vehicle/Part.java` | SubPart 容器、材料进度、耐久度 |
| 新增连接器类型 | `vehicle/connector/AbstractConnector.java` | 关节（New6Dof）、信号端口、完整性 |
| 修改碰撞 | `vehicle/collision/CollisionHandler.java` | 地形、实体、载具-载具、Create 兼容 |
| 新增信号类型 | `signal/Signal.java` | 继承基础类，注册到 ISignalReceiver/Sender |
| 添加控制绑定 | `control/ControlBinding.java` | 按键 → 网络包 → 信号映射 |
| 修改能源网格 | `energy/EnergyGrid.java` | 直流总线、优先级负载卸载 |
| 新增投射物行为 | `projectile/ProjectileManager.java` | SoA 数组、rayTest、BFDamageApi |
| 修改 MoLang 上下文 | `molang/MechMolangContext.java` | 粒子/HUD 动画表达式 |
| 修复物理线程崩溃 | `VehicleCore.java` | 根因：关节两刚体均为运动学模式 |

## 约定

- **线程模型**：Javadoc 标注 `主线程`（20tps）vs `物理线程`（Bullet 物理步进）。
- **累加器模式**：物理线程入队到 `ConcurrentLinkedQueue`；主线程在 `handleAccumulated*()` 中清空。
- **Volatile 快照**：`CollisionEffectManager.latestWheelSnapshot` — 物理写，主读。
- **双数学库**：物理用 JME（`com.jme3.math.*`），渲染用 JOML（`org.joml.*`）。通过 `SparkMathKt.*` 转换。
- **SignalChannel extends ConcurrentHashMap**：通过继承实现线程安全。
- **EnergyGrid**：生产/消费者用 `CopyOnWriteArraySet`；供应比例用 `ConcurrentMap`。
- **机械功率流**：Engine/Motor → Gearbox → Transmission → WheelDriver/JointDriver，通过连接器上的 MechPowerPort 传递。

## 反模式

- **严禁在主线程直接操作物理体**：使用 `getPhysicsLevel().submitImmediateTask(PPhase.ALL/PRE, ...)`。
- **严禁在 `partNet` 之外使用 `synchronized`**：仅 VehicleCore 中有 2 处（行 893, 972）。其余全部使用并发集合。
- **严禁混用 JME 和 JOML**：始终通过 `SparkMathKt.*` 转换。
- **严禁忽略 `DestroyableRigidObject.updateLock`**：服务端→同步数据期间置 `true`，防止反馈循环。
