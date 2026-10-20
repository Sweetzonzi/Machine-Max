---
name: blockbench-content-pack
description: 把一份现成的 Blockbench 模型（.bbmodel）适配成 MachineMax 内容包可用的部件模型：骨骼/子零件划分、轴心、定位器、碰撞箱、交互区、履带与导出校验。基于 Blockbench 桌面端 + machine_max 插件 + MCP 桥。适用于「让这辆车能用」「给模型补碰撞箱/定位器」「按装甲板对齐命中框」这类任务。
---

# Blockbench 模型 → 内容包适配

## 适用场景

- 有一份第三方或新做的 `.bbmodel`，要变成 `spark_modules/<pack>/models/part/*.geo.json` 里的部件模型。
- 模型已有几何，但缺子零件边界、连接点定位器、碰撞箱、交互区。
- 已有部件能用，但要按真实装甲板重做命中框（倾角影响入射角结算）。

不覆盖：纯建模/贴图绘制、动画关键帧编排、内容包 JSON 的字段设计（后者见 `docs/wiki/5-内容包制作教程/`）。

## 前置条件

| 项 | 要求 |
|---|---|
| Blockbench | 桌面端（Electron），Web 版没有本地进程 |
| 插件 | machine_max 插件（提供部件/变体编辑、部件定义导出） |
| MCP 桥 | `http://127.0.0.1:3000/bb-mcp`，传输类型 Streamable HTTP，见 `src/main/resources/spark_modules/machine_max.builtin/machine_max/docs/zh_cn/Blockbench-MCP.md` |
| 工程备份 | 动手前把 `.bbmodel` 复制一份，脚本改结构不可逆（Undo 只能回退单次） |

MCP 无鉴权，只监听回环地址；不要暴露到公网。

## 坐标系与单位（先读，后面全用它）

| 事实 | 含义 |
|---|---|
| 模型正面 = **-Z** | 主动轮/炮口/驾驶窗都在负 Z 一侧 |
| Blockbench 内左侧 = **-X** | 驾驶员在 -X 侧 |
| 导出 `.geo` 时 X 取反 | BB 里 `-27.45` 导出成 `+27.45`；导出后左 = **+X**，与内置载具一致 |
| 20 bb ≈ 1 m | 1 格 = 1 m；`blueprint.scale` 与建模比例对应（K17 按 35 bb/m 建模，装配体写 35） |
| UV 空间 ≠ 图片分辨率 | `.geo` 的 `texture_width/height` 决定 UV 尺度，png 可以是它的 2 倍 |

写内容包 JSON 时用的左右、方向（`xn/xp`）一律按**导出后**的坐标系判断。

## 部件契约（决定骨骼怎么切）

一个部件 = 一个 `.geo` 文件；部件内的刚体（SubPart）由 `start_bone` 与 `end_bones` 划定：

- `start_bone`：该 SubPart 的骨骼子树根，**它自己的 pivot 不参与换基**，父链参与。
- `end_bones`：要挖掉的兄弟子树。后代 SubPart 的 `start_bone` 由 `VariantAttr.computeAutoEndBones` 自动并入，所以层级干净时通常留空。
- 碰撞形状、连接点定位器、`mass_center` 都表达在 **start_bone 空间**。
- 一个 Part 可以含多个 SubPart，SubPart 之间用 `internal: true` 的连接器相连（示例见 K17 的炮塔-火炮）。

轮子的 `wheel` 型碰撞箱是**单个立方体**：

- 半径 = `size.y / 2`，半宽 = `size.x / 2`，圆柱半径 = `sqrt(r² - w²)`，必须为正，否则退化。
- 半径口径有两种，先定再写字号：**含履带厚度**（车体按履带外表面落地，MTLB 轮心 y=8.22、履带节最低点 y≈0.06 → 8.16）或**按轮胎 AABB**（盒子贴住轮胎，履带会沉进地面约 1.19）。MTLB 现行取后者：负重轮 13.94、主动轮 10.59、诱导轮 10.74。
- 一个 SubPart 有多个轮面时取最大半径（`SubPart.getMaxWheelRadius`）。

履带不是模型几何：

- `TrackLink` 的成员是**带轮半径的 SubPart**（主动轮 + 负重轮/诱导轮/托带轮），创建顺序决定包络绕向。
- `TrackRenderer` 按轮组包络程序化画履带节（宽 0.35 m、厚 0.05 m，节距来自 `TrackAttr.linkLength = 0.2 m`）。
- 模型里已有的履带节几何设为不导出，避免和程序化履带叠在一起。

## 工作流

### 0. 摸清现状

先用只读手段摸清现状：

- 复制一份 `.bbmodel`，并确认 `Project.saved`；内存里可能有未落盘的手工改动。
- `list_outline`（`include_cubes:false`）：骨骼树。
- `get_selection`：用户当前选中的元素。
- `risky_eval` 读 `Project.groups` / `Project.elements`：组数、方块数、名称、pivot。
- 配套脚本 `scripts/01-inspect.js` 一次输出这些信息与主要平面。

同时核对：`Project.texture_width/height`、`Project.box_uv`、`Project.format.id`、`Project.visible_box`。

### 1. 决定拆分

先回答三个问题，再动骨骼：

1. 车体、炮塔、火炮、轮子、舱门各自是一个 Part 还是同一个 Part 的 SubPart？（MTLB 用「1 Part + 17 SubPart」；K17 用「车体/轮子/炮塔」三个 Part）
2. 每个 SubPart 的根骨骼叫什么？（大驼峰，例：`AllHull`、`SprocketWheel_Left`、`Roadwheel_Left_1`）
3. 侧向命名按导出坐标系（左 = +X）还是模型内坐标系（左 = -X）？内容包连接器 `direction` 依赖这个决定，建议与内置载具统一。

### 2. 重排层级

目标形状（示例）：

```text
Root                        # pivot (0,0,0)
└ AllHull                   # 车体 SubPart 根
  ├ Hull                    # 车体渲染方块
  ├ Hatch_All / Suspension_All / ...
  ├ HullHitBoxes            # 命中箱骨骼，一个骨骼一块装甲
  ├ HullInteractBoxes       # 交互区骨骼 + 座位定位器
  └ HullConnectors          # 质心 + 连接点定位器
```

要点：

- 把现有的根组改名为 `Root` 并归零 pivot，比新建再搬省事。
- 车壳方块单独放进 `Hull`，`AllHull` 只做分界。
- 轮子/舱盖等 SubPart 根骨骼保持在原父链下即可，自动排除骨骼会处理归属。
- 改完立刻核对 `Project.groups.length` 与预期一致，并检查没有组成为自己的子节点（自引用会让递归遍历栈溢出）。

### 3. 修轴心

- `Root` → (0,0,0)。
- 轮子 → 各自轴心（取该轮自身方块包围盒的中心）。
- 扭杆/悬挂臂 → 内端（朝车体一侧）的轴心，便于以后做悬挂动画。
- 舱盖/车门/车窗 → 铰链边（后缘、外缘或上缘）。

pivot 只影响旋转中心与换基，不动几何；改 pivot 后模型外观不应变化。

### 4. 定位器

| 定位器 | 用途 | 建议位置 |
|---|---|---|
| `mass_center` | 子零件质心 | 车体取几何中心偏下/偏前；轮子可省略（回退到碰撞箱形心，正好是轴心） |
| `hull_<side>_<part>` | 车体侧连接点 | 与轮侧定位器**同坐标**，位于轮轴线上 |
| `<side>_<part>_hull` | 轮侧连接点 | 与对应的车体侧定位器同坐标；沿 X 轴（转动轴）的偏移不影响绕 X 的转动 |
| `<role>_seat` | 座位 | 地板 + 约 1.2~1.4 m 等效高度；数值需进游戏复核 |
| `*_launch_point` / `*_optics` | 武器/观瞄 | 炮口、镜筒前端 |

命名遵循文档：先左右 → 再所属部件 → 再连接部件 → 再细节（`hull_left_sprocket`、`left_sprocket_hull`）。定位器名称在工程内唯一。

### 5. 碰撞箱：手工建

**碰撞箱由作者在视口里手工建。** 按平面聚合出来的盒子只能大致贴合，棱边、倾角、前后错位都要人眼对着装甲板调；返工成本远高于手建。

一个装甲面 = 一个骨骼 + 一个立方体：

1. 在 `HullHitBoxes` 下建骨骼，名字与部件 JSON `hit_boxes` 的键一致（如 `HullRoofArmor`）。
2. 在骨骼里建一个立方体，名字 `<骨骼名>Cube`。**每骨骼只放一个**，多放的会变成额外的碰撞体。
3. 切正投影视图（前/侧/上）对齐：把盒子的外表面贴到装甲外表面，厚度 5~6 格，向车体内退 0.5 格——外表面与装甲齐平才不会漏风。
4. 相邻盒在棱边重叠 1~2 格，斜射的入射角才不会漏缝；重叠区域重复计伤可接受。
5. 立方体 `rotation` 跟装甲倾角一致（最常见是 ±X），轴心放盒子中心。
6. 六个面 UV 置空：MCP `set_cube_uv` 把 `texture` 设为 `null`，导出即为 `"uv": {}`。
7. 骨骼 `visibility = false`，视口里不挡视线。

建完用 `scripts/03-measure-aabb.js` 复核：`targets` 填骨骼名，看 `size` 是否符合预期、`cubes` 是否只有 1 个；`hitBox` 字段同时给出该骨骼下盒子的尺寸与中心。

`scripts/05-coverage.js` 是一个**推荐的检查工具**（不是流程必需项）：从车体中心沿球面均匀方向发射射线（斐波那契分布），对每条射线找工作：

- **表面**：`surface: 'outer'`（默认）取射线上**最后一个**车壳交点，即真正的外蒙皮；`'inner'` 取第一个交点。内构没单独分组时用 `'outer'`，否则内构会被当成蒙皮。
- **碰撞保护**：取所有碰撞盒最外表面 `t1max`，算 `reach = t1max - surface`。

判定是**非对称**的：

```
漏风      ⇔ reach < -toleranceInward     // 盒子比蒙皮浅太多
过度覆盖  ⇔ reach >  toleranceOutward    // 盒子凸出蒙皮太多，单独计数，不算漏风
```

对称窗口是错的：底盘那种向内胀成整块的碰撞体，外表面贴住蒙皮、体块却一直延伸到车心，用「盒子起点离表面多远」去卡会把它判成漏风，而它只是更厚。

**内构必须单独成组并在 `ignore` 里排除**（引擎、变速箱、内腔体块、座椅、内部隔板）。MTLB 实测：车心 `(0, 16.87, 2.18)` 落在 `Hull` 里一块 38.5 × 102.9 × 12.7 的内腔体块内部——不排除的话，`'inner'` 口径下 34% 的射线会「找不到表面」而被记成开口。`'outer'` 只是兜底，内构如果伸出蒙皮，仍然会被当成表面。

```powershell
pwsh -File scripts/mcp.ps1 -EvalFile scripts/05-coverage.js
```

主要参数：`bones`（参与计算的骨骼，可写 `/正则/`）、`ignore`（整棵子树跳过，如 `'Track_|Track__'` 去掉模型里的履带节、或点名内构组）、`collider`（默认 `HitBox`）、`drop`（默认 `Interact`，交互区不算碰撞）、`center`（`'auto'` 取车壳包围盒中心，或写死 `[x,y,z]`）、`rays`、`surface`、`toleranceInward`、`toleranceOutward`。输出 `coverage`、`leaks`、`protruding`、`reachHistogram`、按方位角/仰角分箱的漏点分布、`escaping`（开口），以及每个碰撞骨骼接住的射线数（能看出某块盒子其实是死角）。

判读：`coverage = sealed / hitShell`，只统计「射线确实打到表面」的方向，所以敞篷、没有底板的履带车不会被扣分；`escaping` 是开口数量，随内构排除而上升，属正常。漏点集中在某个仰角带或某个方位，说明那一面的盒子偏小或缺失，不是精度噪声（射线数翻倍后漏点位置应稳定）。补不补由作者判断——这个工具只负责把「哪里可能漏」指出来。

典型面集合（MTLB 实测）：首上、首下、车顶、车底、尾板、尾部下斜板、每侧三块（下侧壁、翼子板外斜面、翼子板顶面）。

**轮（`shape_type: wheel`）**：一个轮 = 一个骨骼 + 一个立方体。游戏只读两个量——`size.y / 2` 是接地球半径、`size.x / 2` 是半宽；`size.z` 只影响视口观感。

1. 用 `scripts/03-measure-aabb.js` 量轮骨骼（含子骨骼）的总 AABB，取轮胎直径。
2. AABB 是整条轮廓的**外接盒**：多边形轮子（如 MTLB 的十边形负重轮）竖向跨 13.94、面到面只有 13.25，写字号前先确认量的是哪一个。
3. 立方体轴心落在轮轴上，`rotation` 保持 `0`（圆柱轴 = X）。
4. 宽度取轮宽或履带宽，两者差得多时取履带宽（碰撞体要覆盖履带）。
5. 手工把 `from/to` 填成「中心 ± 直径/2」；轮子多时可以用 `scripts/04-fit-wheel-hitboxes.js` 批量改已建好的盒子（只改尺寸、不新建方块，默认 `dryRun`）。
半径同时是接地球半径：取值小于「轴心到履带外表面」时，履带会按 `TrackRenderer` 的绘制厚度沉进地面。MTLB 采用的是**轮胎 AABB** 口径（负重轮 13.94、主动轮 10.59、诱导轮 10.74，半径即其一半），因此履带会比「轴心到履带节最低点」的 8.16 半径下沉 1.19；要改成履带外表面口径就把直径放大到 `2 × (轴心高度 - 履带节最低点)`。

### 6. 交互区

- 一块交互区 = 一个骨骼 + 一个立方体，骨骼名与 JSON `interact_boxes` 的键一致。
- 典型：驾驶舱盖、车长舱盖、尾门、顶舱盖。
- 座位定位器放在交互区骨骼下，保持与车体 SubPart 同一 `start_bone` 范围。

### 7. 视口与导出开关

| 目的 | 手段 | 副作用 |
|---|---|---|
| 履带节不参与渲染/导出 | 容器组与每节 `export = false` | 视口仍显示 |
| 命中箱/交互区不挡视线 | 骨骼组 `visibility = false` | 导出仍包含（已验证） |
| 命中箱面不可见 | `set_cube_uv` 面 `texture: null` | 视口仍会以无贴图方式绘制 |

`visibility` 只管视口；`export` 只管导出。两者互不影响。

### 8. 导出校验

用 MCP `export_model` 导一份 Bedrock 几何到临时路径，再用 `scripts/verify-geo.py` 检查：

```powershell
pwsh -File scripts/mcp.ps1 -Tool export_model -ArgsJson '{"codec":"bedrock","path":"<tmp>.geo.json"}'
python scripts/verify-geo.py <tmp>.geo.json
```

它覆盖这些检查项：

- 骨骼数量 = 预期（去掉履带节后的组数）。
- 履带节骨骼数量 = 0（`Track__Left` 这类备用履带板不算）。
- 命中箱骨骼存在且每个立方体 `uv` 为 `{}`。
- 连接点定位器成对同坐标。
- `visible_bounds` 覆盖整车（MTLB 用 `[10, 4, 1]`）。

X 轴镜像（BB 的 `-25` 导出成 `+25`，即导出后左 = +X）需要人工对照一个侧向定位器确认。

### 9. 保存

```js
BarItems.save_project.trigger();
```

保存后确认文件时间戳更新。插件专有数据（`machine_max_plugin`）由插件维护，**不要**手写。

## 脚本操作手册（risky_eval）

`risky_eval` 会拒绝含双斜杠注释、块注释和 `console.` 的代码，代码里不要写注释，说明放在代码块外。下面的片段都满足这个限制。

包一层事务：

```js
Undo.initEdit({ outliner: true });
var g = new Group({ name: 'Hull', origin: [0, 20, -5] }).init();
Undo.finishEdit('建立 Hull 组');
```

建组后自动挂在 outliner 根，要换父级用 `addTo`：

```js
var g = new Group({ name: 'Hull', origin: [0, 20, -5] }).init();
g.addTo(parentGroup);
```

建方块并清掉 UV/Face 数据：

```js
var c = new Cube({ name: 'HullRoofArmorCube', from: [-24, 24, -34], to: [24, 30, 67], origin: [0, 27, 16] }).init();
c.addTo(g);
c.uv = {};
c.faces = {};
c.autouv = 0;
```

建定位器：

```js
var l = new Locator({ name: 'mass_center' }).init();
l.position = [0, 16, -8];
l.addTo(container);
```

读世界位姿（three.js 矩阵是列主序，`elements[12..14]` 是平移）：

```js
cube.mesh.updateMatrixWorld(true);
var e = cube.mesh.matrixWorld.elements;
var R = [[e[0], e[1], e[2]], [e[4], e[5], e[6]], [e[8], e[9], e[10]]];
var pos = [e[12], e[13], e[14]];
var world = [pos[0] + R[0][0] * dx + R[1][0] * dy + R[2][0] * dz,
             pos[1] + R[0][1] * dx + R[1][1] * dy + R[2][1] * dz,
             pos[2] + R[0][2] * dx + R[1][2] * dy + R[2][2] * dz];
```

上面三个 `d` 是相对 pivot 的偏移：`d = p - pivot`。反解时 `d = Rᵀ · (p_world - pos)`。

改完 `rotation`/`from`/`to` 必须重建变换，否则渲染与导出用的还是赋值前的姿态：

```js
cube.rotation = [90, 0, 0];
cube.from = [-24, 24, -34];
cube.to = [24, 30, 67];
cube.preview_controller.updateTransform(cube);
cube.mesh.updateMatrixWorld(true);
```

删组用 `group.remove()`（连同子级），删单块用 `cube.remove()`。

## 配套脚本

`scripts/` 下是可复用实现，通过 `scripts/mcp.ps1` 调用（它负责会话、SSE 解析，并把 JS 注释剥掉——`risky_eval` 不接受 `//` 与 `console.`）：

```powershell
pwsh -File scripts/mcp.ps1 -EvalFile scripts/01-inspect.js
```

| 文件 | 用途 | 是否改工程 |
|---|---|---|
| `01-inspect.js` | 项目概要、2 层骨骼树、`Hull` 下按面积排序的平面 | 只读 |
| `02-hierarchy.js` | 建 `Root`/`AllHull`/`Hull`、搬方块、重挂组、批量改轴心、设 `export=false` | 修改 |
| `03-measure-aabb.js` | 按骨骼（含子骨骼）量取方块总 AABB；顺带回报该骨骼下命中箱方块尺寸 | 只读 |
| `04-fit-wheel-hitboxes.js` | 把**已建好**的轮碰撞箱就地改成「轴心居中、宽度不变」的正方形截面 | 修改（默认 dryRun） |
| `05-coverage.js` | 球面射线覆盖率：车心向四周发射，统计车壳被碰撞盒盖住的比例与漏点方位 | 只读 |
| `verify-geo.py` | 校验导出的 `.geo.json`：骨骼数、履带骨骼、`uv:{}`、连接点成对 | 只读 |

每个脚本顶部是 `CONFIG`，示例值取自 MTLB。碰撞箱手工建：`03` 只做量测复核，`04` 只在盒子已经建好之后批量改尺寸。`dryRun: true` 只输出方案不写入。

## 工具速查

| 任务 | 工具 |
|---|---|
| 读骨骼树 | `list_outline`（`include_cubes:false` 只看组） |
| 读选中 | `get_selection` |
| 读 UV | `get_cube_uv` |
| 删 UV（面设为不可见） | `set_cube_uv` + `texture: null` |
| 试导出 | `list_export_formats` → `export_model` |
| 截图 | `capture_screenshot`（`view` 可指向离屏视图）、`capture_app_screenshot` |
| 离屏视角 | `create_offscreen_view` → `set_camera_angle` → `capture_screenshot` |
| 触发原生动作 | `trigger_action`（如 `save_project`） |
| 任意脚本 | `risky_eval` |

## 常见坑（都已踩过）

| 现象 | 原因 | 处理 |
|---|---|---|
| 盒子尺寸/朝向离谱 | 脚本赋值 `rotation` 没触发 `updateTransform` | 赋值后调用 `preview_controller.updateTransform(cube)` |
| 盒子位置整体偏移 | 用了 `pivot` 而不是 mesh 的 `pos` 做换基 | 换基基准取 `matrixWorld` 的平移分量 |
| 法线轴选错、长边被压扁 | 把法线当绝对点做局部变换 | 法线只做方向变换 `Rᵀ·n` |
| 自己拼欧拉矩阵算 AABB，三轴旋转的方块算宽了 70% | BB 用的是 `Format.euler_order`（不是 XYZ），手写矩阵的旋转顺序不对 | 先 `Project.model_3d.updateMatrixWorld(true)`，再拿 `cube.mesh.matrixWorld` 变换几何包围盒的 8 个角（`03-measure-aabb.js` 就是这么写的） |
| 导出里命中箱变成 1×1 UV 的可见面 | 只清了 `uv` 没禁用面 | 用 `set_cube_uv` 把面设为 `null` |
| 导出后再打开，命中箱又露出来 | 导出过程回填了渲染数据 | 用 `set_cube_uv` 处理，别手改 `faces` |
| 新建组成了自己的子级 | 先建组再把它 `addTo` 到自身 | 建完检查 `children` 不含自身 |
| 视口外面漂着一堆无名方块/命中箱 | `new Cube(...).init()` 先把方块挂到 outliner 根，`addTo(骨骼)` 才是搬进组的那一步；中途报错就留在根 | 建完检查 `Outliner.root` 不含 `Cube`；脚本里建完立刻 `addTo` 并回读 `parent` |
| 履带车悬空 | 轮半径只取了轮胎，不含履带厚度 | 半径按履带外表面算：直径 = `2 × (轴心高度 - 履带节最低点)` |
| 导出后左右反了 | 忘了 BB 与 `.geo` 的 X 镜像 | 按导出坐标系复核命名与 `direction` |
| 手工调过的碰撞箱被脚本改回 | `04-fit-wheel-hitboxes.js` 按直径重写 `from/to` | 先看 `dryRun` 的 `before/after`；只想改一个就单改，别整批跑 |
| 导出 `identifier` 变成 `geometry.geometry.mtlb` | `model_identifier` 里已经带了 `geometry.` 前缀 | `model_identifier` 只写 `mtlb`，导出器会自动加前缀 |

## 与模组代码对照

| 关注点 | 位置 |
|---|---|
| 子零件骨骼过滤 | `common/mech/vehicle/attr/SubPartAttr.java`（`filterBones`、`computeGlobalMassCenter`） |
| 自动排除骨骼 | `common/mech/vehicle/attr/VariantAttr.java`（`computeAutoEndBones`） |
| wheel 碰撞箱解析 | `SubPartAttr.java`（`case "wheel"`） |
| 履带成员与包络 | `common/mech/vehicle/track/TrackLink.java`、`TrackEnvelope2D.java` |
| 履带渲染 | `client/render/renderer/TrackRenderer.java` |
| 部件定义格式 | `docs/wiki/5-内容包制作教程/5.2-模型与动画.md`、`5.3-零件定义.md` |
| 已适配范例 | `spark_modules/sdkfz/machine_max/models/part/k17.geo.json` 及 `parts/k17_*.json` |

## MTLB 实测数据（参考基线）

| 项 | 数值 |
|---|---|
| 组数 / 导出骨骼数 | 251 组；导出 87 根骨骼（164 个履带节组设 export=false） |
| 方块 | 1554（其中 33 个是 `uv:{}` 的命中箱/交互方块） |
| 子零件 | 1 车体 + 16 轮（每侧 6 负重轮 + 主动轮 + 诱导轮） |
| 车体命中箱 | 12 个盒 + 1 个发动机盒 |
| 轮命中箱 | 16 个盒（每轮一个立方体） |
| 交互区 | 4 个（驾驶舱盖、车长舱盖、尾门、顶舱盖） |
| 定位器 | 43 个（16 对连接点 + 10 座位 + 1 质心） |
| 车体盒尺寸（面内） | 首下 41.5×20.5；首上 42.9×31.1；顶 48.8×101；底 43.5×108；尾 54.2×25.3；尾下 40.5×14.1；下侧壁 123.7×22；翼子板斜面 85.6×11；翼子板顶面 86×11.8 |
| 车体盒厚度 | 5（斜面）/ 6（顶、底、尾） |
| 轮 AABB 实测（宽 × 竖 × 前后） | 负重轮 5.07 × 13.94 × 13.25；主动轮 7.18 × 10.59 × 10.32；诱导轮 5.75 × 10.74 × 10.21 |
| 轮碰撞箱 | 负重轮 7 × 13.94 × 13.94；主动轮 7 × 10.59 × 10.59；诱导轮 7 × 10.74 × 10.74（宽取履带宽 7） |
| 轮半径 | 负重轮 6.97；主动轮 5.30；诱导轮 5.37（轮胎 AABB 的一半）。「轴心到履带节最低点」是 8.16，两者差 1.19 |
| visible_bounds | `[10, 4, 1]` |

## 维护约定

这份技能随以下三处变化更新：

1. **插件工具**：插件提供原生「部件定义/命中箱标记」编辑后，优先用它替代本文的 `risky_eval` 配方，并把对应配方标注为“插件化后停用”。
2. **模组规则**：命中箱形状、轮半径口径、履带包络、`mass_center` 回退逻辑变动时，同步「部件契约」与「与模组代码对照」。
3. **验证手段**：游戏内验证通道是 `runClient`/`runServer`（无自动化测试），新的验证结论写进「常见坑」。

新增范例载具时，把它的骨骼数、面集合、盒尺寸补进「MTLB 实测数据」表，供后续作者对照。

## 位置与移植

本文件在仓库 `skills/blockbench-content-pack/SKILL.md`。移植到 DSH 技能目录时保持目录名与 frontmatter 的 `name` 一致，正文中的相对路径按仓库根解析。
