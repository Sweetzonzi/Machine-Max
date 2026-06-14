# 炮镜 HUD 自定义分划设计

> 版本 1.2 · 2026-06-14

***

## 一、设计目标

将现有硬编码的 `SightHud`（圆环+十字线）改造为 UGC 可定制系统，同时解决三个核心问题：

1. **元素跟随基准**：分划、刻度等"画在镜片上的标记"应随炮镜姿态晃动；弹药选择、小地图等服务玩家的 UI 应固定在屏幕上
2. **投影类型**：2D 图形（十字线、数字）用正交投影；3D 模型（激光指示器、标尺模型）用透视投影
3. **旋转跟随深度**：对称分划仅需平移原点；非对称分划（测距标尺）需跟着炮镜偏转方向旋转，实现"斜眼看望远镜"的视差效果

***

## 二、核心设计决策

### 决策一：不改现有 `HudAttr.perspective` 语义

`CustomHud` 已有的 `perspective` 字段继续承担"正交/透视投影"区分。炮镜 HUD 在此基础上增加 `scope_behavior` 枚举，正交两个维度。

### 决策二：`scope_behavior` 用三值枚举

三个语义互不重叠，枚举比双布尔更清晰，不存在 "screen\_fixed + rotate=true" 这种无意义组合。

```java
/** 炮镜模式下 HUD 元素的跟随行为 */
public enum ScopeBehavior {
    /** 固定在屏幕上，原点=屏幕中心，无旋转。用于弹药选择、小地图等 */
    SCREEN_FIXED,
    /** 跟随炮镜位置但不旋转：原点=炮镜投影中心，元素保持屏幕轴对齐。
     *  用于对称十字线、圆形分划等对称图形。默认值。 */
    FOLLOW_POSITION,
    /** 跟随炮镜完整姿态：原点=炮镜投影中心，额外叠加 scope 的 pitch/yaw 偏移旋转。
     *  用于非对称测距标尺、密位点分划等需要"斜眼看"效果的图形。 */
    FOLLOW_TRANSFORM
}
```

### 决策三：枚举序列化为小写蛇形

JSON codec: `"screen_fixed"` / `"follow_position"` / `"follow_transform"`，默认 `"follow_position"`。

### 决策四：FOLLOW\_TRANSFORM 的分划板距离复用 `offset.z`

FOLLOW\_TRANSFORM 需要将元素推到距摄像机原点一定距离再旋转，此距离直接取自元素已有的 `offset.z`：`offset.z` 天然就是摄像机前方距离（如 `-5`）

UGC 作者在 `FOLLOW_TRANSFORM` 下只需正常设 `offset.z`，无需额外字段。实现层对 `|z| < 1f` 的情况回退到最小可用值防止旋转无效。

- `FOLLOW_POSITION`：`translate(scopeOffset)` → `scale(zoom)` → `offset` → `renderContent`
- `FOLLOW_TRANSFORM`：`translate(scopeOffset)` → `scale(zoom)` → `rotate(pitchDiff, yawDiff)` → `offset` → `renderContent`

即 TRANSFORM 只是在 POSITION 的基础上多一次旋转。`offset.z` 本身提供分划板距离，旋转后自然产生屏幕偏移，无需额外 `translate(0,0,-d)`。

***

## 三、行为矩阵

| perspective | scope\_behavior       | 普通模式（CustomHud） | 炮镜模式（SightHud）            |
| ----------- | --------------------- | --------------- | ------------------------- |
| `false`     | `SCREEN_FIXED`        | 2D，原点=屏幕中心      | 2D，原点=屏幕中心，无旋转            |
| `false`     | `FOLLOW_POSITION`（默认） | 2D，原点=屏幕中心      | 2D，原点=炮镜投影中心，无旋转          |
| `false`     | `FOLLOW_TRANSFORM`    | 2D，原点=屏幕中心      | 2D，原点=炮镜投影中心，叠加 scope 旋转  |
| `true`      | `SCREEN_FIXED`        | 3D，原点=摄像机原点     | 3D，原点=摄像机原点               |
| `true`      | `FOLLOW_POSITION`（默认） | 3D，原点=摄像机原点     | 3D，平移到 scope 摄像机局部坐标      |
| `true`      | `FOLLOW_TRANSFORM`    | 3D，原点=摄像机原点     | 3D，平移到 scope 摄像机局部坐标 + 旋转 |

**关键规则：**

- 普通模式下 `scope_behavior` 完全忽略 —— 所有元素等效 `SCREEN_FIXED`
- 正交和透视的变换逻辑统一：
  - `SCREEN_FIXED` = `origin` → `offset` → `renderContent`
  - `FOLLOW_POSITION` = `origin` → `scale(zoom)` → `offset` → `renderContent`
  - `FOLLOW_TRANSFORM` = `origin` → `scale(zoom)` → `rotate(pitchDiff, yawDiff)` → `offset` → `renderContent`
- `scopeOffset`：正交用炮镜投影中心像素偏移，透视用 `worldToCameraLocal` 结果

***

## 四、数据模型变更

### 4.1 `HudAttr` 新增枚举 + 字段

```java
// HudAttr.java

/**
 * 炮镜模式下 HUD 元素的跟随行为。
 * <p>
 * 控制正交元素的原点位置和旋转变换，以及透视元素的摄像机基准。
 * 仅在 SightHud 渲染管线中生效，普通 CustomHud 忽略（全部视为 SCREEN_FIXED）。
 */
public enum ScopeBehavior {
    /** 屏幕固定：原点=屏幕中心，无旋转 */
    SCREEN_FIXED,
    /** 跟随位置：原点=炮镜投影中心，不旋转（默认） */
    FOLLOW_POSITION,
    /** 跟随姿态：原点=炮镜投影中心，叠加 scope 的 pitch/yaw 偏移旋转 */
    FOLLOW_TRANSFORM
}

/**
 * @see ScopeBehavior
 */
public ScopeBehavior scopeBehavior = ScopeBehavior.FOLLOW_POSITION;
```

### 4.2 JSON Codec

枚举序列化：

```java
// 在 HudAttr.CODEC 中：
Codec.STRING
    .xmap(
        s -> ScopeBehavior.valueOf(s.toUpperCase()),
        v -> v.name().toLowerCase()
    )
    .optionalFieldOf("scope_behavior", ScopeBehavior.FOLLOW_POSITION)
    .forGetter(HudAttr::getScopeBehavior)
```

构造器增加对应参数：

```java
public HudAttr(String type, ResourceLocation model, ResourceLocation animation, ResourceLocation texture,
               Vec3 offset, Vec3 rotation, Vec3 scale,
               Vec3i color, int transparency,
               boolean perspective,
               ScopeBehavior scopeBehavior,  // 新增
               Map<String, TextParams> textAttr,
               boolean enableScissor, int scissorX, int scissorY, int scissorWidth, int scissorHeight) {
    // ...
    this.scopeBehavior = scopeBehavior;
    // ...
}
```

***

## 五、ScreenProjectionUtil 工具方法扩展

当前 `ScreenProjectionUtil` 仅有一个 `worldToScreenOffset()`，供 SightHud 计算炮管准星的屏幕投影位置。炮镜 HUD 自定义化后需要更多工具：

### 5.1 现有方法 — `worldToScreenOffset`

```java
/**
 * 将世界坐标点通过 FOV 透视投影到屏幕空间（像素偏移）。
 * 用于炮管准星定位、FOLLOW_POSITION 元素原点计算。
 *
 * @param worldPoint   世界空间目标点
 * @param camera       Minecraft 渲染摄像机
 * @param vFov         垂直视场角（度），炮镜模式传入 REFERENCE_FOV / currentZoom
 * @param screenWidth  屏幕宽度（像素）
 * @param screenHeight 屏幕高度（像素）
 * @return float[2] {offsetX, offsetY}，正=右/下；目标在摄像机后方返回 null
 */
public static float[] worldToScreenOffset(Vec3 worldPoint, Camera camera,
                                          float vFov, int screenWidth, int screenHeight)
```

### 5.2 新增 — `worldToCameraLocal`

```java
/**
 * 将世界坐标点转换到摄像机局部坐标系（不投影到像素）。
 * <p>
 * 用于透视渲染时直接在相机空间摆放元素，无需经过 FOV→像素 转换。
 * 正交渲染中配合正交投影矩阵使用也可能需要。
 *
 * @param worldPoint 世界空间目标点
 * @param camera     Minecraft 渲染摄像机
 * @return float[3] {localX, localY, localZ}，localZ 为深度；
 *         目标在摄像机后方时 localZ ≤ 0
 */
public static float[] worldToCameraLocal(Vec3 worldPoint, Camera camera) {
    Vec3 camPos = camera.getPosition();

    Vector3f camForward = new Vector3f(0, 0, -1);
    camera.rotation().transform(camForward);
    Vector3f camRight = new Vector3f(1, 0, 0);
    camera.rotation().transform(camRight);
    Vector3f camUp = new Vector3f(0, 1, 0);
    camera.rotation().transform(camUp);

    double dx = worldPoint.x - camPos.x;
    double dy = worldPoint.y - camPos.y;
    double dz = worldPoint.z - camPos.z;

    float localX = (float) (camRight.x * dx + camRight.y * dy + camRight.z * dz);
    float localY = (float) (camUp.x * dx + camUp.y * dy + camUp.z * dz);
    float localZ = (float) (camForward.x * dx + camForward.y * dy + camForward.z * dz);

    return new float[]{localX, localY, localZ};
}
```

**透视投影不需要 FOV 的原因**：透视元素的投影矩阵由 OpenGL `Matrix4f.setPerspective(fov, aspect, near, far)` 统一设置，GPU 自动将相机空间的三维坐标映射到屏幕。我们只需拿到摄像机局部坐标即可，FOV 由投影矩阵处理。

### 5.3 新增 — `worldDirToCameraAngles`

```java
/**
 * 计算世界方向向量相对于摄像机朝向的 pitch/yaw 角差（弧度）。
 * <p>
 * 用于 FOLLOW_TRANSFORM 元素的 poseStack 旋转：
 * 将世界方向（如 scope locator 的前方指向）与摄像机朝向做差，
 * 得到"炮镜偏离屏幕中心的角差"，直接作为 poseStack 旋转量。
 *
 * @param worldDir 世界空间方向向量（需归一化或至少方向正确）
 * @param camera   Minecraft 渲染摄像机
 * @return float[2] {pitchDiff, yawDiff}（弧度），pitch 正=上，yaw 正=右
 */
public static float[] worldDirToCameraAngles(Vec3 worldDir, Camera camera) {
    Vector3f camForward = new Vector3f(0, 0, -1);
    camera.rotation().transform(camForward);
    Vector3f camRight = new Vector3f(1, 0, 0);
    camera.rotation().transform(camRight);
    Vector3f camUp = new Vector3f(0, 1, 0);
    camera.rotation().transform(camUp);

    // 投影到摄像机局部坐标系
    float lx = (float) (camRight.x * worldDir.x + camRight.y * worldDir.y + camRight.z * worldDir.z);
    float ly = (float) (camUp.x * worldDir.x + camUp.y * worldDir.y + camUp.z * worldDir.z);
    float lz = (float) (camForward.x * worldDir.x + camForward.y * worldDir.y + camForward.z * worldDir.z);

    float yawDiff = (float) Math.atan2(lx, lz);
    float pitchDiff = (float) Math.asin(Math.clamp(ly / (float) Math.sqrt(lx*lx + ly*ly + lz*lz), -1.0, 1.0));

    return new float[]{pitchDiff, yawDiff};
}
```

### 5.4 CustomHud 炮镜分支使用方式

```java
// CustomHud.renderInScopeMode() 中：

// 炮管瞄准点的屏幕投影中心（供 FOLLOW_POSITION 用）
Vec3 barrelAimPoint = CameraController.getCameraAimPointWorld(partialTick);
float[] screenOffset = ScreenProjectionUtil.worldToScreenOffset(
    barrelAimPoint, mcCam, vFov, screenW, screenH);
int scopeCenterX = Math.clamp(screenW / 2 + Math.round(screenOffset[0]), EDGE_MARGIN, screenW - EDGE_MARGIN);
int scopeCenterY = Math.clamp(screenH / 2 + Math.round(screenOffset[1]), EDGE_MARGIN, screenH - EDGE_MARGIN);

// scope 前方方向相对于玩家摄像机朝向的角差（供 FOLLOW_TRANSFORM 旋转用）
Vec3 scopeForward = barrelAimPoint.subtract(mcCam.getPosition());
float[] angles = ScreenProjectionUtil.worldDirToCameraAngles(scopeForward, mcCam);
float pitchDiff = angles[0];
float yawDiff = angles[1];

// 透视 FOLLOW_POSITION 用：将世界点转为摄像机局部空间（不依赖 FOV）
float[] localPos = ScreenProjectionUtil.worldToCameraLocal(barrelAimPoint, mcCam);
```

***

## 六、正交元素的姿态变换

不修改 `GuiAnimatable`——CustomHud 直接操作 poseStack，最后调用已有的 public 方法 `hud.renderContent()`。三种模式的区别仅在于 `scale` 和 `rotate` 的有无，`origin` 和 `offset` 始终一致：

```java
// CustomHud.renderOrthogonalInScope() 中
PoseStack poseStack = guiGraphics.pose();
poseStack.pushPose();

// 1. 原点
poseStack.translate(originX, originY, 0);

// 2. FOV 缩放（FOLLOW_POSITION / FOLLOW_TRANSFORM）
if (followScope && currentZoom != 1f) {
    poseStack.scale(currentZoom, currentZoom, 1f);
}

// 3. scope 旋转（FOLLOW_TRANSFORM 专属）
if (rotPitch != 0 || rotYaw != 0) {
    poseStack.mulPose(new Quaternionf().rotationYXZ(-rotYaw, rotPitch, 0));
}

// 4. 元素自身位移（HudAttr.offset，三个模式都有）
Vector3f off = hud.getParams().getOffset(partialTick);
poseStack.translate(off.x, off.y, off.z);

// 5. 委托 GuiAnimatable 渲染
hud.renderContent(poseStack, guiGraphics.bufferSource(), partialTick);

poseStack.popPose();
```

变换链：

```
SCREEN_FIXED:      origin → offset → renderContent
FOLLOW_POSITION:   origin → scale(zoom) → offset → renderContent
FOLLOW_TRANSFORM:  origin → scale(zoom) → rotate → offset → renderContent
```

***

## 七、渲染管线分派

### 7.1 职责划分

| 模块            | 职责                                            |
| ------------- | --------------------------------------------- |
| **CustomHud** | UGC HUD 生命周期管理 + 所有模式下的 UGC 元素渲染分派（普通座椅 + 炮镜） |
| **SightHud**  | 引擎级硬编码元素：炮管十字线（保留 `renderCrosshair`），不涉及 UGC  |

CustomHud 是 UGC HUD 的唯一渲染入口，SightHud 不重复渲染 hudComponents，避免双重渲染。

### 7.2 CustomHud：普通座椅模式 — 不变

```
所有元素 → perspective=false → 2D，原点=屏幕中心
          → perspective=true  → 3D，玩家摄像机 FOV + 视角
```

`scope_behavior` 被忽略，全部等效 `SCREEN_FIXED`。

### 7.3 CustomHud：炮镜模式 — 改造

CustomHud 已有炮镜分支（`CameraController.isCameraMode()`），改造为按 `scope_behavior` 分派：

```java
// CustomHud.render() 炮镜分支
if (CameraController.isCameraMode()) {
    CameraSubsystem camera = CameraController.getActiveCamera();
    if (camera != null && camera.isActive()) {
        renderInScopeMode(guiGraphics, camera, partialTick);
    }
    return;
}
```

```java
/** 炮镜模式下按 scope_behavior 分派渲染 UGC 元素 */
private void renderInScopeMode(GuiGraphics guiGraphics, CameraSubsystem camera, float partialTick) {
    Minecraft mc = Minecraft.getInstance();
    int screenW = guiGraphics.guiWidth(), screenH = guiGraphics.guiHeight();
    Camera mcCam = mc.gameRenderer.getMainCamera();
     float vFov = CameraSubsystemStaticAttr.REFERENCE_FOV / CameraController.getCurrentZoom();
     float currentZoom = CameraController.getCurrentZoom();

    // === 计算 scope 基准数据 ===
    Vec3 barrelAimPoint = CameraController.getCameraAimPointWorld(partialTick);
    int scopeCenterX = screenW / 2, scopeCenterY = screenH / 2;
    float pitchDiff = 0, yawDiff = 0;
    float[] scopeCameraLocal = null;

    if (barrelAimPoint != null) {
        float[] screenOff = ScreenProjectionUtil.worldToScreenOffset(
                barrelAimPoint, mcCam, vFov, screenW, screenH);
        if (screenOff != null) {
            scopeCenterX = Math.clamp(screenW/2 + Math.round(screenOff[0]),
                    EDGE_MARGIN, screenW - EDGE_MARGIN);
            scopeCenterY = Math.clamp(screenH/2 + Math.round(screenOff[1]),
                    EDGE_MARGIN, screenH - EDGE_MARGIN);
        }
        float[] angles = ScreenProjectionUtil.worldDirToCameraAngles(
                barrelAimPoint.subtract(mcCam.getPosition()), mcCam);
        pitchDiff = angles[0]; yawDiff = angles[1];
        // 透视 FOLLOW_POSITION 用的摄像机局部坐标
        scopeCameraLocal = ScreenProjectionUtil.worldToCameraLocal(barrelAimPoint, mcCam);
    }

    // === 渲染 UGC 元素 ===
    // 正交和透视用同一套 scope_behavior 分派逻辑。
    // 正交直接在 GUI 上下文渲染；透视在 renderPerspectiveBatch 内统一设置投影矩阵后渲染。
    renderOrthogonalInScopeBatch(guiGraphics, camera, screenW, screenH,
            scopeCenterX, scopeCenterY, pitchDiff, yawDiff, currentZoom, partialTick);
    renderPerspectiveInScopeBatch(guiGraphics, camera, screenW, screenH,
            scopeCenterX, scopeCenterY, pitchDiff, yawDiff, currentZoom, partialTick);
}
```

```java
/** 正交元素：按 scope_behavior 分派原点、缩放与旋转，最后调用 hud.renderContent() */
private void renderOrthogonalInScope(GuiGraphics guiGraphics, GuiAnimatable hud,
        int screenW, int screenH, int scCenterX, int scCenterY,
        float pitchDiff, float yawDiff, float currentZoom, float partialTick) {
    PoseStack poseStack = guiGraphics.pose();
    poseStack.pushPose();

    boolean followScope;
    boolean doRotate;
    switch (hud.getParams().getScopeBehavior()) {
        case SCREEN_FIXED:
            poseStack.translate(screenW / 2, screenH / 2, 0);
            followScope = false;
            doRotate = false;
            break;
        case FOLLOW_POSITION:
            poseStack.translate(scCenterX, scCenterY, 0);
            followScope = true;
            doRotate = false;
            break;
        case FOLLOW_TRANSFORM:
            poseStack.translate(scCenterX, scCenterY, 0);
            followScope = true;
            doRotate = true;
            break;
    }

    if (followScope && currentZoom != 1f) {
        poseStack.scale(currentZoom, currentZoom, 1f);
    }
    if (doRotate) {
        poseStack.mulPose(new Quaternionf().rotationYXZ(-yawDiff, pitchDiff, 0));
    }
    Vector3f off = hud.getParams().getOffset(partialTick);
    poseStack.translate(off.x, off.y, off.z);

    hud.renderContent(poseStack, guiGraphics.bufferSource(), partialTick);
    poseStack.popPose();
}
```

> 透视元素同理：在 `renderPerspectiveBatch` 内对每个元素执行相同的 `origin → scale(zoom) → rotate → offset` 变换，再调 `renderContent`。投影矩阵使用玩家 FOV，所有透视元素共用，无需区分。

### 7.4 SightHud：引擎级元素不变

```java
// SightHud 保留 render() + renderCrosshair() + renderCenterRing()
// 不读取 hudComponents，不涉及 UGC
```

***

## 八、与现有 SightHud 硬编码元素的关系

1. **保留** **`renderCrosshair`** —— 炮管指向十字线是引擎级元素，不交给 UGC
2. **保留** **`renderCenterRing`** —— 圆环作为引擎级"视角中心参考"保持不变
3. **不涉及 UGC** —— SightHud 不读取 `hudComponents`，UGC 全由 CustomHud 管理

***

## 九、涉及文件与改动量

| 文件                          | 改动                                     | 说明                                                 |
| --------------------------- | -------------------------------------- | -------------------------------------------------- |
| `HudAttr.java`              | +枚举 `ScopeBehavior` + 字段 + Codec + 构造器 | 数据模型                                               |
| `ScreenProjectionUtil.java` | +2 方法                                  | 投影工具扩展                                             |
| `CustomHud.java`            | \~140 行改                               | 炮镜分支改造：计算 scope 基准数据 + 按 scope\_behavior 分派正交/透视渲染 |
| `SightHud.java`             | 无改动                                    | 引擎级元素保持不变                                          |
| `GuiAnimatable.java`        | 无改动                                    | CustomHud 直接调 renderContent()                      |

总改动量 ≈ 180 行。

***

## 十、JSON 完整示例

### 对称十字线分划（默认 FOLLOW\_POSITION，不旋转）

```json
{
  "type": "scope_hud",
  "model": "sdkfz:scope/zeiss_reticle",
  "animation": "sdkfz:scope/zeiss_reticle",
  "texture": "sdkfz:textures/hud/zeiss_reticle.png",
  "offset": [0, 0, 0],
  "scale": [20, 20, 20],
  "perspective": false,
  "alpha": 200
}
```

### 非对称测距标尺（FOLLOW\_TRANSFORM，`offset.z` 为分划板距离）

```json
{
  "type": "scope_hud",
  "model": "sdkfz:scope/stadia_rangefinder",
  "animation": "sdkfz:scope/stadia_rangefinder",
  "texture": "sdkfz:textures/hud/stadia_rangefinder.png",
  "offset": [0, -30, 80],
  "scale": [18, 18, 18],
  "perspective": false,
  "scope_behavior": "follow_transform",
  "alpha": 220
}
```

### 弹药面板（SCREEN\_FIXED）

```json
{
  "type": "scope_hud",
  "model": "sdkfz:scope/ammo_panel",
  "animation": "sdkfz:scope/ammo_panel",
  "texture": "sdkfz:textures/hud/ammo_panel.png",
  "offset": [200, -80, 0],
  "scale": [15, 15, 15],
  "perspective": false,
  "scope_behavior": "screen_fixed",
  "alpha": 255,
  "texts": {
    "ammo_count": {
      "key": "hud.sdkfz.ammo_count",
      "centered": true,
      "molang_args": ["query.ammo_count"]
    }
  }
}
```

### 3D 测距标尺模型（透视 + 跟随 scope locator）

```json
{
  "type": "scope_hud",
  "model": "sdkfz:scope/range_finder",
  "animation": "sdkfz:scope/range_finder",
  "texture": "sdkfz:textures/hud/range_finder.png",
  "offset": [0, 0.3, -1.5],
  "scale": [0.5, 0.5, 0.5],
  "perspective": true,
  "alpha": 180
}
```

### 载具损伤预览模型（透视 + 屏幕固定）

```json
{
  "type": "scope_hud",
  "model": "sdkfz:scope/damage_preview",
  "animation": "sdkfz:scope/damage_preview",
  "texture": "sdkfz:textures/hud/vehicle_damage.png",
  "offset": [180, -100, -5],
  "rotation": [0, 0.8, 0],
  "scale": [3, 3, 3],
  "perspective": true,
  "scope_behavior": "screen_fixed",
  "alpha": 200
}
```

