# 武器控制器 HUD 词表与宿主接入设计

> 版本 1.8 · 2026-10-22
> **状态**：已实现。控制器宿主、业务快照、查询绑定、候选并集、条目显隐和重复列表均已落地；实机画面验收另行跟踪。运行期模型与 AUI 武器元素由 `CustomHud` 按 WC 布局统一调度。代码坐标、示例、弹道标定边界与验证范围见《[HUD 上下文实现与验收](HUD上下文实现与验收.md)》。
>
> 本文定义每台武器控制器自己的 HUD 元素、显示快照、`weapon.*` 查询及重复项的 `tube.*` / `belt.*` 查询。通用元素的 `hud.*` 与上下文继承见《[观瞄系统-HUD专用MoLang上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》；样式、布局、元素及玩家覆盖见《[界面-HUD样式挂载与布局设计](../进行中/界面-HUD样式挂载与布局设计.md)》；信息量分级、武器类型画像与炮镜装填环见《[武器系统-武器HUD信息分级与视角形态需求分析](../计划中/武器系统-武器HUD信息分级与视角形态需求分析.md)》。

## 一、目标与宿主

| 目标 | 设计 |
| --- | --- |
| 控制器独立显示 | 每台控制器的 `first_hud_layout` / `third_hud_layout` / `scope_hud_layout` 各引用一份布局；运行时它为自己的布局条目创建武器 HUD 元素 |
| 候选、选中与显示 | 当前座椅所有控制组发现的 WC 并集构成候选；base + 当前子组决定选中状态；布局条目 `selection_visibility` 决定常驻或仅选中时显示，模型与 AUI 通用；不区分主/副武器 |
| 内容包默认值 | 布局是元素数组，元素引用样式并定义投放 |
| 玩家自定义 | 客户端按 `wc:<子系统名>` 保存布局覆盖；重置回到内容包布局 |
| 独立取数 | 武器元素直接绑定所属 `WeaponControllerSubsystem`；`weapon.*` 读取整台控制器，`tube.*` / `belt.*` 读取显式安装的当前项作用域 |

控制器动态属性中的布局引用示例：

```jsonc
{
  "type": "machine_max:weapon_controller",
  "definition": "machine_max:basic_weapon_controller",
  "control_outputs": { "weapon_control": ["subsystem.machine_max.main_launcher"] },
  "first_hud_layout": "machine_max:wc_gunner_first",
  "third_hud_layout": "machine_max:wc_gunner_third",
  "scope_hud_layout": "machine_max:wc_gunner_scope"
}
```

字段缺失表示该视角没有 HUD。控制器可使用三个视角字段；座椅自身提供仪表，相机在 `scope_hud_layout` 提供炮镜边框、遮罩等基础元素，武器控制器提供弹药、装填与弹道分划。运行时分别确定以下三层：

1. **候选集合**：当前观察座椅所有控制组（base + 全部子组）经发现握手得到的控制器并集，按控制器实例去重。遍历按 base、控制组配置顺序进行，不依赖并发 Map 的迭代顺序。范围限定在该座椅发现的控制器，不扫描整辆装配体上与此座椅无关联的武器。
2. **选中集合**：`src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractControllableSubsystem.java#getActiveWeaponControllers` 给出的 base + 当前子组并集。选中只说明当前组包含该控制器，不等同于启用、开火或取得独占控制权。
3. **条目可见性**：候选控制器在当前视角的每个 `HudElement` 读取 `selection_visibility`。`selected_only`（缺省值）要求控制器被选中；`always` 允许未选中时显示。普通座椅/相机元素不受选中条件影响。公共 HUD 调度层在模型和 AUI 的各绘制入口前统一判定，不能只靠 CSS 隐藏 AUI。字段契约及混合载体示例见框架文档 §4.3。

常驻限定在该座椅及当前视角有布局的期间；离开座椅、换座或控制器离开候选集合时不继续显示。候选集合不按 `isActive` / `isDestroyed` 过滤，控制器停用或受损不直接决定条目显隐。HUD 不设焦点组，也不按主/副分组；多台布局重叠由内容包作者处理。

发现缓存位于 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/AbstractControllableSubsystem.java#weaponControllersByGroup`，涵盖全部组。切换当前子组只更新选中状态及条目可见性，保留候选控制器的运行时元素和 AUI Document；不因选中集合变化整体销毁页面。控制组配置或车辆结构变化时通过 `AbstractControllableSubsystem.java#weaponControllerDiscoveryHandshake` 刷新发现缓存，再协调候选集合。

`weapon.selected` 为相对于当前观察座椅的 0/1 查询；同一控制器被多个座椅发现时，各观察者可得到不同结果。选中状态与本轮武器快照一起提供给元素上下文，但不写入控制器的全局状态或供所有观察者共用的业务快照。作者可将该值用于未选中面板的亮度、边框或详细信息；`belt.selected` 则表示供弹弹链选择，两者语义独立。

**代码入口**：`src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/CustomHud.java#tick` 收集候选并集并发布观察者选中状态；各绘制入口检查 `HudRuntimeElement#visible`。

## 二、每台控制器的显示快照

客户端每 tick 为候选控制器构造或共享本控制器的不可变业务快照。快照不包含观察座椅、选中状态、控制组序号或角色；元素求值帧把这份快照与观察者的选中状态关联，保证同一轮求值一致。当前视角布局和条目可见性决定是否提交绘制，不同样式由控制器的 `*_hud_layout` 决定。隐藏期间可跳过页面数据写回，但恢复绘制前须以最新快照完成刷新。

```java
public record HudWeaponSnapshot(
        String name,
        String state,               // ready / reloading / empty
        int ammoRemaining,
        int ammoCapacity,           // < 0 表示无容量概念
        boolean ammoInfinite,
        String ammoText,            // 27/120 或 27/∞
        String roundLabel,
        boolean roundMismatch,
        String reloadText,
        @Nullable ResourceLocation activeAmmoKey,
        List<BeltSnapshot> belts,
        List<TubeSnapshot> tubes
) {}

public record TubeKey(UUID partUuid, String subPartName, String subsystemName) {}
public record BeltSnapshot(List<ResourceLocation> key, String label, int remaining, boolean selected) {}
public record TubeSnapshot(
        TubeKey key, String roundLabel, String state, float fill,
        double reloadRemaining, String reloadText,
        String chamberedAmmoLabel, String loadingBeltLabel
) {}
```

这是业务结构示意。实际类型为 `HudWeaponSnapshot` 及其嵌套 `Tube` / `Belt`；`activeAmmo` 使用注册键字符串，管身份复用 `IAmmoSupplier.SupplierRef`。`TubeKey` 用发射器所属 Part UUID、SubPart 名和子系统名定位实例，不使用显示名称或数组下标。`BeltSnapshot.key` 是完整的展平弹链序列，顺序和重复次数都参与相等比较，例如 `[AP, AP, HE]` 与 `[AP, HE]` 是两条不同弹链。快照中的列表及身份键列表须防御性复制为不可变值。`TubeSnapshot.fill` 使用 0～100，与 `tube.fill`、`weapon.tube_fill(i)` 及百分比 CSS 绑定一致。`reloadRemaining` 使用秒：非装填状态为 0，正在装填但无法取得剩余时间时为 -1。`chamberedAmmoLabel` 只取实际膛内弹药，空膛为空；`loadingBeltLabel` 只取该管实际正在装填的供弹来源，未装填或来源无标签时为空，不用控制器当前选中弹链代替。`roundLabel` 是可包含供给者指针态的行显示文本，与真实膛内标签分别存储。

身份口径可对照 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/IAmmoSupplier.java#SupplierRef` 的三段坐标与 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/IAmmoSupplier.java#getLabel` 的显示名约束；弹链集合来自 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/WeaponControllerSubsystem.java#getAvailableBelts`。

单台控制器的取数规则由 `src/main/java/io/github/sweetzonzi/machine_max/client/render/gui/hud/molang/HudWeaponSnapshot.java#capture` 实现：

| 量 | 口径 |
| --- | --- |
| 发射管顺序 | 按发射器子系统名排序，同名时按 `TubeKey` 的 Part UUID、SubPart 名、子系统名作稳定决胜；被摧毁或未激活者不占槽位 |
| 弹种槽与 `activeAmmoKey` | 第一根膛内有弹的管提供弹种；都没有时退回第一根有供给者的管的指针态。分划只使用确实有注册键的弹种，没有时返回空 |
| 弹药计数 | 供弹弹链在弹药池中全部装弹机的余量/容量之和，池内按实例去重；池里没有该弹链时退回已绑定供给者之和 |
| 口径不符 | 膛内弹种的注册键不在供弹弹链里 |
| 弹链项 | 每条可选弹链一项；快照、默认选择与循环切换共用本节规定的排序规则。名称取池中首个条目的弹链名，余量为该弹链全部条目之和，供弹那条标记为选中；同名弹链按完整序列区分 |
| 装填段 | 每根有效发射器一段：有膛弹常亮；空膛显示供给者状态与进度；无供给者熄灭 |
| 尾标 | 全空显示空弹；任一装填中显示最慢一段的剩余时间或百分比；否则显示就绪 |

发射器数和可选弹链数来自该控制器关联的载具结构。弹链耗尽时保留条目并切换状态；控制器被摧毁或未激活时，只要其引用属于当前座椅的候选集合，就保留宿主与布局条目；条目按 `selection_visibility` 判定显隐。

稳定排序的代码入口是 `src/main/java/io/github/sweetzonzi/machine_max/common/mech/subsystem/WeaponControllerSubsystem.java#compareBelts`：完整弹链资源键序列按字典序比较，公共前缀相同时短序列在前；`BELT_ORDER` 引用该方法，默认选择、轮切和 HUD 共用同一顺序。

## 三、武器与当前项词表

`WeaponHudMolangContext` 继承通用 `HudMolangContext`。武器元素的 `weapon.*` 始终读取所属整台控制器，`tube.*` / `belt.*` 分别要求当前发射管/弹链作用域；普通座椅、相机元素使用这些武器业务查询时须得到可定位诊断。无参值按属性书写，显式下标或距离按函数书写。

| 查询 | 返回 | 说明 |
| --- | --- | --- |
| `weapon.name` | string | 所属控制器的子系统名 |
| `weapon.selected` | 0/1 | 所属控制器是否属于当前观察座椅的 base + 当前子组选中集合；与启用/开火/控制权无关 |
| `weapon.state` | string | `ready` / `reloading` / `empty` |
| `weapon.ammo_remaining` | number | 供弹弹链余量合计 |
| `weapon.ammo_capacity` | number | 容量合计；负数表示无容量概念 |
| `weapon.ammo_infinite` | 0/1 | 是否存在无容量概念的装弹机 |
| `weapon.ammo_text` | string | 如 `27/120`、`27/∞` |
| `weapon.round_label` | string | 弹种槽文本 |
| `weapon.round_mismatch` | 0/1 | 膛内弹种不在供弹弹链上 |
| `weapon.reload_text` | string | 剩余时间或进度的显示文本 |
| `weapon.active_ammo` | string | 当前弹药注册键；没有时为空 |
| `weapon.tube_count` | number | 有效发射管数 |
| `weapon.belt_count` | number | 可选弹链数 |
| `weapon.tube_state(i)` | string | 第 i 根管的状态 |
| `weapon.tube_fill(i)` | number | 第 i 根管的填充比例，0～100 |
| `weapon.tube_round_label(i)` | string | 第 i 根管的弹种或供给者指针态文本 |
| `weapon.tube_reload_text(i)` | string | 第 i 根管的装填时间或进度 |
| `weapon.tube_reload_remaining(i)` | number | 第 i 根管剩余装填秒数；非装填为 0，装填中未知为 -1 |
| `weapon.tube_chambered_ammo_label(i)` | string | 第 i 根管的真实膛内弹药标签；空膛为空 |
| `weapon.tube_loading_belt_label(i)` | string | 第 i 根管实际正在装填的弹链标签；不是控制器当前选中弹链的别名 |
| `weapon.belt_label(i)` | string | 第 i 条弹链的名称 |
| `weapon.belt_remaining(i)` | number | 第 i 条弹链的余量 |
| `weapon.belt_selected(i)` | 0/1 | 第 i 条是否为当前供弹弹链 |

`i` 从 0 开始，按快照中的稳定顺序取值；越界时数值返回 0、字符串返回空。整台控制器与显式索引查询位于 `weapon.*`；当前项查询位于 §3.2 的 `tube.*` / `belt.*`。`math.*` 是 Spark 通用词表；`local.*` / `global.*` 持续提供 Part 与装配体的通用数据，HUD 视角与视口查询使用 `hud.*`。

MoLang 不提供可靠的字符串拼接，故 `ammo_text`、`reload_text` 等组合文本由查询直接提供，或由模型文本槽的本地化键和 `molang_args` 填入；数值仍可在表达式中运算。

### 3.1 炮镜分划

武器控制器的 `scope_hud_layout` 可以挂分划模型。`weapon.bdc_hold_rad(distance_m)` 按所属控制器当前弹药读取已缓存的标准标定表，返回给定距离的 hold-over 角（弧度）。分划样式自行标定角度与骨骼位移的关系；通用 `hud.*` 不承担模型坐标换算。

相机无需识别“服务哪台武器”。弹道缓存的输入与无弹时的降级见通用上下文文档 §三.1。多台控制器各读自己的弹药；各自的 `scope_hud_layout` 提供分划条目，再由该条目的 `selection_visibility` 决定是否提交绘制。

### 3.2 当前项的无参查询

| 当前项查询 | 显式索引对应项 | 作用域与口径 |
| --- | --- | --- |
| `tube.index` | 无 | 当前发射管在本轮快照中的下标，从 0 开始；不是身份键 |
| `tube.state` | `weapon.tube_state(i)` | 当前管状态 |
| `tube.fill` | `weapon.tube_fill(i)` | 当前管填充百分比，0～100 |
| `tube.round_label` | `weapon.tube_round_label(i)` | 当前管的行显示文本 |
| `tube.reload_text` | `weapon.tube_reload_text(i)` | 当前管的装填时间或进度文本 |
| `tube.reload_remaining` | `weapon.tube_reload_remaining(i)` | 当前管剩余装填秒数，含义见 §二 |
| `tube.chambered_ammo_label` | `weapon.tube_chambered_ammo_label(i)` | 当前管的真实膛内弹药标签 |
| `tube.loading_belt_label` | `weapon.tube_loading_belt_label(i)` | 当前管实际正在装填的供弹来源标签 |
| `belt.index` | 无 | 当前弹链在本轮快照中的下标，从 0 开始 |
| `belt.label` | `weapon.belt_label(i)` | 当前弹链标签 |
| `belt.remaining` | `weapon.belt_remaining(i)` | 当前弹链库存余量 |
| `belt.selected` | `weapon.belt_selected(i)` | 当前弹链是否被所属控制器选为供弹弹链 |

`data-mm-repeat="weapon.tubes"` / `"weapon.belts"` 给条目根及后代的数据槽安装对应类型的当前项作用域。绑定器按稳定身份键在本轮不可变快照中解析成员及下标；Java 无参方法读取上下文里的当前项，不根据调用次数推断序号。区域外或类型不匹配时须诊断并使该槽位降级，不隐式取第零项。所有重复项内的 `weapon.*` 仍读取整台控制器。

显式索引接口服务于模型 HUD 固定骨骼、非重复页面和跨项比较；模型骨骼/文本槽没有 DOM 重复作用域时使用该接口。未来若支持模型槽位声明绑定某根管，可复用同一作用域机制；首版不从骨骼名推测绑定。Java 注入、异常恢复、线程隔离和编译器边界见《[HUD 专用 MoLang 上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》§3.3。无参查询使用独立名字，不以省略 `weapon.tube_fill(i)` 的参数表达当前项。

## 四、样式与 AUI 页面

首批武器样式可提供 `standard`（完整状态）、`minimal`（名称/余量/状态）与 `full`（完整状态及火控预留槽）。它们只定义内部模型或页面，位置与炮镜行为由不同布局条目决定。

以下是**单管** AUI 页面示例。`data-mm-text` 与 `data-mm-num` 由 Machine-Max 的 `HudPageBinding` 实现，不属于 AUI 原生属性：

```html
<div class="mm-weapon">
  <span data-mm-text="weapon.name"></span>
  <span data-mm-text="weapon.ammo_text"></span>
  <i data-mm-num="weapon.tube_fill(0)" data-mm-var="--fill" data-mm-unit="%"></i>
</div>
```

### 4.1 发射管与可选弹链列表

内容包定义“一项”的元素和完整子树，在条目根声明 `data-mm-repeat`。绑定器按稳定身份键维护同一 Document 内的重复项，根及后代默认读取当前项无参查询；`data-mm-index` 是可选的下标变量别名。集合标识不是 MoLang 表达式，业务数据来自 §二 的不可变快照。

| `data-mm-repeat` | 项身份 | 默认查询 |
| --- | --- | --- |
| `weapon.tubes` | `TubeSnapshot.key` | `tube.fill`、`tube.reload_text`、`tube.index` |
| `weapon.belts` | `BeltSnapshot.key`，完整有序弹链序列 | `belt.label`、`belt.remaining`、`belt.selected`、`belt.index` |

`weapon.belts` 表示可选弹链，不是投射物类型的去重集合。一条混装弹链可包含多种弹药和重复弹种；同一条弹链由多台装弹机供给时只有一个列表项，其余量按 §二 聚合。耗尽的弹链保留条目；发射管的纳入规则由 §二 决定，重复器不另外按装填状态过滤。

```html
<div class="tubes" style="display: flex; flex-direction: column; gap: 6px;">
  <div class="tube" data-mm-repeat="weapon.tubes" data-mm-class="tube.state">
    <span data-mm-text="tube.chambered_ammo_label"></span>
    <span data-mm-text="tube.loading_belt_label"></span>
    <span data-mm-text="tube.reload_text"></span>
    <i data-mm-num="tube.fill" data-mm-var="--fill" data-mm-unit="%"></i>
  </div>
</div>
<div class="belts" style="display: grid; grid-template-columns: repeat(4, 1fr); gap: 4px;">
  <span data-mm-repeat="weapon.belts" data-mm-text="belt.label"></span>
</div>
```

按下标定位可用 `data-mm-num="tube.index * 28"` 写入带 `px` 单位的 CSS 变量。声明 `data-mm-index="row"` 时也可使用 `variable.row`，它与本项 `tube.index` 或 `belt.index` 相等，并按求值作用域保存/恢复。页面方向、间距、换行及逐项偏移由 CSS 定义；`HudElement.offset`、`anchor`、`scale` 投放整块页面。Grid 四列可排任意数量的管，四个模型骨骼槽位只覆盖其显式索引。模板及作用域的完整语法、非法区域诊断、快照重排与节点复用见框架文档 §8.5、§8.6。

## 五、实施与验收

框架文档 §十二的 V3 阶段接入武器显示快照、查询及候选宿主策略。单控制器快照由 `HudWeaponSnapshot#capture` 提供，模型和 AUI 武器元素消费同一口径；其源码路径见第二章的取数规则。内容包与玩家的 `*_hud_layout` 采用框架文档 §六、§七的解析规则，公共显隐调度采用 §4.3。

验收至少覆盖：候选并集仅来自当前座椅、包含未选中组且按实例去重；同一控制器的常驻面板与仅选中分划分别显隐；模型与 AUI 使用相同规则；切组更新 `weapon.selected` 且保留运行时元素/Document；不同座椅观察同一控制器时选中值隔离；多台数据互不串值；停用/受损不自动剔除候选；无布局时为空；膛内弹药、实际装填弹链和当前选中弹链能同时区分；无参项查询、显式索引、排序变化和异常恢复均取到正确数据；无作用域查询给出诊断；玩家覆盖及重置保留条目显隐策略。代码和自动化测试已覆盖的项目见实现记录；实机交互与画面效果需按本清单验收，玩家布局覆盖属于框架 V4。

## 六、待决事项

1. **模板实例化技术路径**：§4.1 的集合、身份、下标与 CSS 契约已定义；实现使用 `Document.createHTML` 的解析管线；普通文本/图片、Canvas 属性和行内事件监听的初始化已有测试，见实现记录。
2. **缺失弹道数据**：`weapon.bdc_hold_rad` 目前以 0 降级；样式如需区分“零修正”与“无数据”，可增加单独的可用性查询。
3. **武器样式来源**：内置三套样式使用 `machine_max` 命名空间；内容包按框架资源规则提供自己的样式。

## 七、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-16 | 提出武器状态 HUD 的快照、样式与布局 |
| 1.1 | 2026-10-16 | 由通用框架承载样式、挂载、布局与玩家覆盖；本文聚焦武器数据 |
| 1.2 | 2026-10-16 | 定义每控制器自有元素和快照、`weapon.*` 查询、控制器 `scope` 分划与 AUI 重复段边界 |
| 1.3 | 2026-10-17 | 控制器字段改为按视角的 `first_hud_layout` / `third_hud_layout` / `scope_hud_layout`；删除 `HudMount` 与布局组概念，布局为元素数组 |
| 1.4 | 2026-10-18 | 可见性改为座椅当前控制组的选中集合：所有选中的 WC 一同显示，不区分主/副；多台重叠由内容包布局处理；快照只含本控制器数据 |
| 1.5 | 2026-10-18 | 宿主接入落地：三个 `*_hud_layout` 进入动态属性，`WeaponControllerSubsystem` 实现 `IHudHost`，`CustomHud` 按宿主集合选择；武器专用元素、快照与 `weapon.*` 仍未实现 |
| 1.6 | 2026-10-20 | 定义发射管/可选弹链的重复标记与索引、快照身份键和 CSS 内部排布；关联通用绑定器生命周期与 AUI 初始化验证，动态列表尚未实现 |
| 1.7 | 2026-10-21 | 定义座椅候选并集、观察者选中状态与模型/AUI 通用条目显隐；定义当前项无参查询、实际膛内/装填来源及显式索引并存契约 |

| 1.8 | 2026-10-22 | 落实快照、查询、候选显隐与 AUI 列表；提供内置动态页面和标准弹道标定缓存，实机验收待完成 |
