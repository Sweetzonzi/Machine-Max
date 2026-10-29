# 主武器管轴 HUD

`client/render/gui/hud/SightHud.java#render` 在同一个 `sight_hud` 层绘制两类圆环：操作者的期望目标使用大环，当前主武器每个有效发射器的实际管轴使用小环。实体相机、固定炮镜和座椅第三人称观察均适用；普通座椅第一人称采用原版界面。隐藏 GUI、离座、座椅停用或宿主零件离场时不绘制。

本文的 Java 源码短路径均相对 `src/main/java/io/github/sweetzonzi/machine_max/`。

## 武器范围

`common/mech/subsystem/AbstractControllableSubsystem.java#getActiveMainWeaponControllers` 从基础组和当前子组的握手发现结果取得控制器，再按 `common/mech/control/ControlGroupSet.java#getMergedMainWeaponTargets` 的主武器输出频道匹配控制器的开火输入频道。结果排除停用、损毁及宿主离场的控制器，同一控制器只返回一次。`getActiveMainWeaponController` 为炮镜机构选择提供集合中的第一项。

`client/render/gui/hud/SightHud.java#renderWeaponRings` 遍历各控制器的 `common/mech/subsystem/WeaponControllerSubsystem.java#launchers`，按发射器实例去重，排除停用、损毁及宿主离场的发射器。一个发射器对应其动态属性指定的一个枪口 locator；空膛和装填中也可显示管轴。不同发射器朝向相同时，圆环自然重叠。

## 朝向与投影

每帧取渲染相机的 partialTick，调用 `common/mech/vehicle/SubPart.java#getLerpedLocatorWorldTransform` 采样发射点，再将旋转转换成 JOML 并旋转局部 −Z 轴。采样读取已发布的刚体状态，与相机使用同一插值时刻。

`util/ScreenProjectionUtil.java#worldDirectionToScreenOffset` 直接投影世界方向，采用当前相机朝向、`client/input/CameraController.java#currentFov` 和 GUI 尺寸。相机与枪口的位置差不参与计算，所以第三人称距离和平移不会制造管轴位置视差。方向在相机后方、数值无效、投影超出屏幕边界时跳过该环。

小环表达几何管轴，火控修正、散布、重力下坠和平台速度均不参与计算。它不表示预计命中点。期望目标由 `client/render/gui/hud/SightHud.java#renderTargetRing` 独立绘制：期望目标离屏或缺失时，视野内的管轴小环可以显示。

## 外观

两类圆环使用 `src/main/resources/assets/machine_max/textures/gui/aim_ring.png` 及其 `.mcmeta` 过滤设置。大环以 16×16 GUI 像素绘制，可见外半径 6；小环以 8×8 GUI 像素绘制，可见外半径 3。浮点平移保留亚像素位置，绘制尺寸不随炮镜倍率缩放，方向偏移随当前视场角投影。

## 验证

2026-10-29 的验证结果：

- JUnit：313 条通过，包括 `src/test/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/ScreenProjectionUtilTest.java` 的三条投影测试，覆盖相机平移、大世界坐标、方向长度、相机旋转与横滚、变焦、后方及无效方向。报告在 `build/barrel-direction-tests/html/index.html`，日志为 `build/barrel-direction-junit-validation.log`。
- `checkSchemas` 通过。
- GameTest：27 条中 26 条通过。`common/gametest/ViewpointGameTest.java#everyActiveMainWeaponIsSelectedOnce` 覆盖多主 WC、基础组与子组共用实例、切组、副武器和停用控制器筛除，以及没有主武器输出的空集合。
- GameTest 的失败项为 `common/gametest/ViewpointGameTest.java#deviceTypesAndOpticalSources`：该方法要求 2S38 有一台固定炮镜；`src/main/resources/spark_modules/Machine-Max_2S38/machine_max/parts/2s38_turret.json` 将炮手镜和车长镜都配置为 `machine_max:camera`。本功能的控制器枚举用例通过，完整 GameTest 任务以失败退出。日志为 `build/barrel-direction-validation.log`。

客户端实机画面尚待验收：检查炮塔追赶时大小环的相对运动，多武器与多管的数量，切组与上下车的显隐，期望目标离屏时的小环，以及不同 GUI scale 和炮镜倍率下的可辨识度。
