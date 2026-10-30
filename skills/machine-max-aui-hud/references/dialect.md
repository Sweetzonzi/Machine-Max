# 绑定语法与查询词表

本页直接给出内容包使用的绑定契约，不需要查看模组实现。接线方式见 [组件设计与接入流程](workflow.md)。

## 1. 宿主与命名空间

| 名称 | 作用域 |
| --- | --- |
| hud.* | 该 HUD 看到的相机与视口；座位、相机和武器 HUD 均可使用 |
| local.* / global.* | HUD 宿主所属的 Part / 装配体数据，不是任意玩家实体 |
| weapon.* | 整台所属 WC，在重复项内也保持这个含义 |
| tube.* | weapon.tubes 的当前管，仅限该重复项及其后代 |
| belt.* | weapon.belts 的当前弹链，仅限该重复项及其后代 |
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
| data-mm-repeat | 集合标识，只能是 weapon.tubes 或 weapon.belts，不是数组表达式 |
| data-mm-index | 可选的下标别名，格式 [a-z_][a-z0-9_]*，从零开始 |

~~~html
<div class="weapon" data-mm-class="weapon.selected ? 'selected' : 'unselected'">
  <span data-mm-text="weapon.name"></span>
  <span data-mm-text="weapon.ammo_text"></span>
</div>
~~~

根元素得到 is-selected 或 is-unselected。返回值不能含多个空格分隔的类名，也不要再写一次 is- 前缀。对于数值布尔查询，用三元表达式明确选择文字。

MoLang 不是 JavaScript。组合文字优先用已格式化查询，或将标签与数值分到相邻元素。不要假设字符串加法、字符串模板或浏览器对象可用。

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
    <span data-mm-text="tube.state == 'ready' ? 'RDY' : (tube.state == 'empty' ? 'EMPTY' : tube.reload_text)"></span>
    <div class="track">
      <i class="fill" data-mm-num="tube.fill" data-mm-var="--fill" data-mm-unit="%"></i>
    </div>
  </div>
</div>
~~~

tube.fill 是 0～100，配合 % 使用时不要再乘 100。tube.reload_text 在就绪或空管时可以为空，因此示例按状态显式选择文字。

可选写法：在条目根添加 data-mm-index="barrel"，然后使用 weapon.tube_fill(variable.barrel)。variable.barrel / v.barrel 是本轮当前项下标，禁止赋值；不写别名时直接使用 tube.* 即可。

模板约束：

- 标记所在元素及整棵子树一起复制；父容器只定义一次。空集合显示零项。
- 支持同级重复区域，不支持嵌套 data-mm-repeat。
- 重复模板根及后代不能有固定 id，使用 class 或数据标记定位。
- 管区域不能使用 belt.*，弹链区域不能使用 tube.*；区域外也不能隐式读取第零项。
- 管和弹链的稳定身份由绑定层管理。不能把下标、显示标签或列表数量当成身份；重排后下标可能变化。
- 没有 data-mm-key、data-mm-as、作者自定义集合或过滤/分页表达式的约定，不自行增加此类属性。

显式索引查询可在重复区域外使用，例如 weapon.tube_fill(0)、weapon.belt_label(1)。参数不能省略；weapon.tube_fill() 不是“当前管”。非整数、负数或越界索引返回 0 或空字符串，不能据此判断查询拼写或作用域正确。

## 4. 武器查询

表中数值布尔值使用 1/0；字符串状态使用 ready、reloading、empty，装填阶段使用 loading、regenerating、refilling、idle。这些是机器可读值，页面文案可以由作者选择。MoLang 的 `==` / `!=` 在任一侧是字符串（字符串字面量、字符串查询、`??` 兜底或字符串三元）时按文本比较，两侧都是数值时按 double 比较；字符串与数字混比时数字按 `1.0` 这样的文本参与比较，所以 `'1' == 1` 为假，而 `tube.state == 'ready'` 按状态文本判断。样式切换仍交给 `data-mm-class="tube.state"`。

| 查询 | 类型与口径 |
| --- | --- |
| weapon.name | 字符串，WC 名称；有翻译时使用翻译文字 |
| weapon.selected | 数值布尔，本观察者是否选中该 WC |
| weapon.state | 字符串，ready 表示至少一根管已备好一发（可立即开火），reloading 表示没有就绪管但至少一根在装填，empty 表示全部无弹或无供给者 |
| weapon.ammo_remaining | 数值，选中供弹链的库存，不含膛内弹 |
| weapon.ammo_capacity | 数值，供弹库存容量；无上限时结合 ammo_infinite 判断 |
| weapon.ammo_infinite | 数值布尔，容量是否无上限，不表示库存一定充足 |
| weapon.ammo_text | 字符串，格式化的库存/容量读数 |
| weapon.reload_text | 字符串，控制器的准备状态或装填读数 |
| weapon.tube_count / weapon.belt_count | 数值，有效管数 / 可选弹链数 |
| weapon.round_label | 字符串，控制器显示弹药标签，不代表各管都装同一种弹 |
| weapon.active_ammo | 字符串，弹道解使用的弹种注册键（首根有膛内弹的管；无膛内弹时取首个供给者的弹种） |
| weapon.round_mismatch | 数值布尔，任一根膛内弹不包含在选中弹链序列中；为真时 weapon.round_label 显示那发 |
| tube.index / belt.index | 数值，本轮当前项的零基下标 |
| tube.state | 字符串，该管的准备状态：ready 已备好一发、reloading 正在装填、empty 无弹 |
| tube.phase | 字符串，该管的装填阶段：loading 正在输弹、regenerating 逐发再生、refilling 整批回充、idle 没有进行中的装填 |
| tube.has_source | 数值布尔，该管是否已有供给者；0 表示路由没摊到装弹机，与"有机器但打空"不同 |
| tube.fill | 数值，该管 0～100 的准备进度；输弹、再生、整批回充三种阶段各自的口径，用 tube.phase 区分 |
| tube.round_label | 字符串，该管当前显示的弹药 |
| tube.chambered_ammo_label | 字符串，该管实际膛内弹药，空膛可为空字符串 |
| tube.loading_belt_label | 字符串，该管正在装填的来源标签；只有 tube.phase 为 loading 时非空 |
| tube.reload_remaining | 数值，当前阶段的剩余秒数（输弹、逐发再生或整批回充）；非装填状态为 0，无法折算时为负值，不能当成就绪判据 |
| tube.reload_text | 字符串，格式化的装填读数 |
| belt.label | 字符串，弹链标签 |
| belt.remaining | 数值，该弹链库存 |
| belt.selected | 数值布尔，该弹链是否被选中 |

逐管和逐弹链的显式索引写法：

| 当前项查询 | 区域外写法 |
| --- | --- |
| tube.state / tube.fill | weapon.tube_state(i) / weapon.tube_fill(i) |
| tube.round_label / tube.reload_text | weapon.tube_round_label(i) / weapon.tube_reload_text(i) |
| tube.reload_remaining | weapon.tube_reload_remaining(i) |
| tube.phase / tube.has_source | weapon.tube_phase(i) / weapon.tube_has_source(i) |
| tube.chambered_ammo_label | weapon.tube_chambered_ammo_label(i) |
| tube.loading_belt_label | weapon.tube_loading_belt_label(i) |
| belt.label / belt.remaining / belt.selected | weapon.belt_label(i) / weapon.belt_remaining(i) / weapon.belt_selected(i) |

切弹后，选择箭头可以先变化，膛内弹和进行中的装填保持实际来源。库存为零时也可能有就绪管；满格进度表达准备状态，不表达仓库剩余量。正在等输弹、等再生还是等整批回充看 tube.phase，这根管有没有摊到供给者看 tube.has_source。单管进度合入弹链行需要可靠的来源对应关系；这些文字标签不能证明身份相同，缺少对应能力时使用独立逐管行。

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
