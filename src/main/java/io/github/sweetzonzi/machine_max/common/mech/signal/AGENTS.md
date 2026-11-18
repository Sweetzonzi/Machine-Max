# common/mech/signal/ — 信号系统

**范围**: 信号类型定义、信号通道、信号端口、发送/接收接口、信号总线。

**文件数**: 15 个 | **总行数**: ~890 行

## 结构

```
signal/
├── Signal.java                  # 信号抽象基类（携带泛型值 T）
├── EmptySignal.java             # 空信号（仅触发，无数据）
├── SignalResult.java            # 信号处理结果
│
├── ISignalSender.java           # 信号发送接口（352行）
├── ISignalReceiver.java         # 信号接收接口：getSignalAddress() 路由名 + getSignalStorage() 存储
├── ISignalBus.java              # 信号总线接口
│
├── SignalChannel.java           # 信号通道（extends ConcurrentHashMap，线程安全）
├── SignalPort.java              # 信号端口（185行），信号路由 + 频道转译
│
├── MoveInputSignal.java         # 移动输入信号（前进/后退/左/右/跳跃/潜行）
├── RegularInputSignal.java      # 常规交互信号
├── ViewInputSignal.java         # 视角输入信号（偏航/俯仰）
├── RotationSignal.java          # 旋转/角度信号
├── MotorControlSignal.java      # 马达控制信号
├── WheelControlSignal.java      # 车轮控制信号
└── InteractSignal.java          # 交互信号
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增信号类型 | `Signal.java` | 继承 Signal\<T\>，定义值类型和语义 |
| 修改信号路由 | `SignalPort.java` | 输入→输出映射、频道转译、多跳传播 |
| 修改信号通道 | `SignalChannel.java` | 线程安全的通道映射 |
| 实现信号发送 | `ISignalSender.java` | 实现 `getTargetNames()` / `getTargets()` 与信号写入逻辑 |
| 实现信号接收 | `ISignalReceiver.java` | 实现 `getSignalAddress()`（路由名）与 `onSignalUpdated()`；需要 Molang `get/get_str` 时覆写 `getSignalStorage()` |
| 添加总线方法 | `ISignalBus.java` | `AbstractPart`（local）与 `SubsystemController`（global）各持一份订阅集合 |

## 信号流

```
输入源（RawInputHandler / SeatSubsystem / ScriptableSubsystem）
  │
  ▼
SignalPort.send(signal)
  │
  ▼
ISignalBus（local = AbstractPart，global = SubsystemController）
  ├── 按当前订阅集合分发（local 只含本部件，global 含全车）
  ├── 按到达频道与接收者声明筛选
  └── 分发到 ISignalReceiver.onSignalUpdated()
        │
        ▼
  子系统处理（CarController / Engine / TurretDriver ...）
        │
        ▼
  反馈信号（可选，多跳传播）
```

## 信号通道约定

频道名是下划线分隔的扁平字符串，引擎不解析它：频道只作为 `ConcurrentHashMap` 的键做整串比较（`SignalChannel`），唯一的例外是接收者按自己声明的列表顺序挑第一个有效信号。点号属于**寻址名**——`subsystem.<命名空间>.<名字>`、`interact.<命名空间>.<名字>`、`weapon.<类别>.<型号>`——由 `ISignalSender#setTargetFromNames` 与交互区一类代码消费，两类字符串不共用解析入口。

乘员输入频道（座位写、控制器读）一律是 `crew_<role>_<category>` 岗位频道或 `crew_<role>_<category>_<device>` 设备频道：

| 段 | 取值 |
|----|------|
| `role` | `driver` / `commander` / `gunner` / `loader` / `any` |
| `category` | `aim`（view_targets）/ `fire`（main_weapon_targets）/ `secondary_fire`（secondary_weapon_targets）/ `move` / `regular` |
| `device` | `main_gun` / `coax762` / `csamm` / `rws` / `citv` / `aa` / `port_mg` / `aft_mg` / `atgm` |

| 频道 | 信号类型 | 说明 |
|------|----------|------|
| `crew_driver_move` / `crew_commander_move` | MoveInputSignal | 移动控制输入 |
| `crew_driver_regular` / `crew_commander_regular` | RegularInputSignal | 常规按键输入（换挡、离合、引擎启动等） |
| `crew_gunner_aim` / `crew_commander_aim` / `crew_any_aim` | ViewInputSignal | 视角/瞄准输入 |
| `crew_gunner_fire` / `crew_commander_fire` / `crew_any_fire` | RegularInputSignal 或裸值 | 主武器开火指令，按 `isFireSignal` 语义判定 |
| `crew_gunner_secondary_fire` 等 | 同上 | 副武器开火指令 |
| `crew_gunner_aim_coax762` 等 | ViewInputSignal | 同一岗位的第二台设备，一台一条 |

三段与四段的区别是"目标定不定"：三段同名即命中（装配体内谁声明谁收），四段点名一台已知设备。加装件（`m153_crows`、PKT 机枪塔）只声明岗位频道，装到哪台车由宿主的座位预设决定；自带主武器留岗位频道，同一岗位的第二台及以后设备用设备频道。完整规则、字表与替换清单见 `docs/plan/已实现/信号系统-操作权与越级优先级设计.md`。

**读取按三键排序**（`WeaponControllerSubsystem#selectInputSlot`）：越级量降序 → 档位降序（列表靠前者高，档位 = 列表长度 − 下标）→ 发送者稳定身份升序（`ISignalSender#compareSenders`）。越级量由 `Signal#priority` 携带，座位在出口处按频道取 `ControlGroupSet#priorityOf(channel)` 复制一份；非 `Signal` 的裸值（按键绑定、GUI 动作）按 0。全部越级量为 0 时退化为"按列表顺序取第一个有效信号"。只有武器控制器的瞄准与开火接入这套排序，驾驶、引擎、变速箱等读取点仍按列表顺序取第一个。

执行器与内部线不做乘员寻址：

| 频道 | 方向 | 说明 |
|------|------|------|
| `weapon_control` | 武器控制器 → 炮塔驱动器 | 火控输出 |
| `car_control` / `move_control` | 座位 → 行驶控制器/车轮驱动器 | 行驶与移动反馈 |
| `aim_drive_<设备>` | 武器控制器 → 炮塔驱动器 | 单台设备的瞄准驱动，载荷为 `RotationSignal` |
| `diff_lock_control` | 座位 → 变速箱 | 差速锁 |
| `ammo_switch` | 座位 → 武器控制器 | 弹种切换指令（一次性按下：`RegularInputSignal` 的 `NEXT_AMMO_TYPE`／`PREV_AMMO_TYPE`，控制器消费一次后把槽位清空） |
| `interact` | 交互区 → 子系统 | 玩家交互 |

## 约定

- **SignalChannel extends ConcurrentHashMap**：通过继承实现线程安全。键为通道名，值为信号值。
- **信号直送模式**：发送方把值写入接收方的 Channel 并按发送者身份分槽，随后同步调用 `onSignalUpdated`；没有独立的回传通道。
- **多跳传播**：一个子系统的输出信号可作为另一个子系统的输入，形成信号链。
- **越级量**：`Signal#priority`（默认 0）随信号本体走，由发送方在出口处写入；接收者读取时按"越级量 → 档位 → 稳定身份"三键排序。`SignalPort` 没有优先级字段，转发按目标表迭代顺序进行。
- **两级 ISignalBus 都由订阅集合驱动**：`AbstractPart` 提供 `local`，订阅范围是本部件全部 SubPart 当前持有的子系统；`SubsystemController` 提供 `global`，订阅范围是整车全部子系统。两者共用 `ISignalBus#broadcast`：按原始发送者分槽写入接收者输入频道后再通知，广播不递归到端口或其他总线。Part 的 `acceptAllRoutingInput` 为 true，允许经连接器到达 `local` 的频道；`acceptAllBroadcastInput` 保持 false，订阅者仍按自己声明的频道筛选。
- **空信号触发**：`EmptySignal` 仅用于触发性通知（如"开火"），无数据负载。
- **寻址名与身份名分离**：路由使用 `ISignalReceiver.getSignalAddress()`；`getName()` 仅用于日志/身份，不参与路由。保留地址 `"local"` = Part、`"global"` = 装配体（`SubsystemController`）。
- **存储写入自述**：发送端只调用 `getSignalStorage()`，由 Part / SubsystemController 自行提供存储表，不按具体类型 `instanceof` 分派。
- **Part 订阅随结构自动订退**：结构构造期 `AbstractPart` 按声明序为每个子零件调用 `SubPart#installSubsystem`，子系统在这一步进入容器并订阅所属 Part，`SubPart#destroy`（含构造失败回收）退出。显式退订不改变物理归属与 global 订阅；结构变化只清 `signalInputChannels` 与 `signalStorage`，不退订。
- **只读终端查询**：`resolveTargets(outputChannel)` 从首跳表沿端口与总线解析全部终端，返回不可变的「到达频道 → 终端列表」；不写信号槽、不建频道、不触发 `onSignalUpdated`。镜头、车控与火控的执行器发现都用它，发现过程不发空信号。同名频道的两个控制组可以各查各的：`resolveTargets(outputChannel, firstHopNames)` 只用列出的那几条首跳，名字按给定顺序保留、表里没有的直接跳过，空列表即「这条频道在本组不到达任何终端」。首跳的键就是配置里的目标名（子系统名、`"local"`、`"global"`、交互区名、连接点名），所以第二个重载是对首跳表做过滤，不重新按名解析。
- **执行器读输入槽**：火控把 `RotationSignal` / `LauncherSubsystem.FireCommand` 作为信号值，经实例重载写入炮塔/发射器的到达频道；执行器按自己的 `control_inputs` 顺序读取当前槽，`EmptySignal` 表示该来源已释放。结构变化清空输入槽后不需要逐个目标调用释放 setter。
- **控制组按自己的声明投递**：控制组的类目出口（`setMoveInputSignal`、`setViewInputSignal`、主／副武器与常规输入）遍历 base 与活动子组合并后的「频道 → 目标名」，逐个 `sendSignalToTarget(channel, name, value)`；首跳表本身是 base 与全部子组的并集，所以缺省行为与「发给该频道全部首跳」一致，而两组把同一频道指向不同接收者时各发各的。清空有两档：`clearInputSignals`（离座、切组、失活、摧毁、装机变化、视点设备失效）按并集把可能发过的全部频道置空；`clearViewSignals` 只置空视点频道与记录中的视点身份，换视点、换主武器控制器时用它——那两种时刻扳机和油门多半还按着，一并清掉会让"按住"只在本端消失，而服务端的频道里那条开火指令还在，它会按那个值一路打到下一次按键边沿。
- **目标名解析作用域是 Part**：`setTargetFromNames` 在建首跳表时聚合发送者所属**整个 Part** 的子系统 / 交互区 / 连接点（一趟 `subParts` 现场遍历，不设常驻索引），三类名字共用一套命名空间、在 Part 内唯一（解码期 `VariantAttr#validateUniqueNames` 强制）。跨子零件的具名目标因此可达；`getReceiversFromNames` 的形参签名保持不变（JS Hook 契约），改造只更换传入的表内容。名字通路只保证"两个对象同属一个 Part"，不保证线路连通；需要"线断了信号就断"时写连接点名走 `SignalPort`。`global`（`SubsystemController` 总线按每个订阅者声明的接受列表广播）与 `SignalPort`（`signal_targets` 加频道转译）是另外两条通路。首跳表在 `onAttach`、换组与结构变化时整体重建，配置里删掉的频道不会留在表中。按名读取子零件的口是 `SubPart#findOwnSubsystem`（存档恢复、MoLang、能源端口按 `(子零件, 名字)` 定位），跨 Part 全域解析走 `AbstractPart#findSubsystem` / `#findConnector` / `#findInteractBox`。网络载荷对子系统与连接点不走名字：它们按 `(子零件 ID, 声明序序号)` 寻址（`SubPart#subsystemIndex` / `#subsystemAt` 等），理由与约束见 `docs/plan/已实现/信号系统-Part级名字解析与子系统封装.md` §3.6；交互区活表是并发容器且没有声明序（物理线程读、主线程销毁时清空），`SubsystemInteractPayload` 保持交互区名。
- **同 Part 内一律具名直连**：既然名字在 Part 内全域可达，同一 Part 里的连线（含跨子零件，如炮塔里的火控与并列机枪）就写子系统名，不经连接点。内部连接点（`"internal": true`）由 `AbstractPart#autoAttachInternalConnectors` 按「高级 + 简单、不同子零件、几何对齐」自动配对，两端必然同 Part，在它上面配 `signal_targets` / `signal_translations` 只会多一条几何配对依赖，配不上时整条线静默断开；`checkContent` 把这种写法判为失败。连接点只用于跨 Part。
- **端口的表在接收侧**：`SignalPort#onSignalUpdated` 用 `owner.attachedConnector.signalPort` 的 `signal_translations` 与 `signal_targets` 投递，即信号到达哪一侧端口，就用**对侧**（接收方零件）连接点的两张表。因此在端口表里读到的键是"跨过关节之后本零件要收的到达频道"，投递前还要过接收者的 `getAcceptedChannels()`（`acceptAllRoutingInput()` 为真时跳过）。`resolveTargets` 走同一套（`ISignalSender#resolveSignalLine` 同样先 `other.translateChannel` 再查 `other.getTargets()`），所以发现与投递不会给出两套答案。

## 反模式

- **严禁在信号处理中阻塞**：`handleSignal()` 应在主线程执行，避免长时间操作。
- **严禁信号循环**：多跳传播可能形成环路。接收方处理信号时不应将同一信号发回发送方。
- **严禁忽略信号优先级**：高优先级控制信号（如玩家输入）必须优先于低优先级信号（如自动驾驶脚本）。
- **严禁在物理线程走按名发送**：物理步只允许向已绑定的执行器实例发送命令；普通配置线路、状态复位与发现都在主线程完成。物理步算出的反馈先 `publishFeedback` 发布，由本端主线程 `onTick` 经普通发送入口投递。

## 参考文件

- 直送模式与终端查询的实现见 `ISignalSender` / `SignalChannel`，接线约定见仓库根 `AGENTS.md` 的「独特风格」与「反模式」两节。
