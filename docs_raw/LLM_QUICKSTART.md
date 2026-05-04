# Machine Max LLM 快速导览

本文件用于让 LLM 在最短上下文内掌握项目结构，尤其是“载具拼装”逻辑。

## 1. 项目简介

Machine Max 是一个 NeoForge 1.21.1 载具模组，采用：

- 数据驱动定义（资源包 JSON）决定部件/连接器/子系统属性
- 运行时对象（`VehicleCore`/`Part`/`SubPart`）驱动物理与拓扑
- 玩家拼装状态缓存（`VehicleAssemblyAttachment`）协调“选型-预览-安装”
- 网络 payload 将服务端权威状态同步到客户端重建

## 2. 先读这些入口

- 模组入口与注册：
  - `src/main/java/io/github/sweetzonzi/machine_max/MachineMax.java`
- 网络包注册总表：
  - `src/main/java/io/github/sweetzonzi/machine_max/network/MMPayloadRegistry.java`
- 载具运行时管理：
  - `src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/ObjectManager.java`

## 3. 拼装核心链路（最重要）

### 3.1 玩家侧缓存与状态机

- `VehicleAssemblyAttachment`（每玩家附件）
  - 文件：`src/main/java/io/github/sweetzonzi/machine_max/common/attachment/VehicleAssemblyAttachment.java`
  - 负责缓存：
    - 当前部件类型 `partType`
    - 当前变体 `variantName`
    - 当前待连接接口 `connectorName`
    - 安装角 `attachRotation`
    - 预览姿态 `offset/quaternion`
  - 核心动作：
    - `cycleAttachAngle`
    - `cycleConnectors`
    - `cycleVariants`
    - `cycleRecipe`
    - `assembly(...)`（执行实际安装/连接/直接放置）

### 3.2 输入与按键触发

- 客户端按键采集：
  - `src/main/java/io/github/sweetzonzi/machine_max/client/input/RawInputHandler.java`
- 输入包处理：
  - `src/main/java/io/github/sweetzonzi/machine_max/network/payload/RegularInputPayload.java`
  - 服务端落地后调用 `VehicleAssemblyAttachment` 的循环方法

### 3.3 实际放置与拼接

- 单部件放置入口（右键）：
  - `src/main/java/io/github/sweetzonzi/machine_max/common/item/prop/PartItem.java`
  - 通过 `cache.assembly(...)` 决定：
    - 填充未组装部件进度
    - 挂接到目标连接点
    - 或作为新载具直接落地
- 整车装配体放置入口：
  - `src/main/java/io/github/sweetzonzi/machine_max/common/item/prop/AssemblyItem.java`
  - 读取 `AssemblyData -> VehicleData`，碰撞测试通过后整体生成 `VehicleCore`

## 4. 载具运行时核心对象

- `VehicleCore`
  - 文件：`src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/VehicleCore.java`
  - 关键点：
    - `partNet`（部件连接图）
    - `attachConnector(...)`（连接并可自动 combo attach）
    - `detachConnections(...)`（断开并检查分裂）
    - `removePart(...)`
- `Part`
  - 文件：`src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/Part.java`
  - 关键点：
    - `assemble(...)` / `disassemble(...)`
    - `materialProgress` 与 `assemblingProgress`
    - 基于配方逐步消耗/返还材料
- `SubPart`
  - 文件：`src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/SubPart.java`
  - 这是最关键的“执行层”单元，直接承载：
    - 物理交互：刚体、变换、速度、碰撞体、命中盒、交互盒
    - 功能实体：`subsystems`（发动机、座椅、变速箱、控制器等）都挂在 `SubPart`
    - 连接关系：`connectors`（高级/简单连接器）也挂在 `SubPart`
    - 视觉状态：贴图切换、动画绑定、耐久/损伤反馈
  - 理解建议：
    - `Part` 是组装与保存粒度
    - `SubPart` 是物理与功能真实发生的粒度
    - Connector/Subsystem 的实际 tick 与交互都沿着 `SubPart` 展开

### 4.1 Part 与 SubPart 的职责边界

- `Part`（装配逻辑层）：
  - 管配方、组装进度、材料进度、序列化、对外部件连接集合
- `SubPart`（执行与物理层）：
  - 管刚体、碰撞、子系统实例、连接器实例、交互与命中判定
- 典型流程：
  - 放置时先创建 `Part`，再由 `Part.createSubParts(...)` 构造 `SubPart` 图
  - `VehicleCore` 维护的是 `Part` 拓扑图，但物理求解和功能运行依托 `SubPart`

## 5. 信号系统（不要忽略）

信号系统位于：

- `src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/signal/`

核心类型：

- `Signal`：信号基类（各类输入/控制/功率信号）
- `SignalChannel`：信号通道，负责路由与分发
- `SignalPort`：Connector特有接口，用于在SubPart之间进行传递
- `ISignalSender` / `ISignalReceiver`：发送接收接口

常见信号实现：

- `MoveInputSignal`、`RegularInputSignal`（玩家输入）
- `MotorControlSignal`、`WheelControlSignal`（驱动控制）
- `MechPowerSignal`、`ElectricPowerSignal`（动力/电力）
- `InteractSignal`（交互触发）

系统关系：

- 玩家输入与子系统状态先形成 `Signal`
- 子系统和连接器通过 `SignalPort` / `SignalChannel` 互联
- `SubsystemController` 在 tick 中驱动子系统处理输入信号并输出控制信号
- Connector 使信号可跨 `SubPart` / `Part` 传播，形成整车功能链路

理解重点：

- 拼装不仅是几何连接，也是“信号网络接通”
- 一辆车能不能动，取决于子系统链路与信号链路是否闭合

## 6. 拆装/维护相关工具物品

- 焊枪（组装+维修+潜行拆解）：
  - `src/main/java/io/github/sweetzonzi/machine_max/common/item/prop/WeldingTorchItem.java`
- 撬棍（断连接/拆部件）：
  - `src/main/java/io/github/sweetzonzi/machine_max/common/item/prop/CrowbarItem.java`
- 喷漆（循环贴图）：
  - `src/main/java/io/github/sweetzonzi/machine_max/common/item/prop/SprayCanItem.java`

## 7. 客户端拼装预览

- 渲染器：
  - `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderer/PartAssemblyRenderer.java`
- 展示内容：
  - 可连接接口高亮（红/绿）
  - 待放置部件半透明投影
  - 蓝图/装配体包围盒与整车投影

## 8. 网络同步（拼装相关）

位于 `src/main/java/io/github/sweetzonzi/machine_max/network/payload/assembly/`：

- `VehicleCreatePayload`: 客户端创建载具实例
- `VehicleRemovePayload`: 客户端移除载具
- `ConnectorAttachPayload`: 同步连接建立（可带新部件数据）
- `ConnectorDetachPayload`: 同步连接断开与分车映射
- `PartRemovePayload`: 同步部件移除与分车映射
- `PartAssemblyProgressSyncPayload`: 同步组装进度
- `PartChangeRecipePayload`: 同步配方切换
- `PartPaintPayload`: 同步涂装变化
- `PlayerPartAssemblyCacheSyncPayload`: 同步玩家拼装缓存（预览状态）

## 9. 数据驱动资源位置

核心目录：

- `src/main/resources/spark_modules/Machine-Max_Official_Pack/machine_max/`
  - `parts/`：部件类型定义
  - `connectors/`：连接器定义
  - `subsystems/`：子系统定义
  - `templates/`：整车结构模板（`VehicleData`）
  - `assemblies/`：装配体物品定义（`AssemblyData`）
  - `blueprints/`：蓝图物品定义（`BlueprintData`）

关键类：

- `PartType`: `src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/PartType.java`
- `MMDynamicRes`: `src/main/java/io/github/sweetzonzi/machine_max/external/MMDynamicRes.java`
- `AssemblyData`: `src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/data/AssemblyData.java`
- `VehicleData`: `src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/data/VehicleData.java`

## 10. LLM 阅读建议顺序

1. `MachineMax.java` + `MMPayloadRegistry.java`
2. `VehicleAssemblyAttachment.java` + `PartItem.java` + `AssemblyItem.java`
3. `VehicleCore.java` + `Part.java` + `SubPart.java` + `ObjectManager.java`
4. `common/vehicle/signal/*` + `common/vehicle/subsystem/*`
5. `network/payload/assembly/*`
6. `PartAssemblyRenderer.java`
7. `spark_modules/Machine-Max_Official_Pack/machine_max/*`（对照数据）

## 11. 常见扩展点

- 新增可拼装部件：
  - 先加 `parts/*.json` 与相关 `connectors/subsystems/templates` 资源
  - 再通过物品 component（`PART_TYPE`/`RECIPE_TYPE`）接入 `PartItem` 放置链
- 调整拼装约束：
  - `VehicleAssemblyAttachment.cycleConnectors/cycleVariants`
  - `VehicleAssemblyAttachment.assembly`
  - `VehicleCore.attachConnector` 与 `comboAttachConnector`
- 调整拆分规则：
  - `VehicleCore.partNetSpiltCheck`
  - `VehicleCore.serverHandleSpilt`

## 12. 数学类型与转换规范（重要）

不要混淆以下两套数学类型：

- 物理库/JME：`com.jme3.math.Vector3f`、`Quaternion`、`Transform`、`Matrix4f`
- 渲染与通用/JOML：`org.joml.Vector3f`、`Quaternionf`、`Matrix4f`

项目里两套类型都会出现，禁止直接“想当然”互传或手写字段拷贝。  
跨类型转换请统一使用 SparkCore 的 `SparkMathKt`（`SparkMath.kt`）提供的方法，例如：

- `SparkMathKt.toBVector3f(...)`
- `SparkMathKt.toVector3f(...)`
- `SparkMathKt.toBQuaternion(...)`
- `SparkMathKt.toQuaternionf(...)`
- 以及其他 `to*`/`lerp` 工具方法

实务建议：

- 物理计算与刚体状态读取/写入时优先使用 `com.jme3.math.*`
- 客户端渲染、矩阵拼装、UI/HUD 相关优先使用 `org.joml.*`
- 在模块边界（物理 -> 渲染、网络解包 -> 物理应用）处显式调用 `SparkMathKt` 转换
