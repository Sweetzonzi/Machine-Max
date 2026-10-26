# spark 命令速查（1.10.x，可直接复制）

本文件用于**给用户生成可直接粘贴的指令**。所有参数均从 `spark-1.10.124-neoforge.jar` 的 `SamplerModule` 字节码核对，不是按文档猜测。

## 命令入口

- 主命令 `/spark`；`profiler` 可写作 `sampler`（别名）。
- `/spark profiler` 不带子命令等价于 `start`（分发器在无匹配子命令时回落到 `profilerStart`）。
- `/spark profiler stop` 另有旧别名 `upload`。
- **客户端**：`/spark` 由客户端 mod 本地执行（`NeoForgeClientCommandSender`），因此可以在多人服务器上采自己的渲染线程，不会发给服务器。
- **服务端**：需要 OP，权限节点 `spark.profiler`（spark 的节点由 `spark.` + 模块名拼成）。权限不足时回显 `You do not have permission to use this command.`。
- **单人 / 局域网内置服务器是同一个 JVM**：客户端线程与服务端线程共处一个进程，**一条 `--thread *` 就能同时覆盖 `Render thread` 与 `Server thread`**，不需要采两次。
- **只有专用服务器（dedicated server）才需要两端各采一次**：此时服务端与客户端是两个独立进程，各自命令只覆盖本进程。

## `start` 参数表

| 参数 | 取值 | 默认 | 说明 |
| --- | --- | --- | --- |
| `--timeout <秒>` | 整数 | 不限 | `<10` 直接被拒（"not long enough"）；`<30` 会提示精度不足 |
| `--thread <列表>` | 逗号分隔的线程名 | 游戏主线程（服务端 `Server thread` / 客户端 `Render thread`） | 传 `*` 表示**所有线程** |
| `--regex` | 标志 | 关 | 把 `--thread` 的每一项当 Java 正则 |
| `--interval <毫秒>` | 可为小数 | `4` | `≤0` 时回落到当前模式默认值 |
| `--only-ticks-over <毫秒>` | 整数 | 关 | 只记录超阈值的 tick，需服务端 tick hook |
| `--combine-all` | 标志 | 关 | 所有线程合并为一条（`AS_ONE`） |
| `--not-combined` | 标志 | 关 | 按线程名分组（`BY_NAME`） |
| `--ignore-sleeping` | 标志 | 关 | 忽略休眠线程 |
| `--alloc` | 标志 | 关 | 分配模式（数值单位变为字节） |
| `--alloc-live-only` | 标志 | 关 | 分配模式只统计存活对象 |
| `--force-java-sampler` | 标志 | 关 | 强制使用内置 Java 采样器 |
| `--save-to-file` | 标志 | 关 | 结束时保存到本地，不上传 |

**默认线程分组是 `BY_POOL`**（同线程池合并，标签显示为 `Worker-Main (x31)`）。

**关键差异**：不写 `--thread` ≠ 所有线程。spark 的默认 dumper 是 `NeoForgeServerSparkPlugin` / `NeoForgeClientSparkPlugin` 在运行时用实际游戏线程构造的 `Specific`，即服务端只有 `Server thread`、客户端只有 `Render thread`。要全量必须显式 `--thread *`。

## `stop` 参数表

| 参数 | 说明 |
| --- | --- |
| `--save-to-file` | 保存到本地，不上传 |
| `--comment <文本>` | 备注，写入报告 `metadata.comment` |

`start` 上的 `--save-to-file` 只对**超时自动结束**生效；手动 `stop` 必须再带一次。

## 常见线程名（实测样本）

以下名字取自一次 NeoForge 1.21.1 模组实机采样（6 份报告、115 个唯一线程名，含 Sodium / Flywheel / Distant Horizons / KubeJS）。**名字随整合包与版本变化**，可直接用于 `--thread`，但换环境后应先按本文末尾的方式枚举确认。

| 类别 | 线程名 | 说明 |
| --- | --- | --- |
| 游戏主线程 | `Server thread` / `Render thread` | 服务端逻辑 / 客户端渲染，最常采这两个 |
| 区块渲染 | `Chunk Render Task Executor` | Sodium 区块构建池 |
| 渲染 | `Flywheel Task Executor` | Flywheel 并行任务池 |
| 声音 | `Sound engine` | 音频线程 |
| 物理 | `Server PhysicsThread`、`Client PhysicsThread` | Spark-Core / Bullet 物理线程 |
| 物理地形 | `TerrainShapeBuilder-Server PhysicsThread`、`TerrainShapeBuilder-Client PhysicsThread` | 地形碰撞体构建 |
| 通用池 | `Worker-Main`、`IO-Worker`、`Download`、`ForkJoinPool.commonPool-worker`、`modloading-worker` | 名称后带序号（`Worker-Main-1`） |
| 网络 | `Netty Server IO`、`Netty Local Client IO` | 连接与包处理 |
| JVM 内部 | `Common-Cleaner`、`Cleaner`、`Reference Handler`、`Finalizer`、`Signal Dispatcher`、`Attach Listener`、`Notification Thread`、`Timer hack thread` | 通常与卡顿无关，排查时可忽略 |
| 模组自有 | `ApricityUI-Timer`、`ApricityUI-StyleWorker`、`kubejs-background-thread`、`JEI Config File Watcher`、`Auto-Sync thread`、`entity-deserializer` | 示例样本中的模组线程，实际以你的实例为准 |
| Distant Horizons | `DH-*`（如 `DH-LOD Builder Thread[0]`、`DH-Render Loader Thread[0]`） | 数量多且带 `[n]` 后缀 |
| spark 自身 | `spark-java-sampler-1`、`spark-monitoring-thread`、`spark-worker-pool-*` | 采样器开销，不要当成业务热点 |

### 使用这些名字的三个前提

1. **`(xN)` 不是线程名的一部分。** 那是 spark 的 `BY_POOL` 分组标签（如 `Worker-Main (x31)` 表示 31 个线程被合并）。真实名是 `Worker-Main-1`、`Worker-Main-2`，直接抄 `(x31)` 匹配不到。
2. **`--thread` 是精确名匹配、大小写不敏感。** 线程池成员带序号，想一次覆盖必须用正则：`--thread "Worker-Main.*" --regex`。正则用 `Matcher.matches()` 全串匹配，所以 `Worker-Main` 不如 `Worker-Main.*`，开头也要按需补 `.*`。
3. **单人存档里客户端与服务端同进程。** 报告会同时出现 `Render thread` 与 `Server thread`，因此「服务端」报告里也能看到渲染帧——判断依据是线程名，不是 profile 的 platform 字段；也正因如此，单人模式下一条 `--thread *` 就能同时采到两端。

不确定名字时，先枚举再指定：

```bash
$PY <技能目录>/scripts/sparkprofile.py tree <报告文件> --depth 0 --top 100
```

## 需求 → 指令

### 全量采集：所有线程 60 秒（单人 / 内置服务器）

单人存档或局域网内置服务器只有**一条命令**，客户端与服务端线程在同一 JVM 中，一份报告同时包含 `Render thread`、`Server thread`、物理与工作线程：

```
/spark profiler start --thread * --timeout 60 --save-to-file
```

保存到 `<游戏目录>/config/spark/profile-<时间>.sparkprofile`。

### 全量采集：专用服务器（dedicated server）

专用服务器是独立进程，才需要两端各采一次：

- 服务端控制台 / OP 执行（得到服务端全部线程）：
  ```
  /spark profiler start --thread * --timeout 60 --save-to-file
  ```
- 需要客户端侧数据时，每台客户端在游戏内各自执行同一条命令；客户端那份只含客户端进程线程。

两份报告分别解析，不要期望一份覆盖另一端。

### 服务端只采主线程 60 秒

```
/spark profiler start --timeout 60 --save-to-file
```

省略 `--thread` 即默认的 `Server thread`，报告体积最小、信噪比最高。单人模式下同样适用，此时看不到渲染帧属于预期。

### 客户端只采渲染线程 60 秒

```
/spark profiler start --thread "Render thread" --timeout 60 --save-to-file
```

线程名含空格，必须加引号。

### 只抓卡顿 tick（120 秒，阈值 50 ms）

```
/spark profiler start --thread * --only-ticks-over 50 --timeout 120 --save-to-file
```

### 分配热点 / GC 压力

```
/spark profiler start --alloc --thread * --timeout 60 --save-to-file
```

### 高精度长采样（1 ms 间隔，5 分钟）

```
/spark profiler start --thread * --interval 1 --timeout 300 --save-to-file
```

### 不确定时长：手动启停

```
/spark profiler start --thread * --save-to-file
/spark profiler stop --comment "刷出载具后" --save-to-file
```

### 过程中实时预览，最后仍落盘

```
/spark profiler start --thread * --timeout 120 --save-to-file
/spark profiler open
```

客户端预览若提示 `Please provide a client id with '--id <client id>'`，按提示补 `--id`。

### 放弃本次结果

```
/spark profiler cancel
```

### 查看当前会话状态

```
/spark profiler info
```

## 结束后

1. 文件位置：客户端 `<游戏目录>/config/spark/`；专用服务器 `<服务端目录>/config/spark/`。
2. 命名：`profile-<yyyy-MM-dd_HH.mm.ss>.sparkprofile`，不会覆盖既有文件。
3. 交给本技能解析：

```bash
python <技能目录>/scripts/sparkprofile.py report <报告文件> \
  --thread "Server thread" --no-idle -o spark-report.md
```

## 生成指令时的规则

1. 用户说「N 秒」→ `--timeout N`；说「所有线程 / 双端」→ `--thread *`。
2. 用户没提上传时**默认加 `--save-to-file`**，否则报告只会变成一个网页链接，本地没有文件可离线解析。
3. 用户说「双端 / 客户端+服务端」时**先判断部署形态**：单人、局域网内置服务器只给一条 `--thread *` 命令（同一 JVM，一份报告已含两端线程）；只有专用服务器才拆成服务端 + 客户端两条，并说明两份报告各自的范围。
4. 线程名取自 [常见线程名](#常见线程名实测样本) 或 `tree --depth 0` 的实际输出，不要凭空编造；含空格时加引号。
5. `--only-ticks-over` 只在服务端有效；`--alloc` 的报告数值是字节，不能和耗时比较。
6. 不要给用户 `--interval` 小于 1 的建议，采样抖动会盖过真实差异。

## 易错点

- `--thread A,B` 用**逗号**分隔，不要用空格。
- `--thread *` 在游戏内聊天安全；若通过 RCON / 控制台脚本下发，注意 shell 的 glob 展开和引号。
- `--timeout 10` 以下会被直接拒绝，别写 `--timeout 5`。
- 客户端 `Render thread` 的耗时与服务器 TPS 没有直接关系，别混为一谈。
- 自动超时结束的会话如果想中途改为落盘，只能等它结束前手动 `stop --save-to-file`，或事先在 `start` 上加该标志。
