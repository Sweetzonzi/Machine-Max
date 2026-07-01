# CameraSubsystem 重构：从信号发送者转为视角提供者

> 版本 1.1 · 2026-07-02

***

## 一、设计目标

将 CameraSubsystem 从"自行发送 ViewInputSignal"的角色改为"纯视角数据提供者"，信号发送职责统一收归 `AbstractControllableSubsystem` 通过控制组执行。

核心收益：

1. 信号路由统一走控制组，优先级系统（`_p1`/`_p2`/无后缀）在炮镜模式下也生效
2. CameraSubsystem 瘦身为纯数据层，不再依赖信号系统
3. 消除当前座椅/摄像机两条信号路径的互斥竞争逻辑

***

## 二、当前架构问题

当前 ViewInputSignal 有两条互斥的发送路径：

```
炮镜模式: CameraController → ViewInputPayload → CameraSubsystem.onTick()
              └─→ 自身 aimOutputTargets ──→ WeaponController

座椅模式: CameraController → ViewInputPayload → seat.setViewInputSignal()
              └─→ 控制组 viewTargets ──→ WeaponController
```

问题：

- `CameraSubsystemAttr.aimOutputTargets` 独立于控制组，优先级系统对炮镜无效
- `ViewInputPayload.serverHandler` 中需手动清除 `camera.lastAimPoint` 防止两路径竞争
- CameraSubsystem 既存储数据又负责发送，职责过重

***

## 三、目标架构

CameraSubsystem 不调用 `sendSignalToAllTargets()`，仅存储视角数据。
`AbstractControllableSubsystem` 通过 `activeCamera` 字段读取其公开的 volatile 数据，经控制组 `viewTargets` 统一发送。

```
炮镜模式: CameraController → ViewInputPayload → CameraSubsystem.receiveClientAimInput()
                                                   [仅存储 volatile 字段]
                              ↓
           AbstractControllableSubsystem.onTick(): 直接读 activeCamera.lastAimPoint 等
                              ↓
           setViewInputSignal(aimPoint, stabFlags) ──→ viewTargets(控制组) → WeaponController
```

座椅模式与炮镜模式统一切入点：`AbstractControllableSubsystem` 发送信号。

***

## 四、分步改动

### 步骤 1：CameraSubsystem 瘦身

**文件：** `CameraSubsystem.java`

- 删除 `onTick()` 中的 `sendSignalToAllTargets()` 调用
- 删除 `onTick()` 中的 `resetSignalOutputs()` 调用
- `onTick()` 仅保留 `synchedData` 同步 + `readTrackingTarget()` 逻辑
- 删除 `getTargetNames()` 返回的 `aimOutputTargets`
- **不新增 `getCurrentViewpoint()`**：volatile 字段已是 public，直接读即可

### 步骤 2：CameraSubsystemAttr 清理

**文件：** `CameraSubsystemAttr.java`

- 直接删除 `aimOutputTargets` 字段、getter、codec 条目、构造函数参数
- JSON 中 `aim_output_targets` 字段随之废弃，已有的忽略即可

### 步骤 3：AbstractControllableSubsystem 接管信号发送

**文件：** `AbstractControllableSubsystem.java`

- 新增 `activeCamera` 字段（`@Nullable CameraSubsystem`），放在 `discoveredCameras` 旁边
- `setViewInputSignal()` 签名扩展，接受稳定标志参数：

```java
// 旧签名
public void setViewInputSignal(@Nullable Vec3 aimPoint)

// 新签名
public void setViewInputSignal(@Nullable Vec3 aimPoint,
                                boolean pitchStabilized,
                                boolean yawStabilized)
```

- 座椅模式调用点：`setViewInputSignal(aimPoint, true, true)`
- `onTick()` 中新增炮镜路径：若 `activeCamera` 有效且有 `lastAimPoint`，直接从其 volatile 字段 + staticAttr 取数据发送：

```java
// onTick() 中 — 炮镜路径
if (activeCamera != null && activeCamera.isActive() && !activeCamera.isDestroyed()) {
    Vec3 aim = activeCamera.lastAimPoint;
    if (aim != null) {
        var sa = activeCamera.attr.staticAttribute;
        setViewInputSignal(aim,
                activeCamera.lastPitchOffsetDeg,
                activeCamera.lastYawOffsetDeg,
                sa.isVerticalStabilized(),
                sa.isHorizontalStabilized());
    }
}
```

- 暴露 `setActiveCamera(@Nullable CameraSubsystem)` 供 CameraController 调用

### 步骤 4：ViewInputPayload 服务端 handler 改造

**文件：** `ViewInputPayload.java` → `serverHandler()`

当前逻辑：

```java
if (subsystem instanceof AbstractControllableSubsystem controllable) {
    controllable.setViewInputSignal(aimPoint);
    // 清除所有发现摄像机的 lastAimPoint（防止竞争）
    for (CameraSubsystem cam : seat.getDiscoveredCameras()) { ... }
} else if (subsystem instanceof CameraSubsystem cam) {
    cam.receiveClientAimInput(aimPoint, pitchOff, yawOff);
}
```

改为：

```java
if (subsystem instanceof AbstractControllableSubsystem controllable) {
    controllable.setViewInputSignal(aimPoint, true, true);
} else if (subsystem instanceof CameraSubsystem cam) {
    // 炮镜模式：只存数据，由 ControllableSubsystem.onTick() 读取后统一发送
    cam.receiveClientAimInput(aimPoint, pitchOff, yawOff);
}
```

**删除了全部 camera.lastAimPoint 互斥清除逻辑** — 摄像机不再自己发信号，没有竞争。

### 步骤 5：CameraController 客户端适配

**文件：** `CameraController.java`

- `tickCameraMode()` 中删除 `camera.receiveClientAimInput()` 调用（handler 已做）
- `switchCamera()` 进入炮镜时不调 `seat.setViewInputSignal(null)`，改为调 `controllable.setActiveCamera(camera)`
- `exitCameraMode()` 退出炮镜时调 `controllable.setActiveCamera(null)` + 清除 camera.lastAimPoint

***

## 五、不改动的部分

| 组件 | 说明 |
|------|------|
| `CameraSubsystemStaticAttr` | 稳定标志、变焦范围等不变 |
| `ViewInputSignal` | 数据结构不变 |
| `WeaponControllerSubsystem` | 消费端零改动 |
| `SightSubsystem` | 继承 CameraSubsystem，自动受益 |
| `CameraController.turnCamera()` | 渲染帧的 aimPoint 更新逻辑不变 |

***

## 六、执行顺序

| 步骤 | 依赖 | 风险 |
|------|------|------|
| 1. CameraSubsystem 瘦身 | 无 | 低 — 仅删除发送逻辑 |
| 2. CameraSubsystemAttr 清理 | 步骤1 | 低 — 开发阶段直接删除，无兼容负担 |
| 3. AbstractControllableSubsystem 签名扩展 + activeCamera | 无 | 中 — 需同步修改调用点 |
| 4. ViewInputPayload handler 改造 | 步骤1,3 | 中 — 移除互斥逻辑需验证 |
| 5. CameraController 适配 | 步骤4 | 中 — activeCamera 切换逻辑 |
