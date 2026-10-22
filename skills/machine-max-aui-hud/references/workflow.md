# 组件设计与接入流程

本页所有内容包路径均以作者的包为基准；不依赖模组工程目录。绑定表达式见 [语法与查询词表](dialect.md)。

## 1. 先定义读数

整理“宿主实例 → 读数 → 查询 → 视角 → 显隐 → 组件”的小表。区分业务状态、观察者状态和作者选择：

- 业务状态：库存、膛内弹、供弹选择、实际装填来源、每管进度。
- 观察者状态：该 WC 是否被当前观察者选中，以及第一/第三/炮镜视角。
- 作者选择：显示名称、信息粒度、配色、宽度、列数和屏幕位置。

紧凑弹量条、逐管列表、弹链面板可以组合；不强制每种武器都显示全部内容。主炮、同轴等只是作者命名，不会自动聚合、排序或避让。保留用户指定的视觉风格。

支持内置组件的版本可直接引用以下样式：

| 样式资源键 | 内容 |
| --- | --- |
| machine_max:aui_weapon_compact | 名称、库存、选中供弹标签、简短状态 |
| machine_max:aui_weapon_tubes | 逐管弹药、进度、时间与装填来源 |
| machine_max:aui_weapon_feed | 逐管区域、弹链库存与选择、膛内差异提示 |

每台 WC 都会获得自己的数据。需要不同结构时在自己的包里编写页面和样式；内置样式不存在的版本不能直接使用其资源键。

## 2. 在可加载的内容包中接线

使用已经能被游戏识别的内容包，保留其包清单和已有资源。以下采用 ExamplePack 作为包目录、example 作为命名空间；按任务改名。

~~~text
ExamplePack/
  example/
    documents/
      weapon_panel/
        index.html
        hud.css
    huds/
      weapon_panel.json
    hud_layouts/
      weapon_panel.json
~~~

### 页面与 CSS

将以下完整页面保存为 example/documents/weapon_panel/index.html：

~~~html
<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="aui-viewport" content="mode=browser; width=1600; user-scalable=false">
  <title>武器面板</title>
  <link rel="stylesheet" href="hud.css">
</head>
<body>
  <div class="weapon-panel" data-mm-class="weapon.selected ? 'selected' : 'unselected'">
    <div class="heading">
      <span data-mm-text="weapon.name"></span>
      <b data-mm-text="weapon.ammo_text"></b>
    </div>
    <div class="tubes">
      <div class="tube" data-mm-repeat="weapon.tubes" data-mm-class="tube.state">
        <div class="line">
          <span data-mm-text="tube.index + 1"></span>
          <span data-mm-text="tube.round_label"></span>
          <span data-mm-text="tube.state == 'ready' ? 'RDY' : (tube.state == 'empty' ? 'EMPTY' : tube.reload_text)"></span>
        </div>
        <div class="track">
          <i data-mm-num="tube.fill" data-mm-var="--fill" data-mm-unit="%"></i>
        </div>
      </div>
    </div>
    <div class="belts">
      <div class="line belt" data-mm-repeat="weapon.belts" data-mm-class="belt.selected ? 'selected' : 'unselected'">
        <span data-mm-text="belt.label"></span>
        <b data-mm-text="belt.remaining"></b>
      </div>
    </div>
  </div>
</body>
</html>
~~~

同目录 hud.css：

~~~css
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; background: transparent; }
body { display: inline-block; }
.weapon-panel {
  display: inline-block;
  width: 360px;
  padding: 10px;
  color: var(--phosphor, #a4ff80);
  background: rgba(6, 13, 4, 0.48);
  font-size: 16px;
  line-height: 20px;
}
.heading, .line { display: flex; gap: 8px; justify-content: space-between; }
.tubes, .belts { display: flex; flex-direction: column; gap: 6px; margin-top: 8px; }
.track { height: 3px; background: #ffffff20; }
.track i { display: block; height: 3px; width: var(--fill, 0%); background: var(--phosphor, #a4ff80); }
.belt { border: 1px solid transparent; padding: 3px; }
.belt.is-selected { border-color: var(--phosphor, #a4ff80); }
.tube.is-empty { opacity: 0.5; }
~~~

### 样式

example/huds/weapon_panel.json：

~~~json
{
  "type": "machine_max:aui_hud",
  "page": "example/weapon_panel/index.html",
  "defaults": {
    "--phosphor": "#a4ff80"
  }
}
~~~

页面键是 example/weapon_panel/index.html，不是 example:weapon_panel/index.html，也不包含 documents/ 前缀。同目录 CSS 使用相对链接。自定义变量必须被页面 CSS 实际引用才会产生效果。

### 布局

example/hud_layouts/weapon_panel.json 是裸数组：

~~~json
[
  {
    "style": "example:weapon_panel",
    "perspective": false,
    "anchor": "bottom_right",
    "offset": [-8, -52, 0],
    "scale": [1, 1, 1],
    "scope_behavior": "screen_fixed",
    "ignore_zoom": true,
    "selection_visibility": "selected_only"
  }
]
~~~

这是一份正交、固定在屏幕右下且不随炮镜倍率缩放的布局。常驻组件可改为 always；selected_only 表示仅选中所属 WC 时显示。位置和显隐由作者选择，示例数值不是强制规则。

### 宿主引用

在需要显示该组件的 WC 动态属性中合并以下字段，保留 WC 的 type、definition 和控制布线：

~~~json
{
  "first_hud_layout": "example:weapon_panel",
  "third_hud_layout": "example:weapon_panel",
  "scope_hud_layout": "example:weapon_panel"
}
~~~

这只是字段片段，不是完整 WC 定义。只配置任务需要的视角；省略的引用默认 machine_max:empty，即该视角不提供布局。

座位通过其视角配置提供仪表，相机或瞄具通过 scope_hud_layout 提供分划；不要为了显示武器数据而把所有页面合到座位上。修改现有车辆时逐项检查其实际布局引用，保留任务需要的座位仪表和分划。

## 3. 内部排布与屏幕投放

- 组件内部优先使用正常文档流、Flex 或 Grid。绝对定位项不会撑开父容器，需明确尺寸与溢出策略。
- 内容宽高参与锚点计算；外围 block 容器可能撑满视口。用明确组件宽度和可收缩的外层避免空白区域影响位置。
- CSS 像素、AUI 设计视口和 Minecraft GUI 坐标是不同层。正交 offset 使用 GUI 坐标；360px 的页面宽度不等于 360 个 GUI 像素。
- 示例的 1600px 设计宽度可调整。面板随视口缩放，而固定纵向 offset 不会同比变化，需要在多个 GUI scale 下检查组件之间的空隙。
- 各 WC 独立投放，没有跨组件自动避让。多武器、多管或长名称可能增大面板，不能用一张截图证明所有分辨率都合适。
- 普通视角和炮镜均先绘制透视 HUD，再绘制正交 HUD；正交读数覆盖透视模型和页面。两种投影之间隔离深度。同一投影内的跨 WC 覆盖顺序没有作者可依赖的约定，组件应各自安排位置。
- 使用运行环境可显示的字体。引入自定义字体、复杂效果或浏览器特有用法时，分别检查中文、透明背景、裁剪与游戏实际表现。
- 直接使用 data-mm-* 获取动态值，不每帧重建整段 HTML；无需把浏览器演示用的计时器或模拟数据绑定器带入内容包。

内置三种组件的 defaults 可使用这些变量；自定义页面需自行实现对应 CSS：

| 变量 | 默认值 | 用途 |
| --- | --- | --- |
| --phosphor | #a4ff80 | 文字、边框、填充颜色 |
| --panel-bg | rgba(6, 13, 4, 0.48) | 底板颜色 |
| --scanline-opacity | 0.18 | 扫描线浓度，0 关闭 |
| --panel-width | 430px | 组件宽度 |
| --stack-bottom | 0px | 组件底部透明留白；为同锚点下方的独立组件预留设计像素空间 |
| --identity-display | flex | 逐管/弹链组件名称栏，none 隐藏 |
| --belts-display | block | 弹链组件的弹链列表，none 隐藏 |
| --tube-columns | 1fr 1fr | 逐管组件列定义，1fr 为单列 |

同锚点组合示例：两台 WC 都用 bottom_right 和相同 offset，主炮样式设置 --stack-bottom 为 112px，机枪使用紧凑组件且留白为 0px。主炮的内容框包含底部透明留白，面板在机枪上方；留白与页面按相同比例缩放。这是作者按机枪尺寸预留的位置，不会自动测量另一台 WC。需检查长文字、组件高度与 GUI scale，避免留白不足。

## 4. 作者可执行的验证

先检查页面键、样式键、布局键和宿主字段是否逐段对应，JSON 是否有效、文件大小写与路径是否一致。包自带 schema 时可用编辑器校验；schema 不包含某个字段时，核对所安装版本的作者文档和实际支持情况，不通过编造字段解决问题。

在游戏中按该版本提供的资源/内容包重载方式加载，再验证：

| 场景 | 观察点 |
| --- | --- |
| 最小接线 | 乘坐目标座位并选择该 WC，能看到 weapon.name 和 weapon.ammo_text 的真实值 |
| 射击与装填 | 各管状态、进度和时间变化；空膛与就绪显示正确 |
| 切换供弹 | 选中弹链与膛内弹分别表达，进行中的装填不被页面误标到新选择 |
| 零库存 | 已就绪的管仍显示就绪，库存与膛内弹不相加 |
| 多 WC / 管数变化 | 各面板数据独立，列表增减与重排不串项 |
| 视角与缩放 | 第一/第三/炮镜按配置显示，多个 GUI scale 下没有重叠、越界或异常缩放 |
| 重载与进出 | 重载、上下车、重进世界后无重复面板或残留占位文字 |

调试时查看游戏日志中的 HUD 绑定诊断，定位到页面和 data-mm-* 表达式。出现 MOLANG_ERROR、未知查询、错误作用域或不支持的标记，应修正对应槽位；不要只用 CSS 隐藏错误。准备状态为零或文本为空时，也要先排除未挂载、没有发现管/弹链、或当前版本不支持。

浏览器预览可以检查静态结构和样式，但不会自行执行 data-mm-*；模拟数据演示也不能证明游戏接线成功。无法运行客户端时交付明确标注的待验收版本，不宣称已经通过实机验证。

保留静态对照时使用独立页面、样式与布局。比较时切换宿主引用，避免静态与动态面板同时占用相同位置；交付时列出具体需要切换的字段和资源键。
