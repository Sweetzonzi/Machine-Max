# 内容包诊断：Machine-Max 适配计划（Spark-Core v1.4）

> **状态**: 计划中（上游已落地，MM 侧待实施） · **创建**: 2026-10-18 · **修订**: 2026-10-18
>
> **上游**: Spark-Core 提交 `e526385d`，设计文档 `docs/pack_diagnostics_plan.md` v1.4。P0–P3 已落地：四值加载上下文、问题收集器、`PackCodec` / `PackContentException`、服务端按模块汇总、客户端登录摘要、三个加载阶段事件。消费方迁移（P4）不在上游仓库，也没有 `/spark package issues` 命令。
>
> **依赖形态**: `gradle.properties` 的 `spark_core_version=1.0.1034`；`settings.gradle` 在 `../Spark-Core` 存在时用复合构建替代该坐标。本文的签名按本地 Spark-Core 源码核对。
>
> **范围**: 只描述 Machine-Max 侧的改动与验收。§2 是上游 API 摘要，细节以上游文档为准。

---

## 1. 目标

Spark-Core 统一收集内容包问题后，Machine-Max 侧只保留“解析、注册、把错误抛出去或报出去”：

- `MMDynamicRes` 保留资源注册表、配方/研发索引与读取工具；`exceptions` / `errorFiles` / `errorMessages` 三份平行容器由 `SparkPackIssues` 取代；
- 14 个内容包模块只负责解析与注册，不写 `catch`、不手写 `[HUD]` 之类的前缀；
- 客户端登录提示与服务端汇总由 Spark-Core 负责，MM 侧不监听玩家加入事件；
- 配方、研发这类“不是包文件”的校验，通过显式 `report` 进入同一张表；
- 所有给作者看的消息都保留翻译能力，并带英文 fallback——专用服务器没有加载语言表，`Component.translatable(...).getString()` 在服务端日志里只会输出裸 key。

## 2. 上游机制（已落地）

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
| `DfuText` | 把 DFU 的 `No key x in {…}` 压成“缺少必填字段 x”，其余消息截断到 300 字符 |
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

### 2.5 与 MM 相关的边界

- **汇总边界**：汇总/摘要只保证包含本次 `readPackageContent` / `injectPackageContent` 期间记录的问题。消费方在自己的 reload listener 中、晚于 Spark 汇总执行的额外检查，`report` 后仍会进当前上下文的 bucket 并各打一行日志，但不会出现在那一次的汇总行里。
- **运行时无帧**：`SparkPackIssues.warn` 在没有帧时退化为普通日志，不进表；`SparkPackIssues.error` 照常抛出 `PackContentException`，但没有文件归属。
- **Java 互操作**：`SparkPackIssues.report(moduleId, location, severity, message, cause, context)` 是 `@JvmStatic`，Java 可直接调用，`packId` 固定为 `null`；`SparkPackIssues.error` 在 Java 里返回 `Void` 且总是抛出，Java 侧直接写 `throw new PackContentException(...)`；`PackCodec.parse/read`、`DfuText.toComponent`、`RuntimeIssueLog.warn/error` 都是 `@JvmStatic`，`PackCodec.read` 的 `ops` 有 `@JvmOverloads`。
- **消息可读性**：上游统一使用 `Component.translatableWithFallback`，保证服务端日志不是裸 key。MM 侧不在这一点上例外。

## 3. Machine-Max 当前实现

| 位置 | 现状 |
|------|------|
| `external/MMDynamicRes.java:139-141` | `exceptions` / `errorFiles` / `errorMessages` 三个 `ArrayList` |
| `external/MMDynamicRes.java:143-151` | `reload()` 只调用 `initResources()`；`initResources()` 只清空三个列表 |
| `MachineMax.java:54` | 构造时调用 `MMDynamicRes.initResources()` |
| `external/MMDynamicRes.java:641` | `DataPackReloader.prepare` 在资源重载的后台线程调用 `MMDynamicRes.reload()`；两个线程同时读写这三个 `ArrayList` |
| `external/MMDynamicRes.java:629-633` | `recordIfServer` 两段式写入：先加文件、再加消息；非服务端直接返回 |
| `external/MMDynamicRes.java:686-709` | `sendErrorToPlayer` / `sendErrorToConsole` 按 `errorFiles` 下标取消息；`errorMessageAt` 缺消息时回退占位文本 |
| 12 个模块 | `Assembly` / `Blueprint` / `Connector` / `ControlGroup` / `Hud` / `HudLayout` / `Material` / `Part` / `Projectile` / `Subsystem` / `Template` / `Tooltip` 各自 `catch (Exception)` 后写三份列表，并手写 `[Part]` / `[HUD Layout]` 之类的前缀 |
| `HudModule.kt:42-45`、`HudLayoutModule.kt:41-44` | 路径键为 `null` 时不抛异常，直接往列表里写“非法的 HUD 样式/布局路径” |
| `ColorModule.kt:34` | 只用裸 `JsonParser` 解析成 `JsonElement`，没有 `catch` |
| `DocumentModule.kt:57-60` | 非法 AUI 路径只打 `LOGGER.warn("[AUI Document] invalid AUI path: {}")`，不进任何表 |
| `TooltipModule.kt:32-40` | `catch` 里的 `id` 解析到的是模块自身的 `override val id = "tooltips"`，错误文件永远显示 `[Tooltip]tooltips` |
| `ObjectManager.java:387-399` | `EntityJoinLevelEvent` 走 `sendErrorToPlayer`；`ServerAboutToStartEvent` 走 `sendErrorToConsole`，随后清空载具表 |
| `datagen/MMLanguageProviderZH_CN.java:75`、`MMLanguageProviderEN_US.java:75` | `error.machine_max.load` 是“加载外部包文件 … 时出错”的信封文案 |
| `SubPartAttr.java:130,138` | 构造期抛裸 key 的 `IllegalArgumentException("error.machine_max.subpart.zero_mass")` / `("…empty_hit_boxes")` |
| `VariantAttr.java:96-102` | 模型存在性与贴图非空检查抛 `IllegalArgumentException(Component.translatable(...).getString())`；`error.machine_max.part.missing_textures` 没有在任一语言 Provider 中定义 |
| `PartType.java:89-107` | 构造器不做内容不变量检查；`setRegistryKey` / `getRegistryKey` 的异常属于注册期代码约束，不是内容错误 |
| `PartModule.kt:36-38` | 注册 id 只取 `fileName`，子目录不参与 id；文件级诊断位置由 Spark 的帧提供 |

## 4. 迁移后的形态

### 4.1 `MMDynamicRes`

删除：

- `exceptions`、`errorFiles`、`errorMessages` 三个字段；
- `reload()`、`initResources()`，以及 `MachineMax.java:54`、`DataPackReloader.prepare` 里的调用；
- `recordIfServer`、`sendErrorToPlayer`、`sendErrorToConsole`、`errorMessageAt`。

保留：所有资源注册表、配方/研发索引、`fileStream` 等读取工具。

配方与研发校验改为显式上报，仍然只在服务端调用：

```java
if (!serverSide) return;
SparkPackIssues.report(
        "recipe",
        recipeId.toString(),
        PackSeverity.ERROR,
        MMDynamicRes.text("error.machine_max.recipe.wrong_directory",
                "Part recipes must be placed under recipe/part_fabricating/"),
        null,
        PackLoadContext.SERVER_LOCAL);
```

`moduleId` 用 `"recipe"` / `"research"`；`location` 用配方或研发 id；`packId` 由该重载固定为 `null`（这些条目不在 `spark_modules` 里，来自数据包）。`text(...)` 是 MM 侧的薄封装，返回 `Component.translatableWithFallback`，见 §4.6。

### 4.2 内容包模块

统一改成“不 catch”，解码走 `PackCodec.read`：

```kotlin
override fun read(...) {
    if (!fromServer || !fileName.endsWith(".json")) return
    val id = ResourceLocation.fromNamespaceAndPath(namespace, fileName.removeSuffix(".json"))
    val partType = PackCodec.read(PartType.CODEC, content)   // 缺字段/语法错误 → PackContentException
    partType.registryKey = id
    if (isClientSide) MMDynamicRes.PART_TYPES[id] = partType
    else MMDynamicRes.SERVER_PART_TYPES[id] = partType
}
```

模块级动作：

| 模块 | 迁移动作 |
|------|----------|
| `AssemblyModule` / `BlueprintModule` / `ConnectorModule` / `ControlGroupModule` / `MaterialModule` / `PartModule` / `ProjectileModule` / `SubsystemModule` / `TemplateModule` / `TooltipModule` | 删 `try/catch` 与三份列表写入，解码改 `PackCodec.read`；`TooltipModule` 只存字符串，删 `catch` 即可 |
| `HudModule` / `HudLayoutModule` | 同上；`contentKey == null` 时 `SparkPackIssues.error(Component.translatableWithFallback("error.machine_max.hud_style.invalid_path", …))`（布局模块用 `hud_layout` 键），定位由文件帧提供 |
| `ColorModule` | 只做 `PackCodec.parse(content)`，它不经过 codec，只存 `JsonElement`；id 用 `ResourceLocation.tryParse`，失败时 `SparkPackIssues.error(...)` |
| `DocumentModule` | 非法 AUI 路径由 `LOGGER.warn` 改为 `SparkPackIssues.error(...)`：该文档不会进入 `CLIENT_PACK`，按“不能进注册表 → ERROR”归类；有效路径逻辑不变 |

`onStart` 的 `isClientSide` / `fromServer` 门控保持不变。`fromServer` 由 `PackLoadContext` 派生：`CLIENT_LOCAL` 为 `false`，其余上下文为 `true`。来源看 `context.origin`，不要用 `fromServer` 区分 `CLIENT_LOCAL` 与 `CLIENT_REMOTE`。

### 4.3 领域层构造期检查

`SubPartAttr` 的两处裸 key 改成带 `Component` 的异常：

```java
if (mass <= 0f) {
    throw new PackContentException(Component.translatableWithFallback(
            "error.machine_max.subpart.zero_mass", "Sub-part mass must be greater than zero"));
}
if (hitBoxes.isEmpty()) {
    throw new PackContentException(Component.translatableWithFallback(
            "error.machine_max.subpart.empty_hit_boxes", "Sub-part must have at least one hit-box"));
}
```

`VariantAttr` 的模型存在性与贴图检查同样换成 `PackContentException`，消息直接用 `Component`，不要在构造期 `getString()`：

- `error.machine_max.part.model_not_found`（键已存在）；
- `error.machine_max.part.missing_textures`（键当前缺失，需要补）。

模型存在性检查依赖 Spark 的 `models` 模块先读完。模块表按 `SparkPackageReaderRegisterEvent` 的派发顺序构造：`ModLoader.postEvent` 在同一优先级内按 mod 加载顺序逐 mod 派发，两边的监听器都用默认优先级，Spark 是 MM 的依赖、先派发，所以 `models` 排在 MM 的 `parts` 之前。若这个顺序被改动，检查应移到模块的 `onFinish`（模块级帧，没有文件归属），而不是留在构造函数里。

运行时也会构造这些对象：

- `PartType.STREAM_CODEC` 的 `decode` 会重新解码 `variants`，从而在同步线程上构造 `VariantAttr`；
- `SubPart` 构造时走 `getCollisionShape`（`VariantAttr` 已在解码期填充缓存）。

这些路径没有加载期帧：`SparkPackIssues.warn` 退化为普通日志，`PackContentException` 照常抛出但没有文件归属。不要把“必须让玩家看到”的检查放在运行时构造里。

### 4.4 配方/研发上报与时序

配方与研发索引仍在 `MMDynamicRes.DataPackReloader.apply` 里重建，`recordIfServer` 的 `serverSide` 门控保留（客户端重建传 `false`，不产生任何记录）。

时序事实：

- `DataPackReloader` 以 `EventPriority.LOWEST` 注册到 `AddReloadListenerEvent`（`MMDynamicRes.java:655-659`），Spark 的 `SparkPackLoaderApplier.onServerReload` 用默认优先级。低优先级处理器后执行，因此 MM 的 listener 排在 Spark 的 listener 之后，`apply` 晚于 Spark 的 `injectPackageContent`。
- 结果是：配方/研发问题以 `[recipe] <id> @ - → …` / `[research] <id> @ - → …` 的单行日志出现，进入 `SERVER_LOCAL` 的 bucket，但不会出现在本次 reload 的服务端汇总行里。上游 §3.5 明确接受这一边界，本文沿用。
- 不要把重建搬进 `PackAllFinishedEvent` 来挤进汇总：该事件也会在 `AddPackFindersEvent` 的服务端预加载阶段触发，那时还没有 server 与 `RecipeManager`。若将来要搬，必须先加“server 已存在且处于 reload 阶段”的判据。
- `/spark package reload` 不触发 Minecraft 的 reload listener，因此该命令不会重建配方/研发索引。

### 4.5 客户端与服务端事件

删除 `ObjectManager.displayCustomPackError`（`EntityJoinLevelEvent`）与 `onServerStart` 里的 `sendErrorToConsole` 调用；`onServerStart` 保留载具数据清空逻辑。提示、日志与同会话去重由 Spark-Core 的 `PackIssueNotifier` 与加载器汇总负责，MM 侧不监听玩家加入事件。

### 4.6 语言键与日志可读性

- 删除 `error.machine_max.load`：该键只被 `sendErrorToPlayer` / `sendErrorToConsole` 使用，两者随 §4.1 一起删除，信封由 Spark 的汇总/摘要格式化。
- 保留 `error.machine_max.recipe.*`、`error.machine_max.research.*`、`error.machine_max.subpart.*`、`error.machine_max.part.*`：这些是消息正文，仍由 MM 提供。
- 新增：`error.machine_max.part.missing_textures`、`error.machine_max.hud_style.invalid_path`、`error.machine_max.hud_layout.invalid_path`、`error.machine_max.document.invalid_path`。新增警告若将来出现，统一以 `warn.machine_max.` 开头。
- 所有交给 `SparkPackIssues.report` / `error` / `warn` 的消息都走 `Component.translatableWithFallback(键, 英文 fallback, 参数…)`。专用服务器不加载语言表，`translatable` 在日志里会输出裸键；带 fallback 的版本在客户端仍按语言表渲染。建议用一个 `MMDynamicRes.text(...)`（或 `util` 下的同类 helper）统一构造，避免每个调用点重复写 fallback。
- `datagen` 两个语言 Provider 同步增删，并检查 `runData` 生成结果。

## 5. 实施顺序

1. 语言键：删 `error.machine_max.load`，补 §4.6 的新键与英文 fallback，加 helper。
2. 领域层：`SubPartAttr`、`VariantAttr` 改 `PackContentException`（先改，模块迁移后错误信息直接以 `Component` 进入汇总）。
3. `MMDynamicRes`：删三份容器与发送方法，`recordIfServer` 改成 `SparkPackIssues.report`；删 `reload()` / `initResources()` 及其调用点（`MachineMax.java:54`、`DataPackReloader.prepare`）。
4. 12 个模块去 `catch`、换 `PackCodec`；HUD 两个模块与 `DocumentModule` 改显式 `error`；`ColorModule` 换 `parse`。
5. `ObjectManager` 删两个提示入口与失效的 import（`EntityJoinLevelEvent`、`Player`）。
6. `./gradlew build`（含复合构建），`runServer` 验证注册、内容包与配方加载，`runClient` 进世界验证提示与 HUD。
7. 按 §6 逐条验收；改动集中在一个提交，避免两套容器并行。

## 6. 验收

1. `./gradlew build` 通过（Spark-Core 与 MM 的复合构建）。
2. 故意写坏一个 `parts/*.json`（删掉 `variants`）：
   - 客户端登录默认只出现一条摘要（`内容包有 N 个错误…`）；
   - 日志出现 `[parts] machine_max:parts/<file>.json @ <packId> → 缺少必填字段: variants`；
   - 服务端日志至少有一条按模块分组的汇总，其中包含 `parts(1)`（首次启动的预加载与 reload 各会汇总一次）。
3. 写坏 `hud_layouts/*.json` 的元素字段：记录指向具体布局文件，模块是 `hud_layouts`。
4. 写坏 JSON 语法：消息带行列号。
5. 正常内容包：无聊天提示；服务端汇总为 `no issues.`；日志无 ERROR。
6. 配方目录错误：只在服务端日志出现 `[recipe] …`，客户端摘要不包含它；服务端汇总行不包含它（§4.4 的边界）。
7. `/spark package reload` 连续三次：记录数不累加；坏文件每次各出现一行；服务端汇总每次一条。
8. 进出世界两次：旧记录不重复提示（`LoggingOut` 清空 `CLIENT_LOCAL` / `CLIENT_REMOTE`）。
9. `packIssueNotification = OFF`：无聊天提示，日志仍完整。
10. 代码中不存在 `exceptions` / `errorFiles` / `errorMessages` 与 `error.machine_max.load`；内容包错误不会触发 `IndexOutOfBoundsException` 或 `text is null`。

## 7. 风险

| 风险 | 说明 |
|------|------|
| 汇总不含配方/研发 | 由 listener 时序决定，见 §4.4；单行日志仍在，作者据模块名与 id 定位。若要求进汇总，需先解决预加载阶段没有 `RecipeManager` 的问题 |
| 服务端日志裸键 | 只写 `Component.translatable` 时，专用服务器日志显示 `error.machine_max.*`；必须用 `translatableWithFallback` |
| 模块顺序 | `VariantAttr` 的模型检查依赖 `models` 先读完；顺序变化会让正确内容被报 `model_not_found`。顺序不稳时把检查移到 `onFinish` |
| 运行时构造 | `STREAM_CODEC` 路径没有帧，警告只进日志；不要把“必须让玩家看到”的检查放在运行时构造里 |
| `/spark package reload` 会重载 MM | 该命令以 `SERVER_LOCAL` 读取模块（`fromServer = true`），会清空并重建 `SERVER_PART_TYPES`；已放置的载具仍持有旧的 `PartType` 引用。把该命令当开发期工具，不在有载具的世界里使用 |
| 一次性切换 | 不建议新旧两套并行写：两套记录会重复提示，且清空时机再次分叉 |
| 运行时数据竞争 | 当前的 `ArrayList` 在 `prepare` 后台线程被清空、在主线程被读取；容器随 §4.1 删除后，这条竞争自然消失 |
| `PartModule` 的 id 口径 | 注册 id 只取 `fileName`（`PartModule.kt:36-38`），子目录同名文件会互相覆盖；文件级诊断位置仍指向具体文件，便于发现冲突 |

## 8. 修订记录

| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0 | 2026-10-18 | 初稿：删除旧容器与事件、模块去 catch、配方/研发显式上报、领域层异常与语言键调整 |
| 1.1 | 2026-10-18 | 按 Spark-Core 落地版本（`e526385d`，v1.4）复核：`report` 末参改为 `PackLoadContext`、删除命令相关验收、补上游 API 摘要与 `PackAllFinishedEvent` 边界、记录 `/spark package reload` 现已读取 MM 模块、补 `missing_textures` 等缺失键、`TooltipModule` 的 `id` 遮蔽、统一 fallback 口径 |
