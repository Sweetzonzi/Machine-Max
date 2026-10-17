# 内容包诊断：Spark-Core 落地后的 Machine-Max 迁移说明

> **状态**: 计划中（依赖上游） · **创建**: 2026-10-18 · **修订**: 2026-10-18
>
> **上游**: Spark-Core `docs/pack_diagnostics_plan.md`
>
> **范围**: 只描述 Machine-Max 侧的改动；`PackIssue` / `SparkPackIssues` / `PackCodec` 的接口细节以上游文档为准。

---

## 1. 目标

Spark-Core 提供统一的内容包诊断后，Machine-Max 侧只保留“把内容错误抛出去 / 报出去”这一件事：

- `MMDynamicRes` 保留资源注册表、配方/研发索引与读取工具；`exceptions` / `errorFiles` / `errorMessages` 三份平行容器由 Spark-Core 的 `SparkPackIssues` 取代；
- 12 个内容包模块只负责解析与注册，不写 catch、不手写 `[HUD]` 之类的前缀；
- 客户端登录提示与服务端汇总由 Spark-Core 负责，MM 侧不监听玩家加入事件；
- 配方、研发这类“不是包文件”的校验，通过显式 `report` 进入同一张表。

## 2. 当前 MM 侧实现

| 位置 | 现状 |
|------|------|
| `external/MMDynamicRes.java:139-141` | `exceptions` / `errorFiles` / `errorMessages` 三个 `ArrayList` |
| `external/MMDynamicRes.java:147-151` | `initResources()` 只清空这三个列表；`MachineMax` 构造时调用一次 |
| `external/MMDynamicRes.java:628-633` | `recordIfServer` 两段式写入：先加文件、再加消息 |
| `external/MMDynamicRes.java:686-709` | `sendErrorToPlayer` / `sendErrorToConsole` 按 `errorFiles` 索引取消息 |
| `common/mech/ObjectManager.java:387-399` | `EntityJoinLevelEvent` 发提示；`ServerAboutToStartEvent` 打控制台 |
| 12 个模块 | `Assembly` / `Blueprint` / `Connector` / `ControlGroup` / `Hud` / `HudLayout` / `Material` / `Part` / `Projectile` / `Subsystem` / `Template` / `Tooltip` 各自 `catch (Exception)` 后写三份列表 |
| `ColorModule` / `DocumentModule` | 无 catch；异常目前冒泡到 Spark 的日志 catch（`DocumentModule` 的非法路径只打 `LOGGER.warn`） |
| `datagen/MMLanguageProviderZH_CN.java:75` | `error.machine_max.load` 信封文案 |
| `PartType` / `VariantAttr` / `SubPartAttr` | 构造期抛 `IllegalArgumentException`；两处直接抛裸 key（`SubPartAttr.java:130,138`） |

## 3. 迁移后的形态

### 3.1 `MMDynamicRes`

删除：

- `exceptions`、`errorFiles`、`errorMessages` 三个字段；
- `initResources()`、`reload()`，以及 `MachineMax.java:54` 与 `DataPackReloader.prepare` 里的对应调用；
- `recordIfServer`、`sendErrorToPlayer`、`sendErrorToConsole`、`errorMessageAt`。

保留：所有资源注册表、配方/研发索引、`fileStream` 等读取工具。

配方与研发校验改为显式上报，只在服务端调用：

```java
SparkPackIssues.report(
        "recipe",
        holder.id().toString(),
        PackSeverity.ERROR,
        Component.translatable("error.machine_max.recipe.wrong_directory"),
        null,
        PackSide.SERVER);
```

`moduleId` 用 `"recipe"` / `"research"`；`location` 用配方或研发 id；`packId` 为 `null`（这些条目不在 `spark_modules` 里，来自数据包）。

### 3.2 内容包模块

统一改成“不 catch”：

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
| `AssemblyModule` / `BlueprintModule` / `ConnectorModule` / `ControlGroupModule` / `MaterialModule` / `PartModule` / `ProjectileModule` / `SubsystemModule` / `TemplateModule` / `TooltipModule` | 删 `try/catch`，解码改 `PackCodec.read` |
| `HudModule` / `HudLayoutModule` | 同上；`contentKey == null` 时按各自文案 `error(...)`（“非法的 HUD 样式路径” / “非法的 HUD 布局路径”），定位由帧提供，模块不写列表 |
| `ColorModule` | 只做 `PackCodec.parse(content)`，它不经过 codec，只存 `JsonElement` |
| `DocumentModule` | 非法 AUI 路径由 `LOGGER.warn` 改为 `SparkPackIssues.warn(...)`，进入同一张表；有效路径逻辑不变 |

`ColorModule` 与 `DocumentModule` 当前没有 catch；前者只替换解析调用，后者只调整非法路径的上报。

### 3.3 领域层构造期检查

`SubPartAttr` 的两处裸 key 改成带 `Component` 的异常：

```java
if (mass <= 0f) {
    throw new PackContentException(Component.translatable("error.machine_max.subpart.zero_mass"));
}
if (hitBoxes.isEmpty()) {
    throw new PackContentException(Component.translatable("error.machine_max.subpart.empty_hit_boxes"));
}
```

`VariantAttr` 的模型存在性检查（`VariantAttr.java:96-98`）同样换成 `PackContentException`。它依赖 Spark 的 `models` 模块先读完，属于跨文件检查；若上游的模块顺序约定不稳定，应该移到 `onFinish` 的校验阶段，而不是留在构造函数里。

运行时也会构造这些对象：

- `PartType.STREAM_CODEC` 的 `decode` 会在加载期之外重新构造 `PartType`（当前没有调用点）；
- `SubPart` 构造时走 `getCollisionShape`（`VariantAttr` 已在解码期填充缓存）。

这些路径没有加载期帧：`SparkPackIssues.warn` 退化为普通日志，`PackContentException` 照常抛出但没有文件归属。若希望运行时错误也能指回来源，在 `PartModule.read` 注册时给 `PartType` 增加 `sourceLocation`（与现有 `registryKey` 同批写入）。

### 3.4 客户端与服务端事件

删除 `ObjectManager.displayCustomPackError`（`EntityJoinLevelEvent`）与 `onServerStart` 里的 `sendErrorToConsole` 调用；保留载具数据清空逻辑。提示与日志由 Spark-Core 的 `PackIssueNotifier` 和加载器汇总负责。

### 3.5 语言键

- 删除 `error.machine_max.load`（信封由 Spark 格式化）。
- 保留 `error.machine_max.recipe.*`、`error.machine_max.research.*`、`error.machine_max.subpart.*`、`error.machine_max.part.model_not_found`：这些是消息正文，仍由 MM 提供。
- 新增的警告键统一以 `warn.machine_max.` 开头，例如 `warn.machine_max.subpart.low_mass`。
- `datagen` 两个语言 Provider 同步增删。

## 4. 验收

1. `./gradlew build` 通过（Spark-Core 与 MM 的复合构建）。
2. `runClient` 进世界，故意写坏一个 `parts/*.json`（删掉 `variants`）：
   - 登录只出现一条摘要；
   - `/spark package issues` 显示 `[parts] machine_max:parts/<file>.json → 缺少必填字段 "variants"`。
3. 写坏 `hud_layouts/*.json` 的元素字段：记录指向具体布局文件。
4. 写坏 JSON 语法：消息带行列号。
5. 正常内容包：无聊天提示，日志无 ERROR。
6. 服务端启动日志有汇总；配方目录错误只出现在服务端 bucket。
7. 进出世界两次：旧记录不重复提示。
8. 内容包错误不会触发 `IndexOutOfBoundsException` 或 `text is null`。

## 5. 风险

| 风险 | 说明 |
|------|------|
| 上游未落地 | 本文所有改动依赖 Spark-Core 的 P0/P1；在 P0 完成前不能删除 MM 的旧容器，否则内容错误无处可去 |
| 一次性切换 | 不建议新旧两套并行写：两套记录会重复提示，且清空时机再次分叉 |
| 专用服务器上的配方错误 | 服务端 bucket 与客户端 bucket 分离，远程玩家看不到服务端的配方/研发错误；需要等上游 P3 的同步 payload |
| 翻译键缺失 | `PackContentException` 的消息在客户端渲染时才解析；`lang` 没补会显示裸 key |
| 运行时构造 | `STREAM_CODEC` 路径没有帧，警告只进日志；不要把“必须让玩家看到”的检查放在运行时构造里 |

## 6. 修订记录

| 版本 | 日期 | 内容 |
|------|------|------|
| 1.0 | 2026-10-18 | 初稿：删除旧容器与事件、模块去 catch、配方/研发显式上报、领域层异常与语言键调整 |
