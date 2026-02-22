package io.github.sweetzonzi.machine_max.client.input;


import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseInputListener;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelEvent;
import com.github.kwhat.jnativehook.mouse.NativeMouseWheelListener;
import com.jme3.system.JmeSystem;
import com.jme3.system.Platform;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 来自 <a href="https://github.com/Liruochen1207/jnativehook">ArcherLee 魔改版 jnativehook</a>,
 * 支持了mc环境下查找dll文件
 * <p>
 * <a href="https://github.com/kwhat/jnativehook">jnativehook</a> 是一个基于C的原生输入库
 * <p>
 * 原作者: <a href="https://github.com/kwhat">kwhat</a>
 */

public class NativeKeyListener implements NativeMouseInputListener, NativeMouseWheelListener, com.github.kwhat.jnativehook.keyboard.NativeKeyListener {
    // 优化：使用更高效的数据结构存储组合键事件
    public static final Map<String, List<NativeInput>> combineKeyInputsMap = new ConcurrentHashMap<>();
    public static final Map<String, List<NativeInput>> pressKeyInputs = new ConcurrentHashMap<>();
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
    private static final Map<String, Set<NativeInput>> holdEventTriggered = new ConcurrentHashMap<>();

    public static final String MOUSE_LEFT_BUTTON = "mouse1";
    public static final String MOUSE_RIGHT_BUTTON = "mouse2";
    public static final String MOUSE_WHEEL_BUTTON = "mouse3";
    public static final String MOUSE_BACKWARD_BUTTON = "mouse4";
    public static final String MOUSE_FORWARD_BUTTON = "mouse5";

    static {
        new Thread(() -> {
            while (true) {
                try {
                    for (String keyText : pressKeyStatus.keySet()) {
                        if (pressKeyStatus.getOrDefault(keyText, false)) {
                            keyStartPressTimes.putIfAbsent(keyText, System.nanoTime());
                            processKeyPress(keyText);
                        } else {
                            keyStartPressTimes.remove(keyText);
                        }
                    }
                    Thread.sleep(Duration.of(10, ChronoUnit.NANOS));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }).start();
    }

    private String getMouseName(int mouseButton) {
        return "mouse" + mouseButton;
    }

	public void nativeMousePressed(NativeMouseEvent e) {
        String keyText = getMouseName(e.getButton()).toLowerCase();
        // 处理按键事件
        processKeyPress(keyText);
	}

	public void nativeMouseReleased(NativeMouseEvent e) {
        String keyText = getMouseName(e.getButton()).toLowerCase();
        // 并入键盘按键事件逻辑
        pressKeyStatus.put(keyText, false);
        // 清除已触发的长按事件记录
        holdEventTriggered.remove(keyText);
        keyStartPressTimes.remove(keyText);
        combineKeyStatus.clear();
        // 注意：不再需要在按键释放时重置连按计数，因为连按计数是基于时间窗口的
        // 当两次按键间隔超过时间窗口时，会自动重置计数
	}

	public void nativeMouseMoved(NativeMouseEvent e) {
//        System.out.println("Mouse Moved: " + e.getX() + ", " + e.getY());
        // 计算鼠标移动的 delta 量
        int deltaX = e.getX() - lastMouseX;
        int deltaY = e.getY() - lastMouseY;

        // 更新鼠标位置
        lastMouseX = e.getX();
        lastMouseY = e.getY();

        // 处理鼠标 X 轴输入
        processAxisInput(NativeAxisInput.AxisType.MOUSE, "x", deltaX);

        // 处理鼠标 Y 轴输入
        processAxisInput(NativeAxisInput.AxisType.MOUSE, "y", deltaY);
    }

    public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
        String direction = "unknown";
        if (e.getWheelDirection() == NativeMouseWheelEvent.WHEEL_VERTICAL_DIRECTION) {
            direction = "v";
        }
        if (e.getWheelDirection() == NativeMouseWheelEvent.WHEEL_HORIZONTAL_DIRECTION) {
            direction  = "h";
        }

        processAxisInput(NativeAxisInput.AxisType.MOUSE_WHEEL, direction, e.getWheelRotation());
    }

    public void nativeKeyPressed(NativeKeyEvent e) {
        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
        if (DEBUG) System.out.println("NativeKeyListener: Key Pressed: " + keyText);
        // 处理按键事件
        processKeyPress(keyText);
    }

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
        List<NativeInput> nativeInputs = pressKeyInputs.get(keyText);
        if (DEBUG) System.out.println("NativeKeyListener: Native inputs for " + keyText + ": " + (nativeInputs != null ? nativeInputs.size() : 0));
        if (nativeInputs != null && !nativeInputs.isEmpty()) {
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

            // 检查每个 NativeInput 是否达到所需的按键次数aa
            for (NativeInput nativeInput : nativeInputs) {
                if (nativeInput.getHoldTimeNanos() - HOLDING_THRESHOLD >= pressLength || nativeInput.getHoldTimeNanos() + HOLDING_THRESHOLD <= pressLength) {
                    continue;
                }

                // 检查是否是长按事件且已经触发过
                boolean isHoldEvent = nativeInput.getHoldTimeNanos() != 0;
                if (isHoldEvent) {
                    Set<NativeInput> triggeredEvents = holdEventTriggered.getOrDefault(keyText, new HashSet<>());
                    if (triggeredEvents.contains(nativeInput)) {
                        continue;
                    }
                }

                if (nativeInput.getEvent() != null) {
                    int requiredPressTimes = nativeInput.getPressTimes();
                    if (DEBUG) System.out.println("NativeKeyListener: Checking if " + currentCount + " matches " + requiredPressTimes);
                    // 修复：只要计数是所需次数的倍数，就触发事件
                    if (currentCount % requiredPressTimes == 0) {
                        if (DEBUG) System.out.println("NativeKeyListener: Press count matched, running event for input with pressTimes: " + requiredPressTimes);
                        // 优化：异步处理事件
                        eventExecutor.execute(nativeInput.getEvent());

                        // 如果是长按事件，标记为已触发
                        if (isHoldEvent) {
                            holdEventTriggered.computeIfAbsent(keyText, k -> new HashSet<>()).add(nativeInput);
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
        List<NativeInput> matchingCombineInputs = combineKeyInputsMap.get(combinedKeyName);
        if (matchingCombineInputs != null && !matchingCombineInputs.isEmpty()) {
            for (NativeInput input : matchingCombineInputs) {

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
                .filter(entry -> entry.getValue())
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
    private void processAxisInput(NativeAxisInput.AxisType axisType, String axisName, double delta) {
        Map<String, Set<NativeAxisInput>> axisMap = NativeAxisInput.axisInputsMap.get(axisType);
        if (axisMap != null) {
            Set<NativeAxisInput> axisInputs = axisMap.get(axisName);
            if (axisInputs != null && !axisInputs.isEmpty()) {
                for (NativeAxisInput axisInput : axisInputs) {
                    if (axisInput.getEvent() != null && axisInput.shouldTrigger(delta)) {
                        // 优化：异步处理事件并传入 delta 参数
                        final double finalDelta = delta;
                        eventExecutor.execute(() -> axisInput.getEvent().event(finalDelta));
                        // 更新轴输入的当前值
                        axisInput.updateValue(axisInput.getLastValue() + delta);
                    }
                }
            }
        }
    }

    public void nativeKeyReleased(NativeKeyEvent e) {
        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode()).toLowerCase();
        if (DEBUG) System.out.println("NativeKeyListener: Key Released: " + keyText);
        pressKeyStatus.put(keyText, false);
        keyStartPressTimes.remove(keyText);
        // 清除已触发的长按事件记录
        holdEventTriggered.remove(keyText);
        combineKeyStatus.clear();
        // 注意：不再需要在按键释放时重置连按计数，因为连按计数是基于时间窗口的
        // 当两次按键间隔超过时间窗口时，会自动重置计数
    }

    public void nativeKeyTyped(NativeKeyEvent e) {
//        在 Pressed和 Released事件之间，当一次按键动作产生了一个可显示的字符时触发。暂时想不到用处
//        System.out.println("Key Typed: " + e.getKeyText(e.getKeyCode()));
    }

    public static void setUp() {
        try {
            // 配置dll库在run路径下的相对子路径
            Platform platform = JmeSystem.getPlatform();
            switch (platform.getOs()) {
                case Windows -> {
                    MMDynamicRes.tempResourceToFile("jNativeLib/windows/arm", "JNativeHook.dll");
                    MMDynamicRes.tempResourceToFile("jNativeLib/windows/x86", "JNativeHook.dll");
                    MMDynamicRes.tempResourceToFile("jNativeLib/windows/x86_64","JNativeHook.dll");

                }
                case MacOS -> {
                    MMDynamicRes.tempResourceToFile("jNativeLib/darwin/arm64", "libJNativeHook.dylib");
                    MMDynamicRes.tempResourceToFile("jNativeLib/darwin/x86_64", "libJNativeHook.dylib");

                }
                case Android, Linux -> {
                    MMDynamicRes.tempResourceToFile("jNativeLib/linux/arm", "libJNativeHook.so");
                    MMDynamicRes.tempResourceToFile("jNativeLib/linux/arm64", "libJNativeHook.so");
                    MMDynamicRes.tempResourceToFile("jNativeLib/linux/x86", "libJNativeHook.so");
                    MMDynamicRes.tempResourceToFile("jNativeLib/linux/x86_64", "libJNativeHook.so");
                }
            }


            System.setProperty("jnativehook.max_machine.path"
                    , MMDynamicRes.getLibrary("jNativeLib"));

            GlobalScreen.registerNativeHook();
        }
        catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());
        }

        // Construct the example object.
        NativeKeyListener listener = new NativeKeyListener();

        // Add the appropriate listeners.
        GlobalScreen.addNativeMouseListener(listener);
        GlobalScreen.addNativeMouseMotionListener(listener);
        GlobalScreen.addNativeMouseWheelListener(listener);
        GlobalScreen.addNativeKeyListener(listener);
    }

    public static void main(String[] args) {
        try {
            new NativeInput("a", 2)
                    .chain(new NativeInput("ctrl"))
                    .setEvent(() -> {
                        System.out.println("NativeInput Double Ctrl A");
                    });
            new NativeInput("A")
                    .chain(new NativeInput("ctrl")).setEvent(() -> {
                        System.out.println("NativeInput Ctrl A");
                    });
            new NativeInput("A")
                    .chain(new NativeInput("Shift"))
                    .chain(new NativeInput("Ctrl"))
                    .setEvent(() -> System.out.println("NativeInput Ctrl+Shift+A"));

            new NativeInput("A").setEvent(() -> System.out.println("A"));

            new NativeInput(MOUSE_LEFT_BUTTON)
                    .chain(new NativeInput("Ctrl"))
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
            new NativeInput("A")
                    .hold(1, ChronoUnit.SECONDS)
                    .setEvent(() -> System.out.println("A pressed (hold for 1 second)"));

// 创建一个需要长按 500 毫秒才会触发的鼠标按键
            new NativeInput(MOUSE_LEFT_BUTTON)
                    .hold(Duration.ofMillis(500))
                    .setEvent(() -> System.out.println("Mouse left button pressed (hold for 500ms)"));

        System.setProperty("jnativehook.max_machine.path"
                , "src/main/resources/natives/jNativeLib");

        GlobalScreen.registerNativeHook();
    }
        catch (NativeHookException ex) {
        System.err.println("There was a problem registering the native hook.");
        System.err.println(ex.getMessage());
    }

    // Construct the example object.
    NativeKeyListener example = new NativeKeyListener();

    // Add the appropriate listeners.
        GlobalScreen.addNativeMouseListener(example);
        GlobalScreen.addNativeMouseMotionListener(example);
        GlobalScreen.addNativeMouseWheelListener(example);
        GlobalScreen.addNativeKeyListener(example);

}
}
