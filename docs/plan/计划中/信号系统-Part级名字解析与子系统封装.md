# 信号系统-Part 级名字解析与子系统封装

> 状态：计划中。容器定序已落地：`AbstractPart#subParts`、`SubPart#subsystems` 与 `SubPart#connectors` 是 `LinkedHashMap`，迭代序 = 内容包声明序。按名索引与交互区表没有插入序，理由分别在 §3.1 与 §3.6；存档 DTO 的键是名字、只按名定位，不参与任何迭代序。`AbstractPart#findSubsystem` / `#findConnector` / `#findInteractBox`、SubPart 子系统封装、座位定序（删除 `SeatOrder` 的几何排序）与子系统序号化均未实施；名字唯一性校验已在部件定义的解码期生效（§3.3），`checkContent` 与编辑器插件两层提前反馈待补。

## 1. 现状

信号投递有三条通路，它们对 SubPart 边界的处理各不相同：

| 通路 | 机制 | 跨 SubPart | 跨零件 | 依据 |
| --- | --- | --- | --- | --- |
| 具名目标 | `getTargetNames()` 里的子系统名 / 交互区名 / 连接点名 | ✗ | ✗ | `ISignalSender#getReceiversFromNames` 只查发送者所在 SubPart |
| 连接器端口 | 连接点两端的 `signal_targets` + 频道转译 | ✓ | ✓ | `SignalPort#getAttachedPort` / `#translateChannel` |
| `global` 总线 | `SubsystemController` 订阅集合按接受列表扇出 | ✓ | ✓ | `ISignalBus#broadcast` |

具名目标的作用域是**发送者所在的 SubPart**。多子零件 Part 里跨子零件引用名字时解析不到，失败形态是 `ISignalSender#getReceiversFromNames` 末尾的一条 ERROR 日志，信号静默丢失。

多子零件 Part 是少数但确实存在：86 份 `parts/*.json` 共 129 份变体定义，其中 13 份有 2 个以上子零件，最多 19 个（`m1a2_hull` 的 `desert` 变体）；`k17_turret` 4 个、`sdkfz234_turret` 3 个。炮塔上的座位、舱门、武器控制器分属不同子零件，跨件引用是常态而非边缘情况。

内容是已经在用交互区当具名目标的：`passenger_num_outputs.occupied` 的目标里就有 `interact.machine_max.seat` 与连接点名（`Machine-Max_Civilian/machine_max/parts/van/van_seat.json`）。这类引用目前落在同一子零件内；一旦跨件就会落空。

Part 层已经持有两张按名索引：

| 字段 | 写入点 | 读取方 |
| --- | --- | --- |
| `AbstractPart#connectorsByName` | `createConnectors` | `MechMolangContext` 的 `local.connector_*`、客户端 `HudPartSnapshot` |
| `AbstractPart#subsystemsByName` | `createSubsystems` | `MechMolangContext` 的 `local.subsystem_*`、客户端 `HudPartSnapshot` |

两者在构造期填充，重名时后者覆盖；`createSubsystems` 的 Javadoc 把“调用方应避免跨子零件重名”写成了约定，没有校验。交互区没有 Part 级索引，只有 `SubPart#interactBoxes`。

碰撞箱不在信号寻址空间内：`HitBox implements Target`，没有 `getSignalAddress()`，按子零件内索引与接触点访问（射线拾取、客户端命中特效的 `(subPartId, hitBoxKey)` 二元组）。

## 2. 目标与非目标

**目标**

- 具名目标的作用域从 SubPart 提升到 Part：子系统、交互区、连接点三类名字都在 Part 内解析。
- 名字查找只有一个入口：`AbstractPart` 的三个 `find*` 方法。子系统表只经宿主口交出值，不以可按名查的裸 Map 暴露；连接点表 `SubPart#connectors` 不在本轮封装（§3.2 末）。
- 名字在 Part 内唯一，且唯一性由校验拦下，而不是靠约定（解码期已经如此，本计划把同一判据补到构建期与导出期，§3.3）。
- 不为“索引”引入需要清理的长期缓存。

**非目标**

- 不改频道语义、不改端口与 `global` 两条通路。
- 不为碰撞箱建 Part 级索引。
- 不做玩家侧的工位配置（结论见附录 A）。

## 3. 设计

### 3.1 索引只活一次调用

不新建长期存活的 Part 级索引表。理由：任何常驻索引都要在构造、结构变化、子零件销毁三个时点维护，漏一处就是脏读；而名字解析只在**建首跳表**时发生（`setTargetFromNames`，由 attach、换组、结构变化触发），不需要缓存。

做法：

1. `AbstractPart` 提供三个查找方法，实现为遍历 `subParts` 现场查（每 Part 至多 19 个子零件）：

   ```java
   @Nullable public AbstractSubsystem findSubsystem(String name);
   @Nullable public AbstractConnector findConnector(String name);
   @Nullable public InteractBox     findInteractBox(String name);
   ```

   遍历序 = 内容包声明序（`subParts` 是 `LinkedHashMap`）。单变体内的重名在解码期就被拒绝（§3.3），一个 Part 实例因此不会持有两个同名对象，这个方法不需要并列分支：命中即返回。

2. Part 侧不设常驻索引字段：`connectorsByName` / `subsystemsByName` 在 S2 阶段移除，`MechMolangContext` 的 8 处读点改走 `find*`。

3. `setTargetFromNames` 在重建首跳表时把三类对象聚合一次，得到三张临时 Map 交给 `getReceiversFromNames`：

   ```java
   // 一次遍历 subParts，填充 subsystem / interactBox / port 三张表
   getReceiversFromNames(names, getSubPart(), partSubsystems, partInteractBoxes, partPorts);
   ```

   `getReceiversFromNames` 的形参类型与签名保持原样——那是交给 JS Hook 的公开契约，签名变化会破坏脚本侧；改造只换传进去的那张表的内容。

4. 聚合逻辑与 `find*` 各自遍历 `subParts`，两处形状不同（一组方法返回单对象，一处填三张表）。这是有意的取舍：不为共用一段循环而引入一个中间类型。

**不采用的方案**：缓存一张 Part 级总表。它在 `createSubsystems` / `createConnectors` 之后必须重建，而子零件销毁、子零件重建、装配体分裂都缺少现成的清理点；`subsystemsByName` 当前正是这种形态，重名静默覆盖已经存在。

### 3.2 SubPart 封装

`SubPart#subsystems` 改 private，暴露三个职责明确的口：

```java
/** 迭代用：销毁、存档、tick 收集。只有值，没有键。 */
public Collection<AbstractSubsystem> ownSubsystems();

/** 只查自己：网络包与存档按 (subPartId, 名字) 定位。 */
@Nullable public AbstractSubsystem findOwnSubsystem(String name);

/** 名字解析：委托 Part，信号与 MoLang 用这一条。 */
@Nullable public AbstractSubsystem findSubsystem(String name);
```

- `ownSubsystems()` 返回 `Collection` 而不是 `Map`：没有键就没有顺便按名查找的入口，“用错表”在写法上就不顺手。
- `findOwnSubsystem` 必须保留。8 个网络载荷 9 处（`ControlBindingPayload`、`ControlGroupSetEditPayload`、`GuiActionPayload`、`MovementInputPayload` 2 处、`SeatSwitchPayload`、`SeatSwitchResultPayload`、`SubsystemSyncPayload`、`ViewInputPayload`）携带的是 `(subPartId, 子系统名)`，要的是“**这个**子零件上的那个子系统”，精度高于 Part 级唯一性。存档侧同理：`SubPartData` 只存自己的子系统。
- `SubPart.destroy`、`SubPartData` 的遍历改走 `ownSubsystems()`。

两处机械障碍必须在同一次改动里处理：

- **字段改 private 不会关掉 getter**：`SubPart` 类上有 Lombok `@Getter`，它为该字段生成的 `public Map<String, AbstractSubsystem> getSubsystems()` 直接把整张表交给调用方。字段上要加 `@Getter(AccessLevel.NONE)`。
- **接口声明是第二个出口**：`ISubsystemHost#getSubsystems()` 直接声明 `Map<String, AbstractSubsystem>`，15 处整体取读点里的 `EngineSubsystem`、`GearboxSubsystem`、`MotorSubsystem`、`TransmissionSubsystem`、`MMPartEntity`、`WeldingTorchItem` 都靠它。接口收窄为 `Collection<AbstractSubsystem> ownSubsystems()`。

改造面（`subsystems.get(...)` 共 36 处 / 18 文件）：8 个网络载荷 9 处、GameTest 21 处 / 4 文件（`ViewpointGameTest` 9、`SignalPipelineGameTest` 7、`CameraDiscoveryGameTest` 4、`K17TurretGameTest` 1——它们在 `src/main`，必须编译）、客户端渲染 3 处（`VehicleInspectorRenderer`、`VehicleModelElement`、`AssemblyHud3D`）、共通 3 处（`AbstractPart` 的恢复路径、`MechPowerPort`、`GearboxSubsystem` 直接读字段）。

**连接点表不在本轮封装**：`SubPart#connectors` 保持 public `LinkedHashMap`。按名读取只有 `ConnectorSyncPayload` 与 MoLang 两处，且 §3.6 让它成为序号来源；把它一并封装会扩大 S1 的落点，收益只是与子系统对称。

### 3.3 名字唯一性的约束与校验

Part 内唯一的三类名字共用一套命名空间：`subsystem.<ns>.<name>`、`interact.<ns>.<name>`、`connector.<ns>.<name>`。`local` / `global` 为保留地址，不得用作上述任何一类的名字。

这条约束**已经在部件定义的校验期生效**，不需要另加一层运行期检查：

| 层 | 位置 | 行为 |
| --- | --- | --- |
| 解码期（权威） | `VariantAttr#validateUniqueNames`，在变体构造时执行 | 三类名字共用一个命名空间表，重名与保留地址都抛 `PackContentException`，消息带冲突双方（`子零件名/类别`）与模型路径 |
| 整批校验 | `PartDefinitions.validateAndPublish` → `AbstractPartType#validate` → `VariantAttr#validate` | 解码期抛异常的部件定义不进候选表；晚校验（骨骼区间、命中框、跨定义引用）的失败同样剔除，引用失效定义的定义连带剔除，诊断按文件上报 |
| 内容检查 | `./gradlew checkContent` | 构建期给出同一判据，不必启动游戏才发现 |
| 编辑器 | 随包 Blockbench 插件 `collectIssues` | 重名为 error 级，导出即报 |

作用域是**单变体的 `sub_parts` 集合**，不是 part 类型全局：同一类型的两个变体可以各自使用 `subsystem.machine_max.seat`，它们不会同时出现在一台载具上，Part 内因此唯一。运行期一个 Part 实例只对应一个变体，`find*` 的遍历不会遇到同名对象——§1 里 `subsystemsByName` 的“后写覆盖”在解码期校验之下本来就不该发生，缺的只是索引形态（§3.1）。

这一层的含义是：名字写错的代价是**那个部件类型在双端都不存在**，而不是“某一端少一台查不到的控制器”。两端读同一份字节、跑同一段校验，拒绝也一致。

当前内容扫描结果（86 份 `parts/*.json`、129 份变体定义）：单变体内零重名（不同子系统名 287 个、不同连接点名 363 个），T6 以“当前内容即通过”为基线。跨 Part 复用同名是正常用法：`subsystem.machine_max.driver_seat` 在 29 份部件定义里出现，Part 级唯一性管不到它（§3.7 因此不拿名字做座位排序键）。

### 3.4 名字与端口的分工

两条路都是作者显式选择，改造后共存：

- **写子系统名 / 交互区名**：直连，不经过连接点。适用于“这台座位控制那门炮”，不关心线从哪走。
- **写连接点名**：经 `SignalPort`，享受频道转译（`signal_translations`），并表达“信号从这条连接点过”。

由此产生一条要写进作者文档的语义：名字通路的连通性由“两个对象同属一个 Part”决定，不由连接点完整性决定；连接点断开、子零件脱离该 Part 后，名字自然解析不到。需要“线路断了信号就断”的表达时写连接点名。

### 3.5 生命周期

- 首跳表在 `onAttach`、`setControlGroupSet`、结构变化两阶段（`onVehicleStructurePreChanged` → `onVehicleStructureChanged`）时整体重建，聚合随重建发生，没有独立清理点。
- 物理线程不读首跳表，聚合只发生在主线程。

### 3.6 子系统序号（仅线上）

名字同时是翻译键（`TextUtil#getTranslation`）和存档键，不能为了省字节去缩写。第一方内容包的实测：不同子系统名 287 个，平均 33.8 个 UTF-8 字节、最长 50（`subsystem.machine_max.reinforced_container_16x16x8`），`writeUtf` 后约 35 字节；不同连接点名 363 个，平均 34.8、最长 47（`connector.machine_max.right_front_wheel_chassis`）。会话内序号用 varint 是 1 字节。

**序号的来源是声明序，不是额外传输。** 内容定义由 Spark-Core 在 configuration 阶段整包下发（`SparkPackageSendingTask` → `SparkPackagePayload` → 客户端 `readPackageContent` / `injectPackageContent`），传的是部件的原始文件字节，双端用同一个 `Codec` 解同一份 JSON，构造序因此必然相同。序号只是个本机量：`subsystems` 是 `LinkedHashMap`，迭代位置就是声明序，由 `createSubsystems` 在写入时赋给子系统一个只读 `int`，容器侧提供 `subsystemAt(int)` 反查（线性扫，子零件内至多几十个）。不建平行索引表。

**哪些字段换**：

| 载荷 / 类型 | 现状 | 换后 | 频次 |
| --- | --- | --- | --- |
| `SubsystemSyncPayload` | `int id` + `writeUtf(子系统名)` + 脏值 | `varint` 序号 + 脏值 | 每次脏值变化，广播全维度 |
| `ConnectorSyncPayload` | 同形，`writeUtf(连接点名)` | `varint` 序号 + 脏值 | 同上 |
| `MovementInputPayload`、`ControlBindingPayload`、`GuiActionPayload`、`ViewInputPayload`、`ControlGroupSetEditPayload`、`SeatSwitchPayload`、`SeatSwitchResultPayload` | `int subPartId` + `writeUtf(子系统名 / 座位名)` | `varint` + `varint` | 每 tick / 每次操作 |
| `IAmmoSupplier.SupplierRef`、`ReloadJobKey`（同步值） | `VAR_INT` + `STRING_UTF8` | 保持名字（见下） | 作业变化时 |

两类名字长度基本持平（平均 33.8 与 34.8 字节），一起切的理由是同一形改造、一次协议版本变更覆盖两处，不是哪一处更亏。

`SupplierRef` 与 `ReloadJobKey` **不换**，保持 `VAR_INT` + `STRING_UTF8`。两者的 `STREAM_CODEC` 都是无上下文的 `StreamCodec<ByteBuf, T>`（`StreamCodec.composite` 直接拼字节），解码端手上没有 `SubPart`，没有对象能把序号换回名字；而下面硬约束 2 又要求序号不进 `equals` / `hashCode` / `compareTo`，记录里必须留着名字——两条同时成立时“名字换序号”无处落地。频率与代价也不对称：它们只在作业开始与结束、结构变化、玩家加入时整包下发，不像 `*SyncPayload` 那样每次脏值变化都广播，省下的约 35 字节/包不值得改动一条已被测试固定住的排序契约。将来若要动，前提是先把硬约束 2 改成“比较按声明序”。

`SubsystemInteractPayload` 是独立的一档：它按 `subPart.interactBoxes.get(interactBoxName)` 定位，名字属于交互区命名空间，不走子系统表。交互区也是三类里唯一没有现成序号来源的：`InteractBoxes` 继承 `ConcurrentHashMap`（物理宿主需要并发写），JDK 没有带插入序的并发 Map。它的序号应取 `SubPartAttr#interactBoxes` 的键位——那是内容定义表，接收方按位取键名再查 `interactBoxes`。这条随该载荷的阶段单独做（S7），不改 `InteractBoxes` 的继承结构。

“键位 = JSON 字段序”依赖两个前提，写在这里备查：`SubPartAttr` 用 `Codec.unboundedMap` 解 `interact_boxes`，DFU 按输入顺序建表；并且 Spark-Core 传的就是原始文件字节，双端解的是同一份。将来若有 datagen 重排 JSON、NBT 往返或别的加载路径，序号会静默错位。

**四条硬约束**：

1. 序号不进 `saveData` / `CODEC`：`SubPartData#subsystemData` 的键继续是名字，存档、蓝图、物品数据组件不受影响。
2. 序号不进 `equals` / `hashCode` / `compareTo`：`SupplierRef`、`ReloadJobKey` 的比较器按名字排序，“按元组升序撤销预留”那条契约不随序号分配变化。
3. 只用在接收方必然用同一份定义重建了同一个 `SubPart` 的链路；跨机（`BlueprintExtractLocalPayload`）与跨存档一律用名字。
4. 越界即整包丢弃并记一条日志，不回退按名字再查——回退会把“序号错了”变成“有时对有时错”。

### 3.7 定序：座位枚举（删除 `SeatOrder` 的几何排序）

`SeatOrder` 现在按 `(sy, sx, seatName, subPartId)` 给座位排序，`sy` / `sx` 是把 locator 世界位姿投影到参考件局部坐标系得到的水平坐标。三个后果：

| 问题 | 为什么 |
| --- | --- |
| 炮塔上的座位随方位角改变名次 | 座位在参考系里的位置是实时位姿，转动的炮塔让座位扫过其它座位，排序键跟着变。开着车看座位图，编号会跳 |
| 存档前后顺序可能不同 | `seats()` 遍历 `vehicle.partMap`（`ConcurrentHashMap`）；`pickReference` 用 `HashMap<SubPart, ClusterInfo>` 且 `SubPart` 未覆写 `equals` / `hashCode`（身份哈希序），“取第一个含 driver 座位的簇”随会话变化，参考系一换全部编号轮转或镜像 |
| 并列时落到会话计数器 | 最终 tie-break 是 `SubPart#getId()`，那是 `DestroyableObject#ENTITY_COUNTER`，每次载入都重新分配 |

并列并不罕见：`subsystem.machine_max.driver_seat` 被 29 份部件定义复用，合体之后多节车体各有一个同名座位；量化步长 0.05 m 内一旦并列就落到 `subPartId` 上。

**决定：删除 `SeatOrder` 的几何排序**（整个类一并删除，见下），座位顺序只由结构决定：

```
部件 UUID 升序 → 子零件声明序 → 子系统声明序
```

这是“同一个 Part 内的座椅先按子零件、再按子系统”的依据，跨 Part 用 UUID 决定，不需要为座位另建一张表。它不读位姿、不读名字、不读会话计数器，因此炮塔转动、悬挂起伏都改变不了编号；存档恢复后 UUID 不变，编号也不变。

落地位置：

| 现在 | 去处 |
| --- | --- |
| `SeatOrder#seats` | `VehicleCore#getAllSeats()`（`getEmptySeats` / `getFirstEmptySeat` / `getFirstSeat` 一并改走它） |
| `SeatOrder#next` | `VehicleCore#nextSeat(from, forward)`：按同一份列表回绕一圈，跳过自身与不可用座位 |
| `SeatOrder#available` | `SeatSubsystem#isAvailable()`：座位与宿主都还在、宿主已有代理实体、未被占用；只读 `occupied`，不读 `passenger` |
| `SeatOrder#layout` 的参考件选择 | 客户端 `SeatMapLayout`（唯一消费方）：座位图仍需要一个固定局部坐标系把标记投影到载具模型上，但参考件只决定这个坐标系，不参与排序。簇表改 `LinkedHashMap`，并列取先枚举者，删掉 `subPartId` |
| `SeatOrder#order`、`COORD_QUANTUM`、`FRONT_AXIS`、`quantize`、`Keyed` | 删除 |

顺带要改的注释与调用点：`SeatSwitchPayload` 的类注释（“本地按几何顺序算出下一个可用座位”）、`SeatSwitchClient`、`HudVehicleSnapshot`、`SeatOrderGameTest`（类名与批名随 `SeatOrder` 一起改，只保留与排序无关的用例）。

一个诚实的边界：UUID 在恢复存档时不变，但**用同一份蓝图重新放置**会生成新 UUID，编号可能变。编号只承诺“同一台载具在其生命周期内稳定”，跨重建不承诺。

顺带记录、本轮不改：参考件选择里的 `isDriverName` 用翻译键子串 `"driver"` 判驾驶员座位，`subsystem.machine_max.azimuth_driver` 这类伺服键同样命中，只因外面套了 `instanceof SeatSubsystem` 才没有误判。本轮不改：显式判据要么取控制组预设的角色，要么给座位型号加标记字段，两者都要动内容 schema，另起一处改。

## 4. 迁移清单

| 位置 | 现状 | 目标 |
| --- | --- | --- |
| `SubPart#subsystems` | `public final LinkedHashMap`，类级 `@Getter` 生成 `getSubsystems()` | 字段改 private 并加 `@Getter(AccessLevel.NONE)`，暴露 `ownSubsystems()` / `findOwnSubsystem()` / `findSubsystem()` |
| `ISubsystemHost#getSubsystems()` | 接口声明返回 `Map<String, AbstractSubsystem>` | 收窄为 `Collection<AbstractSubsystem> ownSubsystems()` |
| `SubPart.destroy`、`SubPartData` 构造器 | 直接遍历字段 | 遍历 `ownSubsystems()` |
| 8 个网络载荷 / 9 处 | `subPart.subsystems.get(name)` | `subPart.findOwnSubsystem(name)`，并补目标缺失时的丢弃与日志 |
| 客户端渲染 3 处（`VehicleInspectorRenderer`、`VehicleModelElement`、`AssemblyHud3D`） | `subsystems.get(name)` | `findOwnSubsystem(name)` |
| 共通 3 处（`AbstractPart` 恢复路径、`MechPowerPort`、`GearboxSubsystem`） | `subsystems.get(...)` / 直接读字段 | `findOwnSubsystem` |
| GameTest 4 文件 21 处 | `subsystems.get(...)` | 同上；这批在 `src/main`，随 S1 一起编译 |
| `AbstractPart#connectorsByName` / `#subsystemsByName` | 常驻 HashMap，构造期填充 | 删除，改 `findConnector` / `findSubsystem` |
| `MechMolangContext` 8 处 | 读上述两张表 | 改 `find*` |
| `HudPartSnapshot` 2 处 | 遍历上述两张表 | 遍历 `ownSubsystems()` 与子零件连接点 |
| `ISignalSender#setTargetFromNames` | 从发送者所在 SubPart 取表 | 从 Part 聚合三张表 |
| `ISignalSender#getReceiversFromNames` | 形参是 SubPart 级表 | 形参类型与签名不变，传入的表改为 Part 级聚合 |
| `signal/AGENTS.md`、`操作权与越级优先级设计` §8.1、`动画系统` §6.5.3 | 名字解析作用域 = SubPart，跨 SubPart 名字解析列为待办 | 作用域 = Part；端口与名字的分工写清楚 |
| `SeatOrder`（整个类） | 几何排序、参考系与循环换座混在一个类里 | 删除；三件事分别进 `VehicleCore#getAllSeats()` / `#nextSeat()`、`SeatSubsystem#isAvailable()`、客户端 `SeatMapLayout` |
| 子系统寻址（8 个网络载荷 9 处） | `(subPartId, 子系统名)` | `(subPartId, 序号)`；存档与跨机路径保持名字 |
| `IAmmoSupplier.SupplierRef`、`ReloadJobKey`（同步值） | `(宿主 ID, 子系统名)` 的名字形态 | 保持名字（§3.6） |
| `ConnectorSyncPayload`、`SubsystemInteractPayload` | `(subPartId, 连接点名 / 交互区名)` | `(subPartId, 序号)`；交互区序号取 `SubPartAttr#interactBoxes` 的键位 |

## 5. 分阶段交付

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| S1 | `SubPart#subsystems` 封装（`AccessLevel.NONE` + 接口收窄），加三个访问口；36 处按名取与 15 处整体取全部落到新口上；两个 `*SyncPayload` 顺带补目标缺失时的丢弃 | 全部落点编译通过；`./gradlew --offline test` 与 `runGameTestServer` 全绿，行为不变 |
| S2 | `find*` 三个方法 + 删除两张常驻索引；MoLang 与 HUD 快照改读 | `./gradlew --offline test` 全绿；`ViewpointGameTest`、`SignalPipelineGameTest` 等既有用例不回归 |
| S3 | `setTargetFromNames` 改聚合 Part 级表；`checkContent` 与插件补重名判据（解码期校验已存在，这两层只是把同一判据提前到构建期与导出期） | 新增 GameTest：多子零件 Part 内跨子零件具名投递可达；新增 JUnit：内容里不存在跨子零件的三类重名；`./gradlew --offline checkContent` 通过 |
| S4 | 文档：`signal/AGENTS.md`、`操作权与越级优先级设计` §8.1、`动画系统` §6.5.3 的待办标记 | 三处表述一致，无“以旧状态为基线”的表述 |
| S5 | 删除 `SeatOrder`：枚举与循环换座进 `VehicleCore`，可用性进 `SeatSubsystem`，参考件选择进 `SeatMapLayout` | 用例见 T7；`SeatOrderGameTest` 改名并只留与排序无关的用例 |
| S6 | 子系统与连接点序号：构造期赋值 + `subsystemAt(int)` / `connectorAt(int)`，先切 `SubsystemSyncPayload` / `ConnectorSyncPayload`，再切 7 个输入类载荷；`SupplierRef` / `ReloadJobKey` 保持名字 | 双端同步值落到同一个子系统或连接点；序号越界时整包丢弃并留日志 |
| S7 | 交互区序号：取 `SubPartAttr#interactBoxes` 键位，切 `SubsystemInteractPayload` | 右键交互落在同一个交互区；`InteractBoxes` 的容器与继承结构不变 |

S1 与 S3 可以分开提交：S1 只是封装，不改行为，独立可回滚。S5—S7 不依赖 S1—S4，可独立先做。

## 6. 验收用例

| 编号 | 用例 | 判据 |
| --- | --- | --- |
| T1 | 座位与武器控制器分属同一 Part 的两个子零件，控制组具名指向该控制器 | 发现得到该控制器；投递后其输入槽有值 |
| T2 | 同一频道上两台控制器，名字不同 | 两组各自只到达自己的那一台 |
| T3 | 构造一份单变体内重名的部件 JSONC（同一名字同时用作子系统与连接点名） | 解码抛 `PackContentException`，该定义不进候选表，发布表不含它；诊断带冲突双方与所属文件 |
| T4 | 网络包按 `(subPartId, 子系统名)` 定位（S1 阶段） | 落在该子零件的子系统上，不被同名兄弟顶替；S6 之后同一个用例换成按序号定位 |
| T5 | 子零件被销毁后重建 | 名字解析到新实例，旧实例不再可达 |
| T6 | 内容扫描 | 全部第一方部件无跨子零件三类重名（当前 86 份定义扫描为 0） |
| T7 | 同一装配体反复调用 `getAllSeats()` / `nextSeat`；几何上并列的两个座位；同一存档恢复两次 | 逐位相同；与 `SubPart#id` 的分配顺序无关；并列时按声明序；两次恢复的顺序一致 |
| T8 | `SubsystemSyncPayload` / `ConnectorSyncPayload` 按序号落地 | 客户端命中的是服务端所指的那个子系统或连接点；序号越界时整包丢弃并留日志 |
| T9 | 同步包目标缺失（S1 起按名字、S6 起按序号） | 丢弃整包并记一条日志，不 NPE |

T7 不能写成“同一份模板构造两次结果相同”：新建部件的 UUID 是随机生成的，两次构造拿到不同的 UUID，并列座位的先后本来就允许不同。稳定性只对同一台载具（含其存档往返）成立。

## 7. 风险与未决

- **唯一性是内容契约，且已由校验强制执行**：违反的解码期即抛 `PackContentException`，那个部件类型在双端都不出现。风险因此落在“作者写完后发现该类型不见了”，靠 `checkContent` 与插件把同一判据提前到构建期与导出期。
- **连接点定序决定配对优先级**：`comboAttachConnector` 与 `autoAttachInternalConnectors` 都按迭代序尝试候选，`connectors` 是声明序，所以几何上同时成立多个候选时“先配上的那一对”取作者在 JSON 里的书写顺序。内部连接对多候选冲突显式跳过，结果与顺序无关；跨部件装配在多候选时结果可能变化。
- **声明序是内容可改的**：`subsystems` 已是 `LinkedHashMap`，迭代序 = 声明序；作者在 JSON 对象中间插入一条，其后所有序号整体后移。这只影响线上编号与 UI 编号，不影响身份（身份仍是名字），而序号只在同一次连接内存在。
- **座位编号只承诺同一台载具内稳定**：部件 UUID 在存档往返里不变，用蓝图重新放置会换 UUID，编号可能变（§3.7）。
- **端口与名字并存的解释成本**：作者需要知道两者差异，靠文档与 `checkContent` 的提示解决，不加运行时诊断。
- **JS Hook 兼容**：`ScriptableSubsystem#getReceiversFromNames` 的覆写签名保持原样是硬约束；改造只换传入的表内容。

## 附录 A：玩家侧工位配置（不在本轮）

以下结论来自同期的设计讨论，作为后续文档的输入，本轮不实现：

- **粒度分层**：内容包按频道名声明可达上界（`["global"]` 即全部接受者）；玩家在可达集内做减法与编组，不能相加。加法会让玩家绕过“车长超控”这类设计意图。
- **实例键是唯一可用的精确键**：`(频道, 首跳名)` 在两台同名控制器上不可区分，UI 勾一个会中两个。可用的持久键是 `(part UUID, 子零件名, 子系统名)`；`SubPart#id` 是会话内计数器（`DestroyableObject#ENTITY_COUNTER`），不能用于存档。
- **存储位置**：配置存座位自己的 `saveData()`，随座位与蓝图走；引用的零件消失时静默丢弃条目、回落到预设。不需要载具级总表，也不需要新的 int id。
- **生效点**：接收者侧的准入钩子（默认放行，在 `broadcast` 与端口两处扇出写槽之前询问），不改发送路径，避免丢掉 `global` 存储写入与端口转译。
- **权限**：现有编辑校验是“你正坐在这个子系统上”。改成允许玩家配置之后，这条等同于“座位权限 = 编辑权限”，需要收紧到车主/车长或服务器配置。

## 修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| v1 | 2026-11-16 | 首版：Part 级名字解析、SubPart 封装、唯一性校验、分阶段交付与附录 A |
| v2 | 2026-11-16 | 容器定序落地并写入现状（`subParts`、`subsystems`、`connectors`）；新增 §3.6 子系统与连接点序号（仅线上）、§3.7 座位枚举与排序；补迁移清单、S5—S7、T7/T8，改写“遍历顺序”一节 |
| v3 | 2026-11-16 | 按外部审阅订正：内容统计改为实测（86 份定义 / 129 份变体 / 13 份多子零件 / 至多 19 个；子系统名 287 个、平均 33.8、最长 50；连接点名 363 个、平均 34.8、最长 47），删掉“连接点名更长、单包节省更大”的结论；载荷计数改为 8 个 / 9 处并补 `SeatSwitchPayload` / `SeatSwitchResultPayload`；补 `@Getter` 与 `ISubsystemHost` 两处机械障碍、S1 的 36 + 15 处落点与 GameTest 批次；`SupplierRef` / `ReloadJobKey` 改为待定并说明无上下文 `StreamCodec` 的限制；§4 的 `HashMap` 改回 `LinkedHashMap`；§3.7 重写为“删除 `SeatOrder` 的几何排序、座位顺序 = 声明序”，T7 改为同一实例/同一存档口径，新增 T9 |
| v4 | 2026-11-16 | 定案：`SupplierRef` / `ReloadJobKey` 不换序号（§3.6 给出频率与契约理由，S6 收窄到两个 `*SyncPayload` 与 7 个输入类载荷）；唯一性校验确认已在解码期（`VariantAttr#validateUniqueNames` → `PackContentException` → `PartDefinitions.validateAndPublish` 只发布有效子集）生效，删掉“加载期打 ERROR 后继续运行”与运行期并列分支的表述，S3 降为 `checkContent` + 插件，T3 改为解码期拒绝用例；`isDriverName` 明确本轮不改 |
