# PDA 蓝图终端：接口设计文档

**文档状态**：已实现
**适用版本**：NeoForge 1.21.1 · Machine-Max 1.0.3-beta.6
**编写日期**：2026-09-27

## 0. 本文自包含

阅读本文不需要预先阅读任何其他文档、代码或历史讨论。本文给出 **PDA 蓝图终端**的全部对外契约：注册项、数据模型、方法签名、网络包字段、客户端接口、AUI 元素 id 与语言键。每一处契约都写明调用方、被调方、参数含义与失败返回值。

本文只写"接口长什么样、怎么调用"，不写设计动机与取舍理由；动机与完整背景见配套的《PDA蓝图终端-详细设计文档.md》。两份文档可以独立阅读：本文用到的每一个类型与概念都在第 1 章或本节内定义。

配套产物：

| 产物 | 位置 |
| --- | --- |
| 详细设计文档 | `docs/plan/已实现/PDA蓝图终端-详细设计文档.md` |
| 静态原型 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_ui_prototype.html` + `pda_ui.css` |
| 实施页 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_ui.html` |
| HUD 原型 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_hotbar_prototype.html` + `pda_hotbar.css` |
| HUD 实施页 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_hotbar.html` |

## 1. 术语与类型速览

| 术语 | 定义 |
| --- | --- |
| 零件制造蓝图 | 物品类 `PartFabricatingBlueprintItem`，携带 `part_type` 与 `recipe_type` 两个数据组件，可放置为未组装零件 |
| 通用制造蓝图 | 物品类 `FabricatingBlueprintItem`，携带 `recipe_type` 数据组件，不能放置 |
| 蓝图条目 | PDA 内记录的一条已收纳蓝图，由配方 id 唯一标识 |
| 零件配方 | 物品类 `PartFabricatingRecipe`；其 id 的 path 以 `part_fabricating/` 开头，零件 id 由该 id 推导 |
| 通用配方 | `FabricatingRecipe` 中不是零件配方者，只在制造机中加工 |
| 配方索引 | 本侧（客户端与服务端各一份）的"配方 id → 配方"映射，由装载期构建，统一经 `MMDynamicRes.getAllFabricating(Level)` 访问 |
| 装配资格 | 玩家推进某零件装配的许可；持有该零件的零件制造蓝图即可获得 |
| 零件来源 | 一次放置请求所依据的零件；由手持物品提供——零件物品与零件蓝图读自身组件，PDA 读当前格位绑定的条目 |
| 设计模式 | PDA 的一种状态，存在 `PDA_DATA.designMode`：开启时右键按当前格位的条目放置零件 |
| 当前格位 | PDA 的选中格位，存在 `PDA_DATA.selected`，取值域 0~8 |
| 设计模式快捷栏 | PDA 内置的 9 格蓝图选择器，格位序号 0~8 |

以下命名空间简称在本文中通用：`MMItems` 指 `common.registry.MMItems`（Kotlin 注册表），`MMDataComponents` 指 `common.registry.MMDataComponents`，`MMPayloadRegistry` 指 `network.MMPayloadRegistry`，`MMDynamicRes` 指 `external.MMDynamicRes`，`VehicleAssemblyHelper` 指 `common.mech.vehicle.VehicleAssemblyHelper`。

包路径约定：物品与数据模型置于 `io.github.sweetzonzi.machine_max.common.item.prop`，载荷置于 `io.github.sweetzonzi.machine_max.network.payload.pda`，处理器置于 `io.github.sweetzonzi.machine_max.network.handler.pda`，客户端界面置于 `io.github.sweetzonzi.machine_max.client.render.gui.screen`。

## 2. 注册接口

### 2.1 物品

| 项 | 值 |
| --- | --- |
| 物品 id | `machine_max:pda` |
| 类 | `PdaItem extends Item`，全限定名 `io.github.sweetzonzi.machine_max.common.item.prop.PdaItem` |
| 属性 | `new Item.Properties().stacksTo(1)` |
| 注册位置 | `MMItems.kt` |

```java
public class PdaItem extends Item implements PartAssemblyItem {
    public PdaItem();

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand);

    /** 见 3.3。 */
    @Override
    @Nullable
    public PartType getPartType(ItemStack stack, Level level);
}
```

`use` 的契约（客户端按顺序判定；服务端在任意条件下不做任何事并返回 `success`）：

| 条件 | 行为 | 返回值 |
| --- | --- | --- |
| 按住 Shift | 把 `PDA_DATA.designMode` 取反：先写入本地 PDA 栈的数据组件，再发 `PdaSetDesignModePayload`（见 6.2） | `success` |
| `PDA_DATA.designMode == true` | 走既有放置链路：以 `VehicleAssemblyHelper.getInstance().buildRequest(player, usedHand, stack)` 构造请求并 `PacketDistributor.sendToServer(request)`；请求为 `null` 时不发送（见 6.3） | `success` |
| 其余 | `Minecraft.getInstance().setScreen(new PdaScreen(usedHand))` | `success` |

### 2.2 数据组件

在 `MMDataComponents` 新增：

```java
public static final DeferredHolder<DataComponentType<?>, DataComponentType<PdaData>> PDA_DATA;
// builder: persistent(PdaData.CODEC).networkSynchronized(PdaData.STREAM_CODEC).cacheEncoding()

public static DataComponentType<PdaData> getPDA_DATA();
```

约束：必须同时提供 `persistent` 与 `networkSynchronized`，缺任一项都会导致界面读不到最新数据或数据无法落盘。

### 2.3 网络载荷

在 `MMPayloadRegistry` 新增载荷组 `pda:1.0.0`，注册四个 C→S 载荷：

| 载荷 | 方向 | 作用 |
| --- | --- | --- |
| `PdaDepositPayload` | C→S | 把背包中的蓝图存入 PDA |
| `PdaBindShortcutPayload` | C→S | 绑定或解绑设计模式快捷栏格位 |
| `PdaSelectShortcutPayload` | C→S | 写入当前格位序号 |
| `PdaSetDesignModePayload` | C→S | 写入设计模式开关 |

放置请求载荷 `PartAssemblyRequestPayload` 保持现有字段不变。

### 2.4 语言键

新增键（中英各一份，写入 `MMLanguageProviderZH_CN.java` 与 `MMLanguageProviderEN_US.java`）：

| 键 | 中文示例值 |
| --- | --- |
| `item.machine_max.pda` | 蓝图终端 |
| `gui.machine_max.pda.title` | 蓝图终端 |
| `gui.machine_max.pda.group.stored` | 已收纳 |
| `gui.machine_max.pda.group.inventory` | 背包中的蓝图 |
| `gui.machine_max.pda.group.shortcut` | 设计模式快捷栏 |
| `gui.machine_max.pda.btn.deposit` | 存入 |
| `gui.machine_max.pda.btn.deposit_all` | 一键存入全部 |
| `gui.machine_max.pda.btn.stored` | 已收纳 |
| `gui.machine_max.pda.btn.merge` | 合并 (+%1$s) |
| `gui.machine_max.pda.btn.unbind` | 解除绑定 |
| `gui.machine_max.pda.status.select_entry` | 已选中「%1$s」，请点击快捷栏位以完成绑定 |
| `gui.machine_max.pda.status.select_slot` | 已选中第 %1$s 栏，请点击左侧条目以完成绑定 |
| `gui.machine_max.pda.hint.general` | 该蓝图暂无直接使用方式 |
| `gui.machine_max.pda.hint.empty_slot` | 当前栏位未绑定蓝图 |
| `gui.machine_max.pda.tag.unknown` | 未知蓝图 |
| `gui.machine_max.pda.tag.part` | 零件 |
| `gui.machine_max.pda.tag.general` | 通用 |
| `gui.machine_max.pda.count.stored` | 已收纳 %1$s 项 |
| `gui.machine_max.pda.count.infinite` | 无限 %1$s |
| `gui.machine_max.pda.count.limited` | 有限 %1$s |
| `gui.machine_max.pda.hud.title` | 设计模式 |
| `gui.machine_max.pda.key.right_click` | 右键 |
| `gui.machine_max.pda.key.sneak` | 潜行键 |
| `gui.machine_max.pda.action.place` | 放置零件 |
| `gui.machine_max.pda.action.exit` | 退出 |
| `gui.machine_max.pda.hint.unavailable` | 该蓝图当前不可用（配方已失效） |
| `message.machine_max.pda.deposit.success` | 已存入 %1$s 张蓝图 |
| `message.machine_max.pda.deposit.rejected` | %1$s 张蓝图已收纳，未重复存入 |
| `message.machine_max.pda.design_mode.enter` | 已进入设计模式 |
| `message.machine_max.pda.design_mode.exit` | 已退出设计模式 |

沿用既有物品名的键：`item.machine_max.part_fabricating_blueprint`（零件制造蓝图）、`item.machine_max.fabricating_blueprint`（制造蓝图）。

### 2.5 既有接口 `PartAssemblyItem` 的改造

`PartAssemblyItem`（`common/item/prop/PartAssemblyItem.java`）当前的零件解析入口是静态方法 `getPartType(ItemStack, Level)`；本设计把它改为可覆写的实例方法，使"能提供零件来源的物品"成为可扩展契约：

```java
public interface PartAssemblyItem {
    /** 物品自身提供零件来源；默认实现读物品栈上的组件。 */
    @Nullable
    default PartType getPartType(ItemStack stack, Level level);

    /** 静态派发：非 PartAssemblyItem 返回 null。所有外部调用点改用它。 */
    @Nullable
    static PartType partTypeOf(ItemStack stack, Level level);

    /** 仍为静态：无同名实例方法，且只被默认实现内部调用。 */
    @Nullable
    static RecipeHolder<PartFabricatingRecipe> getRecipeHolder(ItemStack stack, Level level);
}
```

约束：

- 默认实现与改造前 `getPartType` 的行为一致：先读 `machine_max:part_type` 组件，缺失时经 `machine_max:recipe_type` 组件查本侧零件配方索引取 `PartType`。因此 `PartItem` 与 `PartFabricatingBlueprintItem` 不需要改写解析逻辑；
- `PdaItem` 覆写 `getPartType`，契约见 3.3；
- 既有调用点由 `PartAssemblyItem.getPartType(...)` 改为 `PartAssemblyItem.partTypeOf(...)`，共 5 处：`PartItem` 内 2 处、`VehicleAssemblyHelper.onClientTick`、`VehicleAssemblyHelper.buildRequest`、`VehicleAssemblyServerHelper.handle`；
- `instanceof PartAssemblyItem` 的判定在改造后同样匹配 `PdaItem`。

## 3. 数据模型与解析契约

### 3.1 记录 `PdaData`

```java
public record PdaData(SortedMap<ResourceLocation, Integer> entries,
                      Map<Integer, ResourceLocation> shortcuts,
                      int selected,
                      boolean designMode) {
    /** 残留次数的哨兵值：表示无限次。 */
    public static final int INFINITE_USES = -1;
    /** entries 的定序器：配方 id 的字符串序（显式比较器，不依赖 ResourceLocation 自身的比较规则）。 */
    public static final Comparator<ResourceLocation> ENTRY_ORDER;
    public static final int SHORTCUT_COUNT = 9;
    public static final PdaData EMPTY;

    public static final Codec<PdaData> CODEC;                        // 四个字段全部参与编解码
    public static final StreamCodec<ByteBuf, PdaData> STREAM_CODEC;  // 由 CODEC 派生

    public static boolean isInfinite(int uses);                    // uses < 0

    @Nullable public ResourceLocation shortcutAt(int index);
    @Nullable public Integer usesOf(ResourceLocation recipeId);    // 未收纳时为 null
    public PdaData withShortcut(int index, @Nullable ResourceLocation recipeId);  // 为 null 即解绑
    public PdaData withSelected(int index);                                       // 见 6.2 的归一规则
    public PdaData withDesignMode(boolean on);
    public PdaData mergeEntry(ResourceLocation recipeId, int incomingUses);       // 按 4.2 规则合并或新增
    public PdaData withoutEntry(ResourceLocation recipeId);                       // 同时清除引用它的格位
    public PdaData sanitized();                                                   // 见 3.2
}
```

字段契约：

| 字段 | 含义 | 约束 |
| --- | --- | --- |
| `entries` | 配方 id → 残留次数（有序映射） | 键非空；值域为 `INFINITE_USES` 或正整数；迭代顺序恒为 `ENTRY_ORDER` |
| `shortcuts` | 格位 → 配方 id | 键域为 `0..8`；每个值必须在 `entries` 中有对应键 |
| `selected` | 当前格位序号 | 域为 `0..8` |
| `designMode` | 设计模式开关 | 无附加约束 |

`selected` 与 `designMode` 是 PDA 的物品状态：随数据组件持久化，并随网络同步到客户端（见 6.2 的写入约定）。

"条目"不是独立类型，它指 `entries` 里的一对「配方 id → 残留次数」；配方 id 即条目的唯一标识，键唯一由映射结构保证、读入时无需去重。

`entries` 的迭代顺序**由数据模型保证**：恒为 `ENTRY_ORDER`（配方 id 字符串序），与解码顺序、插入顺序无关。因此消费方直接按迭代顺序渲染即可，不需要自己排序。`PdaData` 的相等性与顺序无关（映射的相等性按条目比较），因此定序不影响 `mergeEntry` / `withoutEntry` 的判定。

**条目不存条目类别，也不存零件 id**：两者都经 `PdaHelper.partRecipeOf` / `PdaHelper.recipeOf`（见 4.1）从配方 id 反查。反查返回 `null` 表示该配方在本侧索引中不存在（被移除或装载期校验未通过），条目降级为不可用，不删除。

JSON 形态：`entries` 与 `shortcuts` 是对象，`selected` 是整数，`designMode` 是布尔。`entries` 的键是配方 id 字符串、值是残留次数（`-1` 表示无限）；`shortcuts` 的键是格位序号的十进制字符串（`"0"`~`"8"`）、值是配方 id 字符串。JSON 对象的键只能是字符串，因此两个映射都需要"字符串键"编解码器：`entries` 的键用 `ResourceLocation.CODEC`，解码结果再经 `xmap` 收口到 `ENTRY_ORDER` 有序映射；`shortcuts` 的键用 `Codec.STRING.xmap(Integer::parseInt, Object::toString)`。

### 3.2 不变量与 `sanitized()`

`sanitized()` 按顺序执行，返回一个满足全部不变量的新实例，用于所有反序列化入口（读组件、收报文）：

1. 丢弃 `entries` 中键为空的项；
2. `entries` 的值小于 `INFINITE_USES` 时归一为 `INFINITE_USES`；值为 `0` 的条目丢弃（残留次数为 0 等价于已耗尽）；
3. 丢弃 `shortcuts` 中键不在 `0..8` 的项；
4. 丢弃 `shortcuts` 中值在 `entries` 里找不到对应键的项；
5. `selected` 不在 `0..8` 时归一为 `Math.floorMod(selected, SHORTCUT_COUNT)`，与 `withSelected(int)` 共用同一段归一逻辑；
6. `designMode` 不做处理。

键唯一性由映射结构保证，因此不存在去重步骤；`entries` 的定序同样由结构保证（第 1 步之后重建为 `ENTRY_ORDER` 有序映射）。每一步都作用在它上一步的结果上，因此第 4 步看到的是第 2 步过滤后的 `entries`。

写入组件的所有路径都必须先经过 `sanitized()`；读出时可以再次调用以保证防御性。

**产出 `PdaData` 的每条路径都必须以 `sanitized()` 收口**——解码（组件读取、组件网络同步、`CODEC` 的 `xmap`）与每个改动方法（`mergeEntry`、`withoutEntry`、`withShortcut`）都一样。`entries` 的迭代顺序是可见状态（列表次序、8.4 的签名比较），漏在一处就会静默退化成插入序。

`sanitized()` **不**校验 `recipeId` 是否存在于本侧索引：它是无上下文（拿不到 `Level`）的纯数据类，且配方消失时条目应当保留（见 3.1）。

### 3.3 `PdaItem.getPartType` 的契约

`PdaItem` 覆写 `PartAssemblyItem.getPartType(ItemStack, Level)`，把"手持 PDA"解析为"当前格位的零件来源"：

| 前置状态 | 返回值 |
| --- | --- |
| `PDA_DATA.designMode == false` | `null` |
| 设计模式开启，`selected` 指向的格位未绑定条目 | `null` |
| 设计模式开启，格位绑定的配方在本侧索引中不是零件配方（通用配方或配方已失效） | `null` |
| 设计模式开启，格位绑定的配方在本侧索引中是零件配方 | `partRecipeOf(...).value().getPartType()` |

三种 `null` 情形对外不可区分，消费方一律按"该物品此刻不提供零件来源"处理：`VehicleAssemblyHelper` 会把预览状态清空，`buildRequest` 返回 `null`，服务端校验不通过。

实现约束：

- 解析一律走 `MMDynamicRes` 的本侧零件配方索引（`PdaHelper.partRecipeOf`，见 4.1），不查 `RecipeManager`；
- 不产出也不修改任何物品栈；
- 调用方经 `PartAssemblyItem.partTypeOf(ItemStack, Level)` 调用（见 2.5），不直接取 `getItem()` 强转。

## 4. 服务端逻辑接口

### 4.1 `PdaHelper`

```java
public final class PdaHelper {
    public static boolean isPda(ItemStack stack);

    /** 读数据组件；缺失或无 PDA_DATA 时返回 PdaData.EMPTY，不返回 null。 */
    public static PdaData getData(ItemStack stack);

    /** 写入数据组件（内部先 sanitized()）。 */
    public static void setData(ItemStack stack, PdaData data);

    /**
     * 本侧配方索引查找。
     *
     * @return 索引未就绪或该 id 不存在时返回 null
     */
    @Nullable
    public static RecipeHolder<FabricatingRecipe> recipeOf(Level level, ResourceLocation recipeId);

    /**
     * 按配方 id 取零件配方，即"条目是不是零件配方"的判据。
     *
     * @return 不是零件配方、或该 id 不存在时返回 null
     */
    @Nullable
    public static RecipeHolder<PartFabricatingRecipe> partRecipeOf(Level level, ResourceLocation recipeId);

    /**
     * 把一张蓝图物品解析为配方 id（存入时的归一入口）。
     * 归一顺序：part_type 组件经本侧零件配方索引反查配方 id → 缺失时读 recipe_type 组件。
     *
     * @return 无法解析（非蓝图物品、两个组件都缺失、或反查不到配方）时返回 null
     */
    @Nullable
    public static ResourceLocation recipeIdOf(ItemStack stack, Level level);

    /** 读某一格位绑定的配方 id；格位越界或未绑定时返回 null。 */
    @Nullable
    public static ResourceLocation getShortcut(ItemStack pdaStack, int shortcutIndex);

    /** 读当前格位（`selected`）绑定的配方 id；栈不是 PDA、或该格位未绑定时返回 null。 */
    @Nullable
    public static ResourceLocation selectedRecipe(ItemStack pdaStack);

    /**
     * 玩家主手优先、副手兜底地找出持有的 PDA 在哪只手。
     *
     * @return 两只手都没有 PDA 时返回 null
     */
    @Nullable
    public static InteractionHand heldPdaHand(Player player);
}
```

`PdaHelper` 无内部状态，所有方法可在主线程调用；`getData` 与 `selectedRecipe` 只读，`setData` 是唯一的写入入口（内部先 `sanitized()`）。`recipeOf` / `partRecipeOf` 一律走 `MMDynamicRes` 的本侧索引，不查 `RecipeManager`。

### 4.2 合并规则（`mergeEntry` 的契约）

`mergeEntry(recipeId, incomingUses)` 是"把一张刚解析出的蓝图并入数据"的纯函数：

| 前置状态：`usesOf(recipeId)` | 传入 `incomingUses` | 结果 |
| --- | --- | --- |
| `null`（未收纳） | 任意 | 新增 `recipeId → incomingUses` |
| `INFINITE_USES` | 任意 | 原样返回（本次并入被拒绝） |
| 正数 | 正数 | 写入两者之和 |
| 正数 | `INFINITE_USES` | 写入 `INFINITE_USES` |

调用方需要知道本次是否被拒绝时，用 `usesOf(recipeId)` 与 `mergeEntry` 前后的对象比对判断，或用 4.3 的存入服务（它直接返回被拒绝的张数）。

### 4.3 `PdaDepositService`

```java
public final class PdaDepositService {
    /**
     * 把玩家背包中的蓝图存入 PDA。主线程调用。
     *
     * @param player 服务端玩家
     * @param hand   手持 PDA 的手
     * @param slots  目标背包槽位；all 为 true 时本参数被忽略
     * @param all    true 表示扫描整个背包（主背包 36 格 + 副手）
     * @return 本次存入与被拒绝的张数
     */
    public static DepositResult deposit(ServerPlayer player, InteractionHand hand,
                                        @Nullable List<Integer> slots, boolean all);

    /**
     * 绑定或解绑格位。主线程调用。
     *
     * @param recipeId 为 null 表示解绑该格位
     * @return PDA 不在指定手、格位越界、或 recipeId 在 entries 中无对应条目时返回 false
     */
    public static boolean bindShortcut(ServerPlayer player, InteractionHand hand,
                                       int shortcutIndex, @Nullable ResourceLocation recipeId);

    /**
     * 写入当前格位。主线程调用。
     *
     * @return PDA 不在指定手、或 shortcutIndex 不在 0..8 内时返回 false
     */
    public static boolean selectShortcut(ServerPlayer player, InteractionHand hand, int shortcutIndex);

    /**
     * 写入设计模式开关。主线程调用。
     *
     * @return PDA 不在指定手时返回 false
     */
    public static boolean setDesignMode(ServerPlayer player, InteractionHand hand, boolean on);

    public record DepositResult(int stored, int rejected) {}
}
```

共同契约：

- 四个方法都以 `hand` 定位 PDA 栈（`player.getItemInHand(hand)`），若该栈不是 `PdaItem` 则立即失败；
- 写入数据后必须把该栈写回并触发物品同步，否则客户端界面读到旧数据。推荐做法：定位到具体槽位后 `inventory.setItem(slot, stack)` 并调用 `inventoryMenu.broadcastChanges()`；
- `deposit` 对每个候选物品调用 `PdaHelper.recipeIdOf`，返回值非空者取其蓝图物品的次数数据（当前恒为 `INFINITE_USES`）调用 `PdaData.mergeEntry` 并入，被拒绝者留在背包；并入成功者从背包中移除原件；
- `selectShortcut` 与 `setDesignMode` 分别写 `withSelected` / `withDesignMode`。客户端在发包前已用同一个目标值写过本地组件（见 6.2），因此服务端回显的内容与客户端已持有的内容一致；
- 若 `stored == 0`，方法仍可返回，由调用方决定是否提示（提示文案见 2.4）。

### 4.4 装配资格接入

在 `BlueprintAttachment.rebuildAvailableRecipes(Player)` 遍历背包的循环内追加对 PDA 的扫描，方法签名与既有签名保持不变：

```java
private void rebuildAvailableRecipes(Player player);
```

追加的判定逻辑：

```text
对背包中的每个 PdaItem 栈：
    对 PdaData.entries 的每个键（配方 id）：
        RecipeHolder<PartFabricatingRecipe> holder = PdaHelper.partRecipeOf(player.level(), recipeId);
        若 holder 非 null 且 holder.value().getPartType() 非 null：
            availableRecipes.put(holder.value().getPartType(), holder);   // 键是零件 id，不是配方 id
```

通用配方与查不到配方（失效）的条目不进入索引。索引失效沿用既有机制：`hashInventory` 基于 `ItemStack.hashItemAndComponents`，PDA 数据组件内容变化会改变背包哈希，从而触发重建。

## 5. 网络接口

### 5.1 `PdaDepositPayload`（C→S）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `hand` | `InteractionHand` | 手持 PDA 的手 |
| `slots` | `List<Integer>` | 目标背包槽位；`all` 为 true 时忽略 |
| `all` | `boolean` | 是否扫描整个背包 |

服务端契约：校验 `hand` 上的物品为 `PdaItem`；`slots` 中越界或非蓝图的槽位被跳过（不报错、不断开连接）；其余行为见 `PdaDepositService.deposit`。处理完毕后按 4.3 的同步要求刷新客户端。

### 5.2 `PdaBindShortcutPayload`（C→S）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `hand` | `InteractionHand` | 手持 PDA 的手 |
| `shortcutIndex` | `int` | 格位序号，合法域 `0..8` |
| `recipe` | `Optional<ResourceLocation>` | 待绑定的配方 id；`empty` 表示解绑 |

服务端契约：校验 `hand` 物品为 `PdaItem`、`shortcutIndex` 在 `0..8` 内、`recipe` 存在时必须在 `entries` 中有对应条目；任一不满足则忽略本次请求。通过后调用 `PdaDepositService.bindShortcut`。

### 5.3 `PdaSelectShortcutPayload`（C→S）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `hand` | `InteractionHand` | 手持 PDA 的手 |
| `shortcutIndex` | `int` | 新的当前格位序号，合法域 `0..8` |

服务端契约：校验 `hand` 上的物品为 `PdaItem`、`shortcutIndex` 在 `0..8` 内；任一不满足则忽略本次请求（不报错、不断开连接）。通过后调用 `PdaDepositService.selectShortcut`。

### 5.4 `PdaSetDesignModePayload`（C→S）

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `hand` | `InteractionHand` | 手持 PDA 的手 |
| `on` | `boolean` | 目标状态 |

服务端契约：校验 `hand` 上的物品为 `PdaItem`；不满足则忽略本次请求。通过后调用 `PdaDepositService.setDesignMode(player, hand, on)`。

载荷携带**目标状态**而不是"翻转"指令：重复发送同一个值的结果是幂等的，因此丢包或重发都不会把开关翻回来。客户端在发包前已用同一个目标值写过本地组件（见 6.2）。

### 5.5 放置请求

`PartAssemblyRequestPayload` 不新增字段。服务端 `VehicleAssemblyServerHelper.handle` 中"手持物品解析出的 `PartType` 与请求 `registryKey` 一致"这一步改为经 `PartAssemblyItem.partTypeOf(手持栈, level)` 取得（见 2.5）；手持 PDA 时该调用由 `PdaItem.getPartType` 回答（见 3.3），因此这条路径不需要 PDA 专属分支，也不需要请求携带格位序号。

## 6. 客户端接口

### 6.1 `PdaScreen`

```java
@OnlyIn(Dist.CLIENT)
public class PdaScreen extends ApricityScreen {
    private static final String AUI_DOC_PATH = "machine_max/pda/pda_ui.html";

    public PdaScreen(InteractionHand hand);
}
```

契约：

- 构造时传入打开该界面所用的手，界面内所有发包都带上它；
- `init()` 中取 `getLinkedDocument()` 缓存文档引用；`tick()` 中比对数据签名（`PdaData` 内容 + 背包蓝图集合）决定是否重建列表 DOM；
- 所有按钮与条目监听通过 `Element.addEventListener` 挂在 Java 侧；不存在页面脚本，页面只提供结构与样式；
- 界面不持任何权威状态，只做展示与发包。

### 6.2 设计模式与格位的读写

设计模式开关（`designMode`）与当前格位序号（`selected`）都是 `PDA_DATA` 的字段，随物品走，两端读同一份数据。客户端是它们的**预写方**：本机操作时先把目标值写进本地 PDA 栈的组件，再发对应的载荷（5.3 / 5.4），由服务端把权威副本改成同一个值。这样输入反馈不必等待往返，而服务端回显的内容与本地已持有的内容一致。

写入口共三类，都遵循"先本地写、后发包"：

| 入口 | 写入 | 载荷 |
| --- | --- | --- |
| `PdaItem.use` 的 Shift 分支 | `designMode` 取反 | `PdaSetDesignModePayload` |
| `PdaInputInterceptor` 的滚轮与数字键 | `selected` 加一 / 减一（越界环绕）或指定下标 | `PdaSelectShortcutPayload` |
| `PdaScreen` 的格位交互 | 只改 `shortcuts`（绑定 / 解绑），不改 `selected` | `PdaBindShortcutPayload` |

本地写入的代码形状（`heldStack` 由 `PdaHelper.heldPdaHand(player)` 定位）：

```java
PdaData data = PdaHelper.getData(heldStack);
PdaHelper.setData(heldStack, data.withSelected(nextIndex));   // withSelected 内部按 0..8 归一
```

约束：

- **两端共用同一段构造逻辑**：`PdaData.withSelected(int)` 与 `PdaData.withDesignMode(boolean)` 是唯一的构造入口，客户端与服务端都只调它们，保证写入值恒等；
- **只有 `selected` 与 `designMode` 预写**：`entries` 与 `shortcuts` 的合并与校验规则（4.2、4.3）只由服务端执行，客户端等服务端回显；
- 读取一律经 `PdaHelper.getData`，不缓存字段副本；`PdaHelper.selectedRecipe(heldStack)` 给出当前格位绑定的配方 id；
- "设计模式开启"与"手持 PDA"不会出现不一致：`designMode` 是 PDA 自己的字段，PDA 不在手时该状态不产生任何效果（`PdaItem.getPartType` 返回 `null`，见 3.3）。

### 6.3 放置请求的构造与发送

设计模式下的右键放置沿用既有装配请求链路，不需要任何 PDA 专属处理：

```java
// PdaItem.use 的 designMode == true 分支
ItemStack stack = player.getItemInHand(usedHand);
PartAssemblyRequestPayload request = VehicleAssemblyHelper.getInstance().buildRequest(player, usedHand, stack);
if (request != null) PacketDistributor.sendToServer(request);
```

- `buildRequest` 内部的"本地状态与手持物品是否一致"判定走 `PartAssemblyItem.partTypeOf(stack, level)`（见 2.5）；手持 PDA 时由 `PdaItem.getPartType` 回答，该判定天然成立；
- 请求不携带格位序号：服务端 `handle` 用同一个 `partTypeOf` 从自己的 PDA 副本解析（见 5.5），两端读到的是同一份 `selected`；
- 变体自动过滤、连接点循环、安装角计算等全部复用既有逻辑。

### 6.4 输入拦截

新增客户端输入拦截器，负责格位切换与原版热栏屏蔽：

```java
@OnlyIn(Dist.CLIENT)
public final class PdaInputInterceptor {
    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event);

    @SubscribeEvent
    public static void onKey(InputEvent.Key event);
}
```

前置条件（两者相同）：本地玩家手持 PDA 且该 PDA 的 `designMode == true`，且 `Minecraft.getInstance().screen == null`。

| 方法 | 行为 |
| --- | --- |
| `onMouseScroll` | 未按 Alt：按 `event.getScrollDeltaY()` 的正负切换格位——上滚（`> 0`）取前一格（减一）、下滚取后一格（加一），与原版热栏同向；越界环绕 0~8，写本地组件并发送 `PdaSelectShortcutPayload`。按住 Alt：不切格位，Alt+滚轮旋转安装角的行为保持不变。两种情形最后都 `event.setCanceled(true)` |
| `onKey` | 遍历 `options.keyHotbarSlots[0..8]`，对 `consumeClick()` 命中的下标执行 `setDown(false)`，把该下标设为当前格位（写本地组件并发送 `PdaSelectShortcutPayload`） |

共同约束：

- 前置条件不满足时立即返回，不得调用 `consumeClick()`、`setCanceled` 或任何会改变原版输入状态的 API；
- `onKey` 必须逐项 `consumeClick()`，不得直接比较 GLFW 键码，以尊重玩家的按键重绑定；
- 滚轮一律 `setCanceled(true)`（含按住 Alt 的情形），否则原版热栏切换会与格位切换同时发生；Alt+滚轮的安装角旋转由既有 `RawInputHandler` 处理，它不检查事件的取消状态，因此两者可以共存；
- 两个处理器只在客户端注册；除 `PdaSelectShortcutPayload` 外不发送其它网络包。

### 6.5 `PdaHotbarOverlay`

设计模式下的 HUD 快捷栏由 AUI 的 Overlay Document 承载，其生命周期由这个类独占管理：

```java
@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT)
public final class PdaHotbarOverlay {
    /** 按"手持 PDA 的 designMode"同步显隐；首次需要显示时创建文档。 */
    public static void refresh();

    /** 移除文档。幂等。 */
    public static void dispose();

    /** 当前文档；尚未创建时为 null。 */
    @Nullable
    public static Document document();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event);                      // 调 refresh()

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event);       // 调 dispose()
}
```

契约：

| 方法 | 行为 |
| --- | --- |
| `refresh` | 经 `PdaHelper.heldPdaHand(player)` 找到手持 PDA 并读其 `designMode`：为 true 时确保文档已创建、可见，并写入格位图标与选中态；为 false 或没有手持 PDA 时把文档置为不可见。文档一经创建即保留复用，不随模式切换反复销毁重建 |
| `dispose` | 调 `Document.remove()` 移除文档并清空内部引用；未创建时不做任何事 |
| `document` | 只读访问入口，供调试与断言使用 |

- 文档路径固定为 `machine_max/pda/pda_hotbar.html`；
- 显隐通过给文档根元素切换 `pda-hotbar-hidden` 类实现（样式表以 `display: none` 让叠层整体不参与布局与绘制），不依赖文档的创建与销毁；
- 该类的两个事件处理器覆盖了"每客户端 tick 刷新"与"退出世界时清理"两个时机；HUD 的逐帧绘制由 AUI 自身完成，原版快捷栏的取消由 `MMGuiManager` 负责，都不在本类内。

## 7. AUI 元素契约

### 7.1 HTML 元素 id

实施页 `pda_ui.html` 必须提供以下 id；原型页使用同一组 id，便于对照。

| id | 用途 | 写入方 |
| --- | --- | --- |
| `pda-title` | 标题文本 | 静态（`<translation>`） |
| `pda-count-stored` | 标题栏"已收纳 N 项"，Java 写入整段文案 | Java |
| `pda-count-infinite` | 标题栏"无限 N" | Java |
| `pda-count-limited` | 标题栏"有限 N" | Java |
| `pda-shortcut-bar` | 快捷栏容器，含 9 个格位 | Java 生成子节点 |
| `pda-shortcut-0` … `pda-shortcut-8` | 单个格位 | Java 生成 |
| `pda-entry-list` | 已收纳条目列表容器 | Java 写入 `setInnerHTML` |
| `pda-inventory-list` | 背包蓝图列表容器 | Java 写入 `setInnerHTML` |
| `pda-btn-deposit-all` | 一键存入全部按钮 | 静态 + Java 挂监听 |
| `pda-status` | 底部提示栏 | Java |

条目行内的可点击元素统一带 `data-act` 属性，取值域：

| `data-act` | 所在区域 | 含义 |
| --- | --- | --- |
| `SELECT_ENTRY` | 已收纳列表 | 选中条目进入待绑定态，行内需带 `data-target` |
| `DEPOSIT` | 背包蓝图列表 | 存入该行，行内需带 `data-slot` |
| `UNBIND` | 已收纳列表 | 清除所有引用该条目的格位 |

**不使用** `data-act` 之外的自定义属性名。`data-target` 取配方 id 的 `ResourceLocation.toString()`，它就是条目的唯一标识，也是绑定与解绑请求携带的值；行内的"零件 / 通用"标签、配色与"未知蓝图"降级态由 Java 生成行 HTML 时决定，不需要额外的 DOM 属性（原型页自行用 `data-kind` / `data-depositable` / `data-merge` / `data-index` 做脚手架，实施页不复用）。

### 7.2 CSS 契约

样式集中在 `pda_ui.css`，主题色走 CSS 变量，与项目其他 AUI 页面一致：

| 变量 | 用途 |
| --- | --- |
| `--accent` | 主题强调色，本页取青色系 |
| `--accent-dim` | 强调色的低饱和态，用于未选中边框 |
| `--panel-bg` | 区块底色 |
| `--row-hover` | 列表行悬停底色 |

书写约定沿用既有页面：不使用 `calc()`、不使用负 margin、不使用 `grid-template-areas`；`transition` 只写在 AUI 可动画白名单属性（颜色、透明度、宽高、`transform`、`box-shadow`）上。

### 7.3 HUD 文档契约（`pda_hotbar.html`）

`pda_hotbar.html` 与 `pda_ui.html` 是两份独立文档：前者由 `PdaHotbarOverlay` 创建的 Overlay Document 承载，呈现于世界中的 HUD 之上；后者由 `PdaScreen` 承载，呈现为全屏界面。

必需的 meta：

| meta | 取值 | 作用 |
| --- | --- | --- |
| `aui-viewport` | `mode=gui` | 与原版快捷栏的 GUI 坐标一致，保证格位与经验条对齐 |

**禁止**声明 `aui-mouse-events`：HUD 叠层不参与输入，滚轮与数字键由 `PdaInputInterceptor` 独占。

元素 id：

| id | 用途 | 写入方 |
| --- | --- | --- |
| `pda-hotbar` | 条体容器，含 9 个格位 | 静态 |
| `pda-hotbar-label` | 选中蓝图名 | Java |
| `pda-hotbar-slot-0` … `pda-hotbar-slot-8` | 单个格位，下标与格位序号一一对应 | Java |
| `pda-hotbar-banner` | 左上角常驻的「设计模式」横幅，逐行提示：设计模式 / 右键放置零件 / 潜行键 + 右键退出 | 静态，内容由 `<translation>` 元素取语言键，Java 不写入 |

格位元素的状态类：

| 类名 | 含义 |
| --- | --- |
| `selected` | 该格位是当前格位（`PDA_DATA.selected`） |
| `empty` | 该格位未绑定条目 |
| `blocked` | 该格位绑定的条目不是零件配方（通用配方，或配方已失效），序号改琥珀色，提示不可放置 |

格位内嵌一个 AUI 纹理元素（`<texture class="hs-icon">`，与研发界面同一套写法），其 `src` 由 Java 写为该条目图标的资源路径；变化时才写，避免每帧触碰 DOM。格位的选中态、空位与不可放置态分别用 `selected` / `empty` / `blocked` 类表达。样式集中在 `pda_hotbar.css`，沿用 7.2 的书写约定；该页强调色取研发/制造菜单的橙 `#FF6400`（7.2 的 `--accent` 是管理界面的青），格边长 20px、格间距 1px。

叠层显隐：`PdaHotbarOverlay` 给文档根元素（`html`）加 / 去 `pda-hotbar-hidden` 类，样式表以 `html.pda-hotbar-hidden { display: none; }` 让整个叠层不参与布局与绘制；文档一经创建即保留复用，模式切换只切类不销毁。

## 8. 兼容性与约束

| 约束 | 说明 |
| --- | --- |
| 类型判定顺序 | `PartFabricatingBlueprintItem` 是 `FabricatingBlueprintItem` 的子类、`PartFabricatingRecipe` 是 `FabricatingRecipe` 的子类，两处判定都必须先判子类 |
| 配方反查来源 | 条目类别与零件 id 一律经 `MMDynamicRes.getAllFabricating(Level)` 的本侧索引反查（`PdaHelper.recipeOf` / `partRecipeOf`），不查 `RecipeManager`；索引未就绪时反查返回 `null` |
| 失效条目 | 条目引用的配方反查不到时不删除条目，只降级为不可用；`PdaData.sanitized()` 无 `Level`，不做存在性校验 |
| 映射定序 | `PdaData.entries` 恒为 `ENTRY_ORDER`（配方 id 字符串序）有序映射；产出 `PdaData` 的所有路径（`CODEC` 解码、`mergeEntry`、`withoutEntry`、`withShortcut`、`withSelected`、`withDesignMode`）都必须经 `sanitized()` 收口，否则迭代顺序会静默退化 |
| 零件来源解析 | 外部一律经 `PartAssemblyItem.partTypeOf(ItemStack, Level)` 取零件类型（见 2.5），不硬编码 `PdaItem` 类型判断；`PdaItem` 自身覆写 `getPartType`（见 3.3） |
| 组件双写 | `PDA_DATA` 必须同时 `persistent` 与 `networkSynchronized` |
| 同步触发 | 服务端改写 `PDA_DATA` 后必须触发物品同步（`inventoryMenu.broadcastChanges()` 或等价手段），否则客户端界面停在旧数据 |
| 载荷注册 | 新增载荷必须挂到 `MMPayloadRegistry` 的 `pda:1.0.0` 组；`PdaSetDesignModePayload` 携带目标状态而不是"翻转"指令（见 5.4） |
| 输入拦截时序 | `PdaInputInterceptor.onKey` 依赖 `InputEvent.Key` 早于原版 `Minecraft.handleKeybinds()` 执行的顺序；消费热栏键只能发生在该处理器内 |
| 输入拦截与安装角 | 设计模式下的滚轮拦截不占用 Alt 组合：按住 Alt 时切格位不发生，安装角旋转沿用既有行为（见 6.4） |
| 设计模式状态 | `designMode` 与 `selected` 是 `PDA_DATA` 的字段（见 3.1），随物品持久化并同步；服务端按 5.3 / 5.4 的载荷写入，不维护任何独立副本 |
| 客户端预写 | 只有 `selected` 与 `designMode` 由客户端预写（见 6.2），两端都必须经 `PdaData.withSelected` / `withDesignMode` 构造目标值，禁止绕开它们直接写入；`entries` 与 `shortcuts` 禁止客户端预写 |
| HUD 叠层输入 | `pda_hotbar.html` 不得声明 `aui-mouse-events`；HUD 叠层只负责绘制，不消费任何输入事件 |
| 取消的图层 | 只取消 `VanillaGuiLayers.HOTBAR`；`RegisterGuiLayersEvent` 不支持覆盖同名图层，替代栏必须由 `PdaHotbarOverlay` 另行绘制 |
| 线程 | 本章所有服务端方法都在主线程调用；不涉及物理线程，不使用 `synchronized` |
| 不可逆 | 不提供任何从 `PdaData` 生成蓝图物品的接口；`PdaItem.getPartType` 只返回零件类型，不产出也不修改物品栈 |
| 无容器槽位 | 界面不使用 AUI 容器屏，不注册 `MenuType`，不新增 `Slot` |

## 9. 待定项

| 项 | 影响 | 待定原因 |
| --- | --- | --- |
| 蓝图物品的次数组件 | 决定存入时传给 `PdaData.mergeEntry` 的 `incomingUses` 取值来源 | 有限次蓝图尚未设计，当前恒取 `INFINITE_USES` |
| 替代栏的屏幕占位 | 决定是否要额外取消经验条 | 设计上按 20px 格、底部居中，与原版快捷栏同占位；实机对齐后若出现错位再评估 |

---

**修订记录**

| 日期 | 内容 |
| --- | --- |
| 2026-09-27 | 初稿。 |
| 2026-09-27 | 设计模式开关与格位序号改为 `VehicleAssemblyHelper` 的客户端实例字段，取消 `pda_client_state` 附件注册（原 2.3，后续小节顺次前移）。 |
| 2026-09-27 | 条目身份收敛为配方 id：删除 `PdaEntryKind` 与 `PdaEntryKey`，类别与零件 id 改由 `PdaHelper.recipeOf` / `partRecipeOf` 反查（原 3.1、3.3 删除，第 3 章小节顺次前移）。 |
| 2026-09-27 | 条目进一步收敛为映射项：`entries` 由 `List<PdaEntry>` 改为 `Map<ResourceLocation, Integer>`，`PdaEntry` 类型删除，`withEntry` → `mergeEntry`、`find` → `usesOf`（第 3 章由 3 节收缩为 2 节）。 |
| 2026-09-27 | `entries` 改为 `SortedMap`（定序器 `ENTRY_ORDER` = 配方 id 字符串序），迭代顺序成为数据模型的保证，渲染端不再排序；产出 `PdaData` 的所有路径以 `sanitized()` 收口。 |
| 2026-09-27 | `PartAssemblyItem.getPartType` 由静态方法改为可覆写的实例方法，并新增静态派发器 `partTypeOf`；`PdaItem` 覆写它提供当前格位的零件来源，取消 `PdaHelper.resolveEffectiveBlueprint` 与"等效蓝图临时栈"，`PartAssemblyRequestPayload` 不新增字段（新增 2.5、3.3、5.5）。 |
| 2026-09-27 | `designMode` 与 `selected` 改为 `PDA_DATA` 的字段：客户端预写、服务端权威写入；新增 `PdaSelectShortcutPayload` 与 `PdaSetDesignModePayload`，`PdaBindShortcutPayload` 语义收窄为只改绑定（3.1、5.3、5.4、6.2）。 |
| 2026-09-27 | `PdaHotbarOverlay` 改为注册 `ClientTickEvent.Post` 与 `ClientPlayerNetworkEvent.LoggingOut`，`refresh` 的显隐判据改为手持 PDA 的 `designMode`（6.5）。 |
| 2026-09-27 | HUD 文档契约新增 `pda-hotbar-banner`：左上角常驻的「设计模式」横幅，内容全为常量、Java 不写入（7.3）。 |
| 2026-09-27 | 横幅改为扁平多行样式（黑底 + 左侧橙竖线），并增列「右键 放置零件」；HUD 页的强调色取研发/制造菜单的橙 `#FF6400`（7.3）。 |
| 2026-09-27 | 语言键清单补充条目标签键（`tag.part` / `tag.general`）与标题栏计数键（`count.stored` / `count.infinite` / `count.limited`）（2.4）。 |
| 2026-09-27 | HUD 格位图标由 AUI 纹理元素承载（Java 只写 `src`），叠层显隐由根元素的 `pda-hotbar-hidden` 类控制；`pda-count-stored` 由 Java 写入整段文案（6.5、7.1、7.3）。 |
| 2026-09-27 | 语言键清单补充 HUD 横幅与键名文案键（`hud.title` / `key.right_click` / `key.sneak` / `action.place` / `action.exit`）；`pda-hotbar-banner` 的内容明确由 `<translation>` 取语言键（2.4、7.3）。 |
| 2026-09-27 | `PdaItem` 的类契约补 `inventoryTick`（客户端对齐放置预览与瞄准提示）（2.1）。 |
| 2026-09-27 | `PdaItem` 的类契约移除 `inventoryTick`：放置预览的姿态对齐与瞄准提示改由 `VehicleAssemblyHelper.onClientTick` 调用的 `updatePreview` 承担，`PartItem` 与 `PartFabricatingBlueprintItem` 的同名方法一并取消（2.1）。 |
| 2026-09-27 | `onMouseScroll` 的切格方向改为与原版热栏同向：上滚取前一格、下滚取后一格（5.2）。 |
