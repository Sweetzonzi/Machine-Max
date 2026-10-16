# 武器控制器 HUD 词表与宿主接入设计

> 版本 1.2 · 2026-10-16
> **状态**：计划中。当前武器状态由 `client/render/gui/hud/WeaponHudOverlay.java` 从座椅取激活控制器、创建一块 AUI 面板并逐 tick 写入；控制器自有 HUD 尚未实现。
>
> 本文定义每台武器控制器自己的 HUD 元素、显示快照与 `weapon.*` 查询。通用元素的 `hud.*` 与上下文继承见《[观瞄系统-HUD专用MoLang上下文设计](观瞄系统-HUD专用MoLang上下文设计.md)》；样式、挂载、布局及玩家覆盖见《[界面-HUD样式挂载与布局设计](界面-HUD样式挂载与布局设计.md)》。

## 一、目标与宿主

| 目标 | 设计 |
| --- | --- |
| 控制器独立显示 | 每台控制器的 `hud_layout` 引用一份布局；运行时它为自己的挂载创建武器 HUD 元素 |
| 内容包默认值 | 布局列出 `first` / `third` / `scope` 视角的挂载，挂载引用样式并定义投放 |
| 玩家自定义 | 客户端按 `wc:<子系统名>` 保存布局覆盖；重置回到内容包布局 |
| 独立取数 | 武器元素直接绑定所属 `WeaponControllerSubsystem`，其上下文在通用 `hud.*` 上增加 `weapon.*` |

控制器动态属性中的布局引用示例：

```jsonc
{
  "type": "machine_max:weapon_controller",
  "definition": "machine_max:basic_weapon_controller",
  "control_outputs": { "weapon_control": ["subsystem.machine_max.main_launcher"] },
  "hud_layout": "machine_max:layout/wc_gunner"
}
```

字段缺失表示该控制器没有 HUD。控制器可使用三个视角组；座椅只显示自己的布局，相机在 `scope` 组提供炮镜边框、遮罩等基础元素。弹药、装填与弹道分划由武器控制器元素提供。控制器的激活与视角组决定元素是否显示；数据查询只读取元素所属的控制器，不从座椅列表选择另一台控制器。

## 二、每台控制器的显示快照

客户端每 tick 为需要显示的控制器构造一份不可变快照。快照不包含座椅激活控制器序号；主/副角色来自座椅控制组布线，并非控制器固有属性。不同样式由控制器的 `hud_layout` 决定。

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

public record BeltSnapshot(String label, int remaining, boolean selected) {}
public record TubeSnapshot(String label, String state, float fill, String reloadText) {}
```

现有 `WeaponHudOverlay` 的取数规则在**单台控制器内部**继续适用：

| 量 | 口径 |
| --- | --- |
| 发射管顺序 | 按发射器子系统名排序；被摧毁或未激活者不占槽位 |
| 弹种槽与 `activeAmmoKey` | 第一根膛内有弹的管提供弹种；都没有时退回第一根有供给者的管的指针态。分划只使用确实有注册键的弹种，没有时返回空 |
| 弹药计数 | 供弹弹链在弹药池中全部装弹机的余量/容量之和，池内按实例去重；池里没有该弹链时退回已绑定供给者之和 |
| 口径不符 | 膛内弹种的注册键不在供弹弹链里 |
| 弹链项 | 每条可选弹链一项；名称取池中首个条目的弹链名，余量为该弹链全部条目之和，供弹那条标记为选中 |
| 装填段 | 每根有效发射器一段：有膛弹常亮；空膛显示供给者状态与进度；无供给者熄灭 |
| 尾标 | 全空显示空弹；任一装填中显示最慢一段的剩余时间或百分比；否则显示就绪 |

发射器数和可选弹链数来自该控制器关联的载具结构。弹链耗尽时保留条目并切换状态；控制器被摧毁或未激活时隐藏它的 HUD 元素，不影响其它控制器。

## 三、`weapon.*` 词表

`WeaponHudMolangContext` 继承通用 `HudMolangContext`，因此武器元素同时可读 `hud.*` 与 `weapon.*`。普通座椅、相机元素没有 `weapon.*`；把武器表达式放到这两类样式里应得到可定位的绑定诊断。无参值按属性书写，带下标或距离的值按函数书写。

| 查询 | 返回 | 说明 |
| --- | --- | --- |
| `weapon.name` | string | 所属控制器的子系统名 |
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
| `weapon.belt_label(i)` | string | 第 i 条弹链的名称 |
| `weapon.belt_remaining(i)` | number | 第 i 条弹链的余量 |
| `weapon.belt_selected(i)` | 0/1 | 第 i 条是否为当前供弹弹链 |

`i` 从 0 开始，按快照中的稳定顺序取值；越界时数值返回 0、字符串返回空。武器控制器业务查询统一以 `weapon.*` 发布。`math.*` 是 Spark 通用词表；`local.*` / `global.*` 持续提供 Part 与装配体的通用数据，HUD 视角与视口查询使用 `hud.*`。

MoLang 不提供可靠的字符串拼接，故 `ammo_text`、`reload_text` 等组合文本由查询直接提供，或由模型文本槽的本地化键和 `molang_args` 填入；数值仍可在表达式中运算。

### 3.1 炮镜分划

武器控制器布局的 `scope` 组可以挂分划模型。`weapon.bdc_hold_rad(distance_m)` 是按所属控制器当前弹药求出的候选查询，返回给定距离的 hold-over 角（弧度）。分划样式自行标定角度与骨骼位移的关系；通用 `hud.*` 不承担模型坐标换算。

相机无需识别“服务哪台武器”。弹道缓存的输入与无弹时的降级见通用上下文文档 §三.1。多台控制器各读自己的弹药；各自的 `scope` 布局决定是否绘制分划。

## 四、样式与 AUI 页面

首批武器样式可提供 `standard`（完整状态）、`minimal`（名称/余量/状态）与 `full`（完整状态及火控预留槽）。它们只定义内部模型或页面，位置与炮镜行为由不同 `HudMount` 决定。

以下是**单管** AUI 页面示例。`data-mm-text` 与 `data-mm-num` 属于计划中的 Machine-Max 绑定层，并非 AUI 已有的原生属性：

```html
<div class="mm-weapon">
  <span data-mm-text="weapon.name"></span>
  <span data-mm-text="weapon.ammo_text"></span>
  <i data-mm-num="weapon.tube_fill(0)" data-mm-var="--fill" data-mm-unit="%"></i>
</div>
```

多管页面若要自动显示多段装填条，内容包作者定义“一根管的一段界面”，绑定层按该控制器的有效管数生成各段，并为每段提供正确的管索引。这是待设计、待实现的页面重复机制，**不是复制武器、控制器或 HUD 元素**。`data-mm-repeat` 仅是该机制的暂定标记；索引怎样进入每段表达式须在 AUI 绑定层实现前确定，不能把所有段固定写成 `weapon.tube_fill(0)`。模型载体的骨骼槽位固定，可为确定管数的样式显式写各下标。

## 五、实施与验收

框架计划的 S5 阶段接入武器控制器宿主。可先从现有 `WeaponHudOverlay` 抽出单控制器快照供面板使用，再让控制器 HUD 元素消费同一口径。内容包与玩家的 `hud_layout` 采用框架文档 §六、§七的解析规则。

验收至少覆盖：同一载具的主、副两台控制器使用不同样式且数据不串；多管武器各装填段读取正确管索引；炮镜边框由相机元素显示而分划与弹药由控制器元素显示；控制器停用时只隐藏本控制器元素；玩家覆盖并重置布局；面板切换前后的弹种、计数和装填口径一致。设计尚未落地，游戏内结果需实现后验证。

## 六、待决事项

1. **AUI 动态列表**：绑定层如何传入当前管/弹链下标，以及结构变化时如何更新页面；该问题不影响带显式索引的 MoLang 查询。
2. **缺失弹道数据**：`weapon.bdc_hold_rad` 目前以 0 降级；样式如需区分“零修正”与“无数据”，可增加单独的可用性查询。
3. **武器样式来源**：内置三套样式使用 `machine_max` 命名空间；内容包按框架资源规则提供自己的样式。

## 七、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-16 | 提出武器状态 HUD 的快照、样式与布局 |
| 1.1 | 2026-10-16 | 由通用框架承载样式、挂载、布局与玩家覆盖；本文聚焦武器数据 |
| 1.2 | 2026-10-16 | 定义每控制器自有元素和快照、`weapon.*` 查询、控制器 `scope` 分划与 AUI 重复段边界 |
