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
├── ISignalSender.java           # 信号发送接口（249行）
├── ISignalReceiver.java         # 信号接收接口（120行）
├── ISignalBus.java              # 信号总线接口
│
├── SignalChannel.java           # 信号通道（extends ConcurrentHashMap，线程安全）
├── SignalPort.java              # 信号端口（202行），信号路由 + 优先级
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
| 实现信号发送 | `ISignalSender.java` | 实现 `getSignalChannels()` 和信号写入逻辑 |
| 实现信号接收 | `ISignalReceiver.java` | 实现 `handleSignal()` 处理输入信号 |
| 添加总线方法 | `ISignalBus.java` | 由 SubsystemController 实现 |

## 信号流

```
输入源（RawInputHandler / SeatSubsystem / ScriptableSubsystem）
  │
  ▼
SignalPort.send(signal)
  │
  ▼
ISignalBus（SubsystemController 实现）
  ├── 按通道名查找接收者
  ├── 优先级排序
  └── 分发到 ISignalReceiver.handleSignal()
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
- **信号直送模式**：发送方直接写入接收方的 Channel，绕过旧的绕圈回调机制（重构后）。
- **多跳传播**：一个子系统的输出信号可作为另一个子系统的输入，形成信号链。
- **优先级排序**：`SignalPort` 支持按优先级分发，高优先级接收者先处理（如 CarController 先于 Motor 处理油门信号）。
- **ISignalBus 由 SubsystemController 实现**：作为所有子系统信号的中枢路由器。
- **空信号触发**：`EmptySignal` 仅用于触发性通知（如"开火"），无数据负载。

## 反模式

- **严禁在信号处理中阻塞**：`handleSignal()` 应在主线程执行，避免长时间操作。
- **严禁信号循环**：多跳传播可能形成环路。接收方处理信号时不应将同一信号发回发送方。
- **严禁忽略信号优先级**：高优先级控制信号（如玩家输入）必须优先于低优先级信号（如自动驾驶脚本）。
- **严禁跨线程发送信号**：信号发送和接收都应在主线程（20tps）完成。

## 参考文件

- `docs/信号回调机制重构设计.md` — 直送模式代替绕圈回调的重构设计
