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
│   ├── research/                   # FreeRpSync, ResearchCompleteRequest 等 6 个
│   ├── ConnectorSyncPayload.java   # 连接器状态同步
│   ├── ControlBindingPayload.java  # 按键绑定同步
│   ├── ControlGroupSetEditPayload.java
│   ├── ControlPreferencePayload.java
│   ├── GuiActionPayload.java       # GUI 动作
│   ├── MovementInputPayload.java   # 玩家移动输入
│   ├── RegularInputPayload.java    # 常规动作输入
│   ├── ScriptablePayload.java      # 脚本子系统数据
│   ├── SubPartHitEffectPayload.java # 命中音效与命中火花的客户端表现（子部件 id 寻址）
│   ├── SubPartSyncPayload.java     # 增量同步（SynchedEntityData + byte 255）
│   ├── SubsystemInteractPayload.java # 子系统交互
│   ├── SubsystemSyncPayload.java   # 子系统状态同步
│   └── ViewInputPayload.java       # 视角输入
└── handler/research/               # 6 个研究系统载荷处理器
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
| 修复命中音效 / 火花 | `payload/SubPartHitEffectPayload.java` | 只传子部件 id、碰撞箱名、局部命中点与喷射轴，音效与火花 id 由客户端查内容包 |

## 约定

- **5 个协议组**：`input:3.0.0`（控制）、`sync:11.0.0`（世界状态）、`research:2.0.0`（进度）、`misc:3.0.0`（GUI/制造/蓝图）、`pda:1.0.0`（终端）。`common/mech/subsystem/EngineSubsystem.java#SOUND_THROTTLE_ID` 经子系统同步向客户端提供 FMOD 工况音的油门开度。`common/mech/subsystem/LauncherSubsystem.java` 的 `JOB_SYNC_ID` 是一个可选的装填作业快照 `common/mech/subsystem/ReloadJobSync.java`（编号、运输者、提供者、冻结规格、预留份额、进度基线、本阶段时长与阶段；空值即没有作业），经自定义序列化器 `optional_reload_job_sync` 编解码：稳定装填期间服务端只写一次基线，两端各自累计，客户端在 `onPostTick` 统一应用；阶段切换与换人重定时会重发整包（同一条作业换了坐标帧），客户端据此重设进度而不是回退；`JOB_RESULT_ID`／`JOB_RESULT_DELIVERED_ID` 是最近一次结束的作业编号与已交付结论，只在收尾时写一次（取消、来源失效、暂停超时与成功交付共用这一对），客户端据编号结束匹配的本地作业；`AmmoLoaderSubsystem.java#SLOT_OWNER_ID` 只由 `can_supply_multiple=false` 的独占运输者下发槽位拥有者（`ReloadJobKey` = 宿主 SubPart ID + 发射器序号 + 作业编号），`RegenAmmoRackSubsystem.java#RESERVED_ID` 下发已预留总量 `R`。`common/mech/vehicle/track/TrackLinkData.java#STREAM_CODEC` 依次编码 clockwise 铺设绕向、`track_type` 型号 id 与 `link_count` 节数，载具同步与蓝图取出载荷均传输这三个字段。
- **结构序号寻址**：子系统与连接点的载荷不传名字，地址是 `(varint 子零件 ID, varint 声明序序号)`。序号是对象在所属 `SubPart` 容器里的声明序位置（`SubPart#subsystemIndex`／`#connectorIndex`），接收方用 `SubPart#subsystemAt`／`#connectorAt` 反查。容器只整体清空、不部分删减，位置对存活对象稳定；序号不额外传输，两端解同一份内容定义所以构造序相同。越界即整包丢弃并记日志，不回退按名字再查（依据见 `docs/plan/已实现/信号系统-Part级名字解析与子系统封装.md` §3.6）。交互区不序号化：`InteractBoxes` 由物理线程读（`LivingEntityEyesightAttachment` 的射线与碰撞回调）、主线程在销毁时清空，是并发容器且没有声明序；`SubsystemInteractPayload` 保持 `(子零件 ID, 交互区名)`。名字仍是存档键、翻译键与日志身份；弹药端的 `AmmoRef` 与作业身份 `ReloadJobKey` 同样按结构序号编码（宿主 SubPart ID + 声明序序号），与子系统载荷同一口径。
- **全部处理器在主线程**：使用 `MainThreadPayloadHandler`。无物理线程处理器。
- **双向载荷**：使用 `DirectionalPayloadHandler` 分离客户端/服务端方法。
- **普通部件制造状态**：`network/payload/assembly/PartAssemblyProgressSyncPayload.java#STREAM_CODEC` 通过一个 SubPart ID 定位所属部件；ID、组装比例、材料项数、组装形态共 13 字节，接收时通过 `common/mech/ObjectManager.java#getDestroyableObject` 查对象。这里的源码路径均以 `src/main/java/io/github/sweetzonzi/machine_max/` 为基准。
- **普通部件存档布局**：`common/mech/vehicle/data/RegularPartData.java#STREAM_CODEC` 在组装形态之后依次写 `destroyed` 布尔与 `destroy_time` 整数，与 `CODEC` 同序；字段扩展与 `sync`/`misc` 两个协议组的版本号必须同一次提交。
- **增量同步**：`SubPartSyncPayload` 使用 `SynchedEntityData.DataValue` 列表 + byte `255` 终结符。
- **状态同步**：VehicleCore HP、SubPart 耐久度、子系统耐久度/激活状态、连接器完整性通过 `SynchedEntityData` + 定向载荷同步。

## 反模式

- **严禁添加物理线程载荷处理器**：所有处理器必须在主线程运行。
- **严禁破坏协议版本兼容性**：Research 处于 2.0.0 是有原因的。
- **严禁使用旧版连接器 ID 格式**：未来重构将使用 SubPart ID 替代 "载具 UUID-部件 UUID-连接器名"。
