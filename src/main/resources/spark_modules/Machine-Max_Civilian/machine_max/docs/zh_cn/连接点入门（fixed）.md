# 连接点入门（fixed）

本教程目标：
- 学会最基础的连接点配置与使用。
- 从 `fixed_*` 这类固定连接定义开始，完成“可连接”而非“高级关节调参”。
- 本篇不讲信号传输；信号请看《子系统机制与信号传输》。

---

## 1. 连接点的两层配置

连接点分两层：

1. 静态定义层（connector definition）
- 位置：`machine_max/connectors/*.json`
- 作用：定义类型、方向、结构完整性等基础规则。

2. 零件引用层（part 内 connectors）
- 位置：`machine_max/parts/*.json` -> `variants -> sub_parts -> ... -> connectors`
- 作用：声明“这个零件在哪个 locator 上挂哪个定义”。

源码锚点：
- `ConnectorAttr`：解析 `locator`、`definition` 等字段并合并最终属性。
- `Part.createConnectors()`：按 `locator` 创建连接点实例。
- `AbstractConnector`：处理连接/断开与物理关节。

---

## 2. 从 fixed 定义开始

官方示例（节选）：

```json
{
  "$schema": "../docs/zh_cn/schemas/part/subpart/connector/connector_static_attr.schema.json",
  "type": "Simple",
  "direction": "zn",
  "integrity": 20.0,
  "impact_absorption": 0.2,
  "impact_reduction": 2.0,
  "impact_multiplier": 1.0
}
```

关键理解：
- `type: "Simple"`：基础连接类型，优先用于入门。
- `direction`：连接点法线方向，用于对接姿态判定。
- 其余冲击参数可先保持默认示例，不要一开始就大改。

---

## 3. 在 part 中引用 fixed 连接点

在某个 `sub_part` 里添加：

```json
"connectors": {
  "connector.machine_max.up": {
    "locator": "up",
    "definition": "machine_max:fixed_up"
  },
  "connector.machine_max.down": {
    "locator": "down",
    "definition": "machine_max:fixed_down"
  }
}
```

说明：
- `connector.machine_max.up` 是你在此 subpart 内部使用的连接点名称。
- `locator` 必须对应模型中真实存在的 locator 名。
- `definition` 必须指向 `connectors/*.json` 的注册路径。

---

## 4. 必懂字段（入门版）

### `locator`
连接点空间位置来源。若找不到会直接失败。

### `definition`
引用静态连接点定义（例如 `machine_max:fixed_front`）。

### `direction`
不在 part 引用层写，而在 definition 里写。决定连接点法线方向。

### `type`
在 definition 里写。入门阶段优先 `Simple`。

---

## 5. 最小成功标准

1. 内容包加载通过。
2. 游戏内可看到并使用连接点进行安装。
3. 无需任何信号配置也能完成基础对接。

---

## 6. 常见失败与排查

### 问题 A：报 locator 不存在

原因：`locator` 名称与模型不一致。

处理：
- 核对模型 locator 拼写（大小写、下划线）。
- 先只保留 1 个连接点验证。

### 问题 B：definition 找不到

原因：`machine_max:xxx` 与 `connectors/xxx.json` 不匹配。

处理：
- 检查命名空间和文件名。
- 确认 JSON 位于 `machine_max/connectors/` 下。

### 问题 C：连接方向不对，安装姿态异常

原因：definition 的 `direction` 与设计意图不一致。

处理：
- 先用官方 `fixed_front/up/down/...` 对照测试。
- 确认模型中 locator 朝向与预期一致。

---

## 7. 本篇边界（重要）

本篇只讲连接本体，不讲：
- `signal_targets`
- `signal_translations`
- 跨部件信号路由

这些内容统一见：[子系统机制与信号传输](./子系统机制与信号传输.md)

---

## 8. 下一步

完成 fixed 入门后，请继续：
- [连接点高级（自由度与约束）](./连接点高级（自由度与约束）.md)
