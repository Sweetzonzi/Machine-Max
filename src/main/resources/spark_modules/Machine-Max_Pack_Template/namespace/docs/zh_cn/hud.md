# HUD 编写指南

Machine-Max 提供了强大的自定义HUD系统，允许为不同的车辆和子系统创建专属的界面显示。HUD系统基于模型、动画和Molang脚本，可以实现动态的仪表盘、状态指示器和信息显示。

## HUD系统概述

### 核心组件文件路径

以下是示例HUD的核心组件文件路径，您可以在这些位置找到完整的实现示例：

- **HUD配置文件**: [huds/example_hud.json](../../huds/example_hud.json)
- **3D模型文件**: [models/hud/example_hud.geo.json](../../models/hud/example_hud.geo.json)
- **动画文件**: [animations/hud/example_hud/example_hud.animation.json](../../animations/hud/example_hud/example_hud.animation.json)
- **纹理文件**: [textures/hud/example_hud.png](../../textures/hud/example_hud.png)
- **语言文件**: [lang/zh_cn.json](../../lang/zh_cn.json)
- **子系统配置**: [subsystems/default_seat.json](../../subsystems/default_seat.json)
- **高级子系统配置**: [subsystems/senna_gtr/senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json)


## 目录结构
- [HUD系统概述](#hud系统概述)
- [HUD配置文件结构](#hud配置文件结构)
- [模型和动画要求](#模型和动画要求)
- [文本显示配置](#文本显示配置)
- [Molang在HUD中的应用](#molang在hud中的应用)
- [子系统HUD关联](#子系统hud关联)
- [实际案例解析](#实际案例解析)
- [最佳实践](#最佳实践)

## HUD系统概述

Machine-Max的HUD系统包含以下核心组件：

1. **HUD配置** (`.json`文件) - 定义HUD的基本属性和文本显示
2. **3D模型** (`.geo.json`文件) - 提供HUD的几何形状和骨骼结构
3. **动画文件** (`.animation.json`文件) - 控制HUD元素的动态行为
4. **纹理文件** (`.png`文件) - 提供HUD的视觉外观
5. **子系统配置** - 将HUD与特定的座椅子系统关联

## HUD配置文件结构

HUD配置文件是HUD系统的核心，定义了HUD的基本属性和显示行为。

### 基本结构
```json
{
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "offset": [95, 55, -135],
  "rotation": [-25, -30, 0],
  "scale": [15, 15, 15],
  "color": [255, 255, 255],
  "alpha": 255,
  "perspective": true,
  "texts": {
    // 文本配置
  },
  "enable_scissor": false,
  "scissor_x": 0,
  "scissor_y": 0,
  "scissor_width": 0,
  "scissor_height": 0
}
```

### 配置参数说明

#### 基本属性
- **model** (必需): HUD的3D模型资源地址，如 `machine_max:example_hud.geo`
- **animation** (必需): HUD的动画资源地址，如 `machine_max:example_hud`
- **texture** (必需): HUD的纹理资源地址，如 `machine_max:textures/hud/example_hud.png`
- **offset** (可选): 相对屏幕中心的偏移量，透视投影下单位为米，正交投影下单位为像素
- **rotation** (可选): 旋转角度，单位为度
- **scale** (可选): 缩放比例
- **color** (可选): RGB颜色，默认白色
- **alpha** (可选): 透明度，默认255
- **perspective** (可选): 是否采用透视投影渲染，默认true


#### 文本显示
```json
"texts": {
  "locator_name": {
    "key": "translation.key",
    "centered": true,
    "shadow": false,
    "scale": [0.7, 0.7, 0.7],
    "color": [255, 0, 0],
    "molang_args": ["expression1", "expression2"],
    "significand": 0
  }
}
```

- **locator_name**: 定位器名称，用于控制文字的显示位置和旋转
- **key**: 翻译键，用于翻译文本
- **centered**: 是否居中显示，默认true
- **shadow**: 是否渲染文字阴影，默认false
- **scale**: 缩放比例，默认[1, 1, 1]
- **color**: RGB颜色
- **molang_args**: Molang参数，用于填充翻译文本预留的参数位置
- **significand**: Molang参数计算结果为数字时，保留的小数点后位数，默认0

#### 剪裁区域
- **enable_scissor**: 是否启用剪裁矩形，范围外的部分将被裁剪，默认false
- **scissor_x**: 剪裁矩形中心x坐标(像素)
- **scissor_y**: 剪裁矩形中心y坐标(像素)
- **scissor_width**: 剪裁矩形宽度(像素)
- **scissor_height**: 剪裁矩形高度(像素)

## 模型和动画要求

### 3D模型规范
HUD模型使用标准的Minecraft Bedrock格式，需要包含以下元素：

1. **骨骼结构**: 每个需要显示文本的位置需要一个对应的骨骼
2. **定位器**: 用于确定文本显示的位置和方向
3. **几何形状**: 定义HUD的视觉外观


### 动画文件
动画文件控制HUD元素的动态行为，可以使用Molang表达式实现动态效果：

```json
{
  "Pointer": {
    "rotation": {
      "0.0": [0, 0, "240*(math.abs(vehicle.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```


## 文本显示配置

### 基本文本显示
```json
"speed": {
  "key": "hud.machine_max.default_1.speed",
  "centered": true,
  "shadow": true,
  "scale": [2, 2, 2]
}
```

### 动态文本显示
使用Molang参数实现动态内容：
```json
"speed": {
  "key": "hud.machine_max.default_1.speed",
  "molang_args": [
    "math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)",
    "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
    "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))"
  ],
  "significand": 0
}
```

### 档位显示
```json
"gear": {
  "key": "hud.machine_max.default_1.gear",
  "molang_args": ["vehicle.get('gear')??'NO GBX'"],
  "centered": true,
  "shadow": true
}
```

## Molang在HUD中的应用

HUD系统充分利用了Molang的强大功能，可以实现复杂的动态显示效果。

### 常用Molang模式

#### 信号获取和单位转换
```molang
// 获取车辆速度并转换为km/h
math.abs(vehicle.get('vehicle_speed')??0.0) * 3.6

// 获取发动机转速并转换为RPM
math.abs(vehicle.get('engine_speed')??0) * 30 / math.pi
```

#### 数字分解显示
```molang
// 百位数
math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)

// 十位数
math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)

// 个位数
math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))
```

#### 条件显示
```molang
// 根据子系统状态显示警告
subpart.has_subsystem('engine') && subpart.subsystem_destroyed('engine') ? 1.0 : 0.0
```

## 子系统HUD关联

HUD通过座椅子系统的视图属性与特定座位关联：

### 座椅子系统配置
```json
{
  "type": "machine_max:seat",
  "views": {
    "enable_first_person": true,
    "first_person_hud": ["machine_max:example_hud"],
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_person_hud": ["machine_max:example_hud"],
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```


### 视图属性说明
- **enable_first_person**: 是否启用第一人称视角
- **first_person_hud**: 第一人称视角下的HUD组件列表
- **first_person_offset**: 第一人称视角偏移量 (x+右, y+上, z+后，单位米)
- **enable_third_person**: 是否启用第三人称视角
- **third_person_hud**: 第三人称视角下的HUD组件列表
- **follow_vehicle**: 摄像机是否跟随车辆旋转
- **focus_on_center**: 摄像机是否聚焦于载具中心
- **distance_scale**: 第三人称视角距离倍率

## 实际案例解析

### 示例HUD分析
以 `machine_max:example_hud` 为例，分析其完整实现。您可以通过以下路径查看完整的实现文件：

#### 1. HUD配置 ([example_hud.json](../../huds/example_hud.json))

这是一个功能完整的车辆仪表盘HUD配置，能够同时显示发动机转速、变速箱挡位和载具速度等关键信息。

```json
{
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "offset": [95, 55, -135],
  "rotation": [-25, -30, 0],
  "scale": [15, 15, 15],
  "perspective": true,
  "texts": {
    "speed": {
      "key": "hud.machine_max.default_1.speed",
      "centered": true,
      "shadow": true,
      "scale": [2, 2, 2],
      "molang_args": [
        "math.floor((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6 / 100)",
        "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
        "math.floor(math.mod((math.abs(vehicle.get('vehicle_speed')??0.0)) * 3.6, 10))"
      ]
    },
    "gear": {
      "key": "hud.machine_max.default_1.gear",
      "centered": true,
      "shadow": true,
      "molang_args": ["vehicle.get('gear')??'NO GBX'"]
    }
  }
}
```

**HUD功能概述：**

这个HUD配置实现了以下核心功能：

- **速度显示**：实时显示车辆行驶速度，以公里/小时为单位，采用三位数字显示格式
- **挡位指示**：显示当前变速箱所处的挡位状态，包括前进挡、倒挡和空挡
- **转速表指针**：通过动画系统控制转速表指针的动态旋转，反映发动机工作状态


##### 翻译键值对解析 ([lang/zh_cn.json](../../lang/zh_cn.json))

在语言文件中定义了HUD文本的翻译格式，这些翻译键与HUD配置中的文本显示紧密配合，实现了动态内容的格式化显示：

```json
"hud.machine_max.default_1.speed": "§o%1$s%2$s%3$s",
"hud.machine_max.default_1.gear": "§o%1$s",
"hud.machine_max.default_1.kmh": "§oKM/H"
```

**翻译键的工作原理**：

- **速度显示** (`hud.machine_max.default_1.speed`)：这个翻译键接收三个参数，分别对应速度的百位、十位和个位数字。`§o`代码表示使用斜体字体显示，让速度读数更加醒目。

- **档位显示** (`hud.machine_max.default_1.gear`)：只需要一个参数来显示当前档位信息，同样使用斜体格式增强可读性。

**数字转换的完整过程**：

当车辆行驶时，HUD系统会实时处理速度信号并显示在界面上。这个过程可以分为以下几个步骤：

1. **获取实时数据**：首先从车辆系统中获取当前的速度信号，单位为米/秒
2. **单位转换**：将米/秒转换为更常用的公里/小时单位
3. **数字分解**：将三位数的速度值分解为百位、十位和个位三个独立的数字
4. **格式化显示**：将分解后的数字填充到翻译键的占位符中，并应用斜体格式
5. **最终呈现**：例如当车辆以123公里/小时的速度行驶时，HUD会显示为"§o123"的斜体数字

这种设计使得HUD能够实时反映车辆状态，同时保持界面的美观和一致性。

#### 2. 动画控制 ([example_hud.animation.json](../../animations/hud/example_hud/example_hud.animation.json))

动画文件定义了HUD元素的动态行为，比如转速表指针的旋转效果：

```json
{
  "Pointer": {
    "rotation": {
      "0.0": [0, 0, "240*(math.abs(vehicle.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

这个动画配置实现了转速表指针的动态旋转效果。当发动机转速变化时，指针会实时旋转，为驾驶员提供直观的转速反馈。

#### 3. 子系统关联 ([senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json))

座椅子系统配置将HUD与特定的座位关联起来，定义了不同视角下的HUD显示规则：

```json
{
  "views": {
    "enable_first_person": true,
    "first_person_hud": ["machine_max:example_hud"],
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_person_hud": ["machine_max:example_hud"],
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

**第一人称HUD的灵活性**：您可以根据需要为第一人称视角添加多个HUD组件。例如，可以同时显示速度表、转速表、油量表等不同的HUD元素，只需在`first_person_hud`数组中添加相应的HUD资源路径即可。
