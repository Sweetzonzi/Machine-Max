# 内容包诊断：Machine-Max 适配实现（Spark-Core v1.4）

> **状态**：已实现。`MMDynamicRes` 只持有内容包注册表、配方/研发索引与消息工具；14 个内容包模块不写 `catch`，JSON 解码统一走 `PackCodec`（`tooltips` 直接读文本，`colors` 只解析不 decode）；配方与研发校验经 `SparkPackIssues` 上报；客户端摘要与服务端汇总由 Spark-Core 输出。
>
> **上游**：Spark-Core 提交 `e526385d`，设计文档 `docs/pack_diagnostics_plan.md` v1.4。P0–P3 覆盖四值加载上下文、问题收集器、`PackCodec` / `PackContentException`、服务端按模块汇总、客户端登录摘要与三个加载阶段事件；消费方迁移（P4）与 `/spark package issues` 命令不在上游范围内。
>
> **依赖形态**：`gradle.properties` 的 `spark_core_version=1.0.1034`；`settings.gradle` 在 `../Spark-Core` 存在时用复合构建替代该坐标。本文签名按本地 Spark-Core 源码核对。
>
> **范围**：Machine-Max 侧的接入点、时序边界与验收方式；上游内部实现以上游文档为准。

## 1. 结论

MM 的内容包加载链路只做解析、注册、上报三件事：

- `MMDynamicRes` 持有注册表、索引与消息工具 `text(key, fallback, args…)`；读取错误不在 MM 侧留存。
- 14 个内容包模块不写 `catch`，JSON 解码走 `PackCodec`（`tooltips` 直接读文本，`colors` 只解析不 decode）；路径类问题显式 `SparkPackIssues.error`。
- 领域层构造期检查抛 `PackContentException`，消息是带英文 fallback 的 `Component`。
- 配方、研发这类不属于包文件的校验经 `SparkPackIssues.report` 进入同一张诊断表（模块 `recipe` / `research`）。
- 客户端摘要、服务端汇总、同侧会话去重由 Spark-Core 负责；MM 不监听玩家加入或服务器启动来转发错误。
- 所有给作者看的消息用 `Component.translatableWithFallback`：专用服务器不加载语言表，裸 `translatable` 在日志里只剩键名。

## 2. 上游机制（接入面）

### 2.1 数据模型

| 类型 | 形态 |
|------|------|
| `PackSide` | `CLIENT` / `SERVER` |
| `PackOrigin` | `LOCAL` / `REMOTE` |
| `PackLoadContext` | `CLIENT_LOCAL` / `CLIENT_REMOTE` / `SERVER_LOCAL` / `SERVER_REMOTE`（预留，当前无调用）；`isClientSide`；`fromServer`（`CLIENT_LOCAL` 为 false，其余 true） |
| `PackSeverity` | `WARN` / `ERROR` |
| `PackIssue` | `moduleId`、`packId`（非包来源为 `null`）、`location`、`severity`、`message: Component`、`cause`、`context` |
| `PackModuleIssues` / `PackIssueSummary` | 按模块分组；`errors` / `warnings` / `dropped` / `isEmpty` |
| `PackFrame` | `(moduleId, packId, location, context)`；`issue(severity, message, cause = null)` |
| `PackContentException` | `(Component userMessage, Throwable? cause = null)`；`writableStackTrace = false`，内容错误不输出堆栈 |
| `PackCodec` | `parse(ByteArray) → JsonElement`；`read(Codec<T>, ByteArray)`；`read(Codec<T>, JsonElement)`；语法错误与 DFU 解码错误都转成 `PackContentException` |
| `DfuText` | 把 DFU 的 `No key x in {…}` 压成 `Missing required field x`，其余消息截断到 300 字符 |
| `RuntimeIssueLog` | 不属于任何帧的运行时出口，只写日志 |

所有类型位于 `cn.solarmoon.spark_core.pack.diagnostic`；客户端通知器在 `.client` 子包，加载阶段事件在 `.event` 子包。

### 2.2 会话、帧与上限

- 一次加载 = 一个 `PackLoadContext`。`SparkPackLoader.beginSession(context)` 只清该上下文的诊断状态，不碰 `graph` / `modules`；`CLIENT_LOCAL` 与 `CLIENT_REMOTE` 互不影响。MM 不调用 `beginSession`。
- 帧由加载器压栈：模块级帧的 `location` 是 `"*"`、`packId` 为 `null`；文件级帧的 `location` 是 `"<namespace>:<moduleId>/<path>"`。模块不需要自己拼接位置串。
- 每个上下文最多 200 条记录，超出只累加 `dropped`；ERROR 堆栈每个上下文最多打印 50 条，`PackContentException` 只打一行。
- 记录按 `moduleId → packId → location → severity` 排序；不做去重，同一失败只上报一次是调用方契约。

### 2.3 加载器契约

- `onStart` 抛异常 → 记一条模块级 ERROR，本上下文内跳过该模块的 `read` 与 `onFinish`。
- `read` 抛 `PackContentException` → 记一条 ERROR，消息取 `userMessage`，不输出堆栈；抛其他异常 → 记一条 ERROR 并保留堆栈。两种都只影响当前文件。
- 依赖排序失败 → 记一条 `moduleId = "graph"`、`location = "-"` 的 ERROR，本次所有模块都不执行 `onFinish`。
- 加载阶段事件（`NeoForge.EVENT_BUS`，只通知）：
  - `PackModuleStartEvent(context, moduleId)`：`onStart` 之前；
  - `PackModuleFinishEvent(context, moduleId, success)`：每个模块结束后，`onStart` 失败也发一次 `success = false`；
  - `PackAllFinishedEvent(context)`：本上下文所有模块 `onFinish` 之后、服务端汇总与客户端摘要之前；订阅者在这里 `report` 的记录会进本次汇总。
- 日志行格式固定为 `[模块] 位置 @ 包 → 消息`，非包来源的包位显示 `-`。
- 模块按注册顺序执行；同一模块内的文件按包依赖顺序执行。

### 2.4 展示

- 客户端：`PackIssueNotifier` 由 `SparkClientEventRegister` 注册。订阅 `PackAllFinishedEvent`（`CLIENT` 侧）并兜底监听 `ClientPlayerNetworkEvent.LoggingIn`；`LoggingOut` 清空 `CLIENT_LOCAL` / `CLIENT_REMOTE` 并重置提示状态。同一侧会话只提示一次。
- 配置项 `packIssueNotification` 在 Spark 的 common config 里：`OFF` / `SUMMARY`（默认，一条摘要） / `DETAIL`（按模块列出，总计最多 10 行、每模块最多 5 行）。日志始终保留完整记录。
- 服务端：`injectPackageContent(context)` 结束时 `logSummary(context)` 打一条 INFO，按模块分组；没有问题也打一条 `Content pack read finished: no issues.`。
- V1 没有 `/spark package issues` 命令，也没有 GUI；作者通过日志行与汇总定位。

### 2.5 MM 依赖的边界

- **汇总边界**：汇总/摘要只保证包含本次 `readPackageContent` / `injectPackageContent` 期间记录的问题。MM 的配方与研发校验在自己的 reload listener 中、晚于 Spark 汇总执行，`report` 后仍会进当前上下文的 bucket 并各打一行日志，但不会出现在那一次的汇总行里（见 §4）。
- **运行时无帧**：`SparkPackIssues.warn` 在没有帧时退化为普通日志，不进表；`SparkPackIssues.error` 照常抛出 `PackContentException`，但没有文件归属。
- **Java 互操作**：`SparkPackIssues.report(moduleId, location, severity, message, cause, context)` 是 `@JvmStatic`，Java 可直接调用，`packId` 固定为 `null`；`SparkPackIssues.error` 在 Java 里返回 `Void` 且总是抛出，Java 侧写 `throw new PackContentException(...)`；`PackCodec.parse/read`、`DfuText.toComponent`、`RuntimeIssueLog.warn/error` 都是 `@JvmStatic`，`PackCodec.read` 的 `ops` 有 `@JvmOverloads`。
- **消息可读性**：上游统一使用 `Component.translatableWithFallback`，保证服务端日志不是裸键。MM 侧同样如此。

## 3. MM 侧实现

### 3.1 `MMDynamicRes`

持有三类状态：

- 内容包注册表：`MATERIALS`、`CONNECTORS`、`PART_TYPES` / `SERVER_PART_TYPES`、`PROJECTILE_TYPES` / `SERVER_PROJECTILE_TYPES`、`STATIC_SUBSYSTEM_ATTRS` / `SERVER_STATIC_SUBSYSTEM_ATTRS`、`TEMPLATES`、`BLUEPRINTS`、`ASSEMBLIES`、`TOOLTIPS`、`HUD_STYLES`、`HUD_LAYOUTS`、`CONTROL_GROUP_PRESETS`、`COLORS`；
- 制造与研发索引：`ALL_*` / `SERVER_*` 配方表、`SERVER_RESEARCH_BY_PART` / `SERVER_RESEARCH_BY_FABRICATING_RECIPE`、`GROUP_MEMBERS` / `NODE_DEPTH` / `UNREACHABLE_RESEARCH`；
- 消息工具：

```java
public static MutableComponent text(String key, String fallback, Object... args) {
    return Component.translatableWithFallback(key, fallback, args);
}
```

配方与研发的校验在 `rebuildFabricatingIndex` / `buildResearchGraph` 内执行，统一经一个私有出口进入诊断表：

```java
private static void reportIfServer(boolean serverSide, String moduleId, ResourceLocation location, Component message) {
    if (!serverSide) return;
    SparkPackIssues.report(moduleId, location.toString(), PackSeverity.ERROR, message, null, PackLoadContext.SERVER_LOCAL);
}
```

- `moduleId` 取 `"recipe"`（通用制造配方的目录与类型、零件配方的目录/零件存在性/产物数量/唯一性，共 5 处）或 `"research"`（自环、环路径、环下游、悬空前置，共 4 处）；
- `location` 取配方或研发 id，`packId` 由该出口固定为 `null`，`context` 为 `SERVER_LOCAL`；
- `serverSide = false` 的客户端重建（`ClientRecipeIndexHandler`）不产生记录。

`DataPackReloader` 以 `EventPriority.LOWEST` 注册到 `AddReloadListenerEvent`。`prepare` 只返回空集合；`apply` 在 `serverResources` 就绪时重建制造索引与研发索引。

### 3.2 内容包模块

14 个模块都在 `read` 里先判门控，再解码或注册；`onStart` 清本侧缓存，`onFinish` 打数量日志。

| 模块 | `read` 的形态 |
|------|----------------|
| `assemblies` / `blueprints` / `connectors` / `control_groups` / `materials` / `parts` / `projectiles` / `subsystems` / `templates` | `PackCodec.read(<类型>.CODEC, content)`，id 为 `ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".json"))`；`parts` / `projectiles` 额外写回 `registryKey` |
| `huds` / `hud_layouts` | 键由 `HudModule.contentKey` 拼成 `<命名空间>:<相对路径>`（子目录参与拼键）；键为 `null` 时 `SparkPackIssues.error(...invalid_path)` |
| `colors` | 只做 `PackCodec.parse(content)` 存原始 `JsonElement`；id 用 `ResourceLocation.tryParse`，为 `null` 时 `SparkPackIssues.error(...invalid_resource_location)` |
| `documents` | `LOCAL_ONLY`，合法路径写入 `SparkPackLoaderApplier.CLIENT_PACK`（`apricityui:apricity/<key>`）；`ResourceLocation.tryParse` 为 `null` 时 `SparkPackIssues.error(...document.invalid_path)` |
| `tooltips` | 纯文本，不走 codec；键为 `<命名空间>:<文件名>`（含后缀），值为文件全文 |

门控口径：`fromServer` 由 `PackLoadContext` 派生（`CLIENT_LOCAL` 为 false，其余为 true），来源看 `context.origin`；`huds` / `hud_layouts` / `colors` / `documents` / `tooltips` 只在客户端执行。

### 3.3 领域层构造期检查

`SubPartAttr` 的构造期与碰撞体构建期检查都抛 `PackContentException`：

- `mass <= 0` → `error.machine_max.subpart.zero_mass`；
- `hit_boxes` 为空 → `error.machine_max.subpart.empty_hit_boxes`；
- `getCollisionShape` 找不到骨骼或子形状数为 0 → `error.machine_max.subpart.empty_collision_shape`（该方法由 `VariantAttr` 构造器调用，也在运行时构建 `SubPart` 时调用）。

`VariantAttr` 的四处内容不变量同样抛 `PackContentException`：

| 检查 | 键 |
|------|-----|
| 模型不在 `OModel` 表内 | `error.machine_max.part.model_not_found` |
| 贴图列表为空 | `error.machine_max.part.missing_textures` |
| 连接点/子系统/交互区用了信号保留地址 | `error.machine_max.part.reserved_name` |
| 单变体内名称重复 | `error.machine_max.part.duplicate_name` |

消息在构造期就以 `Component` 形式携带，不调用 `getString()`；加载期由文件帧补上模块与位置，日志里只有一行。

模型存在性检查依赖 Spark 的 `models` 模块先读完：模块表按 `SparkPackageReaderRegisterEvent` 的派发顺序构造，`ModLoader.postEvent` 在同一优先级内按 mod 加载顺序逐 mod 派发，两边的监听器都用默认优先级，Spark-Core 是 MM 的依赖、先派发，因此 `models` 排在 MM 的 `parts` 之前。这个顺序若被改动，检查应移到模块的 `onFinish`（模块级帧，没有文件归属），而不是留在一个会误报的构造函数里。

运行时也会构造这些对象：`PartType.STREAM_CODEC` 的 `decode` 会重新解码 `variants`，从而在同步线程上构造 `VariantAttr`。这些路径没有加载期帧，`PackContentException` 照常抛出但没有文件归属；需要让玩家看到的检查不应放在运行时构造里。

### 3.4 事件与提示

`ObjectManager` 不订阅玩家加入或服务器启动来转发内容包错误：`onServerStart` 只清空 `serverAllVehicles` 与 `levelVehicles`。聊天摘要、服务端日志与同会话去重分别由 `PackIssueNotifier` 与 `logSummary` 负责。

### 3.5 语言键

MM 提供下列正文键（两个语言 Provider 都登记）：

- 配方：`error.machine_max.recipe.wrong_directory`、`wrong_type_directory`、`unknown_part`、`duplicate_part`、`invalid_result_count`；
- 研发：`error.machine_max.research.self_prerequisite`、`prerequisite_cycle`、`unreachable_prerequisite`、`missing_prerequisite`；
- 子部件：`error.machine_max.subpart.zero_mass`、`empty_hit_boxes`、`empty_collision_shape`；
- 部件：`error.machine_max.part.model_not_found`、`missing_textures`、`reserved_name`、`duplicate_name`；
- 内容包路径：`error.machine_max.hud_style.invalid_path`、`hud_layout.invalid_path`、`document.invalid_path`；
- 资源 id：`error.machine_max.invalid_resource_location`（色板文件名非法时使用）。

MM 不提供加载信封键；信封（`加载外部包文件 …` 之类）由 Spark 的汇总与摘要格式化。所有调用点使用 `translatableWithFallback`，英文 fallback 与 `en_us` 语言表一致。`runData` 重新生成 `src/generated/resources/assets/machine_max/lang/{zh_cn,en_us}.json`。

## 4. 时序与运行期边界

- `DataPackReloader` 用 `EventPriority.LOWEST`，Spark 的 `SparkPackLoaderApplier.onServerReload` 用默认优先级；低优先级处理器后执行，因此 MM 的 listener 排在 Spark 的 listener 之后，`apply` 晚于 Spark 的 `injectPackageContent`。
- 结果是：配方/研发问题以 `[recipe] <id> @ - → …` / `[research] <id> @ - → …` 的单行日志出现，进入 `SERVER_LOCAL` 的 bucket，但不出现在本次 reload 的服务端汇总行里。上游 v1.4 明确接受这一边界。
- 不要为了挤进汇总把重建搬进 `PackAllFinishedEvent`：该事件也会在 `AddPackFindersEvent` 的服务端预加载阶段触发，那时还没有 server 与 `RecipeManager`。若将来要搬，必须先加“server 已存在且处于 reload 阶段”的判据。
- `/spark package reload` 不触发 Minecraft 的 reload listener，因此该命令不重建配方/研发索引；但它以 `SERVER_LOCAL` 读取模块（`fromServer = true`），会清空并重建 `SERVER_PART_TYPES`。已放置的载具仍持有旧的 `PartType` 引用，因此把该命令当开发期工具，不在有载具的世界里使用。

## 5. 验收

### 5.1 已验证（2026-10-18）

1. `./gradlew build` 通过（含 Spark-Core 与 BallisticsFramework 的复合构建）。
2. `./gradlew runData` 通过，语言表按 Provider 重新生成。
3. `./gradlew runServer` 启动到 `Done`；两次内容包读取（启动预加载与首次 reload）都输出 `Content pack read finished: no issues.`，9 个双端模块计数一致：164 型子系统、118 种连接点、15 种材料、78 个部件、17 份模板、17 种蓝图、17 种装配体、13 个投射物、6 个控制组。
4. 在官方包放入一个 `parts/zz_diagnostic_probe.json`（`{"variants": {"default": {}}}`）后重启，日志为：

```
[内容包诊断/ERROR] [parts] machine_max:parts/zz_diagnostic_probe.json @ machine_max:official → Missing required field: sub_parts
[内容包诊断/INFO] Content pack read finished: 1 module(s) with issues (1 error(s) / 0 warning(s), 0 truncated): parts(1)
```

   记录带模块名、文件位置、包 id 与消息正文，无堆栈；汇总含 `parts(1)`；启动预加载与 reload 各一次。验证后已删除该文件。

### 5.2 待人工执行

1. `runClient` 登录：坏文件只出现一条摘要（`packIssueNotification = SUMMARY`），`DETAIL` 按模块列出且总行数不超过 10。
2. 写坏 `hud_layouts/*.json` 的元素字段：记录指向具体布局文件，模块为 `hud_layouts`。
3. 写坏 JSON 语法：消息带行列号。
4. 配方目录错误：只在服务端日志出现 `[recipe] …`，客户端摘要与服务端汇总行都不包含它。
5. `/spark package reload` 连续三次：记录数不累加；坏文件每次各出现一行；服务端汇总每次一条。
6. 进出世界两次：同一侧会话只提示一次，`LoggingOut` 清空后重新计数。
7. `packIssueNotification = OFF`：无聊天提示，日志仍完整。

## 6. 已知边界与风险

| 风险 | 说明 |
|------|------|
| 汇总不含配方/研发 | 由 listener 时序决定，见 §4；单行日志仍在，作者据模块名与 id 定位 |
| 服务端日志裸键 | 只写 `Component.translatable` 时专用服务器日志显示 `error.machine_max.*`；必须用 `translatableWithFallback` |
| 模块顺序 | `VariantAttr` 的模型检查依赖 `models` 先读完；顺序变化会让正确内容被报 `model_not_found`，此时把检查移到 `onFinish` |
| 运行时构造 | `STREAM_CODEC` 与 `SubPart` 构建没有帧；警告只进日志，错误无文件归属 |
| `/spark package reload` 会重载 MM | 该命令以 `SERVER_LOCAL` 读取模块，清空并重建 `SERVER_PART_TYPES`；已放置载具持有旧引用，只作开发期工具 |
| `PartModule` 的 id 口径 | 注册 id 只取 `fileName`（子目录不参与），不同子目录的同名文件互相覆盖；文件级诊断位置仍指向具体文件，便于发现冲突 |
| 无诊断命令 | V1 没有 `/spark package issues`，也没有 GUI；定位手段是日志行与汇总 |

## 7. 修订记录

| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0 | 2026-10-18 | 初稿：MM 侧接入方案、模块去 catch、配方/研发显式上报、领域层异常与语言键调整 |
| 1.1 | 2026-10-18 | 按 Spark-Core 落地版本（`e526385d`，v1.4）复核：`report` 末参为 `PackLoadContext`、没有诊断命令、补上游 API 摘要与 `PackAllFinishedEvent` 边界、记录 `/spark package reload` 会读取 MM 模块、补 `missing_textures` 等缺失键、`TooltipModule` 的 `id` 遮蔽、统一 fallback 口径 |
| 1.2 | 2026-10-18 | 实现落地：`MMDynamicRes` 的错误容器与发送方法退出、`text(...)` 与 `reportIfServer(...)` 上线、14 个模块改用 `PackCodec`、`SubPartAttr` / `VariantAttr` 改抛 `PackContentException`、`ObjectManager` 的错误转发入口退出、语言表按 §3.5 调整；补 §5.1 的构建、`runData`、`runServer` 与坏文件探针记录 |
