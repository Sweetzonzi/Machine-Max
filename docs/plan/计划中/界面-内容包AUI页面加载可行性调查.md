# 界面-内容包 AUI 页面与资源加载可行性调查

> **状态**：调查完成，代码未实现。结论：内容包提供 AUI 页面与资源可行，且不需要修改 AUI。推荐把内容包文件注入 Spark-Core 已有的虚拟客户端资源包 `CLIENT_PACK`，路径为 `apricityui:apricity/<key>`，由 AUI 现有的 `ResourceManager` 分支读取；HUD、载具手册、PDA、研究页共用这一条通用管线。第一阶段只使用模组 JAR 内的 `assets/apricityui/apricity/` 页面。

## 一、问题

AUI 页面不只服务 HUD：载具说明手册、PDA 页面、研究页、控制面板都可能由内容包提供。需要一条通用管线，让内容包里的 HTML、CSS、JavaScript、图片、字体和音频在客户端按路径解析；HUD 只是这条管线的消费者之一。

内容包在本项目中是 `spark_modules/` 下的目录或 zip，由 Spark-Core 读成内存条目，并通过网络同步给客户端。它不是 Minecraft 资源包，不能直接把文件放进 `assets/`。因此要回答：

1. AUI 从哪里取 HTML 与页面资源；
2. 内容包字节怎样进入这条取数链；
3. 有没有比“替换 AUI 资源服务”更简单的通用注入点。

## 二、结论

| 结论 | 说明 |
| --- | --- |
| 可行 | AUI 1.2.7 的 `ResourceManager` 分支会读取客户端资源包；Spark-Core 已有可写的虚拟客户端资源包 `CLIENT_PACK`，可以把内容包字节注入进去 |
| AUI 没有逐资源注册 API | `HTML.putTemple` 只注册 HTML 模板；CSS、JS、图片、字体、音频没有对应的公开注册入口。可以整体替换资源服务（`AuiServices.setResources`），但直接改 AUI 内部缓存既覆盖不全，也会漏掉异步加载器 |
| 推荐通用注入点 | `SparkPackLoaderApplier.CLIENT_PACK.put(PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("apricityui", "apricity/" + key), bytes)`；AUI 的 `ClientLoader` 会按现有链读取 |
| 关键前提 | `apricityui` 命名空间必须在 `MultiPackResourceManager` 构造前就存在于 `CLIENT_PACK`，否则连接服务器后新增的页面不会被 `FallbackResourceManager` 列出 |
| 服务端同步后 | 注入完成后调用 `HTML.scan()` 让新 HTML 进入模板表，并调用 `ClientLoader.invalidateStaticResourceCache()`；只写资源包不会自动更新模板表 |
| 备选 | `AuiServices.setResources(AuiResourceService)` 包装器 + `HTML.putTemple`；适合需要自定义寻址或不想依赖资源包生命周期时 |
| 第一阶段可不做 | 只使用模组 JAR 的 `assets/apricityui/apricity/machine_max/...` 页面即可；现有页面已经按这条链工作 |
| 不需要 AUI 分支 | AUI 公开 API 与 Spark 虚拟资源包足够；代价是接受资源包与模板表的生命周期约束 |

本文以 AUI 1.2.7 为准。已用依赖 JAR 的 `javap` 核对 `putTemple` / `getTemple` / `scan` / `setResources` / `invalidateStaticResourceCache` 均存在。项目当前的 `neoforge.mods.toml` 声明 `apricityui` 版本范围从 1.2.5 起；这些入口在 1.2.5 上是否同样存在尚未核对。落地时把最低版本提到 1.2.7，或在客户端启动时探测这些入口并给出可定位的缺失日志。

## 三、AUI 的页面与资源解析链

AUI 1.2.7 的读取顺序如下（以本地 AUI 源码副本为准）：

1. `Loader.getResourceStream(path)`：
   - 开发期资源根（`src/main/resources/assets/apricityui/apricity`、`common/src/main/resources/...`）；
   - 开发期项目根下的候选路径；
   - `<gamedir>/apricity/<path>`；
   - AUI 自身类路径的 `assets/apricityui/apricity/<path>`。
2. 以上都没有时，`ClientLoader.getResourceStream(path)` 调用 `AuiServices.resources().openResource("apricity/" + path)`，即 Minecraft 客户端 `ResourceManager` 的资源包链。
3. HTML 模板不由 `openResource` 直接读取。`ClientLoader.loadResources(HTML::putTemple)` 在启动/重载时扫描 HTML，把内容放进 `HTML.temples`；`Document.create(path)` 只检查这张表。
4. CSS、JS、图片、字体、音频在页面解析后按需调用 `ClientLoader.getResourceStream`，因此只要资源在资源包链里，它们就能被现有加载器读取。

关键源码位置（AUI 1.2.7）：

| 行为 | 位置 |
| --- | --- |
| 模板写入/读取 | `com.sighs.apricityui.parser.HTML#putTemple`、`#getTemple` |
| 扫描 HTML 资源 | `com.sighs.apricityui.parser.HTML#scan`，内部 `new ClientLoader("html").loadResources(HTML::putTemple)` |
| 文档创建前检查模板 | `com.sighs.apricityui.dom.DocumentRegistry#create`：`if (HTML.getTemple(path) == null) { error; return null; }` |
| 文件系统与类路径读取 | `com.sighs.apricityui.loader.Loader#getResourceStream` |
| 资源包回退 | `com.sighs.apricityui.loader.ClientLoader#getResourceStream` |
| 资源服务安装 | `com.sighs.apricityui.spi.AuiServices#setResources` |
| 默认命名空间 | NeoForge 目标 `ResourceService#parseLocation`：无冒号时使用 `apricityui` 命名空间 |

由此得到两个直接事实：

- 页面必须进入 `HTML.temples`；只把 HTML 放进资源包而不重新扫描，`Document.create` 仍会在模板检查处返回 `null`。
- 资源包链里的页面路径需要落在 `apricityui:apricity/...`，因为 `openResource` 收到的是不带命名空间的 `apricity/<path>`，默认补 `apricityui`。`listResourcePaths` 会列出其它命名空间，但随后的 `openResource` 仍按 `apricityui` 打开，二者不一致。

## 四、内容包字节怎样到达客户端

Spark-Core 的 `spark_modules` 读取器已经把内容包文件读成 `SparkPackage.entries`：

- 目录包和 zip 包都会保留相对路径与字节内容（`ReadableDirectory` / `ReadableZip`）。
- `SparkPackLoader.readPackageContent` 按模块调用 `SparkPackModule.read(namespace, pathSegments, fileName, content, ...)`。
- 服务端通过配置阶段的 `SparkPackagePayload` 把包图发给客户端；客户端在 `handleInClient` 中 `acceptRemote`、`readPackageContent(true, true)`、`injectPackageContent(true, true)`。
- 模块可以声明 `ReadMode.SERVER_TO_CLIENT`（默认，随包发送）、`LOCAL_ONLY` 或 `CLIENT_LOCAL_ONLY`；`collectRemote` 只发送 `shouldSend()` 为真的模块。

通用页面模块可以沿用 `HudModule` 的生命周期：客户端 `onStart` 清空页面表，`read` 把字节存进共享表，`onFinish` 通知客户端发布。注意 `HudModule.read` 用 `fileName.removeSuffix(".json")` 生成 RL，没有把 `pathSegments` 拼回去；页面模块必须拼完整路径，否则子目录页面会撞键。

## 五、可选路径

### 5.1 模组 JAR 内资源（第一阶段）

把页面放在本模组 JAR 的 `assets/apricityui/apricity/machine_max/...`。AUI 的类路径分支或资源包分支都能读到；现有 21 个页面已经这样工作。

- 优点：零新增代码，构建期即可校验，路径稳定。
- 限制：内容包作者不能只改 `spark_modules/` 就提供页面；页面必须打进一个模组 JAR 或资源包，并放在 `assets/apricityui/apricity/` 这个全局命名空间。
- 适用：第一阶段“写死只能调用 resource 里的那些”。

### 5.2 内容包作为模组 JAR 携带 `assets/`

内容包如果以模组 JAR 分发，可以同时带 `spark_modules/` 和 `assets/apricityui/apricity/...`。Minecraft 的资源包链会看到它，AUI 能读。

- 优点：不改代码；HTML/CSS/图片都能用标准资源包工具链。
- 限制：页面必须放在 `apricityui` 命名空间；两个内容包的同名路径由资源包顺序决定；纯 `spark_modules/` 目录/zip 包没有 `assets/`。

### 5.3 Spark 虚拟客户端资源包（推荐）

Spark-Core 的 `SparkVirtualResourcePack` 是一个可写的 `PackResources`，`SparkPackLoaderApplier.CLIENT_PACK` 是它的 `CLIENT_RESOURCES` 实例，并在 `AddPackFindersEvent` 中注册为始终启用的内置包。内容包模块可以把任意 `assets/...` 字节写进这个包。

对 AUI 页面，路径固定为：

```text
apricityui:apricity/<key>
```

例如内容包 `spark_modules/my_pack/machine_max/aui_pages/manual/tank.html` 映射为：

```text
apricityui:apricity/machine_max/manual/tank.html
```

AUI 收到的键是 `machine_max/manual/tank.html`，页面内的 `./tank.css` 解析为 `machine_max/manual/tank.css`，同一个包里的 `apricityui:apricity/machine_max/manual/tank.css` 会被现有 `ResourceManager` 找到。

落地要点：

| 项 | 要求 |
| --- | --- |
| 命名空间种子 | 在资源包注册前写入 `apricityui:apricity/.keep`（空字节即可），让 `CLIENT_PACK.getNamespaces(CLIENT_RESOURCES)` 在资源管理器构造时包含 `apricityui`。没有这个种子，连接服务器后新增的页面不会进入该命名空间的 `FallbackResourceManager` |
| 注入时机 | 客户端本地内容包在 `AddPackFindersEvent` 的预加载阶段写入；服务端同步在 `SparkPackagePayload.handleInClient` 的 `read/onFinish` 中写入 |
| HTML 模板 | 注入完成后调用 `HTML.scan()`；`HTML.scan` 会通过 `listResourcePaths("apricity", ".html")` 找到新页面并 `putTemple`。只写资源包不会自动更新模板表 |
| 缓存 | `HTML.scan()` 前后调用 `ClientLoader.invalidateStaticResourceCache()`；AUI 的静态资源清单可能仍是旧快照 |
| 资源覆盖 | `HTML.scan` 的加载顺序是资源包 → `<gamedir>/apricity` → 开发目录；本地覆盖文件优先 |
| 生命周期 | `SparkVirtualResourcePack` 没有清空 API；删除或改名的页面会留到退出游戏。页面路径带版本或包名可规避 |
| 资源范围 | HTML/CSS/JS/`<img>`/`@font-face`/`<audio>` 全部走资源管理器；`<canvas>` 的 `drawImage` 直接走 `Loader`，不在覆盖范围 |
| 安全边界 | 服务端同步的页面是可执行内容；脚本由 KubeJS 执行，CSS/图片可引用远程 URL。接受服务端页面前必须定信任策略 |

这个方案不依赖 HUD，也不依赖 `AuiResourceService` 包装器；任何 AUI 页面都可以用同一套路径。

### 5.4 AUI 资源服务包装器（备选）

如果不想依赖 Spark 虚拟资源包的生命周期，可以在客户端安装一个 `AuiResourceService` 委托包装器：

```java
AuiServices.setResources(new PackAwareResourceService(AuiServices.resources()));
```

包装器把 `apricity/<key>` 先映射到内容包页面表，未命中再委托原服务；页面正文仍需客户端模板安装器调用 `HTML.putTemple(pageKey, html)`，因为 `Document.create` 不读 `openResource`。

- 优点：不依赖资源包命名空间；服务端同步的页面加入后立即可用；路径可以按内容包命名空间组织。
- 成本：要实现 `AuiResourceService` 的六个方法；要额外维护模板注入；`<canvas>` 覆盖不到。
- 适用：需要自定义寻址、按来源优先级覆盖，或虚拟资源包的命名空间种子被证明不可靠时。

### 5.5 写入 `<gamedir>/apricity/`

把内容包页面写到 `<gamedir>/apricity/<path>`，AUI 的文件系统分支会优先命中。

- 优点：完全不碰 AUI 内部。
- 限制：客户端磁盘写入、清理、路径穿越、多人服务器内容落盘都是额外风险；只适合本地开发或调试，不作为内容包投递方案。

### 5.6 直接在 `Document` 节点上注入 CSS/JS

在页面创建后 `createElement("style")`、`createElement("script")` 或直接调用 `AuiServices.script().eval` 可以把一段 CSS/JS 塞进某个文档。

- 适用范围：单个文档的局部样式或脚本。
- 不适用：页面资源管线。它解决不了 `<img>`、`@font-face`、`<audio>` 的路径解析；每个页面都要重复注入；页面刷新/重建后要重新挂；还会绕过 AUI 的 `StyleAsyncHandler` 缓存与 `Document` 生命周期。
- 结论：不作为通用方案；只适合调试或极小的内联页。

## 六、推荐实现（第二阶段）

### 6.1 分层

| 层 | 位置 | 职责 |
| --- | --- | --- |
| 通用页面模块 | `common/resource/modules/AuiPageModule.kt` | 读取 `aui_pages/` 下的全部文件，按 `namespace/path` 存入共享表；不引用 AUI 类 |
| 共享表 | `external/AuiPageStore.java` | 不可变快照；`onStart` 清空；`onFinish` 发布；客户端异步读取安全 |
| 客户端钩子 | `AuiPageSink`（共通接口） | 共通模块在 `onFinish` 调用；客户端实现负责把共享表写进 `CLIENT_PACK` 并触发扫描 |
| 客户端注入器 | `client/.../AuiPageResourcePack.java` | 写入 `apricityui:apricity/<key>`；种子 `.keep`；`HTML.scan()`；缓存失效 |
| 客户端安装 | `MachineMaxClient` 或 `ClientSetup` | 注册注入器、设置 `AuiPageSink`、在 `FMLClientSetupEvent` 标记 AUI 已就绪 |

共通模块不能直接 import `com.sighs.apricityui.parser.HTML` 或 `SparkPackLoaderApplier.CLIENT_PACK` 的客户端实现；页面表与钩子放在共通侧，AUI/资源包写入放在 `client/` 包。这样专用服务器加载共通模块时不会触碰客户端类。

### 6.2 生命周期

1. 客户端启动：Spark 预加载本地内容包，`AuiPageModule.read` 填充共享表；`AuiPageResourcePack` 写入 `CLIENT_PACK`。AUI 的 `FMLClientSetupEvent` 扫描会读到这些页面。
2. 连接服务器：`SparkPackagePayload` 在客户端线程调用 `readPackageContent(true, true)`；模块 `onFinish` 触发 `AuiPageSink`，注入器写入新页面，调用 `HTML.scan()` 与 `invalidateStaticResourceCache()`。
3. 页面首次显示：`HudStyle.page` 或手册页路径对应的模板已在表中；`Document.create` 成功；CSS/JS/图片按相对路径走资源管理器。
4. `/reload` 或服务器切换：`onStart` 清空共享表；`onFinish` 重建并注入。`CLIENT_PACK` 与 `HTML.temples` 没有清空 API，旧条目会留到退出游戏；页面路径带版本或包名可规避。

### 6.3 路径约定

- 内容包布局：`spark_modules/<pack>/<namespace>/aui_pages/<path>`。
- 共享表键与 AUI 页面路径：`<namespace>/<path>`，例如 `machine_max/hud/speed.html`、`machine_max/manual/tank.html`。
- 虚拟资源包路径：`apricityui:apricity/<namespace>/<path>`。
- `HudStyle.page`、手册页路径等消费者字段都使用 `<namespace>/<path>`，与共享表键相同。
- 页面内相对引用：`./speed.css`、`../shared/font.ttf`，由 AUI 的 `Loader.resolve` 基于页面路径解析。
- 页面引用的图片、字体、音频放在同一个 `aui_pages` 模块下；`textures`、`font` 模块进入的是 Minecraft 的贴图/字体命名空间，不是 AUI 的 `apricity/` 页面根。
- 注入器拒绝 `..` 逃出根、反斜杠、空白和空路径；只接受已登记的后缀（`.html`、`.css`、`.js`、`.png`、`.jpg`、`.webp`、`.ttf`、`.otf`、`.ogg`、`.wav` 等）。

### 6.4 线程与缓存

- `AuiPageModule.read` 与 `onFinish` 在客户端线程执行；共享表用 `volatile` 快照或不可变 Map。
- `CLIENT_PACK.put` 在客户端线程调用；`SparkVirtualResourcePack` 的内部 `EnumMap`/`MutableMap` 不是并发容器。服务端页面在配置阶段、世界创建前一次写入，此时没有 AUI 页面在读；若将来在游戏内热重载，需要给包加并发保护，或改用不可变快照的 `AuiResourceService` 包装器。
- `HTML.scan()` 只在客户端主线程调用；它会重建 `BLUEPRINTS`，但不清空 `temples`。
- `ClientLoader.invalidateStaticResourceCache()` 在每次注入后调用。
- 页面已经打开时，内容包重载后需要让对应 `Document` 重新解析；`Document.refresh()` 会重新读 `HTML.getTemple`，但要由页面宿主按需触发，不能每 tick 调用。

## 七、边界与风险

### 7.1 安全性

内容包页面是**可执行内容**，不只是数据：

- AUI 会执行 HTML 内的脚本；脚本由 KubeJS 提供，未安装 KubeJS 时静默不执行，安装后由 KubeJS/Rhino 执行，实际能力取决于 KubeJS 暴露的接口。
- CSS 可以引用 `https://` 资源，图片/音频加载器会发起网络请求；`<iframe>` 还涉及 WebView2 的本地/远程内容。
- 服务端可以把内容包同步给客户端。若默认接受服务端页面，等于让服务器决定客户端 UI 内容与远程请求。
- 这条管线是通用的，服务端页面不只影响 HUD，还能影响手册、PDA、研究页等所有 AUI 页面；信任边界比 HUD 专用管线更宽。

第一阶段只使用 JAR 内页面，没有这个问题。第二阶段若接受服务端页面，至少要在设计里明确：是否只信任本地内容包、是否剥离 `<script>`/内联事件、是否禁止远程 URL、是否要求客户端显式开启。本文不替产品做这个决定，但不能把“资源包可写”当成“可以无条件接受任意服务器 HTML”。

### 7.2 命名空间种子

`MultiPackResourceManager` 在构造时按当时的命名空间建立 `FallbackResourceManager`，并把当时的 pack 推入各命名空间。`CLIENT_PACK` 如果在该时刻没有 `apricityui` 命名空间，就不会被推入 `apricityui` 的 fallback 列表；连接服务器后新增的页面即使写进了包，`listResources` 也看不到。

种子 `.keep` 必须在这个构造之前写入。客户端启动时本地内容包已经带页面时，页面本身就会提供 `apricityui` 命名空间；种子覆盖“只有服务端页面”的情况。

### 7.3 `<canvas>` 图片

AUI 的普通 `<img>`、CSS `background-image`、`@font-face`、`<audio>` 都经 `ClientLoader.getResourceStream`，资源包注入能覆盖。`CanvasImageSupport` 在把 `Img` 或字符串路径画进 canvas 时直接调用 `Loader.getResourceStream`，绕过了资源管理器；这类图片只能来自开发目录、`<gamedir>/apricity/`、AUI 类路径或原版资源包。HUD 或手册页面避免用 canvas 绘制内容包图片，或后续给 AUI 提一个统一的资源打开入口。

### 7.4 模板表与资源包没有清空 API

`HTML.temples` 是私有 `HashMap`，公开入口 `putTemple` / `getTemple` / `reload(path)` / `scan()` 都没有清空整张表的方法；`scan()` 只写入不删除。`SparkVirtualResourcePack` 也没有清空 API。内容包删除或改名页面后，旧模板与旧资源会留到退出游戏。页面路径带版本号（如 `manual/tank.v2.html`）或每次发布新路径可以规避；不要依赖“重载后旧页面消失”。

### 7.5 网络载荷

HTML/CSS/JS 通常是几十 KB，随 `SparkPackagePayload` 发送没有问题。图片和字体可能很大；现有 `textures`、`font` 模块是 `LOCAL_ONLY`/`CLIENT_LOCAL_ONLY`，不会随服务器包发送。若让服务端内容包携带大图，需要单独评估载荷大小，或改用客户端资源包分发。

### 7.6 子目录

现有 `HudModule` 只按 `fileName` 生成 RL，`pathSegments` 被丢弃；新的 `AuiPageModule` 必须拼完整路径。否则 `aui_pages/manual/sub/a.html` 与 `aui_pages/manual/a.html` 会撞键，页面路径无法稳定寻址。

## 八、验证计划

在实现第二阶段前，用以下检查逐项验证：

| 检查 | 期望 |
| --- | --- |
| 启动后打开现有 JAR 内页面 | 页面正常显示，不出现模板缺失 |
| 本地 `spark_modules` 内容包提供 `aui_pages/manual/foo.html` | 首次进入世界即可显示；日志无 `template is missing` |
| 专用服务器提供同页面，客户端连接后显示 | 配置阶段同步后页面可创建；无需重启客户端 |
| 只有服务端页面、客户端没有任何本地 `apricityui` 资源 | 种子 `.keep` 生效；页面仍可见 |
| 页面引用 `./foo.css`、`./bg.png` | 样式与图片加载成功；日志无资源缺失 |
| 页面引用 `@font-face` | 字体注册成功，文本度量正确 |
| 删除内容包后重进 | 新页面不引用旧路径；不崩溃 |
| 页面路径含 `../` 或反斜杠 | 注入器拒绝并给出可定位日志 |
| 专用服务器启动 | 共通模块不加载 AUI 客户端类，不抛 `invalid dist DEDICATED_SERVER` |

## 九、证据索引

| 事实 | 源码位置 |
| --- | --- |
| 模板写入/读取 | `AUI/common/src/main/java/com/sighs/apricityui/parser/HTML.java:38`、`:53` |
| HTML 扫描 | 同文件 `:65` |
| 文档创建前检查模板 | `AUI/common/src/main/java/com/sighs/apricityui/dom/DocumentRegistry.java:53` |
| 文件系统/类路径顺序 | `AUI/common/src/main/java/com/sighs/apricityui/loader/Loader.java:46`、`:63`、`:70` |
| 资源包回退 | `AUI/common/src/main/java/com/sighs/apricityui/loader/ClientLoader.java:161`、`:168` |
| AUI 初始扫描时机 | `AUI/targets/neoforge-1.21.1/src/main/java/com/sighs/apricityui/loader/ClientLoaderForge.java:25` |
| 资源服务 setter | `AUI/common/src/main/java/com/sighs/apricityui/spi/AuiServices.java:66` |
| 默认命名空间 | `AUI/targets/neoforge-1.21.1/src/main/java/com/sighs/apricityui/neoforge/ResourceService.java:97` |
| 脚本执行 | `AUI/common/src/main/java/com/sighs/apricityui/init/Document.java:443` |
| 脚本由 KubeJS 执行 | `AUI/targets/neoforge-1.21.1/src/main/java/com/sighs/apricityui/neoforge/ScriptService.java:20` |
| Spark 内容包读取 | `Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/SparkPackLoader.kt:64` |
| Spark 服务端同步 | `Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/sync/SparkPackagePayload.kt:23` |
| Spark 虚拟客户端资源包 | `Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/SparkVirtualResourcePack.kt:20`、`:40` |
| 虚拟包注册 | `Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/SparkPackLoaderApplier.kt:27`、`:57` |
| 模块读取模式 | `Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/modules/ReadMode.kt:3` |
| 现有 HUD 模块 | `Machine-Max/src/main/kotlin/io/github/sweetzonzi/machine_max/common/resource/modules/HudModule.kt:33` |

1.21.1 源码侧的补充核对：

- `MultiPackResourceManager` 构造时按命名空间建立 `FallbackResourceManager`，并只把当时 `getNamespaces` 包含该命名空间的 pack 推入。
- `FallbackResourceManager.listResources` 每次遍历当前 fallback 列表并调用 pack 的 `listResources`，因此已推入的 pack 在构造后新增的条目可以被看到。
- `BuiltInPackSource.fixedResources` 返回同一个 `PackResources` 实例，因此 `CLIENT_PACK` 在构造后的写入不会被快照丢弃。

## 十、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-18 | 调查内容包提供 AUI 页面的可行路径；确认 `AuiResourceService` 只覆盖资源读取，HTML 必须经 `HTML.putTemple` 进入模板表；给出 Spark 内容包模块 + 资源服务包装器 + 模板安装器的推荐组合与安全/缓存/线程边界 |
| 1.1 | 2026-10-18 | 改为通用 AUI 页面管线：推荐 Spark 虚拟客户端资源包 `CLIENT_PACK` 注入 `apricityui:apricity/<key>`，HUD/手册/PDA 共用；补命名空间种子、服务端同步后 `HTML.scan`、资源包无清空 API 与直接 Document 节点注入的边界；`AuiResourceService` 包装器降为备选 |
