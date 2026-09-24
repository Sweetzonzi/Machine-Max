---
name: aui-custom-element
description: 向 ApricityUI 注册自定义 HTML 标签，并在页面里渲染游戏内内容（含 3D 模型）。当需要新增 AUI 元素、把部件或游戏资产画进网页、或排查自定义标签不生效时使用。不用于纯 CSS 样式调整。
---

# AUI 自定义元素

把游戏内的东西画进 AUI 页面，做法是给页面注册一个新标签，由一个 Java 类负责这个标签区域怎么画。

## 版本与现状

依赖版本看 `gradle.properties` 的 `aui_version`（当前 1.2.5），AUI 源码在同级 `../AUI`。

**1.2.5 的现状：注解注册对本模组无效。** AUI 作者的意图是 `@ElementRegister`，但实现顺序让依赖方模组用不上它，详见「为什么注解不生效」。因此本模组统一走 `Element.register`。

## 注册方式

在客户端入口的模组构造函数里直接登记：

```java
Element.register("MACHINEMAX-XXX", (document, tagName) -> new XxxElement(document));
```

- 登记位置：`client/MachineMaxClient.java` 的构造函数
- 时机：必须早于任何 `Document` 的创建。模组构造期满足这个条件；查表只发生在解析页面时，登记晚了就没救
- 标签名带 `MACHINEMAX-` 前缀，避免与其他模组撞车（同标签后登记者覆盖前者）

参考实现：

- 元素类 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/element/PartModelElement.java`
- 登记调用 `MachineMaxClient.registerAuiElements()`
- 配套测试页 `src/main/resources/assets/apricityui/apricity/machine_max/model_test.html`

## 为什么注解不生效

`@ElementRegister` 的扫描在 AUI 自己的模组构造期一次完成，而这个扫描范围是一个**收窄过滤器**：

1. AUI 构造器先调 `ApricityUIRegistry.scanPackages("com.sighs.apricityui.element")`，紧接着才调 `register()` 执行那次扫描；
2. 过滤器非空时，`ReflectionUtils.isPackageAllowed` 只放行集合内的包，本项目位于 `io.github.sweetzonzi...`，在扫描时被直接跳过；
3. `register()` 全工程只调用一次，没有后续补扫；
4. 本模组在 `neoforge.mods.toml` 里声明了 `ordering="AFTER"`，构造函数必然在 AUI 之后执行，来不及插队。

旁证：AUI 自己的 `Item` 元素除了标 `@ElementRegister`，还额外写了 `static { Element.register(...) }` 兜底。

**想继续用注解的话**，需要自己补一步：`scanPackage(你的包)` 之后再调一次 `ApricityUIRegistry.register()` 触发重扫（该方法是 public）。重扫会连带重注册 AUI 自己的元素，同标签覆盖为等价工厂，无副作用。

两点如实说明：这条路径是读代码推出的，**尚未实机验证**；另外注解注册会把每个实例都塞进 `ApricityUIRegistry.ELEMENTS` 这个静态表，且该表从不清理，长期运行会累积引用，直接 `Element.register` 没有这个问题。

`scanPackage` 的语义是「收窄过滤范围」而非「注册」，且与 KubeJS 的 `@KJSBindings` 扫描共用同一份全局集合，调用它会同时影响绑定扫描的范围。

**升级 AUI 后复核**：读 `../AUI/targets/neoforge-1.21.1/.../ApricityUINeoForge.java` 构造器中 `scanPackages` 与 `register()` 的先后顺序，以及 `ReflectionUtils.isPackageAllowed` 对空集合的处理。若作者改为延迟扫描或不再收窄范围，就可以回到注解写法。

## 标签名规则

大小写不敏感，写小写即可：

| 环节 | 行为 |
| --- | --- |
| 解析取标签名 | 正则 `^([\w-]+)`，允许连字符 |
| 创建元素 | `Element` 构造函数内 `toUpperCase` |
| 登记 | `Element.register` 存入时 `toUpperCase` |
| CSS 标签选择器 | `equalsIgnoreCase` |

## 绘制上下文与相位

`drawPhase(PoseStack, Base.RenderPhase)` 按 `SHADOW` / `BODY` / `BORDER` 三个相位分别调用。注意存在 `overflow` 裁剪时 `BORDER` 会先于 `BODY`，不要假设固定顺序。

这个钩子运行在 Minecraft 的 GUI 渲染管线里：

- 传进来的 `PoseStack` 就是 `GuiGraphics.pose()`，同一个对象
- 顶点消费者直接取 `Minecraft.getInstance().renderBuffers().bufferSource()`
- 需要 `GuiGraphics` 时就地构造 `new GuiGraphics(mc, bufferSource)` 再拷贝当前 pose（AUI 的 `ItemRenderService` 是这么做的）
- 框架已在调用前应用好元素变换，并在每个绘制节点前后自带 save/restore；自己仍要把 push/pop 配平

元素的阴影、背景、边框默认由 `Rect.of(this)` 绘制（`drawShadow` / `drawBody` / `drawBorder`），自绘内容通常接在 `BODY` 相位 `drawBody` 之后。

## 四条硬约束

1. **异常必须自己兜住**。AUI 不捕获 `onInitFromDom` 与 `drawPhase` 里的异常，抛出会中断整篇文档的绘制——现象是「整个页面都不渲染」，极易误判成标签没被解析。两处都要包 `try/catch`，并只记一次日志以免刷屏。
2. **两个轴最终都要有确定尺寸，但不必写死像素**。宽度按普通块级规则解析，`auto` 就撑满父容器内容区（自定义元素不在 AUI 的固有尺寸白名单里，不会被当作替换元素取 0 宽）；高度由内容驱动，没有子节点时算出来是 0，需要一个确定来源——`aspect-ratio`、`height: 100%`、flex 拉伸、绝对定位 `inset` 都可以，其中百分比与拉伸要求父级该轴本身确定。元素内容区尺寸在每次绘制时读取，所以由布局推导出的数值能跟随窗口变化，不必硬编码。
3. **自绘 3D 前后处理批处理**。画之前用 `Base.commitLocalDraws()` 冲刷 AUI 延迟累积的几何与贴图批次，画完 `bufferSource.endBatch()`，否则两者错序。
4. **注意深度**。元素背景板先于自绘内容绘制并写入深度，带 Z 跨度的 3D 内容会被深度测试剔掉（表现就是「被方框背景裁掉」）。解法是沿 Z 整体前移，前移量取「包围盒半对角线 × scale + 余量」才能扛住任意视角旋转；AUI 给 GUI 物品模型预留的深度是 `GuiItemDepths.SCREEN_ITEM_MODEL_Z`（150），而 2D 图层的步进只有 `0.005`。

## 排查手册

| 症状 | 病因 |
| --- | --- |
| 连元素色块都没有，但同页普通 div 正常 | 标签未被解析成你的类：查 `Element.register` 是否执行、标签名是否一致 |
| 元素整体不可见，也没有报错 | 某一轴尺寸为 0：高度 `auto` 对空元素解析为 0，需要给一个确定的高度来源 |
| 有色块、无自绘内容 | 解析成功但绘制有问题：看 `drawPhase` 里的异常日志 |
| 页面整体空白、像没被解析 | 大概率是 `drawPhase` 或 `onInitFromDom` 抛异常拖垮了整篇文档 |
| 内容被背景裁掉一半 | 深度不足，沿 Z 前移 |
| 内容偏移不居中 | 模型中心不在原点：先把包围盒中心平移回原点再旋转缩放 |

调试手段：

- 在构造函数、`onInitFromDom`、`drawPhase` 各打一条日志，看哪一级没到——这是区分「没被解析」与「解析了没画出来」最快的办法
- 预览页面：按 `F10` 打开 AUI 内置资源管理器，在资源树里找到目标 html，双击开出可交互预览窗口；改完 HTML/CSS/JS 按 `END` 重载资源
- 帧耗时：`config/apricityui-client.toml` 里设 `[debug] frameTimingHud = true`，重点看 `ly`（全量布局提交次数，稳定页面应长期为 0）

## 渲染游戏内 3D 内容

Spark-Core 的模型渲染入口在 Java 侧叫 `ModelRenderHelperKt`（Kotlin 文件门面类，源码为 `ModelRenderHelper.kt`）。标准写法是逐子部件乘其渲染矩阵，再逐骨骼渲染：

```java
for (SubPartAnimatable subPart : partAnimatable.getSubParts().values()) {
    poseStack.pushPose();
    poseStack.mulPose(subPart.getRenderWorldPositionMatrix(1.0f));
    for (OBone bone : subPart.getBones().values()) {
        ModelRenderHelperKt.render(
                bone,
                subPart.getModelController().getModel().getPose(),
                poseStack,
                bufferSource.getBuffer(RenderType.entityCutout(
                        subPart.getModelController().getTextureLocation())),
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                0xFFFFFFFF, 1.0f, false);
    }
    poseStack.popPose();
}
```

`PartAnimatable` 可以脱离载具独立构造，只需要 `(Level, PartType, String variant)`，不需要 `Part` 实例，也不需要 `VehicleCore`。但**构造完必须补一次 `setTransform(new Transform())`**：`SubPartAnimatable.oldTransform` 的初值是 `null`，而 `getWorldPositionMatrix` 内部用到的 `Transform.lerp` 是非空扩展函数，会直接 NPE。`setTransform` 会同时写入 `transform` 与 `oldTransform`，并把其余子部件摆到相对位置。

坐标与自适应：

- 模型空间 Y 向上、GUI 空间 Y 向下，用 `scale(s, -s, s)` 翻转
- 旋转与缩放之前先把包围盒中心平移到原点，否则模型绕原点转，看着乱跑
- 缩放不要写成常数，按元素当前内容区尺寸每帧推导（`内容区短边 × fill / 模型最长方向`），这样布局变化会自动跟随

## 约定

- 自绘元素统一放 `client/render/gui/element/`
- 注册集中在 `MachineMaxClient`，便于一处总览所有标签
- 读属性用 `getAttribute`；构造函数里读不到初始属性，初始化逻辑放 `onInitFromDom`
- 不要在 `drawPhase` 里做重活（每帧建纹理、解析字符串、触发布局）
