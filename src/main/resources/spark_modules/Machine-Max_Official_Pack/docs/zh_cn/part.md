# 零部件定义JSON文档

## 概述

零部件定义JSON文件用于描述载具系统中的各种零部件属性。每个零部件由多个子部件（SubPart）组成，而每个子部件又可以包含连接点（Connector）和子系统（Subsystem）等功能模块。

## 零部件基本结构

```json
{
  "部件名称": {
    "variants": {
      "变体名称": {
        "icon": "纹理路径",
        "tags": ["标签列表"],
        "models": {
          "状态": "模型路径"
        },
        "textures": {
          "状态": ["纹理路径列表"]
        },
        "animations": {
          "状态": "动画路径"
        },
        "sub_parts": {
          "子部件名称": {
            // 子部件属性
          }
        }
      }
    }
  }
}
```

## PartType与VariantAttr

### PartType（部件类型）
PartType定义了一个部件的基本属性，包括：
- **name**: 部件名称
- **vehicleDurabilityRate**: 载具耐久度贡献系数
- **vehicleDamageRate**: 载具伤害传递系数
- **vehicleDamageRateDestroyed**: 部件被摧毁时的伤害传递系数
- **shareDurability**: 部件内零件是否共享耐久度
- **variants**: 部件的所有变体列表

### VariantAttr（变体属性）
VariantAttr定义了部件某个变体的具体表现和功能：
- **icon**: 图标路径
- **tags**: 部件标签列表
- **models**: 状态到模型路径的映射
- **textures**: 状态到纹理路径列表的映射
- **animations**: 状态到动画路径的映射
- **subParts**: 子部件名称到子部件属性的映射

变体主要用于区分相似但不同的零件形式，例如：
- 左右对称的零件（左轮胎、右轮胎）
- 载具的不同配置（军用版、民用版）
- 不同功能的同类部件（前悬挂、后悬挂）
- 人体部位的不同形式（左腿、右腿）

## SubPartAttr（子部件属性）

子部件是构成部件的基本物理单元，具有独立的质量、碰撞和物理特性。

```json
"子部件名称": {
  "start_bone": "起始骨骼名称",
  "end_bones": ["终止骨骼名称列表"],
  "durability": 20.0,
  "mass": 25.0,
  "projected_area": [0.0, 0.0, 0.0],
  "block_collision": "true",
  "collision_height": -1.0,
  "climb_assist": false,
  "hit_boxes": {
    "碰撞箱名称": {
      // 碰撞箱属性
    }
  },
  "interact_boxes": {
    "交互区名称": {
      // 交互区属性
    }
  },
  "connectors": {
    "连接点名称": {
      // 连接点属性
    }
  },
  "subsystems": {
    "子系统名称": {
      // 子系统属性
    }
  }
}
```

### SubPartAttr 字段详解

#### 模型属性
- **start_bone** (可选): 起始骨骼名称，用于确定子部件渲染和碰撞的起始骨骼
- **end_bones** (可选): 终止骨骼名称列表，用于确定子部件渲染和碰撞的终止骨骼

#### 物理属性
- **durability** (可选，默认20.0): 子部件耐久度，决定部件能够承受多少伤害
- **mass** (可选，默认25.0): 子部件质量(kg)，必须大于0，影响载具的整体重量和物理行为
- **projected_area** (可选，默认Vec3.ZERO): 子部件投影面积，参与空气阻力的计算，用于模拟风阻效果。例如，较大的正面投影面积会使载具在前进时受到更大的空气阻力
- **block_collision** (可选，默认"true"): 方块碰撞模式，可选值：
  - "true": 与所有方块碰撞
  - "ground": 仅与部件之下的地面方块碰撞
  - "false": 不与任何方块碰撞
- **collision_height** (可选，默认-1.0): 碰撞检测高度(m)，负值表示所有障碍均碰撞
- **climb_assist** (可选，默认false): 是否启用攀爬辅助，帮助载具越过小障碍物

#### 功能属性

##### hit_boxes（碰撞箱）
碰撞箱定义了子部件的物理碰撞体积和伤害计算属性，用于物理仿真和伤害处理。

每个碰撞箱的属性包括：
- **name** (可选，默认"part"): 碰撞箱名称，供子系统等查找使用
- **type**: 碰撞形状类型，支持"box"（立方体）、"sphere"（球体）、"cylinder"（圆柱体）等
- **subsystem** (可选): 关联的子系统名称
- **friction** (可选，默认[0.5, 0.5, 0.5]): 各向异性摩擦系数[侧向, 前向, 疑似无用]
- **slip_adaptation** (可选，默认0.5): 滑动适应性系数(0~1)，该属性可削弱打滑时的摩擦系数降低效果，提升抓地力
- **rolling_friction** (可选，默认0.2): 滚动摩擦系数，影响滚动阻力
- **spinning_friction** (可选，默认0): 自旋摩擦系数
- **restitution** (可选，默认0.1): 弹性系数，0为完全非弹性碰撞，1为完全弹性碰撞
- **block_damage_factor** (可选，默认1.0): 方块损伤系数，0为无法破坏方块，1为造成全额伤害
- **angle_effect** (可选，默认true): 等效护甲厚度是否受入射角度影响
- **rha** (可选，默认1.0): 等效护甲厚度，单位mm，减免伤害并控制碰撞时的能量分配情况
- **damage_reduction** (可选，默认0.0): 线性减伤，低于此数值的伤害会被无视
- **collision_damage_reduction** (可选，默认1.0): 碰撞伤害减免，小于此数值的伤害会被免疫
- **damage_multiplier** (可选，默认1.0): 伤害减免系数，1为无伤害减免，0为完全无伤害
- **un_penetrate_damage_factor** (可选，默认0.0): 攻击未能完全击穿护甲时的伤害系数

##### interact_boxes（交互区）
交互区定义了玩家可以与子部件交互的区域。

每个交互区的属性包括：
- **bone**: 骨骼名称，会寻找名称匹配的骨骼，以其内部方块的位置与姿态作为交互判定区体积的位置和姿态
- **signal_targets**: 信号传输目标，定义交互时发送的信号将被传输到哪些子系统
- **interact_mode** (可选，默认"fast"): 交互模式
  - "fast": 玩家碰撞箱与交互区碰撞时按下交互键触发
  - "accurate": 玩家瞄准交互区按下交互键触发
- **condition** (可选，默认"NOR"): 条件逻辑类型，支持AND、OR、NAND、NOR、XOR、XNOR

##### connectors（连接点）
连接点定义了子部件与其他部件连接的节点。

##### subsystems（子系统）
子系统定义为部件提供特定功能的模块。

##### hydro_priority (可选，默认0)
流体动力计算优先级，值越大优先级越高，在流体动力学计算中用于确定遮挡关系。

##### hydrodynamics (可选)
流体动力学属性，用于精确模拟在流体（如空气、水）中的行为，包括阻力、升力等参数。

## Connector（连接点）

连接点是部件之间连接的节点，分为两种类型：
- **Special**: 主动连接端口
- **AttachPoint**: 被动连接端口

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
  "breakable": true,
  "connected_to": "连接目标"
}
```

## Subsystem（子系统）

子系统为部件提供特定功能，例如座椅、引擎、传动系统等。

```json
"子系统名称": {
  "type": "子系统类型",
  "model": "子系统型号",
  // 特定子系统的其他属性
}
```

## 注意事项

1. Connector和Subsystem的具体配置将在专门文档中详细介绍
2. 所有路径均相对于资源包根目录
3. 坐标系遵循右手坐标系，Y轴向上
4. 角度单位一般为度，除非特别说明

---
*本文档为Machine Max载具系统零部件定义JSON的概述，详细的技术规格请参考相关API文档*