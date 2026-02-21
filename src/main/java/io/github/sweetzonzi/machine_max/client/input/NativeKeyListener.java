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

import java.util.*;
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
    public static final Map<String, Set<NativeInput>> combineKeyInputsMap = new HashMap<>();
    public static final Map<String, Set<NativeInput>> pressKeyInputs = new HashMap<>();
    public static final Map<String, Boolean> pressKeyStatus = new HashMap<>();
    
    // 调试日志开关
    private static final boolean DEBUG = false;
    
    // 优化：使用更高效的 Map 实现
    private static final Map<String, Integer> keyPressCounts = new HashMap<>();
    // 为每个按键维护最后按键时间戳
    private static final Map<String, Long> keyLastPressTimes = new HashMap<>();
    // 为每个组合键维护连按计数
    private static final Map<String, Integer> combineKeyPressCounts = new HashMap<>();
    // 为每个组合键维护最后按键时间戳
    private static final Map<String, Long> combineKeyLastPressTimes = new HashMap<>();
    // 连按时间窗口（毫秒）
    private static final long PRESS_WINDOW_MS = 300;
    
    // 优化：添加线程池用于异步处理事件
    private static final ExecutorService eventExecutor = Executors.newCachedThreadPool();

	public void nativeMouseClicked(NativeMouseEvent e) {
//		System.out.println("Mouse Clicked: " + e.getClickCount());
	}

	public void nativeMousePressed(NativeMouseEvent e) {
//		System.out.println("Mouse Pressed: " + e.getButton());
	}

	public void nativeMouseReleased(NativeMouseEvent e) {
//		System.out.println("Mouse Released: " + e.getButton());
	}

	public void nativeMouseMoved(NativeMouseEvent e) {
//		System.out.println("Mouse Moved: " + e.getX() + ", " + e.getY());
	}

    public void nativeMouseWheelMoved(NativeMouseWheelEvent e) {
//        System.out.println("Mouse Wheel Moved: " + e.getWheelRotation());
    }

    public void nativeKeyPressed(NativeKeyEvent e) {
        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode());
        if (DEBUG) System.out.println("NativeKeyListener: Key Pressed: " + keyText);
        pressKeyStatus.put(keyText, true);
        
        // 优化：减少重复的组合键名称计算
        String combinedKeyName = generateCombinedKeyName();

        // 处理单个按键事件，无论是否有组合键事件
        // 这样可以确保即使在按下组合键时，单个按键的连按事件仍然能够被处理
        Set<NativeInput> nativeInputs = pressKeyInputs.get(keyText);
        if (DEBUG) System.out.println("NativeKeyListener: Native inputs for " + keyText + ": " + (nativeInputs != null ? nativeInputs.size() : 0));
        if (nativeInputs != null && !nativeInputs.isEmpty()) {
            // 更新全局连按计数
            long currentTime = System.currentTimeMillis();
            long lastPressTime = keyLastPressTimes.getOrDefault(keyText, 0L);
            long timeDiff = currentTime - lastPressTime;
            
            int currentCount = keyPressCounts.getOrDefault(keyText, 0);
            if (lastPressTime == 0 || timeDiff > PRESS_WINDOW_MS) {
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
            
            // 检查每个 NativeInput 是否达到所需的按键次数
            for (NativeInput nativeInput : nativeInputs) {
                if (nativeInput.getEvent() != null) {
                    int requiredPressTimes = nativeInput.getPressTimes();
                    if (DEBUG) System.out.println("NativeKeyListener: Checking if " + currentCount + " matches " + requiredPressTimes);
                    // 修复：只要计数是所需次数的倍数，就触发事件
                    if (currentCount % requiredPressTimes == 0) {
                        if (DEBUG) System.out.println("NativeKeyListener: Press count matched, running event for input with pressTimes: " + requiredPressTimes);
                        // 优化：异步处理事件
                        eventExecutor.execute(nativeInput.getEvent());
                    }
                }
            }
        }

        // 运行组合键事件
        if (DEBUG) System.out.println("NativeKeyListener: Looking for combine key inputs with name " + combinedKeyName);
        
        // 先更新组合键连按计数
        long currentTime = System.currentTimeMillis();
        long lastPressTime = combineKeyLastPressTimes.getOrDefault(combinedKeyName, 0L);
        long timeDiff = currentTime - lastPressTime;
        
        int currentCount = combineKeyPressCounts.getOrDefault(combinedKeyName, 0);
        if (lastPressTime == 0 || timeDiff > PRESS_WINDOW_MS) {
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
        Set<NativeInput> matchingCombineInputs = combineKeyInputsMap.get(combinedKeyName);
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

    }
    
    // 优化：提取组合键名称生成逻辑为单独方法
    private String generateCombinedKeyName() {
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

    public void nativeKeyReleased(NativeKeyEvent e) {
        String keyText = NativeKeyEvent.getKeyText(e.getKeyCode());
        if (DEBUG) System.out.println("NativeKeyListener: Key Released: " + keyText);
        pressKeyStatus.put(keyText, false);
        
        // 注意：不再需要在按键释放时重置连按计数，因为连按计数是基于时间窗口的
        // 当两次按键间隔超过时间窗口时，会自动重置计数
    }

    public void nativeKeyTyped(NativeKeyEvent e) {
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
            new NativeInput("A", 2)
                    .chain(new NativeInput("Ctrl"))
                    .setEvent(() -> {
                        System.out.println("NativeInput Double Ctrl A");
                    });
            new NativeInput("A")
                    .chain(new NativeInput("Ctrl")).setEvent(() -> {
                        System.out.println("NativeInput Ctrl A");
                    });
            new NativeInput("A")
                    .chain(new NativeInput("Shift"))
                    .chain(new NativeInput("Ctrl"))
                    .setEvent(() -> System.out.println("NativeInput Ctrl+Shift+A"));
            new NativeInput("A").setEvent(() -> System.out.println("A"));

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
