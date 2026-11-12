# 武器系统 — AmmoBelt 与发射器装填第一阶段重构计划

**状态**：P1—P5 已实施，主源码编译通过；运行行为未验。§10 的后续重构仅列问题及待细化事项。

**核对日期**：2026-11-12。

**设计补充日期**：2026-11-13。§4—§7 按本节链接的后续目标统一退回、同步和持久化契约：[客户端模拟同步与状态持久化计划](武器系统-客户端模拟同步与状态持久化计划.md) §2—§6。该补充尚未实施；§9.3 仅记录第一阶段已落地内容，不表示客户端 postTick 校准、引用简化和退回失败丢弃已经进入代码。

**目标**：供给者以 `AmmoBelt` 为单位交付和接收弹药；Launcher 分别持有膛内弹与已装弹药，并通过一个布尔决定单发入膛或整份 Belt 装填。regen 沿用 `regen_per_minute` 生产数字库存，以 `belt_definition` 声明交付规格；Belt 保存 `label` 及可选自身回充能力，由 Launcher 推进。ammoLoader 完成接口的编译适配。物品实现与容器型号资源接入另行细化，本阶段验收以主源码编译通过为准。

## 1. 阅读入口与代码基线

本节为第一阶段实施前的历史快照，固定到提交 `f9efef73`；§8 的迁移表以此为起点。表内 Java 路径以 `src/main/java/io/github/sweetzonzi/machine_max/` 为前缀，Kotlin 路径写全，历史坐标采用“路径:行 @ 提交”。第一阶段落地记录见 §9.3；2026-11-13 确认的后续目标以首部链接的同步与持久化计划为准。

| 编号 | 历史源码坐标 | 快照行为 |
| --- | --- | --- |
| C1 | `common/mech/subsystem/IAmmoSupplier.java:187 @ f9efef73`（requestRound）、consumeReadyRound、returnRound | 交付与退回的单位是单个 `ProjectileType`；接口同时表达储备、输弹计时与交付 |
| C2 | `common/mech/subsystem/IAmmoConsumer.java:54 @ f9efef73`（receiveAmmo）、getCurrentSupplier | 接口上有一个推送式接收方法，生产代码没有调用方；实际交付走拉取链，消费者持有所选供给者引用 |
| C3 | `common/mech/subsystem/LauncherSubsystem.java:166 @ f9efef73`（chamberedType）、tryLoadChamber、tryRequestNextRound、assignSupplier | 显式库存只有膛内一发；空膛向供给者取弹，射击后预请求下一发；换供给者保留膛内弹 |
| C4 | `common/mech/subsystem/LauncherSubsystem.java:1383 @ f9efef73`（loadData）、saveData、ejectRound | 保存 `chambered_type`；退膛尝试归还当前供给者，拒收后的物品落地路径为 TODO |
| C5 | `common/mech/subsystem/RegenLoaderSubsystem.java:160 @ f9efef73`（dockedConsumers）、startDelivery、takeRoundFromMagazine、returnRound | 整匣语义由供给者的对接标记表达；逐发取走才扣数字库存；退回只增加数量 |
| C6 | `common/mech/subsystem/attr/static_attr/module/FeedModuleAttr.java:27 @ f9efef73`（reloadRoundByRound）、reloadTime | 供给者侧决定每发计时或首次对接计时，`reload_time` 单位为秒 |
| C7 | `common/mech/subsystem/attr/dynamic_attr/RegenLoaderSubsystemAttr.java:30 @ f9efef73`（projectileTypesMap）、projectileTypes、beltLabel | `projectile_types` 弹种数量映射被展平为循环列表；实例另存 `belt_label` |
| C8 | `common/mech/subsystem/AmmoLoaderSubsystem.java:57 @ f9efef73`（container）、consumeReadyRound、getProjectileTypeFromItem、getAmmoItem | 物品容器与映射为实验实现，消费一件物品后返回一个类型；物品回收存在占位路径 |
| C9 | `common/mech/subsystem/WeaponControllerSubsystem.java:423 @ f9efef73`（beltOf）、findSupplier、routeAmmoToLaunchers | 弹药池按展平弹链分组，`beltOf` 直接识别再生装弹机；路由可切换供给者 |
| C10 | `common/mech/subsystem/attr/static_attr/LauncherSubsystemStaticAttr.java:27 @ f9efef73`（CODEC） | 发射器静态定义包含控制输入、供弹发现输入及炮管／炮闩／电容模块 |
| C11 | `common/registry/MMDataComponents.java:60 @ f9efef73`（PROJECTILE_TYPE）；`src/main/kotlin/io/github/sweetzonzi/machine_max/common/resource/modules/FittingModule.kt:24 @ f9efef73`（FittingModule） | 已有物品弹种组件，以及“内容包型号 ID → 双端定义表 → 物品呈现”的接入范例 |
| C12 | `common/mech/subsystem/SubsystemController.java:136 @ f9efef73`（onVehicleStructureChanged）、pushAllTo；`network/MMPayloadRegistry.java:34 @ f9efef73`（register） | 结构发现分清理、重建两个相位；首次同步有全量补推；核对时世界同步协议为 `sync:2.9.3` |
| C13 | `client/render/gui/hud/molang/HudWeaponSnapshot.java:39 @ f9efef73`（capture）；`common/mech/subsystem/LauncherSubsystem.java:513 @ f9efef73`（detonateChamberedRound）；`common/mech/explosion/CookoffComposition.java:158 @ f9efef73`（addStoredRound） | HUD 读取膛内弹和供给者；发射器殉爆只汇总膛内一发，供给者处理自身库存 |
| C14 | `common/mech/subsystem/attr/static_attr/module/MagazineModuleAttr.java:25 @ f9efef73`（regenPerMinute）、regenRoundByRound、regenDelay；`common/mech/subsystem/RegenLoaderSubsystem.java:857 @ f9efef73`（tickRegenRoundByRound）、batchTotalTicks | 已有 `regen_per_minute`（发／分钟）、逐发／整批产出及取货后延迟；整批耗时按源容量除以速率计算 |

相关文档的分工：

- [装填语义与供给绑定重构计划](../已实现/武器系统-装弹机装填语义与供给绑定重构计划.md) §3、§5—§7、§14：C5、C6 的对接与绑定行为及其实施记录。该文提供改造起点的行为依据，AmmoBelt 的落地记录见本文 §9.3。
- [装弹机模块化损伤实施计划](../进行中/武器系统-装弹机模块化损伤实施计划.md) §3—§7：机构／弹仓损伤、再生门控、退弹和存档恢复。第一阶段沿用模块健康判据，库存归属按本文 §6.4 核对。
- [武器 HUD 信息分级与视角形态需求分析](../进行中/武器系统-武器HUD信息分级与视角形态需求分析.md) §三：计数与状态需求。本文 §7.2 规定 AmmoBelt 接入所需的最低适配。
- [载具系统殉爆机制设计](../进行中/载具系统-殉爆机制设计.md) §三、§四：实际库存贡献、事件来源和起爆结算。
- [弹药命名参考标准](../../武器系统-弹药命名规范.md) §3.3、§3.5：提供 `AP`、`AP-T`、`HE` 等效果缩写。该文 §3.5 推荐用 `/` 枚举混装弹种；本计划的屏幕 `label` 优先采用主类型短名，详细组成由配弹内容表达，不要求显示名列出全部成员。这是显示建议，不是加载期强制校验。

## 2. 本阶段范围与核心约束

### 2.1 必须交付

1. 一个具体的、可编码和解码的 `AmmoBelt`，表达有限且有序的弹药内容，供武器、物品和两个供给者实现共同使用。
2. Launcher 的 `chamberedType` 与 `loadedBelt` 分别表达膛内一发和其余已装弹药；单发装填用 `single_round_loading` 布尔配置。
3. 单发、整份 Belt 的拉取、消耗与退回；保留膛内弹的整份换装与中断规则。
4. regen_loader 的 `belt_definition` 输出定义、已有速率字段及数字库存回收；Belt 自身回充的参数、运行状态与 Launcher 更新；ammo_loader 对统一交付／回收签名的编译适配。
5. AmmoBelt 的可选容器型号 ID 与实例内容格式；该格式可以供未来物品组件复用，不要求本阶段实现物品、容器资源注册或模型呈现。
6. 武器持久化、权威同步、路由、HUD 与库存损伤代码对新字段和接口的必要适配，保持主源码调用链可编译。
7. 受影响的第一方配置与 schema 字段迁移；最终执行主源码编译，不以 JUnit、GameTest、客户端或多人实机验收作为本阶段完成条件。

### 2.2 约束

- 二值配置使用布尔，本阶段不引入装填模式枚举。
- `container_id` 不表达单发／整份装填；虚拟供弹可以没有物品容器。
- regen 的库存只有数量；接受退回后可以丢弃原弹序和容器信息，不要求按弹种分别恢复库存。
- 源生产速率沿用 `regen_per_minute`，不引入等价的 `production_time` 配置；整份生成所需时间是数量和速率的推导值。
- `projectile_types` 迁移为 `belt_definition`；弹链显示名使用 Belt 内的 `label`。源数字库存的增长与已装 Belt 自身回充各自作用于自己的状态。
- 交付后的 Belt 属于 Launcher；具有回充能力的 Belt 允许正常换装，不建立源持续更新已交付对象的连接关系。
- 玩家实际配弹属于实例数据；容器定义不要求预设配弹，已装弹药不依赖可变的玩家配弹预设。
- 一个 ItemStack 的一件物品对应一个装填单位。物品堆叠数量与每份 Belt 的弹药数量分别计算。
- 不将 3000 发弹链展平为每发一个存档条目；运行期也不以展开全部弹药作为取弹前提。
- 复用现有供给／消费接口并调整操作单位；不为两种库存各套一层同义接口，不建立通用事务或工作流框架。

### 2.3 边界

弹药／弹匣物品读写、容器型号的内容包加载与模型呈现、装弹机构与弹药架的独立子系统、乘员能力、半自动人工／机械分段、待发架与后备架物流、玩家配弹编辑及运行验收属于 §10。第一阶段提供数据和交付基础；不创建没有实际消费方的物品注册、容器管理器或测试夹具。

## 3. AmmoBelt 与容器定义

### 3.1 实例语义

`AmmoBelt` 是一份有限弹药数据，可以来自单发弹药物品、实体弹匣／弹链或再生供给。它不是子系统，也不要求有独立 UUID。当前代码的单膛发射器继续用 `chamberedType` 保存膛内弹；多膛武器不在本阶段范围。

实例持有：

| 数据 | 含义 |
| --- | --- |
| 可选 `container_id` | 内容包容器型号，供物品还原、容量／安装兼容与呈现使用；缺省允许虚拟供弹 |
| `label` | 玩家或内容包填写的弹链类型显示名，空串表示未填写；随实例和物品保存，不作为唯一身份或路由键 |
| `pattern` | 有序配弹模式，见 §3.2；不随消耗变化 |
| `length` | 这份 Belt 的容量（发），定值，不随消耗变化 |
| `remaining` | 剩余发数，初始等于 `length`，耗尽为 0；唯一随消耗变化的计数 |
| 可选 `recharge` | 自身回充定义及运行状态，语义见 §3.4；与源的生产状态分别保存 |

`AmmoBelt` 的模式列表不可变、可以共享；每个实例另带可变的 `remaining` 游标与回充运行态，由持有者原地推进。取首发时读取弹种并推进剩余值。物品数据组件、源库存与武器之间不得共享同一份可写状态。

必要操作仅覆盖实际消费者：首发查询、消费首发、剩余数量、按弹种计数、回充推进、成功射击通知及完整编解码。Launcher 驱动回充并原地推进 Belt，Belt 数据不持有世界或供给者引用。任意编辑、拆分、拼接工具按 §10 的玩家配弹需求细化，不预建接口层级。

### 3.2 紧凑有序表示

使用一条有序配弹模式，加定值容量与剩余发数。模式直接位于 Belt 上，不包装多段周期结构。相同格式支持同种弹药、周期混装和任意有限混装。示意格式如下；字段名在 P1 编解码实现时固定，含义以本节为准：

```json
{
  "container_id": "example:large_belt_box",
  "label": "AP",
  "pattern": [
    { "ammo": "example:ap", "count": 4 },
    { "ammo": "example:ap_tracer", "count": 1 }
  ],
  "length": 3000,
  "remaining": 2998
}
```

`count` 为模式内连续同种弹药数量，`length` 为这份 Belt 的容量，`remaining` 为剩余发数。消费一发只把 `remaining` 减一；下一发的弹种由 `(length - remaining) % patternTotal` 在模式展开序列上取，`patternTotal` 为各 `count` 之和。`remaining` 为零即耗尽，不能因模式可循环而继续发射。新建输出模板的 `remaining` 等于 `length`。

弹序使用有序列表，不使用“弹种 → 数量”映射作为顺序契约。同一弹种可以出现多次；例如 `AP × 20、HE × 10、AP × 7` 直接作为一条模式，长度取 37，即表达一次完整的有限弹序。重复的混装只需保存短模式与较大的有限长度，不必展开 3000 发。

完全无规律的弹序允许具有与内容量相应的数据长度。按弹种计数应利用完整周期与有限尾部计算，不为了汇总殉爆或 HUD 而生成逐发对象。不同周期的无展开拼接结构没有本阶段消费方；实际需要拼接工具时再由 F6 细化。

Codec 和网络解码边界检查：正的模式计数、`0 ≤ remaining ≤ length`、`length` 非负、`patternTotal` 求和与 `length - remaining` 的运算溢出、模式条目数／消息长度边界，以及能由现有注册表解析的弹种。空模式只允许表达 `remaining` 为零且无回充能力的实例；可回充实例即使余量为零也保存有效模式。容器 ID 本阶段仅做标识格式解析和保存，型号存在性、容量与安装条件校验随 §10 的 F5 接入。失败必须保留可诊断的字段位置，不能按默认弹种或占位物品吞掉内容。

### 3.3 容器型号与未来物品格式

容器定义表达型号，不保存玩家实例配弹。未来定义包含最大容量、可容纳弹药条件、武器安装规格，以及模型／图标配置。型号加载与双端表可参照 C11 的 `FittingModule`，但本阶段不交付这条加载通路；目录名、注册名和字段落点由 §10 的 F5 固定。

`container_id` 将用于容量、容纳条件、安装条件与呈现的查询。本阶段 Launcher 按 §4.1 的弹种／数量条件接收，容器 ID 不参与单发模式判定。容器 ID、是否实体物品、是否单发装填三者不互相推导。

物品实现时保存完整 AmmoBelt 组件，并复用其 Codec 与网络格式。有型号时读取型号呈现，无型号时的单发／通用物品形式由 F5 确定。组件应成为物品实际弹药内容的唯一载体，不能同时缓存另一份可独立变化的剩余数。本文的数据格式约定不代表已有可用弹药物品。

`loadedBelt == null` 表示武器没有已装供弹数据；带容器 ID 的空 Belt 可以表达待未来物品实现处理的空容器。无容器且无回充能力的空 Belt 可规范为无已装数据；具有回充能力的空 Belt 必须保留定义与进度，等待恢复。

### 3.4 Belt 自身回充

回充是 AmmoBelt 的可选能力，普通弹链可以不具备。具备时 Belt 带一个 `recharge` 块，字段沿用 `MagazineModuleAttr` 的词汇，属于已装实例，不指向源的同名状态：

| 字段 | 含义 | 缺省 |
| --- | --- | --- |
| `regen_per_minute` | 回充速率（发／分钟） | 必填 |
| `regen_delay` | 成功射击后暂停回充的秒数 | `0` |
| `energy_cost_per_round` | 每发能源费用，由持有者提供实际能源条件 | `0` |

回充上限就是 `length`：补回的是打掉的发数，补到出厂容量为止，因此 `recharge` 块里没有容量字段。也不为相同含义配置满充时间。

回充只有一种推进方式：按 `regen_per_minute` 连续补入 `remaining`，每次成功射击后把 `regen_delay` 重新计时。延迟在每次射击时重置，因此持续开火期间不产生回充，停火 `regen_delay` 秒后才开始补。

不设逐发／整批的粒度开关，两种观感由同一套参数的两端表达：短 `regen_delay` 配低 `regen_per_minute` 是随打随补；长 `regen_delay` 配高 `regen_per_minute` 是打空停火后一次性补满。再加一个布尔会与这两个参数构成第二权威。

回充沿用 Belt 自己保存的 `pattern`，实际余量耗尽后也保留模式；恢复额度时保持剩余序列沿该模式连续，不读取源当前的 `belt_definition`。不可变模式可以共享，进度与余量属于当前 Belt。字段名与运行态编码形状在 P1 固定，物品实现将复用该格式。

回充增加 `remaining`，`length` 保持不变，因此消耗量 `length - remaining` 随之减少，弹序按原模式回退：补回的是最近打掉的那几发。`remaining` 不得超过 `length`，回充到满即停止。

回充的运行态与配置分开保存：`recharge` 块只放上表的配置，运行态另存延迟剩余 tick 与零碎进度，随实例持久化。Launcher 在自身可工作时推进已装 Belt 的回充。本阶段只有 Launcher 更新；卸下并完整保存的 Belt 暂停回充，不计算离线恢复。源不追踪已交付对象，也不持续向它补入弹药。

仅成功消费本 Belt 额度的射击刷新回充延迟；扣扳机但未成功射击、预览弹种或准备射击均不刷新。允许按住开火至余量耗尽后，等待延迟、回充出额度、成功射击，再次进入延迟。

可回充 Belt 使用 Launcher 的整份持有路径，即 `single_round_loading=false`，即使它的容量只有 1。单次射击与单发装填是不同概念。常规火炮的单发入膛保持独立实弹语义；来源继续生产下一发，不把临时交付包装的回充状态藏进 `chamberedType`。

## 4. Launcher 的接收、供弹与换装

### 4.1 布尔与拉取校验

在发射器定义中加入 `single_round_loading`，第一方定义必须显式填写，避免按供给者状态推断。该布尔只描述一次拉取多少发。交付是拉取链：`requestRound` → `isRoundReady` → `consumeReadyRound`，供给者不向消费者推送，也不判断消费者收不收；校验在 Launcher 拉到交付之后执行。

| 值 | 拉到交付后的校验 | 完成后的状态 | 付装填时间的操作 |
| --- | --- | --- | --- |
| `true` | 这一份恰好 1 发、弹种通过 `canAccept`（Launcher 只在膛空时拉取） | 写入 `chamberedType`，不建立持久的 `loadedBelt` | 每次单发入膛 |
| `false` | 这一份的 `length >= 1`，模式内每个弹种都通过 `canAccept`；不设整份长度上限 | 写入 `loadedBelt`，膛内弹独立 | 每次整份装入或替换 |

填写依据是武器实际怎么装填，与口径、射速和供给者类型无关：一次只把一发推进膛室的武器填 `true`，包括坦克与自行火炮主炮、导弹发射管（每管一发）以及逐发取弹的机关炮；由弹链或弹匣整份供弹的机枪、机关炮填 `false`。已经装着 `loadedBelt` 时，常规拉取不会发生；整份替换由 §4.3 的规格差异驱动。校验不通过时，Launcher 把整份退回当前供给者，由供给者按 §5 的回收规则处理，并等待下一次重路由；这与单发路径的防御性归还同一形状。

该布尔只描述武器一次拉几发，与供给者无关。交付单位统一为一份 Belt，供给者不按逐发／对接粒度区分：单发装填的武器收到长度为 1 的 Belt，整匣装填的武器收到整份 Belt，两者都由 `single_round_loading` 决定，不由供给者类型或配置推导。

`false` 也能拉取只含一发的 Belt；数量不决定模式。`true` 的判断不要求缺省 `container_id`，也不检查供给者是否具有物品形式。拉到的整份不通过校验时整份退回，Launcher 不自动把大 Belt 拆为单发。

### 4.2 武器内部消费

普通有限 Belt 在需要入膛时取一发写入 `chamberedType`；这只是库存位置变化。射击消费膛内弹，按炮闩射速推进后续循环。武器从本地 Belt 取弹不访问供给者、不扣其库存、不重复支付换装时间。

可回充 Belt 在成功射击提交时消费本地额度并刷新自身延迟，可以预览弹种但不提前形成一发独立膛内库存，避免预取后回充导致额外一发。若武器确有一发先前独立入膛的实弹，先按实弹处理；该发不再扣新 Belt 的额度，也不刷新未供给这发的 Belt 延迟。

单发模式直接执行一次交付与入膛。两种模式都保留发射器现有的高射速 tick 内循环及主线程向物理线程发射队列，不把整个 Belt 交给物理线程并发消费。

### 4.3 第一阶段的最小换装

整份替换由规格差异驱动：Launcher 已装内容的规格与当前供给者要交付的规格不一致时，就把这次差异当作在途换装——向该供给者 `requestRound`，它按自己的 `reload_time` 备货，备好后整份替换。装填流程只属于供给者，Launcher 侧不另排计时；专用按键、界面与完整多阶段人工动作归 §10。

换装期间（从差异成立到提交完成）保留膛内弹和旧 Belt，暂停本武器射击，目标来源只持有交付预留。旧 Belt 的回充由当前持有者按 §3.4 推进，交接读取提交时的实际余量。有效新交付在主线程验证后提交；旧 Belt 可交给当前合法且接受它的提供者，无人接收或整份拒收则直接丢弃，换装照常完成。新交付失败或不合法时不提交换装，不因取新弹失败丢弃旧 Belt。本阶段不模拟“旧匣已在手中”的中间实体状态；终止未提交操作只释放预留。

充能 Belt 允许同样的换装流程。玩家可以等待当前 Belt 自行恢复，也可以使用库存中的另一份满额 Belt；是否具有回充能力不限制换装。新 Belt 必须来自实际可交付额度，换装本身不将当前 Belt 重置为满额。自动比较“等待回充还是换装”的策略留待后续细化。

示例：膛内 A × 1、已装 A × 17，换入 B × 30 后，膛内为 A × 1、已装为 B × 30。旧 A × 17 若由 regen_loader 接受则加 17；未来 ammo_loader 接受时保存剩余弹序。无人接受则丢弃这 17 发，不影响新 Belt 装入。首次后续射击使用膛内 A。示例数值仅说明数量和顺序契约，不作为本阶段测试或内容标定基线。

### 4.4 选择、已装状态与退回目标

控制器的所选弹药、下一次来源、实际已装 Belt 和膛内弹分别有明确读取点。选择变化经规格差异触发的整份替换影响已装库存，不能立即覆盖 `loadedBelt`。普通来源重排或更快候选不抢走已经装好的 Belt。

整份卸弹与退膛是不同操作。退膛将一发包装成单发交付；卸弹处理整个剩余 Belt。两者可向当前合法提供者尝试回收，由接收策略判断是否接受，不要求退回历史来源。无人接收或拒收时允许直接丢弃，不要求生成掉落物。武器无需保存历史来源追踪，AmmoBelt 也不携带来源引用。

## 5. 供给者的交付与回收

### 5.1 共同契约

供给／消费接口的交付单位统一为 AmmoBelt。候选查询应能预览下一份的内容规格、数量与可用性，供路由和 Launcher 在取货前检查兼容性。查询不扣库存、不启动计时；重复请求不建立重复预留。

预留由实际库存所有者维护，覆盖单发、物品槽位和数字数量，多个消费者合计不能超过可交付库存。第一阶段复用现有交付／认领存储及主线程编排，不建立独立事务服务。`can_supply_multiple` 的已有并发限制继续约束未完成交付；已交付的 Belt 不需要源持续占用服务通道。

在同一主线程提交点完成源库存扣减与目标写入，新交付的兼容性和可用性必须通过校验。换装的旧 Belt 另按回收策略接受或丢弃；回收空间不足可以拒收，但不阻止已经合法的新交付提交。不能把接收方拒收解释为已成功回收。

退回接口采用全收／全拒；接收方拒收时不改库存或传入对象。调用方在正常退膛／卸弹／换装中可以丢弃被拒收的内容，并清理自身对应字段。部分回收及自动寻找多个接收方不在第一阶段范围。

### 5.2 regen_loader

实例配置使用 `belt_definition` 提供输出模板，其值就是一个 AmmoBelt 对象，字段与 §3.2 相同：`label`、`pattern`、`length`、可选 `container_id` 与可选 `recharge`。交付量取 `min(源库存, length)`：够一份交满额，不够交剩余的全部，实例余量按交付发数写。模板自身的 `remaining` 不参与计算，因此它在 `belt_definition` 中可省略，写了也不生效。

`length` 由内容包作者填写：单发装填的武器填 1，整匣装填的武器填该武器供弹具的发数。每次交付生成独立实例及初始回充状态，不能共享可写进度。标签随输出实例保存，不另在源上维护 `belt_label`。

源的数字库存生产沿用 `magazine.regen_per_minute`（发／分钟）、`regen_round_by_round`、`energy_cost_per_round` 与 `regen_delay`，定义含义参照 C14。对逐发增长，从零积攒 q 发的标称时间为 `60 × q / regen_per_minute` 秒；已有 n 发时按缺额积攒。该推导不含源延迟、失能或能源等待；整批产出继续按源容量与速率计算批次，不能把逐发推导值当作完整批次等待时间。不配置另一项等价的 `production_time`。

源的 `regen_round_by_round` 只作用于源自己的储备补充，决定它按逐发还是整批补货。Belt 的 `recharge` 没有对应字段，回充粒度由 `regen_delay` 与 `regen_per_minute` 表达，见 §3.4。

交付量取 `min(可用库存, length)` 并一次扣掉：够一份交满额，不够交剩余的全部。缩水只体现在实例余量上，模板长度不动——长度是规格的一部分，变了会被发射器判成换装。库存归零因此成为常态，整批回充的触发条件总能成立。每次发放生成独立 AmmoBelt，不共享可写消费位置。

`length` 与 `magazine.capacity` 是两个独立的量：前者是一次交付的发数上限，后者是源能存多少发。二者不要求相等；`length` 小于容量时一次交付扣走 `length`，剩余额度留到后续交付。配置须满足 `length` 大于零且不超过容量，并满足潜在目标的弹种兼容（§4.1）；`length` 大于容量属于配置错误，解码期拒绝。

回收按允许弹种、储存健康状态及剩余容量判断；全部接受时增加剩余发数，可以丢弃弹序、当前位置与容器信息。容量不足时整份拒收，不能通过钳位吞掉溢出数量。允许弹种集合与输出模式分别有明确含义，无需逐位置匹配模板。

源库存不存实际弹序，不按弹种分账。源的 `regen_delay` 在成功交付后作用于备用额度生产；Belt 内的 `regen_delay` 在成功射击后作用于已装实例回充。两者作用对象与触发事件各自明确，不因源保留同名字段而把逐次开火通知发送回源。

源生产备用额度与 Launcher 推进 Belt 自身回充可以并行，分别增加各自库存。源只负责模板、备用计数和交付／回收，不更新已经交付的实例。退回充能 Belt 时，计数回收允许丢弃该实例的剩余回充进度；未来物品接收方则保存完整实例，不把“额度回收”和“取回同一块电池”混为一项契约。

### 5.3 ammo_loader 的编译适配与后续策略

本阶段仅要求统一接口的签名和调用方可编译，不要求物品读写、注册或完整回收闭环。尚无实际映射的路径明确返回不可交付／拒收，不能用占位物品或虚假成功冒充物品实现。

后续物品实现的既定策略是：每件物品对应一份实际 AmmoBelt；交付时读取一件物品，提交后堆叠数量减一；接收时将剩余内容保存为一件物品。有容器 ID 时据定义还原型号，内容与当前位置保持。源的物品槽位数与弹药发数独立。

F5 可参考 C11 的 `PROJECTILE_TYPE` 识别通路设计单发物品转换；不能假定投射物 ID 等同物品 ID。实际 ItemStack 组件往返、空容器回收与不可解析物品的处置都由该后续计划交付，不进入本阶段验收。

## 6. 库存、生命周期与持久化

### 6.1 唯一归属

```text
源储备 → Launcher.loadedBelt → Launcher.chamberedType → 射击消耗
                  └────────→ 合法接收方或明确丢弃
```

单发模式由源直接提交到膛室。源扣减发生在交付提交，Belt 入膛不再次扣源。预留表示对源库存的承诺，不计入第二份库存。每份已交付内容只有一个可消费所有者。

已装 Belt 与供给者储备容量独立。源生产与 Belt 自身回充分别增加各自余量；数量核对须计入这两条实际产出，以及退回拒收后的明确丢弃损耗。可回充 Belt 的额度在成功射击时直接消费，不经过一份额外的独立膛内库存。容量、输出长度与两处回充配置必须按 §8 的迁移清单逐型号核对，不能将这项玩法变化藏在计数实现中。

### 6.2 存档

Launcher 保存独立膛内弹、完整紧凑 Belt（含 label、实际弹序、余量、可选回充定义与运行进度）及需要延续的剩余射击冷却。当前供给者、装填路径、历史退回来源和网络地址不持久化；首轮未完成装填不存档，读档后重新发起。关系由服务端恢复实际状态后重新发现、选择并同步到客户端，不要求原路退回。

源保存实际物品或数字库存及自身再生进度／延迟；武器控制器保存玩家选中的弹药规格。发现结果、人员认领、机构占用和交付预留不存档，服务端运行时重建，不在源与目标两边各保存一份已扣库存。结构、接线与连接拓扑由载具／部件层负责恢复，不在子系统存档中重复保存。读档不免费产出新 Belt，不重放库存转移。

武器使用的 AmmoBelt 编解码也作为未来物品数据格式。已装内容保存实际紧凑弹序、标签及恢复内容所需模式，不只保存可被玩家编辑的配弹预设 ID；读档不从源当前模板重置余量或回充进度。定义引用无法解析时保留可恢复信息；容器容量变化的数据迁移由 F5 细化，不能以本阶段不存在的容器查询实现校验。

### 6.3 数量与回收验收口径

未来 ammo_loader 成功接受后的物品往返要求数量、弹种和剩余顺序一致；regen 成功回收要求接受数量正确，允许具体弹种和弹序在下一次发放时按模板重新生成。接收方拒收不改库存，调用方丢弃单独计为损耗。这些是实现行为约定，本阶段不要求为其建立测试夹具或运行验收。

### 6.4 损伤与殉爆

源的 `feed` 损坏阻断未来交付，不能抹掉或阻断另一台健康 Launcher 已取得的 Belt。源弹仓损坏时只处理源当前库存及预留；已交付弹药不属于它的库存贡献。

第一阶段将已装 Belt 的逻辑库存归 Launcher，炮闩判毁时按膛内弹与 Belt 的实际贡献汇总一次殉爆，采用该模块的受击来源及定位；全零贡献的处理沿用 C13 的现有契约。不得让源与 Launcher 对同一份弹药分别起爆。固定外置弹箱的独立物理库存定位与精细命中箱归 §10 的 F1；不能同时把同一份内容保存在源和 Launcher 来模拟外置位置。

## 7. 路由、同步与 HUD

### 7.1 路由和线程

服务端负责真实库存、来源选择、实际交付／回收与投射物生成。客户端跟随服务端选定的来源，正常模拟装填、取弹、扣弹、回充与音效特效；本地消费使用独立状态副本，不回写服务端库存，不生成真实投射物或建立真实预留。同步回调缓存新值，客户端主线程 postTick 在本轮模拟结束后只应用新收到的字段；校准不触发表现，不引入状态回滚或预测重推。AmmoBelt 的物品／持久化／网络解码复用同一含义。

运行期供给指认统一使用宿主 SubPart ID ＋ 子系统名，复用普通子系统同步的寻址形状，不发送 Part UUID 或 SubPart 名，不建立额外的子系统整数注册表。完整字段、空值更新、初始基线及服务端重建关系见 [客户端模拟同步与状态持久化计划](武器系统-客户端模拟同步与状态持久化计划.md) §2—§5。

路由候选根据下一份交付检查长度和全部弹种；容器安装条件校验随 F5 接入。弹药池通过统一接口读取交付规格，ammo_loader 尚不能交付的路径不进入候选，不能用 `instanceof RegenLoaderSubsystem` 作为长期接口契约。池分组使用紧凑配弹规格及需要区分的容器 ID／回充规格，不以 3000 个键的展平数组或 `label` 作为身份。不同内容允许具有相同显示名。

已装弹药的可用性来自 Launcher，自身健康且本地有弹时不因未来来源故障而显示不可发射。结构变化沿用 C12 的两阶段发现，只清理发现结果与失效预留，不清空真实已装库存。

### 7.2 最低显示与同步适配

区分膛内弹、Belt 余量及自身回充进度、源储备及生产进度、所选后续弹药和装填进度。一个源被多条路径引用时只汇总一次；膛内弹不包含在 Belt 余量中。换装期间的状态同时表达“膛内有弹”与“当前操作锁定射击”。

已装弹链名称读取 `loadedBelt.label`，后续所选弹链名称读取预览定义的 `label`；允许显示两者不同。`label` 按玩家或内容包提供的显示文本处理，源只转发 Belt 的标签，不维护第二份名称字段。

屏幕标签使用紧凑类型名，例如以 AP 为主且夹有少量 AP-T 的弹链可标 `AP`，全穿甲曳光弹链可标 `AP-T`；纯曳光弹在命名参考标准 §3.3 中对应 `T`。不在示例短标签中加入“混装弹链”等说明性后缀。实际比例和顺序保存在 `pattern` 中，可供未来详情界面显示；标签不随本次取到的弹种变化，也不要求逐项枚举全部成员。玩家可以自定义显示名，引擎不从名字推断弹药效果。

首次进入、读档及结构恢复需同步完整 Belt、膛内弹与服务端当前操作，沿用 C12 的全量补推；完整初值建立模拟基线，不重播历史音效。稳定射击过程使用紧凑内容与消费位置／数量变化，避免每 tick 展开发送全部弹种，也不逐发分别发送音效和特效包。同步按现有通路调整；每 tick 检查脏字段不等于每 tick 全量下发，客户端只能应用实际收到的新值。

字段表或载荷布局变化必须同步提升 `MMPayloadRegistry#register` 中对应协议组，实施时读取当时版本并递增，不能覆盖同期任务的版本更新。本次文档核对值见 C12，不据此预写未来版本号。

## 8. 字段、内容与存档迁移

本节迁移基线为 §1 的 C1—C14；对照只用于从该形态升级，不作为第一阶段目标行为的定义。

| 当前基线 | 第一阶段迁移要求 |
| --- | --- |
| `feed.reload_round_by_round` 决定逐发／对接，普通装弹机另有未实现的 `round_by_round` | 接收模式由 Launcher 的 `single_round_loading` 决定；供给接口与运行期交付都不区分输弹粒度。`feed.reload_round_by_round` 随本阶段从 codec、schema 与内容包中删除，普通装弹机的 `round_by_round` 仍未实现 |
| `feed.reload_time` 是输弹／对接总耗时 | 第一阶段作为来源路径总取用代价，由 Launcher 的单次操作计时读取；F2 才拆人工与机械耗时 |
| `projectile_types` 映射与 `BELT_INDEX` | 实例配置迁入 `belt_definition` 的紧凑有序内容；数字库存不要求保存实际弹序。加载时保留数量，不承诺恢复此前虚拟库存的逐位置弹种。`belt_definition` 的 `length` 与源 `magazine.capacity` 分别填写，不从容量推导 |
| 实例单独保存 `belt_label` | 迁入 `belt_definition.label`，显示与序列化统一使用 Belt 字段；不作为唯一身份或路由键 |
| 源上已有 `regen_per_minute` 等再生字段 | 源备用额度生产沿用现有速率，不引入生成时间字段；需要电池自身恢复的型号另在 `belt_definition` 声明回充。按作用对象迁移，不能将全部源参数机械复制为双份生产。`regen_round_by_round` 描述源自己的补货粒度，`recharge` 块不含该字段 |
| 供给者容量兼任已接通弹匣容量 | 明确源储备容量和每份输出长度，逐型号核对总可用量、自动回充与装填时间 |
| 发射器仅存 `chambered_type`，对接标记不保存 | 膛内弹照实恢复；没有 Belt 存档时已装 Belt 为空，源库存保持实存数量，首次正常交付才转移，不凭空播种满 Belt |
| 单个类型退回、实验物品映射 | 本阶段做统一接口的编译适配；完整 AmmoBelt 物品组件、物品读取和回收由 F5 实现 |
| `IAmmoConsumer#receiveAmmo`、`#canAcceptAmmo`、`#getFreeCapacity` | 生产代码没有调用方（`getFreeCapacity` 只有测试调用）；拉取链不经过这三个方法，随接口调整删除 |

第一方清单同时检查子系统定义、部件实例和所有 Launcher 的接线，不能只根据供给者文件名推断模式。共享同一型号而实际装填方式不同的安装需要分清配置归属；源输出规格与所有目标都须匹配。逐定义的取值与逐实例的交付规格见 §8.1。

源码涉及 C1—C14 中实际使用新数据／接口的调用方。本阶段不创建容器型号加载模块、双端定义表、弹药物品及物品组件注册。涉及 schema 时只手改 builtin 的中英源并同步副本，参照根 `AGENTS.md` 的“内容包 schema 同步”；容器定义 schema 随 F5 交付。

### 8.1 P0 迁移清单

核对日期 2026-11-12，基线为 §1 的 C1—C14。清点结果：19 个 Launcher 定义（23 个实例）、21 个 regen_loader 定义（25 个实例）、0 个 ammo_loader 实例；世界同步协议组为 `sync:2.9.3`（`fb06184b` 引入）。工作区同期只有本计划与 `docs/plan/README.md` 未提交，没有其他任务改动这些文件。

**Launcher 装填模式。** `single_round_loading` 写在静态定义上：第一方没有同一 Launcher 定义按不同装填方式复用的安装，`pktm_launcher` 的三处安装与 `atgm_tube_launcher` 的四处安装各自模式一致。

| 定义 | 实例数 | `single_round_loading` |
| --- | --- | --- |
| `machine_max:2a90_launcher` | 1 | `true` |
| `machine_max:atgm_tube_launcher` | 4 | `true` |
| `machine_max:kwk39_50mm_launcher` | 1 | `true` |
| `m1a2_sep:m1a2_launcher` | 1 | `true` |
| `t90a:t90a_launcher` | 1 | `true` |
| `type59:type59_launcher` | 1 | `true` |
| `machine_max:2a42_launcher` | 1 | `false` |
| `machine_max:pktm_launcher` | 3 | `false` |
| `machine_max:mg34_launcher` | 1 | `false` |
| `machine_max:basic_launcher` | 0 | `false` |
| `m1a2_sep:coax_launcher` | 1 | `false` |
| `m1a2_sep:coax_762_launcher` | 1 | `false` |
| `m1a2_sep:rws_launcher` | 1 | `false` |
| `m1a2_sep:port_mg_launcher` | 1 | `false` |
| `m1a2_sep:aft_mg_launcher` | 1 | `false` |
| `t90a:coax_launcher` | 1 | `false` |
| `t90a:aa_launcher` | 1 | `false` |
| `type59:coax_launcher` | 1 | `false` |
| `type59:aa_launcher` | 1 | `false` |

**regen_loader 交付规格。** `belt_definition` 是实例字段，与 `projectile_types`、`belt_label` 同处部件 JSON 的子系统块，值是一个 AmmoBelt 对象（§5.2）。`length` 由内容包作者按该武器的供弹具填写，与源 `magazine.capacity` 相互独立：容量是源能存多少发，长度是一份 Belt 多少发。源储备容量保持现有 `magazine.capacity` 不变。

下表 `length` 列是建议值，按武器实际供弹具的发数给出，不是从容量推导的。单发装填的武器固定为 1；整匣装填的武器取该枪弹链／弹箱的发数，可以远小于源容量。

| 实例（部件 / 子系统） | 定义 | 目标 Launcher | 目标模式 | `length` | 源容量 | `reload_time` | 源回充 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 2s38_turret / 2a90_ap_loader | `machine_max:2a90_regen_loader` | `2a90_launcher` | `true` | 1 | 74 | 0.45 | 120／分，逐发，延迟 0.3 |
| 2s38_turret / 2a90_he_loader | `machine_max:2a90_regen_loader` | `2a90_launcher` | `true` | 1 | 74 | 0.45 | 120／分，逐发，延迟 0.3 |
| 2s38_turret / pktm_ammo_loader | `machine_max:pktm_regen_loader` | `pktm_launcher` | `false` | 200 | 2000 | 3 | 12000／分，整批，延迟 0 |
| k17_turret / 2a42_ammo_loader | `machine_max:2a42_regen_loader` | `2a42_launcher` | `false` | 150 | 300 | 4 | 100／分，逐发，延迟 0.3 |
| k17_turret / pktm_ammo_loader | `machine_max:pktm_regen_loader` | `pktm_launcher` | `false` | 200 | 2000 | 3 | 12000／分，整批，延迟 0 |
| k17_turret / atgm_ammo_loader | `machine_max:atgm_regen_loader` | `atgm_tube_launcher` ×4 | `true` | 1 | 4 | 10 | 2／分，逐发，延迟 0 |
| m1a2_turret / ammo_loader | `m1a2_sep:m1a2_loader` | `m1a2_launcher` | `true` | 1 | 24 | 5 | 5／分，逐发，延迟 0 |
| m1a2_turret / ammo_loader_he | `m1a2_sep:m1a2_he_loader` | `m1a2_launcher` | `true` | 1 | 16 | 5 | 3／分，逐发，延迟 0 |
| m1a2_turret / coax_loader | `m1a2_sep:coax_loader` | `coax_launcher` | `false` | 100 | 1000 | 0 | 700／分，逐发，延迟 0.5 |
| m1a2_turret / coax_762_loader | `m1a2_sep:coax_762_loader` | `coax_762_launcher` | `false` | 200 | 1000 | 0 | 700／分，逐发，延迟 0.5 |
| m1a2_turret / rws_loader | `m1a2_sep:rws_loader` | `rws_launcher` | `false` | 100 | 100 | 0 | 750／分，整批，延迟 0 |
| m1a2_turret / port_mg_loader | `m1a2_sep:port_mg_loader` | `port_mg_launcher` | `false` | 200 | 200 | 0 | 2000／分，整批，延迟 0 |
| m1a2_turret / aft_mg_loader | `m1a2_sep:aft_mg_loader` | `aft_mg_launcher` | `false` | 100 | 100 | 0 | 750／分，整批，延迟 0 |
| pkt_turret / pkt_ammo_loader | `machine_max:pktm_regen_loader` | `pktm_launcher` | `false` | 200 | 2000 | 3 | 12000／分，整批，延迟 0 |
| sdkfz234_turret / 50mm_ammo_loader | `machine_max:kwk39_50mm_regen_loader` | `kwk39_50mm_launcher` | `true` | 1 | 40 | 2 | 10／分，逐发，延迟 0 |
| sdkfz234_turret / 50mm_he_ammo_loader | `machine_max:kwk39_50mm_regen_loader` | `kwk39_50mm_launcher` | `true` | 1 | 40 | 2 | 10／分，逐发，延迟 0 |
| sdkfz234_turret / mg34_ammo_loader | `machine_max:mg34_regen_loader` | `mg34_launcher` | `false` | 50 | 150 | 3 | 900／分，整批，延迟 0 |
| t90a_turret / ammo_loader | `t90a:t90a_loader` | `t90a_launcher` | `true` | 1 | 14 | 7.5 | 3／分，逐发，延迟 2 |
| t90a_turret / ammo_loader_he | `t90a:t90a_he_loader` | `t90a_launcher` | `true` | 1 | 8 | 7.5 | 2／分，逐发，延迟 2 |
| t90a_turret / coax_loader | `t90a:coax_loader` | `coax_launcher` | `false` | 250 | 2000 | 0 | 500／分，逐发，延迟 0.5 |
| t90a_turret / aa_loader | `t90a:aa_loader` | `aa_launcher` | `false` | 100 | 300 | 0 | 240／分，逐发，延迟 0.5 |
| type59_turret / ammo_loader | `type59:type59_loader` | `type59_launcher` | `true` | 1 | 22 | 10 | 3／分，逐发，延迟 2 |
| type59_turret / ammo_loader_he | `type59:type59_he_loader` | `type59_launcher` | `true` | 1 | 12 | 10 | 2／分，逐发，延迟 2 |
| type59_turret / coax_loader | `type59:coax_loader` | `coax_launcher` | `false` | 250 | 3000 | 0 | 500／分，逐发，延迟 0.5 |
| type59_turret / aa_loader | `type59:aa_loader` | `aa_launcher` | `false` | 100 | 500 | 0 | 240／分，逐发，延迟 0.5 |

整匣交付把连续供弹切成「打空一份、等补满一份」的循环，周期取两者较大值。持续射速因此是 `min(炮闩射速, 补满速率)`，与逐发交付相同；改变的是交付粒度，不是长期火力。补满速率默认取源 `magazine.regen_per_minute`，K17 的 2A42 取已装 Belt 的自身回充。下表按 `length`、`breech.fire_rate` 与补满速率推算，未计入每次整份装入的 `reload_time`：

| 实例 | 打空一份 | 补满一份 | 持续射速 |
| --- | --- | --- | --- |
| 2s38／k17／MTLB 的 PKTM | 16 秒 | 1 秒 | 750／分（炮闩） |
| K17 的 2A42 | 16 秒 | 90 秒（Belt 回充） | 100／分（Belt 回充） |
| M1A2 并列 12.7 | 11 秒 | 9 秒 | 550／分（炮闩） |
| M1A2 并列 7.62 | 16 秒 | 17 秒 | 700／分（源） |
| M1A2 遥控武器站 | 11 秒 | 8 秒 | 550／分（炮闩） |
| M1A2 左前机枪 | 16 秒 | 6 秒 | 750／分（炮闩） |
| M1A2 尾机枪 | 11 秒 | 8 秒 | 550／分（炮闩） |
| Sdkfz234 的 MG34 | 4 秒 | 3 秒 | 800／分（炮闩） |
| T90A 并列机枪 | 19 秒 | 30 秒 | 500／分（源） |
| T90A 高射机枪 | 9 秒 | 25 秒 | 240／分（源） |
| Type59 并列机枪 | 25 秒 | 30 秒 | 500／分（源） |
| Type59 高射机枪 | 10 秒 | 25 秒 | 240／分（源） |

表中「持续射速」标为（源）的五个实例，打空快于补满，循环由源决定：M1A2 的并列 7.62、T90A 的并列机枪与高射机枪、Type59 的并列机枪与高射机枪。K17 的 2A42 标为（Belt 回充）：循环由已装 Belt 的自身回充决定，速率与源回充同为 100／分。其余六个整匣实例的源补得比打空快，循环由炮闩射速决定。

**迁移结论。**

- `belt_definition` 落在实例上，不落在静态定义上。`machine_max:2a90_regen_loader` 同时服务 AP 与 HE 两个实例（标签分别为 `AP`、`HE`），`machine_max:kwk39_50mm_regen_loader` 同时服务 `50mm_pzgr39` 与 `50mm_sprgr38`；把标签与配弹写进静态定义会让这两组共用一份内容。
- 表内 `length` 与源容量解耦：PKTM 的 200 发弹链配 2000 发储备、T90A 并列机枪的 250 发弹链配 2000 发储备、Type59 并列机枪的 250 发弹链配 3000 发储备。一次交付取 `min(库存, length)`：够一份交满额，不够交剩余的全部。
- 第一方只有 K17 的 `2a42_ammo_loader` 声明 `recharge`：`regen_per_minute: 100`、`regen_delay: 0.3`、`energy_cost_per_round: 0`。已装 Belt 打空后由自身回充补满；源 `2a42_regen_loader` 的 100／分保留为备用库存与换装来源。两条产出按 §6.1 分别计入，K17 的持续射速为 100／分。其余实例不声明 `recharge`。
- 三个实例没有 `belt_label`：K17 的 `atgm_ammo_loader`、Sdkfz234 的 `50mm_ammo_loader` 与 `50mm_he_ammo_loader`。迁移后 `label` 为空串，显示回退到首发弹种名。
- 发现频道跨车复用不构成冲突：`ammo_coax` 由三辆车的并列机枪各自声明，`ammo_pktm` 由三辆车的 PKTM 各自声明，发现按载具隔离。
- `machine_max:basic_launcher` 在内容包中零引用，填 `false` 不影响任何装配体。
- 第一方内容没有 `ammo_loader` 实例，该类型只做 §5.3 的编译适配，没有内容迁移。
- schema 改动面：`regen_loader_dynamic_attr.schema.json` 去掉 `projectile_types` 与 `belt_label`、加入 `belt_definition`；`launcher_static_attr.schema.json` 加入 `single_round_loading`。两份各有 24 份副本，改完跑 `./gradlew syncSchemas`。
- 代码改动面：`RegenLoaderSubsystemAttr` 的 CODEC 与展平、`RegenLoaderSubsystem` 的交付与回收、`WeaponControllerSubsystem#beltOf` 的池分组、`HudWeaponSnapshot#beltLabel` 与 `WeaponHudMolangContext` 的读取；`IAmmoConsumer` 的三个推送式方法在生产代码中没有调用方，随拉取口径删除。三个弹药专用 GameTest（`AmmoDiscoveryGameTest`、`AmmoRoutingGameTest`、`RegenLoaderFillingGameTest`）删除；`ModularDamageGameTest` 保留模块损伤与殉爆用例，删除其中依赖逐发退弹／交付的用例并改写其弹药夹具。
- 协议版本在 P4 实施时重读 `MMPayloadRegistry#register` 并递增，不按本节的 `sync:2.9.3` 预写。

## 9. 实施顺序与编译验收

### 9.1 实施顺序

| 步骤 | 工作 | 完成证据 |
| --- | --- | --- |
| P0 | 读取根与相关嵌套 AGENTS、核对工作区；清点两类供给者、所有 Launcher、协议版本与内容字段 | 源／目标模式、输出数量、容量、时间的迁移清单，指出同期其他任务改动；清单见 §8.1 |
| P1 | AmmoBelt 值类型与 Codec／网络格式、label、可选容器 ID 及自身回充定义／状态 | 有限混装数据和回充状态进入主源码；物品／容器资源实现保持为后续事项 |
| P2 | regen 使用 belt_definition，沿用 regen_per_minute，接入整份预览、预留、交付与计数回收；ammo_loader 编译适配 | 源生产和已交付 Belt 回充分别持有状态；不引入等价生成时间字段，物品路径不以占位实现声称完成 |
| P3 | Launcher 布尔、本地 Belt 消费与回充、保留膛内弹的整份替换 | 普通／充能消费时点明确；成功射击刷新 Belt 延迟，充能 Belt 可正常换装 |
| P4 | 路由、存档、同步、HUD、损伤代码及相关测试源码的适配；删除逐发弹药用例；按需更新协议与配置 | 现有主源码调用链可编译；字段迁移与未实现部分准确记录 |
| P5 | 主源码编译、文档和文件清单核对 | §9.2 命令成功；报告明确“编译通过，运行行为未验” |

### 9.2 唯一阶段验收：主源码编译

代码实施后从 Machine-Max 仓库根执行：

```powershell
.\gradlew --offline classes
```

本阶段不要求编写新的 JUnit／GameTest，不运行 `test`、`runGameTestServer`、`runClient` 或完整 `build` 作为验收。服务于逐发交付场景的 `AmmoDiscoveryGameTest`、`AmmoRoutingGameTest`、`RegenLoaderFillingGameTest` 随接口改造删除；`ModularDamageGameTest` 的模块损伤与殉爆用例保留，只删除其中依赖逐发退弹／交付的用例并改写夹具。既有行为的运行验证归 F7。

`classes` 成功只能证明主源码及其构建依赖可编译，不能报告物品往返、库存守恒、存档、多人同步或实机表现已经通过验证。涉及 schema 的副本同步属于编辑步骤，不扩展本阶段验收门槛。

### 9.3 实施记录

1. P1：`common/mech/ammo/AmmoBelt` 落地为带可变游标的 final class，含 JSON Codec、NBT 存档形状与 `StreamCodec`；回充配置与运行态分开保存，同步快照按 1/20 量化回充进度。新增 `OPTIONAL_AMMO_BELT` 同步序列化器。
2. P2：regen 实例改用 `belt_definition`，源按 `magazine.regen_per_minute` 生产数量库存，交付取 `min(库存, length)`（不足一份时交剩余）、回收按剩余发数全收或全拒；删除弹链指针与对接状态；`ammo_loader` 明确报告不可交付／拒收。
3. P3：发射器新增 `single_round_loading`、已装 `AmmoBelt` 与膛内弹两个同步项、本地消费与自身回充、以及保留膛内弹的整份换装。换装不设本地计时与执行状态：已装规格与当前供给者的交付规格不一致即在途，由供给者自己的装填流程决定何时可交付。
4. P4：路由按交付规格分组，控制器的选中弹链改为交付规格同步；协议组提升为 `sync:3.0.0`；`IAmmoConsumer` 的三个推送式方法删除；三个弹药专用 GameTest 删除，`ModularDamageGameTest` 改为按份退弹夹具。
5. P5：`.\.gradlew --offline classes` 通过；`checkSchemas`、`checkContent`、`compileTestJava` 与 `test` 同时通过。

## 10. 问题与后续待细化重构计划

以下条目是后续设计入口，不代表已有实现，也不将某个新接口或管理器列为必然交付。

| 编号 | 计划主题 | 需要解决的问题 | 细化前提与范围 |
| --- | --- | --- | --- |
| F1 | 装弹机构与弹药架子系统拆分 | 一套机构服务多个储存位置；同一储备可由人工或机械取用；固定外置弹箱的真实库存位置、命中箱与殉爆归属 | 以本阶段交付边界为基础，确定各独立子系统的状态、发现、共享占用和迁移；普通物品弹架与再生弹架分别管理自身库存 |
| F2 | 装填任务、人工／机械阶段与取用代价 | 人力备份和机构流程各自的基准秒数、容器附加秒数、分段音效、换装中断、待发／后备位置的时间差 | [人工与机械装填及操作员接入计划](武器系统-人工与机械装填及操作员接入计划.md) §2—§3、§6—§8 记录固定人工／机械两段及统一执行；本文第一阶段采用 §4.3 的规格差异驱动整份替换 |
| F3 | 乘员岗位与装填能力 | 个人装填能力与岗位时间倍率、最快可用执行者、单人占用、机娘宿主能力入口 | [人工与机械装填及操作员接入计划](武器系统-人工与机械装填及操作员接入计划.md) §4、§8 记录需求方发现座位、推荐 Part 内范围及按实际耗时选人；玩家／NPC／机娘的能力读取入口待实施前确定 |
| F4 | 待发架与后备架之间的弹药转运 | 待发位置耗尽、后备有弹时由人员或机构转运；转运可与武器装填形成不同任务 | 确定允许路径、存入单位、容量预留和对射击的影响，不把所有可发现库存自动视为可达 |
| F5 | 容器定义与弹药／供弹具物品实现 | 型号加载、容量与安装校验、模型呈现、AmmoBelt 物品组件、实际读取和回收、空容器与单发物品映射 | 复用本阶段实例格式；物品供给者按整件交付／回收，regen 按数量回收；交付真实物品闭环后再运行相关验证 |
| F6 | 玩家配弹编辑与操作界面 | 自定义有序混装、保存配弹方案、实际取出／补入、空容器填装、弹链编辑及手动提前换装输入 | 依赖 F5；预设指导未来装填，已装内容保存实际结果；完整编辑与交互另列实施计划 |
| F7 | 行为回归与运行验收 | 有限弹序和自身回充进度编解码、成功射击延迟、充能换装、两处生产的数量核对、接收方拒收不改库存及调用方明确丢弃、物品往返、库存损伤／殉爆、多人同步和客户端表现 | 依据实际交付的功能确定 JUnit、GameTest 与实机场景；本阶段只编译，不把这些验证写成已完成 |
| F8 | 客户端模拟同步与状态持久化 | 2S38 首发后音效／特效缺失、导弹发射表现缺失；本地消费后的校准、供给指认、关系恢复与历史来源追踪 | [客户端模拟同步与状态持久化计划](武器系统-客户端模拟同步与状态持久化计划.md) §2—§7：回调缓存后在客户端 postTick 应用，不做回滚重推；宿主 ID ＋ 名称寻址；只保存子系统实际状态，关系重建、未完成装填重启，拒收允许丢弃；尚未实施 |

本阶段收尾报告给出各 F 项对应的新计划链接；若尚未细化，以本节编号作为明确入口，不创建只有标题的计划文件。第一阶段文档按 §9 的实际落地状态归类，不能因 F 项尚未实现而将其误计为本阶段缺口。
