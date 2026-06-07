# network/ — 网络同步

**范围**: 所有网络载荷类、协议注册、载荷处理器。

## 结构

```
network/
├── MMPayloadRegistry.java          # 将所有载荷注册到 4 个协议组
├── payload/
│   ├── assembly/                   # LevelVehicleData, PlayerPartAssemblyCache, VehicleDataSaved 等 14 个
│   ├── fabrication/                # 制造相关载荷 4 个
│   ├── projectile/                 # 投射物载荷 2 个
│   ├── research/                   # FreeRpSync, ResearchCompleteRequest 等 7 个
│   ├── ConnectorSyncPayload.java   # 连接器状态同步
│   ├── ControlBindingPayload.java  # 按键绑定同步
│   ├── ControlGroupSetEditPayload.java
│   ├── ControlPreferencePayload.java
│   ├── GuiActionPayload.java       # GUI 动作
│   ├── MovementInputPayload.java   # 玩家移动输入
│   ├── RegularInputPayload.java    # 常规动作输入
│   ├── ScriptablePayload.java      # 脚本子系统数据
│   ├── SubPartSyncPayload.java     # 增量同步（SynchedEntityData + byte 255）
│   ├── SubsystemInteractPayload.java # 子系统交互
│   ├── SubsystemSyncPayload.java   # 子系统状态同步
│   └── ViewInputPayload.java       # 视角输入
└── handler/research/               # 7 个研究系统载荷处理器
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 新增载荷 | `MMPayloadRegistry.java` | 选择正确的协议组注册 |
| 修复载具同步 | `payload/SubPartSyncPayload.java` | 增量同步，byte 255 终结符 |
| 修复输入延迟 | `payload/MovementInputPayload.java` | TODO: 测试输入延迟 |
| 修复研究同步 | `payload/research/*` + `handler/research/*` | 双向同步，版本 2.0.0 |
| 修复组装同步 | `payload/assembly/*.java` | LevelVehicleData, 缓存同步 |
| 修复投射物同步 | `payload/ProjectileSpawnPayload.java` | 服务端→客户端生成 + 命中特效 |

## 约定

- **4 个协议组**：`input:1.0.0`（控制）、`sync:1.0.0`（世界状态）、`research:2.0.0`（进度）、`misc:1.0.0`（GUI/制造）。
- **全部处理器在主线程**：使用 `MainThreadPayloadHandler`。无物理线程处理器。
- **双向载荷**：使用 `DirectionalPayloadHandler` 分离客户端/服务端方法。
- **增量同步**：`SubPartSyncPayload` 使用 `SynchedEntityData.DataValue` 列表 + byte `255` 终结符。
- **状态同步**：VehicleCore HP、SubPart 耐久度、子系统耐久度/激活状态、连接器完整性通过 `SynchedEntityData` + 定向载荷同步。

## 反模式

- **严禁添加物理线程载荷处理器**：所有处理器必须在主线程运行。
- **严禁破坏协议版本兼容性**：Research 处于 2.0.0 是有原因的。
- **严禁使用旧版连接器 ID 格式**：未来重构将使用 SubPart ID 替代 "载具 UUID-部件 UUID-连接器名"。
