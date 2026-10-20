# 可复用脚本

这些脚本通过 Blockbench 的 MCP 桥操作**当前打开**的工程。每个 `.js` 是一个自包含的表达式，读文件末尾的 `CONFIG` 改参数，不要改中间的逻辑。

## 运行

```powershell
pwsh -File scripts/mcp.ps1 -EvalFile scripts/01-inspect.js
pwsh -File scripts/mcp.ps1 -Tool capture_screenshot -ArgsJson '{"max_size":1600}'
```

`mcp.ps1` 负责：初始化 MCP 会话（session 存在 `%TEMP%\bb_mcp_session.txt`）、把 JS 里的注释剥掉（`risky_eval` 不接受 `//` 与 `console.`）、解析 SSE 响应。

## 脚本

| 文件 | 作用 | 是否修改工程 |
|---|---|---|
| `01-inspect.js` | 项目概要、骨骼树（2 层）、`Hull` 下按面积排序的平面 | 只读 |
| `02-hierarchy.js` | 建 `Root`/`AllHull`/`Hull`、搬车壳方块、重挂结构组、批量改轴心、设 `export=false` | 修改 |
| `03-measure-aabb.js` | 按骨骼（含子骨骼）量方块总 AABB，顺带回报命中箱方块尺寸 | 只读 |
| `04-fit-wheel-hitboxes.js` | 把已建好的轮碰撞箱改成指定直径（轴心居中、宽度不变） | 修改（默认 dryRun） |
| `05-coverage.js` | 球面射线覆盖率：车心向四周发射，报覆盖率、漏点方位与每个碰撞骨骼的负载 | 只读 |
| `verify-geo.py` | 校验导出的 `.geo.json`：骨骼数、履带骨骼、`uv:{}`、连接点成对 | 只读 |

碰撞箱在视口里手工建：`03` 只量测复核，`04` 只在盒子存在之后按直径改尺寸。

## 使用顺序

1. `01-inspect.js`：确认根组、方块数、主要平面（法线 + 平面偏移 + 面积）。
2. `02-hierarchy.js`：改 `CONFIG` 里的根组名、要建的组、要搬的组、轴心表。
3. 手工建碰撞箱骨骼与方块（见 SKILL「碰撞箱：手工建」）。
4. `03-measure-aabb.js`：量轮/炮塔等骨骼的 AABB，确认尺寸与「每骨骼一个方块」，`targets` 写精确名或 `/正则/`。
5. `04-fit-wheel-hitboxes.js`：按第 4 步量到的直径重设轮盒，先看 dry run 的 `before/after` 再落盘。
6. （可选）`05-coverage.js`：球面射线覆盖率检查，漏点按方位角/仰角分箱，用来发现哪一面可能有缝。
7. 用 `export_model` 导出，再跑 `verify-geo.py`。
8. `BarItems.save_project.trigger()` 或 `trigger_action` 的 `save_project` 保存。

## AABB 量测口径

`03-measure-aabb.js` 走 Blockbench 自己的变换链：先 `Project.model_3d.updateMatrixWorld(true)`，再把每个方块的 `geometry.boundingBox` 八个角用 `cube.mesh.matrixWorld` 变换后求并。**不要**自己拼欧拉矩阵——BB 的旋转顺序是 `Format.euler_order`，三轴旋转的方块（如主动轮齿）手写 XYZ 矩阵会把宽度算大一倍。

结果包含 `inflate` 的微小膨胀，是「视口里看到的」外接盒；多边形轮廓（十边形轮子）的竖跨比面到面大，写碰撞箱字号前先确认用哪个口径。

## 覆盖度口径

`05-coverage.js` 从车心沿斐波那契球面方向发射射线，每条射线取两个量：**表面** `surface`（`'outer'` 默认取射线上最后一个车壳交点，`'inner'` 取第一个）和**碰撞盒最外表面** `t1max`，然后

```
reach = t1max - surface
漏风     ⇔ reach < -toleranceInward
过度覆盖 ⇔ reach >  toleranceOutward   （单独计数，不算漏风）
```

- 必须用**区间**求交、而且只卡一个方向。用「最近命中距离」时，车心落在某个大盒内部会使距离恒为 0；用**对称窗口**时，向内胀成整块的碰撞体（外表面贴住蒙皮、体块延伸到车心）会被误判成漏风。
- 没有表面交点的射线记为 `escaping`（开口：敞篷、无底板都正常），**不计入分母**；`coverage = sealed / hitShell`，所以这类车不会被扣分。
- `ignore` 整棵子树跳过：模型里的履带节、悬挂装饰件会把覆盖率算虚高；**内构（引擎、变速箱、内腔体块、座椅、隔板）必须单独成组后在这里排除**，否则它会被当成蒙皮。
- 漏点按方位角/仰角分箱；射线数翻倍后分箱位置应稳定，抖动说明是精度噪声而不是真漏。

## 安全规则

- **先备份**：脚本改结构前，复制一份 `.bbmodel`；Blockbench 的自动备份在 `%APPDATA%\Blockbench\backups`，每 10 分钟一份，格式是 `<lz>` + LZUTF8，只能由 Blockbench 自己读。
- **先 dry run**：`04` 的 `dryRun: true` 只输出每个盒子改前改后的 `from/to`，不写入工程。
- **手工调整优先**：已经有手工调过的碰撞箱时，改 `from/to/rotation` 就地微调，不要整批重跑。
- 脚本更新后先跑 `01-inspect.js` 验证会话与注释剥离没坏，再动写操作的脚本。
