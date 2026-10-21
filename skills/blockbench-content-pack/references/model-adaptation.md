# 模型与几何适配参考

本文件用于 Blockbench 模型适配。配置操作、schema 入口与环境安装见 [SKILL.md](../SKILL.md)。下文模型坐标为 Blockbench 单位（bb），实际游戏尺寸需要结合导出与装配缩放确认。

## 坐标与子零件范围

- 模型正面按 -Z 组织。Bedrock 导出会镜像 X：模型中的 -X 对应导出后的 +X。通过一个已知侧向定位器对照导出文件，确定左右命名与连接方向。
- 标准基岩模型的 16 bb 对应一格。现实尺寸与建模单位的比例由作品选择，20 bb/m 是载具制作建议，不能当成通用坐标换算；保留用户作品的比例，配套核对装配缩放。
- UV 尺度由工程或几何文件的 texture_width/texture_height 决定，图片实际像素尺寸可以与之不同。
- 一个 Part 可包含多个 SubPart。start_bone 指定子树根，end_bones 排除子树；后代 SubPart 的起始骨骼会自动纳入排除范围。碰撞形状、连接器定位器和质心需按各自 SubPart 的空间理解，不能直接混用世界坐标。
- 改轴心或父子层级后对照截图与导出结果，检查静态外观、旋转中心与坐标换基。保留可用层级，不强制重命名为 Root/AllHull/Hull。

## 命中箱、交互区与定位器

命中箱字段见 [hit_box_attr.schema.json](schemas/part/subpart/hit_box/hit_box_attr.schema.json)。配置使用 type，例如 {"type":"box"}。导出定义中 hit_boxes 的键指向骨骼名，骨骼下每个 cube 都可能生成一个碰撞形状。希望只有一个形状时只放一个 cube；多个形状应当是有意设计。

装甲盒贴合目标表面与倾角，厚度、重叠量按模型比例及期望效果决定。JSON 的 thickness 是护甲等效厚度（mm），与几何盒厚度分别配置。可通过工具生成盒子或由作者手工调整，最后都应从正交视角检查覆盖、棱边与倾角。

交互区配置键与骨骼名分别处理，bone 字段指定实际骨骼；连接器用 locator 字段引用定位器，定义见 [interact_box_attr.schema.json](schemas/part/subpart/interact_box_attr.schema.json) 与 [connector_attr.schema.json](schemas/part/subpart/connector/connector_attr.schema.json)。先创建模型元素，再通过 add_instance 或 bind_marker 维护配置关联。

配对连接点需在装配目标姿态下重合，并检查定位器朝向。未装配的两个独立 Part 处于不同局部空间时，不能直接比较两组局部坐标是否相等。座位、炮口、观瞄定位器按实际用途放置，并在游戏中复核。

隐藏碰撞几何时，用 set_cube_uv 将六面 texture 设为 null，并检查实际导出是否为 uv: {}；只清 cube.uv 不保证所有面不可见。组的 visibility 控制视口显示，export 控制是否导出。模型渲染骨骼可隐藏在视口中但保持导出。

## 轮与履带

wheel 的尺寸口径见 [hit_box_attr.schema.json](schemas/part/subpart/hit_box/hit_box_attr.schema.json)：Y 半尺寸作为接地半径，X 半尺寸作为半宽。随包快照对应实现还生成轴向为 X 的一般碰撞圆柱，其半径为 sqrt(radius² - halfWidth²)，因此须保证 radius > halfWidth。建议一个轮 SubPart 使用一个 wheel 命中箱，内含一个以轮轴为中心的 cube。

先在轮胎外接尺寸与履带外表面两种轮半径口径中选定一种，再设置尺寸。履带模型的轮心到履带最低点距离可能大于轮胎半径，差值会影响落地外观。多边形轮廓的竖向外接尺寸与面到面尺寸也可能不同。

使用程序化履带时，成员为具有轮半径的 SubPart，成员顺序影响包络方向。确认用户使用这条渲染路径后，将原模型的重复履带节设为 export=false；不对所有带履带外观的模型直接执行这项修改。

## 量测与辅助脚本

这些脚本使用当前打开的工程，示例 CONFIG 源于 MTLB。先按工程修改目标名称、匹配表达式与尺寸；读取配置时沿用专用 MCP 工具。

| 脚本 | 用途与限制 |
|---|---|
| [01-inspect.js](../scripts/01-inspect.js) | 只读：工程概要、两层骨骼树及指定车壳的主要平面 |
| [02-hierarchy.js](../scripts/02-hierarchy.js) | 修改：按 CONFIG 重排层级、轴心与导出开关；不是自动识别任意模型的适配器 |
| [03-measure-aabb.js](../scripts/03-measure-aabb.js) | 只读：按骨骼及其子骨骼量 AABB，并报告命中箱尺寸；targets 可用名字或正则 |
| [04-fit-wheel-hitboxes.js](../scripts/04-fit-wheel-hitboxes.js) | 修改已有轮盒尺寸，默认 dryRun；先看 before/after，避免覆盖作者精调 |
| [05-coverage.js](../scripts/05-coverage.js) | 只读：采样射线估算蒙皮覆盖情况，发现可能漏点 |
| [verify-geo.py](../scripts/verify-geo.py) | 只读：有限的导出检查，具体能力见下节 |

AABB 使用 Blockbench 自己的变换链：先更新 Project.model_3d 的世界矩阵，再将 cube 几何包围盒八角用 cube.mesh.matrixWorld 变换后求并。手写固定 XYZ 欧拉矩阵可能与 Format.euler_order 不同。结果包含 inflate，应确认所量对象是否包含碰撞盒、装饰或内构。

05-coverage.js 从车心向球面采样射线，默认 surface='outer' 取最后一个车壳交点，计算 reach = 碰撞盒最外表面距离 - 蒙皮距离。reach < -toleranceInward 判为可能漏风，reach > toleranceOutward 单独计为凸出。coverage 只统计打到蒙皮的射线，escaping 表示未打到表面的方向。内构、装饰和重复履带节应通过 ignore 排除；覆盖率不是装甲封闭性的证明。

可选调用方式（从技能目录执行；端点参数按实际连接设置）：

```powershell
pwsh -File scripts/mcp.ps1 -EvalFile scripts/03-measure-aabb.js
pwsh -File scripts/mcp.ps1 -EvalFile scripts/05-coverage.js
python scripts/verify-geo.py 'D:/output/model.geo.json' --expect-bones 87
```

脚本通过 risky_eval 在编辑器内执行，需要该工具已启用。mcp.ps1 会剥离注释并拒绝剩余的 // 与 console.；它不是通用 JavaScript 解析器，含 URL 等文本的脚本需检查是否受影响。写几何时包裹正确的原生 Undo 编辑范围；创建元素后立即挂到目标父级，检查孤立方块与层级循环。通过脚本直接修改 cube.rotation/from/to 时调用 cube.preview_controller.updateTransform(cube)，再更新世界矩阵并回读结果。

## 导出检查的能力边界

verify-geo.py 只读取 minecraft:geometry 的第一项。它可检查显式传入的预期骨骼数、匹配指定前缀的履带骨骼、重复定位器名，以及按 hull_ / left_ / right_ 命名规则找到的已有连接器配对坐标。

它只要求全模型至少存在一个 uv: {} 方块，不逐个验证配置命中箱；未匹配到的连接器对不会报缺失；visible_bounds 仅打印；多层骨骼下定位器的位置也没有转换到统一空间。不能据此宣称所有命中箱不可见、所有连接点对齐或可视边界正确。

完整检查需要把实际部件定义与模型对照：各 start_bone、hit_boxes、interact_boxes.bone、connectors.locator 都能解析到正确范围，导出开关与 UV 符合预期，纹理和资源存在，并通过插件业务校验。物理、交互与运动效果需游戏内验证。

## MTLB 案例

以下取自一次 MTLB 适配的量测记录，仅用于理解脚本配置与轮径口径，不作为其它模型的验收基线。

| 项目 | 该案例数值 |
|---|---|
| 工程组数 / 导出骨骼数 | 251 / 87；164 个履带节组设为不导出 |
| 子零件 | 1 车体 + 16 轮，每侧 6 负重轮、1 主动轮、1 诱导轮 |
| 命中箱与交互方块 | 33 个：13 车体/发动机盒、16 轮盒、4 交互盒 |
| 定位器 | 43 个：16 对连接点、10 座位、1 质心 |
| 轮 AABB（宽 × 竖 × 前后，bb） | 负重轮 5.07 × 13.94 × 13.25；主动轮 7.18 × 10.59 × 10.32；诱导轮 5.75 × 10.74 × 10.21 |
| 轮盒（宽 × 高 × 长，bb） | 负重轮 7 × 13.94 × 13.94；主动轮 7 × 10.59 × 10.59；诱导轮 7 × 10.74 × 10.74 |
| 半径口径 | 轮胎 AABB 的一半：6.97 / 5.30 / 5.37；负重轮轴心到履带最低点为 8.16，比选用半径大 1.19 |

案例数据不附带原模型，不能用它证明用户当前工程与该案例一致。
