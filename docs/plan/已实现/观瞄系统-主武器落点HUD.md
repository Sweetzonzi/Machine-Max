# 主武器落点 HUD

> **状态**：已实现，实机画面验收待完成。

`client/render/gui/hud/SightHud.java#render` 在同一个 `sight_hud` 层绘制两类圆环：操作者的期望目标使用大环，当前主武器每根有效发射管的落点使用小环。实体相机、固定炮镜和座椅第三人称观察均适用；普通座椅第一人称采用原版界面。隐藏 GUI、离座、座椅停用或宿主零件离场时不绘制。

本文的 Java 源码短路径均相对 `src/main/java/io/github/sweetzonzi/machine_max/`。

## 武器范围

`common/mech/subsystem/AbstractControllableSubsystem.java#getActiveMainWeaponControllers` 从基础组和当前子组的握手发现结果取得控制器，再按 `common/mech/control/ControlGroupSet.java#getMergedMainWeaponTargets` 的主武器输出频道匹配控制器的开火输入频道。结果排除停用、损毁及宿主离场的控制器，同一控制器只返回一次。`getActiveMainWeaponController` 为炮镜机构选择提供集合中的第一项。

`client/render/gui/hud/SightHud.java#renderWeaponRings` 遍历各控制器的 `common/mech/subsystem/WeaponControllerSubsystem.java#launchers`，按发射器实例去重，排除停用、损毁及宿主离场的发射器。一个发射器对应其动态属性指定的一个枪口 locator；空膛和装填中同样绘制。不同发射器落点相同时，圆环自然重叠。

## 落点与投影

小环表示「这一管此刻会打到什么」：沿发射点与管轴发出的射线在第一个不属于本装配体的表面上停下，圆环画在那个世界点上。

距离在客户端主线程的 `ClientTickEvent.Post` 里测一次。`client/render/gui/hud/SightHud.java#onClientTick` 遍历同一批发射器，逐根调用 `common/mech/subsystem/LauncherSubsystem.java#findAimHit`：以 `#getMuzzleWorldPosition` 与 `#getMuzzleDirection` 作为起点和方向，经 `common/mech/ray/RayQueryContext.java#withTerrain` 同时查询只读物理快照与 `Level.clip` 给出的方块地形，取最近一条命中。本装配体自己的几何被跳过——内容把发射点放进炮管或导弹管的命中箱内部时，下一格就是自己的盒壁。开火归属（谁打的这一发）是武器控制器的职责，射线上在飞的投射物不做归属过滤。

距离按 `fraction() × AimMath.AIM_DISTANCE_METERS` 折成米，与发射管实例一起写进一份整份替换的只读快照；渲染阶段只读这份快照，不在渲染线程发射线。没有任何命中时距离取 `AimMath.AIM_DISTANCE_METERS`。

每帧取渲染相机的 partialTick，调用 `common/mech/vehicle/SubPart.java#getLerpedLocatorWorldTransform` 采样发射点与朝向，按缓存距离在插值后的位姿上重建落点——圆环跟随炮口平滑运动，距离仍是本 tick 量出来的值。采样读取已发布的刚体状态，与相机使用同一插值时刻。

`util/ScreenProjectionUtil.java#worldToScreenOffset` 把该点投影到屏幕，采用当前相机位置与朝向、`client/input/CameraController.java#currentFov` 和 GUI 尺寸。投影的输入是世界点，第三人称相机与枪口之间的位置差因此作为真实视差进入结果：近处目标的圆环落在它自己的表面上。点落在相机后方、数值无效、投影超出屏幕边界时跳过该环。

小环表达几何管轴上的第一个表面：火控修正、散布、重力下坠和平台速度均不参与计算，它不是弹道落点预测。期望目标由 `client/render/gui/hud/SightHud.java#renderTargetRing` 独立绘制，其投影仍按期望点自身的位置处理；期望目标离屏或缺失时，视野内的落点小环可以显示。

物理快照滞后一次物理步，画面使用插值后的渲染位姿；载具快速横移时，圆环相对可见车身最多差一个物理步的位移。

## 外观

两类圆环使用 `src/main/resources/assets/machine_max/textures/gui/aim_ring.png` 及其 `.mcmeta` 过滤设置。大环以 16×16 GUI 像素绘制，可见外半径 6；小环以 8×8 GUI 像素绘制，可见外半径 3。浮点平移保留亚像素位置，绘制尺寸不随炮镜倍率缩放，点的屏幕位置随当前视场角投影。

## 验证

- 离线 `compileJava` 与 `test` 通过。
- 已知遗留：`common/gametest/ViewpointGameTest.java#deviceTypesAndOpticalSources` 要求 2S38 有一台固定炮镜，而 `src/main/resources/spark_modules/Machine-Max_2S38/machine_max/parts/2s38_turret.json` 把炮手镜和车长镜都配置成 `machine_max:camera`，该用例使 GameTest 任务失败；与本 HUD 无关。
- 客户端实机画面尚待验收：炮塔追赶时大小环的相对运动、多武器与多管的数量、切组与上下车的显隐、近处目标上圆环是否贴住它自己的表面、期望目标离屏时的小环，以及不同 GUI scale 和炮镜倍率下的可辨识度。
