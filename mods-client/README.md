# mods-client/ — 仅客户端的开发期模组

把「只在客户端成立」的第三方 mod jar 放进这个目录，文件名以 `.jar` 结尾就会被 `devClientMods` 收走。当前开发
环境放的是：

| jar | 用途 |
|-----|------|
| `DistantHorizons-2.4.5-b-1.21.1.jar` | Distant Horizons：远景 LOD 渲染 |
| `iris-neoforge-1.8.8+mc1.21.1.jar` | Iris：光影加载器 |
| `sodium-neoforge-0.6.13+mc1.21.1.jar` | Sodium：渲染优化 |

加载范围：只有 `runClient`。挂载点是 `runClient` 任务自己的 `classpathProvider`，理由见 `build.gradle` 里
`devClientMods` 那段注释。

**这些 jar 不能放 `../mods/`**：那批进的是四个 run 共用的 main `runtimeClasspath`，而 DistantHorizons 会在
`ServerAboutToStart` 里把服务器强转 `DedicatedServer`，`GameTestServer` 不是该类型，进程会在启动阶段以
`ClassCastException` 退出；Iris / Sodium 的 mixin 也只对客户端成立。

**jar 不入库**：`.gitignore` 忽略 `/mods-client/*.jar`，本仓库只提交这份 README。clone 之后需要自己下载放进
这个目录，版本以上表为准。

**从 IDE 发起的 run 拿不到这里的东西**：IDE 的类路径来自 Gradle 的 main `runtimeClasspath`（四个 run 共用
一份），只有 `gradlew runClient` 会走 `classpathProvider`。
