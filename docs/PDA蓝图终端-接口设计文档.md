# PDA 蓝图终端：接口设计文档

**文档状态**：设计中（未实施）
**适用版本**：NeoForge 1.21.1 · Machine-Max 1.0.3-beta.6
**编写日期**：2026-09-27

## 0. 本文自包含

阅读本文不需要预先阅读任何其他文档、代码或历史讨论。本文给出 **PDA 蓝图终端**（一个尚未实施的新功能）的全部对外契约：注册项、数据模型、方法签名、网络包字段、客户端接口、AUI 元素 id 与语言键。每一处契约都写明调用方、被调方、参数含义与失败返回值。

本文只写"接口长什么样、怎么调用"，不写设计动机与取舍理由；动机与完整背景见配套的《PDA蓝图终端-详细设计文档.md》。两份文档可以独立阅读：本文用到的每一个类型与概念都在第 1 章或本节内定义。

配套产物：

| 产物 | 位置 |
| --- | --- |
| 详细设计文档 | `docs/PDA蓝图终端-详细设计文档.md` |
| 静态原型 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_ui_prototype.html` + `pda_ui.css` |
| 实施页 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_ui.html` |
| HUD 原型 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_hotbar_prototype.html` + `pda_hotbar.css` |
| HUD 实施页 | `src/main/resources/assets/apricityui/apricity/machine_max/pda/pda_hotbar.html` |

## 1. 术语与类型速览

| 术语 | 定义 |
| --- | --- |
| 零件制造蓝图 | 物品类 `PartFabricatingBlueprintItem`，携带 `part_type` 与 `recipe_type` 两个数据组件，可放置为未组装零件 |
| 通用制造蓝图 | 物品类 `FabricatingBlueprintItem`，携带 `recipe_type` 数据组件，不能放置 |
| 蓝图条目 | PDA 内记录的一条已收纳蓝图，由"类别 + 定位"唯一标识 |
| 装配资格 | 玩家推进某零件装配的许可；持有该零件的零件制造蓝图即可获得 |
| 等效蓝图栈 | 由 PDA 条目即时重建的临时物品栈，只用于解析，不入任何物品栏 |
| 设计模式 | 手持 PDA 时的一种输入状态：右键按当前格位的蓝图放置零件 |
| 设计模式快捷栏 | PDA 内置的 9 格蓝图选择器，格位序号 0~8 |

以下命名空间简称在本文中通用：`MMItems` 指 `common.registry.MMItems`（Kotlin 注册表），`MMDataComponents` 指 `common.registry.MMDataComponents`，`MMAttachments` 指 `common.registry.MMAttachments`，`MMPayloadRegistry` 指 `network.MMPayloadRegistry`，`MMDynamicRes` 指 `external.MMDynamicRes`。

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
public class PdaItem extends Item {
    public PdaItem();

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand);
}
```

`use` 的契约：

| 端 | 条件 | 行为 | 返回值 |
| --- | --- | --- | --- |
| 客户端 | 未按 Shift | `Minecraft.getInstance().setScreen(new PdaScreen(usedHand))`，服务端不参与 | `success` |
| 客户端 | 按住 Shift | 以 `PdaClientState.toggled()` 翻转设计模式状态 | `success` |
| 服务端 | 任意 | 不做任何事 | `success` |

### 2.2 数据组件

在 `MMDataComponents` 新增：

```java
public static final DeferredHolder<DataComponentType<?>, DataComponentType<PdaData>> PDA_DATA;
// builder: persistent(PdaData.CODEC).networkSynchronized(PdaData.STREAM_CODEC).cacheEncoding()

public static DataComponentType<PdaData> getPDA_DATA();
```

约束：必须同时提供 `persistent` 与 `networkSynchronized`，缺任一项都会导致界面读不到最新数据或数据无法落盘。

### 2.3 附件

在 `MMAttachments` 新增（不提供序列化器，即不落盘）：

| 项 | 值 |
| --- | --- |
| 附件 id | `pda_client_state` |
| 类型 | `PdaClientState`（设计模式开关 + 当前格位序号） |
| 工厂 | `PdaClientState::inactive` |
| 用途 | 玩家级设计模式状态与当前格位，仅在客户端读写 |

```java
public record PdaClientState(boolean designMode, int shortcutIndex) {
    public static final int SHORTCUT_COUNT = 9;

    public static PdaClientState inactive();            // designMode=false, shortcutIndex=0
    public PdaClientState toggled();                    // 翻转 designMode
    public PdaClientState withShortcut(int index);      // index 归一化到 0~8
}

public static AttachmentType<PdaClientState> getPDA_CLIENT_STATE();
```

### 2.4 网络载荷

在 `MMPayloadRegistry` 新增载荷组 `pda:1.0.0`，注册两个 C→S 载荷：

| 载荷 | 方向 | 作用 |
| --- | --- | --- |
| `PdaDepositPayload` | C→S | 把背包中的蓝图存入 PDA |
| `PdaBindShortcutPayload` | C→S | 绑定或解绑设计模式快捷栏格位 |

同时扩展既有的装配请求载荷（见 5.3）。

### 2.5 语言键

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
| `message.machine_max.pda.deposit.success` | 已存入 %1$s 张蓝图 |
| `message.machine_max.pda.deposit.rejected` | %1$s 张蓝图已收纳，未重复存入 |
| `message.machine_max.pda.design_mode.enter` | 已进入设计模式 |
| `message.machine_max.pda.design_mode.exit` | 已退出设计模式 |

沿用既有物品名的键：`item.machine_max.part_fabricating_blueprint`（零件制造蓝图）、`item.machine_max.fabricating_blueprint`（制造蓝图）。

## 3. 数据模型接口

### 3.1 枚举 `PdaEntryKind`

```java
public enum PdaEntryKind {
    PART,       // 来自零件制造蓝图；target 是零件类型注册名
    GENERAL;    // 来自通用制造蓝图；target 是配方 id

    public static final Codec<PdaEntryKind> CODEC;         // 字符串枚举名
    public static final StreamCodec<ByteBuf, PdaEntryKind> STREAM_CODEC;
}
```

### 3.2 记录 `PdaEntry`

```java
public record PdaEntry(PdaEntryKind kind, ResourceLocation target, int remainingUses) {
    public static final Codec<PdaEntry> CODEC;
    public static final StreamCodec<ByteBuf, PdaEntry> STREAM_CODEC;

    public static PdaEntry unlimited(PdaEntryKind kind, ResourceLocation target);  // remainingUses = -1
    public boolean isInfinite();                                                   // remainingUses < 0
    public PdaEntryKey key();
}
```

字段契约：

| 字段 | 含义 | 约束 |
| --- | --- | --- |
| `kind` | 条目类别 | 非空 |
| `target` | `PART` 时是零件类型注册名；`GENERAL` 时是配方 id | 非空 |
| `remainingUses` | 残留可使用次数 | `-1` 表示无限；其它负值非法，读取时按 `-1` 处理 |

JSON 字段名：`kind`、`target`、`remaining_uses`（缺省 `-1`）。

### 3.3 记录 `PdaEntryKey`

```java
public record PdaEntryKey(PdaEntryKind kind, ResourceLocation target) {
    public static final Codec<PdaEntryKey> CODEC;
    public static final StreamCodec<ByteBuf, PdaEntryKey> STREAM_CODEC;
}
```

`PdaEntryKey` 是条目的唯一标识，`equals` / `hashCode` 由 record 自动提供，可直接用作 Map 键。

### 3.4 记录 `PdaData`

```java
public record PdaData(List<PdaEntry> entries, Map<Integer, PdaEntryKey> shortcuts) {
    public static final int SHORTCUT_COUNT = 9;
    public static final PdaData EMPTY;

    public static final Codec<PdaData> CODEC;                        // entries + shortcuts
    public static final StreamCodec<ByteBuf, PdaData> STREAM_CODEC;  // 由 CODEC 派生

    @Nullable public PdaEntryKey shortcutAt(int index);
    @Nullable public PdaEntry find(PdaEntryKey key);
    public PdaData withShortcut(int index, @Nullable PdaEntryKey key);   // key 为 null 即解绑
    public PdaData withEntry(PdaEntry entry);                             // 按 4.2 规则合并或新增
    public PdaData withoutEntry(PdaEntryKey key);                          // 同时清除引用它的格位
    public PdaData sanitized();                                            // 见 3.5
}
```

字段契约：

| 字段 | 含义 | 约束 |
| --- | --- | --- |
| `entries` | 已收纳条目 | 不存在重复 `(kind, target)` |
| `shortcuts` | 格位 → 条目键 | 键域为 `0..8`；每个值必须在 `entries` 中有对应条目 |

JSON 形态：`entries` 为数组；`shortcuts` 为对象，键是格位序号的十进制字符串（`"0"`~`"8"`），值是 `PdaEntryKey`。

### 3.5 不变量与 `sanitized()`

`sanitized()` 按顺序执行，返回一个满足全部不变量的新实例，用于所有反序列化入口（读组件、收报文）：

1. 丢弃 `target` 为空或 `kind` 为空的条目；
2. `remainingUses < -1` 归一为 `-1`；
3. 对 `entries` 按 `key()` 去重，保留首次出现者，其余丢弃，丢弃项按累加规则并入首次出现者（见 4.2）；
4. 丢弃 `shortcuts` 中键不在 `0..8` 的项；
5. 丢弃 `shortcuts` 中值在 `entries` 里找不到对应条目的项。

写入组件的所有路径都必须先经过 `sanitized()`；读出时可以再次调用以保证防御性。

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
     * 把一张蓝图物品解析为条目。
     * 判定顺序：先 PartFabricatingBlueprintItem（子类），后 FabricatingBlueprintItem。
     * 零件制造蓝图取 part_type 组件作 target，通用制造蓝图取 recipe_type 组件作 target。
     *
     * @return 无法解析（非蓝图物品、或所需组件缺失）时返回 null
     */
    @Nullable
    public static PdaEntry entryOf(ItemStack stack, Level level);

    /** 读某一格位绑定的条目键；格位越界或未绑定时返回 null。 */
    @Nullable
    public static PdaEntryKey getShortcut(ItemStack pdaStack, int shortcutIndex);

    /**
     * 按格位重建等效蓝图临时栈，供装配链路解析。
     * PART  → PartFabricatingBlueprintItem，写 PART_TYPE = target 与对应 RECIPE_TYPE
     * GENERAL → FabricatingBlueprintItem，写 RECIPE_TYPE = target
     *
     * @return 格位越界、未绑定、条目类别为 GENERAL 时返回 null
     */
    @Nullable
    public static ItemStack resolveEffectiveBlueprint(ItemStack pdaStack, Level level, int shortcutIndex);

    /** 取当前手持的 PDA 栈；不是 PDA 时返回 ItemStack.EMPTY。 */
    public static ItemStack heldPda(Player player, InteractionHand hand);
}
```

`PdaHelper` 无内部状态，所有方法可在主线程调用；`resolveEffectiveBlueprint` 只在解析期被调用，不修改任何物品栈。

### 4.2 合并规则（`withEntry` 的契约）

`withEntry(entry)` 是"把一张刚解析出的蓝图并入数据"的纯函数：

| 前置状态 | 传入 `entry` | `withEntry` 结果 |
| --- | --- | --- |
| 无该键 | 任意 | 追加 `entry` |
| 有，`remainingUses == -1` | 任意 | 原样返回（本次并入被拒绝） |
| 有，`remainingUses > 0` | `remainingUses > 0` | 结果为两者之和 |
| 有，`remainingUses > 0` | `remainingUses == -1` | 结果为 `-1` |

调用方需要知道本次是否被拒绝时，用 `find(key) == null` 与 `withEntry` 前后的对象比对判断，或用 4.3 的存入服务（它直接返回被拒绝的张数）。

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
     * @param key 为 null 表示解绑该格位
     * @return PDA 不在指定手、格位越界、或 key 在 entries 中无对应条目时返回 false
     */
    public static boolean bindShortcut(ServerPlayer player, InteractionHand hand,
                                       int shortcutIndex, @Nullable PdaEntryKey key);

    public record DepositResult(int stored, int rejected) {}
}
```

共同契约：

- 两个方法都以 `hand` 定位 PDA 栈（`player.getItemInHand(hand)`），若该栈不是 `PdaItem` 则立即失败；
- 写入数据后必须把该栈写回并触发物品同步，否则客户端界面读到旧数据。推荐做法：定位到具体槽位后 `inventory.setItem(slot, stack)` 并调用 `inventoryMenu.broadcastChanges()`；
- `deposit` 对每个候选物品调用 `PdaHelper.entryOf`，返回值非空者按 4.2 规则并入，被拒绝者留在背包；并入成功者从背包中移除原件；
- 若 `stored == 0`，方法仍可返回，由调用方决定是否提示（提示文案见 2.5）。

### 4.4 装配资格接入

在 `BlueprintAttachment.rebuildAvailableRecipes(Player)` 遍历背包的循环内追加对 PDA 的扫描，方法签名与既有签名保持不变：

```java
private void rebuildAvailableRecipes(Player player);
```

追加的判定逻辑：

```text
对背包中的每个 PdaItem 栈：
    对 PdaData.entries 中每个 kind == PART 的条目：
        RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(player.level(), entry.target());
        若 holder 非 null，则 availableRecipes.put(entry.target(), holder);
```

`GENERAL` 条目不入索引。索引失效沿用既有机制：`hashInventory` 基于 `ItemStack.hashItemAndComponents`，PDA 数据组件内容变化会改变背包哈希，从而触发重建。

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
| `key` | `Optional<PdaEntryKey>` | 待绑定条目键；`empty` 表示解绑 |

服务端契约：校验 `hand` 物品为 `PdaItem`、`shortcutIndex` 在 `0..8` 内、`key` 存在时必须在 `entries` 中有对应条目；任一不满足则忽略本次请求。通过后调用 `PdaDepositService.bindShortcut`。

### 5.3 `PartAssemblyRequestPayload` 扩展

在既有字段之外新增：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `pdaShortcut` | `int` | 本次放置取自 PDA 的第几格；`-1` 表示不是 PDA 来源 |

服务端契约（`VehicleAssemblyServerHelper.handle` 中"手持物品解析出的 PartType 与请求一致"这一步）：

```text
若手持物品是 PdaItem：
    当 pdaShortcut < 0 时中止；
    ItemStack effective = PdaHelper.resolveEffectiveBlueprint(手持栈, level, pdaShortcut);
    当 effective 为 null 时中止；
    以 effective 的 PartType 作为"手持解析结果"参与既有校验；
否则：
    保持既有行为（以手持物品解析的 PartType 参与校验）。
```

中止的处理方式与既有校验失败一致，不新增失败分支的对外行为。

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

设计模式开关与当前格位都通过附件读写，客户端唯一入口：

```java
// 客户端
LocalPlayer player = Minecraft.getInstance().player;
PdaClientState state = player.getData(MMAttachments.getPDA_CLIENT_STATE());
player.setData(MMAttachments.getPDA_CLIENT_STATE(), state.toggled());            // 进出设计模式
player.setData(MMAttachments.getPDA_CLIENT_STATE(), state.withShortcut(index));  // 切换格位
```

服务端不读取该附件。

### 6.3 `VehicleAssemblyHelper` 接入点

在 `onClientTick` 解析手持装配物品的位置，插入 PDA 分支：

```java
@Nullable
private static ItemStack resolveHeldAssemblyStack(LocalPlayer player);   // 新增的私有辅助方法
```

该方法的行为：

```text
主手物品是 PartAssemblyItem            → 返回主手栈
副手物品是 PartAssemblyItem            → 返回副手栈
主手或副手是 PdaItem 且设计模式开启    → 返回 PdaHelper.resolveEffectiveBlueprint(PDA栈, level, 当前格位序号)
其它                                   → 返回 null
```

返回的栈随后按既有方式参与 `PartAssemblyItem.getPartType` 解析；变体自动过滤、连接点循环、安装角计算等全部复用既有逻辑，不新增加分支。

### 6.4 `PdaItem.use` 的发包路径

设计模式下右键的发包沿用既有装配请求链路：客户端由 `VehicleAssemblyHelper` 构造请求后发送 `PartAssemblyRequestPayload`，并在该请求中把 `pdaShortcut` 填为当前格位序号（`PdaClientState.shortcutIndex()`）。

### 6.5 输入拦截

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

契约：

| 方法 | 前置条件 | 行为 |
| --- | --- | --- |
| `onMouseScroll` | 本地玩家处于设计模式、手持 PDA，且 `Minecraft.getInstance().screen == null` | 按 `event.getScrollDeltaY()` 的正负把格位 +1 / -1（越界环绕 0~8），然后 `event.setCanceled(true)` |
| `onKey` | 同上 | 遍历 `options.keyHotbarSlots[0..8]`，对 `consumeClick()` 命中的下标执行 `setDown(false)`，并把该下标设为当前格位 |

共同约束：

- 前置条件不满足时立即返回，不得调用 `consumeClick()`、`setCanceled` 或任何会改变原版输入状态的 API；
- `onKey` 必须逐项 `consumeClick()`，不得直接比较 GLFW 键码，以尊重玩家的按键重绑定；
- 两个处理器只在客户端注册，不发送网络包。

### 6.6 `PdaHotbarOverlay`

设计模式下的 HUD 快捷栏由 AUI 的 Overlay Document 承载，其生命周期由这个类独占管理：

```java
@OnlyIn(Dist.CLIENT)
public final class PdaHotbarOverlay {
    /** 按当前 PdaClientState 同步显隐；首次需要显示时创建文档。每客户端 tick 调用。 */
    public static void refresh();

    /** 移除文档；回到主菜单或断开连接时调用。幂等。 */
    public static void dispose();

    /** 当前文档；尚未创建时为 null。 */
    @Nullable
    public static Document document();
}
```

契约：

| 方法 | 行为 |
| --- | --- |
| `refresh` | 读本地玩家的 `PdaClientState`：`designMode == true` 时确保文档已创建并可见；`false` 时把文档置为不可见。文档一经创建即保留复用，不随模式切换反复销毁重建 |
| `dispose` | 调 `Document.remove()` 移除文档并清空内部引用；未创建时不做任何事 |
| `document` | 只读访问入口，供调试与断言使用 |

- 文档路径固定为 `machine_max/pda/pda_hotbar.html`；
- 显隐通过给文档根元素切换 CSS 类实现，不依赖文档的创建与销毁；
- 该类不注册任何 NeoForge 事件：HUD 的逐帧绘制由 AUI 自身完成；原版快捷栏的取消由 `MMGuiManager` 负责，不在本类内。

## 7. AUI 元素契约

### 7.1 HTML 元素 id

实施页 `pda_ui.html` 必须提供以下 id；原型页使用同一组 id，便于对照。

| id | 用途 | 写入方 |
| --- | --- | --- |
| `pda-title` | 标题文本 | 静态（`<translation>`） |
| `pda-count-stored` | 标题栏"已收纳 N 项" | Java |
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
| `SELECT_ENTRY` | 已收纳列表 | 选中条目进入待绑定态，行内需带 `data-kind` 与 `data-target` |
| `DEPOSIT` | 背包蓝图列表 | 存入该行，行内需带 `data-slot` |
| `UNBIND` | 已收纳列表 | 清除所有引用该条目的格位 |

**不使用** `data-act` 之外的自定义属性名；`data-kind` 取 `PART` / `GENERAL`，`data-target` 取 `ResourceLocation.toString()`。

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

格位元素的状态类：

| 类名 | 含义 |
| --- | --- |
| `selected` | 该格位是当前格位（`PdaClientState.shortcutIndex()`） |
| `empty` | 该格位未绑定条目 |
| `blocked` | 该格位绑定的是 `GENERAL` 条目，序号改琥珀色，提示不可放置 |

格位元素的 `background-image` 由 Java 写为该条目图标；变化时才写，避免每帧触碰 DOM。样式集中在 `pda_hotbar.css`，沿用 7.2 的 CSS 变量与书写约定，格边长 20px、格间距 1px。

## 8. 兼容性与约束

| 约束 | 说明 |
| --- | --- |
| 类型判定顺序 | `PartFabricatingBlueprintItem` 是 `FabricatingBlueprintItem` 的子类，解析蓝图时必须先判子类 |
| 组件双写 | `PDA_DATA` 必须同时 `persistent` 与 `networkSynchronized` |
| 同步触发 | 服务端改写 `PDA_DATA` 后必须触发物品同步（`inventoryMenu.broadcastChanges()` 或等价手段），否则客户端界面停在旧数据 |
| 载荷注册 | 新增载荷必须挂到 `MMPayloadRegistry` 的 `pda:1.0.0` 组；`pdaShortcut` 字段的默认值 `-1` 必须与"非 PDA 来源"语义一致 |
| 输入拦截时序 | `PdaInputInterceptor.onKey` 依赖 `InputEvent.Key` 早于原版 `Minecraft.handleKeybinds()` 执行的顺序；消费热栏键只能发生在该处理器内 |
| HUD 叠层输入 | `pda_hotbar.html` 不得声明 `aui-mouse-events`；HUD 叠层只负责绘制，不消费任何输入事件 |
| 取消的图层 | 只取消 `VanillaGuiLayers.HOTBAR`；`RegisterGuiLayersEvent` 不支持覆盖同名图层，替代栏必须由 `PdaHotbarOverlay` 另行绘制 |
| 线程 | 本章所有服务端方法都在主线程调用；不涉及物理线程，不使用 `synchronized` |
| 不可逆 | 不提供任何从 `PdaData` 生成蓝图物品的接口；`resolveEffectiveBlueprint` 的返回值只允许在解析期使用，禁止写入任何容器 |
| 无容器槽位 | 界面不使用 AUI 容器屏，不注册 `MenuType`，不新增 `Slot` |

## 9. 待定项

| 项 | 影响 | 待定原因 |
| --- | --- | --- |
| 蓝图物品的次数组件 | 决定 `PdaHelper.entryOf` 中 `remainingUses` 的取值来源 | 有限次蓝图尚未设计，当前恒取 `-1` |
| `resolveEffectiveBlueprint` 写入的 `RECIPE_TYPE` 取值 | 只影响蓝图的显示名与图标解析 | 需确认是否总要写入零件配方 id，或可省略 |
| 替代栏的屏幕占位 | 决定是否要额外取消经验条 | 设计上按 20px 格、底部居中，与原版快捷栏同占位；实机对齐后若出现错位再评估 |

---

**修订记录**

| 日期 | 内容 |
| --- | --- |
| 2026-09-27 | 初稿。 |
