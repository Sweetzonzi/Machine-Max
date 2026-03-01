package io.github.sweetzonzi.machine_max.client.input;


import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 核心输入监听器类，处理原生输入事件
 *
 * 该类是输入处理系统的核心，实现了多个原生输入监听接口，用于捕获和处理键盘、鼠标事件。
 * 支持异步事件处理，使用线程池提高响应速度。
 * 处理按键状态管理、组合键检测、连按计数和长按事件。
 * <p>
 * 技术特性：
 * <ul>
 *     <li>异步事件处理：使用线程池提高响应速度</li>
 *     <li>高性能：使用线程安全的数据结构和优化算法</li>
 *     <li>详细的调试日志支持</li>
 * </ul>
 * <p>
 * 使用前需要初始化：
 * <pre>
 * // 初始化输入系统
 * InputListener.setUp();
 * </pre>
 *
 * @author ArcherLee
 * @version 1.1.0
 * @see ChainAbleInput
 * @see NativeAxisInput
 */

public class InputListener {
    // 优化：使用更高效的数据结构存储组合键事件
    public static final Map<String, List<ChainAbleInput>> combineKeyInputsMap = new ConcurrentHashMap<>();
    public static final Map<String, List<ChainAbleInput>> pressKeyInputs = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> pressKeyStatus = new ConcurrentHashMap<>();
    public static final Map<String, Boolean> combineKeyStatus = new ConcurrentHashMap<>();

    // 调试日志开关
    private static final boolean DEBUG = false;

    // 优化：使用更高效的 Map 实现
    private static final Map<String, Integer> keyPressCounts = new ConcurrentHashMap<>();
    // 为每个按键维护最后按键时间戳
    private static final Map<String, Long> keyLastPressTimes = new ConcurrentHashMap<>();
    private static final Map<String, Long> keyStartPressTimes = new ConcurrentHashMap<>();
    // 为每个组合键维护连按计数
    private static final Map<String, Integer> combineKeyPressCounts = new ConcurrentHashMap<>();
    // 为每个组合键维护最后按键时间戳
    private static final Map<String, Long> combineKeyLastPressTimes = new ConcurrentHashMap<>();
    // 连按时间窗口（纳秒）
    private static final long PRESS_WINDOW_NS = 300_000_000;

    // 优化：添加线程池用于异步处理事件
    private static final ExecutorService eventExecutor = Executors.newCachedThreadPool();
    public static final int HOLDING_THRESHOLD = 70_000_000; //长按判定窗口

    // 鼠标位置跟踪
    private static int lastMouseX = 0;
    private static int lastMouseY = 0;

    // 跟踪每个按键在当前按下期间已经触发的长按事件
    private static final Map<String, Set<ChainAbleInput>> holdEventTriggered = new ConcurrentHashMap<>();

    public static final String MOUSE_LEFT_BUTTON = "mouse1";
    public static final String MOUSE_RIGHT_BUTTON = "mouse2";
    public static final String MOUSE_WHEEL_BUTTON = "mouse3";
    public static final String MOUSE_BACKWARD_BUTTON = "mouse4";
    public static final String MOUSE_FORWARD_BUTTON = "mouse5";

    public static Long windowHandle = null;
    public static ChainAbleInput W = new ChainAbleInput(GLFW.GLFW_KEY_W)
            .chain(new ChainAbleInput(GLFW.GLFW_KEY_LEFT_CONTROL))
            .setEvent(() -> {
        System.out.println("you pressed w with ctrl");
    });
    public static ChainAbleInput A = new ChainAbleInput(GLFW.GLFW_KEY_W).hold(1, ChronoUnit.SECONDS).setEvent(() -> {
        System.out.println("you pressed w for 1 sec");
    });

    static {


        new Thread(() -> {
            while (true) {
                try {
                    if (windowHandle != null) {
                        for (String keyText : pressKeyInputs.keySet()) {
                            List<ChainAbleInput> chainAbleInputs = pressKeyInputs.get(keyText);
                            for (ChainAbleInput chainAbleInput : chainAbleInputs) {
                                if (chainAbleInput.getContext() instanceof GLFWKeyContext glfwKeyContext) {
                                    String innerText = glfwKeyContext.getKeyText();
                                    if (GLFW.glfwGetKey(windowHandle, glfwKeyContext.getData()) == GLFW.GLFW_PRESS) {
                                        keyStartPressTimes.putIfAbsent(keyText, System.nanoTime());
                                        processKeyPress(innerText);
                                    } else {
                                        keyStartPressTimes.remove(keyText);
                                        processKeyReleased(innerText);
                                    }

                                }
                            }
                        }
//                        for (String keyText : pressKeyStatus.keySet()) {
//                            if (pressKeyStatus.getOrDefault(keyText, false)) {
//                                keyStartPressTimes.putIfAbsent(keyText, System.nanoTime());
//                                processKeyPress(keyText);
//                            } else {
//                                keyStartPressTimes.remove(keyText);
//                            }
//                        }
                    }
                    Thread.sleep(Duration.of(10, ChronoUnit.NANOS));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
//
//        new Thread(() -> {
//            while (true) {
//                try {
//                    Minecraft minecraft = Minecraft.getInstance();
//                    // 测试环境没有mc，所以判空
//                    if (minecraft != null && minecraft.screen != null && minecraft.screen.getMinecraft().isWindowActive()) { //恢复窗口时恢复震动
//                        // todo 手柄震动的演示，后期可能需要区分每个玩家的手柄：
//                        //   1.devices中是本地计算机所有连接的手柄，如果是支持多人同时在一台电脑上驾驶的情况需要区分
//                        //   2.不同的座位上，手柄的震动位置、震动大小可以按需指定、更新
//                        //   3.手柄一旦设置震动大小，不管有没有继续更新大小，触发左右扳机都会随按压深度增大振幅。
//                        //   4.手柄需要手动设置0振幅 来停止震动，若失去进程焦点也会自动停止
//                        //   5.也许未来需要手写 DLL文件，让手柄的静默震动和扳机按压震动解耦，方便外部包作者定义更细致的震动反馈：
//                        //     比如 直升机的操纵杆会改变静默震动的位置和大小，但与旋翼力矩相同方向的踏板扳机需要关闭震动反馈
//
//                        // 演示：把该计算机上所有连接的手柄全设置为20%振幅
////                        for (XInputDevice device : devices) {
////                            device.setVibration(0, Double.valueOf(MAX_VIBRATION * 0.2).intValue());
////                        }
//                    }
//                    for (XInputDevice device : devices) {
//                        if (device.poll()) {
//                            // 获取增量
//                            XInputComponentsDelta delta = device.getDelta();
//
//                            XInputButtonsDelta buttons = delta.getButtons();
//                            XInputComponents components = device.getComponents();
//                            XInputAxes axes = components.getAxes();
//
//                            for (XInputButton button : XInputButton.values()) {
//                                String keyText = xInputButton(button).toLowerCase();
//                                if (buttons.isPressed(button)) {
//                                    processKeyPress(keyText);
//                                } else if (buttons.isReleased(button)) {
//                                    processKeyReleased(keyText);
//                                }
//                            }
//                            for (XInputAxis axis : XInputAxis.values()) {
//                                float axesData = axes.get(axis);
//                                processAxisInput(NativeAxisInput.AxisType.GAMEPAD, axis.name().toLowerCase(), axesData);
//                            }
//
//                        } else {
//                            // 控制器未连接；显示消息
//                        }
//                    }
//                    Thread.sleep(Duration.of(40, ChronoUnit.MILLIS));
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                }
//            }
//        }).start();
    }

//    public static String deltaAxis(XInputAxis axis) {
//        return "delta_" + axis.name();
//    }
//
//    public static String xInputButton(XInputButton button) {
//        return "xinput_" + button.name();
//    }
//    private String getMouseName(int mouseButton) {
//        return "mouse" + mouseButton;
//    }

//	public void nativeMousePressed(NativeMouseEvent e) {
//        String keyText = getMouseName(e.getButton()).toLowerCase();
//        // 处理按键事件
//        processKeyPress(keyText);
//	}
//
//	public void nativeMouseReleased(NativeMouseEvent e) {
//        String keyText = getMouseName(e.getButton()).toLowerCase();
//        processKeyReleased(keyText);
//	}
//
//	public void nativeMouseMoved(NativeMouseEvent e) {
////        System.out.println("Mouse Moved: " + e.getX() + ", " + e.getY());
//        // 计算鼠标移动的 delta 量
//        int deltaX = e.getX() - lastMouseX;
//        int deltaY = e.getY() - lastMouseY;
//
//        // 更新鼠标位置
//        lastMouseX = e.getX();
//        lastMouseY = e.getY();
//
//        // 处理鼠标 X 轴输入
//        processAxisInput(NativeAxisInput.AxisType.MOUSE, "x", deltaX);
//
//        // 处理鼠标 Y 轴输入
//        processAxisInput(NativeAxisInput.AxisType.MOUSE, "y", deltaY);
//    }
//
//    public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
//        String direction = "unknown";
//        if (e.getWheelDirection() == NativeMouseWheelEvent.WHEEL_VERTICAL_DIRECTION) {
//            direction = "v";
//        }
//        if (e.getWheelDirection() == NativeMouseWheelEvent.WHEEL_HORIZONTAL_DIRECTION) {
//            direction  = "h";
//        }
//
//        processAxisInput(NativeAxisInput.AxisType.MOUSE_WHEEL, direction, e.getWheelRotation());
//    }
//
//    public void nativeKeyPressed(NativeKeyEvent e) {
//        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
//        if (DEBUG) System.out.println("NativeKeyListener: Key Pressed: " + keyText);
//        // 处理按键事件
//        processKeyPress(keyText);
//    }

    // 处理按键按下事件
    private static void processKeyPress(String keyText) {
//        // 检查是否是长按，如果按键状态已经是 true，则不处理事件
//        if (pressKeyStatus.getOrDefault(keyText, false)) {
//            return;
//        }
        // 先设置按键状态为 true，确保组合键名称生成时包含当前按键
        pressKeyStatus.put(keyText, true);
        // 优化：减少重复的组合键名称计算
        String combinedKeyName = generateCombinedKeyName();
        // 处理单个按键事件，无论是否有组合键事件
        handleSingleKeyEvent(keyText);
        // 运行组合键事件
        handleCombineKeyEvent(combinedKeyName);
    }

    // 处理单个按键事件
    private static void handleSingleKeyEvent(String keyText) {
        List<ChainAbleInput> ChainAbleInputs = pressKeyInputs.get(keyText);
        if (DEBUG) System.out.println("NativeKeyListener: Native inputs for " + keyText + ": " + (ChainAbleInputs != null ? ChainAbleInputs.size() : 0));
        if (ChainAbleInputs != null && !ChainAbleInputs.isEmpty()) {
            // 更新全局连按计数
            long currentTime = System.nanoTime();
            long lastPressTime = keyLastPressTimes.getOrDefault(keyText, 0L);
            long timeDiff = currentTime - lastPressTime;
            long pressLength = currentTime - keyStartPressTimes.getOrDefault(keyText, 0L);
//            System.out.println(pressLength);
            int currentCount = keyPressCounts.getOrDefault(keyText, 0);
            if (lastPressTime == 0 || timeDiff > PRESS_WINDOW_NS) {
                currentCount = 1;
                if (DEBUG) System.out.println("NativeKeyListener: Resetting count to 1 for key " + keyText);
            } else {
                currentCount++;
                if (DEBUG) System.out.println("NativeKeyListener: Incrementing count to " + currentCount + " for key " + keyText);
            }

            // 更新时间戳和计数
            keyLastPressTimes.put(keyText, currentTime);
            keyPressCounts.put(keyText, currentCount);

            if (DEBUG) System.out.println("NativeKeyListener: Current count for key " + keyText + " is " + currentCount);

            // 检查每个 ChainAbleInput 是否达到所需的按键次数aa
            for (ChainAbleInput ChainAbleInput : ChainAbleInputs) {
                if (ChainAbleInput.getHoldTimeNanos() - HOLDING_THRESHOLD >= pressLength || ChainAbleInput.getHoldTimeNanos() + HOLDING_THRESHOLD <= pressLength) {
                    continue;
                }

                // 检查是否是长按事件且已经触发过
                boolean isHoldEvent = ChainAbleInput.getHoldTimeNanos() != 0;
                if (isHoldEvent) {
                    Set<ChainAbleInput> triggeredEvents = holdEventTriggered.getOrDefault(keyText, new HashSet<>());
                    if (triggeredEvents.contains(ChainAbleInput)) {
                        continue;
                    }
                }

                if (ChainAbleInput.getEvent() != null) {
                    int requiredPressTimes = ChainAbleInput.getPressTimes();
                    if (DEBUG) System.out.println("NativeKeyListener: Checking if " + currentCount + " matches " + requiredPressTimes);
                    // 修复：只要计数是所需次数的倍数，就触发事件
                    if (currentCount % requiredPressTimes == 0) {
                        if (DEBUG) System.out.println("NativeKeyListener: Press count matched, running event for input with pressTimes: " + requiredPressTimes);
                        // 优化：异步处理事件
                        eventExecutor.execute(ChainAbleInput.getEvent());

                        // 如果是长按事件，标记为已触发
                        if (isHoldEvent) {
                            holdEventTriggered.computeIfAbsent(keyText, k -> new HashSet<>()).add(ChainAbleInput);
                        }
                    }
                }
            }
        }
    }

    // 处理组合键事件
    private static void handleCombineKeyEvent(String combinedKeyName) {
        if (combineKeyStatus.getOrDefault(combinedKeyName, false)) {
            return;
        }
        if (DEBUG) System.out.println("NativeKeyListener: Looking for combine key inputs with name " + combinedKeyName);
        // 先更新组合键连按计数
        long currentTime = System.nanoTime();
        long lastPressTime = combineKeyLastPressTimes.getOrDefault(combinedKeyName, 0L);
        long timeDiff = currentTime - lastPressTime;

        int currentCount = combineKeyPressCounts.getOrDefault(combinedKeyName, 0);
        if (lastPressTime == 0 || timeDiff > PRESS_WINDOW_NS) {
            currentCount = 1;
            if (DEBUG) System.out.println("NativeKeyListener: Resetting combine key count to 1 for " + combinedKeyName);
        } else {
            currentCount++;
            if (DEBUG) System.out.println("NativeKeyListener: Incrementing combine key count to " + currentCount + " for " + combinedKeyName);
        }

        // 更新时间戳和计数
        combineKeyLastPressTimes.put(combinedKeyName, currentTime);
        combineKeyPressCounts.put(combinedKeyName, currentCount);

        if (DEBUG) System.out.println("NativeKeyListener: Current combine key count for " + combinedKeyName + " is " + currentCount);

        // 优化：直接从Map中获取匹配的组合键输入，避免遍历整个Set
        List<ChainAbleInput> matchingCombineInputs = combineKeyInputsMap.get(combinedKeyName);
        if (matchingCombineInputs != null && !matchingCombineInputs.isEmpty()) {
            for (ChainAbleInput input : matchingCombineInputs) {

                if (input.getEvent() != null) {
                    int requiredPressTimes = input.getPressTimes();
                    if (DEBUG) System.out.println("NativeKeyListener: Checking if " + currentCount + " matches " + requiredPressTimes);
                    // 修复：只要计数是所需次数的倍数，就触发事件
                    if (currentCount % requiredPressTimes == 0) {
                        if (DEBUG) System.out.println("NativeKeyListener: Combine key press count matched, running event for input with pressTimes: " + requiredPressTimes);
                        // 优化：异步处理事件
                        eventExecutor.execute(input.getEvent());
                    }
                }
            }
        }
        combineKeyStatus.put(combinedKeyName, true);
    }

    // 优化：提取组合键名称生成逻辑为单独方法
    private static String generateCombinedKeyName() {
        return pressKeyStatus.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .sorted((key1, key2) -> {
                    int lengthCompare = Integer.compare(key2.length(), key1.length());
                    if (lengthCompare != 0) {
                        return lengthCompare;
                    }
                    return key1.compareTo(key2);
                })
                .reduce((key1, key2) -> key1 + "-" + key2)
                .orElse("");
    }

    // 处理轴输入
    private static void processAxisInput(NativeAxisInput.AxisType axisType, String axisName, double delta) {
        Map<String, Set<NativeAxisInput>> axisMap = NativeAxisInput.axisInputsMap.get(axisType);
        if (axisMap != null) {
            Set<NativeAxisInput> axisInputs = axisMap.get(axisName);
            if (axisInputs != null && !axisInputs.isEmpty()) {
                for (NativeAxisInput axisInput : axisInputs) {
                    if (axisInput.getEvent() != null) {
                        // 优化：异步处理事件并传入 delta 参数
                        final double finalDelta = delta;
                        if (axisInput.isDelta()) {
                            if (axisInput.getLastValue() != delta)
                                eventExecutor.execute(() -> axisInput.getEvent().event(delta));
                            axisInput.updateValue(delta);

                        } else {
                            eventExecutor.execute(() -> axisInput.getEvent().event(finalDelta));
                            axisInput.updateValue(axisInput.getLastValue() + delta);
                        }

                    }
                }
            }
        }
    }

//    public void nativeKeyReleased(NativeKeyEvent e) {
//        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
//        processKeyReleased(keyText);
//    }

    private static void processKeyReleased(String keyText) {
        pressKeyStatus.put(keyText, false);
        keyStartPressTimes.remove(keyText);
        // 清除已触发的长按事件记录
        holdEventTriggered.remove(keyText);
        combineKeyStatus.clear();
        // 注意：不再需要在按键释放时重置连按计数，因为连按计数是基于时间窗口的
        // 当两次按键间隔超过时间窗口时，会自动重置计数
    }

//    public void nativeKeyTyped(NativeKeyEvent e) {
////        在 Pressed和 Released事件之间，当一次按键动作产生了一个可显示的字符时触发。暂时想不到用处
////        System.out.println("Key Typed: " + e.getKeyText(e.getKeyCode()));
//    }



    public static void setUp() {
        windowHandle = Minecraft.getInstance().getWindow().getWindow();
    }


    public static void main(String[] args) {

//            new ChainAbleInput(XInputButton.X)
//                    .setEvent(() -> System.out.println("Xbox x button"));
//
//            new NativeAxisInput(XInputAxis.RIGHT_TRIGGER)
//                    .setEvent(delta -> {
//                        System.out.println("Xbox right trigger " + delta);
//                    });

            new ChainAbleInput("a", 2)
                    .chain(new ChainAbleInput("ctrl"))
                    .setEvent(() -> {
                        System.out.println("ChainAbleInput Double Ctrl A");
                    });
            new ChainAbleInput("A")
                    .chain(new ChainAbleInput("ctrl")).setEvent(() -> {
                        System.out.println("ChainAbleInput Ctrl A");
                    });
            new ChainAbleInput("A")
                    .chain(new ChainAbleInput("Shift"))
                    .chain(new ChainAbleInput("Ctrl"))
                    .setEvent(() -> System.out.println("ChainAbleInput Ctrl+Shift+A"));

            new ChainAbleInput("A").setEvent(() -> System.out.println("A"));

            new ChainAbleInput(MOUSE_LEFT_BUTTON)
                    .chain(new ChainAbleInput("Ctrl"))
                    .setEvent(() -> System.out.println("mouse left button clicked when ctrl pressed"));

            // 创建鼠标 X 轴输入
            new NativeAxisInput(NativeAxisInput.AxisType.MOUSE, "X")
                    .setEvent(delta -> System.out.println("Mouse X axis moved " + delta));

// 创建鼠标 Y 轴输入
            new NativeAxisInput(NativeAxisInput.AxisType.MOUSE, "Y")
                    .setEvent(delta -> System.out.println("Mouse Y axis moved " + delta));


            new NativeAxisInput(NativeAxisInput.AxisType.MOUSE_WHEEL, "V")
                    .setEvent(delta -> System.out.println("vertical mouse wheel " + delta));

            new NativeAxisInput(NativeAxisInput.AxisType.MOUSE_WHEEL, "H")
                    .setEvent(delta -> System.out.println("horizontal mouse wheel " + delta));

            // 创建一个需要长按 1 秒才会触发的按键
            new ChainAbleInput("A")
                    .hold(1, ChronoUnit.SECONDS)
                    .setEvent(() -> System.out.println("A pressed (hold for 1 second)"));

// 创建一个需要长按 500 毫秒才会触发的鼠标按键
            new ChainAbleInput(MOUSE_LEFT_BUTTON)
                    .hold(Duration.ofMillis(500))
                    .setEvent(() -> System.out.println("Mouse left button pressed (hold for 500ms)"));


    }
}
