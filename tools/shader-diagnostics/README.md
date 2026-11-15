# 屏幕空间光照诊断工具

工具读取游戏截取的颜色、深度、光源和着色器，在隐藏的 OpenGL 窗口中回放，输出同帧对照。它针对 Machine-Max 的 `screen_space_light` 输入契约，可用于比较候选算法、预算、厚度与遮挡范围。

独立回放不启动 Minecraft，也不编译模组业务源码。运行环境为 Java 21、可用的 OpenGL 3.3 图形会话，以及项目本地缓存中的 Gson 2.10.1、LWJGL 3.3.3。命令统一通过仓库 Gradle Wrapper，以 `--offline` 运行。Windows 已实测；其他平台按本机原生库分类解析，但没有本项目的会话验证。

## 日常使用

在仓库根运行，`latest` 选择 `run/screenshots` 中最后写入的 `mm-light-*` 目录：

```powershell
# 用截取时保存的源码复现当前图
.\tools\shader-diagnostics\diagnose.ps1 -Capture latest

# 用工作区中的着色器计算同一帧，比较查询预算64/128/256/512
.\tools\shader-diagnostics\diagnose.ps1 -Capture latest -Shader current -Preset budget

# 其他参数对照
.\tools\shader-diagnostics\diagnose.ps1 -Capture latest -Shader current -Preset range
.\tools\shader-diagnostics\diagnose.ps1 -Capture latest -Shader current -Preset thickness

# 以PNG左上角为原点，检查某像素到槽0光源的独立射线
.\tools\shader-diagnostics\diagnose.ps1 -Capture latest -Pixel 1350,1470 -Light 0

# 自定义标量参数；会同时输出current组
.\tools\shader-diagnostics\diagnose.ps1 -Capture latest -Shader current -Variants tools/shader-diagnostics/variants.example.json
```

`-Capture`也接受明确路径，包含空格时加引号。`-Shader`取`saved`、`current`或一个候选片元文件路径；默认`saved`。显式路径采用工作区深度归并程序；需指定其他归并程序时使用下方Gradle入口的`-PshaderReducer`。

默认输出到`run/diagnostics/shader-light/<时间>-<随机编号>/`。可用`-Output`指定一个**尚不存在**的目录；工具拒绝覆盖现有目录，也拒绝把结果写入截取目录内部。源截图和原始深度只读。

## 对照组

| `-Preset` | 内容 |
| --- | --- |
| `current`（默认） | 使用输入帧参数，所有`Capture*`控制复位为0；附原因图 |
| `captured` | 按`frame.json`中实际记录的`comparisons`回放，适合复现截取时的整组结果 |
| `budget` | 所选源码默认算法的单条/像素共享查询预算各64、128、256、512 |
| `range` | 关闭S3及8/16/64m范围；保留光照、光晕和星芒 |
| `thickness` | 2/5/10/25cm厚度；使用完整原始像素格参考与逐灯充足预算，排除共享预算竞争 |
| `all` | 使用`ScreenSpaceLightDiagnosticPreset.forFrame`，与开发截取共用全部预设 |

`all`指当前源码支持的控制集合。某份保存源码若没有对应控制，工具会报出不支持的uniform；复现该份源码应选择`captured`，试验当前控制应选择`-Shader current`。自定义参数中的uniform也必须是候选程序的有效标量uniform，拼写错误和无效控制会报错。

自定义JSON是数组，每项包含`name`、`uniforms`和可选`reasons`。名称只接受字母、数字、下划线和连字符；同一组中不可重名。示例见[variants.example.json](variants.example.json)。实际使用的控制值与用户覆盖值分别写入报告的`uniforms`、`overrides`。

## 报告与指标

打开输出目录中的`index.html`，选择候选并滑动分割线，查看同一位置的参考/候选图。默认参考是截取目录里的`01_current.png`，可以用`-Baseline`指定其他PNG。自动参考缺失时只生成图像和参数，不给出匹配结论；显式参考路径无效时直接报错。

| 文件 | 内容 |
| --- | --- |
| `index.html` | 可滑动的图像对照、差异与原因图链接 |
| `overview.png` | 全组缩略拼图 |
| `report.json` | 输入来源、源码/输入校验信息、有效参数、差异、原因计数、可选像素结果 |
| `metrics.csv` | 逐组RGB差异统计 |
| `<组名>.png` | 正常光照回放 |
| `<组名>_diff.png` | 相对参考的差异热图，差值放大4倍 |
| `<组名>_reasons.png` | 请求该视图且源码支持时输出的S3原因图 |
| `input-frame.json`、`fragment.fsh`、`vertex.vsh`、`depth_reduce.fsh` | 本次回放所用的输入参数与源码；无层级算法时不需要归并源码 |

`changed_pixels`统计RGB任一通道差值**大于**阈值的像素数，默认阈值2，使用`-Threshold`调整。`nonidentical_pixels`统计任一RGB差值非零的像素，`exact_rgb_match`只在全部RGB逐位一致时为true。Alpha不参与比较。`mean_absolute_channel_difference`是全部RGB通道的平均绝对差，单位为8位显示值。

原因图为所有参与光源的聚合诊断：橙=预算耗尽、紫=裁剪、蓝=缺深度、青=点间未检查、红=确认命中、绿=完整未命中、灰=未查询。高优先级未知原因可以覆盖其他灯的命中显示。没有差异只证明同输入下的数值复现，不证明阴影的几何正确性。

`tiled_render_wall_ms`包含分块提交、同步及回读；**它不是生产GPU绘制耗时**，不能用它声称游戏帧率提升。需要成本测量时运行实际生产程序的GPU探针：

```powershell
$env:MM_S3_BENCHMARK = '1'
.\gradlew --offline probeTest --tests '*ScreenSpaceLightProbe' -x createMinecraftArtifacts -x :Spark-Core:createMinecraftArtifacts -x :BallisticsFramework:createMinecraftArtifacts
Remove-Item Env:MM_S3_BENCHMARK
```

## 像素查询

`-Pixel X,Y -Light N`读取PNG左上角坐标，对指定光源独立调用所选源码中的`traceLightRay`。报告给出HIT/CLEAR/UNKNOWN、层级／原始深度查询次数（不含法线邻域读取）、原因位、表面可见性，以及GPU重建的视空间位置与到光源距离。

原因位：1=预算、2=裁剪、4=缺有效深度、8=点采样间隔未检查。查询使用该组的单灯/像素预算，不模拟正常画面中其他光源已经消耗的共享预算。它适合检查“这一盏灯的这条射线”，不能将独立结果解释成完整多灯通路的预算分配。关闭S3和7点平均基线组会标注有限深度追踪不适用。

## 仅比较PNG

这条路径不创建OpenGL上下文，可在没有图形会话的环境运行：

```powershell
.\tools\shader-diagnostics\diagnose.ps1 -CompareLeft '参考.png' -CompareRight '候选.png'
```

## 开发截取入口（默认停用）

F6按键默认以源码注释停用：没有构造诊断`KeyMapping`，没有注册按键，也没有监听按键触发。正常客户端的世界退出清理和失色衰减保持启用。

需要现场保存输入时，在以下两个文件中启用标记为“开发诊断”的代码块，然后重启客户端：

1. `KeyBinding.java`：`lightCaptureKey`字段定义，以及`onKeyRegister`中的`event.register(lightCaptureKey)`。
2. `PostProcessingManager.java`：`lightCaptureHeld`字段、`onLightCaptureTick`辅助方法，以及`onClientTick`中的调用。

启用后可在控制设置重绑定，默认F6。按一次仅请求一次截取，最多等待5秒有光源的帧；可以先请求再开火。截取会同步绘制多组完整参考并保存大图，适合开发诊断。保存目录为`run/screenshots/mm-light-*/`，使用后将这些开发代码块恢复为注释。

截取格式1记录`capture_format_version`、效果ID、深度编码、完整标量控制值，以及颜色、深度和三份源码的SHA-256。回放核对已有校验值，尺寸、数组长度、深度字节数或校验值不一致时拒绝执行。顶点源码也随帧保存。

格式0是没有`capture_format_version`的截图目录：支持既有颜色、深度和字段；缺少`ShadowRange`表示截取时没有距离裁剪，候选源码的范围设置为1000000m以保持这一语义，报告标记`legacy_unbounded_range=true`。没有顶点文件时使用协议中的全屏三角形。保存源码不使用层级时无需`depth_reduce.fsh`。缺少哈希不能验证文件是否被编辑，报告会标明可用的校验情况。

## Gradle入口与测试

PowerShell脚本只是参数转发；直接运行Gradle也可以：

```powershell
.\gradlew --offline shaderDiagnostics -PshaderCapture='run/screenshots/mm-light-...' -PshaderShader=current -PshaderPreset=range
.\gradlew --offline shaderDiagnostics -PshaderDiagnosticsHelp=true
.\gradlew --offline shaderDiagnosticsTest
```

其他属性为`shaderOutput`、`shaderVariants`、`shaderBaseline`、`shaderThreshold`、`shaderPixel`（如`1350,1470`）、`shaderLight`、`shaderReducer`、`shaderCompareLeft`和`shaderCompareRight`。所有路径通过独立参数传递，不解析为可执行命令。

`shaderDiagnosticsTest`为无需GPU的输入校验与图像差异契约，纳入`check`。实际GPU解析几何仍由`ScreenSpaceLightProbe`验证。独立源码集只编译入口、共享诊断预设和生产层级构建器/GL状态保存器，不加入模组发布jar。
