JXInput
=======

[XInput](https://msdn.microsoft.com/en-us/library/windows/desktop/ee417001(v=vs.85).aspx) 的 Java 绑定。

Visual Studio 2017 解决方案包含原生代码，已编译并包含在 Java 项目中。它依赖于 XInput 1.3，该版本被 Direct 9.0c 游戏使用。XInput 1.4 也通过扩展 API 支持。

# 要求

使用 JXInput 需要 [Visual Studio 2017 的 Visual C++ 可再发行组件包](https://support.microsoft.com/en-gb/help/2977003/the-latest-supported-visual-c-downloads)：
- [vc_redist.x86.exe](https://aka.ms/vs/15/release/vc_redist.x86.exe)（适用于 32 位应用程序）
- [vc_redist.x64.exe](https://aka.ms/vs/15/release/vc_redist.x64.exe)（适用于 64 位应用程序）

XInput 1.3 支持在 Windows 7、Vista 和 XP SP1 中开箱即用。XInput 1.4 仅在 Windows 8 或更高版本中支持。

# 使用方法

如果您只是想在项目中使用该库，只需前往 [发布页面](https://github.com/StrikerX3/JXInput/releases/)，获取 [最新版本](https://github.com/StrikerX3/JXInput/releases/latest) 并将其包含在您的项目中。如果您更喜欢使用兼容 Maven 的构建系统，请使用以下选项之一：

## 安装到本地 Maven 仓库

1. 克隆此项目
2. 通过运行 `mvn clean install` 安装到本地 Maven 仓库（因为它不在中央 Maven 仓库中）。
3. 通过在 `pom.xml` 中添加以下内容，将 Maven 依赖项包含到您的项目中：

    ```xml
    <dependency>
        <groupId>com.github.strikerx3</groupId>
        <artifactId>jxinput</artifactId>
        <version>1.0.0</version>
    </dependency>
    ```

## 使用 [JitPack](http://jitpack.io/)

在您的 `pom.xml` 中添加以下内容：

```xml
<repositories>
   <repository>
        <id>jitpack.io</id>           <!-- JitPack 允许将 github 仓库用作 maven 仓库 -->
        <url>https://jitpack.io</url> <!-- 文档：http://jitpack.io/ -->
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.strikerx3</groupId>
        <artifactId>jxinput</artifactId>
        <version>1.0.0</version>    <!-- 这里可以使用任何已发布的版本、提交哈希或分支-SNAPSHOT -->
    </dependency>
</dependencies>
```
    
# 代码示例

该库的入口点是 `com.github.strikerx3.jxinput.XInputDevice` 和 `com.github.strikerx3.jxinput.XInputDevice14` 类。

`XInputDevice` 包含所有 XInput 1.3 功能，并且自 XP SP1 以来与所有 Windows 版本开箱即用。`XInputDevice14` 扩展了该类，添加了 XInput 1.4 特有的功能，需要 Windows 8 及更高版本。并非所有功能都受支持；最值得注意的是缺少 [音频](https://docs.microsoft.com/en-us/windows/desktop/api/xinput/nf-xinput-xinputgetaudiodeviceids) [功能](https://docs.microsoft.com/en-us/windows/desktop/api/XInput/nf-xinput-xinputgetdsoundaudiodeviceguids)。

为了在运行时检查所需的 XInput 版本是否可用，您可以使用这些类上的静态方法 `isAvailable()`：

```java
// 检查 XInput 1.3 是否可用
if (XInputDevice.isAvailable()) {
    // XInput 1.3 在该平台上可用
}

// 检查 XInput 1.4 是否可用
if (XInputDevice14.isAvailable()) {
    // XInput 1.4 在该平台上可用
}
```

您可能还对检查加载了哪个 DLL 版本感兴趣：

```java
// 获取 DLL 版本，可能是以下之一：
// - XInputLibraryVersion.NONE: 无 XInput 可用
// - XInputLibraryVersion.XINPUT_1_4: XInput 1.4（Windows 8 及更高版本）
// - XInputLibraryVersion.XINPUT_1_3: XInput 1.3（Windows XP SP1 及更高版本）
// - XInputLibraryVersion.XINPUT_9_1_0: XInput9 1.0（仅 Windows Vista）
XInputLibraryVersion libVersion = XInputDevice.getLibraryVersion();
```

## 使用 `XInputDevice`：XInput 1.3

支持 Windows XP SP1、Vista、7、8、8.1 和 10。
    
### 获取设备

``` java
// 获取所有设备
XInputDevice[] devices = XInputDevice.getAllDevices();

// 获取玩家 1 的设备
XInputDevice device = XInputDevice.getDeviceFor(0); // 或 devices[0]
```
    
### 使用设备 ([XInputGetState](https://msdn.microsoft.com/en-us/library/windows/desktop/microsoft.directx_sdk.reference.xinputgetstate(v=vs.85).aspx))

```java
XInputDevice device = ...;

// 首先我们需要轮询数据。
// poll() 如果设备未连接将返回 false
if (device.poll()) {
    // 获取组件
    XInputComponents components = device.getComponents();

    XInputButtons buttons = components.getButtons();
    XInputAxes axes = components.getAxes();

    // 按钮和轴具有公共字段（尽管这不是惯用的 Java 风格）

    // 获取按钮状态
    if (buttons.a) {
        // A 按钮当前被按下
    }

    // 检查是否支持 Guide 按钮
    if (XInputDevice.isGuideButtonSupported()) {
        // 使用它
        if (buttons.guide) {
            // Guide 按钮当前被按下
        }
    }

    // 获取轴状态
    float acceleration = axes.rt;
    float brake = axes.lt;
} else {
    // 控制器未连接；显示消息
}

// 这与上面完全相同
device.poll();
if (device.isConnected()) {
    // ...
} else {
    // ...
}
```

### 使用增量（轮询之间的状态变化）

```java
XInputDevice device = ...;

if (device.poll()) {
    // 获取增量
    XInputComponentsDelta delta = device.getDelta();

    XInputButtonsDelta buttons = delta.getButtons();
    XInputAxesDelta axes = delta.getAxes();

    // 获取按钮状态变化
    if (buttons.isPressed(XInputButton.a)) {
        // A 按钮刚刚被按下
    } else if (buttons.isReleased(XInputButton.a)) {
        // A 按钮刚刚被释放
    }

    // 获取轴状态变化。
    // 该类为每个轴提供方法，并为提供 XInputAxis 的方法
    float accelerationDelta = axes.getRTDelta();
    float brakeDelta = axes.getDelta(XInputAxis.leftTrigger);
} else {
    // 控制器未连接；显示消息
}
```

### 使用监听器

```java
XInputDevice device = ...;

// SimpleXInputDeviceListener 允许我们只实现我们实际需要的方法
XInputDeviceListener listener = new SimpleXInputDeviceListener() {
    @Override
    public void connected() {
        // 恢复游戏
    }

    @Override
    public void disconnected() {
        // 暂停游戏并显示消息
    }

    @Override
    public void buttonChanged(final XInputButton button, final boolean pressed) {
        // 给定的按钮刚刚被按下（如果 pressed == true）或释放（pressed == false）
    }
};

// 每当设备被轮询时，只要有变化，就会触发监听器事件
device.poll();
```

### 振动 ([XInputSetState](https://msdn.microsoft.com/en-us/library/windows/desktop/microsoft.directx_sdk.reference.xinputsetstate(v=vs.85).aspx))

```java
XInputDevice device = ...;

// 振动速度从 0 到 65535
//   其中 0 = 无振动
//   65535 = 最大振动
// 超出范围的值会抛出 IllegalArgumentException
int leftMotor = ...;
int rightMotor = ...;

device.setVibration(leftMotor, rightMotor);
```
    
## 使用 `XInputDevice14`：XInput 1.4

支持 Windows 8、8.1 和 10。

`XInputDevice` 中的所有方法在 `XInputDevice14` 中也可用。例如，您可以通过以下方式轮询设备：

``` java
// 获取玩家 1 的设备
XInputDevice14 device = XInputDevice14.getDeviceFor(0); // 或 devices[0]

// 轮询设备
if (device.poll()) {
    ...
}
```

### 启用或禁用 XInput 报告状态 ([XInputEnable](https://msdn.microsoft.com/en-us/library/windows/desktop/microsoft.directx_sdk.reference.xinputenable(v=vs.85).aspx))

``` java
// 当您的应用程序失去焦点时使用
XInputDevice14.setEnabled(false);
// - 轮询将返回中性数据，无论实际状态如何（例如，摇杆静止，按钮释放）
// - 振动设置将被忽略

// 当您的应用程序重新获得焦点时使用
XInputDevice14.setEnabled(true);
// - 轮询将返回控制器的实际状态
// - 振动设置将被应用
```
	
### 从设备获取电池信息 ([XInputGetBatteryInformation](https://msdn.microsoft.com/en-us/library/windows/desktop/microsoft.directx_sdk.reference.xinputgetbatteryinformation(v=vs.85).aspx))

```java
XInputDevice14 device = ...;

// 获取游戏手柄电池数据
XInputBatteryInformation gamepadBattInfo = device.getBatteryInformation(XInputBatteryDeviceType.GAMEPAD);
// gamepadBattInfo.getLevel() 包含电池电量级别，是 XInputBatteryLevel 的值之一：EMPTY（空）、LOW（低）、MEDIUM（中）或 FULL（满）。
// gamepadBattInfo.getType() 包含电池类型：
// - XInputBatteryType.DISCONNECTED: 控制器已断开连接
// - XInputBatteryType.WIRED: 有线控制器
// - XInputBatteryType.ALKALINE: 使用碱性电池
// - XInputBatteryType.NIMH: 使用可充电镍氢电池
// - XInputBatteryType.UNKNOWN: 使用未知类型的电池

// 检查电池级别
if (gamepadBattInfo.getLevel() == XInputBatteryLevel.LOW) {
    // 电池电量低！可能需要警告用户充电或更换电池
}
```
    
### 获取设备功能 ([XInputGetCapabilities](https://msdn.microsoft.com/en-us/library/windows/desktop/microsoft.directx_sdk.reference.xinput_capabilities(v=vs.85).aspx))

```java
XInputDevice14 device = ...;

XInputCapabilities caps = device.getCapabilities();
// caps.getType() 返回设备类型，始终是 XInputDeviceType.GAMEPAD
// caps.getSubType() 返回子类型，是 XInputDeviceSubType 枚举值之一
// caps.getSupportedButtons() 返回一个包含支持的按钮的 Set<XInputButton>
// caps.getResolutions() 返回一个包含所有轴分辨率的对象，作为位掩码
```
    
### 获取按键 ([XInputGetKeystroke](https://msdn.microsoft.com/en-us/library/windows/desktop/microsoft.directx_sdk.reference.xinputgetkeystroke(v=vs.85).aspx))

```java
XInputDevice14 device = ...;

XInputKeystroke keystroke = device.getKeystroke();
// 使用 keystroke.isKeyDown()、.isKeyUp() 和 .isRepeat() 检查按键类型
// 使用 keystroke.getVirtualKey() 获取虚拟键码（常量可在 XInputVirtualKeyCodes 中找到）
// 使用 keystroke.getUnicode() 获取 Unicode 字符
```

# 调试

JXInput 随附原生库的调试和发布版本。默认情况下，使用发布库。要加载调试库，请将系统属性 `native.debug` 设置为 `true` 作为 JVM 参数：`-Dnative.debug=true`。

根据 [MIT 许可证](http://opensource.org/licenses/MIT) 发布。