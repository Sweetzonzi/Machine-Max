# client/render/post/ — 屏幕空间后处理

**范围**: `PostChain` 后处理效果及其共享的世界深度通路（爆炸波前折射、通用屏幕空间光照、过载黑视、失色、CRT 显像管）。屏幕空间光照按"来源 → 光源表 → 效果"三层拆开，当前来源是投射物曳光与爆炸火光。

**文件数**: 15 个 | 本包的坑集中在"深度什么时候还有效"与"GL 状态谁负责还原"两件事上，两者出错都不报错、只表现为画面不对。

## 结构

| 文件 | 作用 |
|------|------|
| `PostProcessingManager.java` | 唯一的事件订阅点与次序所有者：`AFTER_LEVEL` 内先取两份世界深度快照、再提交 **屏幕空间光照**；`RenderLevelLastEvent` 内 **折射 → 过载 → 失色**；GUI 阶段 CRT。各效果的懒加载与释放也在这里统一转发，光照的来源列表也在这里构造 |
| `BlastDistortionEffect.java` | 爆炸波前折射；持有链、数组 uniform 引用与世界深度快照（`captureWorldDepth`） |
| `ScreenSpaceLightEffect.java` | 通用光照效果：总开关与总倍率、按来源列表采集、持有链、数组 uniform 引用与世界深度快照（`captureWorldDepth`，在 `render` 内、pass 之前），并在加载期核验数组长度与 JSON `count` 一致（`verifyArrayCounts`） |
| `ScreenSpaceLightSource.java` | 来源接口：稳定的来源 ID、显式优先级、`collect(frame, out)` |
| `ProjectileTracerLightSource.java` | 曳光来源：门禁、按弹种口径折算的照射半径与遮挡容差、剩余寿命淡出；三条曳光特有剔除留在本类 |
| `ExplosionLightSource.java` | 火光来源：按游戏时钟求年龄包络、火光类别倍率、`NONE` 遮挡策略；几何剔除交给光源表 |
| `ExplosionLightState.java` | 客户端火光状态：按维度登记，每条按自己的 `duration_ticks` 独立结束，退出世界/换维度时清理；`onDetonate` 是绑定给 `ExplosionManager#setLightSink` 的入口 |
| `ScreenSpaceLightBuffer.java` | 光源表：数值校验、影响体剔除（距离/近裁面/视锥）、`(优先级升序, 投影半径降序)` 容量选择、扁平数组与逐来源诊断计数；不含 GL 调用 |
| `ScreenSpaceLight.java` / `ScreenSpaceLightPriority.java` / `LightFrameContext.java` | 光源提交载体（`setPoint` / `setSpot` 一次写齐）、优先级常量、每帧只读的帧上下文 |
| `OverloadVisionEffect.java` / `DesaturateEffect.java` / `CrtMonitorEffect.java` | 全屏颜色滤镜，不参与深度通路 |
| `BlastProjectionUtil.java` | 共享数学：世界坐标→视空间、像素焦距、深度两点标定 |

深度副本的另一份既有写法在 `client/fbo/OffscreenFbo.java`（`copyMainDepth`），与本包同源。

## 屏幕空间光照：来源 → 光源表 → 效果

- **来源**只提供身份、优先级与采集函数：采集不碰 GL，不保存借用的上下文，逐盏复用同一个 `ScreenSpaceLight` 载体（每盏灯新建记录会让采集分配随光源数增长）。
- **光源表**是唯一做校验与选择的地方：排序键 `(来源优先级升序, 投影半径降序)`，同键先到先留；优先级不是保留名额，高优先级来源占满容量时低优先级来源会被整类排除。当前容量 16，与程序 JSON 的 `LightView[64]` / `LightColor[64]` / `LightTol[16]` / `LightOcclusion[16]` 对齐。
- **影响体剔除按光源的影响球判定**：`distanceToCenter > renderDistance + radius`、整个球位于近裁面之后、球与视锥不相交，三条之一成立才剔除。光心在相机背后或投影离屏都不是剔除理由——爆炸火光经常正好落在这些位置。视锥侧平面取自投影矩阵的 `m00` / `m11`，跟随 FOV 与视口比例。
- **遮挡策略逐盏上传**：`LightOcclusion` 编 0（不做判定）或 1（S2），片元据此决定是否在光心投影处采一次深度。曳光用 S2，爆炸火光用 `NONE`（爆心可能不在视野内、也可能被自己掀起的粒子挡住，用光心深度会整盏误杀）。同一帧里两种策略可以共存。
- **阶段能力在提交处显式报告**：锥光（`Shape.SPOT`）与 S3（`OcclusionMode.SCREEN_RAY`）当前返回 `UNSUPPORTED`，不会按球光或按 S2 悄悄渲染；接入对应 GPU 编码后才放开那条判断。
- **来源特有剔除留在来源里**：光心在相机背后、超出渲染距离、屏幕投影半径小于下限三条留在 `ProjectileTracerLightSource`。光源表的影响体剔除只会移除整球都不可能贡献的条目，因此它腾出的槽位可能让同一帧里排序更后的低优先级光源入选。
- **诊断**按来源计提交/非法/几何剔除/候选/入选/容量拒绝/被置换；容量触顶日志限频 5 秒（短暂不触顶不重置限频），非法数据日志与容量日志分开。
- **瞬态光源的寿命不依赖是否入选**：火光年龄由关卡游戏时钟推导（暂停即冻结），关效果、容量拒绝、换维度都不影响它推进；总开关关闭时 `ScreenSpaceLightEffect` 连采集都不做，年龄仍照常。
- **新增一类光源** = 新增一个 `ScreenSpaceLightSource` 实现并加进 `PostProcessingManager` 的来源列表；优先级、容量与数组契约都不必改（只有改容量才要同步程序 JSON、Java 常量与 GLSL 数组长度）。

## 四条硬约束

1. **每帧的世界深度快照取在 `RenderLevelStageEvent` 的 `AFTER_LEVEL`。** 更晚就没有世界几何了（见下）。
2. **所有需要深度的 pass 都从快照采样**，都不声明 `minecraft:main:depth`。
3. **快照目标的深度格式必须与主目标逐位相同**（本仓库基线为 `GL_DEPTH32F_STENCIL8`，带 stencil）。深度位 blit 在两侧格式不一致时按规范非法。
4. **一帧内所有快照必须在第一个以 `minecraft:main` 为输出目标的 pass 之前取完。** 屏幕空间光照的 pass 就挂在 `AFTER_LEVEL`、正是一个这样的 pass，所以 `PostProcessingManager` 里两个 `capture*` 都排在它的 `render()` 之前——顺序反了，后取的那份快照拿到的是被 pass 清空过的深度（常量，~0.12 m），判据会全像素命中。当前副本所有权是"每条链各声明并拷贝一份"（两份取的是同一时刻的同一份深度），因此别把两条链的取快照时机拆到不同的事件里。

## 为什么：主目标的深度什么时候还有效

- `GameRenderer.renderLevel` 在手部渲染之前执行一次 `RenderSystem.clear(GL_DEPTH_BUFFER_BIT)`，它是否真的抹平深度取决于**执行那一刻**的深度写掩码：
  - 无光影会话：清除那一刻掩码为真（实测 `AFTER_LEVEL` 与之后为真，同一帧更早的 `AFTER_WEATHER` 时刻读到的却是假——掩码在帧内会变）→ 主目标深度整体变成远平面；
  - Iris 会话：掩码为假（实测四个时刻均为假）→ 清除是空操作，深度原样留下。

  于是 `RenderLevelLastEvent` 时刻的 `minecraft:main:depth` 在无光影下不含世界几何、在光影下却完好——**同一份代码的表现随光影开关变化**，不可依赖。
- 任何以 `minecraft:main` 为输出目标的 pass（本项目每条链末尾的 `blit → main`）在绘制前会清空自己的输出目标，把主目标深度写成常量（本项目标定下约 0.12 m）。因此**排在它后面的深度读取会全像素判为"被完全遮挡"**：屏幕空间光照链的 pass 就挂在 `AFTER_LEVEL`，若把折射链的快照取在它之后，爆炸环会整屏消失。
- `AFTER_LEVEL` 取的快照与那一刻的世界深度**逐位相同**（实测 0 / 409920 像素不同），既不受手部清除影响，也不含手部与 HUD。
- **屏幕空间光照的 pass 排在手部渲染之前**，因此它那次 `blit → main` 会先一步清空主目标深度：无光影会话本来就在此处有一次生效的清除（行为不变），光影会话里手部则从"与遮挡它的世界几何做深度测试"变成"总画在最前"——只在手被更近的几何挡住时可辨，已知且接受。设计文档把它记为 §13 风险 13。

设计依据、两次采集的对照数据与验收判据见 `docs/plan/计划中/武器系统-投射物曳光光照设计.md` 的 §5.1、§5.2、§6.7 与 §七。

## 会静默出错的五个点

| 症状 | 原因 | 处置 |
|------|------|------|
| 光斑/环整体**上下镜像**到视线轴另一侧（近轴时几乎看不出，接地时光斑跑到天上） | 把屏幕 y 当成了自上而下。后处理全屏 quad 的顶点是 `(0,0),(W,0),(W,H),(0,H)`，正交矩阵是 `setOrtho(0, W, 0, H, ...)`，所以 `Position.y = 0` 在屏幕**下沿**，`px.y = texCoord.y · OutSize.y` 自下而上 | 视线方向写成 `q.y = (px.y - H/2) / f`，世界点投影写成 `c.y = H/2 + f·C.y/zc`；两者必须同号，否则先是画错、再是"天空早退"把该亮的像素全跳过 |
| `copyDepthFrom` 抛 `GL_INVALID_OPERATION`，副本里只剩清屏值 | 副本深度是 `GL_DEPTH_COMPONENT`，主目标是深度-模板格式，深度位 blit 非法 | 用链 JSON 声明的目标：`PostChain.addTempTarget` 会镜像主目标的 stencil；手工 `new TextureTarget(...)` 必须自己 `enableStencil()` |
| 该调用之后的渲染错位、清除打到窗口上 | `RenderTarget.copyDepthFrom`、`RenderTarget.clear` / `resize`、`PostChain` 构造结束时都会把 FBO 0 绑在 DRAW 上 | 每处之后 `main.bindWrite(true)` |
| 该清的没清，或深度被意外抹平 | `glClear` 受 `GL_DEPTH_WRITEMASK` 与剪裁框约束，与"上一次是谁清的"无关 | 需要清就临时 `_depthMask(true)`，随后还原原值 |
| 深度采样在轮廓处出现一圈毛边 | 误以为 `use_linear_filter` 也作用于深度 | 它只改颜色纹理的过滤方式；深度恒为 NEAREST，无需处理 |

## uniform 与目标的三条通路

| 量 | 路径 | 关键点 |
|----|------|--------|
| 标量 | `PostChain.setUniform(String, float)` | 只写第 0 个分量：**不要打包 `vec2` / `vec3` / 小数组**，其余分量会取到 JSON 默认值里的垃圾；**int uniform 走这条路会因 `floatValues` 为 null 而 NPE**，计数量必须是 float |
| 数组 | `PostChainAccessor` 取 `PostPass` → `Uniform.set(float[])` | JSON 默认值只有前若干个分量有效、其余是未初始化内存 → 每帧全量写；`Uniform` 引用在链加载时取一次。**数组长度必须 ≥ JSON 声明的 `count`**：不足时 `Uniform.set` 只打一条 WARN 并整段忽略（静默失效，不崩）。通用光照链在加载期用 `Uniform#getCount()` 与 Java 数组长度对比（`ScreenSpaceLightEffect#verifyArrayCounts`），不一致时直接拒绝本链生效 |
| 链内目标 | `PostChain.getTempTarget(name)` | 名字与链 JSON 的 `targets` 一致；只有声明尺寸等于屏幕尺寸的目标会跟随窗口 `resize`，显式声明其它尺寸的目标不会 |

链 JSON 的三个加载期约束：`targets` 是**数组**（字符串项按屏幕尺寸建，对象项可给 `width` / `height`）；`auxtargets` 的 `id` 在**加载时**解析，引用未声明的目标会让整条链加载失败；每条链的目标表彼此独立——跨链共用一份副本要么注入目标表，要么把两个效果并入同一条链。

## 诊断配方

改深度相关代码前先量，别靠肉眼：

1. **四时刻回读**：在 `AFTER_WEATHER`、`AFTER_LEVEL`、`RenderLevelLastEvent` 的高/低优先级各回读一次主目标深度（绑目标 FBO 后 `glReadPixels(GL_DEPTH_COMPONENT, GL_FLOAT)`），逐像素比较数组并统计远平面占比。"判据失效"与"深度被抹平"在这一张表里立刻分开。
2. **判据复算**：把着色器的遮挡判据在 CPU 侧按同一公式复算，输出"被遮挡 / 正常 / 无几何"的判定图，与截图对照。shader 输入与复算不一致、或复算与画面不一致，都能定位。
3. **诊断代码必须零副作用**：结束时还原主目标绑定与深度写掩码；一次按键只采一帧（缺收尾标志会每帧截图跑飞，几十秒能写掉几百 MB）。探针留下的状态偏差会改变被观察的现象本身——本项目出现过一次：探针把 FBO 0 留在 DRAW 上，那次清除因此打到窗口而不是主目标，缺陷看起来"自己好了"。
4. **观感不是验证**：折射的画面色偏与深度无关——它直读主目标深度的那段时间里，遮挡判据一次都没成立过，画面却一直"看起来能用"。任何深度相关改动都要给出数字对照（例如遮挡像素占比 0.0% → 68.0%）。

## 构建与验证

- 有 JVM 占用 neoforge 的 artifact jar（开发客户端、另一个 Gradle 构建）时，`createMinecraftArtifacts` 删不掉它、报 `Unable to delete file`。`-x createMinecraftArtifacts` 只排除根项目的同名任务，复合构建里 `:Spark-Core` 与 `:BallisticsFramework` 的仍会执行，因此要逐个限定：`gradlew compileJava -x createMinecraftArtifacts -x :Spark-Core:createMinecraftArtifacts -x :BallisticsFramework:createMinecraftArtifacts`。
- 改链 JSON 或着色器后要让链**重新加载**：链在进入世界时懒加载一次、退出世界才释放，资源重载不会重建它。
- 改**程序** JSON（`shaders/program/*.json`）里的数组声明后必须重启客户端：数组 `count` 在链加载期与 Java 数组长度比对，不一致会直接拒绝本链生效（日志里有一条 `count 与 Java 数组长度不一致`）；只改数值不重启不算生效。
- 火光参数走内容包与网络：`common/mech/explosion/ExplosionParams` 的线格式变化要求对端同版本（registrar `sync:2.0.0`），因此改这些字段时客户端与服务端必须同时更新。
- **光影会话与无光影会话的渲染状态不同**（深度写掩码），深度相关改动两个都要测；`run/config/iris.properties` 会滞后于游戏内开关，判断当前状态要读运行期值。

## 反模式

- 在 `RenderLevelLastEvent` 直读 `minecraft:main:depth`。
- 把任一 `capture*`（取深度快照）排在屏幕空间光照 `render()` 之后——那一帧的快照已经是 pass 清过的深度。
- 拿某一时刻读到的 `depthMask` 推断另一时刻的清除是否生效。
- 用与主目标格式不一致的目标接深度 blit。
- 在诊断代码里不做状态还原，或让它每帧重复执行。
- 把 int 或打包向量写进 `chain.setUniform`。
- 在本包文档与注释里写死行号（会随改动失准）——引用符号名。
- 在 CPU 侧回读深度来做逐光源遮挡剔除——那是一次 GPU→CPU 同步；S2 的片元形态把成本摊进已有的一次全屏 pass。

## 参考文件

- `docs/plan/进行中/渲染-屏幕空间光照通用化设计.md` — 通用光照通路的设计：提交契约、容量与优先级、诊断、爆炸火光与车灯的分期（P1 与 P2 已落地，P3–P5 未实现）
- `docs/plan/计划中/武器系统-投射物曳光光照设计.md` — 曳光光照的完整设计（S1/S2 落地形态与实测待办见 §十五）、折射的深度快照、链次序不变量与 F1 的落地/实测记录（§5.1、§5.2、§6.7、§七）
- `docs/plan/已实现/武器系统-爆炸波前视觉设计.md` — 屏幕空间折射的完整设计（几何、剖面、色散、深度的获取与比对）
- `client/fbo/OffscreenFbo.java` — 深度副本与离屏目标的既有写法
