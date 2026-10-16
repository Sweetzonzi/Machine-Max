# HUD 样式、挂载与布局设计

> 版本 1.2 · 2026-10-16
> **状态**：计划中（未实现。代码中的 HUD 定义仍是单一 codec：`MMDynamicRes.CUSTOM_HUD` 存 `HudAttr`，表现与投放写在同一个文件里；宿主用内联 RL 列表声明；尚无 `HudStyle` / `HudMount` / `HudLayoutSet` 三个注册表，也没有玩家覆盖）

***

## 一、要解决的问题

### 1.1 现状

| 环节 | 现状 |
| --- | --- |
| HUD 定义 | `huds/*.json`（4 份：`example_hud`、`example_hud_2`、`gun_sight_hud`、`gun_sight_hud_fixed`），由 `HudModule` 读入 `MMDynamicRes.CUSTOM_HUD`。**表现与投放（`offset` / `rotation` / `scale` / `perspective`）写在同一个文件里** |
| 相机宿主 | `CameraSubsystemStaticAttr.hud_components`：裸 `ResourceLocation` 列表 |
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
| 投放可复用、可命名 | `HudMount` 是具名资源，一份投放可被多个宿主引用 |
| 三方宿主同形 | 相机 / 座椅 / 武器控制器都只写一个 `hud_layout: ResourceLocation` |
| 内容包默认与玩家覆盖同 codec | 包内 `hud_layouts/*.json` 与玩家本地布局文件共用布局格式；拖动位置的表示须按 §十四第 5 项确定 |
| 两个载体对等 | 基岩模型（`model`/`animation`/`texture`/`texts`）与 AUI 页面（HTML/CSS + Molang 绑定）都是 `HudStyle` 的一个 `type` |
| 内容包能自带页面 | 见 §九 的资源桥 |

### 1.3 非目标

- 世界空间 HUD（`WorldWindow` 形态的铭牌/显示屏）不在本文范围；
- 页面脚本（`<script>`）通道不在本文范围：它由 KubeJS 提供，而 KubeJS 不是本模组的必需依赖，未安装时脚本静默不执行。本文的数据通道只用 Molang 绑定（§八）。

***

## 二、术语与三层资源

```text
宿主  ──引用──▶  HudLayoutSet  ──列出──▶  HudMount  ──引用──▶  HudStyle
持有布局             按视角分组的挂载清单        样式 + 投放               内部表现
```

| 术语 | 含义 |
| --- | --- |
| **样式（HudStyle）** | 一份 HUD 的内部：模型载体的 `model`/`animation`/`texture`/`color`/`alpha`/`scissor`/`texts`，或 AUI 载体的 `page` |
| **挂载（HudMount）** | 一个样式 RL + 一组完整的投放值（投影、炮镜行为、锚点、偏移、旋转、缩放、层级） |
| **布局（HudLayoutSet）** | 按**视角组**组织的挂载清单：`{ first: [...], third: [...], scope: [...] }` |
| **宿主（host）** | 声明"我要用哪份布局"的子系统：相机、座椅、武器控制器 |
| **视角组（view group）** | `first` / `third` / `scope`。三者互斥（`scope` 态强制第一人称，见 §六.3） |
| **玩家覆盖** | 玩家本地按宿主键保存的布局，优先于内容包预设；偏移/缩放的持久化表示见 §十四第 5 项 |

### 2.1 与控制组预设的对照

控制组提供“具名资源 + 玩家可改”的现有形态，HUD 的玩家文件也需要可编辑的布局数据。挂载位置的持久化表示仍须按 §十四第 5 项确定：

| | 控制组（现状） | HUD（本文） |
| --- | --- | --- |
| 具名资源 | `ControlGroupSet` ← `control_groups/*.json`，`ControlGroupModule` 读入 `MMDynamicRes.CONTROL_GROUP_PRESETS` | `HudStyle` ← `huds/*.json`；`HudMount` ← `hud_mounts/*.json`；`HudLayoutSet` ← `hud_layouts/*.json` |
| 宿主引用 | `control_group_preset: ResourceLocation`（哨兵值表示不用预设） | `hud_layout: ResourceLocation` |
| 包内 JSON | `ControlGroupSet.CODEC` | `HudLayoutSet.CODEC`；玩家文件需在该格式上表达每实例的位置 |
| 玩家改过的副本 | `ControlGroupSetEditPayload` + `STREAM_CODEC` → 服务端 `setControlGroupSet(...)` | 不过网络：本机表现，写客户端文件 |
| 重置 | `restoreControlGroupPreset()` | "重置为预设" = 删除该宿主的玩家覆盖项 |
| 编辑入口 | `VehicleControlScreen` + `PanelConfigEditor` | 同一屏新增"HUD 布局"页 |

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
| `scissor` | 裁剪矩形。它裁剪的是这份样式自身的绘制区，因此留在样式里 |
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
| `page` | 页面逻辑路径，经 §九 的资源桥解析 |
| `defaults` | 定义级 CSS 变量，可选；页面自身仍可用样式表 |

页面的结构与取值见 §八。

### 3.3 为什么"行为"不算内部

`gun_sight_hud.json` 与 `gun_sight_hud_fixed.json` 使用**同一个模型、同一份动画与纹理**，差异只有 `scope_behavior`（`follow_position` 与 `screen_fixed`）与 `ignore_zoom`。这两个字段描述的是"这份分划装在什么上、在炮镜里怎么动"，属于挂载；拆开之后两份样式合并为一份，两种行为变成两个挂载。

***

## 四、HudMount：样式 + 投放

```jsonc
// hud_mounts/gunner_reticle.json
{
  "hud": "machine_max:gun_sight_hud",     // 样式 RL（模型或 AUI，同一张注册表）
  "perspective": false,                   // true=透视投影（offset 单位为米）；false=正交投影（offset 单位为像素）
  "scope_behavior": "screen_fixed",       // 仅 scope 视角组有意义
  "ignore_zoom": true,                    // 仅 scope 视角组有意义
  "anchor": "center",                     // AUI 载体用；模型载体以屏幕中心为原点
  "offset": [0, 0, 0],
  "rotation": [-25, -30, 0],
  "scale": [25, 25, 25],
  "z": 0,
  "order": 10
}
```

| 字段 | 模型载体 | AUI 载体 |
| --- | --- | --- |
| `perspective` | 读 | 忽略（提示警告） |
| `scope_behavior` / `ignore_zoom` | 读 | 忽略（提示警告） |
| `anchor` | 忽略 | 读：`top_left` … `bottom_right` / `center` |
| `offset` | 读三维（透视下米、正交下像素），相对屏幕中心 | 读二维逻辑像素（`z` 忽略） |
| `rotation` | 读（度） | 忽略：AUI 的旋转属于样式（CSS `transform`） |
| `scale` | 读三维 | 读单值（取 x） |
| `z` / `order` | 读 | 读 |

规则：**一个挂载就是一组完整投放值**，不做"部分覆盖"。需要另一种投放就再写一个挂载文件。理由是每项投放只有一个权威来源，避免"这个值从哪儿来"的推理。

### 4.1 为什么挂载是具名资源而不是内联对象

现有内容包里，`machine_max:example_hud` 与 `example_hud_2` 被 `dayun`、`senna_gtr`、`vn`、`golden_closestool`、`k17`、`sdkfz234` 等座椅以主/副视角共约 10 处引用，且共用同一组投放。若投放内联在引用点，同一组数要抄 10 遍，改一次仪表盘布局要改 10 个文件。具名挂载让"一处定义、多处引用"成立。

***

## 五、HudLayoutSet：按视角分组的挂载清单

```jsonc
// hud_layouts/dashboard.json
{
  "first": ["machine_max:mount/dashboard_fp"],
  "third": ["machine_max:mount/dashboard_tp"]
}
```
```jsonc
// hud_layouts/gunner.json —— 三个视角组都在：炮镜下继续显示弹药
{
  "first": ["machine_max:mount/wc_full"],
  "third": ["machine_max:mount/wc_full"],
  "scope": ["machine_max:mount/wc_mini"]
}
```

规则：

1. **宿主没有的视角组被忽略**。相机只认 `scope`；座椅只认 `first` / `third`；武器控制器三组都认。宿主布局里出现它不认的组时不报错、不显示。
2. **空组 = 该视角不显示**；`{}`（整体为空）= 该宿主没有 HUD。没有单独的 `hidden` 开关。
3. 组内顺序即绘制顺序，`order` 用于同组内的显式排序。

```java
public record HudLayoutSet(
        List<ResourceLocation> first,
        List<ResourceLocation> third,
        List<ResourceLocation> scope) {
    public static final Codec<HudLayoutSet> CODEC = ...;   // 当前仅定义资源列表；玩家偏移尚未表达
    public List<ResourceLocation> forView(ViewGroup group) { ... }
}
```

***

## 六、宿主：三方同形

### 6.1 引用形态

| 宿主 | 字段（迁移后） | 视角集合 | 玩家覆盖键 |
| --- | --- | --- | --- |
| 相机 | `CameraSubsystemStaticAttr.hud_layout`（取代 `hud_components`） | `scope` | `camera:<子系统名>` |
| 座椅 | `SeatSubsystemStaticAttr.ViewAttr.hud_layout`（取代 `first_person_hud` / `third_person_hud`） | `first` / `third`（`THIRD_PERSON_FRONT` 归 `third`） | `seat:<子系统名>` |
| 武器控制器 | 动态属性 `hud_layout`（`WeaponControllerSubsystemAttr`） | `first` / `third` / `scope` | `wc:<子系统名>` |

座椅不设 `scope` 组：炮镜态下要显示的东西挂在该炮镜自己的相机布局上（相机宿主天然只在 `scope` 态出现），或者挂在武器控制器的布局的 `scope` 组里。

### 6.2 数据上下文由持有者决定

每个运行时挂载生成自己的 HUD 元素，元素缓存自己的 MoLang 上下文。所有元素可读 `hud.*`（当前渲染视角与视口）；武器控制器元素继承通用元素，并增加直接取自所属控制器的 `weapon.*`。`math.*` 属于 Spark 通用词表；载具 HP、能量与信号等继续从 `local.*` / `global.*` 读取，HUD 元素通过所属 Part/装配体关联它们。类层次和查询口径见《[观瞄系统-HUD专用MoLang上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》。

| 持有者 | 自有数据与炮镜职责 |
| --- | --- |
| 座椅 | 座椅布局只产生自己的仪表等元素；没有 `weapon.*` |
| 相机 | `scope` 视角下产生边框、遮罩等基础元素；没有 `weapon.*` |
| 武器控制器 | 直接绑定这台控制器；弹药、装填与弹道分划都在自己的元素中，`scope` 组可挂炮镜分划 |

相机无需反查武器，座椅也不替武器 HUD 选取控制器。一个相机边框和多台控制器的 HUD 可以同时处于 `scope` 视角；各元素只读取自己的上下文。若多台控制器都配置了分划，是否显示由各自的布局和激活规则决定。

### 6.3 视角三态互斥

`scope` 态强制第一人称（`CameraController.tickCameraMode` 里做视角强制），`CustomHud.tick()` 也是"先判是否相机模式，否则按 `options.getCameraType()` 选列表"。因此运行期只会是 `first` / `third` / `scope` 之一，可以用分组列表表达，不需要"叠加显示条件"的位掩码。

***

## 七、玩家覆盖

- **解析顺序**：玩家覆盖 > 宿主引用的布局预设 > 空（该宿主无 HUD）。
- **存储**：客户端文件 `config/machine_max/hud_layouts.json`，键为 §6.1 的覆盖键。值包含视角组与挂载清单；拖动产生的每实例位置须按 §十四第 5 项加入格式后才能落盘。`ControlPreference` 走玩家 Attachment 是因为服务端要读它参与玩法判定；HUD 布局只影响本机表现，因此不入网络、不入载具 NBT。
- **重置**：删除该键，回到预设。
- **编辑入口**：`VehicleControlScreen`（AUI Screen，本身接收鼠标，已有 3D 预览拖拽代码）新增"HUD 布局"页：拖拽调整挂载位置、增删挂载、切换挂载用的样式、保存/重置。位置编辑须以 §十四第 5 项的数据格式为前提。
- **叠层不拦截输入**的契约继续保留：显示态的 HUD 文档不声明 `<meta name="aui-mouse-events">`，编辑只在 Screen 内进行。
- **v1 只做单一覆盖**：一个宿主一条覆盖项。玩家自定义的多套命名布局留到有需求时再加；存储格式须先满足单套布局的位置编辑。

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
| `data-mm-repeat` | 暂定的动态列表标记；按实际发射管或弹链数自动生成“一项”的多段界面。它只重复显示结构，不复制武器或 HUD 对象；每段如何取得自己的下标仍待设计 |

例如四根发射管的样式只需定义一段装填条，绑定层在页面中生成四段，并依次绑定到第 0～3 根管。**当前没有可用的 `data-mm-repeat` 实现或下标约定**，因此可执行示例只展示明确下标的单管查询 `weapon.tube_fill(0)`；多管页面的表达式写法须等绑定层确定“当前项”规则后再发布。模型载体的骨骼槽位固定，可在确定管数的样式中显式写下标。

### 8.3 扫描与求值时机

| 时机 | 动作 |
| --- | --- |
| 元素创建、页面加载或热重载 | 按运行时元素关联上下文，扫描数据标记并编译表达式；热重载可命中编译缓存 |
| 武器结构变化 | 只重建受影响控制器的区域；动态列表机制落地后按新条目数生成各段 |
| 每客户端 tick | 刷新武器快照与有效挂载，原子发布不可变值；对 AUI 数据槽求值，只在结果变化时写入 |
| 模型动画求值前 | 当前线程的上下文读取已发布快照，再计算消费 `hud.*` 的骨骼表达式 |
| 模型文本渲染前 | 客户端主线程上下文读取本次渲染的参数，求值 `molang_args` |

文本必须写到叶子元素，且只有值变化才改 `innerText`；类名 token 与 CSS 变量也只在变化时改写。`setAttribute` / `setInnerHTML` 不进逐 tick 路径，避免无谓的样式与布局重算。槽位缓存随元素或页面区域销毁而释放。

### 8.4 词表归属

- `hud.*`：通用 HUD 元素的当前相机视角与视口；座椅、相机、武器元素均可读。见《[观瞄系统-HUD专用MoLang上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》。
- `weapon.*`：仅武器控制器元素可读，包含所属控制器的弹药、发射管、弹链和分划弹道角。见《[武器系统-武器控制器HUD样式与布局设计](武器系统-武器控制器HUD样式与布局设计.md)》。
- `math.*`：Spark 通用查询；`local.*` / `global.*`：Part 与装配体查询，持续用于载具仪表。这三者不形成新的 HUD 业务前缀。

MoLang 不提供可靠的字符串拼接；`weapon.ammo_text` 等组合文本由查询返回，或由模型文本槽的本地化键和 `molang_args` 组装。普通元素使用 `weapon.*`、表达式语法错误或求值失败时，绑定层应标明出错槽位并记录一次可定位诊断，其他槽位继续刷新。

## 九、内容包资源桥

AUI 的资源解析链是：开发期项目根 → `<gamedir>/apricity/<path>` → 类路径 `assets/apricityui/apricity/<path>` → MC 资源管理器（资源包）。内容包模块（`spark_modules/`）的文件由 Spark-Core 读进内存，不在上述任何一条链上，因此**内容包当前无法自带 HTML 页面**。

AUI 提供了可替换的资源服务：`AuiServices.setResources(AuiResourceService)` 是公开 setter，不受 bootstrap 门控；唯一安装点是 NeoForge 的 `ClientServicesBootstrap`，它在 AUI 模组构造期执行。本模组在 `neoforge.mods.toml` 中声明 `ordering="AFTER"`（apricityui），因此在客户端侧安装一个委托包装器即可：

```java
AuiServices.setResources(new PackAwareResourceService(AuiServices.resources()));
// openResource(path):  先查 Spark 包图（内存中的包内文件）→ 命中即返回，否则委托
// listResourcePaths(): 合并包图条目与委托结果
```

落地要点：

| 项 | 要求 |
| --- | --- |
| 安装时机 | 客户端构造期或 `FMLClientSetupEvent`，必须晚于 AUI 的 `ClientServicesBootstrap`；幂等（重复安装不得套娃） |
| 线程安全 | `openResource` 会被样式与图片的异步处理器调用；包图用 volatile 快照，重载时换引用 |
| 缓存失效 | 安装后调用一次 `ClientLoader.invalidateStaticResourceCache()`，否则 AUI 的静态资源清单可能不含包内页面 |
| 优先级 | `<gamedir>/apricity/` 由 AUI 的文件系统分支先命中，天然高于本桥；本桥高于资源包 |
| 路径规范化 | 处理前导 `/`、`..`、大小写；后缀过滤与 `listResourcePaths` 的语义一致 |

这条桥同时服务两个载体：模型载体的模型/动画/纹理由内容包提供，AUI 载体从此也能自带页面。

***

## 十、迁移面

### 10.1 内容包

| 对象 | 现状 | 迁移后 |
| --- | --- | --- |
| `huds/example_hud.json`、`example_hud_2.json` | 表现 + 投放 | 两份样式（去掉 `offset`/`rotation`/`scale`/`perspective`）+ 各自若干挂载 |
| `huds/gun_sight_hud.json`、`gun_sight_hud_fixed.json` | 同模型两种行为 | 合并为一份样式 + 两个挂载 |
| `subsystems/*/*_seat.json` | `first_person_hud` / `third_person_hud` 两个列表 | 一个 `hud_layout`；布局文件 `hud_layouts/dashboard.json` |
| 相机/瞄具子系统 | `hud_components` | 一个 `hud_layout` |
| `docs/{zh_cn,en_us}/schemas/**` 与 `hud.md` | 单层 HUD 定义 | 分"样式 / 挂载 / 布局"三章；补三份 schema |

这是一次性迁移：旧字段不保留并存形式（旧字段与"按视角分组的布局"语义重复，两套并存必然分叉）。

### 10.2 代码

| 文件 | 改动 |
| --- | --- |
| `common/visual/HudAttr.java` | 拆为 `ModelHudStyle`（表现）与 `HudMount`（投放），或保留类名并按 §三/§四 裁剪字段 |
| `client/render/renderable/GuiAnimatable.java` | 模型载体读取样式与有效挂载；由通用 HUD 元素提供 `IAnimatable#getMolangContext()` |
| `client/render/gui/hud/CustomHud.java` | 解析宿主 `hud_layout` 与玩家覆盖，按挂载创建各自带上下文的运行时元素并分流渲染 |
| `external/MMDynamicRes.java` | `CUSTOM_HUD` 拆为样式、挂载、布局三个注册表 |
| `common/resource/modules/HudModule.kt` | 按路径与 `type` 分派三个 codec；增加 `hud_mounts` / `hud_layouts` 两个模块 id |
| 相机、座椅、武器控制器属性类 | 各提供单个 `hud_layout` 引用 |
| 新增 | `HudStyleRegistry` / `HudMountRegistry` / `HudLayoutRegistry`、玩家布局管理器、内容包 AUI 资源桥、通用/武器 HUD 元素与对应 MoLang 上下文、AUI 绑定层 |

RL 命名约定：挂载与布局的 RL 加前缀（`machine_max:mount/<文件名>`、`machine_max:layout/<文件名>`）。现有模块从**文件名**生成 RL（不带子目录），若把挂载放进 `huds/` 的子目录会与样式撞名；改 id 推导又会改变现有全部 RL。加前缀是零破坏的做法。

***

## 十一、性能与边界

### 11.1 成本模型

| 环节 | 频率 | 成本 |
| --- | --- | --- |
| 表达式编译 | 每个表达式一次（跨实例、跨热重载复用） | 按 `(上下文类, 表达式串)` 缓存 |
| 扫描 + 建槽位表 | 仅结构变化 / 热重载 | 一次 O(元素数) 遍历 |
| 求值 | 每客户端 tick | 槽位数 × 一次字节码调用 |
| 写入 | 每客户端 tick，仅变化时 | 文本写触发 AUI 文本重排；类名 token 未变不触发样式重算 |
| 布局写入 | 仅布局变化 | `setInlineStyleProperty` 每次都排一次样式重算 |

槽位数量级：一台控制器 5~15 个槽位、一个座位通常 ≤ 3 台控制器，合计 ≤ 50 槽位/页。

### 11.2 载体能力边界

| 需求 | 模型载体 | AUI 载体 |
| --- | --- | --- |
| 屏幕空间任意排版、文本、列表、图标 | 弱（骨骼 + 固定文本槽） | 强 |
| 贴镜头的 3D 元素（分划随动、透视偏移、忽略变焦） | 强 | 只能近似（屏幕空间的反向缩放） |
| 逐帧数据槽 | 有（`molang_args`） | 有（`data-mm-*` + 词表） |
| 动态条数（几根发射管 / 几条弹链） | 模型骨骼槽位固定，已知数量可显式写下标 | AUI 重复界面是待实现能力，须先确定每段的索引绑定 |
| 内容包投递 | 模型/动画/纹理（美术工具链） | HTML/CSS（§九 的资源桥） |

### 11.3 AUI 边界（按 1.2.7 本地源码核对）

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
| 每帧渲染 | 无脏区域渲染，逐帧遍历整棵绘制列表；同类 HUD 收在一个 Document 内，不为每个挂载建文档 |
| 页面脚本 | 由 KubeJS 提供；未安装时静默不执行。本文的数据通道不依赖它 |
| 输入拦截 | `Document.interceptMouseEvents` 只在文档构造与 `refresh()` 时从页面 meta 读取，没有运行时 setter；显示态 HUD 不声明该 meta |

> `docs/AUI性能边界.md` 的调研对象是 AUI 1.1.6-dev，其陷阱表中 `calc()/vw/vh`、`::before/::after`、`Element.querySelector` 三条与 1.2.7 本地源码不符。

***

## 十二、分阶段实施

| 阶段 | 内容 | 产出 |
| --- | --- | --- |
| **S1** | 三个注册表与 codec；模型载体按样式和挂载拆分 | `HudStyle` / `HudMount` / `HudLayoutSet` |
| **S2** | 相机、座椅切到 `hud_layout`；内容包迁移；按有效挂载创建运行时元素 | 旧内容包的座椅/相机 HUD 表现等价 |
| **S3** | 通用 `HudElement`、`HudMolangContext` 和 AUI 基础绑定；模型与 AUI 按元素求值 | `hud.*`、持续可用的 `local.*` / `global.*`、内容包资源桥 |
| **S4** | 玩家布局覆盖与编辑页 | `HudLayoutManager` 与编辑 UI；拖动位置的持久化表示须先解决 §十四第 5 项 |
| **S5** | 武器控制器自有 HUD、快照和武器上下文 | `WeaponHudElement`、`WeaponHudMolangContext`、`weapon.*`、控制器 `scope` 分划与武器样式 |

AUI 动态列表属于绑定层的后续能力：在确定“当前发射管/弹链下标”的传递规则后实现。S1/S2 不改变旧画面；S3 开始提供通用查询，S5 再把武器面板接入相同的样式/挂载/布局框架。

## 十三、验收

计划实现后在 `runClient` 进世界验证：

| 用例 | 覆盖 |
| --- | --- |
| 迁移后现有内容包表现不变 | 座椅/相机的 HUD 位置、缩放、投影和 `global.get('vehicle_speed')` 求值 |
| 一份挂载被两个宿主引用 | 默认投放共享；运行时每个元素的上下文独立 |
| 同一份样式的两种炮镜行为 | `screen_fixed` 与 `follow_position` 挂载读取各自生效参数 |
| 视角组过滤 | 座椅只显示 `first` / `third`，相机只显示 `scope` |
| 武器分划 | 相机边框使用普通上下文；每台武器的 `scope` 分划读取本控制器弹药，数据互不串值 |
| 玩家覆盖 | 编辑后显示值取有效挂载；保存/重进与重置按最终覆盖格式验证 |
| AUI 基础绑定 | 页面显示数值；错误只使对应槽位降级 |
| 内容包自带页面 | 包内页面由资源桥解析；本地 `<gamedir>/apricity/` 同名文件优先 |
| 未安装 KubeJS | HUD 仍正常显示 |

## 十四、待决事项

1. **玩家覆盖键是否带部件型号**：`seat:<子系统名>` 与 `seat:<型号>:<子系统名>` 之间尚待选择；示例使用前者。
2. **挂载是否允许部分覆盖**：内容包挂载目前按完整投放值定义。
3. **`scissor` 的归属**：当前放在样式里，因为它裁剪样式的绘制区；其屏幕坐标也与投放有关。
4. **玩家自定义命名布局**：v1 只保存每宿主一份覆盖，命名多套布局留待后续。
5. **拖动位置怎样持久化**：当前 `HudLayoutSet` 只有挂载 RL 列表，不能存储玩家拖动产生的偏移/缩放。S4 前须决定是在布局条目中加入每实例投放值，还是在玩家文件中保存完整的本地挂载副本；确定后才能让拖动、保存、重置闭环。投放数值供渲染使用，不进入 `hud.*` 查询面。
6. **AUI 动态列表**：按发射管/弹链数量重复界面时，需定义每段的稳定下标与热重载行为；该机制当前未实现。

## 十五、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-16 | 定义样式、挂载、布局、三方宿主、玩家覆盖与 AUI 载体 |
| 1.1 | 2026-10-16 | 明确通用/武器 HUD 查询分工及每元素上下文 |
| 1.2 | 2026-10-16 | 对齐元素与上下文继承、武器控制器 `scope` 分划及 AUI 重复界面的待实现边界；列出玩家拖动位置的持久化缺口 |
