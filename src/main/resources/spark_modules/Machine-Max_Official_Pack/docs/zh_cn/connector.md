# 连接点定义文档

## 概述

连接点（Connector）是载具系统中用于连接不同部件的机制。通过连接点，部件可以灵活地组装成复杂的载具结构。目前系统支持两种类型的连接点：Special（主动连接端口）和AttachPoint（被动连接端口），其中只有Special类型会应用六自由度关节参数。

## 连接点基本结构

```json
"连接点名称": {
  "locator": "定位器名称",
  "type": "连接点类型",
  "integrity": 10.0,
  "required_tags": [],
  "acceptable_tags": [],
  "forbidden_tags": [],
  "joint_attrs": {
    // 关节属性
  },
  "signal_translations": {},
  "signal_targets": {},
  "collide_between_parts": false,
  "impactMultiplier": true,
  "connected_to": "连接目标"
}
```

## 连接点类型

### Special（主动连接端口）
Special类型的连接点是主动连接端口，只能和AttachPoint类型的连接点连接。这种类型的连接点支持完整的六自由度关节参数配置，可以定义复杂的物理连接行为。

### AttachPoint（被动连接端口）
AttachPoint类型的连接点是被动连接端口，能够和任意类型的连接点连接。这种类型的连接点通常用于接收连接，不支持复杂的关节参数配置。

## 连接点属性详解

### 基础属性

- **locator**（必须）: 定位器名称，用于确定连接点在模型中的位置和姿态。系统会寻找模型中名称匹配的定位器，并以其位置和姿态作为连接点的位置和姿态。

- **type**（必须）: 连接点类型，可选值为"Special"或"AttachPoint"。

- **integrity**（可选，默认10.0）: 连接点结构完整性系数。当连接点受到大于此数值的伤害时，连接的关节会断开。

- **required_tags**（可选，默认[]）: 连接部件必须全部拥有的标签。只有当待连接部件包含所有指定标签时才能建立连接。

- **acceptable_tags**（可选，默认[]）: 连接部件必须至少拥有其中一个的标签。待连接部件只需包含其中任意一个标签即可建立连接。

- **forbidden_tags**（可选，默认[]）: 连接部件不可包含的标签。如果待连接部件包含任何禁止标签，则不能建立连接。

- **collide_between_parts**（可选，默认false）: 是否允许部件间碰撞。设置为true时，连接的部件之间仍会发生碰撞检测。

- **breakable**（可选，默认true）: 连接点是否可被破坏。设置为false时，连接点不会因为外力或伤害而断开连接。

- **connected_to**（可选，默认""）: 连接点默认连接到的部件内连接点名称。用于定义部件内部的默认连接关系。

### 信号系统属性

信号系统是连接点的重要功能之一，它允许在连接的部件之间传递控制信号和状态信息。信号系统有两个主要属性：

- **signal_translations**（可选）: 接收到的信号频道转译规则。格式为{"源频道": "目标频道"}，用于将接收到的信号从一个频道转发到另一个频道。这个功能主要用于解决不同部件使用不同命名约定的问题。

- **signal_targets**（可选）: 控制信号传输目标。定义接收到的信号将被传输到哪些目标，目标可以是子系统、连接点名、part（部件）或vehicle（载具）。

#### 信号系统工作原理

信号系统基于发布-订阅模式工作。当一个连接点接收到信号时，它会根据[signal_targets](file:///D:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/attr/ConnectorAttr.java#L31-L31)配置将信号转发给指定的目标。每个信号都有一个频道名称，这使得多个不同类型的信息可以通过同一个连接点传输。

例如，一个车门连接点可能会将交互信号转发给座椅子系统：

```json
"signal_targets": {
  "door_interact": ["seat"]
}
```

在这个例子中，当车门被交互时会产生一个名为"door_interact"的信号，连接点会将这个信号转发给名为"seat"的子系统。

#### 信号转译示例

当不同部件使用不同的信号命名约定时，可以使用[signal_translations](file:///D:/Files/Project_MinecraftMods/Machine-Max/src/main/java/io/github/sweetzonzi/machine_max/common/vehicle/attr/ConnectorAttr.java#L30-L30)进行转译：

```json
"signal_translations": {
  "engine_start": "motor_on",
  "engine_stop": "motor_off"
}
```

这样，当连接点接收到"engine_start"信号时，会将其转译为"motor_on"再转发出去。

#### 常见信号目标类型

1. **子系统**: 直接指定子系统的名称，如["engine"]、["gearbox"]
2. **部件**: 使用"part"关键字，信号将被发送到整个部件
3. **载具**: 使用"vehicle"关键字，信号将被发送到整个载具
4. **其他连接点**: 直接指定其他连接点的名称

### 关节属性（joint_attrs）

关节属性定义了连接点连接时的物理行为，目前仅在Special类型的连接点中生效。关节支持六自由度的完全自定义配置，可以为每个自由度单独设置参数。

```json
"joint_attrs": {
  "x": {
    "lower_limit": -1.0,
    "upper_limit": 1.0,
    "equilibrium": 0.0,
    "stiffness": 1000.0,
    "damping": 50.0
  },
  "y": {
    // Y轴平移参数
  },
  "z": {
    // Z轴平移参数
  },
  "xr": {
    // X轴旋转参数
  },
  "yr": {
    // Y轴旋转参数
  },
  "zr": {
    // Z轴旋转参数
  }
}
```

六个自由度分别对应：
- **x, y, z**: 分别代表X、Y、Z轴的平移自由度
- **xr, yr, zr**: 分别代表绕X、Y、Z轴的旋转自由度

每个自由度可以设置以下参数：

- **lower_limit**（可选）: 关节位置下限。对于平动轴单位为米，对于旋转轴单位为度。当下限高于上限时，指定的轴自由活动；下限低于上限时，指定的轴被限定于区间内活动；下限等于上限时，指定的轴被固定死。

- **upper_limit**（可选）: 关节位置上限。规则同lower_limit。

- **equilibrium**（可选）: 平衡位置。关节趋向于回到此位置，类似于弹簧的自然长度。

- **stiffness**（可选）: 刚度系数，单位为N/m（平动）或N·m/deg（旋转）。值越大，关节越难被移动。系统会对过大的刚度值进行自动限制以确保数值稳定性。

- **damping**（可选）: 阻尼系数，单位为N/(m/s)（平动）或N·m/(deg/s)（旋转）。值越大，关节运动时的阻力越大，运动会更快停止。系统会对过大的阻尼值进行自动限制以确保数值稳定性。

## 六自由度关节详解

六自由度关节系统允许完全自定义每个自由度的行为，可以模拟各种现实世界中的机械连接：

1. **平动自由度**（x, y, z）:
   - 单位：米（m）
   - 控制部件在三个轴向上的平移运动

2. **旋转自由度**（xr, yr, zr）:
   - 单位：度（°）
   - 控制部件绕三个轴的旋转运动

通过合理配置上下限参数，可以实现多种连接方式：
- **固定连接**: 上下限相等，自由度被锁定
- **自由连接**: 下限大于上限，自由度完全自由
- **受限连接**: 下限小于上限，自由度在范围内活动

结合弹簧刚度和阻尼参数，可以模拟：
- **弹簧阻尼系统**: 如汽车悬架
- **铰链连接**: 如车门铰链
- **滑动连接**: 如抽屉滑轨

## 应用示例

### 简单固定连接
```json
"fixed_connector": {
  "locator": "FixedPoint",
  "type": "Special",
  "integrity": 20.0,
  "joint_attrs": {
    "x": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    },
    "y": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    },
    "z": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    },
    "xr": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    },
    "yr": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    },
    "zr": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    }
  }
}
```

### 带弹簧阻尼的悬架连接
```json
"suspension_connector": {
  "locator": "SuspensionPoint",
  "type": "Special",
  "integrity": 15.0,
  "joint_attrs": {
    "y": {
      "lower_limit": -0.3,
      "upper_limit": 0.3,
      "equilibrium": 0.0,
      "stiffness": 25000.0,
      "damping": 1500.0
    },
    "xr": {
      "lower_limit": -45.0,
      "upper_limit": 45.0,
      "stiffness": 4000.0,
      "damping": 20.0
    }
  }
}
```

### 铰链连接
```json
"hinge_connector": {
  "locator": "HingePoint",
  "type": "Special",
  "integrity": 12.0,
  "joint_attrs": {
    "xr": {
      "lower_limit": 0.0,
      "upper_limit": 90.0
    },
    "y": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    },
    "z": {
      "lower_limit": 0.0,
      "upper_limit": 0.0
    }
  }
}
```

## 注意事项

1. 目前仅有Special类型的连接点会应用关节属性（joint_attrs）参数
2. AttachPoint类型的连接点主要用于被动连接，不支持复杂的关节参数配置
3. 关节参数的单位根据自由度类型有所不同，平动使用米制单位，旋转使用角度制单位
4. 系统会自动限制过大的刚度和阻尼值以确保数值稳定性
5. 通过合理配置关节参数可以模拟各种物理连接行为，如固定连接、铰链连接、悬架连接等

---
*本文档为Machine Max载具系统连接点定义的详细说明，更多技术细节请参考相关API文档*