# 履带包络线 · 纯 2D 验证原型

这是 `docs/plan/进行中/履带系统-物理实现设计文档.md` §8.3 的可执行参考实现与测试。
不依赖 Minecraft、Bullet、JOML 或任何第三方库，只使用 Canvas 2D 与原生 JavaScript。

原型分两条线：

- **凸包基线**：`track-envelope.js` / `index.html` / `detail.html`，验证圆盘采样凸包、支撑函数与 300 组随机圆；
- **有序非凸包络**：`track-path.js` / `non-convex.html`，按 `TrackLink.members` 的锚定顺序走闭合带路，
  凸角绕圆弧、凹角单点接触、端帽一律绕圆弧，包络始终贴住每一个成员轮。

## 文件

| 文件 | 作用 |
| --- | --- |
| `track-envelope.js` | 凸包基线：圆盘采样 → monotone chain → 弧长参数化 |
| `track-envelope-tests.js` | 凸包 9 场景、退化自检、300 组随机模糊 |
| `visualize.js` | 凸包页 Canvas 绘制 |
| `index.html` | 凸包场景矩阵，适合截图复核 |
| `detail.html` | 凸包细节 + 真实布局 + phase 滑块 |
| `track-path.js` | 有序非凸包络：锚定轮序 + 绕行符号 + 外公切线/圆弧/点接触 + 端帽 |
| `track-path-tests.js` | 非凸 7 场景、17 用例、退化自检 |
| `visualize-path.js` | 非凸页 Canvas 绘制 |
| `non-convex.html` | 抬起/降低负重轮对照 + 退化轮序端帽 |
| `screenshots/index.png` | 凸包总览（1800×2300） |
| `screenshots/detail.png` | 凸包细节（1500×1750） |
| `screenshots/non-convex.png` | 非凸对照（1620×3050） |

## 运行

```bash
# 凸包基线
node track-envelope-tests.js

# 有序非凸包络
node track-path-tests.js

# 浏览器打开
# index.html / detail.html / non-convex.html

# 无头 Edge 截图（Windows）
& "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe" `
  --headless=new --disable-gpu --hide-scrollbars `
  --window-size=1620,3050 `
  --screenshot=screenshots/non-convex.png `
  "file:///D:/Files/Project_MinecraftMods/Machine-Max/prototype/track-envelope-2d/non-convex.html"
```

## 凸包基线覆盖

| 层 | 内容 |
| --- | --- |
| 解析解 | 单圆 `2πr`；等半径双圆 `2πr + 2D`；不等半径双圆外公切线公式；共线三圆 `2πr + 2D`；包含圆只保留外圆 |
| 独立性质 | 凸性、逆时针、采样点包含、支撑函数上下界、每个支撑圆参与外轮廓 |
| 参数化 | `sampleAt(0) == sampleAt(L)`、负弧长回绕、按弧长等距、切线与法线单位正交、外法线朝外、点位于边界 |
| 推导 | `N = max(1, round(L / 0.2))`、`maxHp = 20 × N` |
| 随机模糊 | 300 组 1–8 个随机圆，检查凸性、包含、支撑函数上下界 |
| 负对照 | 圆心凸包在 `+Y` 方向支撑 0，圆盘包络 1.2 m |

结果：`9/9` 场景、`157/157` 断言、`300/300` 随机。

## 有序非凸覆盖

`track-path.js` 是 `BeltPath2D.java` 的逐条移植，两侧共用同一套规则：

| 规则 | 内容 |
| --- | --- |
| 锚定顺序 | 首个圆是主动轮；3 个及以上按输入顺序直接构建，输出方向跟随锚定顺序，不做输入反转 |
| 绕行符号 | 闭合边属于上行程不参与判断；其余边里取中点最低的一条，要求其外法线朝下 |
| 凸角 | 相邻轮外侧外公切线 + 圆弧；圆弧方向按“远离轮心集合质心”的那一侧选取 |
| 凹角 / 共线 | 单点接触，接触点在轮子外侧角平分线方向，接触长度为 0 |
| 端帽 | 下标 0、下标 n-1、或转角绝对值超过 3π/4 的顶点一律凸角 |
| 回程二次接触 | 闭合边穿过一个下行程轮时，把同一轮追加到序列末尾，下行程点接触 + 回程圆弧 |
| 割线校验 | 非相邻轮不能被直线段穿过；同一物理轮出现两次时按圆心+半径判等跳过 |
| 2 轮 | 外部凸包（stadium）、不等半径外公切线、互相包含退化为外圆整圆 |
| 退化回退 | 轮心共线抛 `DegenerateLayoutError`；`hasOverlappingWheels` 识别悬挂压缩造成的轮心重叠；两者由调用侧决定是否 `convexFallback` |

场景（每张卡片都渲染）：

| 场景 | 包络 | 接触 |
| --- | --- | --- |
| 正方形四轮 | 凸 | 四个 90° 圆弧，L = 9.2566 |
| 平地五轮 | 凸 | 中间轮点接触 0 m，与凸包差 0.0009 m |
| 抬高 2 cm | 非凸 | 点接触，比凸包长 0.0012 m |
| 抬高 30 cm | 非凸 | 点接触，比凸包长 0.0613 m |
| 降低 30 cm | 凸 | 圆弧 22.62° / 0.1382 m |
| 等半径三轮回程二次接触 | 非凸 | 下行程点接触 + 回程圆弧，同一轮两个顶点 |
| 退化轮序的左端帽 | 非凸 | 端帽转角 ≈ π，绕圆弧 180°，所有轮心在环内 |

单元用例与 Java `BeltPath2DTest` 一一对应：正方形圆弧、平地五轮、顶起中间轮、回程二次接触、
退化轮序端帽稳定性、三角形三轮、同一轮心不同锚定顺序、反向输入、弧长采样、圆弧终点、
各高度不切入/不自交、2 轮 stadium/不等半径/包含/逆序、凸包回退、重叠轮对识别。

结果：`7/7` 场景、`17/17` 用例、`270/270` 断言（含 `11/11` 自检）。

关键性质：

- 凸角（主动轮、诱导轮、比两侧低的负重轮、端帽）走外公切线 + 圆弧；
- 凹角 / 共线（被障碍抬起的负重轮、平地负重轮）走**单点接触**，接触点在轮子外侧角平分线方向；
- 抬起的轮子不会被丢掉：测试检查路径到该轮的最小距离等于半径、路径无自交、不切入任何轮子；
- 凸布局下有序带路与凸包周长一致（平地五轮差 0.0009 m，来自圆弧 1° 采样）；
- 每个成员轮的轮心都必须落在包络内部，单点接触把轮心留在环外即判失败；
- 服务端与客户端必须用同一 `TrackLink.members` 顺序和同一带路推导 `N` / 最大耐久，否则会出现两套长度口径。

### 端帽稳定性

端帽处轮心折线接近回折，`turn` 接近 ±π。`traversalSign * turn` 的符号只由共线的哪一侧决定，
位姿插值或浮点噪声会让它翻掉，包络于是在“绕过该轮”和“擦过半径上一点、轮心落在环外”之间跳变。
`|turn| > 3π/4` 的绝对判据不依赖绕行方向，`endcap-stability` 用例扫过第四个轮子的六个高度，
逐项检查端帽保持凸角、有圆弧、所有轮心在环内、无自交。

## 已知近似与边界

凸角圆弧按 1° 采样；凹角点接触是精确的单点。平坦布局下与凸包的差异只来自圆弧采样。

原型要求：

- 相邻轮不能互相包含到“支撑点落进邻轮”的程度；这种输入抛错，生产实现按 `hasOverlappingWheels` 回退凸包并告警；
- 轮序必须形成简单闭合折线，不处理自交的轮序；
- 端帽判定按转角绝对值，端帽处包角可以超过 180°；
- 凸包基线页保留为对照与回归；非凸页是抬轮场景的正式方向。

## Java 侧

同一套 2D 几何已经落到：

- `src/main/java/io/github/sweetzonzi/machine_max/util/geometry/Vec2.java`
- `Circle2D.java`
- `ConvexHull2D.java`
- `BeltPath2D.java`
- `src/test/java/io/github/sweetzonzi/machine_max/util/geometry/BeltPath2DTest.java`

```bash
./gradlew test --tests "io.github.sweetzonzi.machine_max.util.geometry.BeltPath2DTest" --offline
```

21 个纯 JUnit 用例覆盖同一组凸/平/抬/降布局、退化轮序端帽、2 轮 stadium/不等半径/包含、顺时针输入、
弧长采样、圆弧终点、无自交、不切入轮子、非法输入与凸包基线；不启动 Minecraft。Java 侧目前只到几何层，
`TrackRenderer`、`TrackAttr` 的长度耐久字段与关键事件同步尚未接入。
