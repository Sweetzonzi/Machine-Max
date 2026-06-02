# CameraSubsystem 观瞄系统 — 详细设计

> 版本 5.1 · 2026-06-02

---

## 一、设计目标

为武器系统提供配合的观瞄子系统，支持：

- **炮镜视角**：玩家切换到摄像机挂载点位置，以受限 FOV/视角范围观察
- **三轴稳定**：水平稳定 + 垂直稳定，由静态 `boolean` 控制；可组合产生无稳/垂稳/双稳效果
- **动态目标跟踪**（TRACKING）：当外部传入目标位置后自动追踪，叠加玩家提前量偏移
- **变焦**：一键切换基准/最大倍率，未绑定按键支持连续缩放
- **多摄像机切换**：车辆上可挂载多个 CameraSubsystem，按键循环遍历
- **统一信号输出**：始终以 `ViewInputSignal(Vec3)` 输出瞄准点，WeaponController 零改动

### 1.1 设计需求与典型场景

观瞄系统的核心需求来自真实载具作战中的四种典型光学设备：

#### 场景 A：坦克炮镜（无稳）

> 二战坦克的车长/炮手潜望镜。炮镜固定在炮塔内，随炮塔/车体运动。玩家通过按键切到炮镜视角，视野极窄（±5°），放大倍率固定或可切换。车体晃动 → 镜中画面跟着晃 → 瞄准点漂移 → 炮塔跟着漂移 ≡ **真实无稳定器体验**。

**对应配置：** `verticalStabilized=false, horizontalStabilized=false, yawLimit=5, pitchLimits=±8`

#### 场景 B：现代坦克炮镜（双稳）

> 带有陀螺稳定器的现代主战坦克炮镜。镜头伺服系统持续补偿车体晃动，炮手看到的画面始终稳定。离开炮镜视角后，炮镜持续跟踪上次瞄准的世界坐标点——这是**双轴稳定器**的效果。

**对应配置：** `verticalStabilized=true, horizontalStabilized=true`

#### 场景 C：战机瞄准吊舱（目标跟踪 + 提前量）

> 机载光电瞄准吊舱。雷达锁定敌机 → 摄像机跟随目标移动 → 飞行员在 HUD 上看到目标位置 → 叠加提前量偏移（预估弹丸飞行时间）→ 炮塔/导弹转向偏移后的瞄准点。离开视角后，吊舱持续照射目标。**`trackingTarget`（跟踪的敌机位置）≠ `lastAimPoint`（含提前量的输出瞄准点）**。

**对应配置：** `trackingTargetInputs` 配置雷达频道

#### 场景 D：船内/车长周视镜（独立于炮手）

> 大型舰船内部的 CCTV 监控屏或装甲车的车长独立周视镜。车长可以环顾四周而不影响炮手的瞄准。每个座位只能看到自己"被授权"的摄像机——炮手只能切炮镜，车长只能切周视镜。

**对应配置：** 通过 `discoveryInputs` 频道隔离（炮手座椅 → `"gunner_optics"` 频道 → 炮镜；车长座椅 → `"commander_panoramic"` 频道 → 周视镜）

---

### 1.2 设计原则

#### 原则一：统一输出 ViewInputSignal(Vec3)，不引入新信号类型

无论摄像机内部工作在何种模式（无稳/有稳/跟踪），对外始终以 `ViewInputSignal(Vec3)` 输出世界坐标瞄准点。**WeaponControllerSubsystem 一个字不用改**——它继续接收 `aim_inputs` 频道上的 `Vec3`，调用 `computeAimAngles()`，发送 `RotationSignal` 给 TurretDriver。

#### 原则二：稳定效果在客户端 CameraController 中实现，而非服务端

> **版本 5 变更**：版本 4 中稳定器在服务端 `onTick()` 中执行 (`updatePitchYawForStabilization()`)。版本 5 将其移至客户端 `CameraController` 中每渲染帧执行。

**原因**：

1. **帧率**：服务端 20tps 的稳定补偿不够平滑，画面跳变。客户端渲染帧（60fps+）可做到无缝稳定。
2. **locator 插值**：服务端只有 `getLocatorWorldTransform()`（无插值），而客户端有 `getLerpedLocatorWorldTransform(partialTick)`（带插值），后者才能产生平滑的视觉补偿。
3. **SynchedEntityData 延迟**：20tps→60fps 的同步间隔导致连续多帧读到同一值，画面停滞→跳变（抖振）。
4. **职责分离**：服务端只需关心"瞄准点是哪个世界坐标"，不需要关心"画面看起来稳不稳"。

稳定器的数学本质是：**从 `lastAimPoint`（上一帧的世界瞄准点）反算 `aimPitch`/`aimYaw`，以补偿 locator 的变化**。这个计算需要每渲染帧的插值 locator，天然属于客户端。

#### 原则三：TRACKING 是运行时态，不是静态模式

摄像机平时按稳定配置工作。当外部信号源（雷达、数据链、脚本）向 `trackingTargetInputs` 频道写入 `Vec3` 时，摄像机自动转入追踪。信号消失 → 退回稳定配置。

#### 原则四：座椅主动握手发现摄像机，而非摄像机广播自身

跟随 WeaponController → TurretDriver/Launcher 的现有握手机制。座椅向配置的频道发握手信号，摄像机通过基础设施的自动回调回复。

#### 原则五：playerOffset 用于跟踪模式下的提前量调整，非稳定补偿

> **版本 5 新增**

`playerOffsetPitch`/`playerOffsetYaw`（CameraController 中的鼠标累计偏移）的作用是允许玩家在 **TRACKING 模式下** 手动调整瞄准位置——例如防空时在跟踪敌机的基础上叠加水平偏移以给出提前量。它**不是**稳定器的一部分。

稳定器通过从 `lastAimPoint` 反算自动完成，而 `lastAimPoint` 天然编码了玩家偏移后的结果：
```
帧 N:   稳定修正后 aimPitch/aimYaw，玩家鼠标偏移 +2°pitch → 计算 lastAimPoint
帧 N+1: 车体晃动 → 稳定器从 lastAimPoint 反算 → aimPitch 自动含 +2° 偏移
```
玩家偏移被"编码"进了 `lastAimPoint`，稳定器会自动保持它。

**无稳模式下**：`playerOffset` 在 `turnCamera()` 中累积，叠加到不变的 `aimPitch`/`aimYaw` 上，然后 clamp 到摄像机限制。退出炮镜模式时清零。

#### 原则六：无观众且无跟踪 → 输出 EmptySignal，由座椅接管瞄准

> **版本 5 新增**

当摄像机没有活跃观众（玩家不在炮镜视角），且没有跟踪目标时，摄像机应输出 `EmptySignal`（即 `resetSignalOutputs()`）。此时玩家处于一般第三人称/第一人称状态，瞄准数据流应由座椅子系统通过 `ViewInputPayload` 的座椅路径接管。这避免了两个来源同时向 `aim_input` 频道发送瞄准信号的冲突。

---

### 1.3 数据流总览（版本 5）

```
═══════════════════════════════════════════════════════════════
  客户端 CameraController（每渲染帧 ~60fps+）
═══════════════════════════════════════════════════════════════

  进入炮镜模式时 (switchCamera):
    ├── 清除旧摄像机 hasViewer = false
    ├── 新摄像机 hasViewer = true
    ├── 从 synchedData 初始化 aimPitch/aimYaw（仅一次）
    └── resetCameraModeState() 重置 playerOffset / zoom

  炮镜模式每 tick (tick):
    ├── activeCamera.hasViewer = true  ← 客户端标记观众存在

  炮镜模式每帧 (updateCameraRotCameraMode):
    ├── Transform locator = camera.getLerpedLocatorWorldTransform(partialTick)  ← 插值! 平滑!
    │
    ├── ① 稳定器修正 camera.aimPitch / camera.aimYaw（补偿车体晃动）
    │     if (vertStab && lastAimPoint != null)  aimPitch = computePitchToPoint(locator, lastAimPoint)
    │     if (horStab && lastAimPoint != null)  aimYaw   = computeYawToPoint(locator, lastAimPoint)
    │     无稳 → aimPitch/aimYaw 不变（随车体漂移，符合真实无稳定器体验）
    │
    ├── ② 叠加 playerOffset（鼠标累计偏移，TRACKING 下用于提前量）
    │     finalPitch = aimPitch + playerOffsetPitch
    │     finalYaw   = aimYaw   + playerOffsetYaw
    │
    ├── ③ clamp 到摄像机限制
    │     finalPitch = clamp(finalPitch, minPitchRad, maxPitchRad)
    │     finalYaw   = clamp(finalYaw, -yawLimitHalf, yawLimitHalf)
    │
    ├── ④ 构建世界方向 → 设置 MC 相机角度
    │     Vec3 aimDir = buildWorldDirection(locator, finalPitch, finalYaw)
    │     event.setPitch/Yaw/Roll
    │
    └── ⑤ 更新 camera.lastAimPoint = rayFromAngles(locator, finalPitch, finalYaw)
          （供下一帧稳定器使用）

  炮镜模式每 tick (tickCameraMode):
    ├── 从 camera.aimPitch/aimYaw + playerOffset 计算 aimPoint
    └── 发送 ViewInputPayload(subPartId, cameraName, aimPoint) → 服务端

  退出炮镜模式 (exitCameraMode):
    ├── activeCamera.hasViewer = false  ← 清除观众标记
    └── activeCamera = null; 还原座椅控制

═══════════════════════════════════════════════════════════════
  服务端 CameraSubsystem.onTick()（20tps）
═══════════════════════════════════════════════════════════════

  if (isDestroyed() || !isActive()) → resetSignalOutputs(); return;

  readTrackingTarget();           // 读取跟踪目标 → isTracking / trackingTarget

  if (hasViewer) {                // 直接读取 volatile 标记（由 receiveClientAimInput 设置）
      // 有观众：瞄准点由客户端通过 receiveClientAimInput 注入（已含稳定+偏移）
      updatePitchYawFromAimPoint(lastAimPoint);  // 反算仅用于 synchedData（初始化用）

  } else if (isTracking) {
      // 有跟踪目标但无观众：服务端纯跟踪
      updatePitchYawFromTracking();
      lastAimPoint = trackingTarget;

  } else {
      // 无观众 + 无跟踪：输出 EmptySignal，座椅接管
      lastAimPoint = null;
  }

  // synchedData 精简同步
  synchedData.set(DATA_AIM_PITCH, aimPitch);
  synchedData.set(DATA_AIM_YAW, aimYaw);
  synchedData.set(DATA_IS_TRACKING, isTracking);

  // 输出信号
  if (lastAimPoint != null) {
      发送 ViewInputSignal(lastAimPoint) → aimOutputTargets
  } else {
      resetSignalOutputs();  // 发 EmptySignal，座椅接管
  }

  // 清除观众标记（由 receiveClientAimInput 每网络包设置，tick 末尾清理）
  this.hasViewer = false;

═══════════════════════════════════════════════════════════════
  网络
═══════════════════════════════════════════════════════════════

  ViewInputPayload(subPartId, subSystemName, aimX, aimY, aimZ)  ← 结构不变
    ├── subsystem instanceof AbstractControllableSubsystem  → setViewInputSignal()  [座椅路径]
    └── subsystem instanceof CameraSubsystem                → receiveClientAimInput() [摄像机路径]
```

---

## 二、CameraSubsystem 实例状态字段

> **注意**：`aimPitch`/`aimYaw`/`lastAimPoint` 为实例字段（非静态），因为一辆车上可能同时存在多个 CameraSubsystem（炮镜 + 周视镜 + 瞄准吊舱），每个有独立的视角状态。

```java
public class CameraSubsystem extends BasicSubsystem {
    public final CameraSubsystemAttr attr;

    /** 当前俯仰角偏移（弧度），相对于摄像机基座正前方。
     *  由客户端 CameraController 在渲染帧中通过稳定器修正。
     *  public volatile 供客户端跨线程直接读写。 */
    public volatile float aimPitch = 0f;

    /** 当前偏航角偏移（弧度），相对于摄像机基座正前方。
     *  由客户端 CameraController 在渲染帧中通过稳定器修正。
     *  public volatile 供客户端跨线程直接读写。 */
    public volatile float aimYaw = 0f;

    /** 最后一次计算的世界坐标瞄准点（含稳定 + playerOffset）。
     *  客户端每渲染帧更新；服务端 onTick 在 hasViewer 时由客户端注入。
     *  public volatile 供客户端跨线程直接读写。 */
    public volatile Vec3 lastAimPoint = null;

    /** TRACKING 模式下跟踪的外部目标世界坐标（从信号频道读取，服务端 onTick 更新） */
    private volatile Vec3 trackingTarget = null;

    /** 当前是否处于 TRACKING 模式（由 trackingTarget 驱动，非静态属性） */
    private volatile boolean isTracking = false;

    /** 当前是否有观众在看此摄像机。
     *  服务端由 receiveClientAimInput 每网络包设置，onTick 末尾清除；
     *  客户端由 CameraController 进入/退出炮镜时设置。 */
    public volatile boolean hasViewer = false;
}
```

---

## 三、静态属性 `CameraSubsystemStaticAttr`

文件: `attr/static_attr/CameraSubsystemStaticAttr.java`

```java
@Getter
public class CameraSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /** 垂直方向是否世界稳定（补偿车体俯仰晃动） */
    public final boolean verticalStabilized;

    /** 水平方向是否世界稳定（补偿车体偏航晃动） */
    public final boolean horizontalStabilized;

    /** 基准视场角（度），即 1× 变焦时的 FOV */
    public final float baseFov;

    /** 最小变焦倍率（默认 1.0） */
    public final float baseZoom;

    /** 最大变焦倍率（例如 8.0） */
    public final float maxZoom;

    /** 最小俯仰角限制（度），正值=抬头，负数=低头 */
    public final float minPitch;

    /** 最大俯仰角限制（度） */
    public final float maxPitch;

    /** 偏航角张角限制（度），实际范围 [center - limit/2, center + limit/2] */
    public final float yawLimit;

    /** 摄像机 HUD 组件列表 */
    public final List<ResourceLocation> hudComponents;

    /** 跟踪目标的输入频道列表 */
    public final List<String> trackingTargetInputs;

    /** 摄像机被发现的握手频道列表 */
    public final List<String> discoveryInputs;

    /** 是否允许在 F5 视角循环中出现 */
    public final boolean allowCycle;

    // CODEC 定义（字段与版本 4 相同，省略）
}
```

### 稳定模式组合

| verticalStabilized | horizontalStabilized | 效果 |
|:-:|:-:|------|
| `false` | `false` | 完全无稳，炮镜视角随车体运动 |
| `true` | `false` | 仅垂稳（pitch 自动补偿车体俯仰，yaw 固定） |
| `false` | `true` | 仅水平稳（yaw 自动补偿车体偏航，pitch 固定） |
| `true` | `true` | 双稳，镜中画面完全独立于车体晃动 |

TRACKING 模式为**运行时态**：只要 `trackingTarget != null`，摄像机自动转入 TRACKING（等效于双稳 + 目标跟踪），无需静态属性配置。

---

## 四、动态属性 `CameraSubsystemAttr`

文件: `attr/dynamic_attr/CameraSubsystemAttr.java`

```java
@Getter
public class CameraSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final CameraSubsystemStaticAttr staticAttribute;

    /** 摄像机挂载 locator 名称 */
    public final String locator;

    /** 瞄准点输出频道 → 目标接收者名列表 */
    public final Map<String, List<String>> aimOutputTargets;

    // CODEC 定义（字段与版本 4 相同，省略）
}
```

---

## 五、核心逻辑 `CameraSubsystem`

文件: `CameraSubsystem.java`

继承链: `AbstractSubsystem` → `BasicSubsystem` → `CameraSubsystem`

### 5.1 `onTick()` — 主线程 20tps（版本 5.1）

**与版本 4 的关键差异**：
1. 删除 `updatePitchYawForStabilization()` —— 稳定器移到客户端
2. 无观众+无跟踪 → `lastAimPoint = null` → `resetSignalOutputs()` → 座椅接管
3. `SynchedEntityData` 不再同步 `DATA_AIM_POINT_*` —— 客户端自己算
4. `hasViewer` 为 `public volatile` 字段，由 `receiveClientAimInput` 直接设置，不再通过 `SignalChannel` 中转
5. `onTick` 末尾清除 `this.hasViewer = false`

```java
@Override
public void onTick() {
    super.onTick();
    if (isDestroyed() || !isActive()) {
        resetSignalOutputs();
        return;
    }

    readTrackingTarget();                // ① 读取跟踪目标 → isTracking / trackingTarget

    if (hasViewer) {                     // ② 直接读取 volatile 标记
        // 有活跃观众：瞄准点由客户端通过 receiveClientAimInput 注入（已含稳定 + playerOffset）
        updatePitchYawFromAimPoint(lastAimPoint);
    } else if (isTracking) {
        // 有跟踪目标但无观众：服务端纯跟踪
        updatePitchYawFromTracking();
        this.lastAimPoint = trackingTarget;
    } else {
        // 无观众 + 无跟踪 → 清空瞄准点，输出 EmptySignal，座椅接管
        this.lastAimPoint = null;
    }

    // 精简同步（仅用于客户端初始化 + 状态标记）
    synchedData.set(DATA_AIM_PITCH, aimPitch);
    synchedData.set(DATA_AIM_YAW, aimYaw);
    synchedData.set(DATA_IS_TRACKING, isTracking);

    // 输出信号
    if (lastAimPoint != null) {
        for (String channel : attr.aimOutputTargets.keySet()) {
            sendSignalToAllTargets(channel, new ViewInputSignal(lastAimPoint));
        }
    } else {
        resetSignalOutputs();  // EmptySignal，座椅接管瞄准数据流
    }

    // 清除观众标记（由 receiveClientAimInput 每网络包设置，tick 末尾清理）
    this.hasViewer = false;
}
```

### 5.2 辅助方法

```java
/**
 * 接收来自 ViewInputPayload 服务端 handler 的客户端瞄准点。
 * 直接设置 hasViewer 标记和 lastAimPoint，不再通过 SignalChannel 中转。
 */
public void receiveClientAimInput(Vec3 aimPoint) {
    this.hasViewer = true;
    this.lastAimPoint = aimPoint;
}

/** 从 trackingTargetInputs 频道读取跟踪目标 */
private void readTrackingTarget() { /* 与版本 4 相同 */ }

/** 无观众时从 trackingTarget 计算 aimPitch/aimYaw */
private void updatePitchYawFromTracking() { /* 与版本 4 相同 */ }

/** 从 lastAimPoint 反算 aimPitch/aimYaw（仅用于 synchedData 初始化） */
private void updatePitchYawFromAimPoint(Vec3 aimPoint) { /* 与版本 4 相同 */ }

/** 计算 locator → 目标点的 pitch */
private float computePitchToPoint(Transform locator, Vec3 aimPoint) { /* 与版本 4 相同 */ }

/** 计算 locator → 目标点的 yaw */
private float computeYawToPoint(Transform locator, Vec3 aimPoint) { /* 与版本 4 相同 */ }
```

### 5.3 `SynchedEntityData` 精简

**版本 5** 不在 `defineSynchedData` 中注册 `DATA_AIM_POINT_X/Y/Z`（移除 3 个 key）。客户端不使用同步的瞄准点——它自己每帧从 `volatile aimPitch`/`aimYaw` + `lerpedLocator` 计算。

保留的 data keys：
```
DATA_AIM_PITCH   → 进入炮镜模式时的 aimPitch 初始值
DATA_AIM_YAW     → 进入炮镜模式时的 aimYaw 初始值
DATA_IS_TRACKING → 跟踪状态（客户端可能用于 HUD 显示）
```

---

## 六、`AbstractControllableSubsystem` 摄像机发现与缓存

**无变更，与版本 4 相同。**（`discoveredCameras`、`cameraDiscoveryHandshake()`、`onSignalUpdated` 回调、座椅 `SeatSubsystemAttr.cameraDiscoveryTargets` 等全部保持不变）

---

## 七、网络协议

### 7.1 `ViewInputPayload.serverHandler()`

**无变更，与版本 4 相同。**（已有 `CameraSubsystem` 的 `else if` 分支）

### 7.2 状态同步

利用现有 `SubsystemSyncPayload` 同步 `SynchedEntityData`。

**版本 5 变化**：`CameraSubsystem` 同步的字段从 6 个减少到 3 个：
- ~~`DATA_AIM_POINT_X`~~ → 移除
- ~~`DATA_AIM_POINT_Y`~~ → 移除
- ~~`DATA_AIM_POINT_Z`~~ → 移除
- `DATA_AIM_PITCH` → 保留（进入炮镜时初始化用）
- `DATA_AIM_YAW` → 保留（进入炮镜时初始化用）
- `DATA_IS_TRACKING` → 保留（HUD 状态显示用）

---

## 八、客户端 `CameraController`改造（版本 5 全新设计）

文件: [CameraController.java](file:///d:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/client/input/CameraController.java)

### 8.1 CameraController 静态状态

> **注意**：`activeCamera`、`playerOffset*`、`currentZoom` 仍是 CameraController 的静态字段——同一时刻只有一个 activeCamera，这些是"当前玩家的视角状态"。多摄像机共存的 `aimPitch`/`aimYaw`/`lastAimPoint` 存储在各自的 `CameraSubsystem` 实例上（volatile）。

```java
/** 当前激活的摄像机（null=普通座椅视角） */
private static CameraSubsystem activeCamera = null;
/** 炮镜模式下玩家的鼠标累计偏移 pitch（弧度）。用于 TRACKING 下的提前量调整 */
private static float playerOffsetPitch = 0f;
/** 炮镜模式下玩家的鼠标累计偏移 yaw（弧度） */
private static float playerOffsetYaw = 0f;
/** 当前变焦倍率，在 [baseZoom, maxZoom] 之间 */
private static float currentZoom = 1f;
/** 连续变焦混合值（0=baseZoom, 1=maxZoom） */
private static float zoomBlend = 0f;
```

### 8.2 `tick()` 改造

```java
@SubscribeEvent
public static void tick(ClientTickEvent.Post event) {
    if (client == null) client = Minecraft.getInstance();
    if (client.player == null) return;

    AbstractControllableSubsystem subsystem =
            ((IEntityMixin) client.player).machine_Max$getControllingSubsystem();

    if (subsystem instanceof SeatSubsystem seat) {
        // 验证当前摄像机仍然有效
        if (activeCamera != null) {
            if (!activeCamera.isActive() || activeCamera.isDestroyed()) {
                exitCameraMode();
            }
        }

        // 炮镜模式
        if (activeCamera != null) {
            activeCamera.hasViewer = true;  // 客户端标记观众存在
            tickCameraMode(seat);
            return; // 跳过座椅视角处理
        }

        // 普通座椅视角（现有逻辑不变）
        tickSeatMode(seat);
    } else {
        exitCameraMode();
        lastSentAimPoint = null;
    }
}
```

### 8.3 `updateCameraRotCameraMode()` — 炮镜每渲染帧

**这是版本 5 的核心：稳定器在此实现。**

```java
/** 炮镜模式下的相机旋转计算（每渲染帧，含稳定器） */
private static void updateCameraRotCameraMode(ViewportEvent.ComputeCameraAngles event, float partialTick) {
    CameraSubsystem camera = activeCamera;
    Transform locator = camera.getLerpedLocatorWorldTransform(partialTick);  // ← 插值后的locator
    var sa = camera.attr.staticAttribute;

    // ① 稳定器修正 camera.aimPitch / camera.aimYaw（补偿车体晃动）【版本5新增】
    if (camera.lastAimPoint != null) {
        if (sa.isVerticalStabilized()) {
            camera.aimPitch = computePitchToPoint(locator, camera.lastAimPoint);
        }
        // 无垂稳 → aimPitch 不变（车体俯仰 → 瞄准点漂移）
        if (sa.isHorizontalStabilized()) {
            camera.aimYaw = computeYawToPoint(locator, camera.lastAimPoint);
        }
        // 无水平稳 → aimYaw 不变（车体偏航 → 瞄准点漂移）
    }

    // ② 叠加玩家鼠标偏移（playerOffset 用于 TRACKING 模式提前量）
    float finalPitch = camera.aimPitch + playerOffsetPitch;
    float finalYaw   = camera.aimYaw   + playerOffsetYaw;

    // ③ clamp 到摄像机限制
    finalPitch = Math.clamp(finalPitch,
            (float) Math.toRadians(sa.getMinPitch()),
            (float) Math.toRadians(sa.getMaxPitch()));
    finalYaw = Math.clamp(finalYaw,
            -(float) Math.toRadians(sa.getYawLimit() / 2),
             (float) Math.toRadians(sa.getYawLimit() / 2));

    // ④ 在 locator 局部空间构建方向向量，转世界空间 → 设置 MC 相机角度
    Matrix3f aimMat = new Quaternion().fromAngles(finalPitch, finalYaw, 0).toRotationMatrix();
    Vector3f dir = new Vector3f();
    aimMat.mult(new Vector3f(0, 0, 1), dir);
    locator.getRotation().toRotationMatrix().mult(dir, dir);
    Vec3 aimDir = SparkMathKt.toVec3(dir);

    double pitchDeg = - Math.toDegrees(Math.asin(Math.clamp(aimDir.y, -1.0, 1.0)));
    double yawDeg   = -Math.toDegrees(Math.atan2(-aimDir.x, -aimDir.z));

    event.setPitch((float) pitchDeg);
    event.setYaw((float) yawDeg);
    event.setRoll(0);

    // ⑤ 更新 lastAimPoint = 本帧世界瞄准点（供下一帧稳定器反算）
    camera.lastAimPoint = rayFromAngles(locator, finalPitch, finalYaw);
    aimDirection = aimDir;
}
```

### 8.4 `tickCameraMode()` 炮镜每 tick

```java
/** 炮镜模式每 tick（20tps）：发送瞄准点到服务端 */
private static void tickCameraMode(SeatSubsystem seat) {
    CameraSubsystem camera = activeCamera;

    // 使用本地 volatile aimPitch/aimYaw + playerOffset 计算瞄准点
    float finalPitch = camera.aimPitch + playerOffsetPitch;
    float finalYaw   = camera.aimYaw   + playerOffsetYaw;

    Transform locator = camera.getLerpedLocatorWorldTransform(1f);
    Vec3 aimPoint = rayFromAngles(locator, finalPitch, finalYaw);

    // 发送 ViewInputPayload（目标=camera，不是 seat）
    SubPart subPart = camera.getOwner().getSubPart();
    if (lastSentAimPoint == null
            || aimPoint.distanceToSqr(lastSentAimPoint) > AIM_POINT_THRESHOLD_SQ) {
        lastSentAimPoint = aimPoint;
        PacketDistributor.sendToServer(new ViewInputPayload(
                subPart.getId(), camera.getName(),
                aimPoint.x, aimPoint.y, aimPoint.z));
    }
}
```

### 8.5 `turnCamera()` 鼠标偏移累积

```java
public static void turnCamera(double yRot, double xRot) {
    float f = (float) xRot * 0.15F;
    float f1 = (float) yRot * 0.15F;
    LocalPlayer player = client.player;
    if (player == null) return;

    // 炮镜模式：鼠标偏移累积为 playerOffset（用于 TRACKING 下提前量调整）
    if (activeCamera != null && activeCamera.isActive()) {
        var sa = activeCamera.attr.staticAttribute;
        float pitchRad = (float) Math.toRadians(f);
        float yawRad   = (float) Math.toRadians(f1);
        playerOffsetPitch = Math.clamp(playerOffsetPitch + pitchRad,
                (float) Math.toRadians(sa.getMinPitch()),
                (float) Math.toRadians(sa.getMaxPitch()));
        playerOffsetYaw = Math.clamp(playerOffsetYaw + yawRad,
                -(float) Math.toRadians(sa.getYawLimit() / 2),
                 (float) Math.toRadians(sa.getYawLimit() / 2));
        return;
    }

    // 座椅模式（现有逻辑不变）
    // ...
}
```

### 8.6 `switchCamera()` 进入炮镜时初始化

```java
public static void switchCamera(int direction) {
    // ... 摄像机筛选与选择逻辑与版本4相同 ...

    CameraSubsystem oldCamera = activeCamera;

    if (activeCamera == null || direction == 0) {
        activeCamera = cameras.getFirst();
    } else if (direction > 0) {
        activeCamera = cameras.get((idx + 1) % cameras.size());
    } else {
        activeCamera = cameras.get(idx - 1);
        // ... (direction < 0 时 idx<=0 则 exitCameraMode)
    }

    // 清除旧摄像机的观众标记
    if (oldCamera != null && oldCamera != activeCamera) {
        oldCamera.hasViewer = false;
    }

    // 设置新摄像机的观众标记 + 从 synchedData 初始化 aimPitch/aimYaw
    if (activeCamera != null) {
        activeCamera.hasViewer = true;
        activeCamera.aimPitch = activeCamera.getSynchedData().get(CameraSubsystem.DATA_AIM_PITCH);
        activeCamera.aimYaw = activeCamera.getSynchedData().get(CameraSubsystem.DATA_AIM_YAW);
    }

    resetCameraModeState();
}
```

### 8.7 `exitCameraMode()` 退出炮镜

```java
public static void exitCameraMode() {
    if (activeCamera != null) {
        activeCamera.hasViewer = false;  // 清除观众标记
    }
    activeCamera = null;
    playerOffsetPitch = 0;
    playerOffsetYaw = 0;
    currentZoom = 1f;
    zoomBlend = 0f;
}
```

---

## 九、按键绑定

**无变更，与版本 4 相同。**（`CYCLE_CAMERA_KEY`(V)、`CAMERA_ZOOM_KEY`(Z)、`CAMERA_ZOOM_IN/OUT_KEY`）

---

## 十、HUD 渲染

**无变更，与版本 4 相同。**（`CustomHud.java` 炮镜分支已实现）

---

## 十一、内容包 JSON 示例

**无变更，与版本 4 相同。**

---

## 十二、版本 4 → 版本 5.1 变更总结

| 变更项 | 版本 4 | 版本 5.1 |
|--------|--------|--------|
| 稳定器位置 | 服务端 `onTick()` 的 `updatePitchYawForStabilization()` | 客户端 `updateCameraRotCameraMode()` 每渲染帧 |
| 稳定器帧率 | 20tps（跳变/抖振） | 渲染帧率 60fps+（平滑） |
| locator | `getLocatorWorldTransform()` 无插值 | `getLerpedLocatorWorldTransform(partialTick)` 带插值 |
| aimPitch/aimYaw 驱动 | 客户端从 `SynchedEntityData` 每帧读取 | 客户端本地 `public volatile` 实例字段 + 每帧稳定器修正 |
| hasViewer 检测 | `readClientAimInput()` 读 SignalChannel | 服务端 `volatile hasViewer` 字段，`receiveClientAimInput` 直接设置 + `onTick` 末尾清除；客户端 `CameraController` 在 tick/switch/exit 中设置 |
| 客户端瞄准点注入 | SignalChannel `client_aim_input` 中转 | `receiveClientAimInput` 直接写 `hasViewer=true` + `lastAimPoint` |
| SynchedEntityData 同步字段 | 6 个（含 aimPointX/Y/Z） | 3 个（仅 pitch/yaw/isTracking） |
| 无观众+无跟踪输出 | 仍输出 `ViewInputSignal`（与座椅冲突） | 输出 `EmptySignal`（座椅接管） |
| playerOffset 角色 | 未明确 | 明确：跟踪模式下的提前量调整 |
| 服务端 onTick() | 4 分支逻辑（isTracking/hasViewer/稳定/销毁） | 3 分支逻辑（hasViewer/isTracking/二者皆无） |

### 变更清单（代码层面）

| 文件 | 改动 |
|------|------|
| `CameraSubsystem.java` | ① aimPitch/aimYaw/lastAimPoint 改为 public volatile；② 新增 public volatile hasViewer；③ 删除 readClientAimInput()；④ receiveClientAimInput() 直接设置 hasViewer=true + lastAimPoint；⑤ onTick() 直接读 this.hasViewer、末尾清除；⑥ defineSynchedData 移除 3 个 aimPoint key |
| `CameraController.java` | ① updateCameraRotCameraMode 新增稳定器逻辑；② tickCameraMode 用 camera.aimPitch/aimYaw 替代 synchedData；③ tick() 中设置 activeCamera.hasViewer=true；④ switchCamera() 管理 hasViewer（清旧设新）+ synchedData 初始化；⑤ exitCameraMode() 清除 hasViewer；⑥ 新增 computePitchToPoint/computeYawToPoint |

---

## 十三、实现状态（版本 5.1 — 已完成）

| 阶段 | 涉及文件 | 改动量 | 说明 |
|------|---------|--------|------|
| **1. 服务端** | `CameraSubsystem.java` | ~40 行改 | 精简 onTick + volatile + 移除 SynchedData key |
| **2. 客户端** | `CameraController.java` | ~80 行改 | 稳定器移到 updateCameraRotCameraMode + 移除 synchedData 驱动 |
| **3. 编译验证** | — | — | `./gradlew build` |
