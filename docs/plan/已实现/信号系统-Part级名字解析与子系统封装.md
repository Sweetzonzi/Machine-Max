# 信号系统-Part 级名字解析与子系统封装

> 状态：已实现。S1—S7 全部落地：具名目标的作用域是 Part；`SubPart#subsystems` 封装为 `ownSubsystems` / `findOwnSubsystem` / `findSubsystem` 三个口；按名查找只有 `AbstractPart` 的 `findSubsystem` / `findConnector` / `findInteractBox` 三个现场遍历方法，没有常驻索引；`checkContent` 与两个测试补上三类名字唯一性判据（解码期校验与编辑器插件原本就有）；座位顺序只由结构决定（`VehicleCore#getAllSeats()` / `#nextSeat()`）；9 个网络载荷（子系统、座位/视点、连接点）的线上寻址是 `(子零件 ID, 声明序序号)`，`SubsystemInteractPayload` 保持交互区名，名字只用于翻译、存档与日志。容器定序：`AbstractPart#subParts`、`SubPart#subsystems` 与 `SubPart#connectors` 是 `LinkedHashMap`，迭代序 = 内容包声明序。按名索引、交互区表没有插入序：前者见 §3.1，后者是交互区不序号化的原因（§3.6）；存档 DTO 的键是名字、只按名定位，不参与任何迭代序。

## 1. 现状

信号投递有三条通路，它们对 SubPart 边界的处理各不相同：

| 通路 | 机制 | 跨 SubPart | 跨零件 | 依据 |
| --- | --- | --- | --- | --- |
| 具名目标 | `getTargetNames()` 里的子系统名 / 交互区名 / 连接点名 | ✓ | ✗ | `ISignalSender#setTargetFromNames` 聚合发送者所属整个 Part |
| 连接器端口 | 连接点两端的 `signal_targets` + 频道转译 | ✓ | ✓ | `SignalPort#getAttachedPort` / `#translateChannel` |
| `global` 总线 | `SubsystemController` 订阅集合按接受列表扇出 | ✓ | ✓ | `ISignalBus#broadcast` |

具名目标的作用域是**发送者所属的部件（Part）**：`setTargetFromNames` 建首跳表时一趟现场遍历 `subParts`，把子系统、交互区、连接点三类名字聚合成三张临时表交给 `getReceiversFromNames`，不保留常驻索引。名字在 Part 内唯一（§3.3），因此跨子零件的引用可达，而跨零件的名字引用仍解析不到（失败形态是 `getReceiversFromNames` 末尾的一条 ERROR 日志）。

多子零件 Part 是少数但确实存在：86 份 `parts/*.json` 共 129 份变体定义，其中 13 份有 2 个以上子零件，最多 19 个（`m1a2_hull` 的 `desert` 变体）；`k17_turret` 4 个、`sdkfz234_turret` 3 个。炮塔上的座位、舱门、武器控制器分属不同子零件，跨件引用是常态而非边缘情况。

内容是已经在用交互区当具名目标的：`passenger_num_outputs.occupied` 的目标里就有 `interact.machine_max.seat` 与连接点名（`Machine-Max_Civilian/machine_max/parts/van/van_seat.json`）。

Part 层不持有任何按名索引表：按名读取只有 `AbstractPart#findSubsystem` / `#findConnector` / `#findInteractBox` 三个现场遍历方法（§3.1）。子系统表由宿主口交出值（`SubPart#ownSubsystems()`），不以可按名查的裸 Map 暴露；子零件内按名定位走 `SubPart#findOwnSubsystem`（存档恢复、MoLang、能源端口、命中箱子系统绑定用）。连接点表 `SubPart#connectors` 保持 public `LinkedHashMap`，不在本轮封装（§3.2 末），它的迭代序同时是线上序号的来源（§3.6）。

线上寻址与名字分开：9 个网络载荷（子系统、座位/视点、连接点）的类注释与编解码按 `(子零件 ID, 声明序序号)` 定位（§3.6），载荷里不出现子系统名、座位名或连接点名；`SubsystemInteractPayload` 保持 `(子零件 ID, 交互区名)`（§3.6 说明理由）。名字用于翻译键、存档键与日志，`IAmmoSupplier.SupplierRef` 与 `ReloadJobKey` 也按名字编码（§3.6 给出理由）。

碰撞箱不在信号寻址空间内：`HitBox implements Target`，没有 `getSignalAddress()`，按子零件内索引与接触点访问（射线拾取、客户端命中特效的 `(subPartId, hitBoxKey)` 二元组）。

## 2. 目标与非目标

**目标**

- 具名目标的作用域是 Part：子系统、交互区、连接点三类名字都在 Part 内解析。（S3 已落地）
- 名字查找只有一个入口：`AbstractPart` 的三个 `find*` 方法。子系统表只经宿主口交出值，不以可按名查的裸 Map 暴露；连接点表 `SubPart#connectors` 保持 public `LinkedHashMap`（§3.2 末）。（S2 已落地）
- 名字在 Part 内唯一，且唯一性由校验拦下，而不是靠约定：解码期、`checkContent`、编辑器插件三层同判据（§3.3）。（三层均已落地）
- 线上寻址不传名字：子系统与连接点的载荷改为 `(子零件 ID, 声明序序号)`，序号越界即整包丢弃；`SubsystemInteractPayload` 保持交互区名（§3.6）。（S6 已落地；S7 收窄为保持名字）
- 不为“索引”引入需要清理的长期缓存。（S1—S7 未引入）

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

2. Part 侧不设常驻索引字段：`AbstractPart` 上没有 `connectorsByName` / `subsystemsByName` 这类常驻表；`MechMolangContext` 的 8 处按名读点与客户端 `HudPartSnapshot` 的遍历都走 `find*` / `ownSubsystems()`。

3. `setTargetFromNames` 在重建首跳表时把三类对象聚合一次，得到三张临时 Map 交给 `getReceiversFromNames`：

   ```java
   // 一次遍历 subParts，填充 subsystem / interactBox / port 三张表
   getReceiversFromNames(names, getSubPart(), partSubsystems, partInteractBoxes, partPorts);
   ```

   `getReceiversFromNames` 的形参类型与签名保持原样——那是交给 JS Hook 的公开契约，签名变化会破坏脚本侧；改造只换传进去的那张表的内容。

4. 聚合逻辑与 `find*` 各自遍历 `subParts`，两处形状不同（一组方法返回单对象，一处填三张表）。这是有意的取舍：不为共用一段循环而引入一个中间类型。

**不采用的方案**：缓存一张 Part 级总表。它在构造期填充后，子零件销毁、子零件重建、装配体分裂都缺少现成的清理点；`AbstractPart` 曾经持有 `subsystemsByName` / `connectorsByName` 两张这类表（重名静默覆盖），已随本项改动删除。

### 3.2 SubPart 封装

`SubPart#subsystems` 是 private 字段（类级 Lombok `@Getter` 之外单独加 `@Getter(AccessLevel.NONE)`），暴露三个职责明确的口，外加一个构造期安装口：

```java
/** 迭代用：销毁、存档、tick 收集。只有值，没有键。 */
public Collection<AbstractSubsystem> ownSubsystems();

/** 只查自己：存档恢复、MoLang、能源端口按 (subPartId, 名字) 定位。 */
@Nullable public AbstractSubsystem findOwnSubsystem(String name);

/** 名字解析：委托 Part，信号与 MoLang 用这一条。 */
@Nullable public AbstractSubsystem findSubsystem(String name);

/** 构造期安装：按声明序写入容器并订阅所属 Part 的 local 总线。 */
public void installSubsystem(String name, AbstractSubsystem subsystem);
```

- `ownSubsystems()` 返回 `Collection` 而不是 `Map`：没有键就没有顺便按名查找的入口，“用错表”在写法上就不顺手。
- `findOwnSubsystem` 必须保留。网络载荷按 `(subPartId, 序号)` 定位（§3.6），而存档恢复（`AbstractPart#restoreState`）、MoLang、`MechPowerPort`、`HitBox` 的子系统绑定、`Engine` / `Gearbox` / `Motor` / `Transmission` 的动力寻址按名字，要的都是“**这个**子零件上的那个子系统”。
- `SubPart.destroy`、`SubPartData` 的遍历改走 `ownSubsystems()`。
- S6 在同一个类上补了两个序号口（`subsystemIndex` / `subsystemAt`、`connectorIndex` / `connectorAt`），见 §3.6；交互区不序号化。

两处机械障碍在同一改动里处理：

- **字段改 private 不会关掉 getter**：`SubPart` 类上有 Lombok `@Getter`，它原本为该字段生成的 `public Map<String, AbstractSubsystem> getSubsystems()` 会把整张表交给调用方。字段上因此加了 `@Getter(AccessLevel.NONE)`。
- **接口声明是第二个出口**：`ISubsystemHost` 原本直接声明 `Map<String, AbstractSubsystem> getSubsystems()`，`EngineSubsystem`、`GearboxSubsystem`、`MotorSubsystem`、`TransmissionSubsystem`、`MMPartEntity`、`WeldingTorchItem` 等整体取读点都靠它。接口已收窄为 `Collection<AbstractSubsystem> ownSubsystems()` 并补 `findOwnSubsystem`。

改造面（`subsystems.get(...)` 共 36 处 / 18 文件）：8 个网络载荷 9 处、GameTest 21 处 / 4 文件（`ViewpointGameTest` 9、`SignalPipelineGameTest` 7、`CameraDiscoveryGameTest` 4、`K17TurretGameTest` 1——它们在 `src/main`，必须编译）、客户端渲染 3 处（`VehicleInspectorRenderer`、`VehicleModelElement`、`AssemblyHud3D`）、共通 3 处（`AbstractPart` 的恢复路径、`MechPowerPort`、`GearboxSubsystem` 直接读字段）。

**连接点表不在本轮封装**：`SubPart#connectors` 保持 public `LinkedHashMap`。按名读取只剩 MoLang 一处；它的迭代序是 `ConnectorSyncPayload` 的序号来源（§3.6），封装会把序号口一并卷进来，而收益只是与子系统对称。

### 3.3 名字唯一性的约束与校验

Part 内唯一的三类名字共用一套命名空间：`subsystem.<ns>.<name>`、`interact.<ns>.<name>`、`connector.<ns>.<name>`。`local` / `global` 为保留地址，不得用作上述任何一类的名字。

这条约束**已经在部件定义的校验期生效**，不需要另加一层运行期检查：

| 层 | 位置 | 行为 |
| --- | --- | --- |
| 解码期（权威） | `VariantAttr#validateUniqueNames`，在变体构造时执行 | 三类名字共用一个命名空间表，重名与保留地址都抛 `PackContentException`，消息带冲突双方（`子零件名/类别`）与模型路径 |
| 整批校验 | `PartDefinitions.validateAndPublish` → `AbstractPartType#validate` → `VariantAttr#validate` | 解码期抛异常的部件定义不进候选表；晚校验（骨骼区间、命中框、跨定义引用）的失败同样剔除，引用失效定义的定义连带剔除，诊断按文件上报 |
| 内容检查 | `./gradlew checkContent` | 扫描各包 `parts/` 下每个变体（含 `parts/prop/` 一层分组），重名与保留地址按 error 报出，消息带文件、冲突双方；构建期就能看到 |
| 编辑器 | 随包 Blockbench 插件 `collectIssues` | 重名与保留地址为 error 级，导出即报 |

作用域是**单变体的 `sub_parts` 集合**，不是 part 类型全局：同一类型的两个变体可以各自使用 `subsystem.machine_max.seat`，它们不会同时出现在一台载具上，Part 内因此唯一。运行期一个 Part 实例只对应一个变体，`find*` 的遍历不会遇到同名对象，命中即返回，不需要并列分支。

这一层的含义是：名字写错的代价是**那个部件类型在双端都不存在**，而不是“某一端少一台查不到的控制器”。两端读同一份字节、跑同一段校验，拒绝也一致。

当前内容扫描结果（86 份 `parts/*.json`、129 份变体定义）：单变体内零重名（不同子系统名 287 个、不同连接点名 363 个），T6 由 `PartSignalNameUniquenessTest` 固定，`checkContent` 每次构建同判据扫一遍。跨 Part 复用同名是正常用法：`subsystem.machine_max.driver_seat` 在 29 份部件定义里出现，Part 级唯一性管不到它（§3.7 因此不拿名字做座位排序键）。

### 3.4 名字与端口的分工

两条路都是作者显式选择，改造后共存：

- **写子系统名 / 交互区名**：直连，不经过连接点。适用于“这台座位控制那门炮”，不关心线从哪走。
- **写连接点名**：经 `SignalPort`，享受频道转译（`signal_translations`），并表达“信号从这条连接点过”。

由此产生一条要写进作者文档的语义：名字通路的连通性由“两个对象同属一个 Part”决定，不由连接点完整性决定；连接点断开、子零件脱离该 Part 后，名字自然解析不到。需要“线路断了信号就断”的表达时写连接点名。

### 3.5 生命周期

- 首跳表在 `onAttach`、`setControlGroupSet`、结构变化两阶段（`onVehicleStructurePreChanged` → `onVehicleStructureChanged`）时整体重建，聚合随重建发生，没有独立清理点。
- 物理线程不读首跳表，聚合只发生在主线程。

### 3.6 子系统与连接点的线上序号

线上寻址不传名字：子系统与连接点各用自己的**声明序序号**，载荷里是 `(子零件 ID, 序号)` 二元组。名字是翻译键、存档键与日志身份，不在线上出现。`SubsystemInteractPayload` 是例外，保持交互区名（理由见本节末）。

**实测代价**：第一方内容里不同子系统名 287 个、平均 33.8 个 UTF-8 字节、最长 50（`subsystem.machine_max.reinforced_container_16x16x8`，`writeUtf` 后约 35 字节）；不同连接点名 363 个、平均 34.8、最长 47（`connector.machine_max.right_front_wheel_chassis`）。序号是 1 字节 varint：单个子零件内最多 18 个子系统与 18 个连接点；全部子零件摊平后最多的部件是 `m1a2_turret` 的 `desert` 变体，39 个子系统、39 个连接点，仍在 127 以内。

**序号的作用域是子零件，不是 Part。** 载荷已经带了子零件 ID，序号只需在该容器内唯一；名字要求 Part 级唯一是给“裸名寻址”用的，序号带了容器限定，不需要 Part 级编号。选子零件级还有一条正确性理由：清空单位与容器一致。`SubPart#destroy` 一次清掉本子零件的 `subsystems`（`connectors` 只销毁连接点对象、表项不删），`installSubsystem` 只增不删，所以“迭代位置”对存活对象稳定。若摊平成 Part 级位置序，存量存档恢复时服务端会销毁存档里缺失的新增子零件并清空它的子系统表，而发给客户端的 `SubPartData` 按 `subParts` 表序列化、仍含该子零件名，客户端不会销毁它——服务端枚举跳过那批子系统、客户端不跳过，其后所有 Part 级序号整体错位，同步值会静默落到相邻子零件的另一个子系统上。

**序号是怎么算出来的：现场线性扫，不加字段。** 两个容器都是声明序 `LinkedHashMap`，位置就是迭代位置；`SubPart` 提供两对方法，发送侧取位置、接收侧取第 i 个：

```java
public int subsystemIndex(AbstractSubsystem subsystem);      // -1 = 不在本子零件
@Nullable public AbstractSubsystem subsystemAt(int index);   // null = 越界
public int connectorIndex(AbstractConnector connector);
@Nullable public AbstractConnector connectorAt(int index);
```

**交互区不序号化。** 交互区活表 `InteractBoxes` 的写入只有两处：`SubPart` 构造期逐个 `put`，与销毁期 `destroy()` 里一次 `clear()`；但**读跨线程**——`LivingEntityEyesightAttachment` 把射线检测投到 `submitImmediateTask(PPhase.PRE)`，在物理线程里调 `findInteractBox`，`collision(...)` 回调也在物理线程里调 `getInteractBox`。因此它是「物理线程读、主线程终态清空」的并发容器，没有声明序；要拿序号只能从内容定义表 `SubPartAttr#interactBoxes` 的 JSON 键位推出，而这是右键才发一次的低频包，省下约 25 字节不值得新增一条「键位必须两端一致」的依赖（datagen 重排 JSON、NBT 往返或别的加载路径都会让它静默错位）。因此 `SubsystemInteractPayload` 保持 `(子零件 ID, 交互区名)`，定位走 `subPart.interactBoxes.get(name)`，`InteractBoxes` 的容器与继承结构不变。

扫的成本：发送侧每包一次迭代（≤18 项），接收侧一次；`SubsystemSyncPayload` / `ConnectorSyncPayload` 只在 `packDirty()` 非空时构造，输入类载荷按事件或输入变化发送，量级都低于一次 `PacketDistributor` 广播本身。若要 O(1)，把 `subsystemIndex` / `subsystemAt` 换成构造期赋值的 `int` 字段即可，改动局限在这几个方法里；那会引入一份可变状态与跨包赋值，因此当前不采用。

**哪些字段换**：

| 载荷 / 类型 | 换前 | 换后 | 频次 |
| --- | --- | --- | --- |
| `SubsystemSyncPayload` | `writeInt(id)` + `writeUtf(子系统名)` + 脏值 | `writeVarInt(id)` + `writeVarInt(子系统序号)` + 脏值 | 每次脏值变化，广播全维度 |
| `ConnectorSyncPayload` | 同形，`writeUtf(连接点名)` | `writeVarInt(id)` + `writeVarInt(连接点序号)` + 脏值 | 同上 |
| `MovementInputPayload`、`ControlBindingPayload`、`GuiActionPayload`、`ControlGroupSetEditPayload`、`SeatSwitchPayload`、`SeatSwitchResultPayload` | `writeInt(subPartId)` + `writeUtf(子系统名 / 座位名)` | 两个 `writeVarInt` | 每 tick / 每次操作 |
| `ViewInputPayload` | 座椅与视点各一对 `writeInt` + `writeUtf` | 各一对 `writeVarInt`；虚拟视点为 `(-1, -1)` | 每 tick |
| `SubsystemInteractPayload` | `writeInt(subPartId)` + `writeUtf(交互区名)` | 不变 | 每次右键 |
| `IAmmoSupplier.SupplierRef`、`ReloadJobKey`（同步值） | `VAR_INT` + `STRING_UTF8` | 保持不变 | 作业变化时 |

`SubsystemInteractPayload`、`SupplierRef` 与 `ReloadJobKey` **不换**。交互区的理由见上。`SupplierRef` 与 `ReloadJobKey` 的 `STREAM_CODEC` 都是无上下文的 `StreamCodec<ByteBuf, T>`（`StreamCodec.composite` 直接拼字节），解码端手上没有 `SubPart`，没有对象能把序号换回名字；而下面硬约束 2 又要求序号不进 `equals` / `hashCode` / `compareTo`，记录里必须留着名字——两条同时成立时“名字换序号”无处落地。频率与代价也不对称：它们只在作业开始与结束、结构变化、玩家加入时整包下发，不像 `*SyncPayload` 那样每次脏值变化都广播，省下的约 35 字节/包不值得改动一条已被测试固定住的排序契约。将来若要动，前提是先把硬约束 2 改成“比较按声明序”。

**协议组版本**：线格式变了，`MMPayloadRegistry` 的三个协议组同批提升——`input:3.0.0`（`MovementInputPayload`、`ControlBindingPayload`、`ViewInputPayload` 与两个换座包）、`sync:9.0.0`（两个 `*SyncPayload`）、`misc:2.0.0`（`ControlGroupSetEditPayload`、`GuiActionPayload`）。`SubsystemInteractPayload` 的线格式未变，随所在组一起受版本号约束。

**五条硬约束**：

1. 序号不进 `saveData` / `CODEC`：`SubPartData#subsystemData` 的键继续是名字，存档、蓝图、物品数据组件不受影响。
2. 序号不进 `equals` / `hashCode` / `compareTo`：`SupplierRef`、`ReloadJobKey` 的比较器按名字排序，“按元组升序撤销预留”那条契约不随序号分配变化。
3. 只用在接收方必然用同一份定义重建了同一个 `SubPart` 的链路；跨机（`BlueprintExtractLocalPayload`）与跨存档一律用名字。
4. 越界即整包丢弃并记一条日志，不回退按名字再查——回退会把“序号错了”变成“有时对有时错”。
5. 两端内容定义必须一致：序号只由声明序决定，不额外传输；定义不一致时序号会指向另一个对象。这一条由协议组版本与 Spark-Core 的整包下发共同保证，序号本身不做校验（校验不了）。

### 3.7 定序：座位枚举（只由结构决定）

座位顺序只由结构决定：

```
部件 UUID 升序 → 子零件声明序 → 子系统声明序
```

这是“同一个 Part 内的座椅先按子零件、再按子系统”的依据，跨 Part 用 UUID 决定，不需要为座位另建一张表。它不读位姿、不读名字、不读会话计数器，因此炮塔转动、悬挂起伏都改变不了编号；存档恢复后 UUID 不变，编号也不变。

不采用几何排序（把 locator 世界位姿投影到参考件局部坐标系后按 `(sy, sx)` 排序）的理由：

| 失效方式 | 为什么 |
| --- | --- |
| 炮塔上的座位随方位角改变名次 | 座位在参考系里的位置是实时位姿，转动的炮塔让座位扫过其它座位，排序键跟着变。开着车看座位图，编号会跳 |
| 存档前后顺序可能不同 | 排序键的并列裁决会落到“遍历顺序”上（`partMap` 是 `ConcurrentHashMap`，参考件选择若用身份哈希序）；参考系一换全部编号轮转或镜像 |
| 并列时落到会话计数器 | 若 tie-break 取 `SubPart#getId()`（= `DestroyableObject#ENTITY_COUNTER`），每次载入都重新分配 |

并列并不罕见：`subsystem.machine_max.driver_seat` 被 29 份部件定义复用，合体之后多节车体各有一个同名座位；量化步长 0.05 m 内一旦并列就需要一个不随会话变化的 tie-break，而结构顺序本身就是这个 tie-break。

职责与落点：

| 职责 | 落点 |
| --- | --- |
| 枚举 | `VehicleCore#getAllSeats()`（`getEmptySeats` / `getFirstEmptySeat` / `getFirstSeat` 一并走它） |
| 循环换座 | `VehicleCore#nextSeat(from, forward)`：按同一份列表回绕一圈，跳过自身与不可用座位 |
| 可用性 | `SeatSubsystem#isAvailable()`：座位与宿主都还在、宿主已有代理实体、未被占用；只读 `occupied`，不读 `passenger` |
| 座位图坐标系 | 客户端 `SeatMapLayout`：座位图仍需要一个固定局部坐标系把标记投影到载具模型上，但参考件只决定这个坐标系，不参与排序。簇表用 `LinkedHashMap`，并列取先枚举者，不用 `SubPart#id` 做 tie-break |

同源改动：`SeatSwitchPayload` 的类注释、`SeatSwitchClient`、`HudVehicleSnapshot`、`KeyBinding`、`SeatMapLayout`、换座相关文档，以及测试类改名 `SeatSwitchGameTest`（批名 `mmSeatSwitch`，只保留与排序无关的用例）。

一个诚实的边界：UUID 在恢复存档时不变，但**用同一份蓝图重新放置**会生成新 UUID，编号可能变。编号只承诺“同一台载具在其生命周期内稳定”，跨重建不承诺。

记录、未改：参考件选择里的 `isDriverName` 用翻译键子串 `"driver"` 判驾驶员座位，`subsystem.machine_max.azimuth_driver` 这类伺服键同样命中，只因外面套了 `instanceof SeatSubsystem` 才没有误判。显式判据要么取控制组预设的角色，要么给座位型号加标记字段，两者都要动内容 schema，另起一处改。

## 4. 迁移清单

| 位置 | 当前形态 | 状态 |
| --- | --- | --- |
| `SubPart#subsystems` | private `LinkedHashMap` + `@Getter(AccessLevel.NONE)`；暴露 `ownSubsystems()` / `findOwnSubsystem()` / `findSubsystem()` / `installSubsystem()` | 已落地（S1） |
| `ISubsystemHost` | 收窄为 `Collection<AbstractSubsystem> ownSubsystems()`，并补 `findOwnSubsystem` | 已落地（S1） |
| `SubPart.destroy`、`SubPartData` 构造器 | 遍历 `ownSubsystems()` | 已落地（S1） |
| 8 个网络载荷 / 9 处 | `findOwnSubsystem(name)`；`SubsystemSyncPayload` / `ConnectorSyncPayload` 目标缺失时整包丢弃并记日志 | 已落地（S1）；S6 起这些读点改为按序号定位（见本表末尾） |
| 客户端渲染 3 处（`VehicleInspectorRenderer`、`VehicleModelElement`、`AssemblyHud3D`） | `findOwnSubsystem(name)` | 已落地（S1） |
| 共通 3 处（`AbstractPart` 恢复路径、`MechPowerPort`、`GearboxSubsystem`） | `findOwnSubsystem` | 已落地（S1） |
| GameTest（`src/main`） | `findOwnSubsystem` / `ownSubsystems()`；`ReactiveArmorHitGameTest` 的探针改用 `installSubsystem` | 已落地（S1） |
| `AbstractPart#connectorsByName` / `#subsystemsByName` | 已删除，改 `findConnector` / `findSubsystem` | 已落地（S2） |
| `MechMolangContext` 8 处 | 改 `find*` | 已落地（S2） |
| `HudPartSnapshot` 2 处 | 遍历 `ownSubsystems()` 与子零件连接点 | 已落地（S2） |
| `ISignalSender#setTargetFromNames` | 一趟 `subParts` 聚合三张临时表 | 已落地（S3） |
| `ISignalSender#getReceiversFromNames` | 形参与签名不变；传入的是 Part 级聚合表 | 已落地（S3） |
| `checkContent` / 编辑器插件 | 两类名字唯一性判据（重名、保留地址）按 error 上报 | 已落地（S3） |
| `signal/AGENTS.md`、`操作权与越级优先级设计` §8.1、`动画系统` §6.5.3 | 作用域 = Part；端口与名字的分工写清楚 | 已落地（S4） |
| `SeatOrder`（整个类） | 已删除；职责进 `VehicleCore#getAllSeats()` / `#nextSeat()`、`SeatSubsystem#isAvailable()`、客户端 `SeatMapLayout` | 已落地（S5） |
| `SubPart#subsystemIndex` / `#subsystemAt`、`#connectorIndex` / `#connectorAt` | 声明序位置 ↔ 对象的两个方向，现场线性扫 | 已落地（S6） |
| `SubsystemSyncPayload`、`ConnectorSyncPayload` 与 7 个输入类载荷 | `(subPartId, 名字)` → `(varint 子零件 ID, varint 序号)`；越界整包丢弃并记日志 | 已落地（S6） |
| `SubsystemInteractPayload` | 保持 `(subPartId, 交互区名)`：`InteractBoxes` 是并发无序容器，序号只能来自 JSON 键位，右键低频包不值得新增这条依赖 | 保持不变（S7 收窄） |
| `MMPayloadRegistry` 协议组 | `input:3.0.0`、`sync:9.0.0`、`misc:2.0.0` | 已落地（S6） |
| `IAmmoSupplier.SupplierRef`、`ReloadJobKey`（同步值） | `(宿主 ID, 子系统名)` 的名字形态 | 保持名字（§3.6） |

## 5. 分阶段交付

| 阶段 | 内容 | 验收 | 状态 |
| --- | --- | --- | --- |
| S1 | `SubPart#subsystems` 封装（`AccessLevel.NONE` + 接口收窄），加三个访问口；全部按名取与整体取读点落到新口上；两个 `*SyncPayload` 顺带补目标缺失时的丢弃 | 全部落点编译通过；`./gradlew --offline test` 与 `runGameTestServer` 全绿，行为不变 | 已落地 |
| S2 | `find*` 三个方法 + 删除两张常驻索引；MoLang 与 HUD 快照改读 | `./gradlew --offline test` 全绿；`ViewpointGameTest`、`SignalPipelineGameTest` 等既有用例不回归 | 已落地 |
| S3 | `setTargetFromNames` 聚合 Part 级表；`checkContent` 与插件补重名判据（解码期校验已存在，这两层只是把同一判据提前到构建期与导出期） | 新增 GameTest：多子零件 Part 内跨子零件具名投递可达；新增 JUnit：内容里不存在跨子零件的三类重名；`./gradlew --offline checkContent` 通过 | 已落地（插件判据原已存在，`checkContent` 与两条测试本轮补齐） |
| S4 | 文档：`signal/AGENTS.md`、`操作权与越级优先级设计` §8.1、`动画系统` §6.5.3 的待办标记 | 三处表述一致，无“以旧状态为基线”的表述 | 已落地 |
| S5 | 删除 `SeatOrder`：枚举与循环换座进 `VehicleCore`，可用性进 `SeatSubsystem`，参考件选择进 `SeatMapLayout` | 用例见 T7；`SeatOrderGameTest` 改名并只留与排序无关的用例 | 已落地 |
| S6 | 子系统与连接点序号：`SubPart#subsystemIndex` / `#subsystemAt`、`#connectorIndex` / `#connectorAt`，先切 `SubsystemSyncPayload` / `ConnectorSyncPayload`，再切 7 个输入类载荷；`SupplierRef` / `ReloadJobKey` 保持名字 | 双端同步值落到同一个子系统或连接点；序号越界时整包丢弃并留日志 | 已落地 |
| S7 | 交互区复核：保持 `(子零件 ID, 交互区名)`，不序号化 | `SubsystemInteractPayload` 的定位与行为不变；`InteractBoxes` 的容器与继承结构不变 | 已落地（收窄为保持名字） |

S1—S7 已全部落地。S6 的线上部分随本批改协议组版本（`input:3.0.0`、`sync:9.0.0`、`misc:2.0.0`，见 `network/AGENTS.md`）；S7 复核后保持名字，不涉及线格式改动。

## 6. 验收用例

| 编号 | 用例 | 判据 | 覆盖 |
| --- | --- | --- | --- |
| T1 | 座位与武器控制器分属同一 Part 的两个子零件，控制组具名指向该控制器 | 发现得到该控制器；投递后其输入槽有值 | `PartSignalBusGameTest#namedTargetsResolveAcrossSubParts`（探针发送者的目标在同 Part 另一个子零件上） |
| T2 | 同一频道上两台控制器，名字不同 | 两组各自只到达自己的那一台 | 同一 GameTest 的第二段（两个发送者、两个名字） |
| T3 | 构造一份单变体内重名的部件 JSONC（同一名字同时用作子系统与连接点名） | 解码抛 `PackContentException`，该定义不进候选表，发布表不含它；诊断带冲突双方与所属文件 | 解码期校验原本就有（`VariantAttr#validateUniqueNames`）；本轮不改其实现 |
| T4 | 网络包按 `(subPartId, 序号)` 定位 | 落在该子零件的子系统上，不被同名兄弟顶替；序号越界或落在已清空的容器上时解析不到 | `StructureSequenceGameTest#sequenceAddressesRoundTripWithinEachSubPart`（三个方向的逆映射与越界）、`#sequenceIsScopedToSubPartNotPart`（同一序号在两个子零件上指向不同对象） |
| T5 | 子零件被销毁后重建 | 名字解析到新实例，旧实例不可达 | 无现成用例；`SubPart#destroy` 清空容器后重建走同一构造路径 |
| T6 | 内容扫描 | 全部第一方部件无跨子零件三类重名（当前 86 份定义扫描为 0） | `PartSignalNameUniquenessTest` + `./gradlew checkContent` |
| T7 | 同一装配体反复调用 `getAllSeats()` / `nextSeat`；几何上并列的两个座位；同一存档恢复两次 | 逐位相同；与 `SubPart#id` 的分配顺序无关；并列时按声明序；两次恢复的顺序一致 | `SeatSwitchGameTest#seatEnumerationFollowsStructureAndIsRepeatable` 覆盖同实例反复枚举与“无代理实体时不可用”；存档往返两次恢复未写成用例 |
| T8 | `SubsystemSyncPayload` / `ConnectorSyncPayload` 按序号落地 | 客户端命中的是服务端所指的那个子系统或连接点；序号越界时整包丢弃并留日志 | 序号 ↔ 对象两个方向由 `StructureSequenceGameTest` 固定；越界丢弃分支已实现并记 WARN，无 GameTest（需要伪造客户端收包上下文） |
| T9 | 同步包目标缺失 | 丢弃整包并记一条日志，不 NPE | 两个 `*SyncPayload` 的缺失分支已实现并记 WARN；无 GameTest（需要伪造客户端收包上下文） |

T7 不能写成“同一份模板构造两次结果相同”：新建部件的 UUID 是随机生成的，两次构造拿到不同的 UUID，并列座位的先后本来就允许不同。稳定性只对同一台载具（含其存档往返）成立。

## 7. 风险与未决

- **唯一性是内容契约，且已由校验强制执行**：违反的解码期即抛 `PackContentException`，那个部件类型在双端都不出现。风险因此落在“作者写完后发现该类型不见了”，靠 `checkContent` 与插件把同一判据提前到构建期与导出期。
- **连接点定序决定配对优先级**：`comboAttachConnector` 与 `autoAttachInternalConnectors` 都按迭代序尝试候选，`connectors` 是声明序，所以几何上同时成立多个候选时“先配上的那一对”取作者在 JSON 里的书写顺序。内部连接对多候选冲突显式跳过，结果与顺序无关；跨部件装配在多候选时结果可能变化。同一份声明序现在也是线上连接点序号的来源（§3.6），改内容顺序会同时改变配对优先级与线上编号。
- **声明序是内容可改的**：`subsystems` / `connectors` 是声明序 `LinkedHashMap`；作者在对象中间插入一条，其后所有序号整体后移。这只影响同一次连接内的线上编号，不影响身份（身份仍是名字），而序号只在本次连接内存在。
- **两端内容定义必须一致**：序号不额外传输，定义不同则同一序号指向不同对象。保证来自协议组版本与 Spark-Core 在 configuration 阶段的整包下发；序号本身无法自校验，越界只能拦住“数量不同”，拦不住“顺序不同”。
- **交互区保持名字**：`InteractBoxes` 由物理线程读、主线程在销毁时清空，是并发容器且没有声明序；序号只能取自 `SubPartAttr#interactBoxes` 的 JSON 键位，而右键低频包不值得为省约 25 字节新增这条依赖，因此 `SubsystemInteractPayload` 带交互区名。
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
| v5 | 2026-11-16 | S1—S5 落地并回填现状：Part 级名字解析、`SubPart#subsystems` 封装与 `installSubsystem`、`find*` 与两张常驻索引删除、`checkContent` 唯一性判据、座位定序（`SeatOrder` 删除）；§4 改为「当前形态 + 状态」并把 S1—S5 标为已落地、S6/S7 标为未实施；§6 补覆盖列；S3 的插件判据注明原已存在 |
| v6 | 2026-11-16 | S6/S7 落地并回填：序号定为**子零件级**声明序位置（`(子零件 ID, 序号)`），现场线性扫计算、不新增字段；§3.6 重写为已实现形态，补“为什么不是 Part 级”、发送/接收成本、五条硬约束与协议组版本（`input:3.0.0` / `sync:9.0.0` / `misc:2.0.0`）；§1、§2、§3.2 的载荷寻址表述同步；§4/§5 标为已落地并补序号口与协议行；§6 的 T4/T8 覆盖改为 `StructureSequenceGameTest`；§7 补“两端定义必须一致”风险 |
| v7 | 2026-11-16 | S7 收窄为**保持交互区名**：`InteractBoxes` 是并发无序容器，交互区序号只能取自 `SubPartAttr#interactBoxes` 的 JSON 键位，而右键低频包不值得为省约 25 字节新增「键位必须两端一致」的依赖；`interactBoxIndex` / `interactBoxAt` 两方法不落地，`SubsystemInteractPayload` 保持 `(子零件 ID, 交互区名)`，线格式不变。§3.6 标题、载荷表、§4、§5、§7 与测试同步订正 |
