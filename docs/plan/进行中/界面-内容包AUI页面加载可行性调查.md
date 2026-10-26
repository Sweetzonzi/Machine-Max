# 界面-内容包 AUI 页面与资源加载可行性调查

> **状态**：`DocumentModule` 已落地，待客户端实机验收。结论：内容包提供 AUI 页面与资源可行，且不需要修改 AUI。`DocumentModule` 像 `TextureModule` 一样，在客户端把内容包 `documents/` 直接写进 Spark-Core 已有的虚拟客户端资源包 `CLIENT_PACK`，路径为 `apricityui:apricity/<key>`，由 AUI 的 `ResourceManager` 分支读取；AUI 的资源扫描/自动重载负责发现 HTML。HUD、载具手册、PDA、研究页共用这一条通用管线。第一阶段只使用模组 JAR 内的 `assets/apricityui/apricity/` 页面。
>
> **版本基准**：本文的 AUI 接口核对基于 1.2.7。项目当前构建依赖 1.2.8，下列入口与路径尚未按 1.2.8 逐项复核。

## 一、问题

AUI 页面不只服务 HUD：载具说明手册、PDA 页面、研究页、控制面板都可能由内容包提供。需要一条通用管线，让内容包里的 HTML、CSS、JavaScript、图片、字体和音频在客户端按路径解析；HUD 只是这条管线的消费者之一。

内容包在本项目中是 `spark_modules/` 下的目录或 zip，由 Spark-Core 读成内存条目。它不是 Minecraft 资源包，不能直接把文件放进 `assets/`。文档属于客户端资源，不要求服务端同步：客户端本地的 `spark_modules` 就是来源。因此要回答：

1. AUI 从哪里取 HTML 与页面资源；
2. 内容包字节怎样进入这条取数链；
3. 有没有比“替换 AUI 资源服务”更简单的通用注入点。

## 二、结论

| 结论 | 说明 |
| --- | --- |
| 可行 | AUI 1.2.7 的 `ResourceManager` 分支会读取客户端资源包；Spark-Core 已有可写的虚拟客户端资源包 `CLIENT_PACK`，可以把内容包字节注入进去 |
| AUI 没有逐资源注册 API | `HTML.putTemple` 只注册 HTML 模板；CSS、JS、图片、字体、音频没有对应的公开注册入口。可以整体替换资源服务（`AuiServices.setResources`），但直接改 AUI 内部缓存既覆盖不全，也会漏掉异步加载器 |
| 推荐通用注入点 | `SparkPackLoaderApplier.CLIENT_PACK.put(PackType.CLIENT_RESOURCES, ResourceLocation.fromNamespaceAndPath("apricityui", "apricity/" + key), bytes)`；AUI 的 `ClientLoader` 会按现有链读取 |
| 关键前提 | `apricityui` 命名空间必须在 `MultiPackResourceManager` 构造前就存在于 `CLIENT_PACK`，否则之后通过 `/reload` 新增的页面不会被 `FallbackResourceManager` 列出 |
| 页面发现 | 常规发现由 AUI 的资源扫描/自动重载完成；`DocumentModule.onFinish` 在客户端再调用一次 `HTML.scan()` 作为保险。`HTML` 属于 AUI 的 common 包，不是 Minecraft 客户端类，客户端分支直接调用是安全的 |
| 同步边界 | `DocumentModule` 使用 `ReadMode.LOCAL_ONLY`：客户端读取本地内容包，服务端不向客户端发送文档；文档被视为客户端表现资源，不影响游戏性 |
| 备选 | `AuiServices.setResources(AuiResourceService)` 包装器 + `HTML.putTemple`；适合需要自定义寻址或不想依赖资源包生命周期时 |
| 第一阶段可不做 | 只使用模组 JAR 的 `assets/apricityui/apricity/machine_max/...` 页面即可；现有页面已经按这条链工作 |
| 不需要 AUI 分支 | AUI 公开 API 与 Spark 虚拟资源包足够；代价是接受资源包与模板表的生命周期约束 |

本文的接口核对基准是 AUI 1.2.7。已用依赖 JAR 的 `javap` 核对 `putTemple` / `getTemple` / `scan` / `setResources` / `invalidateStaticResourceCache` 均存在。项目构建依赖与 `neoforge.mods.toml` 的最低版本均为 1.2.8；这些入口在 1.2.8 上尚未逐项复核，运行时应在客户端启动阶段探测并给出可定位的缺失日志。

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

- 页面必须进入 `HTML.temples`；AUI 的资源扫描必须运行过，`Document.create` 才能从模板表取到页面。
- 资源包链里的页面路径需要落在 `apricityui:apricity/...`，因为 `openResource` 收到的是不带命名空间的 `apricity/<path>`，默认补 `apricityui`。`listResourcePaths` 会列出其它命名空间，但随后的 `openResource` 仍按 `apricityui` 打开，二者不一致。

## 四、内容包字节怎样进入资源包

Spark-Core 的 `spark_modules` 读取器已经把内容包文件读成 `SparkPackage.entries`：

- 目录包和 zip 包都会保留相对路径与字节内容（`ReadableDirectory` / `ReadableZip`）。
- `SparkPackLoader.readPackageContent` 按模块调用 `SparkPackModule.read(namespace, pathSegments, fileName, content, ...)`。
- `DocumentModule` 声明 `ReadMode.LOCAL_ONLY`：客户端读取本地内容包，服务端不会把文档发给客户端。
- `read` 在客户端直接 `CLIENT_PACK.put(...)`，与 `TextureModule` 写贴图的方式一致；不需要中间表，也不需要额外的资源包类。

`HudModule` 是服务端到客户端的模块，`DocumentModule` 是客户端本地模块，两者的 `ReadMode` 不同。`HudModule.read` 用 `fileName.removeSuffix(".json")` 生成 RL，没有把 `pathSegments` 拼回去；`DocumentModule` 必须拼完整路径，否则子目录页面会撞键。

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

例如内容包 `spark_modules/my_pack/machine_max/documents/manual/tank.html` 映射为：

```text
apricityui:apricity/machine_max/manual/tank.html
```

AUI 收到的键是 `machine_max/manual/tank.html`，页面内的 `./tank.css` 解析为 `machine_max/manual/tank.css`，同一个包里的 `apricityui:apricity/machine_max/manual/tank.css` 会被现有 `ResourceManager` 找到。

落地要点：

| 项 | 要求 |
| --- | --- |
| 命名空间种子 | 在资源包注册前写入 `apricityui:apricity/.keep`（空字节即可），让 `CLIENT_PACK.getNamespaces(CLIENT_RESOURCES)` 在资源管理器构造时包含 `apricityui`。没有这个种子，之后通过 `/reload` 新增的页面不会进入该命名空间的 `FallbackResourceManager` |
| 注入时机 | 客户端读取本地内容包时，在 `DocumentModule.read` 中直接写入 `CLIENT_PACK`；启动阶段发生在资源包构造前，`/reload` 阶段发生在 AUI 的资源重载前 |
| HTML 模板 | `DocumentModule.onFinish` 在客户端调用 `HTML.scan()`；AUI 的资源扫描/自动重载仍负责常规发现，重复扫描不会破坏模板表 |
| 缓存 | 不需要本模组手动失效；AUI 的资源重载会处理自己的静态清单 |
| 资源覆盖 | AUI 的资源扫描顺序是资源包 → `<gamedir>/apricity` → 开发目录；本地覆盖文件优先 |
| 生命周期 | `SparkVirtualResourcePack` 没有清空 API；删除或改名的页面会留到退出游戏。页面路径带版本或包名可规避 |
| 资源范围 | HTML/CSS/JS/`<img>`/`@font-face`/`<audio>` 全部走资源管理器；`<canvas>` 的 `drawImage` 直接走 `Loader`，不在覆盖范围 |
| 安全边界 | 文档是客户端本地内容包，安装者即信任来源；脚本仍由 KubeJS 执行，CSS/图片仍可引用远程 URL |

这个方案不依赖 HUD，也不依赖 `AuiResourceService` 包装器；任何 AUI 页面都可以用同一套路径。

### 5.4 AUI 资源服务包装器（备选）

如果不想依赖 Spark 虚拟资源包的生命周期，可以在客户端安装一个 `AuiResourceService` 委托包装器：

```java
AuiServices.setResources(new PackAwareResourceService(AuiServices.resources()));
```

包装器把 `apricity/<key>` 先映射到内容包页面表，未命中再委托原服务；页面正文仍需客户端模板安装器调用 `HTML.putTemple(pageKey, html)`，因为 `Document.create` 不读 `openResource`。

- 优点：不依赖资源包命名空间；客户端本地页面加入后立即可用；路径可以按内容包命名空间组织。
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
| 通用页面模块 | `common/resource/modules/DocumentModule.kt` | 读取客户端本地 `documents/`，规范化成 `namespace/path` 后直接写 `CLIENT_PACK`；不引用 AUI 类 |

`DocumentModule` 像 `TextureModule` 一样直接引用 Spark 的 `CLIENT_PACK`，因此不需要共通桥、客户端接收器或额外安装钩子。它只在 `isClientSide` 时写入；专用服务器加载该类时不会触碰 AUI 客户端类。

`DocumentModule` 使用 `ReadMode.LOCAL_ONLY`：客户端读取本地 `spark_modules`，服务端不向客户端发送文档。文档是客户端表现资源，不参与游戏性同步。

### 6.2 生命周期

1. 客户端启动：Spark 预加载本地内容包，`DocumentModule.read` 在资源包构造前写入 `CLIENT_PACK`；AUI 的首次资源扫描会发现这些页面，`onFinish` 再调用一次 `HTML.scan()` 作为保险。
2. `/reload`：Spark 的客户端重载重新读取本地 `documents/` 并写入；AUI 的自动重载重新扫描资源，`onFinish` 的 `HTML.scan()` 再兜底一次。
3. 页面首次显示：`HudStyle.page` 或手册页路径对应的模板已在表中；`Document.create` 成功；CSS/JS/图片按相对路径走资源管理器。
4. 删除或改名页面：`CLIENT_PACK` 与 `HTML.temples` 没有清空 API，旧条目会留到退出游戏；页面路径带版本或包名可规避。

### 6.3 路径约定

- 内容包布局：`spark_modules/<pack>/<namespace>/documents/<path>`。
- AUI 页面键：`<namespace>/<path>`，例如 `machine_max/hud/speed.html`、`machine_max/manual/tank.html`。
- 虚拟资源包路径：`apricityui:apricity/<namespace>/<path>`。
- `HudStyle.page`、手册页路径等消费者字段都使用 `<namespace>/<path>`，与页面键相同。
- 页面内相对引用：`./speed.css`、`../shared/font.ttf`，由 AUI 的 `Loader.resolve` 基于页面路径解析。
- 页面引用的图片、字体、音频放在同一个 `documents` 模块下；`textures`、`font` 模块进入的是 Minecraft 的贴图/字体命名空间，不是 AUI 的 `apricity/` 页面根。
- `DocumentModule` 拒绝 `..` 逃出根、反斜杠、空白和空路径；后缀不在这里过滤，HTML、CSS、JS、图片、字体和音频都按同一套路径写入。

### 6.4 线程与缓存

- `DocumentModule.read` 在客户端线程执行；`CLIENT_PACK.put` 也在客户端线程调用。
- `SparkVirtualResourcePack` 的内部 `EnumMap`/`MutableMap` 不是并发容器；写入发生在启动预加载或客户端重载的读取阶段，AUI 的资源扫描在之后进行，二者不并行。
- `DocumentModule.onFinish` 只在客户端且有文件写入时调用 `HTML.scan()`；`HTML` 是 AUI common 包，不是 Minecraft 客户端类，调用不构成 dist 违规。常规页面发现仍由 AUI 自动重载负责。
- 本模组不调用 `ClientLoader.invalidateStaticResourceCache()`；AUI 的资源重载会处理自己的静态清单。
- 页面已经打开时，内容包重载后需要让对应 `Document` 重新解析；`Document.refresh()` 会重新读 `HTML.getTemple`，但要由页面宿主按需触发，不能每 tick 调用。

## 七、边界与风险

### 7.1 安全性

内容包页面是**可执行内容**，不只是数据：

- AUI 会执行 HTML 内的脚本；脚本由 KubeJS 提供，未安装 KubeJS 时静默不执行，安装后由 KubeJS/Rhino 执行，实际能力取决于 KubeJS 暴露的接口。
- CSS 可以引用 `https://` 资源，图片/音频加载器会发起网络请求；`<iframe>` 还涉及 WebView2 的本地/远程内容。
- 文档来自客户端本地的 `spark_modules`，安装内容包的人就是信任来源；服务端不参与这条管线。
- 这条管线是通用的，页面不只影响 HUD，还能影响手册、PDA、研究页等所有 AUI 页面。

第一阶段只使用 JAR 内页面，没有这个问题。第二阶段开启内容包页面后，脚本、远程 CSS/图片和 `<iframe>` 按客户端本地代码的信任级别处理；若将来改成服务端下发，需要重新定义信任策略。

### 7.2 命名空间种子

`MultiPackResourceManager` 在构造时按当时的命名空间建立 `FallbackResourceManager`，并把当时的 pack 推入各命名空间。`CLIENT_PACK` 如果在该时刻没有 `apricityui` 命名空间，就不会被推入 `apricityui` 的 fallback 列表；之后通过 `/reload` 新增的页面即使写进了包，`listResources` 也看不到。

种子 `.keep` 必须在这个构造之前写入。`DocumentModule.onInitialize(true)` 在 Spark 客户端预加载阶段执行，先于资源管理器构造；因此即使启动时还没有任何内容包页面，`apricityui` 命名空间也已经存在。

### 7.3 `<canvas>` 图片

AUI 的普通 `<img>`、CSS `background-image`、`@font-face`、`<audio>` 都经 `ClientLoader.getResourceStream`，资源包注入能覆盖。`CanvasImageSupport` 在把 `Img` 或字符串路径画进 canvas 时直接调用 `Loader.getResourceStream`，绕过了资源管理器；这类图片只能来自开发目录、`<gamedir>/apricity/`、AUI 类路径或原版资源包。HUD 或手册页面避免用 canvas 绘制内容包图片，或后续给 AUI 提一个统一的资源打开入口。

### 7.4 模板表与资源包没有清空 API

`HTML.temples` 是私有 `HashMap`，公开入口 `putTemple` / `getTemple` / `reload(path)` / `scan()` 都没有清空整张表的方法；`scan()` 只写入不删除。`SparkVirtualResourcePack` 也没有清空 API。内容包删除或改名页面后，旧模板与旧资源会留到退出游戏。页面路径带版本号（如 `manual/tank.v2.html`）或每次发布新路径可以规避；不要依赖“重载后旧页面消失”。

### 7.5 资源体积

文档不参与网络同步，HTML/CSS/JS 的体积只影响客户端加载。图片和字体可能很大，建议按需拆分页面资源；`textures`、`font` 模块仍走各自的命名空间，不自动成为 AUI 页面资源。

### 7.6 子目录

`HudModule` 只按 `fileName` 生成 RL，`pathSegments` 被丢弃；`DocumentModule` 把 `pathSegments + fileName` 完整拼成键。否则 `documents/manual/sub/a.html` 与 `documents/manual/a.html` 会撞键，页面路径无法稳定寻址。

## 八、验证计划

用以下检查验收：

| 检查 | 期望 |
| --- | --- |
| 启动后打开现有 JAR 内页面 | 页面正常显示，不出现模板缺失 |
| 客户端本地 `spark_modules` 提供 `documents/manual/foo.html` | 首次进入世界即可显示；日志无 `template is missing` |
| 启动时没有任何内容包页面 | 种子 `.keep` 生效；之后 `/reload` 新增页面仍可见 |
| `/reload` 后新增或修改页面 | AUI 自动重载重新扫描，`onFinish` 的 `HTML.scan()` 再兜底；页面可创建或更新 |
| 页面引用 `./foo.css`、`./bg.png` | 样式与图片加载成功；日志无资源缺失 |
| 页面引用 `@font-face` | 字体注册成功，文本度量正确 |
| 删除内容包后重进 | 新页面不引用旧路径；不崩溃 |
| 页面路径含 `../` 或反斜杠 | `DocumentModule` 拒绝并给出可定位日志 |
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
| Spark 服务端同步（本方案不启用） | `Spark-Core/src/main/kotlin/cn/solarmoon/spark_core/pack/sync/SparkPackagePayload.kt:23` |
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
| 1.2 | 2026-10-18 | `DocumentModule`、`AuiDocumentStore`、`AuiDocumentResourcePack` 落地；内容包目录改为 `documents/`；模块注册与客户端注入钩子接入；待客户端实机验收 |
| 1.3 | 2026-10-18 | 去掉独立的 `AuiDocumentStore` 与 `AuiDocumentResourcePack`：`DocumentModule` 通过 `AuiDocumentHook` 把文件直接交给客户端 `AuiDocumentScan`，后者写 `CLIENT_PACK` 并触发 `HTML.scan()`；保留共通/客户端分层，模块只负责路径与传输 |
| 1.4 | 2026-10-18 | 按客户端资源定位收敛：`DocumentModule` 使用 `ReadMode.LOCAL_ONLY` 并在 `read` 中像 `TextureModule` 一样直接写 `CLIENT_PACK`；不参与服务端同步，不需要扫描钩子或接收器，页面发现交给 AUI 的资源扫描/自动重载 |
| 1.5 | 2026-10-18 | 在 `DocumentModule.onFinish` 增加一次 `HTML.scan()` 作为保险；`HTML` 位于 AUI common 包，客户端分支直接调用，不引入额外钩子 |
