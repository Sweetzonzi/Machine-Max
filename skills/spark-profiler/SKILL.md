---
name: spark-profiler
description: 离线解析 lucko 的 spark 性能分析模组（spark-*.jar）产出的 .sparkprofile 报告，无需上传 spark.lucko.me 即可得到 Markdown 报告、调用树、方法排名与 JSON，用于定位任意 Minecraft / NeoForge / Fabric 实例（含整合包与开发环境）的 CPU 热点、卡顿来源与 TPS 下降原因。当用户提到 sparkprofile、spark 性能报告、profiler 采样、卡顿掉帧、TPS/MSPT 异常、火焰图时使用。注意与同名模组 Spark-Core（Bullet 物理封装）无关。
---

# spark 性能报告解析

> **范围声明**：本技能处理的是 lucko 的 [spark](https://github.com/lucko/spark) 性能分析模组产出的 `.sparkprofile` 文件，适用于任何 Minecraft 服务端、客户端或开发环境。它与同名的 [Spark-Core](https://github.com/SolarMoonQAQ/Spark-Core)（Bullet 物理引擎封装库）毫无关系，后者在报告中只会作为普通业务栈帧出现。

把 `.sparkprofile`（`/spark profiler` 或 `/spark sampler` 产出）离线转换为智能体可读的结构化结果。文件通常位于 `<游戏或服务端目录>/config/spark/`；Forge / NeoForge 开发环境一般是 `run/config/spark/`。

不需要联网、不需要 spark 网页、不需要启动 Minecraft，也不依赖 protobuf / protoc。

## 何时使用

| 场景 | 命令 |
| --- | --- |
| 先了解这份报告是什么 | `info` |
| 给出完整可读分析 | `report` |
| 定位某个线程里谁在耗时 | `tree` |
| 只看方法排名（扁平） | `top` |
| 看是哪个 mod / 库在占时间 | `pkg` |
| 需要程序化处理原始数据 | `json` |
| 改动前后对比 | `diff` |

## 用户要采集新数据时

用户说「录一段性能」「采 60 秒」「抓卡顿」时，不要只描述步骤，直接给出**可整条粘贴**的 `/spark` 指令。参数表与场景配方见 [命令速查](references/commands.md)，生成规则如下：

- 「N 秒」→ `--timeout N`（小于 10 会被 spark 拒绝）。
- 「所有线程 / 双端」→ `--thread *`。单人存档、局域网内置服务器的两端线程在同一 JVM，**一条命令即可**；只有专用服务器才需要服务端与客户端各采一次。
- 未明确要求上传时，一律加 `--save-to-file`，否则报告只会变成一个 `spark.lucko.me` 链接，本地没有文件可解析。
- 线程名以 [常见线程名表](references/commands.md#常见线程名实测样本) 为准，含空格要加引号；`(xN)` 是分组标签不是线程名，线程池要用 `--regex "Worker-Main.*"`。

示例（所有线程 60 秒并落盘，单人与专用服务器通用）：

```
/spark profiler start --thread * --timeout 60 --save-to-file
```

## 前置：确认文件与解释器

```bash
# 1) 找到报告文件（路径随实例类型不同）
ls <实例目录>/config/spark/*.sparkprofile
#   官方启动器： .minecraft/config/spark/
#   服务端：     <服务端根目录>/config/spark/
#   Forge/NeoForge 开发环境： run/config/spark/

# 2) 确认 Python 3.8+ 可用（脚本零第三方依赖）
python --version || py -3 --version || python3 --version
```

下文用 `$PY` 表示可用的解释器，`$TOOL` 表示本技能目录下的 `scripts/sparkprofile.py`。若环境没有 Python，可用 WorkBuddy 托管解释器：`~/.workbuddy/binaries/python/versions/<版本>/python.exe`。

## 推荐工作流

```bash
# 步骤 1：概要。确认是服务端还是客户端、模式、时长、TPS/MSPT、线程分组方式
$PY $TOOL info <报告文件>

# 步骤 2：整体报告（最常用）
$PY $TOOL report <报告文件> --thread "Server thread" --no-idle -o spark-report.md

# 步骤 3：针对可疑线程展开调用树
$PY $TOOL tree <报告文件> --thread "Server thread" --depth 14 --min-pct 1

# 步骤 4：需要精确数字或二次加工时导出 JSON
$PY $TOOL json <报告文件> --slim -o spark.json
```

**服务端/客户端 profile 强烈建议加 `--thread` + `--no-idle`。** 不加时，线程池的 `park` / `await` 会占掉 70% 以上，把真实热点淹没。

## 参数要点

- `--thread <子串>`：只统计名称包含该子串的线程。线程标签形如 `Server thread`、`Render thread`、`Worker-Main (x31)`（`(xN)` 是 spark 按线程池合并的数量，不是线程名的一部分）。
- `--no-idle`：排除 `park/await/wait/take/poll/sleep/...` 等阻塞帧，并把 total 口径改为「有效工作时间」。判断瓶颈时几乎总是需要。
- `--depth`：调用树深度，默认 12。折叠逻辑会把「单子节点且耗时相同」的链压缩成 `A → … → D`，所以可以放心调大。
- `--min-pct`：调用树中低于该百分比的子树不展开。
- `--top-n` / `--limit`：表格行数。
- `-o <path>`：写文件；省略则打印到 stdout。

## 输出解读

1. **概要**：先看 `采样模式`。`EXECUTION` 才是 CPU 时间；`ALLOCATION` 下所有数值是分配字节数；`LOCK` 是锁等待次数。三类数值不可互相比较。
2. **时间窗**：按分钟切分的 TPS / MSPT 走势。`MSPT 峰` 远高于 `MSPT 中位` 说明存在尖峰卡顿而非整体变慢，应回到对应时间窗的调用树继续查。
3. **线程排名**：谁在占时间。注意服务端空闲时间也算在线程里，务必配合 `--no-idle`。
4. **self time**：方法自身耗时，定位「真正的计算」。占比按 `--no-idle` 后的有效工作时间归一。
5. **total time**：含子调用。同一方法出现在多条路径时占比可超 100%，这是正常的，不要当成数据错误。
6. **命名空间聚合**：把类名归约到前三到四段（如 `com.example.mymod`、`com.jme3.bullet`、`java.util.concurrent`）这一级，快速判断是目标模组、依赖库还是原版在耗时。
7. **调用树**：叶子越靠下越接近真实开销；`EventBus#post` 之下才是各 mod 的监听器本体。

## 结论必须带证据

报告结论要引用具体行，例如：

> `Server thread` 60 s 中有效工作 15.96 s（26.6%）；`AnimController#tick` self 1.72 s（占有效时间 10.78%），主要经 `EventListenerFactory$entityTick → ModelPose#getBonePoseOrCreateEmp...` 进入。

不要在没有数字支撑时断言「某个 mod 卡顿」。

## 让 spark 不要上传到网页

`/spark profiler stop` 默认走 bytebin 上传，只返回一个 `spark.lucko.me/<key>` 链接，本地不留文件。要拿到可解析的 `.sparkprofile`：

| 目标 | 命令 |
| --- | --- |
| 手动结束时保存到本地 | `/spark profiler stop --save-to-file` |
| 定时自动结束时也保存到本地 | `/spark profiler start --timeout 60 --save-to-file` |
| 放弃本次结果 | `/spark profiler cancel` |

`--save-to-file` 在 spark 1.10.x 上同时被 `profiler start` 与 `profiler stop` 接受：前者把标志捕获进超时回调，后者在停止时直接落盘。文件写到 `<游戏目录>/config/spark/profile-<yyyy-MM-dd_HH.mm.ss>.sparkprofile`，不会产生任何 URL。

**没有官方的「永久关闭上传」配置项。** `config/spark/config.json` 只支持 `backgroundProfiler`、`bytebinUrl`、`viewerUrl`、`bytesocksHost`、`disableResponseBroadcast`。可行的替代做法是把 `bytebinUrl` 指向不可达地址（如 `http://127.0.0.1:1/`），上传失败后 spark 会自动回退到保存到磁盘（日志中出现 `Error whilst uploading profiler results`）；代价是每次停止都要等一次连接超时，且属于非官方用法。日常使用 `--save-to-file` 即可。

上传失败自动落盘这一行为由 `SamplerModule.handleUpload` 的 catch 分支保证，因此网络异常时不会丢数据——这也是为什么 `config/spark/` 里常会留下历史文件。

## 常见陷阱

- 不要把它和 Spark-Core 混淆：`cn.solarmoon.spark_core.*` 只是被采样的普通代码，本技能不做任何 Spark-Core 侧的分析或修改。
- `times[]` **已经是毫秒**（EXECUTION 模式），不要乘以采样间隔；`interval` 字段单位是**微秒**（4000 → 4 ms）。
- 多时间窗时 `times` 是数组，逐列对应 `time_windows`；工具已自动求和。
- 类名同名不同包（如多个 `Main#main`）会污染聚合，工具使用全限定名作为键，读表时以完整类名为准。
- `spark_version` 等字段在部分旧版报告里缺失，显示 `-` 属正常。
- 客户端 profile 的 `Render thread` 包含渲染循环，帧内尖峰与 TPS 没有直接关系。
- 采样间隔越大（如 4 ms 在低 TPS 下）统计越粗，低于 1% 的差异不具可信度。

## 参考

- [spark 命令速查（可直接复制给用户）](references/commands.md)
- [profile 数据格式与字段语义](references/profile-format.md)
- 上游 schema：`spark-common/src/main/proto/spark/spark_sampler.proto`、`spark.proto`（lucko/spark）
