# .sparkprofile 数据格式

## 容器

- 文件是 **protobuf** 编码的 `spark.SamplerData`。
- 部分版本外层再套 **gzip**（首字节 `1f 8b`）。解析器自动识别。
- 不是 zip，也不含 JSON 头；直接用文本工具打开只会看到乱码。

```
message SamplerData {
  SamplerMetadata metadata = 1;
  repeated ThreadNode threads = 2;
  map<string,string> class_sources = 3;   // 可选，体积大
  map<string,string> method_sources = 4;
  map<string,string> line_sources = 5;
  repeated int32 time_windows = 6;
  map<int32,WindowStatistics> time_window_statistics = 7;
  SocketChannelInfo channel_info = 8;
}
```

## 关键字段

### SamplerMetadata

| 字段 | 含义 | 注意 |
| --- | --- | --- |
| `interval` | 采样间隔 | 单位 **微秒**，4000 = 4 ms |
| `start_time` / `end_time` | unix 毫秒 | |
| `number_of_ticks` | 覆盖的游戏 tick 数 | |
| `sampler_mode` | `EXECUTION` / `ALLOCATION` / `LOCK` | 决定 `times` 的量纲 |
| `sampler_engine` | `JAVA` / `ASYNC` | |
| `data_aggregator.type` | `SIMPLE` / `TICKED` | TICKED 时只保留超阈值 tick |
| `data_aggregator.thread_grouper` | `BY_NAME` / `BY_POOL` / `AS_ONE` | `BY_POOL` 会把同池线程合并为 `xxx (xN)` |
| `platform_metadata` | 平台名、MC 版本、spark 版本、brand | proto3 默认值不上线，`SERVER`/`JAVA` 可能缺失 |
| `platform_statistics` | TPS、MSPT、内存、玩家/实体数 | |
| `sources` | 涉及的 mod / 插件元数据 | 用于确认环境 |

### 时间/耗时的量纲（最容易错的地方）

节点累计值来自 `AbstractNode.times`，源码注释为 **microseconds**，导出到 proto 时经 `SamplerMode.valueTransformer()` 变换：

| 模式 | `times` 单位 |
| --- | --- |
| `EXECUTION` | 毫秒（源码注释明确为 milliseconds） |
| `ALLOCATION` | 分配字节数 |
| `LOCK` | 锁事件计数 |

**不要**再用 `times × interval` 换算，那是旧格式的采样计数语义。

### 时间窗

`times` 是数组，长度 = 时间窗数量。位置 i 对应 `time_windows[i]`，其值是 `epoch 分钟数`（`System.currentTimeMillis() / 60000`）。单窗时长度为 1。

窗口内的每列独立累加，跨窗口求和即该节点在整个采样期的总耗时。

### ThreadNode / StackTraceNode

```
ThreadNode      : name, children[], times[], children_refs[]   // children 是扁平节点池
StackTraceNode  : class_name, method_name, line_number, method_desc, times[], children_refs[]
```

`children_refs` 是**指向同线程节点池的索引**，不是嵌套结构。构建树的方式：

1. 读线程池 `thread.children`。
2. 线程根的子节点 = `thread.children_refs` 指向的池元素。
3. 每个元素的子节点 = 其 `children_refs` 指向的池元素。

池中节点按深度优先后序排列，所以索引通常指向前面的元素。

### self / total 计算

- `total(node) = sum(node.times)`
- `self(node) = total(node) − Σ total(children)`

节点值是**含子调用**的累计值，这一点与 Chrome trace / async-profiler 的原始栈采样不同，容易算错。

## 已验证样本

一次 NeoForge 1.21.1 实机采样中的 6 份报告（2026-04 ~ 2026-07，spark 1.10.124）全部可解析，覆盖服务端与客户端、`SPECIFIC` 与默认线程筛选、多时间窗。不同 spark 版本的字段可能增减，未知字段会被安全跳过。

## 上游来源

- `https://github.com/lucko/spark` → `spark-common/src/main/proto/spark/spark_sampler.proto`
- `spark-common/src/main/proto/spark/spark.proto`
- `spark-common/src/main/java/me/lucko/spark/common/sampler/node/AbstractNode.java`
- `spark-common/src/main/java/me/lucko/spark/common/sampler/window/ProtoTimeEncoder.java`
- `spark-common/src/main/java/me/lucko/spark/common/sampler/AbstractSampler.java`
