# client/ — 渲染、输入、GUI

**范围**: 客户端渲染、玩家输入、HUD、屏幕、摄像头控制。

## 结构

```
client/
├── render/
│   ├── renderer/                 # 实体/方块实体渲染器
│   │   ├── PartEntityRenderer.java   # 主载具渲染器（GeoEntityRenderer）
│   │   ├── PartAssemblyRenderer.java # 装配阶段渲染
│   │   ├── Hud3DRenderer.java        # 3D HUD 渲染
│   │   ├── ClientProjectileRenderer.java
│   │   ├── ProjectileEntityRenderer.java
│   │   ├── LightSourceRenderer.java  # 光源渲染
│   │   ├── CustomModelItemRenderer.java
│   │   ├── BlockEntityItemRenderer.java
│   │   ├── MMRenderHandler.java
│   │   └── block/                    # 方块实体渲染器
│   │       ├── FabricatorBlockEntityRenderer.java
│   │       ├── ResearchTableBlockEntityRenderer.java
│   │       └── TotalStationBlockEntityRenderer.java
│   ├── gui/
│   │   ├── screen/               # 7 个游戏屏幕
│   │   │   ├── VehicleControlScreen.java  # AUI 载具控制面板
│   │   │   ├── FabricatingScreen.java     # 制造屏幕
│   │   │   ├── BlueprintResearchScreen.java
│   │   │   ├── ItemStorageSubsystemScreen.java
│   │   │   ├── VehicleNamingScreen.java
│   │   │   ├── WelcomeScreen.java
│   │   │   └── ResearchState.java
│   │   ├── hud/                  # HUD 叠加层
│   │   │   ├── AssemblyHud.java
│   │   │   ├── CustomHud.java
│   │   │   ├── InteractHud.java
│   │   │   └── ... (共 5 个)
│   │   ├── hud3d/                # 3D HUD
│   │   │   └── AssemblyHud3D.java (822 行)
│   │   ├── panel/                # 载具信息面板（7 个文件）
│   │   ├── animation/            # GUI 动画工具（4 个文件）
│   │   ├── renderable/           # GUI 可渲染元素
│   │   ├── MMGuiManager.java     # GUI 管理器
│   │   ├── MMGuis.java           # GUI 注册
│   │   └── VehicleInfoPanelManager.java
│   ├── renderable/               # ModelAnimatable, GuiAnimatable
│   ├── toast/                    # Toast 通知
│   └── MMRenderTypes.java        # 自定义渲染类型
├── input/
│   ├── RawInputHandler.java      # 键盘/鼠标 → 网络载荷
│   ├── CameraController.java     # 基于座位的摄像机，偏航/俯仰夹持
│   ├── KeyBinding.java           # 按键绑定
│   ├── MMKeyMapping.java         # 按键映射
│   └── MouseButtonMapping.java   # 鼠标按键映射
├── compat/
│   ├── jade/                     # Jade 插件（2 个文件）
│   └── jei/                      # JEI 插件（5 个文件）
├── event/                        # 客户端事件（2 个文件）
├── network/
│   └── ClientResearchHandler.java # 客户端研究系统处理器
├── MachineMaxClient.java          # 客户端入口
├── ClientSetup.java               # 视觉特效初始化
└── MMClientConfig.java            # 客户端设置
```

## 快速定位

| 任务 | 文件 | 说明 |
|------|------|------|
| 修复载具渲染 | `render/renderer/PartEntityRenderer.java` | GeoEntityRenderer，线框/纹理切换 |
| 修复 3D 装配 HUD | `render/gui/hud3d/AssemblyHud3D.java` | 822 行，摄像头控制、部件放置 |
| 修复 GUI 屏幕 | `render/gui/screen/*.java` | FabricatingScreen, BlueprintResearchScreen 等 |
| 修复 HUD 叠加层 | `render/gui/hud/*.java` | AssemblyHud, CustomHud, InteractHud |
| 修复输入处理 | `input/RawInputHandler.java` | 键盘/鼠标 → 网络载荷 |
| 修复摄像头 | `input/CameraController.java` | 座位摄像机、分轴稳定、FOV 过渡 |
| 新增渲染类型 | `render/MMRenderTypes.java` | LINES_ALWAYS_VISIBLE, SOLID_ALWAYS_VISIBLE |
| 修复方块实体渲染 | `render/renderer/block/*.java` | Fabricator, ResearchTable, TotalStation |
| 新增 JEI/Jade 集成 | `compat/jei/` 或 `compat/jade/` | 客户端配方/信息显示 |
| 客户端网络处理 | `network/ClientResearchHandler.java` | 研究系统客户端处理 |

## 约定

- **渲染数学仅用 JOML**：`org.joml.*`。服务端物理数据是 JME — 渲染前通过 `SparkMathKt.*` 转换。
- **PartEntityRenderer 模式**：线框（装配）、全纹理（完成）、淡入（新部件）、命中闪白、销毁淡出。
- **输入管线**：RawInputHandler → KeyBinding → 网络载荷 → 服务端信号系统。
- **VisualEffectHelper**（位于 `common/visual/VisualEffectHelper.java`）：存储客户端只渲染对象（附着点、包围盒、投射）。
- **3D HUD**：使用 `Hud3DRenderer` 配合自定义姿态栈和顶点消费者。

## 反模式

- **严禁在渲染代码中使用 JME 数学**：客户端仅用 JOML。服务端数据必须转换。
- **严禁从客户端渲染器调用仅服务端方法**：检查 `level.isClientSide`。
- **严禁忽略 `ModelAnimatable` 弃用**：迁移到 `SubPartAnimatable`。
