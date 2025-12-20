# 子系统定义文档

## 概述

子系统（Subsystem）是为载具部件提供特定功能的功能模块。每个子系统都有其独特的功能，如引擎、座椅、传动系统等。子系统通过静态属性（型号）和动态属性（运行时参数）共同定义其行为特征。

## 子系统基本结构

子系统在部件定义JSON文件中声明，基本结构如下：

```json
"子系统名称": {
  "type": "子系统类型",
  "model": "子系统型号",
  // 其他特定属性
}
```

其中：
- **子系统名称**: 在部件内唯一的标识符
- **type**: 子系统类型，决定了子系统的功能类别
- **model**: 子系统型号，指向具体的子系统静态属性定义文件

## 子系统类型与型号

### 静态属性（型号）

静态属性定义了某一型号子系统的固有特性，这些属性在所有同型号的子系统实例中都是相同的。静态属性存储在独立的JSON文件中，位于`subsystems`目录下。

例如，[ae86_engine.json](file:///D:/Files/Project_MinecraftMods/Machine-Max/src/main/resources/spark_modules/Machine-Max_Official_Pack/subsystems/machine_max/ae86/ae86_engine.json)定义了AE86车型使用的引擎型号：

```json
{
  "type": "machine_max:engine",
  "basic_durability": 70,
  "max_power": 125000,
  "max_torque": 149,
  "idle_rpm": 500,
  "idle_rpm_torque_ratio": 0.333,
  "max_torque_rpm": 5200,
  "red_line_rpm": 7500,
  "red_line_torque_ratio": 0.95,
  "inertia": 20,
  "damping_factors": [2.0, 0.03, 0.00002]
}
```

静态属性通常包括：
- 基础耐久度
- 功能相关的核心参数（如引擎的最大功率、最大扭矩）
- 物理特性（如转动惯量）
- 其他决定子系统基本性能的参数

### 动态属性

动态属性定义了子系统在特定部件中的具体配置和运行参数。这些属性在每个子系统实例中都可以不同，它们在部件定义JSON中指定。

例如，在部件定义中引用引擎子系统：

```json
"engine": {
  "type": "machine_max:engine",
  "model": "machine_max:ae86_engine",
  "power_output": "gearbox"
}
```

动态属性通常包括：
- 信号输入输出配置
- 与其他系统的连接关系
- 实例特定的参数调整

## 常见子系统类型

### 引擎系统（engine）
提供动力输出的核心系统。

静态属性关键参数：
- `max_power`: 最大功率(W)
- `max_torque`: 最大扭矩(N·m)
- `idle_rpm`: 怠速转速(rpm)
- `red_line_rpm`: 最大转速(rpm)
- `inertia`: 转动惯量(kg·m²)

动态属性关键参数：
- `power_output`: 功率输出目标

### 变速箱系统（gearbox）
调节动力输出的传动装置。

静态属性关键参数：
- `ratios`: 各个挡位的传动比
- `final_ratio`: 最终变速比

动态属性关键参数：
- `control_inputs`: 控制信号输入频道

### 传动系统（transmission）
将动力分配到多个输出目标。

静态属性关键参数：
- `diff_lock`: 差速锁模式
- `diff_lock_sensitivity`: 差速锁灵敏度

动态属性关键参数：
- `power_outputs`: 动力输出目标及减速比映射

### 轮胎驱动系统（wheel_driver）
控制对接口上部件的滚动和转向。

静态属性关键参数：
- `max_drive_force`: 最大驱动力
- `max_brake_force`: 最大制动力

动态属性关键参数：
- `connector`: 控制的对接口名称

### 座椅系统（seat）
提供乘客乘坐功能。

静态属性关键参数：
- `block_damage`: 是否阻挡伤害
- `render_passenger`: 是否渲染乘客

动态属性关键参数：
- `seat_point_locator`: 乘坐点定位器名称

### 物品存储系统（item_storage）
提供物品存储功能。

静态属性关键参数：
- `rows`: 容器行数
- `columns`: 容器列数

## 子系统信号系统

子系统通过信号系统与其他系统进行通信。信号具有频道名称，可以在子系统间传输数据。

常见的信号类型：
- 控制信号：如油门、刹车、转向等
- 状态信号：如转速、挡位、温度等
- 反馈信号：如负载、阻力等

通过合理配置信号输入输出，可以构建复杂的控制系统。

## 开发建议

1. 合理规划子系统类型和型号，避免重复定义
2. 充分利用静态属性共享机制，减少资源占用
3. 明确区分静态属性和动态属性的职责范围
4. 合理设计信号系统，确保系统间的有效通信
5. 根据实际需求选择合适的子系统类型组合

---
*本文档为Machine Max载具系统子系统定义的详细说明，更多技术细节请参考相关API文档和示例配置文件*