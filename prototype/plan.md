# 载具拼装模组 H5 控制菜单实现规范（供编码智能体参考）

## 项目目标

实现一个单页 H5（HTML/CSS/JS）载具控制菜单，用于载具拼装模组。

该界面用于：

- 查看载具状态
- 控制设备
- 管理控制组
- 编辑控制配置
- 管理库存

设计目标：

> 星际公民（Star Citizen）风格的信息架构 + Homeworld 3 的工业科幻感 + Neo-Brutalism（新粗野主义）的高对比大色块 UI。

不要实现成传统 Minecraft GUI，也不要实现成圆角移动端 App UI。

界面应具有：

- 强模块化
- 高可读性
- 极强层级感
- 工业感 / 战术感
- 快速操作优先

必须适应构型差异极大的载具。

界面整体应看起来像：

> 工业控制终端 / 舰桥 HUD / 战术控制台，而不是普通设置菜单。

***

# 文件组织建议

由于页面复杂，不建议单文件实现。

建议拆分：

```text
vehicle-ui/
├── index.html
├── style.css
├── app.js
├── assets/
│   ├── font/
│   └── icons/
```

允许进一步拆分：

```text
components/
    header.js
    overview.js
    device-control.js
    config-editor.js
    inventory.js
```

避免在 HTML 中写大量 inline style。

CSS 与 JS 必须解耦。

***

# 页面总体布局

整个页面固定尺寸。

所有 Tab 页面尺寸一致。

推荐逻辑尺寸：

```text
1600 × 900
```

或：

```text
16:9
```

页面分为：

```text
┌─────────────────────────────┐
│ 顶部标题/导航条              │
├─────────────────────────────┤
│ Tab 内容区域                 │
└─────────────────────────────┘
```

只有：

- 载具概览（01）
- 设备控制（02）

拥有三维预览。

编辑配置（03）和库存管理（04）不显示三维预览，以最大化空间利用率。

***

# 顶部导航设计（极重要）

禁止使用普通 tab bar。

禁止：

```text
[Overview] [Control] [Edit]
```

必须采用：

> 折叠式标题导航（Accordion Header Strip）

设计形式：

当前页面展开：

```text
┌──────────────────────────────────────┐
│ 01 [ DEVICE CONTROL ] 03 04          │
│    /// CONTROL                       │
└──────────────────────────────────────┘
```

或：

```text
01  [02 DEVICE CONTROL]  03  04
```

本质：

当前页：

- 展开
- 白底黑字编号
- 大标题
- 小副标题

其他页：

- 折叠
- 仅数字
- 黑底白字
- 可点击

示例：

选中 01：

```text
[01] VEHICLE OVERVIEW      02 03 04
```

选中 02：

```text
01 [02 DEVICE CONTROL]     03 04
```

选中 03：

```text
01 02 [03 EDIT CONFIG]     04
```

选中 04：

```text
01 02 03 [04 INVENTORY]
```

布局要求：

- 横向连续条带
- 无圆角
- 粗边界
- 强对比

视觉比例：

当前项宽度约：

```text
70~80%
```

其他数字：

```text
5~8%
```

高度：

```text
90~120px
```

当前项内部：

左：

巨大编号。

右：

大标题。

下：

小号副标题。

例如：

```text
02
DEVICE CONTROL
/// CONTROL
```

编号与标题形成强层级关系。

参考：

工业海报、舰桥 HUD、战术系统。

***

# 全局视觉风格（非常重要）

关键词：

```text
Neo-Brutalism
Industrial Sci-Fi
Star Citizen
Homeworld 3
Tactical Dashboard
```

必须：

- 大色块
- 纯色背景
- 黑白高对比
- 强网格布局
- 白色文字
- 几何切分
- 粗边界
- 强信息层级

禁止：

- 圆角
- 毛玻璃
- 拟物
- 阴影泛滥
- 卡通化
- Material Design
- iOS 风格
- 发光赛博朋克风

不要：

```text
border-radius: 12px
```

而是：

```text
border-radius: 0
```

***

# 配色

主色：

```text
黑色 / 深灰
```

例如：

```text
#0B0B0B
#111111
#161616
#1E1E1E
```

文字：

```text
纯白
```

例如：

```text
#FFFFFF
#EAEAEA
```

强调色仅少量使用：

蓝：

设备/控制

```text
#335D9C
```

黄：

警告

```text
#D8A631
```

红：

严重错误

```text
#9E3434
```

绿：

启用状态

```text
#5E7A4A
```

绝不要使用高饱和彩虹色。

***

# 字体风格

推荐：

窄体工业字体。

例如：

```text
Oswald
Rajdhani
Orbitron（谨慎）
Roboto Condensed
DIN 风格
```

标题：

全大写。

例如：

```text
DEVICE CONTROL
```

小字：

可使用：

```text
/// CONTROL
```

形成战术终端气质。

***

# 控制组切换（极重要）

位于：

> 页面顶部，三维预览上方。

Panel 1 与 Panel 2 结构一致。

布局：

```text
BASE
COMBAT
PILOTING
MINING
UTILITY
```

形式：

> 横向大卡片

禁止下拉菜单。

原因：

需要快速切换。

基础组：

```text
BASE GROUP
```

永远启用。

不可关闭。

其他组：

只能单选激活。

例如：

```text
COMBAT
PILOTING
MINING
```

交互：

点击卡片立即切换。

选中态：

- 白色高亮
- 黑字
- 更强对比

未选中：

- 深色背景
- 白字

布局：

```text
┌────┬────┬────┬────┐
│BASE│COMB│PILO│MINE│
└────┴────┴────┴────┘
```

高度：

约：

```text
70~90px
```

***

# Panel 01：载具概览（Vehicle Overview）

必须与 Panel 02 共享布局。

结构：

```text
顶部导航

控制组切换

三维预览（约 55~60%）

底部信息区（约 40%）
```

布局：

```text
┌────────────────────────────┐
│ control groups             │
├────────────────────────────┤
│                            │
│       3D preview           │
│                            │
├────────┬────────┬──────────┤
│status  │warning │extra     │
└────────┴────────┴──────────┘
```

三维预览：

占页面主体。

展示：

载具线框投影。

允许：

- 缓慢旋转
- 鼠标拖拽

风格：

白线框。

状态区：

显示：

- durability
- energy
- speed
- heading
- altitude
- mass
- thrust

使用：

条带式进度条。

警告区：

显示：

```text
▲ LEFT ENGINE LOW
▲ POWER FAILURE
▲ WEAPON OFFLINE
```

禁止 emoji。

允许 UTF-8：

```text
▲
⚠
■
```

***

# Panel 02：设备控制（Device Control）

注意：

> 布局必须与 Panel01 同构。

不能变成另一套布局。

正确结构：

```text
┌────────────────────────────┐
│ control groups             │
├────────────────────────────┤
│                            │
│       3D preview           │
│                            │
├────────┬────────┬──────────┤
│buttons │toggles │sliders   │
└────────┴────────┴──────────┘
```

关键约束：

### 滑条高度

必须与按钮/开关区域同高。

绝不能：

从页面顶部贯穿到底。

错误：

```text
┌preview──────┬||||||||||||||┐
│              │||||||||||||||│
└──────────────┴||||||||||||||┘
```

正确：

```text
┌────preview────┐
└───────────────┘
┌button┬toggle┬slider┐
│      │      │||||||│
│      │      │||||||│
└──────┴──────┴──────┘
```

按钮区：

显示：

```text
THRUST
LANDING GEAR
BRAKE
CRUISE MODE
```

开关区：

ON/OFF。

视觉：

矩形。

滑条：

必须：

- 粗
- 竖向
- 集中排列于右侧
- 横向排开

例如：

```text
THRUST
|||||

YAW
|||||

PITCH
|||||
```

不要细滑杆。

而是：

> 工业控制杆视觉。

***

# Panel 03：编辑配置（Edit Configuration）

布局：

```text
┌──────────────┬──────────────┐
│ control list │ details      │
└──────────────┴──────────────┘
```

左：

控制组列表。

支持：

- 新增
- 删除
- 重命名
- 排序

右：

详细配置。

显示：

- keybind
- toggle/button/axis
- sensitivity
- deadzone
- invert axis
- display name

配置项支持：

- button
- toggle
- slider
- axis

***

# Panel 04：库存管理（Inventory）

允许留空。

仅预留：

```text
NO DATA
```

但保留：

- 标题
- 分类栏
- 搜索
- 容量信息

风格一致。

***

# 间距系统

推荐：

```text
8px 基础单位
```

例如：

```text
8
16
24
32
48
64
```

避免杂乱 spacing。

***

# 动画与交互

允许：

- hover 高亮
- 轻微过渡
- tab 切换

禁止：

花哨动画。

动画时长：

```text
120~220ms
```

推荐：

```css
ease-out
```

***

# 响应式要求

优先：

桌面宽屏。

但避免写死。

使用：

```css
grid
flex
clamp()
minmax()
```

避免绝对定位堆 UI。

***

# 代码规范

要求：

- 模块化
- 可维护
- CSS class 命名明确
- 不允许把所有逻辑塞一个函数

优先：

语义化 DOM。

避免：

大量 magic number。

***

# 实现优先级

1. 先完成布局与视觉还原
2. 再实现 tab 切换
3. 再实现控制组切换
4. 最后添加假数据与交互

