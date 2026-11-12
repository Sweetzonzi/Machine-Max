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
├── SignalPort.java              # 信号端口（185行），信号路由 + 优先级
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
| 修改信号路由 | `SignalPort.java` | 输入→输出映射、信号优先级、多跳传播 |
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

通道名称采用点分命名空间：`{域}.{子域}.{操作}`

| 通道名 | 信号类型 | 说明 |
|--------|----------|------|
| `control.move` | MoveInputSignal | 移动控制输入 |
| `control.view` | ViewInputSignal | 视角/瞄准输入 |
| `control.regular` | RegularInputSignal | 常规交互（跳跃、互动等） |
| `control.motor` | MotorControlSignal | 马达/引擎控制 |
| `control.wheel` | WheelControlSignal | 车轮驱动/转向 |
| `control.rotation` | RotationSignal | 旋转/角度控制（炮塔、关节等） |
| `interact` | InteractSignal | 玩家交互信号 |

## 约定

- **SignalChannel extends ConcurrentHashMap**：通过继承实现线程安全。键为通道名，值为信号值。
- **信号直送模式**：发送方把值写入接收方的 Channel 并按发送者身份分槽，随后同步调用 `onSignalUpdated`；没有独立的回传通道。
- **多跳传播**：一个子系统的输出信号可作为另一个子系统的输入，形成信号链。
- **优先级排序**：`SignalPort` 支持按优先级分发，高优先级接收者先处理（如 CarController 先于 Motor 处理油门信号）。
- **两级 ISignalBus 都由订阅集合驱动**：`AbstractPart` 提供 `local`，订阅范围是本部件全部 SubPart 当前持有的子系统；`SubsystemController` 提供 `global`，订阅范围是整车全部子系统。两者共用 `ISignalBus#broadcast`：按原始发送者分槽写入接收者输入频道后再通知，广播不递归到端口或其他总线。Part 的 `acceptAllRoutingInput` 为 true，允许经连接器到达 `local` 的频道；`acceptAllBroadcastInput` 保持 false，订阅者仍按自己声明的频道筛选。
- **空信号触发**：`EmptySignal` 仅用于触发性通知（如"开火"），无数据负载。
- **寻址名与身份名分离**：路由使用 `ISignalReceiver.getSignalAddress()`；`getName()` 仅用于日志/身份，不参与路由。保留地址 `"local"` = Part、`"global"` = 装配体（`SubsystemController`）。
- **存储写入自述**：发送端只调用 `getSignalStorage()`，由 Part / SubsystemController 自行提供存储表，不按具体类型 `instanceof` 分派。
- **Part 订阅随结构自动订退**：子系统在 `AbstractPart#createSubsystems` 进入 SubPart 容器后订阅所属 Part，`SubPart#destroy`（含构造失败回收）退出。显式退订不改变物理归属与 global 订阅；结构变化只清 `signalInputChannels` 与 `signalStorage`，不退订。
- **只读终端查询**：`resolveTargets(outputChannel)` 从首跳表沿端口与总线解析全部终端，返回不可变的「到达频道 → 终端列表」；不写信号槽、不建频道、不触发 `onSignalUpdated`。镜头、车控与火控的执行器发现都用它，发现过程不发空信号。
- **执行器读输入槽**：火控把 `RotationSignal` / `LauncherSubsystem.FireCommand` 作为信号值，经实例重载写入炮塔/发射器的到达频道；执行器按自己的 `control_inputs` 顺序读取当前槽，`EmptySignal` 表示该来源已释放。结构变化清空输入槽后不需要逐个目标调用释放 setter。
- **目标名解析作用域（当前）**：`setTargetFromNames` / `getReceiversFromNames` 仍只在发送者所在 SubPart 内解析子系统/交互区/连接点；跨 SubPart 的 Part 级解析见设计文档 §6.5.3 TODO。首跳表在结构变化时整体重建，配置里删掉的频道不会留在表中。

## 反模式

- **严禁在信号处理中阻塞**：`handleSignal()` 应在主线程执行，避免长时间操作。
- **严禁信号循环**：多跳传播可能形成环路。接收方处理信号时不应将同一信号发回发送方。
- **严禁忽略信号优先级**：高优先级控制信号（如玩家输入）必须优先于低优先级信号（如自动驾驶脚本）。
- **严禁在物理线程走按名发送**：物理步只允许向已绑定的执行器实例发送命令；普通配置线路、状态复位与发现都在主线程完成。物理步算出的反馈先 `publishFeedback` 发布，由本端主线程 `onTick` 经普通发送入口投递。

## 参考文件

- 直送模式与终端查询的实现见 `ISignalSender` / `SignalChannel`，接线约定见仓库根 `AGENTS.md` 的「独特风格」与「反模式」两节。
