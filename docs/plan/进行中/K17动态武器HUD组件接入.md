# K17 动态武器 HUD 组件接入

## 资源与挂载

K17 的武器读数由三个 WC 分别提供。每个 WC 的第一人称、第三人称和炮镜布局引用相同，布局条目使用 `selection_visibility: always`；当前控制组的选中状态只调整名称亮度。屏幕位置由内容包指定，组件没有跨 WC 聚合或自动避让。

| WC | 内置样式 | sdkfz 布局 | 屏幕位置 |
| --- | --- | --- | --- |
| `subsystem.machine_max.main_weapon` | `machine_max:aui_weapon_feed` | `machine_max:k17_weapon_feed` | 右下，offset `[-8,-52,0]` |
| `subsystem.machine_max.atgm_weapon` | `machine_max:aui_weapon_tubes` | `machine_max:k17_weapon_tubes` | 左下，offset `[8,-52,0]` |
| `subsystem.machine_max.secondary_weapon` | `machine_max:aui_weapon_compact` | `machine_max:k17_weapon_compact` | 下方居中，offset `[0,-52,0]` |

WC 配置位于 `src/main/resources/spark_modules/sdkfz/machine_max/parts/k17_turret.json` 的 `variants.sub_parts.sub_part.machine_max.main.subsystems`。座位引用 `machine_max:k17_vehicle_hud` 保留透视仪表；炮手和车长瞄具引用 `machine_max:k17_scope_hud` 保留分划。WC 面板为正交投放，使用 `screen_fixed` 和 `ignore_zoom: true`。

运行期的武器读数统一由 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick` 调度。每台 WC 的三个视角布局独立决定是否显示；没有布局引用的视角不产生武器 HUD。

## 组件契约

共享页面目录为 `src/main/resources/spark_modules/machine_max.builtin/machine_max/documents/weapon_hud_components/`。

| 页面 | 提供的信息 |
| --- | --- |
| `compact.html` | 控制器名称、选中供弹链的库存、供弹链标签、简短准备状态 |
| `tubes.html` | 库存与容量、选中供弹链、各管的弹药标签、准备进度、剩余时间和装填来源 |
| `feed.html` | 逐管信息、各条供弹链的库存与选中箭头、膛内弹种与选中供弹链不匹配时的提示 |

页面通过 `data-mm-text`、`data-mm-num` 和 `data-mm-class` 绑定 MoLang。管和弹链使用同级 `data-mm-repeat`，没有嵌套重复区域。身份重排、条目增减、作用域与异常处理由 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudPageBinding.java#refresh` 负责。

库存不包含膛内弹。即使库存为零，就绪发射管也显示完整进度和 `RDY`。选中箭头由 `belt.selected` 决定；逐管标签、进度和来源分别读取 `tube.round_label`、`tube.fill`、`tube.loading_belt_label`。供弹选择与实际装填来源可以不同。进度显示在独立的管行中，不根据标签文本推断来源身份或合并进某条弹链行。

查询数据由 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudWeaponSnapshot.java#capture` 在客户端 tick 发布，查询实现位于 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/WeaponHudMolangContext.java#WeaponHudMolangContext`。名称使用 WC 的翻译键；没有翻译的名称按原文本显示。页面没有 JavaScript 模拟计时器和固定武器状态。

## 内容作者的组合方式

其他内容包可在自己的布局条目中引用三种内置样式。每个条目产生独立 Document，绑定该条目所属的 WC。页面结构由 HTML 定义，组件内部共用 `hud.css`；需要定制结构时，可以复制一份页面并在自己的 AUI 样式中引用它。

样式的 `defaults` 支持以下 CSS 变量：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| `--phosphor` | `#a4ff80` | 文字、边框与填充色 |
| `--panel-bg` | `rgba(6, 13, 4, 0.48)` | 底板 |
| `--scanline-opacity` | `0.18` | 扫描线浓度，`0` 可关闭 |
| `--panel-width` | `430px` | 页面内容宽度 |
| `--identity-display` | `flex` | 逐管与弹链面板的名称栏，`none` 可隐藏 |
| `--belts-display` | `block` | 弹链面板的弹链列表，`none` 可隐藏 |
| `--tube-columns` | `1fr 1fr` | 逐管面板的列定义，`1fr` 表示单列 |

文字使用 Minecraft 原生字体路径。页面设计视口宽度为 1600 CSS 像素；实际投放缩放由 AUI 视口与布局 `scale` 共同决定，`offset` 使用 Minecraft GUI 坐标。调整宽度、列数或发射管数量时，需要检查组件之间的空隙。

## 静态对照

以下资源保留独立的静态武器面板：

- `src/main/resources/spark_modules/sdkfz/machine_max/documents/k17_weapon_preview/index.html` 与同目录 `hud.css`；
- 样式 `machine_max:k17_weapon_preview`；
- 布局 `machine_max:k17_weapon_preview` 与 `machine_max:k17_weapon_preview_scope`。

需要游戏内对照时，把三个 WC 的三项视角布局设为 `machine_max:empty`；把 `subsystems/k17/k17_seat.json` 的 `views.third_hud_layout` 指向静态布局，把两个 `subsystems/k17/k17_*_sight_preview.json` 的 `scope_hud_layout` 指向静态炮镜布局。以上相对路径均位于 `src/main/resources/spark_modules/sdkfz/machine_max/`。

## 验证边界

2026-10-23 验证记录：`gradlew --offline test` 通过，当前工作区共 108 项测试、零失败；`gradlew --offline syncSchemas build` 通过，包含 `checkSchemas`。本轮没有启动游戏客户端。

`src/test/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/WeaponHudComponentsTest.java#WeaponHudComponentsTest` 用实际 AUI 页面和绑定器验证：选中供弹与膛内弹分离、装填来源、逐管百分比、零库存就绪管、稳定身份重排、四管扩展为八管、页面重载、多 Document 隔离、外观变量、空快照恢复及 K17 的三视角挂载。

`src/test/java/io/github/sweetzonzi/machine_max/client/render/renderable/K17WeaponHudLayoutTest.java#configuredPanelsFitAndDoNotOverlapAcrossGuiSizes` 以 AUI 实际测量的组件尺寸和内容包布局验证七档 GUI 尺寸的边界与重叠，范围从 320×240 到 3840×2160。测试采用标准四管导弹、单管主炮和单个弹链数据；更多弹链、管数或较长翻译需要额外验收。

组件测试导出 `build/reports/hud-components/index.html`，内容来自 AUI 绑定后的 DOM，使用测试快照；它是浏览器排版预览，不是游戏截图。浏览器策略禁止直接打开本地文件，本轮的几何与样式核对以 AUI 测量为准。

游戏内待验收：控制组切换、射击与切弹时的实时数据、上下车、第三人称与两个瞄具切换、缩放和 GUI scale、多管损毁与内容包重载。源码资源配置供新生成或重新加载定义的 K17 使用；已保存载具如带有动态属性覆盖，需要核对其布局字段。
