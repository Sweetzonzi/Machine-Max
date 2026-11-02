# pack-reports — 第三方内容包的开发期验证报告

这里存放**不随游戏分发**的内容包开发档案。内容包本体在
`src/main/resources/spark_modules/<pack>/`，报告是逐版本工作记录，只对维护者有意义，
因此从包内移出，避免打进 `spark_modules/*.zip` 与模组 jar。

| 目录 | 来源包 | 版本区间 | 文件数 |
|---|---|---|---|
| `M1A2_SEP/` | `Machine-Max_M1A2_SEP`（`m1a2_sep:pack`） | 0.3.1 → 0.5.9 | 26 |
| `T90A/` | `Machine-Max_T90A`（`t90a:pack`） | 0.1.1 → 0.1.17 | 20 |

## 文件性质

每份 `*-report.json` 是单个版本的改动记录，体例为：`problem`（要解决的问题）→
配置改动前后值 → `validation`（离线校验计数：schema、骨骼、定位器、碰撞体、姿态对）。
多数条目带 `game_tested: false`，即验证覆盖离线 schema / 几何 / 绑定，不含实机验收。

`validation.json` 是整包快照，`armor-update-report.json`、`historical-data-sources.json`
等是参数溯源记录（含固定 commit 与 SHA-256）。

## 注意

- 报告中含原作者本机绝对路径（`E:\codex\...`）与备份目录名，属于过程残留，不影响内容。
- 内容包内的 `docs/notes/` 保留参数台账与 `references/sources.txt`（许可证与出处
  都在其中），那是随包发布的部分；本目录只放过程记录。
- 需要彻底丢弃这些档案时，直接删除本目录即可，不影响构建与运行。
