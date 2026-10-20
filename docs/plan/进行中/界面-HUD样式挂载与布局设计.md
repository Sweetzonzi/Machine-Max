# HUD 样式、布局与元素设计

> 版本 3.6 · 2026-10-21
> **状态**：进行中。V1（模型载体的样式 / 布局 / 元素、座椅与相机/瞄具宿主、内容包迁移）已落地并实机验收。V2 的 AUI 载体已落地：`machine_max:aui_hud` 样式、`anchor` 投放、每个元素独立 Document、按布局顺序手动绘制；`data-mm-*` 绑定层与 `hud.*` 上下文未实现。V3 的武器控制器宿主字段及选中集合接入已实现；候选宿主并集、通用条目显隐、武器业务快照和查询尚未实现。V4（玩家覆盖与编辑页）未实现。`selection_visibility`、`weapon.selected` 与当前项 `tube.*` / `belt.*` 为待实现契约。

***

## 一、要解决的问题

### 1.1 现状

| 环节 | 现状 |
| --- | --- |
| HUD 样式 | `huds/*.json`（3 份：`example_hud`、`example_hud_2`、`gun_sight_hud`），由 `HudModule` 读入 `MMDynamicRes.HUD_STYLES`；`type` 字段判别载体，V1 只有 `machine_max:model_hud`。样式只描述模型、动画、纹理、染色、文本槽与 `scissor` |
| HUD 布局 | `hud_layouts/*.json`（4 份：`seat_dashboard`、`seat_dashboard_2`、`scope_gunner`、`scope_gunner_fixed`），由 `HudLayoutModule` 读入 `MMDynamicRes.HUD_LAYOUTS`；文件内容是裸元素数组，键为 `<命名空间>:<相对路径>` |
| 相机/瞄具宿主 | `CameraSubsystemStaticAttr.scope_hud_layout`（`SightSubsystemStaticAttr` 提供同名字段）：一个 `ResourceLocation` |
| 座椅宿主 | `SeatSubsystemStaticAttr.ViewAttr.first_hud_layout` / `third_hud_layout`：各一个 `ResourceLocation` |
| 武器控制器宿主 | 无（武器状态 HUD 由 `WeaponHudOverlay` 单独实现，V3 接入） |
| 玩家参与 | 无（V4） |

这套结构解决三件事：

1. **投放可复用**：`machine_max:seat_dashboard` 被 7 个座椅文件的第一/第三人称共 11 处引用，`seat_dashboard_2` 被 `dayun` 的两处引用；投放只在布局里写一次，样式文件只保留外观。
2. **宿主同形**：座椅与相机/瞄具的每个视角字段都是一个 `ResourceLocation`，视角由字段名决定；武器控制器在 V3 沿用同一形态。
3. **样式与行为分离**：`gun_sight_hud` 与 `scope_gunner_fixed` 共用同一份样式，差异只由布局里的 `scope_behavior` 表达。

### 1.2 目标

| 目标 | 落地判据 |
| --- | --- |
| 定义只说"长什么样" | `HudStyle` 里不存在投放字段；`scissor` 是 §14.3 记录的例外，V1 仍留在样式里 |
| 投放可复用、可命名 | `HudLayout` 是具名资源，一份布局可被多个宿主、多个视角引用 |
| 宿主引用同形 | 每个视角字段都是一个 `ResourceLocation`；视角由字段名决定 |
| 内容包默认与玩家覆盖同 codec | 布局文件与玩家文件的每视角值都是 `HudElement` 数组 |
| 两个载体对等 | 基岩模型（`model`/`animation`/`texture`/`texts`）与 AUI 页面（HTML/CSS + Molang 绑定）都是 `HudStyle` 的一个 `type` |
| 内容包能自带页面 | 见 §九 的内容包页面资源管线 |

### 1.3 非目标

- 世界空间 HUD（`WorldWindow` 形态的铭牌/显示屏）不在本文范围；
- 页面脚本（`<script>`）通道不在本文范围：它由 KubeJS 提供，而 KubeJS 不是本模组的必需依赖，未安装时脚本静默不执行。本文的数据通道只用 Molang 绑定（§八）。

***

## 二、术语与资源层级

```text
宿主 ──每个视角一个 RL──▶ HudLayout（有序 HudElement 数组）──style RL──▶ HudStyle
座椅/相机/瞄具/武器控制器        命名资源 hud_layouts/*.json        内联条目            命名资源 huds/*.json
```

| 术语 | 含义 |
| --- | --- |
| **样式（HudStyle）** | 一份 HUD 的内部：模型载体的 `model`/`animation`/`texture`/`color`/`alpha`/`scissor`/`texts`，或 AUI 载体的 `page` |
| **布局（HudLayout）** | 一份有序的 `HudElement` 数组；命名资源 `hud_layouts/*.json`；键为 `<命名空间>:<相对路径>`；不含视角分组 |
| **元素（HudElement）** | 布局中的一个条目：`style` RL + 完整投放值 + `selection_visibility`；内联，不单独注册。选中可见性字段待实现，模型与 AUI 通用 |
| **宿主（host）** | 声明"哪个视角用哪份布局"的子系统：座椅、相机/瞄具、武器控制器 |
| **视角字段（view field）** | `first_hud_layout` / `third_hud_layout` / `scope_hud_layout`；字段名决定视角 |
| **玩家覆盖** | 玩家本地按宿主键保存的每视角元素数组，优先于内容包预设（V4） |

### 2.1 与控制组预设的对照

控制组提供“具名资源 + 玩家可改”的现有形态，HUD 的玩家文件也按宿主键保存可编辑的布局数据：

| | 控制组（现状） | HUD（本文） |
| --- | --- | --- |
| 具名资源 | `ControlGroupSet` ← `control_groups/*.json`，`ControlGroupModule` 读入 `MMDynamicRes.CONTROL_GROUP_PRESETS` | `HudStyle` ← `huds/*.json`；`HudLayout` ← `hud_layouts/*.json` |
| 宿主引用 | `control_group_preset: ResourceLocation`（哨兵值表示不用预设） | `first_hud_layout` / `third_hud_layout` / `scope_hud_layout: ResourceLocation` |
| 包内 JSON | `ControlGroupSet.CODEC` | `HudLayout.CODEC`（元素数组）；玩家文件为每视角元素数组 |
| 玩家改过的副本 | `ControlGroupSetEditPayload` + `STREAM_CODEC` → 服务端 `setControlGroupSet(...)` | 不过网络：本机表现，写客户端文件 |
| 重置 | `restoreControlGroupPreset()` | 删除该视角键（该视角回到预设）或宿主键（整个宿主回到预设） |
| 编辑入口 | `VehicleControlScreen` + `PanelConfigEditor` | 同一屏提供"HUD 布局"页 |

***

## 三、HudStyle：只描述内部

### 3.1 模型载体

```jsonc
// huds/example_hud.json
{
  "type": "machine_max:model_hud",
  "model": "machine_max:example_hud.geo",
  "animation": "machine_max:example_hud",
  "texture": "machine_max:textures/hud/example_hud.png",
  "color": [255, 255, 255],
  "alpha": 255,
  "scissor": { "enable": false, "x": 0, "y": 0, "width": 0, "height": 0 },
  "texts": {
    "speed": {
      "key": "hud.machine_max.default_1.speed",
      "centered": true,
      "shadow": true,
      "scale": [2, 2, 2],
      "molang_args": ["math.floor((math.abs(global.get('vehicle_speed')??0.0)) * 3.6 / 100)"],
      "significand": 0
    }
  }
}
```

| 字段 | 说明 |
| --- | --- |
| `type` | 载体判据，`machine_max:model_hud` |
| `model` / `animation` / `texture` | 与现状同义 |
| `color` / `alpha` | 整体染色与透明度 |
| `scissor` | 裁剪矩形。它裁剪的是这份样式自身的绘制区；其 x/y 在现有实现中是相对屏幕中心的坐标，归属见 §14.3 |
| `texts` | 槽位表：键 = 模型里的骨骼/定位器名；每槽有本地化键、若干 Molang 参数、缩放、颜色、阴影、小数位 |

### 3.2 AUI 载体

```jsonc
// huds/speed_ui.json
{
  "type": "machine_max:aui_hud",
  "page": "machine_max/huds/speed_ui.html",
  "defaults": { "--mm-accent": "#7dd3fc" }
}
```

| 字段 | 说明 |
| --- | --- |
| `page` | 页面逻辑路径，经 §九 的内容包页面资源管线解析 |
| `defaults` | 定义级 CSS 变量，可选；页面自身也可用样式表 |

在本设计的 HUD 路径中，AUI 文档的宿主是 AUI 的 Overlay/Screen：`Client.drawOverlay` 订阅 `RenderGuiEvent.Post`，用 `GuiGraphics.pose()` 调 `Base.drawOverlayDocument(...)`；`Base.drawOverlayDocument` 只施加 2D 缩放与裁剪，不设置投影矩阵。因此 `machine_max:aui_hud` 固定在 GUI/HUD 正交阶段绘制，不能作为透视 HUD 使用。

AUI 另有一条世界内路径：`WorldWindow` 把 Document 放到世界坐标，由 `WorldWindowRenderer` 把关卡投影矩阵传给 `WorldWindow.render(...)`，并启用深度测试、视锥裁剪与世界遮挡。它确实能做透视投影，但语义是世界内面板，不是屏幕空间 HUD；本文的非目标已把它排除（§1.3），`HudElement.perspective` 也不选择这条路径。`WorldWindow` 默认只在创建它的客户端本地渲染，AUI 不提供窗口的网络同步；若本模组将来把载具面板同步给其他客户端并由各客户端重建窗口，它就属于世界可见内容，私人 HUD 只适用于未同步的本地窗口。

页面的结构与取值见 §八。

运行时实现：每个布局条目创建一个独立 `Document`，标记 `manuallyRendered`，AUI 的 overlay 批次跳过该标记的文档，由 `CustomHud` 在布局顺序里调用 `Base.drawOverlayDocument` 绘制；页面 `body` 加 `mm-hud` 类并注入按内容收缩、无外边距、透明背景的规则；位置由元素的 `anchor` / `offset` / `scale.x` 决定；页面随 `/reload` 刷新。

### 3.3 为什么"行为"不算内部

`scope_behavior` 与 `ignore_zoom` 描述的是"这份分划在炮镜里怎么动"，属于 `HudElement`，不属于 `HudStyle`。内容包中的 `gun_sight_hud.json` 与 `gun_sight_hud_fixed.json` 使用同一个模型、同一份动画与纹理，差异只有这两个字段；它们对应同一份 `HudStyle`，行为差异由引用该样式的不同布局表达。

***

## 四、HudElement：样式 + 投放 + 可见性

```jsonc
// hud_layouts/gunner_scope.json 中的一个条目（selection_visibility 为待实现字段）
{
  "style": "machine_max:gun_sight_hud",
  "selection_visibility": "selected_only",
  "perspective": false,
  "scope_behavior": "screen_fixed",
  "ignore_zoom": true,
  "offset": [0, 0, 0],
  "rotation": [-25, -30, 0],
  "scale": [25, 25, 25]
}
```

| 字段 | 模型载体 | AUI 载体 |
| --- | --- | --- |
| `style` | 必填：`HudStyle` 的 RL | 必填：`HudStyle` 的 RL |
| `selection_visibility` | 读：`selected_only`（缺省）或 `always`，仅 WC 宿主应用选中条件，见 §4.3；待实现 | 与模型相同，由公共调度判断，不依赖 CSS；待实现 |
| `perspective` | 读 | 不适用：AUI 页面固定在 GUI/HUD 正交阶段渲染；写 `true` 也按 `false` 处理 |
| `scope_behavior` / `ignore_zoom` | 读；仅当布局被宿主的 `scope` 字段引用时生效 | 忽略（提示警告） |
| `anchor` | 忽略 | 读：`top_left` … `bottom_right` / `center` |
| `offset` | 读三维（透视下米、正交下像素），相对屏幕中心 | 读二维逻辑像素（`z` 忽略） |
| `rotation` | 读（度） | 忽略：AUI 的旋转属于样式（CSS `transform`） |
| `scale` | 读三维 | 读单值（取 x） |

规则：

1. **一个元素就是一组完整投放值**，不做"部分覆盖"。需要另一种投放就在布局里再写一个元素。每项投放只有一个权威来源。
2. **没有 `z` / `order` 字段**。`offset.z` 是空间深度：透视下为摄像机前方距离（现有内容使用负值，如 `-135`），正交下为 GUI 深度平移；`FOLLOW_TRANSFORM` 的分划板距离也取 `offset.z`。`offset.z` 不参与绘制顺序；绘制顺序由布局数组决定（同投影模式内）。
3. `scope_behavior` / `ignore_zoom` 只在布局被 `scope` 视角字段引用时生效；被 `first_hud_layout` / `third_hud_layout` 引用时忽略，避免内容包把炮镜行为写进普通视角。
4. `anchor`、`rotation` 等载体专属字段按上表处理；不适用于当前载体的字段忽略。
5. **投影模式由载体决定，不由字段单独决定**：`perspective` 是模型载体专属字段；模型载体的元素按它进入透视或正交批次，AUI 载体的元素一律进入正交批次。布局可以同时包含两种载体，运行时先解析 `style` 的 `type`，再决定元素归入哪个批次。

### 4.1 为什么元素内联在布局里

- **复用单位是布局**：一份布局可以被多个座椅、多个视角引用；单个元素的投放跨布局复用是少数情况。
- **同一份样式可以出现多次**：一个布局里可以有多个元素引用同一个 `style`，各自带不同的 `offset`/`scale`。运行时容器按条目身份（布局 + 下标）管理，`style` RL 不是唯一键。
- **不引入第二层注册表**：元素不是具名资源；如果将来确实需要跨布局共享一个元素，再加显式引用（引用项是完整投放，不做部分覆盖），不在当前版本引入。

***

### 4.2 运行时元素身份

运行时元素的身份是**当前视角内、该宿主布局数组的序数**，不引入第二套元素键。同一份样式可在布局中出现多次，运行时各自管理。目标宿主集合由座椅或相机/瞄具，加上当前座椅全部控制组发现的候选 WC 并集构成，候选范围与观察者状态见 §6.1。

宿主、视角、实际布局或样式资源变化可触发重建；仅切换当前子组时，候选集合及布局不变，只更新选中状态和条目显隐，保留模型元素及 AUI Document。隐藏不等于从布局或候选集合注销，恢复显示前须刷新该条目数据。没有布局的候选宿主保留空列表，避免每 tick 把空布局判为宿主变化。

**当前代码与目标的边界**：`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick` 已比较宿主、视角、布局及样式，武器宿主只取当前组选中集合；候选并集、选中状态更新与条目显隐待实现。目标实现不因选中集合变化重建候选元素。

### 4.3 选中可见性（已定、待实现，模型与 AUI 通用）

`selection_visibility` 是布局条目字段，由公共 HUD 调度在载体绘制入口前判断；不是 `HudStyle` 字段，也不是 AUI 专属属性。

| 值 | WC 宿主条目的显示条件 | 普通座椅/相机宿主 |
| --- | --- | --- |
| `selected_only`（缺省） | 宿主在候选集合、当前视角有布局，且属于观察座椅 base + 当前子组选中集合 | 不附加选中条件 |
| `always` | 宿主在候选集合，当前视角有布局；未选中也可显示 | 不附加选中条件 |

普通座椅/相机条目的这项配置不产生“是否选中 WC”的要求。未知枚举值按布局配置错误上报。常驻限定在当前座椅及当前视角的有效布局期间，离开座椅或候选关系失效就不提交武器条目；它不表示跨座椅、跨视角或全局常驻。

同一 WC 的 `scope` 布局可以将 AUI 状态面板常驻，并将模型分划设为仅选中显示。以下沿用两份已有样式名示意规则，具体位置由内容包安排：

```json
[
  {
    "style": "machine_max:aui_weapon_status",
    "selection_visibility": "always",
    "perspective": false,
    "anchor": "bottom_right",
    "offset": [-8, -52, 0]
  },
  {
    "style": "machine_max:gun_sight_hud",
    "selection_visibility": "selected_only",
    "perspective": false,
    "scope_behavior": "screen_fixed",
    "ignore_zoom": true,
    "scale": [25, 25, 25]
  }
]
```

该条件必须覆盖模型/AUI、普通/炮镜以及各载体支持的正交/透视提交路径。隐藏条目不提交模型几何、文本或 AUI 文档，不要求页面具有脚本、CSS 隐藏类或 MoLang 绑定。选中切换只改变显隐与观察者查询，不重新创建页面；隐藏期间可跳过数据写回，但恢复绘制前须完成本轮刷新。

`weapon.selected` 是武器元素的观察者查询，可用于未选中常驻条目的暗化或细节折叠；它与 `selection_visibility` 的公共显隐判定读取同一轮选中状态。该值不等于控制器启用、正在开火或独占控制权。数据口径见《[武器控制器 HUD 词表与宿主接入设计](../计划中/武器系统-武器控制器HUD样式与布局设计.md)》§一。玩家覆盖使用相同字段及缺省值，重置按 §七回到内容包布局。

***

## 五、HudLayout：有序元素数组

```jsonc
// hud_layouts/seat_dashboard_fp.json
[
  {
    "style": "machine_max:example_hud_2",
    "perspective": true,
    "offset": [95, 55, -135],
    "rotation": [-25, -30, 0],
    "scale": [15, 15, 15]
  }
]
```

```jsonc
// hud_layouts/scope_gunner.json
[
  {
    "style": "machine_max:gun_sight_hud",
    "perspective": false,
    "scope_behavior": "follow_position",
    "ignore_zoom": true,
    "offset": [0, 0, 0],
    "scale": [25, 25, 25]
  }
]
```

```java
public record HudLayout(List<HudElement> elements) {
    public static final Codec<HudLayout> CODEC = ...;   // 文件是裸数组，codec 映射到 elements
}
```

规则：

1. **数组顺序是同一种投影模式内的提交顺序**，没有 `order`。透视元素之间、正交元素之间分别按数组顺序提交；透视与正交之间的先后由渲染批次决定，不作为布局契约。`offset.z` 只决定空间深度，不参与排序。归入哪个批次由 `style` 的载体类型决定：模型载体看 `perspective`，AUI 载体一律归正交（§四规则 5）。AUI 文档由 AUI 的平面文档层绘制（z 从 1.0 起），同一帧内位于模型元素之上；数组顺序决定 AUI 文档之间的提交顺序，不改变它们与模型元素的上下关系。透视批次共用一个 `MultiBufferSource` 与 `entityTranslucent`，同 buffer 的半透明四边形在上传时可能按深度重排，因此数组顺序不保证等于像素覆盖顺序；这是已接受的边界，验收只断言提交顺序。
2. **布局不含视角信息**。同一份布局可以被座椅的 `first_hud_layout` 与 `third_hud_layout` 同时引用，也可以被相机/瞄具的 `scope_hud_layout` 引用；视角由宿主的字段名决定。
3. **空数组 = 该视角不显示 HUD**；宿主字段缺失 = 该视角没有布局，同样不显示。
4. **布局是命名资源**：`hud_layouts/*.json` 由 `HudLayoutModule` 读入 `MMDynamicRes.HUD_LAYOUTS`，键为 `<命名空间>:<相对路径>`，与样式使用同一条拼键规则，不额外加 `layout/` 前缀。`hud_layouts/` 下的子目录参与拼键，因此 `hud_layouts/foo/bar.json` 的键是 `<命名空间>:foo/bar`，不同目录的同名文件不会撞键。布局与样式分属两张注册表，键同名互不影响。
5. **布局只描述"按顺序画哪些元素"**；它不知道被哪个宿主、哪个视角引用，也不知道玩家是否覆盖。
6. **跨宿主顺序不设契约**。同一视角可能同时有座椅/相机布局和多台武器控制器布局；`CustomHud` 按宿主集合的迭代顺序提交，这个顺序由候选宿主集合的遍历决定，内容包不能依赖它。内容包负责让同一视角的元素互不遮挡；如果将来需要显式交错，再在元素上加排序字段。

### 5.1 为什么布局是具名资源

`machine_max:seat_dashboard` 被 `default_seat`、`default_3rd_seat`、`vn`、`senna_gtr`、`golden_closestool`、`k17`、`sdkfz234` 七个座椅文件以第一/第三人称共 11 处引用，`machine_max:seat_dashboard_2` 被 `dayun` 的第一/第三人称引用。若投放内联在宿主里，同一组数要抄 13 遍，改一次仪表盘布局要改 8 个文件。具名布局让"一处定义、多处引用"成立：

- 多个座椅可以引用同一份 `machine_max:seat_dashboard`；
- 同一份布局可以被第一人称和第三人称同时引用；
- 某辆车需要不同投放时，写一份新的布局文件并在该车的子系统里引用它。

***

## 六、宿主：每个视角一个 RL

### 6.1 引用形态

| 宿主 | 字段 | 视角 | 玩家覆盖键（V4） |
| --- | --- | --- | --- |
| 相机/瞄具 | `CameraSubsystemStaticAttr.scope_hud_layout`（`SightSubsystemStaticAttr` 提供同名字段） | `scope` | `camera:<子系统名>` |
| 座椅 | `SeatSubsystemStaticAttr.ViewAttr.first_hud_layout` / `third_hud_layout` | `first` / `third`（`THIRD_PERSON_FRONT` 归 `third`） | `seat:<子系统名>` |
| 武器控制器 | 动态属性 `first_hud_layout` / `third_hud_layout` / `scope_hud_layout`（`WeaponControllerSubsystemAttr`） | `first` / `third` / `scope` | `wc:<子系统名>` |

每个字段是一个 `ResourceLocation`，指向一份 `HudLayout`。字段名决定视角，宿主不读取其它视角的字段。三类宿主的字段都已按此形态落地；武器控制器的内容包示例待补。目标 `CustomHud` 每个客户端 tick 协调当前视角的候选宿主集合（座椅或相机/瞄具，加上座椅全部控制组发现的 WC 并集）和独立选中状态，宿主/视角/布局/样式变化触发重建，纯选中切换保留元素；随后向宿主取实际布局（`hudLayoutFor`），宿主内部用 `IHudHost.resolve` 把内容包 RL 解析成对象，缺失时提示一次。没有布局时按空布局处理，样式 RL 查不到时跳过该条目并提示一次。没有布局的宿主保留一个空列表，使宿主集合与键集合一致。

```jsonc
// 座椅
"views": {
  "first_hud_layout": "machine_max:seat_dashboard",
  "third_hud_layout": "machine_max:seat_dashboard",
  "enable_first_person": true,
  ...
}
```

```jsonc
// 相机/炮镜
"scope_hud_layout": "machine_max:scope_gunner"
```

```jsonc
// 武器控制器
{
  "type": "machine_max:weapon_controller",
  "definition": "machine_max:basic_weapon_controller",
  "control_outputs": { "weapon_control": ["subsystem.machine_max.main_launcher"] },
  "first_hud_layout": "machine_max:wc_gunner_first",
  "third_hud_layout": "machine_max:wc_gunner_third",
  "scope_hud_layout": "machine_max:wc_gunner_scope"
}
```

字段缺失表示该视角没有 HUD。武器控制器可以使用三个视角字段；座椅只有第一/第三人称；相机/瞄具只有炮镜。座椅没有 `scope_hud_layout`：炮镜态下要显示的基础元素由该炮镜自己的 `scope_hud_layout` 提供，武器分划与弹药由武器控制器的 `scope_hud_layout` 提供。

武器控制器的 HUD 不由座椅聚合弹药数据。目标实现从当前座椅所有控制组发现的 WC 并集建立候选宿主；数据源是 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractControllableSubsystem.java#weaponControllersByGroup`。按 base、控制组配置顺序遍历并按控制器实例去重，不直接依赖并发 Map 的迭代顺序；不扫描整辆装配体上未被该座椅发现的武器。选中状态由同一文件的 `#getActiveWeaponControllers` 提供，即 base + 当前子组并集；`#weaponControllerDiscoveryHandshake` 在控制组配置或结构变化时刷新缓存，纯切组无需重新发现。

候选范围不按 `isActive` / `isDestroyed` 过滤，不区分主/副、不设焦点组。每个候选 WC 的当前视角布局按 §4.3 的 `selection_visibility` 判定条目显隐；未选中并不必然隐藏。同一控制器对不同座椅的选中结果可以不同，选中值属于观察者求值帧，不写入控制器全局状态或共享业务快照。没有观察座椅时不产生这组武器候选宿主。座椅只提供发现/选择关系，各 WC 独立取自己的弹药数据，多台布局重叠由作者处理。

### 6.2 数据上下文由持有者决定

每个被引用的布局条目在运行时生成自己的 HUD 元素，元素缓存自己的 MoLang 上下文。所有元素可读 `hud.*`（当前渲染视角与视口）；武器控制器元素继承通用元素，并提供整台控制器的 `weapon.*` 与显式安装当前项后可读的 `tube.*` / `belt.*`。`math.*` 属于 Spark 通用词表；载具 HP、能量与信号等继续从 `local.*` / `global.*` 读取，HUD 元素通过所属 Part/装配体关联它们。类层次和查询口径见《[观瞄系统-HUD专用MoLang上下文设计](../计划中/观瞄系统-HUD专用MoLang上下文设计.md)》。V1 尚未实现元素级上下文，模型元素继续沿用座椅 Part 的上下文，边界见 §8.1。

| 持有者 | 自有数据与炮镜职责 |
| --- | --- |
| 座椅 | 座椅布局只产生自己的仪表等元素；没有 `weapon.*` |
| 相机/瞄具 | `scope` 视角下产生边框、遮罩等基础元素；没有 `weapon.*` |
| 武器控制器 | 直接绑定这台控制器；弹药、装填与弹道分划都在自己的元素中，`scope_hud_layout` 可挂炮镜分划 |

相机/瞄具无需反查武器；座椅只提供候选并集和当前组选中关系，不替 WC 聚合弹药数据。一个相机/瞄具边框和多台控制器的 HUD 可以同时处于 `scope` 视角；各元素只读取自己的上下文。多台控制器的分划分别按条目可见性提交，数据各取自己的控制器，重叠由内容包布局处理。

### 6.3 视角三态互斥

`scope` 态强制第一人称（`CameraController.tickCameraMode` 里做视角强制），`CustomHud.tick()` 也是"先判是否相机模式，否则按 `options.getCameraType()` 选 `first_hud_layout` 或 `third_hud_layout`"。因此运行期只会是 `first` / `third` / `scope` 之一，宿主各自只读取对应字段，不需要"叠加显示条件"的位掩码。

***

## 七、玩家覆盖

- **解析顺序（按视角）**：玩家覆盖中该视角的元素数组 > 宿主该视角引用的 `HudLayout` > 空（该视角无 HUD）。
- **存储**：客户端文件 `config/machine_max/hud_layouts.json`，键为 §6.1 的覆盖键。值按视角保存元素数组，视角键名使用 `first` / `third` / `scope`：

```jsonc
{
  "seat:dayun_seat": {
    "first": [ ... ],
    "third": [ ... ]
  },
  "camera:gunner_sight": {
    "scope": [ ... ]
  },
  "wc:main_gun": {
    "first": [ ... ],
    "third": [ ... ],
    "scope": [ ... ]
  }
}
```

- **缺失与空数组**：缺失的视角键 = 使用预设；存在但为 `[]` = 该视角明确不显示 HUD。

| 玩家文件中的视角键 | 宿主字段 | 结果 |
| --- | --- | --- |
| 缺失 | 有 RL | 使用预设布局 |
| 缺失 | 缺失 | 该视角不显示 |
| `[]` | 任意 | 该视角不显示 |
| 有元素数组 | 任意 | 使用玩家数组 |

这样改 `first` 不必把 `third` 抄进玩家文件，也不会把"没覆盖"和"不显示"混为一谈。

- **解码失败**：某个视角的数组解码失败时，记录一次可定位诊断并按预设处理，不影响其它视角与其它宿主。

- **重置**：删除该视角键 = 该视角回到预设（没有预设时不显示）；删除宿主键 = 整个宿主回到预设。
- **编辑入口**：`VehicleControlScreen`（AUI Screen，本身接收鼠标，已有 3D 预览拖拽代码）提供"HUD 布局"页：拖拽调整元素位置、增删元素、切换元素用的样式、保存/重置。位置编辑直接改 `HudElement.offset`/`scale`；保存时写入该视角的完整元素数组。
- **叠层不拦截输入**的契约继续保留：显示态的 HUD 文档不声明 `<meta name="aui-mouse-events">`，编辑只在 Screen 内进行。
- **v1 只做单一覆盖**：一个宿主一条覆盖项。玩家自定义的多套命名布局留到有需求时再加；存储格式已能表达每视角的元素级位置。
- **覆盖键粒度**：`seat:<子系统名>` 按子系统而不是按载具区分。两台车共用同一个子系统定义时会共享同一份玩家覆盖；如果要求按车区分，键需要带载具/部件身份（§14.1）。

***

## 八、数据上下文与 AUI 绑定层

### 8.1 上下文与编译

Spark 的 `MolangContextRegistry` 按“上下文类、表达式”缓存编译结果，并用上下文类的无参构造创建编译原型。通用 HUD 元素实现 `IAnimatable` 并持有 `HudMolangContext`；武器 HUD 元素继承通用元素并持有 `WeaponHudMolangContext`。后者继承前者的 `hud.*` 查询并增加 `weapon.*`。**上下文按元素隔离**，相同类型元素可共享编译结果；物理线程与客户端主线程分别缓存求值实例，读取同一不可变快照，避免并发修改 `reset` 状态。现有 `MechMolangContext` 绑定 `IAnimatable<Part>`，不能直接把 HUD 元素传给它的 `reset`；`local.*` / `global.*` 查询需按通用上下文文档显式转发。

模型载体的动画与文本槽使用元素的 `getMolangContext()`；AUI 页面绑定层给该元素的页面区域关联同一上下文。一个 AUI `Document` 可以包含多个元素区域，内部按运行时元素路由求值；内容包作者写单元素样式时不必声明“查哪台控制器”。武器元素从自己持有的控制器构造快照，不经座椅选控制器。

**V1 的上下文边界**：模型 HUD 元素由 `GuiAnimatable` 委派到“玩家所坐座椅所属 Part”的 `SparkMolangContext`，`local.*` / `global.*` 的查询对象分别是该 Part 与其所属装配体；相机/瞄具宿主下的 `local.*` 读到的是座椅所在部件。`hud.*` 与按宿主解析 Part 属于 V2 的 `HudMolangContext`，V1 的样式只使用静态文本或现有上下文能求值的表达式。

| API | 作用 |
| --- | --- |
| `MolangContextRegistry.compile(expr, ctx)` | 编译数值表达式，按上下文类和表达式复用 |
| `MolangContextRegistry.compileString(expr, ctx)` | 编译字符串表达式 |
| `MolangExpression.evaluate(ctx)` | 在对应元素的上下文实例上求值 |
| `@QueryBinding` / `@StringQueryBinding` | 声明数值与字符串词表 |

### 8.2 页面数据标记

下表是**拟由 Machine-Max 实现**的 AUI 绑定层属性，不是 AUI 自带的 HTML 或 MoLang 语法。它让内容包作者在页面元素上写表达式，不要求编写 JavaScript。

| 属性 | 作用 |
| --- | --- |
| `data-mm-text` | 将表达式结果写入叶子元素文本 |
| `data-mm-num`、`data-mm-var`、`data-mm-unit` | 将数值与单位写入指定 CSS 变量 |
| `data-mm-class` | 将状态映射为 `is-<值>` 类名 |
| `data-mm-repeat` | 集合标识；绑定层按集合条目实例化标记所在元素及其完整子树，规则见 §8.5 |
| `data-mm-index` | 可选下标变量别名；在重复项及其后代提供 `variable.<名字>`，从 0 开始；默认使用 `tube.index` / `belt.index` |

四根发射管的页面只需定义一份条目模板，绑定层按身份生成四项，为各数据槽安装对应管的当前项作用域；同一条 `tube.fill` 表达式分别读取四根管。**`data-mm-*` 绑定层尚未实现**；本文的语法和示例是实现契约，不代表当前页面已能执行绑定。模型载体的骨骼槽位固定，可在确定管数的样式中显式写下标。

**实现载体（已定）**：绑定层由本模组实现，`data-mm-*` 由绑定层扫描页面属性识别；AUI 不承担武器集合和重复标记的解析。模板实例化使用的 AUI 节点能力与前置验证见 §8.6。依据三条：

1. **生命周期**：页面加载后一次扫描建立槽位；运行期节点增删由 `Document.createMutationObserver(...)` 增量绑定与释放——`observe(root, childList=true, attributes=true, subtree=true, …)` 覆盖新增子树与标记改写。观察者记录创建时的刷新代数，`refresh()` 换代后自动失效，无须手动摘除。
2. **故障隔离**：绑定层要求“一个槽位求值失败只降级该槽位”（§8.4 末段）。AUI 的逐 tick 元素遍历没有异常隔离（§11.3），把求值挂进元素自身的 `tick()`，一次异常就会中断该文档的全部元素。
3. **不占全局命名空间**：`Element.register` 是全局同名覆盖，最后写入者赢，无注销也无冲突诊断（§11.3）。覆盖 `span` 会波及 AUI 自身 UI 与其它模组的页面。

**已评估、不采用的两条路线**（留档以免重复讨论）：

| 路线 | 机制可行性 | 不采用的理由 |
| --- | --- | --- |
| 新增自定义标签（页面写 `<mm-text expr>`，免扫描） | 可行，但须直接调用 `Element.register`：`@ElementRegister` 的扫描按已登记包前缀过滤，下游模组标了也不会生效 | AUI 的默认 display 是硬编码标签白名单，未列出的标签一律 `block`，写在行内文本里会换行，每页都要补 `display:inline`。将来若确实需要独立标签，应选白名单内且无人注册的标签（`<data>`），而不是新造名字 |
| 注册子类覆盖内置标签（页面写 `<span data-mm-text>`，免扫描） | 可行：`Element.REGISTRY` 是普通 `HashMap`，`register` 直接覆盖；解析期由 `Element.init` 把基础 `Element` 换成注册类，且属性在替换前写入，`onInitFromDom` 能读到标记 | 第 2、3 条依据。另有两处覆盖不到：带 `contenteditable` 的元素会被强制换成 `RichText`；`Element.init` 只在原实例恰为基础类 `Element` 时替换 |

两条被否决的路线都不改变 §8.3 的求值时机与“仅变化时写入”要求。

### 8.3 扫描与求值时机

| 时机 | 动作 |
| --- | --- |
| 页面加载或热重载 | 按运行时元素关联上下文，扫描模板和数据标记，建立重复区域与槽位；在本页面内容测量和绘制前完成首次展开；表达式可命中编译缓存 |
| 运行期节点增删 | `MutationObserver` 对外部加入的子树增量绑定，对离树子树释放槽位；绑定器直接登记自己生成的重复项，观察者须识别这些已登记节点 |
| 武器结构变化 | 对受影响集合按身份键增删、复用和排序节点，更新槽位所用的快照下标；同样数量的成员更换也须识别 |
| 每客户端 tick | 刷新武器快照与有效布局，原子发布不可变值；对 AUI 数据槽求值，只在结果变化时写入 |
| 模型动画求值前 | 当前线程的上下文读取已发布快照，再计算消费 `hud.*` 的骨骼表达式 |
| 模型文本渲染前 | 客户端主线程上下文读取本次渲染的参数，求值 `molang_args` |

文本必须写到叶子元素，且只有值变化才改 `innerText`；类名 token 与 CSS 变量也只在变化时改写。`setAttribute` / `setInnerHTML` 不进逐 tick 路径，避免无谓的样式与布局重算。槽位缓存随元素或页面区域销毁而释放。

### 8.4 词表归属

- `hud.*`：通用 HUD 元素的当前相机视角与视口；座椅、相机/瞄具、武器元素均可读。见《[观瞄系统-HUD专用MoLang上下文设计](../计划中/观瞄系统-HUD专用MoLang上下文设计.md)》。
- `weapon.*`：仅武器控制器元素可读，包含整台控制器的弹药、显式索引查询、分划弹道角及观察者选中状态；在重复项内含义一致。见《[武器系统-武器控制器HUD样式与布局设计](../计划中/武器系统-武器控制器HUD样式与布局设计.md)》§三。
- `tube.*` / `belt.*`：仅在相应当前项类型作用域内有效，默认供发射管/弹链重复项使用；区域外或类型不匹配时诊断并使对应槽位降级，不隐式取第零项。词表见武器文档 §3.2，Java 求值注入见通用上下文文档 §3.3。
- `math.*`：Spark 通用查询；`local.*` / `global.*`：Part 与装配体查询，持续用于载具仪表。这三者不形成新的 HUD 业务前缀。

MoLang 不提供可靠的字符串拼接；`weapon.ammo_text` 等组合文本由查询返回，或由模型文本槽的本地化键和 `molang_args` 组装。普通元素使用 `weapon.*`、表达式语法错误或求值失败时，绑定层应标明出错槽位并记录一次可定位诊断，其他槽位继续刷新。

### 8.5 动态列表与内部排布（已定、待实现）

内容包作者定义一项的 HTML/CSS，绑定层根据集合实例化这份模板。Java 业务代码提供数据与身份键，通用绑定器维护节点；武器业务代码不硬编码标签树、类名或排版。重复项是同一 Document 内的 DOM 子树，不各自创建 HUD 元素、Document、控制器或发射器。

首批集合是 `weapon.tubes`（有效发射管）和 `weapon.belts`（可选弹链）。集合口径、身份与排序见《[武器控制器 HUD 词表与宿主接入设计](../计划中/武器系统-武器控制器HUD样式与布局设计.md)》§二、§4.1。`data-mm-repeat` 的值是绑定器解析的集合标识，不作为 MoLang 表达式求值，不要求 MoLang 返回数组。

属性与作用域契约：

1. `data-mm-repeat` 写在条目根上，根及完整子树共同构成模板，父容器只保留一份；模板原件不作为数据项显示，空集合生成零项。
2. 绑定器为 `weapon.tubes` 项安装 `tube.*` 作用域，为 `weapon.belts` 项安装 `belt.*` 作用域，覆盖条目根和后代全部数据槽。`tube.index` / `belt.index` 是本轮快照下标，从 0 开始；Java 无参查询读取当前项，不根据调用次数或 DOM 位置推断序号。
3. `data-mm-index` 可选。声明时须匹配 `[a-z_][a-z0-9_]*`，例如 `row` 提供 `variable.row` / `v.row`，值与当前项的 `.index` 相同；该变量为只读保留名。每次数据槽求值前安装作用域，结束或异常时恢复原值或未定义状态，不写入 Part/动画体共享变量。表达式检查同时识别 `variable.*` 和 `v.*` 的赋值。
4. 同级重复区域独立绑定身份与类型。首版不支持嵌套 `data-mm-repeat`；遇到嵌套须诊断并停止受影响区域展开，不静默借用外层作用域。区域外使用当前项查询、管区域访问 `belt.*` 或弹链区域访问 `tube.*`，均须按页面/模板/槽位诊断，不默认第零项。
5. 首版模板根及后代不允许固定 `id`；使用 class 和数据标记定位，避免重复 ID。未知集合、非法下标别名或固定 ID 均按区域诊断，其他区域继续刷新；省略 `data-mm-index` 是合法写法。
6. 身份键由集合提供者定义，作者无需声明 `data-mm-key`。`tube.*` / `belt.*` 是固定类型查询，不是 `data-mm-as` 或任意对象别名；任意计数表达式、分页/过滤属性和作者自定义集合不属于首版语法。
7. `weapon.*` 始终读取所属整台控制器。显式索引接口 `weapon.tube_fill(i)` / `weapon.belt_label(i)` 用于非重复页面、跨项比较及模型 HUD；不能把省略实参视为当前项，编译器边界见通用上下文文档 §3.3。

普通列表由 CSS 排布。以下每项高 20px、间距 6px，相邻起点相差 26px：

```html
<style>
.tubes { display: flex; flex-direction: column; gap: 6px; }
.tube { height: 20px; flex-shrink: 0; }
</style>
<div class="tubes">
  <div class="tube" data-mm-repeat="weapon.tubes" data-mm-class="tube.state">
    <span data-mm-text="tube.reload_text"></span>
    <i data-mm-num="tube.fill" data-mm-var="--fill" data-mm-unit="%"
       style="display: block; height: 2px; width: var(--fill); background-color: white;"></i>
  </div>
</div>
```

横向标签使用 `flex-direction: row`；每行四项可使用 `display: grid; grid-template-columns: repeat(4, 24px); gap: 6px`，第五项由 Grid 排到下一行。弹链列表例如：

```html
<div class="belts" style="display: flex; flex-direction: row; gap: 8px;">
  <div class="belt" data-mm-repeat="weapon.belts">
    <span data-mm-text="belt.label"></span>
    <b data-mm-text="belt.remaining"></b>
  </div>
</div>
```

下标可用于计算 CSS 变量。以下纵坐标为 0px、28px、56px；绝对定位项不撑开父容器，160px 高度由作者指定，实际样式须安排足够的尺寸或其他溢出处理：

```html
<style>
.custom-tubes { position: relative; height: 160px; }
.custom-tube { position: absolute; left: 0; top: var(--row-y); }
</style>
<div class="custom-tubes">
  <div class="custom-tube" data-mm-repeat="weapon.tubes"
       data-mm-num="tube.index * 28" data-mm-var="--row-y" data-mm-unit="px">
    <span data-mm-text="tube.reload_text"></span>
  </div>
</div>
```

需要变量别名时可以显式声明，和默认无参查询同时使用：

```html
<div data-mm-repeat="weapon.tubes" data-mm-index="row">
  <span data-mm-text="weapon.tube_reload_text(variable.row)"></span>
  <b data-mm-text="tube.chambered_ammo_label"></b>
</div>
```

`HudElement.offset`、`anchor`、`scale` 投放整块页面；内部方向、间距、换行与偏移由 CSS 处理，不另设重复器的 `offset_x` / `offset_y` / `step`。模型固定骨骼槽位不参与 DOM 重复机制，缺少显式成员绑定时使用索引查询。

### 8.6 重复区域生命周期与源码依据

一次列表刷新固定元素求值帧，武器业务数据与观察者选中状态均来自同一轮已发布快照；身份、顺序、下标及各槽位取值不可在本轮中切换。绑定器为槽位保存集合类型和稳定身份键，不永久缓存节点创建时的下标或某一轮的成员快照。绑定器持有独立模板原型或结构快照，按每项身份维护运行时子树，不从已更新的某一行提取下一行的模板。

运行步骤：

1. 在客户端主线程扫描模板，用开始/结束注释节点标记原位置，把模板原件保存到绑定器。通过 `Node` 插入接口维护边界内的实例，不插入额外布局容器。
2. 按集合身份键与上一份快照比较；离开集合的项释放节点及槽位，加入的项实例化模板，保留的项复用节点。集合顺序变化时同步调整 DOM 顺序和下标；不能只比较条目数。
3. 各行共享按“上下文类、原表达式”缓存的编译结果。每个数据槽求值先按身份从固定快照解析当前项与下标，再在本元素、本消费线程的上下文安装类型作用域及可选下标别名；结束或异常时恢复。`tube.fill` 的编译结果可复用于所有管，数据来自本次上下文，不为每行生成带常量下标的表达式，不共享全局当前项或调用计数器。Java 示意见通用上下文文档 §3.3。
4. 普通状态、进度与选中态变化只更新变化的数据槽；不重建节点，不逐 tick 写 `innerHTML`。绑定器生成的行直接登记作用域和槽位，观察者对同一节点的通知不得再次注册模板或重复绑定。
5. 检查 `Document#getRefreshGeneration`：换代时释放旧模板、节点句柄、作用域和观察器，在该页面下一次内容测量与绘制前重新扫描、展开和绑定；Document 销毁时释放全部关联状态。

下表基于 2026-10-20 核对的本地 AUI 与 Spark-Core 源码，均为活引用：

| 源码坐标 | 能力与约束 |
| --- | --- |
| `../AUI/common/src/main/java/com/sighs/apricityui/parser/HTML.java#buildDocument`、`../AUI/common/src/main/java/com/sighs/apricityui/parser/HTML.java#applyAttributesFast` | HTML 属性写入节点后由 `Element.init` 初始化；解析器没有为浏览器 `HTMLTemplateElement.content` 提供专门语义，因此页面使用普通元素上的数据标记 |
| `../AUI/common/src/main/java/com/sighs/apricityui/init/Element.java#cloneNode` | 深克隆复制属性、子树和原 ID；固定 ID 须按 §8.5 的规则诊断 |
| `../AUI/common/src/main/java/com/sighs/apricityui/init/Element.java#runInitFromDomOnce`、`../AUI/common/src/main/java/com/sighs/apricityui/element/Canvas.java#onInitFromDom` | 初始化钩子只运行一次，Canvas 从属性同步尺寸；现有 `cloneNode` 在复制属性前调用 `Element.init`，属性依赖型标签与行内事件的克隆须专项验证 |
| `../AUI/common/src/main/java/com/sighs/apricityui/init/Node.java#insertBefore`、`../AUI/common/src/main/java/com/sighs/apricityui/init/Document.java#createComment` | 支持注释边界和按边界插入重复项；调用接受 `Node` 的接口以容纳注释节点 |
| `../AUI/common/src/main/java/com/sighs/apricityui/init/Document.java#getRefreshGeneration`、`../AUI/common/src/main/java/com/sighs/apricityui/init/Document.java#createMutationObserver` | 可检测页面换代；观察者与创建时的代数绑定，刷新时清理 |
| `../AUI/common/src/main/java/com/sighs/apricityui/layout/Flex.java#resolveMainAxisGap`、`../AUI/common/src/main/java/com/sighs/apricityui/layout/Grid.java#resolveAssignedSize`、`../AUI/common/src/main/java/com/sighs/apricityui/style/Style.java#resolveVarReferences` | 提供 Flex 间距、Grid 排布与 CSS 变量解析；逐项位置属于 CSS |
| `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangContext.java#getVariableStorage`、`../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/runtime/MolangCompilingVisitor.java#visitAccess` | 编译后的 `variable.*` 从本次上下文读取；索引可作为求值输入，不必改变编译表达式 |
| `../Spark-Core/src/main/java/cn/solarmoon/spark_core/molang/MolangContextRegistry.java#compile` | 按上下文类与表达式共享编译结果 |
| `src/main/java/io/github/sweetzonzi/machine_max/client/render/renderable/AuiHudAnimatable.java#prepareDocument` | 当前内容测量入口；实现绑定层时须在测量、绘制之前完成首次展开与换代重绑 |

**实现前置项**：模板实例化须保证属性及必要子树就绪后才执行一次性 DOM 初始化。实现可以在 Machine-Max 内按 AUI 解析顺序建立通用模板实例；若直接采用 AUI 的 `cloneNode`，须先修复其初始化顺序并验证特殊标签。两条路径都必须通过普通文本/图片、Canvas 尺寸和行内事件属性的初始化用例，不能仅凭普通 `div/span` 克隆通过就宣布任意模板可用。该技术选型不改变 §8.5 的页面语法。

动态列表验收至少覆盖：0/1/多项、发射管和弹链两个区域同时存在、数量相同但身份更换、排序变化、耗尽弹链保留、进度变化期间节点身份保持、默认无参查询与可选下标别名一致、当前项及变量在其他行/其他 HUD/其他线程中不泄漏、异常后恢复、无作用域和类型不匹配诊断、模型显式索引不依赖 DOM、非法模板的区域级诊断、资源刷新后重新绑定、Flex/Grid 排布以及按下标写 CSS 变量的定位。

***

## 九、内容包 AUI 页面与资源注入

AUI 的资源解析链是：开发期项目根 → `<gamedir>/apricity/<path>` → 类路径 `assets/apricityui/apricity/<path>` → MC 资源管理器（资源包）。内容包模块（`spark_modules/`）的文件由 Spark-Core 读进内存，不在上述任何一条链上。

AUI 没有逐资源的“注册一个 CSS/JS/图片资源”API；可以整体替换资源服务（`AuiServices.setResources`），但 HTML 模板仍要 `HTML.putTemple(String, String)`。CSS、JS、图片、字体、音频都走 `ClientLoader.getResourceStream`，因此通用注入点是**客户端资源包**，而不是 AUI 内部缓存。

推荐用 Spark-Core 已有的虚拟客户端资源包 `SparkPackLoaderApplier.CLIENT_PACK`：

```java
SparkPackLoaderApplier.CLIENT_PACK.put(
        PackType.CLIENT_RESOURCES,
        ResourceLocation.fromNamespaceAndPath("apricityui", "apricity/" + pageKey),
        bytes);
```

这条管线不绑定 HUD：HUD、载具手册、PDA、研究页都使用同一个 `<namespace>/<path>` 页面键。`HudStyle.page` 只是其中一个消费者字段。

落地要点：

| 项 | 要求 |
| --- | --- |
| 命名空间种子 | 资源包注册前写入 `apricityui:apricity/.keep`，让 `CLIENT_PACK` 在 `MultiPackResourceManager` 构造时具有 `apricityui` 命名空间；否则之后通过 `/reload` 新增的页面不会被列出 |
| 注入时机 | 客户端读取本地内容包时，在 `DocumentModule.read` 中直接写入 `CLIENT_PACK`；启动阶段在资源包构造前，`/reload` 阶段在 AUI 资源重载前 |
| 页面模板 | `DocumentModule.onFinish` 在客户端调用一次 `HTML.scan()` 作为保险；AUI 的资源扫描/自动重载仍负责常规发现 |
| 缓存失效 | 不需要本模组手动失效；AUI 的资源重载处理自己的静态清单 |
| 资源范围 | HTML/CSS/JS/`<img>`/`@font-face`/`<audio>` 全部走资源管理器；`<canvas>` 的 `drawImage` 直接走 `Loader`，不在覆盖范围 |
| 优先级 | AUI 资源扫描的顺序是资源包 → `<gamedir>/apricity` → 开发目录；本地覆盖文件优先 |
| 路径 | 页面键为 `<内容包命名空间>/<path>`，例如 `machine_max/hud/speed.html`、`machine_max/manual/tank.html`；`HudStyle.page` 与键相同 |
| 路径规范化 | 处理前导 `/`、`..`、反斜杠；拒绝逃出页面根与空段，避免把内容包当成任意文件读取通道 |
| 安全边界 | 文档来自客户端本地内容包，安装者即信任来源；脚本仍由 KubeJS 执行，CSS/图片仍可引用远程 URL |

`DocumentModule` 使用 `ReadMode.LOCAL_ONLY`：客户端读取本地 `spark_modules`，服务端不向客户端发送文档。文档属于客户端表现资源，不参与游戏性同步；客户端调试页面也可以放在 `<gamedir>/apricity/` 或模组 JAR 的 `assets/apricityui/apricity/`。

备选方案是 `AuiServices.setResources(AuiResourceService)` 包装器 + `HTML.putTemple`：不依赖资源包命名空间，但要实现六个委托方法并额外维护模板注入。两种方案的比较、命名空间种子细节与验证计划见 [内容包 AUI 页面加载可行性调查](../计划中/界面-内容包AUI页面加载可行性调查.md)。

这条管线同时服务两个载体：模型载体的模型/动画/纹理由内容包提供，AUI 载体也能自带页面。`hud_layouts/*.json` 由 Spark 内容包模块读取，不经过这条资源管线。

页面来源有两条：模组 JAR 的 `assets/apricityui/apricity/machine_max/...`，以及内容包 `documents/` 经 `DocumentModule` 写入 `CLIENT_PACK` 的页面。`machine_max.builtin` 的 `documents/weapon_hud_element/index.html` 是内容包页面的现成实例。HUD 样式引用页面（`machine_max:aui_hud`）与 `data-mm-*` 绑定层属于 V2。

***

## 十、迁移面

### 10.1 内容包

V1 的内容包迁移已完成，当前形态：

| 对象 | 当前形态 |
| --- | --- |
| `huds/example_hud.json`、`example_hud_2.json` | 各一份样式，只含 `type`/`model`/`animation`/`texture`/`color`/`alpha`/`texts`/`scissor`；投放分别在 `hud_layouts/seat_dashboard.json` 与 `hud_layouts/seat_dashboard_2.json` |
| `huds/gun_sight_hud.json` | 一份样式；两个布局 `hud_layouts/scope_gunner.json`（`follow_position`）与 `hud_layouts/scope_gunner_fixed.json`（`screen_fixed`）共用它 |
| `subsystems/*/*_seat.json` | `views.first_hud_layout` / `third_hud_layout` 各一个 RL；空列表的视角直接省略字段 |
| 相机/瞄具子系统 | `scope_hud_layout` 一个 RL；没有布局时省略字段 |
| 武器控制器 | 动态属性已有 `first_hud_layout` / `third_hud_layout` / `scope_hud_layout`，默认省略即无 HUD；内容包尚无使用这些字段的示例 |
| `docs/{zh_cn,en_us}/schemas/**` 与 `hud.md` | 座椅、相机/瞄具的 schema 已含对应视角字段；武器控制器动态属性的 schema 尚未补三个 HUD 字段；`hud.md` 按"样式 / 布局 / 元素"三部分组织 |

当前代码只读取新字段：`first_person_hud`、`third_person_hud`、`hud_components` 没有对应的解码字段，写进 JSON 会被忽略。

### 10.2 代码

| 文件 | 当前实现 |
| --- | --- |
| `common/visual/HudView.java` | 视角枚举：`FIRST` / `THIRD` / `SCOPE` |
| `common/visual/IHudHost.java` | 宿主接口：`hudLayoutFor(HudView)` 返回实际布局对象，`null` 表示无 HUD；静态 `resolve(ResourceLocation)` 供持有内容包 RL 的宿主把键解析成对象，缺失时提示一次 |
| `common/visual/HudStyle.java` | 样式接口：`type` 分派（`model_hud` / `aui_hud`）、`ScissorParams`、`TextParams` |
| `common/visual/ModelHudStyle.java` | 模型载体样式：`model`/`animation`/`texture`/`color`/`alpha`/`scissor`/`texts` |
| `common/visual/AuiHudStyle.java` | AUI 载体样式：`page` 页面键与 `defaults` CSS 变量 |
| `common/visual/HudAnchor.java` | 九宫格锚点枚举，`anchor` 字段的序列化取值 |
| `common/visual/HudElement.java` | `style` RL + 投放值；`perspective`、`scope_behavior`、`ignore_zoom`、`offset`、`rotation`、`scale`、`anchor` |
| `common/visual/HudLayout.java` | 裸元素数组的 codec；`EMPTY` 是无布局的哨兵 RL，`EMPTY_LAYOUT` 是空布局对象 |
| `client/render/renderable/GuiAnimatable.java` | 模型元素：每个实例绑定一份 `ModelHudStyle` + 一个 `HudElement`；上下文仍委派给玩家所坐座椅的 Part |
| `client/render/renderable/HudElementInstance.java` | 两种载体共用的运行时接口：`isPerspective` / `renderGui` / `dispose` |
| `client/render/renderable/AuiHudAnimatable.java` | AUI 元素：每个条目创建一个 `Document`，标记 `manuallyRendered`，按 `anchor` / `offset` / `scale.x` 投放；`body` 加 `mm-hud` 类并注入收缩规则；`/reload` 后重新贴类名 |
| `client/render/gui/hud/CustomHud.java` | 按宿主分组：`LinkedHashMap<IHudHost, List<HudElementInstance>>`；每 tick 解析当前视角的宿主集合，集合或视角变化时整体重建；向宿主取 `hudLayoutFor`，为 `null` 时按空布局处理；每个宿主保留一个可能为空的列表，空列表也占键；渲染按宿主顺序，透视/正交用 `isPerspective()` 分趟；AUI 元素在正交趟按布局顺序手动绘制并刷新 AUI 批次；炮镜的 `scope_behavior` 变换链不变 |
| `external/MMDynamicRes.java` | `HUD_STYLES` 与 `HUD_LAYOUTS` 两张表 |
| `common/resource/modules/HudModule.kt` / `HudLayoutModule.kt` | 按 `<命名空间>:<相对路径>` 拼键，子目录参与键；分别解码样式与布局 |
| `common/registry/MMPackModuleRegistries.kt` | 注册 `huds` 与 `hud_layouts` 两个内容包模块 |
| `common/mech/subsystem/SeatSubsystem.java` | 实现 `IHudHost`：`first` / `third` 读静态属性，`scope` 取 `HudLayout.EMPTY`，三者都经 `IHudHost.resolve` 解析 |
| `CameraSubsystem.java` / `SightSubsystem.java` | 实现/继承 `IHudHost`：`scope` 读静态属性，其余视角取 `HudLayout.EMPTY`，都经 `IHudHost.resolve` |
| `WeaponControllerSubsystem.java` / `WeaponControllerSubsystemAttr.java` | 实现 `IHudHost`：三个视角读动态属性，字段缺省时取 `HudLayout.EMPTY`，都经 `IHudHost.resolve` |
| `common/mech/subsystem/attr/static_attr/SeatSubsystemStaticAttr.java` | `first_hud_layout` / `third_hud_layout` 各一个 `ResourceLocation`，codec 缺省值为 `HudLayout.EMPTY` |
| `CameraSubsystemStaticAttr.java` / `SightSubsystemStaticAttr.java` | `scope_hud_layout` 一个 `ResourceLocation`，codec 缺省值为 `HudLayout.EMPTY` |

尚未实现：`HudMolangContext` / `WeaponHudMolangContext`、AUI 绑定层（`data-mm-*`）、武器控制器 HUD 的内容包示例与 schema、玩家布局管理器与编辑页。AUI 载体的页面渲染已接入，语法的取值通道仍待绑定层。

RL 命名约定：样式与布局都使用 `<命名空间>:<相对路径>`，样式目录是 `huds/`、布局目录是 `hud_layouts/`，因此两者的键可以同名而互不冲突；不额外加 `layout/` 前缀，也不在键里写目录名。

***

## 十一、性能与边界

### 11.1 成本模型

| 环节 | 频率 | 成本 |
| --- | --- | --- |
| 表达式编译 | 每个表达式一次（跨实例、跨热重载复用） | 按 `(上下文类, 表达式串)` 缓存 |
| 布局解析 | 内容包加载 / 热重载 | 每份布局一次 O(元素数) |
| 扫描 + 建槽位表 | 仅结构变化 / 热重载 | 一次 O(元素数) 遍历 |
| 求值 | 每客户端 tick | 槽位数 × 一次字节码调用 |
| 写入 | 每客户端 tick，仅变化时 | 文本写触发 AUI 文本重排；类名 token 未变不触发样式重算 |
| 布局写入 | 仅布局变化 | `setInlineStyleProperty` 每次都排一次样式重算 |

槽位数量级：一台控制器 5~15 个槽位、一个座位通常 ≤ 3 台控制器，合计 ≤ 50 槽位/页。

一份布局被多个宿主引用时，样式与布局数据只解析一份；每个宿主/视角各自创建运行时元素，各自持有上下文。

### 11.2 载体能力边界

| 需求 | 模型载体 | AUI 载体 |
| --- | --- | --- |
| 屏幕空间任意排版、文本、列表、图标 | 弱（骨骼 + 固定文本槽） | 强 |
| 贴镜头的 3D 元素（分划随动、透视偏移、忽略变焦） | 强 | 不支持真正的透视投影：AUI HUD 页面固定在 GUI/HUD 正交阶段，只能做屏幕空间近似（反向缩放、投影点换算），不能表现深度与透视形变 |
| 世界内 HTML 面板（铭牌、显示屏） | 不支持 | `WorldWindow` 支持，但属于世界内面板，不在本文的 HUD 宿主范围（§1.3）；默认客户端本地，AUI 不负责同步，若随载具同步则对其他玩家可见 |
| 逐帧数据槽 | 有（`molang_args`） | 有（`data-mm-*` + 词表） |
| 动态条数（几根发射管 / 几条弹链） | 模型骨骼槽位固定，已知数量可显式写下标；不隐式安装当前项 | `data-mm-repeat` + 当前项 `tube.*` / `belt.*`；`data-mm-index` 可选，语法见 §8.5；绑定层待实现 |
| 内容包投递 | 模型/动画/纹理（美术工具链） | HTML/CSS（§九 的页面资源管线） |

### 11.3 AUI 边界（按 1.2.7 发布提交 `fd60a433` 核对）

| 项 | 结论 |
| --- | --- |
| `calc()` / `min/max/clamp` | 支持 |
| `vw` / `vh` | 支持 |
| `::before` / `::after` | 支持 |
| 属性选择器 | 支持（含 `i` / `s` 标志） |
| `innerText` | 只在无子节点元素上参与渲染 |
| `setAttribute` / `setInnerHTML` | 无内容比较，不进每 tick 路径 |
| `var()` | 支持；`background` 简写不解析 `var()`，用 `background-color` |
| 8 位 hex 颜色 | alpha 解析不正确，半透明一律 `rgba()` |
| 每帧渲染 | 无脏区域渲染，逐帧遍历整棵绘制列表；同类 HUD 收在一个 Document 内，不为每个元素建文档 |
| 页面脚本 | 由 KubeJS 提供；未安装时静默不执行。本文的数据通道不依赖它 |
| 输入拦截 | `Document.interceptMouseEvents` 只在文档构造与 `refresh()` 时从页面 meta 读取，没有运行时 setter；显示态 HUD 不声明该 meta |
| `Element.register` | 全局同名覆盖，最后写入者赢，无注销也无冲突诊断。`@ElementRegister` 的扫描按已登记包前缀过滤，AUI 在 `register()` 之前只登记了自己的元素包，因此下游模组须直接调用 `Element.register` |
| `Style` 默认 display | 标签白名单：`SPAN`、`A`、`DATA`、`TRANSLATION` 等表内标签为 `inline`，未列出的标签一律 `block` |
| 每 tick 元素遍历 | `RenderQueue.tickElements` 复制文档全部元素后逐个 `tick()`，无异常隔离；任一元素抛异常都会中断该文档的 tick |

> `docs/AUI性能边界.md` 的调研对象是 AUI 1.1.6-dev，其陷阱表中 `calc()/vw/vh`、`::before/::after`、`Element.querySelector` 三条与 1.2.7 发布提交 `fd60a433` 不符。

***

## 十二、分阶段实施

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| **V1（已实现）** | `HudStyle` / `HudElement` / `HudLayout` 与两张资源表；座椅、相机/瞄具切到按视角的 `*_hud_layout`；`CustomHud` 按布局数组创建、排序与渲染模型元素；内容包迁移；沿用现有 `GuiAnimatable` 与 Part 的 `MechMolangContext` | 现有座椅/相机/瞄具 HUD 表现等价；一份布局被多个座椅/视角引用；模型载体的元素-布局体系跑通 |
| **V2-b（进行中）** | AUI 载体：`machine_max:aui_hud` 样式、`anchor` 投放、每个元素独立 Document、`body.mm-hud` 收缩规则、按布局顺序手动绘制；`DocumentModule` 与客户端资源包注入 | AUI 页面作为 HUD 元素渲染；内容包页面可创建 |
| **V2-a（后做）** | 通用 `HudMolangContext`（`hud.*`）、AUI 绑定层（`data-mm-*`）、`local.*` / `global.*` 在 HUD 上下文中的转发 | AUI 页面显示数值；`hud.*` 查询可用；内容包页面按表达式刷新 |
| **V3（按需）** | 武器控制器宿主的三个 `*_hud_layout` 字段与宿主选择已入代码；剩余候选并集、载体通用 `selection_visibility`、观察者选中状态、`WeaponHudElement`、`WeaponHudMolangContext`、业务快照、`weapon.*` 与当前项查询、`scope` 分划、内容包示例及 schema | 候选 WC 按当前视角布局逐条选择常驻/仅选中显示；切组保留元素；各作用域数据不串；炮镜分划读取本控制器弹药；多台重叠由内容包布局决定 |
| **V4（后做）** | 玩家覆盖与编辑页 | `HudLayoutManager`、按视角覆盖/重置、`VehicleControlScreen` 的"HUD 布局"页 |

V1 已落地并通过 `gradlew compileJava compileKotlin`，`runClient` 进世界的表现验收见 §十三。宿主分组与武器控制器宿主的集合选择也已落地：座椅、相机/瞄具、武器控制器都实现 `IHudHost`，`CustomHud` 按宿主集合重建；武器控制器没有布局字段时不产生元素。V1 刻意不包含 `HudMolangContext`、`WeaponHudMolangContext`、AUI 绑定与玩家覆盖。座椅 HUD 的 `global.get('vehicle_speed')` 继续走 Part 的 `MechMolangContext`；相机/瞄具的模型 HUD 只要求静态文本或现有上下文能求值的表达式。V2-a 起提供 `hud.*`，V3 起提供 `weapon.*`。`WeaponHudOverlay` 继续作为武器状态面板。AUI 载体已在 V2-b 接入 `CustomHud`：页面由 `AuiHudAnimatable` 持有并绘制，静态内容不依赖 `hud.*`，表达式绑定等 V2-a。

渲染顺序契约只覆盖提交顺序：同一宿主内按布局数组顺序提交，宿主之间按宿主集合的迭代顺序提交。宿主顺序不构成契约，内容包负责让同一视角的元素互不遮挡；两个投影批次之间的先后由渲染器固定，不要求布局表达跨投影顺序。同批次半透明几何按深度重排的风险已接受，验收只断言提交顺序。

`scissor` 是样式级、相对屏幕中心的矩形；V1 沿用这套语义。现有三份样式的 `scissor.enable` 为 false，因此不阻塞 V1；开启 scissor 的样式由 §14.3 单独决定归属。

***

## 十三、验收

V1 已通过 `gradlew compileJava compileKotlin`。2026-10-18 `runClient` 进世界：世界加载无失败；座椅与炮镜 HUD 的位置、缩放、投影和 `global.get('vehicle_speed')` 求值均正常。逐条用例：

| 用例 | 阶段 | 覆盖 |
| --- | --- | --- |
| 现有内容包迁移后表现等价 | V1 | 座椅/相机/瞄具的 HUD 位置、缩放、投影和 `global.get('vehicle_speed')` 求值 |
| 一份布局被两个宿主引用 | V1 | 默认投放共享；每个运行时元素独立创建 |
| 同一份样式的两种炮镜行为 | V1 | 两个布局/元素读取各自 `scope_behavior`/`ignore_zoom` |
| 视角字段过滤 | V1 | 座椅只读 `first_hud_layout`/`third_hud_layout`；相机/瞄具只读 `scope_hud_layout` |
| 宿主集合变化重建 | V1 | 上下车、换座、切换第一/第三人称、开关炮镜时整体重建；宿主集合与视角都不变时不重建 |
| 无布局宿主保留空列表 | V1 | 武器控制器未配 `*_hud_layout` 时该宿主为空列表，键集合仍与宿主集合一致，不触发每 tick 重建 |
| 热重载刷新时机 | V1 | 内容包重载后运行中的 HUD 不立即刷新；上下车或切换视角后按新资源重建 |
| 同一布局用于第一/第三人称 | V1 | 同一 RL 在两个视角各自生成元素，互不串值 |
| 不同车不同布局 | V1 | 两台车的座椅/相机/瞄具子系统引用不同布局 RL，互不影响 |
| 同投影模式内的提交顺序 | V1 | 同一布局中多个透视元素、多个正交元素分别按数组顺序提交；同批次半透明几何可能按深度重排，不作像素覆盖顺序断言；跨投影的先后不作断言 |
| AUI 载体的投影归类 | V2 | 布局中的 AUI 元素即使写 `perspective: true` 也按 `false` 处理并进入正交批次 |
| AUI 页面渲染 | V2-b | 页面在 `anchor` 指定的屏幕位置出现，背景透明，不遮挡世界；页面缺失时只提示一次并跳过该条目 |
| AUI 元素与模型元素同帧 | V2-b | 同一布局内先提交模型元素，再提交 AUI 文档；AUI 文档位于模型元素之上（AUI 平面文档层 z 从 1.0 起） |
| 玩家按视角覆盖 | V4 | 只覆盖 `first` 时 `third` 使用预设；`first: []` 明确隐藏；单视角重置与整宿主重置 |
| 武器分划 | V3 | 相机/瞄具边框使用普通上下文；每台武器的 `scope` 分划读取本控制器弹药，数据互不串值 |
| 武器候选与选择 | V3 | 当前座椅所有控制组发现的 WC 并集进入候选，同实例去重；其他座椅独有武器不进入；base + 当前子组独立决定选中；不按主/副、`isActive`/`isDestroyed` 过滤；无布局为空列表 |
| 载体通用条目显隐 | V3 | 模型与 AUI 的各提交入口使用同一 `selection_visibility` 规则；同一 WC 的常驻面板和仅选中分划可同时配置；普通座椅/相机不被误筛 |
| 选中切换与观察者隔离 | V3 | 切组只刷新 `weapon.selected` 和显隐，元素/Document 身份保持；两个座椅观察同一 WC 的选中值互不串 |
| 当前项无参查询 | V3 | 根与后代作用域一致；排序后按稳定键读取本轮成员；同编译表达式多项复用；异常后恢复；区域外/类型错误诊断；显式索引不依赖 DOM |
| AUI 基础绑定 | V2 | 页面显示数值；错误只使对应槽位降级 |
| 内容包自带页面 | V2 | 客户端本地包内资源写入 `CLIENT_PACK`；AUI 资源扫描/自动重载后模板可创建；CSS/JS/图片由资源管理器解析；本地 `<gamedir>/apricity/` 同名文件优先 |
| 未安装 KubeJS | V2 | HUD 正常显示 |

***

## 十四、待决事项

1. **玩家覆盖键是否带载具/部件型号**：`seat:<子系统名>` 与 `seat:<型号>:<子系统名>` 之间尚待选择；示例使用前者。两台车共用同一个子系统定义时会共享覆盖。
2. **元素级复用与部分覆盖**：当前布局是复用单位，元素按完整投放值定义；如果将来需要跨布局共享一个元素，再加显式引用，不做部分覆盖。
3. **`scissor` 的归属**：V1 把它留在样式里，且 x/y 是相对屏幕中心的屏幕坐标，与"样式只描述内部"的边界冲突。后续应移到元素、使用元素局部坐标，或明确接受这个例外。
4. **玩家自定义命名布局**：v1 只保存每宿主一份覆盖，命名多套布局留待后续。
5. **AUI 模板实例化路径**：页面语法、索引、身份与换代行为由 §8.5、§8.6 定义；实现前须验证属性依赖型标签与行内事件的初始化，选择 Machine-Max 通用模板实例化器或经修复的 AUI 克隆路径。嵌套、分页/过滤属性与自定义集合属于后续扩展，不阻塞首版。
6. **AUI 世界内面板与 HUD 的关系**：`WorldWindow` 能用关卡投影矩阵做透视，但它有世界坐标、遮挡、交互与显示距离，语义与屏幕空间 HUD 不同。`WorldWindow` 默认只在创建它的客户端本地渲染，AUI 不提供窗口的网络同步；若将来把载具面板同步给其他客户端并由各客户端重建窗口，它就属于世界可见内容，私人 HUD 只适用于未同步的本地窗口。实现前必须定义 per-viewer 与 world-visible 两种可见性，以及同步、距离与性能边界；不复用 `HudElement.perspective`。

***

## 十五、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-16 | 定义样式、挂载、布局、三方宿主、玩家覆盖与 AUI 载体 |
| 1.1 | 2026-10-16 | 明确通用/武器 HUD 查询分工及每元素上下文 |
| 1.2 | 2026-10-16 | 对齐元素与上下文继承、武器控制器 `scope` 分划及 AUI 重复界面的待实现边界；列出玩家拖动位置的持久化缺口 |
| 1.3 | 2026-10-17 | §8.2 追加绑定层三种实现载体（扫描属性／新增标签／覆盖内置标签）备选记录，标注未决定，不改动既定属性语法与后续章节 |
| 1.4 | 2026-10-17 | §8.2 定绑定层载体为属性扫描并补 `MutationObserver` 增量绑定，把“新增自定义标签”“覆盖内置标签”记为已评估不采用；§8.3 增“运行期节点增删”一行；§11.3 增 `Element.register`、默认 display 白名单、逐 tick 全量遍历三条，核对基准改为 1.2.7 发布提交 |
| 1.5 | 2026-10-17 | 布局为有序 `HudElement` 数组，视角由宿主的 `first_hud_layout` / `third_hud_layout` / `scope_hud_layout` 字段决定；`HudMount` 与三组 `HudLayoutSet` 退出模型，`z` / `order` 删除；玩家覆盖为按视角元素数组，缺失视角用预设、空数组表示不显示；实施顺序为 V1 先验证元素-布局体系，V2–V4 依次接入 AUI/上下文、武器控制器与玩家编辑 |
| 1.6 | 2026-10-17 | 绘制顺序契约限定为同一种投影模式内；透视与正交各自按布局数组顺序绘制，跨投影先后由渲染批次决定 |
| 1.7 | 2026-10-17 | 明确 AUI 载体的绘制阶段：Overlay/Screen 文档固定走 GUI/HUD 正交，`WorldWindow` 的世界内透视路径不属于 HUD 宿主；`perspective` 仅对模型载体生效，AUI 元素一律归正交批次，并补相应验收与待决项 |
| 1.8 | 2026-10-17 | 收紧 AUI 载体的措辞为“本设计的 HUD 路径”；补充 `WorldWindow` 的客户端本地性与同步后世界可见性边界，明确 world panel 必须另定 carrier 与可见性模型 |
| 1.9 | 2026-10-18 | 武器控制器 HUD 可见性改为座椅当前控制组的选中集合：选中的 WC 全部显示，不区分主/副、不设焦点组；多台重叠由内容包布局处理 |
| 2.0 | 2026-10-18 | §九 补齐内容包 AUI 页面的模板注入：`AuiResourceService` 只覆盖 CSS/JS/图片，HTML 必须经 `HTML.putTemple` 进入模板表；补页面模块、线程/缓存/路径/安全边界与第一阶段 JAR 内页面基线；V2 与验收同步 |
| 2.1 | 2026-10-18 | §九 改为通用 AUI 页面管线：推荐注入 Spark `CLIENT_PACK` 的 `apricityui:apricity/<key>`，HUD/手册/PDA 共用；补命名空间种子与 `HTML.scan` 时机；`AuiResourceService` 包装器降为备选；V2 与验收同步 |
| 2.2 | 2026-10-18 | `DocumentModule` 与客户端资源包注入器落地；§九 与迁移表对齐 `documents/` 目录与服务端同步语义；待客户端实机验收 |
| 2.3 | 2026-10-18 | 文档收敛为客户端本地资源：`DocumentModule` 使用 `LOCAL_ONLY`，`read` 直接写 `CLIENT_PACK`；移除扫描钩子与服务端同步；页面发现交给 AUI 自动重载 |
| 2.4 | 2026-10-18 | `DocumentModule.onFinish` 在客户端补一次 `HTML.scan()` 作为保险；`HTML` 属于 AUI common 包，不需要额外客户端钩子 |
| 3.0 | 2026-10-18 | V1 落地：`HudStyle`（`type` 分派，现只有 `model_hud`）/`ModelHudStyle`/`HudElement`/`HudLayout` 与 `HUD_STYLES`/`HUD_LAYOUTS` 两张表；运行时元素以当前视角内数组序数为身份，宿主/视角/布局对象/样式对象变化时整体重建；布局与样式都按 `<命名空间>:<相对路径>` 拼键、子目录参与键、不加 `layout/` 前缀；座椅与相机/瞄具切到单个 `*_hud_layout`，内容包与 schema 同步迁移；V1 上下文继续用座椅 Part，绘制顺序契约降为提交顺序并接受同批次深度重排 |
| 3.1 | 2026-10-18 | 宿主分组落地：新增 `HudView` / `IHudHost`，座椅、相机/瞄具与武器控制器均实现；`CustomHud` 改为 `LinkedHashMap<IHudHost, List<GuiAnimatable>>`，每 tick 比较解析出的宿主集合与键集合、并比较视角，变化时整体重建；空布局宿主保留空列表；武器控制器动态属性加入三个 `*_hud_layout`；跨宿主绘制顺序明确不设契约，内容包负责元素不重叠；热重载改为上下车/换视角时生效 |
| 3.2 | 2026-10-18 | 修正 `*_hud_layout` 的 codec：改用 `optionalFieldOf(..., HudLayout.EMPTY)` 的非空默认值，删除产生 null 的 `xmap`；新增 `HudLayout.EMPTY` 哨兵与 `EMPTY_LAYOUT`；`IHudHost` 只保留 `hudLayoutFor`（返回实际布局对象），另提供静态 `resolve` 供持有内容包 RL 的宿主解析，玩家自定义布局直接覆盖 `hudLayoutFor`；`CustomHud` 对 `null` 布局按空布局处理；内容包错误上报对空异常消息与两列表长度不一致做兜底 |
| 3.3 | 2026-10-18 | V1 实机验收：`runClient` 进世界加载无失败，座椅与炮镜 HUD 的位置、缩放、投影和 `global.get('vehicle_speed')` 求值正常 |
| 3.4 | 2026-10-18 | AUI 载体落地：`AuiHudStyle`（`aui_hud`，`page` + `defaults`）、`HudAnchor` 与元素 `anchor` 字段、`HudElementInstance`；`AuiHudAnimatable` 为每个条目创建 `manuallyRendered` Document，按锚点/偏移/缩放投放并注入 `body.mm-hud` 收缩规则；`CustomHud` 改用 `List<HudElementInstance>`，正交趟按布局顺序绘制 AUI 文档并刷新 AUI 批次；内容包示例与 `hud.md` 同步。`data-mm-*` 绑定层与 `hud.*` 上下文留待 V2-a |
| 3.5 | 2026-10-20 | 定义 `data-mm-repeat` / `data-mm-index`、发射管与可选弹链集合、作用域变量、CSS 内部排布、按身份维护 DOM 及页面换代契约；记录 AUI 克隆初始化前置验证，绑定层尚未实现 |
| 3.6 | 2026-10-21 | 定义模型/AUI 通用 `selection_visibility`、座椅候选并集与独立选中状态；定义当前项无参查询、可选下标别名、按身份安装/恢复作用域及验收要求 |
