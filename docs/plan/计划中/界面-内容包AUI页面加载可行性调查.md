# 界面-内容包 AUI 页面加载可行性调查

> **状态**：调查完成，代码未实现。结论：内容包提供 HTML/CSS 页面在本项目现有依赖上可行，不需要修改 AUI；推荐走 Spark 内容包模块 + AUI `AuiResourceService` 包装器 + `HTML.putTemple` 模板注入。第一阶段保持只使用模组 JAR 内的 `assets/apricityui/apricity/` 页面。

## 一、问题

HUD 的 AUI 载体把页面路径写在 `HudStyle.page`（模组 JAR 内的例子是 `machine_max/huds/speed_ui.html`，内容包页面的例子是 `machine_max/hud_pages/speed_ui.html`）。如果内容包作者要自带 HUD 页面，需要让这个路径在客户端解析到内容包里的 HTML；页面内的 CSS、JavaScript、图片和字体也应能按相对路径解析。

内容包在本项目中是 `spark_modules/` 下的目录或 zip，由 Spark-Core 读成内存条目并通过网络同步给客户端。它不是 Minecraft 资源包，不能直接把文件放进 `assets/`。因此要回答三件事：

1. AUI 从哪里取 HTML；
2. 内容包字节怎样进入这条取数链；
3. 只替换资源读取是否足够，还是必须同时处理模板表。

## 二、结论

| 结论 | 说明 |
| --- | --- |
| 可行 | AUI 1.2.7 暴露了 `AuiServices.setResources(AuiResourceService)`、`HTML.putTemple(String, String)`、`HTML.scan()`、`ClientLoader.invalidateStaticResourceCache()`，足够在不改 AUI 的前提下接入内容包页面 |
| 只换 `openResource` 不够 | `Document.create` 先从 `HTML.getTemple(path)` 取模板；模板不存在时直接失败，不会回退到 `AuiResourceService.openResource`（`DocumentRegistry.create` 在构造文档前检查模板） |
| 推荐组合 | 内容包模块把 `.html` 交给模板安装器写入模板表，把 `.css`/`.js`/图片等放进资源服务包装器；两者都以内容包里的相对路径为键 |
| 第一阶段可不做 | 只使用模组 JAR 的 `assets/apricityui/apricity/machine_max/...` 页面即可；现有页面已经按这条链工作 |
| 不需要 AUI 分支 | AUI 公开 API 足够；代价是要接受 `HTML` 模板表的生命周期与线程约束 |

本文以 AUI 1.2.7 为准。已用依赖 JAR 的 `javap` 核对 `putTemple` / `getTemple` / `scan` / `setResources` / `invalidateStaticResourceCache` 均存在。项目当前的 `neoforge.mods.toml` 声明 `apricityui` 版本范围从 1.2.5 起；这些入口在 1.2.5 上是否同样存在尚未核对。落地时把最低版本提到 1.2.7，或在客户端启动时探测这些入口并给出可定位的缺失日志。

## 三、AUI 的页面解析链

AUI 1.2.7 的读取顺序如下（以本地 AUI 源码副本为准）：

1. `Loader.getResourceStream(path)`：
   - 开发期资源根（`src/main/resources/assets/apricityui/apricity`、`common/src/main/resources/...`）；
   - 开发期项目根下的候选路径；
   - `<gamedir>/apricity/<path>`；
   - AUI 自身类路径的 `assets/apricityui/apricity/<path>`。
2. 以上都没有时，`ClientLoader.getResourceStream(path)` 调用 `AuiServices.resources().openResource("apricity/" + path)`，即 Minecraft 客户端 `ResourceManager` 的资源包链。
3. HTML 模板不由 `openResource` 直接读取。`ClientLoader.loadResources(HTML::putTemple)` 在启动/重载时扫描 HTML，把内容放进 `HTML.temples`；`Document.create(path)` 只检查这张表。

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

- 页面必须进入 `HTML.temples`；只实现资源服务时 `Document.create` 会在模板检查处返回 `null`。页面引用的 CSS/JS/图片只有在需要按路径读取时才需要资源服务。
- 资源包链里的页面路径需要落在 `apricityui:apricity/...`，因为 `openResource` 收到的是不带命名空间的 `apricity/<path>`，默认补 `apricityui`。`listResourcePaths` 虽然会列出其它命名空间，但随后的 `openResource` 仍按 `apricityui` 打开，二者不一致。

## 四、内容包字节怎样到达客户端

Spark-Core 的 `spark_modules` 读取器已经把内容包文件读成 `SparkPackage.entries`：

- 目录包和 zip 包都会保留相对路径与字节内容（`ReadableDirectory` / `ReadableZip`）。
- `SparkPackLoader.readPackageContent` 按模块调用 `SparkPackModule.read(namespace, pathSegments, fileName, content, ...)`。
- 服务端通过配置阶段的 `SparkPackagePayload` 把包图发给客户端；客户端在 `handleInClient` 中 `acceptRemote`、`readPackageContent(true, true)`、`injectPackageContent(true, true)`。
- 模块可以声明 `ReadMode.SERVER_TO_CLIENT`（默认，随包发送）、`LOCAL_ONLY` 或 `CLIENT_LOCAL_ONLY`；`collectRemote` 只发送 `shouldSend()` 为真的模块。

现有 `HudModule` 是可直接复用的样板：它在客户端且 `fromServer == true` 时清空 `MMDynamicRes.CUSTOM_HUD`，逐个读取 `huds/*.json`。新页面模块可以沿用同样的生命周期。

注意一个现有实现细节：`HudModule.read` 用 `fileName.removeSuffix(".json")` 生成 RL，没有把 `pathSegments` 拼回去。新页面模块必须把 `pathSegments + fileName` 完整拼成键，否则 `hud_pages/sub/foo.html` 会与 `foo.html` 撞键。

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

### 5.3 Spark 虚拟客户端资源包

Spark-Core 已有 `SparkVirtualResourcePack`，`SparkPackLoaderApplier.CLIENT_PACK` 是一个 `CLIENT_RESOURCES` 类型的虚拟包，可以在 `AddPackFindersEvent` 中注册为始终启用的内置包。内容包模块可以把任意 `assets/...` 字节放进这个包。

- 优点：复用 Minecraft 资源包链；不需要替换 AUI 资源服务；CSS/JS/图片/字体都能走同一条链。
- 限制一：路径必须落在 `apricityui:apricity/...`，否则 AUI 默认命名空间打不开。
- 限制二：`MultiPackResourceManager` 在构造时按当时的命名空间建立 `FallbackResourceManager`，并把当时的 pack 推入各命名空间。客户端启动前预加载的本地内容包会进入；**连接服务器后**才由 `SparkPackagePayload` 写入的页面，如果 `apricityui` 命名空间在构造时不存在，虚拟包不会被推入该命名空间，之后的 `listResources` 就看不到这些条目。解决方式是在注册虚拟包前放一个 `apricityui` 命名空间的占位条目，或改用 5.4。
- 限制三：`SparkVirtualResourcePack` 没有清空 API；`/reload` 后删除的页面仍留在包内，直到退出游戏。
- 限制四：AUI 的 `ClientLoader` 有静态资源缓存；服务端同步后要调用 `invalidateStaticResourceCache()`，必要时重新 `HTML.scan()`。`HTML.temples` 不会自动清除旧条目。

### 5.4 AUI 资源服务包装器 + 模板注入（推荐）

在客户端安装一个 `AuiResourceService` 委托包装器：

```java
AuiServices.setResources(new PackAwareResourceService(AuiServices.resources()));
```

包装器把 `apricity/<key>` 先映射到内容包页面/资源表，未命中再委托原服务。内容包模块读完页面后，由客户端模板安装器把 `.html` 内容用 `HTML.putTemple(pageKey, html)` 放进模板表。

- 优点：不依赖资源包命名空间；服务端同步的页面在加入后立即可用；不需要为了新页面重建整个资源包；路径可以按内容包命名空间组织。
- 必须同时做模板注入，否则 `Document.create` 失败（见 §二）。
- CSS/JS/图片/字体通过 `ClientLoader.getResourceStream` 走包装器；`<canvas>` 的 `drawImage` 是例外（见 §七）。

### 5.5 写入 `<gamedir>/apricity/`

把内容包页面写到 `<gamedir>/apricity/<path>`，AUI 的文件系统分支会优先命中。

- 优点：完全不碰 AUI 内部。
- 限制：客户端磁盘写入、清理、路径穿越、多人服务器内容落盘都是额外风险；只适合本地开发或调试，不作为内容包投递方案。

## 六、推荐实现（第二阶段）

### 6.1 分层

| 层 | 位置 | 职责 |
| --- | --- | --- |
| 内容包模块 | `common/resource/modules/HudPageModule.kt` | 读取 `hud_pages/` 下的 `.html`、`.css`、`.js` 与图片字节，按 `namespace/<module id>/path` 存入共享表；不引用 AUI 类 |
| 共享表 | `external/HudPageStore.java`（或 `MMDynamicRes` 新表） | 不可变快照；`onStart` 清空；`onFinish` 发布；客户端异步读取安全 |
| 资源服务包装器 | `client/.../PackAwareResourceService.java` | 实现 `AuiResourceService` 六个方法；`apricity/<key>` 命中共享表则返回，否则委托 |
| 模板安装器 | `client/.../HudPageInstaller.java` | 客户端主线程把 `.html` 写入 `HTML.putTemple`；必要时 `HTML.prepareTemplates()`；使 `ClientLoader` 静态缓存失效 |
| 客户端钩子 | `MachineMaxClient` 或 `ClientSetup` | 安装包装器、注册安装器；设置共通侧可调用的 `Runnable`/接口 |

共通模块不能直接 import `com.sighs.apricityui.parser.HTML`：该类的签名和实现落在 AUI 的客户端代码路径上，专用服务器加载共通模块时不应触碰它。用共通接口 + 客户端实现注入，和现有 `ClientResearchHandler` / `PdaItem.setScreenOpener` 的做法一致。

### 6.2 生命周期

1. 客户端启动：Spark 预加载本地内容包，`HudPageModule.read` 填充共享表；`HudPageInstaller` 在 AUI 的 `FMLClientSetupEvent` 扫描之前或之后都可以，只要在第一个 HUD 文档创建前执行 `putTemple`。包装器安装用普通 `FMLClientSetupEvent` 监听器或模组构造期，不要放进 `event.enqueueWork`：AUI 的首次 `ClientLoader.reloadResources()` 本身就在 enqueueWork 队列里，排在它后面的安装器会让首次扫描看不到内容包资源。
2. 连接服务器：`SparkPackagePayload` 在客户端线程调用 `readPackageContent(true, true)`；模块 `onFinish` 触发安装器，逐页 `putTemple`，再 `invalidateStaticResourceCache()`。
3. 页面首次显示：`HudStyle.page` 对应的模板已在表中；`Document.create` 成功；CSS/JS/图片按相对路径走包装器。
4. `/reload` 或服务器切换：`onStart` 清空共享表；`onFinish` 重新注入。`HTML.temples` 没有清空 API，旧页面会留在表中；用带版本或包名的路径避免旧页面被再次引用。

### 6.3 路径约定

- 共享表键：`<pack namespace>/<module id>/<module 内相对路径>`，例如 `machine_max/hud_pages/speed.html`。模组 JAR 内的页面使用资源路径 `<namespace>/<path>`，不带模块 id。
- `HudStyle.page`：与共享表键相同的字符串。
- 页面内相对引用：`./speed.css`、`../shared/font.ttf`，由 AUI 的 `Loader.resolve` 基于页面路径解析，包装器按同一键查找。
- 包装器拒绝 `..` 逃出根、反斜杠、空白和空路径；只接受已登记的后缀（`.html`、`.css`、`.js`、`.png`、`.jpg`、`.webp`、`.ttf`、`.otf`、`.ogg`、`.wav` 等），避免把内容包当成任意文件读取通道。
- 页面引用的图片、字体、音频放在同一个 `hud_pages` 模块下。`textures`、`font` 模块进入的是 Minecraft 的资源命名空间，不是 AUI 的 `apricity/` 页面根，页面路径不能直接引用它们。

### 6.4 线程与缓存

- `AuiResourceService.openResource` 会被 CSS 与图片的异步处理器调用；共享表用 `volatile` 快照或不可变 Map，重载时整体换引用。
- `HTML.putTemple` 与 `HTML.prepareTemplates` 只在客户端主线程调用；不要在异步读取线程写模板表。
- `ClientLoader.invalidateStaticResourceCache()` 在每次发布新快照后调用；否则 AUI 的静态资源清单可能仍是旧快照。
- 若页面已经打开，内容包重载后需要让对应 `Document` 重新解析；`Document.refresh()` 会重新读 `HTML.getTemple`，但要由 HUD 宿主按需触发，不能每 tick 调用。

## 七、边界与风险

### 7.1 安全性

内容包页面是**可执行内容**，不只是数据：

- AUI 会执行 HTML 内的脚本；脚本由 KubeJS 提供，未安装 KubeJS 时静默不执行，安装后由 KubeJS/Rhino 执行，实际能力取决于 KubeJS 暴露的接口。
- CSS 可以引用 `https://` 资源，图片/音频加载器会发起网络请求；`<iframe>` 还涉及 WebView2 的本地/远程内容。
- 服务端可以把内容包同步给客户端。若默认接受服务端页面，等于让服务器决定客户端 UI 内容与远程请求。

第一阶段的“只用 JAR 内页面”没有这个问题。第二阶段若接受服务端页面，至少要在设计里明确：是否只信任本地内容包、是否剥离 `<script>`/内联事件、是否禁止远程 URL、是否要求客户端显式开启。本文不替产品做这个决定，但不能把“资源桥可用”当成“可以无条件接受任意服务器 HTML”。

### 7.2 `<canvas>` 图片

AUI 的普通 `<img>`、CSS `background-image`、`@font-face`、`<audio>` 都经 `ClientLoader.getResourceStream`，包装器能覆盖。`CanvasImageSupport` 在把 `Img` 或字符串路径画进 canvas 时直接调用 `Loader.getResourceStream`，绕过了资源服务；这类图片只能来自开发目录、`<gamedir>/apricity/`、AUI 类路径或原版资源包。HUD 页面避免用 canvas 绘制内容包图片，或后续给 AUI 提一个统一的资源打开入口。

### 7.3 模板表没有清空 API

`HTML.temples` 是私有 `HashMap`，公开入口 `putTemple` / `getTemple` / `reload(path)` / `scan()` 都没有清空整张表的方法；`scan()` 只写入不删除。内容包删除或改名页面后，旧模板会留到退出游戏。页面路径带版本号（如 `hud_pages/speed.v2.html`）或每次发布新路径可以规避；不要依赖“重载后旧页面消失”。

### 7.4 网络载荷

HTML/CSS/JS 通常是几十 KB，随 `SparkPackagePayload` 发送没有问题。图片和字体可能很大；现有 `textures`、`font` 模块是 `LOCAL_ONLY`/`CLIENT_LOCAL_ONLY`，不会随服务器包发送。若让服务端内容包携带大图，需要单独评估载荷大小，或改用客户端资源包分发。

### 7.5 `HudModule` 的子目录

现有 `HudModule` 只按 `fileName` 生成 RL，`pathSegments` 被丢弃；新页面模块必须拼完整路径。否则 `hud_pages/sub/a.html` 与 `hud_pages/a.html` 会撞键，且 `HudStyle.page` 无法稳定寻址。

## 八、验证计划

在实现第二阶段前，用以下检查逐项验证：

| 检查 | 期望 |
| --- | --- |
| 启动后打开现有 JAR 内页面 | 页面正常显示，不出现模板缺失 |
| 本地 `spark_modules` 内容包提供 `hud_pages/foo.html` | 首次进入世界即可显示；日志无 `template is missing` |
| 专用服务器提供同页面，客户端连接后显示 | 配置阶段同步后页面可创建；无需重启客户端 |
| 页面引用 `./foo.css`、`./bg.png` | 样式与图片加载成功；日志无资源缺失 |
| 页面引用 `@font-face` | 字体注册成功，文本度量正确 |
| 删除内容包后重进 | 新 HUD 不引用旧页面；不崩溃 |
| 页面路径含 `../` 或反斜杠 | 包装器拒绝并给出可定位日志 |
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

## 十、修订记录

| 版本 | 日期 | 内容 |
| --- | --- | --- |
| 1.0 | 2026-10-18 | 调查内容包提供 AUI 页面的可行路径；确认 `AuiResourceService` 只覆盖资源读取，HTML 必须经 `HTML.putTemple` 进入模板表；给出 Spark 内容包模块 + 资源服务包装器 + 模板安装器的推荐组合与安全/缓存/线程边界 |
