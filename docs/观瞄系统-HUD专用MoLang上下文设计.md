# HUD 专用 MoLang 上下文设计

> 版本 1.0 · 2026-06-15

***

## 一、设计动机

现有 `MechMolangContext` 绑定 `IAnimatable<Part>`，为载具部件动画提供 `local.*`（Part）/ `global.*`（装配体）查询。但在 HUD 渲染场景中缺少以下能力：

1. **HUD 渲染参数读取**：无法在模型动画关键帧中获取当前 zoom、FOV、屏幕尺寸、透视/正交标志
2. **HudAttr 参数可用**：模型的 `offset.z`（分划板距离）、`scale`（模型缩放）对角度→骨骼位移换算至关重要
3. **炮镜分划数据**：弹道 hold-over 角度需要在 MoLang 表达式中引用，驱动分划刻度线的骨骼位移

统一方案：新建 `HudMolangContext`，继承 `MechMolangContext`，新增 `hud.*` 与 `scope.*` 命名空间查询。

***

## 二、类层次结构

```
SparkMolangContext<IAnimatable<Part>>          (Spark-Core)
  └─ MechMolangContext                           (现有：local.* global.*)
       └─ HudMolangContext                       (★ 新增：hud.* scope.*)
```

`HudMolangContext` 继承 `MechMolangContext`，自动获得全部载具查询能力，无需转发。

### 2.1 多态用法

`GuiAnimatable` 持有一个 `hudContext` 字段（可空），`getMolangContext()` 中优先返回：

```java
// GuiAnimatable.java

/** HUD 专用 MoLang 上下文。非 null 时优先使用，覆盖 Part 默认上下文 */
private SparkMolangContext<?> hudContext = null;

public void setHudContext(SparkMolangContext<?> ctx) { this.hudContext = ctx; }

@Nullable
public SparkMolangContext<?> getMolangContext() {
    if (hudContext != null) return hudContext;
    SubPart sp = getRidingSubPart();
    if (sp != null) return sp.part.getSparkMolangContext();
    return molangContext;
}
```

`CustomHud` 在首次为炮镜创建 HUD 元素时调用 `hud.setHudContext(hudMolangContext)`，后续永久生效。

***

## 三、`hud.*` 命名空间 — HUD 渲染参数

每帧渲染前由 `CustomHud` 调用 `prepareFrame()` 刷新。

```java
public class HudMolangContext extends MechMolangContext {

    // ===== 每帧更新的渲染参数 =====
    private float currentZoom = 1f;
    private float vFovDeg = 70f;
    private int screenWidth = 1920;
    private int screenHeight = 1080;
    private boolean isPerspective = false;
    private float modelScaleX = 1f;
    private float modelScaleY = 1f;
    private float modelScaleZ = 1f;
    private float hudOffsetX = 0f;
    private float hudOffsetY = 0f;
    private float hudOffsetZ = 0f;
    private float scopeCenterX = 960f;  // 炮镜投影中心（屏幕像素坐标）
    private float scopeCenterY = 540f;

    /**
     * CustomHud 渲染每个元素前调用，更新 HUD 渲染参数。
     * 调用时机：renderOrthogonalInScope / renderPerspectiveInScopeBatch 的循环体内，
     * renderContent() 之前。
     */
    public void prepareFrame(float zoom, float vFov, int scrW, int scrH, boolean persp,
                             Vector3f scale, Vector3f offset,
                             float scopeCX, float scopeCY) {
        this.currentZoom = zoom;
        this.vFovDeg = vFov;
        this.screenWidth = scrW;
        this.screenHeight = scrH;
        this.isPerspective = persp;
        this.modelScaleX = scale.x;
        this.modelScaleY = scale.y;
        this.modelScaleZ = scale.z;
        this.hudOffsetX = offset.x;
        this.hudOffsetY = offset.y;
        this.hudOffsetZ = offset.z;
        this.scopeCenterX = scopeCX;
        this.scopeCenterY = scopeCY;
    }
```

### 3.1 查询方法定义

| MoLang 表达式 | 返回值 | 说明 |
|---|---|---|
| `hud.current_zoom` | float | 当前变焦倍率（1.0=基准） |
| `hud.fov` | float | 当前垂直视场角（度） |
| `hud.screen_width` | int | 屏幕宽度（像素） |
| `hud.screen_height` | int | 屏幕高度（像素） |
| `hud.is_perspective` | 0/1 | 是否透视投影 |
| `hud.model_scale_x` | float | HudAttr.scale.x |
| `hud.model_scale_y` | float | HudAttr.scale.y |
| `hud.model_scale_z` | float | HudAttr.scale.z |
| `hud.offset_x` | float | HudAttr.offset.x |
| `hud.offset_y` | float | HudAttr.offset.y |
| `hud.offset_z` | float | HudAttr.offset.z（分划板到后镜的视觉距离） |
| `hud.scope_center_x` | float | 炮镜投影中心 X（屏幕坐标） |
| `hud.scope_center_y` | float | 炮镜投影中心 Y（屏幕坐标） |
| `hud.focal_length_px` | float | 焦距像素值：`screenH/2 / tan(vFov/2)` |

`hud.focal_length_px` 是计算属性（每次从当前 `screenHeight`/`vFov` 算），其他直接返回字段。

---

## 四、`scope.*` 命名空间 — 炮镜分划数据

### 4.1 弹道数据模型

分划弹道数据为一组 `{距离, holdOverAngle}` 对，按弹药类型缓存。

```java
/**
 * 弹道分划数据缓存。从 BallisticsFramework 预计算，运行时查表。
 * <p>
 * 按弹药类型（ResourceLocation）索引，每组为距离→hold-over 角度（弧度）的数组。
 * 距离步长固定 100m，覆盖归零距离~2000m。
 */
public class BallisticHoldCache {
    /** 弹药 → (距离索引 → hold-over角弧度) */
    private final Map<ResourceLocation, float[]> holdMap = new ConcurrentHashMap<>();

    /**
     * @param ammo       弹药 ResourceLocation
     * @param distanceMeters 目标距离（米）
     * @return hold-over 角（弧度），无数据返回 0
     */
    public float getHoldAngle(ResourceLocation ammo, float distanceMeters) {
        float[] holds = holdMap.get(ammo);
        if (holds == null) return 0f;
        int idx = Math.round(distanceMeters / 100f);
        if (idx < 0 || idx >= holds.length) return 0f;
        return holds[idx];
    }

    /** 加载/重载时从 BallisticsFramework 预计算全部已注册弹药的分划表 */
    public void reload() { ... }
}
```

### 4.2 查询方法定义

```java
// ===== scope.* 在 HudMolangContext 中 =====

/** scope.bdc_hold_rad(distance) → 该距离的 hold-over 角（弧度） */
@QueryBinding(value = "bdc_hold_rad", namespace = "scope")
public double scopeBdcHoldRad(double distanceMeters) {
    if (holdCache == null || activeAmmo == null) return 0.0;
    return holdCache.getHoldAngle(activeAmmo, (float) distanceMeters);
}

/** scope.bdc_mark_y(distance) → 可直接用作骨骼 Y 位移的模型空间偏移量。
 *  自动区分正交/透视：正交用 focal_length 手动换算，透视用 offset_z 配合 GPU 投影矩阵。 */
@QueryBinding(value = "bdc_mark_y", namespace = "scope")
public double scopeBdcMarkY(double distanceMeters) {
    double angle = scopeBdcHoldRad(distanceMeters);
    double tanA = Math.tan(angle);
    if (isPerspective) {
        // 透视：GPU setPerspective 处理投影，元素距摄像机 hudOffsetZ
        // 角偏移 → 相机局部空间位移 = tan(θ) × 距离
        return tanA * Math.abs(hudOffsetZ) / modelScaleY;
    } else {
        // 正交：手动做透视投影换算
        // 屏幕像素偏移 = tan(θ) × focal_length
        // 模型空间单位 = 屏幕像素 / (zoom × modelScale)
        double focal = (screenHeight / 2.0) / Math.tan(Math.toRadians(vFovDeg / 2.0));
        return tanA * focal / (currentZoom * modelScaleY);
    }
}
```

| MoLang 表达式 | 返回值 | 说明 |
|---|---|---|
| `scope.bdc_hold_rad(200)` | float | 200m 处的 hold-over 角（弧度） |
| `scope.bdc_mark_y(200)` | float | 200m 刻度线的骨骼 Y 位移（模型空间） |
| `scope.active_ammo` | string | 当前弹药 ResourceLocation（可选） |

---

## 五、CustomHud 集成

### 5.1 初始化（首帧）

CustomHud 持有一个 `HudMolangContext` 单例。在 `syncHuds()` 为新创建的 `GuiAnimatable` 设置：

```java
// CustomHud.java 字段
private final HudMolangContext hudMolangContext = new HudMolangContext();
private final BallisticHoldCache ballisticHoldCache = new BallisticHoldCache();

// syncHuds() 中新 HUD 创建后：
if (hud.getHudContext() == null) {
    hud.setHudContext(hudMolangContext);
}
```

### 5.2 每帧 prepareFrame()

在 `renderOrthogonalInScope` 和 `renderPerspectiveInScopeBatch` 中，渲染每个元素前调用：

```java
// renderOrthogonalInScope() 中，renderContent() 之前：
hudMolangContext.prepareFrame(
    currentZoom, vFov, screenW, screenH, false /* 正交 */,
    params.getScale(partialTick),
    params.getOffset(partialTick),
    scCenterX, scCenterY
);
// 有活跃火炮时刷新弹道查表
CameraSubsystem activeCam = CameraController.getActiveCamera();
if (activeCam instanceof SightSubsystem sight) {
    hudMolangContext.setActiveAmmo(sight.getLoadedAmmunition());
}
hud.renderContent(poseStack, bufferSource, partialTick);
```

### 5.3 BallisticsFramework 预计算

在内容包加载完成后（或在首次渲染前懒加载），对每种弹药预计算分划表：

```
输入：弹药初速 V₀、弹道系数 BC、归零距离 Z、空气密度 ρ
对每个目标距离 D = 100, 200, 300, ..., 2000:
  二分搜索仰角 e 使弹道在水平距离≈D 处高度=0
  hold(D) = e - zeroAngle
  存入 holds[D/100]
```

---

## 六、美术侧用法示例

### 6.1 分划刻度骨骼动画

```json
{
  "BDC_2": {
    "position": { "0.0": [0, "scope.bdc_mark_y(200)", 0] }
  },
  "BDC_4": {
    "position": { "0.0": [0, "scope.bdc_mark_y(400)", 0] }
  },
  "BDC_6": {
    "position": { "0.0": [0, "scope.bdc_mark_y(600)", 0] }
  },
  "BDC_8": {
    "position": { "0.0": [0, "scope.bdc_mark_y(800)", 0] }
  }
}
```

### 6.2 数字标签反向缩放

标签骨骼不随变焦放大（`ignore_zoom` 的 MoLang 替代方案）：

```json
{
  "BDC_4_label": {
    "scale": {
      "0.0": [
        "1 / hud.current_zoom",
        "1 / hud.current_zoom",
        "1 / hud.current_zoom"
      ]
    },
    "position": { "0.0": [0, "scope.bdc_mark_y(400)", 0] }
  }
}
```

### 6.3 自适应遮罩

利用 `hud.*` 参数做圆形遮罩裁剪：

```json
{
  "scope_mask": {
    "scale": {
      "0.0": [
        "hud.screen_height / hud.model_scale_y * 0.015",
        "hud.screen_height / hud.model_scale_y * 0.015",
        1
      ]
    }
  }
}
```

---

## 七、涉及文件与改动量

| 文件 | 改动 | 说明 |
|------|------|------|
| `molang/HudMolangContext.java` | 新建，~80行 | HUD 专用上下文，hud.* + scope.* |
| `molang/BallisticHoldCache.java` | 新建，~60行 | 弹道分划预计算与查表 |
| `GuiAnimatable.java` | +1字段 +3行 | hudContext 字段与 getMolangContext 分支 |
| `CustomHud.java` | +~10行 | 初始化 hudContext、每帧 prepareFrame |

总新增 ~150 行，改动 ~15 行。

---

## 八、与现有系统的关系

| 系统 | 关系 |
|------|------|
| `MechMolangContext` | 继承，自动获得 local.* / global.* 查询 |
| `GuiAnimatable` | 通过 hudContext 字段注入，不修改现有渲染逻辑 |
| `CustomHud` | prepareFrame 在正交/透视渲染循环中调用 |
| `SightHud` | 不变，引擎级元素不涉及 MoLang |
| `BallisticsFramework` | 预计算时调用弹道求解 API，不侵入 |
| 内容包 JSON | 无影响，现有 HudAttr CODEC 不变 |
