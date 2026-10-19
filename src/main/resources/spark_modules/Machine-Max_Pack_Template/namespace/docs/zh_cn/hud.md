# HUD 编写指南

Machine-Max 提供了强大的自定义HUD系统，允许为不同的车辆和子系统创建专属的界面显示。HUD系统基于模型、动画和Molang脚本，可以实现动态的仪表盘、状态指示器和信息显示。HUD 分为三层：**样式**只描述长什么样，**布局**是一组有序的元素，**元素**把一份样式放到一个具体位置。

## HUD系统概述

### 核心组件文件路径

以下是示例HUD的核心组件文件路径，您可以在这些位置找到完整的实现示例：

- **HUD样式**: [huds/example_hud.json](../../huds/example_hud.json)
- **HUD布局**: [hud_layouts/seat_dashboard.json](../../hud_layouts/seat_dashboard.json)
- **3D模型文件**: [models/hud/example_hud.geo.json](../../models/hud/example_hud.geo.json)
- **动画文件**: [animations/hud/example_hud/example_hud.animation.json](../../animations/hud/example_hud/example_hud.animation.json)
- **纹理文件**: [textures/hud/example_hud.png](../../textures/hud/example_hud.png)
- **语言文件**: [lang/zh_cn.json](../../lang/zh_cn.json)
- **子系统配置**: [subsystems/default_seat.json](../../subsystems/default_seat.json)
- **高级子系统配置**: [subsystems/senna_gtr/senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json)


## 目录结构
- [HUD系统概述](#hud系统概述)
- [HUD样式（huds/）](#hud样式huds)
- [HUD布局（hud_layouts/）](#hud布局hud_layouts)
- [模型和动画要求](#模型和动画要求)
- [文本显示配置](#文本显示配置)
- [Molang在HUD中的应用](#molang在hud中的应用)
- [子系统HUD关联](#子系统hud关联)
- [实际案例解析](#实际案例解析)
- [最佳实践](#最佳实践)

## HUD系统概述

Machine-Max的HUD系统包含以下核心组件：

1. **HUD样式**（`huds/*.json`） - 定义模型、动画、纹理、染色和文本槽
2. **HUD布局**（`hud_layouts/*.json`） - 有序的元素数组，决定画哪些样式、各在什么位置
3. **3D模型** (`.geo.json`文件) - 提供HUD的几何形状和骨骼结构
4. **动画文件** (`.animation.json`文件) - 控制HUD元素的动态行为
5. **纹理文件** (`.png`文件) - 提供HUD的视觉外观
6. **子系统配置** - 通过 `*_hud_layout` 字段把布局挂到座位或瞄具上

## HUD样式（huds/）

HUD样式定义一个 HUD 的内部外观，不包含位置、旋转、缩放或投影。

### 基本结构
```json
{
  "type": "machine_max:model_hud",
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "color": [255, 255, 255],
  "alpha": 255,
  "texts": {
    // 文本配置
  },
  "scissor": {
    "enable": false,
    "x": 0,
    "y": 0,
    "width": 0,
    "height": 0
  }
}
```

### 配置参数说明

#### 基本属性
- **type** (必需): 载体类型：`machine_max:model_hud`（基岩模型）或 `machine_max:aui_hud`（AUI 页面）
- **model** (必需): HUD的3D模型资源地址，如 `machine_max:example_hud.geo`
- **animation** (必需): HUD的动画资源地址，如 `machine_max:example_hud`
- **texture** (必需): HUD的纹理资源地址，如 `machine_max:textures/hud/example_hud.png`
- **color** (可选): RGB颜色，默认白色
- **alpha** (可选): 透明度，默认255


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
- **scissor**: 剪裁矩形配置（可选，默认不启用）
  - **enable**: 是否启用剪裁矩形
  - **x**: 剪裁矩形中心 x 坐标（像素）
  - **y**: 剪裁矩形中心 y 坐标（像素）
  - **width**: 剪裁矩形宽度（像素）
  - **height**: 剪裁矩形高度（像素）

### AUI 页面样式

`machine_max:aui_hud` 用一个 HTML 页面描述 HUD 外观，页面由内容包 `documents/` 目录提供，可在 GUI/HUD 正交叠层或相机空间的透视平面上绘制。

```json
{
  "type": "machine_max:aui_hud",
  "page": "machine_max/weapon_hud_element/weapon_ammo.html",
  "defaults": { "--hud-accent": "#7dd3fc" }
}
```

- **page** (必需): AUI 页面键，形式为 `<内容包命名空间>/<相对路径>`，例如内容包里的 `documents/weapon_hud_element/weapon_ammo.html` 对应 `machine_max/weapon_hud_element/weapon_ammo.html`
- **defaults** (可选): 写进页面 `body` 的 CSS 变量，页面样式表可用 `var(...)` 读取

页面按内容收缩：运行时给 `body` 加上 `mm-hud` 类并设置 `inline-block`、无外边距、透明背景的内联规则。页面描述组件内容，布局决定投放位置与投影；需要暗底时，在组件容器上设置背景。布局的 `perspective: false` 选择正交叠层，`perspective: true` 选择透视平面；该字段默认是 `true`，屏幕锚定的 AUI 元素应显式写 `false`。

AUI HUD 不向页面转发鼠标输入。透视模式支持页面的几何、文字与裁剪，不保证复杂 `backdrop-filter` 等屏幕采样效果。

## HUD布局（hud_layouts/）

布局文件的内容是一个数组，每个条目是一个元素：一份样式引用加一组完整投放值。元素内联在布局里，不单独注册；同一个样式可以在一个布局里出现多次。

```json
[
  {
    "style": "machine_max:example_hud",
    "perspective": true,
    "offset": [95, 55, -135],
    "rotation": [-25, -30, 0],
    "scale": [15, 15, 15]
  }
]
```

| 字段 | 必需 | 说明 |
| --- | --- | --- |
| `style` | 是 | 样式资源键，指向 `huds/` 下的文件 |
| `perspective` | 否 | 默认 `true`；`true` 走透视投影，`false` 走正交投影 |
| `offset` | 否 | 透视：相机空间位移 `(x, -y, z)`，可见平面需在相机前方（通常 `z < 0`）；正交模型：相对屏幕中心的像素偏移；正交 AUI：相对 `anchor` 对齐位置的 GUI 像素偏移，`z` 为绘制深度 |
| `rotation` | 否 | `[x, y, z]` 旋转角度，单位为度；透视 AUI 按 Z/Y/X 合成，正交 AUI 不读取该字段 |
| `scale` | 否 | 默认 `[1, 1, 1]`；正交 AUI 的 X/Y 分量分别缩放页面两轴，透视 AUI 将 CSS 像素缩放为相机空间单位 |
| `scope_behavior` | 否 | 默认 `follow_position`；只在炮镜布局里生效，可选 `screen_fixed` / `follow_position` / `follow_transform` |
| `ignore_zoom` | 否 | 默认 `false`；`true` 时元素尺寸不随炮镜变焦变化 |
| `anchor` | 否 | 仅正交 AUI 载体：默认 `top_left`，可选 `top_center`、`top_right`、`center_left`、`center`、`center_right`、`bottom_left`、`bottom_center`、`bottom_right`；元素的对应角（或中心）对齐屏幕的对应角（或中心）；模型与透视 AUI 忽略该字段 |

正交 AUI 按内容尺寸计算 `anchor`，并在对齐后叠加 `offset.x/y`。AUI 视口的 CSS → GUI 换算比例同时作用于两轴，再分别乘 `scale.x/y`；例如实际宽度为 `CSS 宽度 × viewport.renderScale × scale.x`。绘制与裁剪使用同一组位置和缩放。正交 AUI 的布局 `rotation`、`scope_behavior` 与 `ignore_zoom` 不生效，页面内部可用 CSS 旋转。

透视 AUI 以页面内容中心为局部原点，将 CSS 向下的 Y 轴翻为相机向上的 Y 轴，按 `T(x, -y, z) × Rz × Ry × Rx × S(scale.x, -scale.y, scale.z)` 投放；角度以度为单位。该模式直接把 CSS 像素经 `scale` 换算为相机空间单位，不叠加正交视口的 CSS → GUI 缩放，也不使用屏幕 `anchor`。`scope_behavior` 与 `ignore_zoom` 在炮镜视角中参与投放；`screen_fixed` 使用屏幕中心对应的相机空间原点。

将“AUI 页面样式”中的样式示例保存为命名空间 `machine_max` 的 `huds/aui_weapon_status.json` 后，可用这份布局同时显示中心正交面板与右上角透视面板。透视参数按约 `280 × 120` CSS 像素的内容、70° 视场角与 16:9 画面设置，面板位置需随实际内容尺寸和视场角调整。

```json
[
  {
    "style": "machine_max:aui_weapon_status",
    "perspective": false,
    "anchor": "center",
    "offset": [0, 0, 0],
    "scale": [1, 1, 1]
  },
  {
    "style": "machine_max:aui_weapon_status",
    "perspective": true,
    "scope_behavior": "screen_fixed",
    "offset": [95, -60, -135],
    "rotation": [15, -25, -5],
    "scale": [0.2, 0.2, 0.2]
  }
]
```

键的规则：文件相对路径去掉 `.json` 就是资源键，并带上内容包命名空间。`hud_layouts/seat_dashboard.json` 的键是 `machine_max:seat_dashboard`；`hud_layouts/foo/bar.json` 的键是 `machine_max:foo/bar`。样式同理：`huds/example_hud.json` 的键是 `machine_max:example_hud`。布局与样式分属两个模块，键同名不会冲突。

布局数组的顺序就是同一种投影模式内的提交顺序；透视与正交之间的先后由渲染批次决定，不由布局表达。

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
      "0.0": [0, 0, "240*(math.abs(global.get('engine_speed')??0)*30/math.pi)/9000"]
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
    "math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)",
    "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
    "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 10))"
  ],
  "significand": 0
}
```

### 档位显示
```json
"gear": {
  "key": "hud.machine_max.default_1.gear",
  "molang_args": ["global.get('gear')??'NO GBX'"],
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
math.abs(global.get('vehicle_speed')??0.0) * 3.6

// 获取发动机转速并转换为RPM
math.abs(global.get('engine_speed')??0) * 30 / math.pi
```

#### 数字分解显示
```molang
// 百位数
math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)

// 十位数
math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)

// 个位数
math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 10))
```

#### 条件显示
```molang
// 根据子系统状态显示警告
local.has_subsystem('engine') && local.subsystem_destroyed('engine') ? 1.0 : 0.0
```

## 子系统HUD关联

HUD 通过宿主的视角字段与特定座位或瞄具关联：座椅使用 `first_hud_layout` / `third_hud_layout`，相机与瞄具使用 `scope_hud_layout`。每个字段是一个布局资源键，缺失表示该视角不显示 HUD。

### 座椅子系统配置
```json
{
  "type": "machine_max:seat",
  "views": {
    "enable_first_person": true,
    "first_hud_layout": "machine_max:seat_dashboard",
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_hud_layout": "machine_max:seat_dashboard",
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

### 瞄具/相机子系统配置
```json
{
  "type": "machine_max:sight",
  "scope_hud_layout": "machine_max:scope_gunner"
}
```

### 视图属性说明
- **enable_first_person**: 是否启用第一人称视角
- **first_hud_layout**: 第一人称视角使用的 HUD 布局；省略表示该视角不显示 HUD
- **first_person_offset**: 第一人称视角偏移量 (x+右, y+上, z+后，单位米)
- **enable_third_person**: 是否启用第三人称视角
- **third_hud_layout**: 第三人称视角使用的 HUD 布局；省略表示该视角不显示 HUD
- **follow_vehicle**: 摄像机是否跟随车辆旋转
- **focus_on_center**: 摄像机是否聚焦于载具中心
- **distance_scale**: 第三人称视角距离倍率
- **scope_hud_layout**（相机/瞄具）: 炮镜视角使用的 HUD 布局；省略表示炮镜不叠加布局提供的 HUD

## 实际案例解析

### 示例HUD分析
以 `machine_max:example_hud` 为例，分析其完整实现。您可以通过以下路径查看完整的实现文件：

#### 1. HUD样式 ([example_hud.json](../../huds/example_hud.json))

这是一个功能完整的车辆仪表盘样式，能够同时显示发动机转速、变速箱挡位和载具速度等关键信息。投放值在同目录的 [seat_dashboard.json](../../hud_layouts/seat_dashboard.json) 里。

```json
{
  "type": "machine_max:model_hud",
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "texts": {
    "speed": {
      "key": "hud.machine_max.default_1.speed",
      "centered": true,
      "shadow": true,
      "scale": [2, 2, 2],
      "molang_args": [
        "math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)",
        "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 100) / 10)",
        "math.floor(math.mod((math.abs(global.get('vehicle_speed')??0.0)) * 3.6, 10))"
      ]
    },
    "gear": {
      "key": "hud.machine_max.default_1.gear",
      "centered": true,
      "shadow": true,
      "molang_args": ["global.get('gear')??'NO GBX'"]
    }
  }
}
```

**HUD功能概述：**

这个HUD样式与它引用的布局实现了以下核心功能：

- **速度显示**：实时显示车辆行驶速度，以公里/小时为单位，采用三位数字显示格式
- **挡位指示**：显示当前变速箱所处的挡位状态，包括前进挡、倒挡和空挡
- **转速表指针**：通过动画系统控制转速表指针的动态旋转，反映发动机工作状态


##### 翻译键值对解析 ([lang/zh_cn.json](../../lang/zh_cn.json))

在语言文件中定义了HUD文本的翻译格式，这些翻译键与样式中的文本槽紧密配合，实现了动态内容的格式化显示：

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
      "0.0": [0, 0, "240*(math.abs(global.get('engine_speed')??0)*30/math.pi)/9000"]
    }
  }
}
```

这个动画配置实现了转速表指针的动态旋转效果。当发动机转速变化时，指针会实时旋转，为驾驶员提供直观的转速反馈。

#### 3. 子系统关联 ([senna_gtr_seat.json](../../subsystems/senna_gtr/senna_gtr_seat.json))

座椅子系统配置将 HUD 与特定的座位关联起来，定义了不同视角使用的布局：

```json
{
  "views": {
    "enable_first_person": true,
    "first_hud_layout": "machine_max:seat_dashboard",
    "first_person_offset": [0, 0.15, -0.1],
    "enable_third_person": true,
    "third_hud_layout": "machine_max:seat_dashboard",
    "follow_vehicle": true,
    "focus_on_center": true,
    "distance_scale": 1.1
  }
}
```

**一个布局可以包含多个元素**：要同时显示速度表、转速表、油量表，就在布局数组里写多个元素，各自引用对应样式并给出自己的投放；同一个视角只读一个 `*_hud_layout` 字段。需要另一套位置时，复制一份布局文件并改投放，而不是复制样式。
