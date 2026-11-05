/**
 * 手工探针：把一次性实验、引擎行为标定与环境相关测量从自动化用例里分离出来。
 *
 * <p>本包由 {@code build.gradle} 的 {@code test} 任务按路径排除，{@code ./gradlew test} 不执行
 * 其中的用例，源码照常编译。需要复测时用 {@code ./gradlew probeTest} 一次跑完本包，
 * 或在 IDE 里逐方法运行。</p>
 *
 * <p>判据：留在本包之外的断言必须是产品契约。只回答「引擎在某个参数下表现如何」、依赖本机重建的
 * native、或者以打印表格为主要产出的，放在这里。</p>
 *
 * <p>{@link NativeLibraryLoader} 与 {@link ClimbLiftRig} 是 {@code test} 与 {@code probeTest}
 * 共用的测试基建（native 加载、台阶抬升试具），放在本包以免每个不启动 Minecraft 的物理用例各抄一份。</p>
 */
package io.github.sweetzonzi.machine_max.probe;
