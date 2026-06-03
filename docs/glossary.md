# 项目术语表

> 生成时间：2026-05-04
> 项目：Machine-Max

## 概念列表

### VehicleCore（载具核心）

- **职责**：载具的整体管理与生命周期控制
- **描述**：载具的最高层级管理者，持有所有零件的拓扑图（`partNet`）、管理速度/质量/生命值、处理载具的分裂与合并、驱动物理模拟 tick。每个载具实例对应一个 `VehicleCore`。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore` — 载具核心，管理零件图、物理 tick、HP/质量

---

### Part（零件）

- **职责**：组装与 UGC 创作的最小单元
- **描述**：`Part` 是载具的基本组成单元，持有多个 `SubPart`（子零件）。零件拥有 `PartType`（类型定义）和 `VariantAttr`（变体属性），通过 `AdvancedConnector` / `SimpleConnector` 与其他零件连接构成载具拓扑结构。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.Part` — 零件本体
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType` — 零件类型定义（耐久度系数、伤害传递系数、功能阈值等）
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr` — 变体属性，定义零件在不同变体下的子零件结构

---

### SubPart（子零件）

- **职责**：零件内部的模块化组件
- **描述**：`SubPart` 是零件内部的更细粒度拆分，承载碰撞体、连接点（`AbstractConnector`）、交互判定区（`InteractBox`）、物理刚体以及子系统（`AbstractSubsystem`）。它是物理模拟的基本参与单位。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart` — 子零件，持有碰撞体、连接点、子系统

---

### Connector（连接点）

- **职责**：零件之间的物理连接与信号/能量传递
- **描述**：连接点分两种：`AdvancedConnector`（母口，含关节属性 `JointAttr`，可驱动旋转/平移）与 `SimpleConnector`（公口，提供简单对接）。两个匹配的连接点对接后创建一个 `New6Dof` 物理关节，并可附带 `SignalPort`（信号端口）和 `MechPowerPort`（机械能端口）。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector` — 连接点抽象基类
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AdvancedConnector` — 高级连接点（母口），支持关节驱动
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.SimpleConnector` — 简单连接点（公口）
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.ConnectorAlignmentHelper` — 连接点对齐辅助
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.ConnectorAttr` — 连接点属性
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.connector.JointAttr` — 关节属性（驱动轴、限位等）

---

### Subsystem（子系统）

- **职责**：为零件提供具体的功能行为
- **描述**：子系统是零件功能的模块化实现，挂载在 `SubPart` 上。通过 `AbstractSubsystem` 抽象基类派生，`BasicSubsystem` 提供基础实现，`AbstractControllableSubsystem` 扩展了可控能力。子系统种类丰富：引擎（`EngineSubsystem`）、电机（`MotorSubsystem`）、变速箱（`GearboxSubsystem`）、传动（`TransmissionSubsystem`）、座舱（`SeatSubsystem`）、电池（`BatterySubsystem`）、照明（`LightingSubsystem`）、摄像头（`CameraSubsystem`）、脚本（`ScriptableSubsystem`）、物品存储（`ItemStorageSubsystem`）、车辆控制（`CarControllerSubsystem`）、摩托车控制（`MotorbikeControllerSubsystem`）、轮毂电机驱动（`WheelDriverSubsystem`）、关节驱动（`JointDriverSubsystem`）等。由 `SubsystemController` 统一管理生命周期。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem` — 子系统抽象基类
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.BasicSubsystem` — 基本子系统实现
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractControllableSubsystem` — 可控子系统抽象
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.EngineSubsystem` — 发动机子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.MotorSubsystem` — 电动机子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.GearboxSubsystem` — 变速箱子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.TransmissionSubsystem` — 传动子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem` — 座椅子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.BatterySubsystem` — 电池子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.CarControllerSubsystem` — 车辆控制子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.MotorbikeControllerSubsystem` — 摩托车控制子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.WheelDriverSubsystem` — 轮毂电机驱动子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.JointDriverSubsystem` — 关节驱动子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.LightingSubsystem` — 照明子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.CameraSubsystem` — 摄像头子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.ScriptableSubsystem` — JavaScript 脚本子系统
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.ItemStorageSubsystem` — 物品存储子系统
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.subsystem.SubsystemTypes` — 子系统类型枚举
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.subsystem.WorkingState` — 子系统工作状态

---

### SubsystemController（子系统控制器）

- **职责**：载具级子系统与信号/能量的统一管理
- **描述**：每个 `VehicleCore` 持有一个 `SubsystemController`，负责：tick 所有子系统、管理信号通道（`SignalChannel`）与信号存储、维护能量网（`EnergyGrid`）、处理机械能传递路径。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.SubsystemController` — 子系统控制器，管理所有子系统、信号通道、能量网

---

### Signal System（信号系统）

- **职责**：载具内部的信号传递机制
- **描述**：通过 `SignalPort` 绑定到连接点或交互盒上，在不同零件/子系统间传递各类信号，包括：`RegularInputSignal`（常规按键输入）、`MoveInputSignal`（移动输入）、`MotorControlSignal`（电机控制）、`WheelControlSignal`（车轮控制）、`InteractSignal`（交互信号）等。`SignalChannel` 为命名通道，`ISignalReceiver` / `ISignalSender` 定义收发接口。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.signal.Signal` — 信号抽象基类
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.signal.SignalPort` — 信号端口，挂载在连接点上
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.signal.SignalChannel` — 命名信号通道
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.signal.ISignalReceiver` — 信号接收接口
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.signal.ISignalSender` — 信号发送接口

---

### Energy System（能量系统）

- **职责**：载具的电力与机械能管理
- **描述**：`EnergyGrid` 为全载具共享的电力总线，自动平衡发电/用电/储能。`MechPower` 与 `MechPowerPort` 管理机械能（转速/转矩）在连接点间的传播。`IMechPowerProducer` / `IMechPowerConsumer` 定义机械能的产消接口。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.energy.EnergyGrid` — 电力能量网（总线模式）
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.energy.IEnergyProducer` — 电力生产者接口
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.energy.IEnergyConsumer` — 电力消费者接口
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.energy.IEnergyStorage` — 电力储能接口
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.energy.MechPower` — 机械能（转速+转矩）
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.energy.MechPowerPort` — 机械能传输端口

---

### ObjectManager（对象管理器）

- **职责**：全局的载具与可破坏对象注册管理
- **描述**：静态管理器，按维度（`Level`）维护所有 `VehicleCore` 和 `DestroyableObject` 的映射。处理载具的添加/移除、客户端-服务端同步、区块加载/卸载时的载具睡眠/唤醒、世界保存/读取时的数据持久化。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.ObjectManager` — 载具与可破坏对象的全局管理器

---

### DestroyableObject（可破坏对象）

- **职责**：具有耐久度与物理模拟的载具基础对象
- **描述**：`Part` 与 `SubPart` 的抽象基类，提供同步位置/旋转/速度的数据管理、耐久度系统、伤害累计与同步。`DestroyableRigidObject` 扩展了刚体物理支持。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.DestroyableObject` — 可破坏对象基类
  - `io.github.sweetzonzi.machine_max.common.mech.DestroyableRigidObject` — 可破坏刚体对象

---

### MMPartEntity（部件实体）

- **职责**：载具在 Minecraft 世界中的实体表示
- **描述**：`MMPartEntity` 是 `Part` 在 Minecraft 实体层的代理，负责驱动的注册、交互处理、实体属性管理。通过 `IMMPartEntityAttribute` 接口和 `MMAttributeHandler` 管理实体属性。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.entity.MMPartEntity` — 部件实体
  - `io.github.sweetzonzi.machine_max.common.entity.IMMPartEntityAttribute` — 部件实体属性接口
  - `io.github.sweetzonzi.machine_max.common.entity.MMAttributeHandler` — 属性处理器

---

### Research & Blueprint System（研究与蓝图系统）

- **职责**：研究的解锁与载具/零件的蓝图保存/制造
- **描述**：`ResearchRecipe` 和 `BlueprintResearchRecipe` 定义研究配方（消耗研发点和材料解锁新技术）。`FabricatingRecipe` 定义制造配方（消耗材料按时间加工零件）。`BlueprintData` 保存载具的完整结构数据，可序列化为物品。`AssemblyData` 保存单个装配体数据。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe` — 研究配方基类
  - `io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe` — 蓝图研究配方
  - `io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe` — 制造台配方
  - `io.github.sweetzonzi.machine_max.common.recipe.FabricatingInput` — 制造输入
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.data.BlueprintData` — 蓝图数据
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.data.AssemblyData` — 装配体数据
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.data.VehicleData` — 载具完整数据（用于保存/同步）

---

### Item System（物品系统）

- **职责**：玩家交互的核心物品
- **描述**：一系列具有自定义模型和 3D 预览的特殊物品：`PartItem`（放置零件）、`PartAssemblyItem`（零件装配接口）、`AssemblyItem`（放置整个装配体）、`VehicleBlueprintItem`（载具蓝图，预览并生成载具）、`FabricatingBlueprintItem`（制造蓝图）、`EmptyBlueprintItem`（空白蓝图用于保存）、`VehicleCaptureItem`（载具封装接口，由具体物品实现）、`EnderGkResinItem`（末影树脂，封装载具为装配体）、`EnderScannerItem`（末影扫描仪，封装载具为装配体）、`CrowbarItem`（撬棍，拆卸部件）、`WeldingTorchItem`（焊枪，修复组装）、`SprayCanItem`（喷罐，切换贴图）、`MaterialItem`（合成材料）。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.item.prop.PartItem` — 零件物品，右键放置到世界
  - `io.github.sweetzonzi.machine_max.common.item.prop.AssemblyItem` — 装配体物品，放置预组装的零件组
  - `io.github.sweetzonzi.machine_max.common.item.prop.VehicleBlueprintItem` — 载具蓝图物品
  - `io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem` — 制造蓝图物品
  - `io.github.sweetzonzi.machine_max.common.item.prop.EmptyBlueprintItem` — 空白蓝图
  - `io.github.sweetzonzi.machine_max.common.item.prop.CrowbarItem` — 撬棍
  - `io.github.sweetzonzi.machine_max.common.item.prop.WeldingTorchItem` — 焊枪
  - `io.github.sweetzonzi.machine_max.common.item.prop.SprayCanItem` — 油漆喷罐
  - `io.github.sweetzonzi.machine_max.common.item.prop.EnderGkResinItem` — 末影树脂，实现载具封装
  - `io.github.sweetzonzi.machine_max.common.item.prop.EnderScannerItem` — 末影扫描仪，实现载具封装
  - `io.github.sweetzonzi.machine_max.common.item.MaterialItem` — 基础材料物品

---

### Blocks & BlockEntities（方块与方块实体）

- **职责**：模组的功能性方块
- **描述**：提供四个功能性方块：`FabricatorBlock`（制造台，自动加工零件）、`ResearchTableBlock`（研究台，消耗研发点解锁配方）、`TotalStationBlock`（全站仪，测量/扫描载具）、`RoadBaseBlock`（路基方块，用于铺设道路）。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.block.fabricator.FabricatorBlock` / `FabricatorBlockEntity` — 制造台
  - `io.github.sweetzonzi.machine_max.common.block.research_table.ResearchTableBlock` / `ResearchTableBlockEntity` — 研究台
  - `io.github.sweetzonzi.machine_max.common.block.total_station.TotalStationBlock` / `TotalStationBlockEntity` — 全站仪
  - `io.github.sweetzonzi.machine_max.common.block.road.RoadBaseBlock` / `RoadBaseBlockEntity` — 路基方块

---

### Attachment System（实体附件系统）

- **职责**：扩展 Minecraft 实体的数据存储
- **描述**：使用 NeoForge Attachment 机制为玩家和世界附加额外数据：`VehicleAssemblyAttachment`（缓存玩家的组装状态）、`ControlPreferenceAttachment`（控制偏好设置）、`BlueprintAttachment`（玩家持有的蓝图与研发点数）、`LivingEntityEyesightAttachment`（视线追踪，用于识别目标载具/连接点）、世界级别的 `LEVEL_VEHICLES`（存储维度内所有载具数据）。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.attachment.VehicleAssemblyAttachment` — 组装缓存附件
  - `io.github.sweetzonzi.machine_max.common.attachment.ControlPreferenceAttachment` — 控制偏好附件
  - `io.github.sweetzonzi.machine_max.common.attachment.BlueprintAttachment` — 蓝图附件
  - `io.github.sweetzonzi.machine_max.common.attachment.LivingEntityEyesightAttachment` — 实体视线附件

---

### Visual System（可视化系统）

- **职责**：载具的渲染、动画与 HUD 显示
- **描述**：客户端渲染体系包括：零件/子零件/载具的动画模型渲染（`PartAnimatable` / `SubPartAnimatable` / `VehicleAnimatable`）、3D HUD 元素（`AssemblyHud3D`）、2D HUD（`AssemblyHud` / `InteractHud`）、GUI 界面（`FabricatingScreen` / `BlueprintResearchScreen` / `VehicleNamingScreen`）、特效辅助（`VisualEffectHelper`）。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.visual.PartAnimatable` — 零件动画对象
  - `io.github.sweetzonzi.machine_max.common.visual.VehicleAnimatable` — 载具动画对象（用于蓝图预览）
  - `io.github.sweetzonzi.machine_max.common.visual.VisualEffectHelper` — 视觉效果辅助
  - `io.github.sweetzonzi.machine_max.client.render.renderer.PartAssemblyRenderer` — 零件装配渲染器
  - `io.github.sweetzonzi.machine_max.client.render.gui.screen.FabricatingScreen` — 制造台 GUI
  - `io.github.sweetzonzi.machine_max.client.render.gui.screen.BlueprintResearchScreen` — 蓝图研究 GUI
  - `io.github.sweetzonzi.machine_max.client.render.gui.hud.AssemblyHud` — 组装 HUD

---

### Collision System（碰撞系统）

- **职责**：载具碰撞检测与效果处理
- **描述**：`CollisionManager` 管理全局碰撞事件，`CollisionHandler` 处理具体的碰撞逻辑，`CollisionEffectManager` 管理碰撞视觉效果（粒子、声音），`CollisionContext` 提供碰撞上下文信息。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.CollisionManager` — 碰撞管理器
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionHandler` — 碰撞处理器
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.collision.CollisionEffectManager` — 碰撞效果管理器

---

### Interact System（交互系统）

- **职责**：载具的交互判定与处理
- **描述**：`InteractBox` 定义方体交互区域（门、开关等），`HitBox` 定义被击中判定区域，`InteractBoxes` 管理一组交互盒。`PartInteractHandler` 和 `ItemInteractHandler` 分别处理零件和物品的交互逻辑。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.InteractBox` — 交互判定区
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox` — 击中判定区
  - `io.github.sweetzonzi.machine_max.common.entity.PartInteractHandler` — 零件交互处理器
  - `io.github.sweetzonzi.machine_max.common.item.ItemInteractHandler` — 物品交互处理器

---

### Event System（事件系统）

- **职责**：载具内部事件驱动机制
- **描述**：提供连接点连接/断开事件（`ConnectorAttachEvent` / `ConnectorDetachEvent`）、子零件伤害事件（`SubPartDamageEvent`）、载具事件（`VehicleEvent`）、载具分裂事件（`VehicleSpiltEvent`），通过 NeoForge 总线广播。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector.ConnectorAttachEvent` — 连接点连接事件
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector.ConnectorDetachEvent` — 连接点断开事件
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent` — 子零件伤害事件
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleEvent` — 载具事件基类
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.event.VehicleSpiltEvent` — 载具分裂事件

---

### Molang & Scripting（动画表达式与脚本系统）

- **职责**：提供动画表达式绑定与 JavaScript 扩展能力
- **描述**：`VehicleBinding` 和 `SubPartBinding` 将载具运行时数据暴露给 Molang 动画表达式引擎，支持数据驱动的动画。`ScriptableSubsystem` 集成 GraalJS JavaScript 引擎，允许 UGC 作者编写自定义子系统逻辑。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.molang.VehicleBinding` — 载具 Molang 绑定
  - `io.github.sweetzonzi.machine_max.common.mech.vehicle.molang.SubPartBinding` — 子零件 Molang 绑定
  - `io.github.sweetzonzi.machine_max.common.mech.subsystem.ScriptableSubsystem` — JavaScript 脚本子系统
  - `io.github.sweetzonzi.machine_max.external.js.JSUtils` — JS 工具类

---

### Network System（网络系统）

- **职责**：客户端-服务端数据同步
- **描述**：使用 NeoForge 网络系统实现载具状态同步，包括：`SubPartSyncPayload`（子零件数据）、`ConnectorSyncPayload`（连接点数据）、`SubsystemSyncPayload`（子系统数据）、`MovementInputPayload`（移动输入）、`RegularInputPayload`（按键输入）、`ScriptablePayload`（脚本数据）等。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.network.payload.SubPartSyncPayload` — 子零件同步包
  - `io.github.sweetzonzi.machine_max.network.payload.ConnectorSyncPayload` — 连接点同步包
  - `io.github.sweetzonzi.machine_max.network.payload.SubsystemSyncPayload` — 子系统同步包
  - `io.github.sweetzonzi.machine_max.network.MMPayloadRegistry` — 网络包注册器

---

### Util & Data（工具与数据层）

- **职责**：提供通用的数学/物理/数据工具
- **描述**：`PDController` / `PIDController`（控制器算法）、`PosRot` / `PosRotVelVel`（位置旋转速度数据结构）、`Axis`（轴向枚举）、`MMMath`（数学工具）、`Easing`（缓动函数）、`ShapeHelper`（形状辅助）、`LocalHeightField` / `TerrainBuilder`（地形交互）、`ChunkHelper`（区块工具）。
- **关键类**：
  - `io.github.sweetzonzi.machine_max.util.control.PDController` — PD 控制器
  - `io.github.sweetzonzi.machine_max.util.control.PIDController` — PID 控制器
  - `io.github.sweetzonzi.machine_max.util.data.PosRot` — 位置旋转数据
  - `io.github.sweetzonzi.machine_max.util.data.Axis` — 轴向枚举
  - `io.github.sweetzonzi.machine_max.util.MMMath` — 数学工具
  - `io.github.sweetzonzi.machine_max.util.terrain.LocalHeightField` — 局部高度场