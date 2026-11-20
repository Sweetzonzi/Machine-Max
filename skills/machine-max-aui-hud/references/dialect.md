# 绑定语法与查询词表

本页直接给出内容包使用的绑定契约，不需要查看模组实现。接线方式见 [组件设计与接入流程](workflow.md)。

## 1. 宿主与命名空间

| 名称 | 作用域 |
| --- | --- |
| hud.* | 该 HUD 看到的相机与视口；座位、相机和武器 HUD 均可使用 |
| vehicle.* | HUD 宿主所属载具的座位数据，以及同车观察者的换座状态 |
| local.* / global.* | HUD 宿主所属的 Part / 装配体数据，不是任意玩家实体 |
| weapon.* | 整台所属 WC，在重复项内也保持这个含义 |
| tube.* | weapon.tubes 的当前管，仅限该重复项及其后代 |
| ammo_option.* | weapon.ammo_options 的当前供弹选项，仅限该重复项及其后代 |
| seat.* | vehicle.seats 的当前座位，仅限该重复项及其后代 |
| variable.* / v.* | 元素变量；data-mm-index 声明的下标别名只读 |
| math.* | MoLang 数学函数，不代表任意浏览器函数均可用 |

武器读数的布局必须挂到 WC 上。座位或瞄具自己的布局可显示视角和载具数据，但不会因此获得 weapon.*。

## 2. 页面标记

| 属性 | 用法 |
| --- | --- |
| data-mm-text | 把字符串或有限数值写进叶子元素；该元素不能再包含其他元素 |
| data-mm-num | 求值为有限数值，写到 data-mm-var 指定的 CSS 变量 |
| data-mm-var | 合法 CSS 自定义属性名，例如 --fill |
| data-mm-unit | 附加单位；省略表示无单位。允许 %、px、deg、rad、em、rem、vw、vh、s、ms |
| data-mm-class | 返回一个类名片段，自动加 is- 前缀；元素的静态 class 保留 |
| data-mm-repeat | 集合标识：weapon.tubes、weapon.ammo_options 或 vehicle.seats，不是数组表达式 |
| data-mm-index | 可选的下标别名，格式 [a-z_][a-z0-9_]*，从零开始 |

`data-mm-text` 的元素内保留可见占位文字（`--`、`0`、`EMPTY` 等），`data-mm-num` 对应的 CSS 变量在样式里写 fallback。浏览器预览不执行绑定，没有占位时页面会是空白；占位不要写成看起来像实时读数的数字。完整示例见 [组件设计与接入流程](workflow.md) 的“无数据与预览默认值”。

~~~html
<div class="weapon" data-mm-class="weapon.selected ? 'selected' : 'unselected'">
  <span data-mm-text="weapon.name"></span>
  <span data-mm-text="weapon.reserve_text"></span>
</div>
~~~

根元素得到 is-selected 或 is-unselected。返回值不能含多个空格分隔的类名，也不要再写一次 is- 前缀。对于数值布尔查询，用三元表达式明确选择文字。

MoLang 不是 JavaScript。组合文字优先用已格式化查询（这类查询以 `_text` 结尾，例如 `weapon.reserve_text`），或将标签与数值分到相邻元素。不要假设字符串加法、字符串模板或浏览器对象可用。

## 3. 重复模板

~~~html
<style>
.tubes { display: flex; flex-direction: column; gap: 6px; }
.track { height: 3px; background: #ffffff20; }
.fill { display: block; height: 3px; width: var(--fill, 0%); background: #a4ff80; }
</style>
<div class="tubes">
  <div class="tube" data-mm-repeat="weapon.tubes" data-mm-class="tube.state">
    <span data-mm-text="tube.index + 1"></span>
    <span data-mm-text="tube.round_label"></span>
    <span data-mm-text="tube.state == 'ready' ? 'RDY' : (tube.state == 'empty' ? 'EMPTY' : tube.loading_time_text)"></span>
    <div class="track">
      <i class="fill" data-mm-num="tube.loading_progress * 100" data-mm-var="--fill" data-mm-unit="%"></i>
    </div>
  </div>
  <div class="ammo-option" data-mm-repeat="weapon.ammo_options" data-mm-class="ammo_option.selected ? 'selected' : 'unselected'">
    <span data-mm-text="ammo_option.label"></span>
    <b><span data-mm-text="ammo_option.reserve_remaining"></span>/<span data-mm-text="ammo_option.reserve_infinite ? '∞' : ammo_option.reserve_capacity"></span></b>
  </div>
</div>
~~~

tube.loading_progress 与 tube.supply_refill_progress 均为 0～1；CSS 百分比需乘 100。装入发射管的进度与供弹源的逐发再生/整批回充是两套不同读数，不要共用一条进度值。

可选写法：在条目根添加 data-mm-index="barrel"，然后使用 weapon.tube_loading_progress(variable.barrel)。variable.barrel / v.barrel 是本轮当前项下标，禁止赋值；不写别名时直接使用 tube.* 即可。

模板约束：

- 标记所在元素及整棵子树一起复制；父容器只定义一次。空集合显示零项。
- 支持同级重复区域，不支持嵌套 data-mm-repeat。
- 重复模板根及后代不能有固定 id，使用 class 或数据标记定位。
- 管区域不能使用 ammo_option.*，供弹选项区域不能使用 tube.*；区域外也不能隐式读取第零项。
- 管和供弹选项的稳定身份由绑定层管理。不能把下标、显示标签或列表数量当成身份；重排后下标可能变化。
- 没有 data-mm-key、data-mm-as、作者自定义集合或过滤/分页表达式的约定，不自行增加此类属性。

显式索引查询可在重复区域外使用，例如 weapon.tube_loading_progress(0)、weapon.ammo_option_label(1)。参数不能省略；weapon.tube_loading_progress() 不是“当前管”。非整数、负数或越界索引返回 0 或空字符串，不能据此判断查询拼写或作用域正确。

## 4. 武器查询

HUD 业务查询使用单层 `namespace.property`，不支持对象路径式的多段点写法。带参数的查询写为 `weapon.tube_state(i)`，不要写成 `tube.state.value`。数值布尔使用 1/0；时间值单位为秒；进度值为 0～1。无活动时间的数值时间查询返回 -1，对应文本查询为空。

`weapon.state` / `tube.state` 使用 `ready`、`reloading`、`empty`。`tube.loading_phase` 表示当前装入发射管的作业阶段（`human` 或 `machine`，没有作业时为 `idle`）；`tube.supply_refill_phase` 表示供弹源状态（`regenerating`、`refilling` 或 `idle`）。这是两条独立流程，装填进度与供弹源补充进度可能同时变化。MoLang 的 `==` / `!=` 在任一侧是字符串（字符串字面量、字符串查询、`??` 兜底或字符串三元）时按文本比较，两侧都是数值时按 double 比较；字符串与数字混比时数字按 `1.0` 这样的文本参与比较，所以 `'1' == 1` 为假，而 `tube.state == 'ready'` 按状态文本判断。样式切换仍交给 `data-mm-class="tube.state"`。

| 查询 | 类型与口径 |
| --- | --- |
| weapon.name | 字符串，WC 名称；有翻译时使用翻译文字 |
| weapon.selected | 数值布尔，本观察者是否选中该 WC |
| weapon.aimed | 数值布尔，全部有效发射管的炮管轴线都在瞄准容差内；没有发射管时为 0，与装填就绪无关 |
| weapon.state | 字符串，ready 表示至少一根管已备好一发（可立即开火），reloading 表示没有就绪管但至少一根在装填，empty 表示全部无弹或无供给者 |
| weapon.loaded_rounds | 数值，待发发数 = 膛内发数 + 已装弹链余量 |
| weapon.loaded_belt_rounds | 数值，各有效管已装弹链余量合计，不含膛内弹 |
| weapon.reserve_remaining | 数值，当前选中供弹选项对应的备弹，不含膛内弹与已装弹链；未选中选项时汇总可用供弹源 |
| weapon.reserve_capacity | 数值，上述备弹容量；无上限时为 -1，并可用 reserve_infinite 判断 |
| weapon.reserve_infinite | 数值布尔，上述备弹容量是否无上限，不表示备弹一定充足 |
| weapon.reserve_text | 字符串，格式化的备弹/容量读数；无限容量显示 ∞ |
| weapon.chambered_count | 数值，已压弹的有效管数 |
| weapon.tube_count / weapon.ammo_option_count | 数值，有效管数 / 可选供弹项数 |
| weapon.round_label | 字符串，控制器显示弹药标签，不代表各管都装同一种弹 |
| weapon.active_ammo_id | 字符串，弹道解使用的弹种注册键（首根有膛内弹的管；无膛内弹时取首个供给者的弹种） |
| weapon.chambered_round_mismatch | 数值布尔，任一根膛内弹不包含在选中弹链序列中；为真时 weapon.round_label 显示那发 |
| tube.index / ammo_option.index | 数值，本轮当前项的零基下标 |
| tube.state | 字符串，该管的准备状态：ready 已备好一发、reloading 正在装填、empty 无弹 |
| tube.loading_phase | 字符串，发射管装填作业阶段：human、machine 或 idle |
| tube.loading_progress | 数值，当前装入发射管的进度，范围 0～1；无进行中的装填为 0 |
| tube.loading_time_remaining | 数值，当前装填作业剩余秒数；无活动作业为 -1 |
| tube.loading_time_text | 字符串，格式化的装填剩余时间；无活动作业为空 |
| tube.supply_refill_phase | 字符串，供弹源补充阶段：regenerating 逐发再生、refilling 整批回充、idle 无补充 |
| tube.supply_refill_progress | 数值，供弹源当前补充进度，范围 0～1；无活动补充为 0 |
| tube.supply_refill_time_remaining | 数值，供弹源当前补充剩余秒数；无活动补充为 -1 |
| tube.supply_refill_time_text | 字符串，格式化的供弹源补充剩余时间；无活动补充为空 |
| tube.has_supply_source | 数值布尔，该管是否已有供给者；0 表示路由没摊到装弹机，与"有机器但打空"不同 |
| tube.round_label | 字符串，该管当前显示的弹药 |
| tube.loaded_rounds | 数值，该管待发发数 = 膛内（0 或 1）+ 已装弹链余量 |
| tube.chambered | 数值布尔，该管膛内是否有弹 |
| tube.chambered_ammo_label | 字符串，该管实际膛内弹药，空膛可为空字符串 |
| tube.loading_source_label | 字符串，当前装填作业来源的显示标签；无作业时为空 |
| tube.swapping | 数值布尔，该管是否正在替换已装的那一份；为真时射击锁定，即使膛内有弹也不算就绪 |
| tube.aimed | 数值布尔，该管炮管轴线与观察者瞄准点的夹角在控制器瞄准容差内 |
| tube.has_belt | 数值布尔，该管当前是否实际装着弹链 |
| tube.belt_remaining | 数值，该管已装弹链余量 |
| tube.belt_capacity | 数值，该管已装弹链的整份长度，不是剩余容量 |
| tube.belt_label | 字符串，该管已装弹链的标签 |
| tube.source_reserve_remaining | 数值，该管当前供给者还能提供的备弹 |
| tube.source_reserve_capacity | 数值，该管当前供给者的备弹容量；无上限时为 -1 |
| tube.source_reserve_infinite | 数值布尔，该管当前供给者的容量是否无上限 |
| ammo_option.label | 字符串，可选供弹项标签 |
| ammo_option.reserve_remaining | 数值，该选项对应供弹源的备弹数量 |
| ammo_option.reserve_capacity | 数值，该选项对应供弹源的备弹容量；无上限时为 -1 |
| ammo_option.reserve_infinite | 数值布尔，该选项对应供弹源的备弹容量是否无上限 |
| ammo_option.selected | 数值布尔，该供弹项是否被选中 |

逐管和逐弹链的显式索引写法：

| 当前项查询 | 区域外写法 |
| --- | --- |
| tube.state / tube.round_label | weapon.tube_state(i) / weapon.tube_round_label(i) |
| tube.loading_phase / tube.loading_progress / tube.loading_time_remaining / tube.loading_time_text | weapon.tube_loading_phase(i) / weapon.tube_loading_progress(i) / weapon.tube_loading_time_remaining(i) / weapon.tube_loading_time_text(i) |
| tube.supply_refill_phase / tube.supply_refill_progress / tube.supply_refill_time_remaining / tube.supply_refill_time_text | weapon.tube_supply_refill_phase(i) / weapon.tube_supply_refill_progress(i) / weapon.tube_supply_refill_time_remaining(i) / weapon.tube_supply_refill_time_text(i) |
| tube.loaded_rounds / tube.chambered / tube.has_supply_source / tube.swapping / tube.aimed | weapon.tube_loaded_rounds(i) / weapon.tube_chambered(i) / weapon.tube_has_supply_source(i) / weapon.tube_swapping(i) / weapon.tube_aimed(i) |
| tube.chambered_ammo_label | weapon.tube_chambered_ammo_label(i) |
| tube.loading_source_label | weapon.tube_loading_source_label(i) |
| tube.has_belt / tube.belt_remaining / tube.belt_capacity / tube.belt_label | weapon.tube_has_belt(i) / weapon.tube_belt_remaining(i) / weapon.tube_belt_capacity(i) / weapon.tube_belt_label(i) |
| tube.source_reserve_remaining / tube.source_reserve_capacity / tube.source_reserve_infinite | weapon.tube_source_reserve_remaining(i) / weapon.tube_source_reserve_capacity(i) / weapon.tube_source_reserve_infinite(i) |
| ammo_option.label / ammo_option.reserve_remaining / ammo_option.reserve_capacity / ammo_option.reserve_infinite / ammo_option.selected | weapon.ammo_option_label(i) / weapon.ammo_option_reserve_remaining(i) / weapon.ammo_option_reserve_capacity(i) / weapon.ammo_option_reserve_infinite(i) / weapon.ammo_option_selected(i) |

三个计数按“发数现在在哪”分层，彼此不包含：膛内是已压进膛的那一发，已装弹链是本管当前装着的一份，备弹是供弹源还能给出的数量。一份弹链交到本管时发数从备弹转到已装弹弹链，压进膛时从已装弹链转到膛内。需要显示选项的备弹上限时，重复 `weapon.ammo_options` 并分别读取 `ammo_option.reserve_remaining` 与 `ammo_option.reserve_capacity`；无限容量由 `ammo_option.reserve_infinite` 判别。

对准读数是另一条轴，与装填无关：

- 目标点是观察者自己的期望瞄准点，观察者换了读数就换，同一控制器对不同观察者可以不同。
- 容差来自控制器的瞄准容差，判定与开火门限是同一条：方向取含火控修正量的有效发射方向，修正只在武器持有开火权时生效。开火输入会转发回客户端，因此客户端的控制器同样参与这套判定。
- 容差设得很大的控制器（例如第一方机枪常见的 60 度）几乎恒为对准，这类条目应另配显示条件。
- `weapon.aimed` 要求全部有效管都对准；没有发射管时为 0。单管用 `tube.aimed` 或 `weapon.tube_aimed(i)`。

切弹后，选择箭头可以先变化，膛内弹和进行中的装填保持实际来源。库存为零时也可能有就绪管；满格进度表达准备状态，不表达仓库剩余量。装入发射管的作业用 `tube.loading_*` 查询；供弹源逐发再生或整批回充用 `tube.supply_refill_*` 查询。这根管有没有摊到供给者看 `tube.has_supply_source`。单管进度合入弹链行需要可靠的来源对应关系；这些文字标签不能证明身份相同，缺少对应能力时使用独立逐管行。

weapon.bdc_hold_rad(distance) 接收距离（米），返回标准弹道抬角（弧度）。其口径为海平面、静止平台、同高目标、相对水平出射轴和零归零角，不是对当前目标的完整火控解。无解、无弹或不支持的制导/推进弹可返回 0，不能把 0 当作“求解一定成功”。

## 5. 视角与载具查询

| 查询 | 含义 |
| --- | --- |
| hud.camera_pos_x / hud.camera_pos_y / hud.camera_pos_z | 相机世界坐标 |
| hud.view_heading_deg | 航向角：北 0°、东 90° |
| hud.view_pitch_deg | 俯仰角，抬头为正 |
| hud.view_roll_deg | 视角横滚角，单位度 |
| hud.fov_deg | 当前视角 FOV，单位度 |
| hud.current_zoom | 当前缩放倍率 |
| hud.screen_width / hud.screen_height | 屏幕尺寸读数；不要直接当作页面 CSS 像素 |
| local.durability / local.max_durability / local.is_destroyed | 宿主 Part 根部位的耐久、最大耐久、是否损毁 |
| global.hp / global.max_hp | 所属装配体的生命值 / 最大生命值 |
| global.energy / global.max_energy | 所属装配体的能源 / 能源容量 |
| local.get('name') / global.get('name') | 读取 Part / 装配体已提供的数值变量 |
| local.get_str('name') / global.get_str('name') | 读取对应的字符串变量 |

name 必须是目标内容包和运行环境实际提供的变量名。这些接口不用于查询任意对象字段；一个未提供的变量得到 0 或空文字，不代表所需数据已经接通。

查询与标记由运行环境提供。超出本页词表的能力应查该版本随附的作者文档；无法确认支持时明确标注，不把自创名称写进最终 HUD。

## 载具座位查询

| 查询 | 含义 |
| --- | --- |
| `vehicle.seat_count` | 宿主载具的座位数 |
| `vehicle.seat_right(i)`、`vehicle.seat_up(i)`、`vehicle.seat_forward(i)` | 相对车体包围盒中心向右、向上、向前的带符号距离，单位为方块 |
| `vehicle.seat_has_position(i)` | 1 表示 locator 可解析，0 表示无位置 |
| `vehicle.seat_name(i)` | 本地化座位名称 |
| `vehicle.seat_entity_name(i)` | 从当前乘员实体读取的名称，含非玩家乘员；未占用或实体引用暂缺时为空文本 |
| `vehicle.seat_occupied(i)`、`vehicle.seat_available(i)` | 占用、是否可换入，各为 1 或 0 |
| `vehicle.current_seat_number`、`vehicle.next_seat_number`、`vehicle.pending_seat_number` | 同车观察者的当前座、顺向候选和在途目标编号 |
| `vehicle.seat_switch_key` | 换座键的本地化显示名 |
| `vehicle.seat_chord_armed` | 换座键按住期间为 1，此时数字键按座号解释 |
| `vehicle.seat_map_visible` | 内置座位图是否满足同车多座、界面与相机状态 |

座位编号从 1 开始。无载具、无对应座位或无有效数据时，相关数字为 0、字符串为空。有效原点用 `seat_has_position(i)` 区分。座位位置与 CSS 尺寸、模型观察角无关。动态名单使用 `data-mm-repeat="vehicle.seats"`，当前项查询与 builtin 样式见 [座位列表](vehicle-seats.md)。
