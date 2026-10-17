# HUD 样式、布局与元素设计

> 版本 1.9 · 2026-10-18
> **状态**：计划中（未实现。当前代码的 HUD 定义是单一 codec：`MMDynamicRes.CUSTOM_HUD` 存 `HudAttr`，表现与投放写在同一个文件里；宿主用内联 RL 列表声明；代码中尚无 `HudStyle` / `HudElement` / `HudLayout` 与玩家覆盖）

***

## 一、要解决的问题

### 1.1 现状

| 环节 | 现状 |
| --- | --- |
| HUD 定义 | `huds/*.json`（4 份：`example_hud`、`example_hud_2`、`gun_sight_hud`、`gun_sight_hud_fixed`），由 `HudModule` 读入 `MMDynamicRes.CUSTOM_HUD`。**表现与投放（`offset` / `rotation` / `scale` / `perspective`）写在同一个文件里** |
| 相机/瞄具宿主 | `CameraSubsystemStaticAttr.hud_components`（`SightSubsystemStaticAttr` 的同名字段）：裸 `ResourceLocation` 列表 |
| 座椅宿主 | `SeatSubsystemStaticAttr.ViewAttr.first_person_hud` / `third_person_hud`：两个裸 RL 列表（`first_person_offset` / `third_person_offset` 是相机支点，不是 HUD 投放，见 [CameraController](../../../src/main/java/io/github/sweetzonzi/machine_max/client/input/CameraController.java) 的第三人称支点计算） |
| 武器控制器宿主 | 无（武器状态 HUD 由 `WeaponHudOverlay` 单独实现，见《武器系统-武器控制器HUD样式与布局设计》） |
| 玩家参与 | 无 |

由此产生三个问题：

1. **投放无法复用也无法调整**：`machine_max:example_hud` 被 7 个座椅以主/副视角共约 10 处引用，全部共用文件里那一组投放值；想给某个座位挪一点位置，只能再复制一份 HUD 定义。
2. **三方形状不一致**：相机与座椅是"列表"，武器控制器目前没有布局字段，同一件事有两种写法。
3. **玩家无从参与**：布局完全由内容包决定。

### 1.2 目标

| 目标 | 落地判据 |
| --- | --- |
| 定义只说"长什么样" | `HudStyle` 里不存在任何投放字段 |
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
| **布局（HudLayout）** | 一份有序的 `HudElement` 数组；命名资源 `hud_layouts/*.json`；不含视角分组 |
| **元素（HudElement）** | 布局中的一个条目：`style` RL + 一组完整投放值；内联，不单独注册 |
| **宿主（host）** | 声明"哪个视角用哪份布局"的子系统：座椅、相机/瞄具、武器控制器 |
| **视角字段（view field）** | `first_hud_layout` / `third_hud_layout` / `scope_hud_layout`；字段名决定视角 |
| **玩家覆盖** | 玩家本地按宿主键保存的每视角元素数组，优先于内容包预设 |

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

### 3.3 为什么"行为"不算内部

`scope_behavior` 与 `ignore_zoom` 描述的是"这份分划在炮镜里怎么动"，属于 `HudElement`，不属于 `HudStyle`。内容包中的 `gun_sight_hud.json` 与 `gun_sight_hud_fixed.json` 使用同一个模型、同一份动画与纹理，差异只有这两个字段；它们对应同一份 `HudStyle`，行为差异由引用该样式的不同布局表达。

***

## 四、HudElement：样式 + 投放

```jsonc
// hud_layouts/gunner_scope.json 中的一个条目
{
  "style": "machine_max:gun_sight_hud",
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
| `perspective` | 读 | 不适用：AUI 页面固定在 GUI/HUD 正交阶段渲染；写 `true` 时提示并按 `false` 处理 |
| `scope_behavior` / `ignore_zoom` | 读；仅当布局被宿主的 `scope` 字段引用时生效 | 忽略（提示警告） |
| `anchor` | 忽略 | 读：`top_left` … `bottom_right` / `center` |
| `offset` | 读三维（透视下米、正交下像素），相对屏幕中心 | 读二维逻辑像素（`z` 忽略） |
| `rotation` | 读（度） | 忽略：AUI 的旋转属于样式（CSS `transform`） |
| `scale` | 读三维 | 读单值（取 x） |

规则：

1. **一个元素就是一组完整投放值**，不做"部分覆盖"。需要另一种投放就在布局里再写一个元素。每项投放只有一个权威来源。
2. **没有 `z` / `order` 字段**。`offset.z` 是空间深度：透视下为摄像机前方距离（现有内容使用负值，如 `-135`），正交下为 GUI 深度平移；`FOLLOW_TRANSFORM` 的分划板距离也取 `offset.z`。`offset.z` 不参与绘制顺序；绘制顺序由布局数组决定（同投影模式内）。
3. `scope_behavior` / `ignore_zoom` 只在布局被 `scope` 视角字段引用时生效；被 `first_hud_layout` / `third_hud_layout` 引用时忽略并提示，避免内容包把炮镜行为写进普通视角。
4. `anchor`、`rotation` 等载体专属字段按上表处理；不适用于当前载体的字段忽略并提示。
5. **投影模式由载体决定，不由字段单独决定**：`perspective` 是模型载体专属字段；模型载体的元素按它进入透视或正交批次，AUI 载体的元素一律进入正交批次。布局可以同时包含两种载体，运行时先解析 `style` 的 `type`，再决定元素归入哪个批次。

### 4.1 为什么元素内联在布局里

- **复用单位是布局**：一份布局可以被多个座椅、多个视角引用；单个元素的投放跨布局复用是少数情况。
- **同一份样式可以出现多次**：一个布局里可以有多个元素引用同一个 `style`，各自带不同的 `offset`/`scale`。运行时容器按条目身份（布局 + 下标）管理，`style` RL 不是唯一键。
- **不引入第二层注册表**：元素不是具名资源；如果将来确实需要跨布局共享一个元素，再加显式引用（引用项是完整投放，不做部分覆盖），不在当前版本引入。

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

1. **数组顺序是同一种投影模式内的绘制顺序**，没有 `order`。透视元素之间、正交元素之间分别按数组顺序绘制；透视与正交之间的先后由渲染批次决定，不作为布局契约。`offset.z` 只决定空间深度，不参与排序。归入哪个批次由 `style` 的载体类型决定：模型载体看 `perspective`，AUI 载体一律归正交（§四规则 5）。
2. **布局不含视角信息**。同一份布局可以被座椅的 `first_hud_layout` 与 `third_hud_layout` 同时引用，也可以被相机/瞄具的 `scope_hud_layout` 引用；视角由宿主的字段名决定。
3. **空数组 = 该视角不显示 HUD**；宿主字段缺失 = 该视角没有布局，同样不显示。
4. **布局是命名资源**：`hud_layouts/*.json` 由 `HudModule` 读入 `MMDynamicRes.HUD_LAYOUTS`，键为 `machine_max:layout/<文件名>`。如果 `hud_layouts/` 下再分子目录，模块需把子目录纳入 RL，或统一使用前缀，避免不同目录同名文件撞键。
5. **布局只描述"按顺序画哪些元素"**；它不知道被哪个宿主、哪个视角引用，也不知道玩家是否覆盖。

### 5.1 为什么布局是具名资源

`machine_max:example_hud` 与 `example_hud_2` 被 `dayun`、`senna_gtr`、`vn`、`golden_closestool`、`k17`、`sdkfz234` 等座椅以主/副视角共约 10 处引用，且共用同一组投放。若投放内联在宿主里，同一组数要抄 10 遍，改一次仪表盘布局要改 10 个文件。具名布局让"一处定义、多处引用"成立：

- 多个座椅可以引用同一份 `seat_dashboard_fp`；
- 同一份布局可以被第一人称和第三人称同时引用；
- 某辆车需要不同投放时，写一份新的布局文件并在该车的子系统里引用它。

***

## 六、宿主：每个视角一个 RL

### 6.1 引用形态

| 宿主 | 字段 | 视角 | 玩家覆盖键 |
| --- | --- | --- | --- |
| 相机/瞄具 | `CameraSubsystemStaticAttr.scope_hud_layout`（`SightSubsystemStaticAttr` 提供同名字段） | `scope` | `camera:<子系统名>` |
| 座椅 | `SeatSubsystemStaticAttr.ViewAttr.first_hud_layout` / `third_hud_layout` | `first` / `third`（`THIRD_PERSON_FRONT` 归 `third`） | `seat:<子系统名>` |
| 武器控制器 | 动态属性 `first_hud_layout` / `third_hud_layout` / `scope_hud_layout`（`WeaponControllerSubsystemAttr`） | `first` / `third` / `scope` | `wc:<子系统名>` |

每个字段是一个 `ResourceLocation`，指向一份 `HudLayout`。字段名决定视角，宿主不读取其它视角的字段。RL 查不到或布局解码失败时，记录一次可定位诊断，并按该视角无 HUD 处理。

```jsonc
// 座椅
"views": {
  "first_hud_layout": "machine_max:layout/seat_dashboard_fp",
  "third_hud_layout": "machine_max:layout/seat_dashboard_tp",
  "enable_first_person": true,
  ...
}
```

```jsonc
// 相机/炮镜
"scope_hud_layout": "machine_max:layout/scope_gunner"
```

```jsonc
// 武器控制器
{
  "type": "machine_max:weapon_controller",
  "definition": "machine_max:basic_weapon_controller",
  "control_outputs": { "weapon_control": ["subsystem.machine_max.main_launcher"] },
  "first_hud_layout": "machine_max:layout/wc_gunner_first",
  "third_hud_layout": "machine_max:layout/wc_gunner_third",
  "scope_hud_layout": "machine_max:layout/wc_gunner_scope"
}
```

字段缺失表示该视角没有 HUD。武器控制器可以使用三个视角字段；座椅只有第一/第三人称；相机/瞄具只有炮镜。座椅没有 `scope_hud_layout`：炮镜态下要显示的基础元素由该炮镜自己的 `scope_hud_layout` 提供，武器分划与弹药由武器控制器的 `scope_hud_layout` 提供。

武器控制器的 HUD 不由座椅聚合弹药数据：运行时取该座椅当前控制组（base + 当前子组）的选中集合 `getActiveWeaponControllers()`，集合内、当前视角有布局且未停用的 WC 全部显示；不区分主/副，不设焦点组。多台 WC 的布局重叠由内容包处理；座椅只提供选中集合。

### 6.2 数据上下文由持有者决定

每个被引用的布局条目在运行时生成自己的 HUD 元素，元素缓存自己的 MoLang 上下文。所有元素可读 `hud.*`（当前渲染视角与视口）；武器控制器元素继承通用元素，并增加直接取自所属控制器的 `weapon.*`。`math.*` 属于 Spark 通用词表；载具 HP、能量与信号等继续从 `local.*` / `global.*` 读取，HUD 元素通过所属 Part/装配体关联它们。类层次和查询口径见《[观瞄系统-HUD专用MoLang上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》。

| 持有者 | 自有数据与炮镜职责 |
| --- | --- |
| 座椅 | 座椅布局只产生自己的仪表等元素；没有 `weapon.*` |
| 相机/瞄具 | `scope` 视角下产生边框、遮罩等基础元素；没有 `weapon.*` |
| 武器控制器 | 直接绑定这台控制器；弹药、装填与弹道分划都在自己的元素中，`scope_hud_layout` 可挂炮镜分划 |

相机/瞄具无需反查武器；座椅只提供“当前控制组选中了哪些 WC”的集合，不替 WC 聚合弹药数据。一个相机/瞄具边框和多台控制器的 HUD 可以同时处于 `scope` 视角；各元素只读取自己的上下文。若多台控制器都配置了分划，它们各自绘制，重叠由内容包布局处理。

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
| `data-mm-repeat` | 暂定的动态列表标记；按实际发射管或弹链数自动生成“一项”的多段界面。它只重复显示结构，不复制武器或 HUD 对象；每段如何取得自己的下标待设计 |

例如四根发射管的样式只需定义一段装填条，绑定层在页面中生成四段，并依次绑定到第 0～3 根管。**当前没有可用的 `data-mm-repeat` 实现或下标约定**，因此可执行示例只展示明确下标的单管查询 `weapon.tube_fill(0)`；多管页面的表达式写法须等绑定层确定“当前项”规则后再发布。模型载体的骨骼槽位固定，可在确定管数的样式中显式写下标。

**实现载体（已定）**：绑定层由本模组实现，`data-mm-*` 由绑定层扫描页面属性识别，AUI 自身不改动。依据三条：

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
| 页面加载或热重载 | 按运行时元素关联上下文，整页扫描数据标记并编译表达式；热重载可命中编译缓存 |
| 运行期节点增删 | 由 `MutationObserver` 对新增子树增量绑定、对移除子树释放槽位，不整页重扫 |
| 武器结构变化 | 只重建受影响控制器的区域；动态列表机制落地后按新条目数生成各段 |
| 每客户端 tick | 刷新武器快照与有效布局，原子发布不可变值；对 AUI 数据槽求值，只在结果变化时写入 |
| 模型动画求值前 | 当前线程的上下文读取已发布快照，再计算消费 `hud.*` 的骨骼表达式 |
| 模型文本渲染前 | 客户端主线程上下文读取本次渲染的参数，求值 `molang_args` |

文本必须写到叶子元素，且只有值变化才改 `innerText`；类名 token 与 CSS 变量也只在变化时改写。`setAttribute` / `setInnerHTML` 不进逐 tick 路径，避免无谓的样式与布局重算。槽位缓存随元素或页面区域销毁而释放。

### 8.4 词表归属

- `hud.*`：通用 HUD 元素的当前相机视角与视口；座椅、相机/瞄具、武器元素均可读。见《[观瞄系统-HUD专用MoLang上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》。
- `weapon.*`：仅武器控制器元素可读，包含所属控制器的弹药、发射管、弹链和分划弹道角。见《[武器系统-武器控制器HUD样式与布局设计](武器系统-武器控制器HUD样式与布局设计.md)》。
- `math.*`：Spark 通用查询；`local.*` / `global.*`：Part 与装配体查询，持续用于载具仪表。这三者不形成新的 HUD 业务前缀。

MoLang 不提供可靠的字符串拼接；`weapon.ammo_text` 等组合文本由查询返回，或由模型文本槽的本地化键和 `molang_args` 组装。普通元素使用 `weapon.*`、表达式语法错误或求值失败时，绑定层应标明出错槽位并记录一次可定位诊断，其他槽位继续刷新。

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

备选方案是 `AuiServices.setResources(AuiResourceService)` 包装器 + `HTML.putTemple`：不依赖资源包命名空间，但要实现六个委托方法并额外维护模板注入。两种方案的比较、命名空间种子细节与验证计划见 [内容包 AUI 页面加载可行性调查](界面-内容包AUI页面加载可行性调查.md)。

这条管线同时服务两个载体：模型载体的模型/动画/纹理由内容包提供，AUI 载体也能自带页面。`hud_layouts/*.json` 由 Spark 内容包模块读取，不经过这条资源管线。

第一阶段只使用本模组 JAR 的 `assets/apricityui/apricity/machine_max/...` 页面；内容包页面接入留到第二阶段。

***

## 十、迁移面

### 10.1 内容包

| 对象 | 现状 | 迁移后 |
| --- | --- | --- |
| `huds/example_hud.json`、`example_hud_2.json` | 表现 + 投放 | 各一份样式（去掉 `offset`/`rotation`/`scale`/`perspective`/`scope_behavior`/`ignore_zoom`）+ 引用该样式的布局文件 |
| `huds/gun_sight_hud.json`、`gun_sight_hud_fixed.json` | 同模型两种行为 | 一份样式 + 两个布局（或同一布局的两个元素，按引用方式） |
| `subsystems/*/*_seat.json` | `first_person_hud` / `third_person_hud` 两个 RL 列表 | `views.first_hud_layout` / `third_hud_layout` 各一个 RL；当前列表中的多个 HUD 收进同一份布局数组 |
| 相机/瞄具子系统 | `hud_components` RL 列表 | `scope_hud_layout` 一个 RL；当前列表中的多个 HUD 收进同一份布局数组 |
| 武器控制器 | 无布局字段 | `first_hud_layout` / `third_hud_layout` / `scope_hud_layout` |
| `docs/{zh_cn,en_us}/schemas/**` 与 `hud.md` | 单层 HUD 定义 | 分"样式 / 布局 / 元素"三章；提供三份 schema |

迁移后只保留新字段；同一语义不保留两套写法。

### 10.2 代码

| 文件 | 改动 |
| --- | --- |
| `common/visual/HudAttr.java` | 拆为 `HudStyle`（表现）与 `HudElement`（`style` RL + 投放），或保留类名并按 §三/§四 裁剪字段 |
| `client/render/renderable/GuiAnimatable.java` | 模型载体读取 `HudStyle` 与 `HudElement`；由通用 HUD 元素提供 `IAnimatable#getMolangContext()` |
| `client/render/gui/hud/CustomHud.java` | 按宿主视角字段解析 `HudLayout` 与玩家覆盖，按 `HudElement` 创建各自带上下文的运行时元素；按解析后的投影归类维护两个有序 `List`（模型且 `perspective: true` 进透视列表，模型 `false` 与全部 AUI 进正交列表），同一 `style` 可出现多次，组内顺序即布局数组顺序；武器控制器元素只为该座椅当前控制组选中的 WC 创建，集合内全部显示，不按主/副分组 |
| `external/MMDynamicRes.java` | `CUSTOM_HUD` 拆为 `HUD_STYLES` 与 `HUD_LAYOUTS` 两张表 |
| `common/resource/modules/HudModule.kt` | 按模块 id/path 分派 `HudStyle` 与 `HudLayout`；增加 `hud_layouts` 模块 id |
| `common/resource/modules/DocumentModule.kt` | 读取客户端本地内容包 `documents/` 下的全部页面资源，按命名空间/相对路径直接写 `CLIENT_PACK`；不引用 AUI 类；HUD、手册、PDA 共用 |
| 相机、瞄具、座椅、武器控制器属性类 | 各提供按视角的 `*_hud_layout` 字段 |
| 新类 | `HudStyleRegistry` / `HudLayoutRegistry`、玩家布局管理器、`DocumentModule`、通用/武器 HUD 元素与对应 MoLang 上下文、AUI 绑定层 |

RL 命名约定：样式 RL 保持 `machine_max:<文件名>`（`huds/`）；布局 RL 加前缀 `machine_max:layout/<文件名>`。如果 `hud_layouts/` 下按子目录组织，模块需把子目录纳入 RL，或统一加前缀，避免不同目录同名文件撞键。

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
| 动态条数（几根发射管 / 几条弹链） | 模型骨骼槽位固定，已知数量可显式写下标 | AUI 重复界面是待实现能力，须先确定每段的索引绑定 |
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
| **V1（先验证）** | `HudStyle` 与 `HudLayout` 两个注册表，以及 `HudElement` 的 codec；座椅、相机/瞄具切到按视角的 `*_hud_layout`；`CustomHud` 按布局数组创建、排序与渲染模型元素；内容包迁移；沿用现有 `GuiAnimatable` 与 Part 的 `MechMolangContext` | 现有座椅/相机/瞄具 HUD 表现等价；一份布局被多个座椅/视角引用；模型载体的元素-布局体系跑通 |
| **V2（按需）** | AUI 载体（仅 GUI/HUD 正交；`WorldWindow` 不在内）、通用 `HudMolangContext`（`hud.*`）、AUI 绑定层、`DocumentModule` 与客户端资源包注入 | AUI 样式显示数值；`hud.*` 查询可用；`local.*` / `global.*` 在 HUD 上下文中继续可用；内容包页面可创建 |
| **V3（按需）** | 武器控制器宿主的三个 `*_hud_layout` 字段、`WeaponHudElement`、`WeaponHudMolangContext`、`HudWeaponSnapshot`、`weapon.*` 与 `scope` 分划 | 座椅当前控制组选中的 WC 全部按各视角显示；`weapon.*` 数据不串；炮镜分划读取本控制器弹药；多台重叠由内容包布局决定 |
| **V4（后做）** | 玩家覆盖与编辑页 | `HudLayoutManager`、按视角覆盖/重置、`VehicleControlScreen` 的"HUD 布局"页 |

V1 刻意不包含 `HudMolangContext`、`WeaponHudMolangContext`、AUI 绑定、武器控制器宿主与玩家覆盖。座椅 HUD 的 `global.get('vehicle_speed')` 继续走 Part 的 `MechMolangContext`；相机/瞄具的模型 HUD 只要求静态文本或现有上下文能求值的表达式。V2 起提供 `hud.*`，V3 起提供 `weapon.*`。§10.1 的武器控制器字段属于 V3；V1 只迁移座椅/相机/瞄具内容，`WeaponHudOverlay` 继续作为武器 HUD。

V1 的渲染顺序契约限定在同一种投影模式内：`CustomHud` 按透视/正交各维护一个有序列表，分别按布局数组顺序渲染；两个批次之间的先后由渲染器固定，不要求布局表达跨投影顺序。这样不需要按 run 切换投影，也不需要限制一个布局只能有一种投影。

`scissor` 是样式级、相对屏幕中心的矩形；V1 沿用这套语义。现有四份 HUD 的 `scissor.enable` 为 false，因此不阻塞 V1；开启 scissor 的样式由 §14.3 单独决定归属。

***

## 十三、验收

计划实现后在 `runClient` 进世界验证：

| 用例 | 阶段 | 覆盖 |
| --- | --- | --- |
| 现有内容包迁移后表现等价 | V1 | 座椅/相机/瞄具的 HUD 位置、缩放、投影和 `global.get('vehicle_speed')` 求值 |
| 一份布局被两个宿主引用 | V1 | 默认投放共享；每个运行时元素独立创建 |
| 同一份样式的两种炮镜行为 | V1 | 两个布局/元素读取各自 `scope_behavior`/`ignore_zoom` |
| 视角字段过滤 | V1 | 座椅只读 `first_hud_layout`/`third_hud_layout`；相机/瞄具只读 `scope_hud_layout` |
| 同一布局用于第一/第三人称 | V1 | 同一 RL 在两个视角各自生成元素，互不串值 |
| 不同车不同布局 | V1 | 两台车的座椅/相机/瞄具子系统引用不同布局 RL，互不影响 |
| 同投影模式内的绘制顺序 | V1 | 同一布局中多个透视元素、多个正交元素分别按数组顺序绘制；跨投影的先后不作断言 |
| AUI 载体的投影归类 | V2 | 布局中的 AUI 元素即使写 `perspective: true` 也按 `false` 处理并进入正交批次；诊断提示可见 |
| 玩家按视角覆盖 | V4 | 只覆盖 `first` 时 `third` 使用预设；`first: []` 明确隐藏；单视角重置与整宿主重置 |
| 武器分划 | V3 | 相机/瞄具边框使用普通上下文；每台武器的 `scope` 分划读取本控制器弹药，数据互不串值 |
| 武器选中集合 | V3 | 同一座椅选中多台 WC 时全部显示；不属于当前控制组的 WC 不显示；不按主/副分组；重叠由布局决定 |
| AUI 基础绑定 | V2 | 页面显示数值；错误只使对应槽位降级 |
| 内容包自带页面 | V2 | 客户端本地包内资源写入 `CLIENT_PACK`；AUI 资源扫描/自动重载后模板可创建；CSS/JS/图片由资源管理器解析；本地 `<gamedir>/apricity/` 同名文件优先 |
| 未安装 KubeJS | V2 | HUD 正常显示 |

***

## 十四、待决事项

1. **玩家覆盖键是否带载具/部件型号**：`seat:<子系统名>` 与 `seat:<型号>:<子系统名>` 之间尚待选择；示例使用前者。两台车共用同一个子系统定义时会共享覆盖。
2. **元素级复用与部分覆盖**：当前布局是复用单位，元素按完整投放值定义；如果将来需要跨布局共享一个元素，再加显式引用，不做部分覆盖。
3. **`scissor` 的归属**：当前放在样式里；其 x/y 是相对屏幕中心的屏幕坐标，与"样式只描述内部"的边界冲突。应移到元素、使用元素局部坐标，或继续留在样式并接受例外。
4. **玩家自定义命名布局**：v1 只保存每宿主一份覆盖，命名多套布局留待后续。
5. **AUI 动态列表**：按发射管/弹链数量重复界面时，需定义每段的稳定下标与热重载行为；该机制当前未实现。
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
