---
name: blockbench-content-pack
description: 将现成的 Blockbench 模型（.bbmodel）适配为 MachineMax 内容包部件：划分子零件、调整轴心与定位器、配置碰撞箱和连接点、绑定子系统并校验导出。使用 Blockbench 桌面端、MachineMax 插件及 MCP 桥；适用于模型适配、补齐部件配置与排查导出问题。
---

# Blockbench 模型 → MachineMax 内容包

将用户的模型与部件定义配套完成，交付可复核的工程、导出文件和验证结果。纯建模、贴图绘制与动画编排不属于本技能的主要工作。

## 随包资源与路径

本文所有相对路径以本技能目录为基准；执行命令前解析成绝对路径，或将工作目录切到本技能目录。用户的模型路径与内容包路径单独确认，不从当前工作目录推测。

- [references/model-adaptation.md](references/model-adaptation.md)：调整骨骼、碰撞箱、定位器、轮与履带时按需读取；含量测脚本用法与 MTLB 案例。
- [references/schemas/part_definition_schema.json](references/schemas/part_definition_schema.json)：部件定义入口。`references/schemas/` 是 builtin 中文目录中全部 71 个 JSON 的快照，保持内部目录与引用关系。
- [assets/plugins/machine_max_bb_plugin.js](assets/plugins/machine_max_bb_plugin.js)：可加载到 Blockbench 的 MachineMax 单文件编译产物，版本 `0.5.11`，含零件与装配体图标自动生成；内置内容包同步上游（新增通用 X/Y 轴铰链连接点）；打开或切换内容包时会按 `meta.json` 的依赖在同一父目录自动解析同级内容包，未解析到的依赖提示手动选择。
- [bundle-manifest.json](bundle-manifest.json)：schema 与插件的来源仓库、提交、文件清单和 SHA-256。随包插件来自已有构建产物；版本与来源只描述本次快照，兼容性需在用户环境确认。

使用本技能不要求克隆 Machine-Max 或插件源码仓库。schema 是字段与结构参考；可用定义由用户实际安装的内容包决定。

## 环境检查与插件安装

需要 Blockbench 桌面端、MachineMax 插件和独立的 Blockbench MCP 桥插件。MachineMax 插件提供业务工具，MCP 桥提供通信端点，两者都要加载。

1. 先发现已连接的工具，确认当前工程、保存状态与活动模式。工程含未保存修改时先保留当前内存态；结构编辑前准备可恢复的工程副本。
2. 缺少 MachineMax 插件时，指导用户打开 Blockbench 插件窗口，以“从文件加载插件”方式选择随包 `assets/plugins/machine_max_bb_plugin.js`。已有满足能力要求的版本时直接使用。
3. 缺少 MCP 桥时，按[上游安装说明](https://github.com/jasonjgardner/blockbench-mcp-plugin/blob/main/README.md)指导用户安装。上游提供的 URL 为 `https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js`；该第三方插件未包含在本技能中。
4. MCP 桥默认端点为 `http://127.0.0.1:3000/bb-mcp`，使用 Streamable HTTP。以用户实际设置为准；保持回环监听，该服务没有鉴权。
5. 打开 `.bbmodel` 并进入 MachineMax 的“零件定义”模式（`machine_max_part`），再刷新工具列表，调用 `machine_max_bb_plugin_get_context`。专用工具需要工程与该模式，缺少工具不一定是连接失败。

安装指导本身不代表用户授权覆盖已有插件、修改客户端连接配置或开放网络监听。需要执行这些变更时，按用户给定范围操作。没有编辑器连接时，可先检查磁盘文件与 schema，并明确哪些编辑器步骤待完成。

## 工具选择与数据契约

配置编辑优先使用 MachineMax 专用工具；几何编辑、UV、截图使用 MCP 桥的通用工具。先读取实际暴露的参数 schema；表中专用工具名省略共同前缀 `machine_max_bb_plugin_`。

| 任务 | 工具与用法 |
|---|---|
| 当前工程与活动上下文 | `get_context`；`set_active` 切换部件或变体 |
| 读配置 | `read_node`；编辑既有定义前使用 `as: "definition"` 获取可往返的定义形态 |
| 查可用材料、连接器、子系统型号等 | `list_defs` → `read_def`，读取 builtin、依赖包与当前包合并后的真实定义 |
| 修改一个字段 | `set_node`，保留其它字段 |
| 创建或整件替换 | `import_part` / `import_variant` / `import_sub_part` / `import_subsystem` |
| 添加小实例 | `add_instance`，`kind` 为 `hit_box`、`interact_box` 或 `connector`；目标骨骼或定位器须已存在 |
| 绑定模型标记 | `bind_marker`，通过元素 UUID 绑定到配置条目 |
| 清理节点与引用 | `remove_node`，先检查引用与 `cascade` 的含义 |
| 配置与业务校验 | `validate`，检查 `issues`、`errors` 和 `export_ok` |
| 隐藏碰撞几何 | `clear_cube_uv`，把方块六面 `texture` 置 null 并重建网格，导出写成 `"uv": {}` |
| 骨骼、选择、UV、截图 | `list_outline`、`get_selection`、`get_cube_uv`、`capture_screenshot`，以实际工具列表为准 |

路径使用段数组或 JSON Pointer。含点的子零件键是一个完整段，不按 `.` 拆分。例如修改当前部件/变体中的质量：

```json
{"path":["sub_parts","sub_part.example.hull","mass"],"value":1200}
```

- `sub_parts` 键使用 `sub_part.<namespace>.<name>`；连接点、交互区和子系统使用各自完整配置键，模型元素名与配置键分别处理。
- `read_node(as="definition")` 将内部 UUID 投影为骨骼名并剥离内部字段；读取结果保持 `sub_parts` 映射形态。导出文件可能使用 schema 允许的单项简写。
- `import_*` 是整件写入：省略的字段会回到默认值，下级树可能整体替换。局部修改用 `set_node`；确需整件修改时先读完整定义并保留用户配置。
- 修改后检查返回的 `ok`、错误和 `introduced_issues`；一次成功调用不代表整个工程通过校验。
- 专用工具维护配置、标记与原生撤销，不手写 `.bbmodel` 中的 `machine_max_plugin` 数据。
- `risky_eval` 仅用于现有工具无法覆盖的几何操作或量测，不用它绕过专用配置工具。脚本执行条件见几何参考。

## 工作流程

### 1. 读取并确定适配范围

检查工程路径、保存状态、骨骼树、方块与定位器、纹理尺寸、现有配置、当前选择和校验问题。识别已有人工调整，说明本次需要修改的部件。

决定哪些可动结构属于独立 Part，哪些属于同一 Part 中的 SubPart；为各 SubPart 选择起始骨骼。保留能满足契约的现有命名和层级，不默认套用某辆车的骨骼名、轮径或碰撞箱厚度。

### 2. 配套完成模型与定义

按 [model-adaptation.md](references/model-adaptation.md) 处理坐标、轴心、几何与定位器。创建或读取部件/变体，设置子零件范围、质量与命中箱；需要连接和控制功能时再配置连接点、交互区及子系统。

先用 `list_defs/read_def` 查真实可用定义，再填写 `definition` 等资源引用。确认骨骼、定位器、信号目标和配置键对应，编辑一组相关配置后回读并运行校验。

### 3. 使用 schema 核对输出结构

按任务读取入口与其依赖，不把全部 schema 一次装入上下文。

| 关注点 | 本地 schema |
|---|---|
| Part 与变体 | [part_definition_schema.json](references/schemas/part_definition_schema.json)、[variant_attr.schema.json](references/schemas/part/variant_attr.schema.json) |
| SubPart | [sub_part_attr.schema.json](references/schemas/part/subpart/sub_part_attr.schema.json) |
| 命中箱与交互区 | [hit_box_attr.schema.json](references/schemas/part/subpart/hit_box/hit_box_attr.schema.json)、[interact_box_attr.schema.json](references/schemas/part/subpart/interact_box_attr.schema.json) |
| 连接器实例与定义 | [connector_attr.schema.json](references/schemas/part/subpart/connector/connector_attr.schema.json)、[connector_static_attr.schema.json](references/schemas/part/subpart/connector/connector_static_attr.schema.json) |
| 子系统实例与型号 | [subsystem_dynamic_attr.schema.json](references/schemas/subsystem/subsystem_dynamic_attr.schema.json)、[subsystem_static_attr.schema.json](references/schemas/subsystem/subsystem_static_attr.schema.json) |
| 材料与基础类型 | [material_attr.schema.json](references/schemas/base/material_attr.schema.json)、`references/schemas/base/` |
| 配方 | `references/schemas/recipe/` |

这是 builtin 已提供的 schema 集合，不保证覆盖所有内容包模块。schema 的 `default` 是注解，不代表通用校验器会自动填值；`examples` 是结构参考，使用时需替换资源引用并重新校验。

离线校验使用 Draft-07，并将全部文件预注册到本地 registry。内部 `$ref` 按文件位置解析；涉及 HTTPS `$id` 时同时映射其 URI 与由引用形成的 URI 到本地文件，禁止自动联网补 schema。用户环境版本与快照有差异时，报告差异并结合实际插件校验结果判断。

随包 schema 留在技能目录供读取和校验。导出 JSON 的 `$schema` 若指向技能绝对路径，文件分发后会失效；需要随内容包提供编辑器提示时，将所需 schema 及其引用闭包复制到包内并使用包内相对路径。

### 4. 校验、保存与导出

1. 运行专用 `validate`，处理阻断问题并说明待确认的警告。业务校验与 schema 校验都不能证明游戏内物理效果。
2. 按用户授权保存工程、通过 MachineMax 导出界面导出内容包。专用 MCP 编辑工具修改内存态，不自动保存或导出；通用 `export_model` 只导出模型，不能替代完整内容包导出。
3. 检查实际输出的定义、模型、贴图、资源引用与目录结构，核对导出骨骼和定位器。按需运行 `scripts/verify-geo.py`；其限制见几何参考，`OK` 不能作为完整验收。
4. 有游戏验证环境时检查内容包加载、生成、碰撞、交互与运动效果。缺少该环境时明确标注“未做游戏内验证”，不把静态检查描述为可运行证明。

交付时列出修改与输出位置、校验结果、用户需要保存/导出的步骤，以及尚未验证的效果。

## 随包脚本

`scripts/` 用于辅助量测和几何处理，不作为所有模型的固定流水线。`01-inspect.js`、`03-measure-aabb.js` 和 `05-coverage.js` 只读；`02-hierarchy.js` 会改层级，`04-fit-wheel-hitboxes.js` 会调整已存在的轮盒且默认 dry run。先检查各脚本的 `CONFIG` 与当前工程是否匹配。

`mcp.ps1` 是可选的 PowerShell 7 调用适配器，支持 `-Endpoint`、`-Tool`、`-ArgsJson` 与 `-EvalFile`。已有可用 MCP 客户端时直接使用其工具。脚本参数与量测口径见 [model-adaptation.md](references/model-adaptation.md)。
