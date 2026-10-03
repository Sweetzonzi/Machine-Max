# mods-disabled/ — 不能出现在运行期类路径上的 jar 的停放处

这个目录不参与任何 run：没有任何 Gradle 配置读它，放这里的 jar 不会被加载。它的用途是停放「本地留着有用、
但一旦进入类路径就会让 ModLauncher 中止」的 jar：

| jar | 为什么只能停在这里 |
|-----|--------------|
| `geckolib-neoforge-1.21.1-4.8.4.jar` | 与 Maven 依赖 `software.bernie.geckolib:geckolib-neoforge-<mc>:<geckolib_version>` 的同名模块撞包 |
| `spark-1.10.124-neoforge.jar` | Spark-Core 的 `implementation(files(fileTree("mods")))` 已经把它带进运行期类路径，本地再放一份就撞包 |

两者撞包的后果都是 `java.lang.module.ResolutionException`：同一个包被两个模块导出，ModLauncher 在模块解析阶段
直接中止，服务端起不来。

**jar 不入库**：`.gitignore` 忽略 `/mods-disabled/*.jar`，本仓库只提交这份 README。
