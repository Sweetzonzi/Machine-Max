# common/mech/vehicle/ — 载具聚合根

**范围**: 载具核心拓扑、零件实体、连接器、碰撞、数据序列化、事件系统。

**文件数**: 42 个 | **总行数**: ~8800 行

## 结构

```
vehicle/
├── VehicleCore.java            # 聚合根（1233行），实现 IPartAssembly
├── Part.java                   # 部件聚合 + 动画体（IAnimatable<Part>），持有 ModelController/AnimController/Molang 与 SubPart 清单
├── SubPart.java                # 零件实体（刚体 + 骨骼切分 + 渲染视图），实现 ISubsystemHost
├── PartType.java               # 部件类型数据（JSON Codec + StreamCodec）
├── IPartAssembly.java          # 装配体顶层接口（解耦 Part↔VehicleCore）
├── DamageModifier.java         # 数据驱动的伤害修改器
├── CollisionManager.java       # 实体碰撞冲量管理器
│
├── attr/                       # 零件属性系统（11文件，~1600行）
│   ├── SubPartAttr.java        #   零件基本属性（物理、碰撞、可视化）
│   ├── VariantAttr.java        #   变体属性（贴图、模型）
│   ├── MaterialAttr.java       #   材质属性
│   ├── MotorAttr.java          #   马达/引擎属性
│   ├── HitBoxAttr.java         #   命中判定框
│   ├── AdvancedAeroAttr.java   #   高级空气动力学
│   ├── HydrodynamicAttr.java   #   流体动力学
│   ├── InteractBoxAttr.java    #   交互框
│   └── connector/              #   连接器属性（ConnectorAttr, ConnectorStaticAttr, JointAttr）
│
├── connector/                  # 连接器系统（4文件，~780行）
│   ├── AbstractConnector.java  #   抽象连接器基类（~711行），PhysicsHost + SyncedDataHolder
│   ├── SimpleConnector.java    #   简单连接器（刚性连接、无信号）
│   ├── AdvancedConnector.java  #   高级连接器（阻尼关节、信号端口）
│   └── ConnectorAlignmentHelper.java # 连接对齐工具
│
├── data/                       # 数据序列化（7文件，~700行）
│   ├── VehicleData.java        #   载具整体数据
│   ├── PartData.java           #   部件数据
│   ├── SubPartData.java        #   零件数据
│   ├── ConnectionData.java     #   连接关系数据
│   ├── AssemblyData.java       #   装配体数据
│   ├── BlueprintData.java      #   蓝图数据
│   └── MMDamageExtensions.java #   弹道伤害扩展初始化
│
├── event/                      # 事件系统（8文件，~200行）
│   ├── VehicleEvent.java       #   载具生命周期事件基类
│   ├── VehicleSpiltEvent.java  #   载具分裂事件
│   └── connector/              #   连接器事件（Attach/Detach/Tick）
│   │   ├── ConnectorAttachEvent.java
│   │   ├── ConnectorDetachEvent.java
│   │   ├── ConnectorEvent.java
│   │   └── ConnectorTickEvent.java
│   └── subpart/                #   零件事件
│       ├── SubPartEvent.java
│       └── SubPartDamageEvent.java
│
├── collision/                  # 碰撞系统（2文件，~1288行）
│   ├── CollisionHandler.java   #   碰撞处理器（723行），地形/实体/载具-载具/Create
│   └── CollisionEffectManager.java # 碰撞效果管理器（565行），音效+特效
│
└── interact/                   # 交互系统（3文件，~370行）
    ├── HitBox.java             #   命中判定框
    ├── InteractBox.java        #   玩家交互框
    └── InteractBoxes.java      #   交互框集合
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 修改载具生命周期 | `VehicleCore.java` | 聚合根：创建、销毁、合并、分裂、物理世界注册 |
| 修改拓扑图 | `VehicleCore.java` | `partNet`（Guava MutableNetwork），图连通性检测 |
| 修改零件物理体 | `SubPart.java` | 刚体、碰撞形状、空气阻力、浮力 |
| 修改装配/耐久 | `Part.java` | SubPart 容器、制造进度条、耐久度传递 |
| 修改部件类型数据 | `PartType.java` | JSON Codec + StreamCodec，注册名→数据查找 |
| 新增连接器类型 | `connector/AbstractConnector.java` | 继承、实现关节类型和信号端口 |
| 修改碰撞逻辑 | `collision/CollisionHandler.java` | 地形高度场、实体碰撞、Create 兼容 |
| 修改碰撞特效 | `collision/CollisionEffectManager.java` | 火花/声音/轮子快照 |
| 新增属性字段 | `attr/SubPartAttr.java` | 加字段 + JSON Codec |
| 新增事件 | `event/` | 继承对应事件基类，注册到 NeoForge 总线 |
| 修改交互框 | `interact/InteractBox.java` | 玩家右键交互、命中检测 |
| 修改实体碰撞 | `CollisionManager.java` | 实体碰撞冲量映射 |
| 修复物理线程崩溃 | `VehicleCore.java` | 根因：关节两刚体均为运动学模式 |

## 关键类关系

```
VehicleCore (IPartAssembly)
  ├── 拥有 N 个 Part（动画体：ModelController + AnimController + 共享 ModelPose + Molang 上下文）
  │     └── 每个 Part 拥有 N 个 SubPart
  │           ├── 刚体（Bullet Physics via Spark-Core）
  │           ├── N 个 AbstractConnector（连接点）
  │           ├── N 个子系统（SubsystemController 管理）
  │           └── N 个 HitBox / InteractBox
  └── partNet（Guava MutableNetwork<SubPart, AbstractConnector>）
        └── 图拓扑：节点=SubPart，边=Connector 配对
```

## 约定

- **线程模型**：Javadoc 标注 `主线程` vs `物理线程`。物理线程操作入队到 `ConcurrentLinkedQueue`。
- **拓扑图线程安全**：`partNet` 仅有的 2 处 `synchronized` 块（VehicleCore.java:893,972）。
- **累加器模式**：物理线程入队伤害/冲击/完整性变更；主线程在 `handleAccumulated*()` 清空。
- **Volatile 快照**：`CollisionEffectManager.latestWheelSnapshot` — 物理写，主读。
- **同步数据**：`SynchedEntityData` + 增量同步（byte 255 终结符）。
- **伤害传递链**：外部伤害 → SubPart → Part（折算）→ VehicleCore（累计）。
- **连接器对偶性**：一次连接涉及 2 个 Connector（同类型配对），每个 SubPart 持有一组。
- **序列化**：使用 Mojang Codec（JSON） + StreamCodec（网络）。`data/` 包处理全量序列化。
- **动画体归属**：`Part implements IAnimatable<Part>`，独占共享 `ModelPose`；由 `VehicleCore` 驱动 `Part.onTick()`（主线程发布）/ `Part.onPrePhysicsTick()`（物理线程混合），双端运行且两侧同门控（`inLoadedChunk`）。`setChanged()` 唯一发布者。
- **信号寻址**：`ISignalReceiver.getSignalAddress()` 参与路由，`getName()` 仅身份/日志。保留地址 `"local"` = Part、`"global"` = 装配体。
- **Molang 命名空间**：`local.*` = Part（耐久取 `rootSubPart`、按名寻址连接点/子系统），`global.*` = 装配体（HP/能源/信号存储）。
- **涂装 Part 级统一**：`Part.applyTexture()` 只做本地状态变更，广播由调用方负责（`SprayCanItem` / `PartPaintPayload`）。

## 反模式

- **严禁在主线程直接操作物理体**：始终使用 `getPhysicsLevel().submitImmediateTask(PPhase.ALL/PRE, ...)`。
- **严禁在 `partNet` 之外使用 `synchronized`**：仅 2 处存在。共享状态使用并发集合。
- **严禁混用 JME 和 JOML**：物理用 JME，渲染用 JOML。通过 `SparkMathKt.*` 转换。
- **严禁忽略 `DestroyableRigidObject.updateLock`**：同步期间置 `true` 防反馈循环。
- **严禁在遍历 SubPart 列表时修改**：使用快照迭代器或 `CopyOnWriteArraySet`。
- **严禁让 `MMPartEntity` 重新实现 `IEntityAnimatable`**：会产生第二个动画发布者，破坏共享 `ModelPose` 的插值（渲染冻结/抖动）。

## 已知问题

- **多线程物理 + 关节 = 崩溃**（VehicleCore.java）。临时方案：顺序 addToLevel。
- **耦合扭矩禁用**：`MotorSubsystem.coupleTorque = 0`，轮子停止时振荡。
