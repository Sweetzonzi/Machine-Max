# 用 Blockbench MCP 让 AI 助手操作编辑器

本文说明如何在 Blockbench 桌面端启用 MCP（Model Context Protocol）服务器，让支持该协议的 AI 客户端直接读取场景、修改模型并导出。内容包 JSON 的编写规则见同目录其他文档。

- 插件仓库：<https://github.com/jasonjgardner/blockbench-mcp-plugin>
- 插件文件：<https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js>

---

## 1. 安装

1. 打开 Blockbench **桌面端**（Electron 版；Web 版没有本地进程和监听端口，用不了这个插件）。
2. `File > Plugins`，点 **Load Plugin from URL**，粘贴上面的插件文件地址。
3. 加载时按提示允许它申请的 `net` 与 `process` 权限。

插件在编辑器进程内启动一个 HTTP MCP 服务器；端口与路径在 `Settings > General` 里配置。

---

## 2. 连接参数

| 设置项 | 默认值 | 说明 |
| --- | --- | --- |
| MCP Server Port | `3000` | 监听端口 |
| MCP Server Endpoint | `/bb-mcp` | 完整地址 `http://localhost:3000/bb-mcp` |
| MCP Server Host | `127.0.0.1` | 改成 `0.0.0.0` 或 `::` 才允许其他机器连入 |
| Enable risky_eval | 勾选 | 允许客户端在编辑器里执行任意 JavaScript；取消勾选后客户端看不到该工具 |

客户端一侧填 `http://localhost:3000/bb-mcp`，传输类型选 **Streamable HTTP**；各客户端的配置片段见插件仓库 README。

---

## 3. 能做什么

- 读：工程结构、大纲、当前选中项、方块 UV、材质与贴图清单。
- 改：放置与修改方块和组、网格编辑、盒式与网格 UV、贴图与 PBR 材质、粒子、动画与骨骼。
- 看：离屏视图多角度渲染截图，不移动你自己的视角。
- 出：按 Blockbench 的导出格式直接导出（Bedrock 实体、Java 方块/物品等）。

---

## 4. 安全提醒

服务器默认只监听回环地址，非本机的 `Origin`/`Host` 会被拒绝。但它**没有鉴权**：能访问这个端口的本机进程可以调用全部工具，包括 `risky_eval`。不要把它暴露到公网；在不受信任的环境里取消勾选 `Enable risky_eval`。

---

## 5. 不开编辑器也能用

插件仓库另提供一个 stdio 模式的 headless 服务器，不启动 Blockbench 也能读写 `.bbmodel`：

```bash
npx -y github:jasonjgardner/blockbench-mcp-plugin --root ./models
```

`--root` 限定可访问的目录，必填；适合批量校验与批量出图。
