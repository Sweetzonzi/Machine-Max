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

/**
 * 来自 <a href="https://github.com/Liruochen1207/jnativehook">ArcherLee 魔改版 jnativehook</a>,
 * 支持了mc环境下查找dll文件
 * <p>
 * <a href="https://github.com/kwhat/jnativehook">jnativehook</a> 是一个基于C的原生输入库
 * <p>
 * 原作者: <a href="https://github.com/kwhat">kwhat</a>
 */

public class NativeKeyListener implements NativeMouseInputListener, NativeMouseWheelListener, com.github.kwhat.jnativehook.keyboard.NativeKeyListener {
    public static final Map<String, Set<NativeInput>> combineKeyInputs = new HashMap<>();
    public static final Map<String, Set<NativeInput>> pressKeyInputs = new HashMap<>();

    // 按键点击计数器，按按键类型分开计数
    private static final Map<String, Integer> keyClickCounts = new HashMap<>();
    // 记录最后一次输入的时间戳
    private static long lastInputTime = System.currentTimeMillis();
    // 记录当前按下的按键
    private static final Set<Integer> pressedKeys = new HashSet<>();

    private static boolean waitingForInputs = true;
    
    static {
        // 启动定时任务，每100毫秒检查一次是否超过220毫秒无输入
        new Thread(() -> {
            while (true) {
                try {
                    while (waitingForInputs) {
                        Thread.sleep(100);
                    }
                    if (System.currentTimeMillis() - lastInputTime > 220) {
                        synchronized (NativeKeyListener.class) {
                            if (System.currentTimeMillis() - lastInputTime > 220) {
                                // 生成组合键名称
                                String combinedKeyName = keyClickCounts.keySet().stream()
                                        .sorted((key1, key2) -> {
                                            int lengthCompare = Integer.compare(key2.length(), key1.length());
                                            if (lengthCompare != 0) {
                                                return lengthCompare;
                                            }
                                            return key1.compareTo(key2);
                                        })
                                        .map(node -> node + ":" + keyClickCounts.get(node))
                                        .reduce((key1, key2) -> key1 + "-" + key2)
                                        .orElse("");
                                
                                // 从nativeInputs中取出对应的Set<NativeInput>并运行所有event
                                Set<NativeInput> inputs = combineKeyInputs.get(combinedKeyName);
                                if (inputs != null) {
                                    for (NativeInput input : inputs) {
                                        if (input.getEvent() != null) {
                                            input.getEvent().run();
                                        }
                                    }
                                }
                                
                                keyClickCounts.clear();
                                waitingForInputs = true;
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }
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
//        System.out.println("Key Pressed: " + NativeKeyEvent.getKeyText(e.getKeyCode()));
        waitingForInputs = false;
        Set<NativeInput> nativeInputs = pressKeyInputs.get(NativeKeyEvent.getKeyText(e.getKeyCode()));
        if  (nativeInputs != null) {
            for (NativeInput nativeInput : nativeInputs) {
                if (nativeInput.getEvent() != null) {
                    nativeInput.getEvent().run();
                }
            }
        }
        synchronized (NativeKeyListener.class) {
            // 更新最后输入时间
            lastInputTime = System.currentTimeMillis();
            // 记录按下的按键
            pressedKeys.add(e.getKeyCode());
        }




    }

    public void nativeKeyReleased(NativeKeyEvent e) {
//        System.out.println("Key Released: " + NativeKeyEvent.getKeyText(e.getKeyCode()));
        waitingForInputs = false;
        synchronized (NativeKeyListener.class) {
            // 更新最后输入时间
            lastInputTime = System.currentTimeMillis();
            // 检查按键是否存在于按下集合中
            if (pressedKeys.remove(e.getKeyCode())) {
                // 获取按键的文本表示
                String keyText = NativeKeyEvent.getKeyText(e.getKeyCode());
                // 增加对应按键的点击计数
                if (keyClickCounts.size() < 6) //限制组合键长度为6，提高性能
                    keyClickCounts.put(keyText, keyClickCounts.getOrDefault(keyText, 0) + 1);
            }
        }
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
        NativeKeyListener example = new NativeKeyListener();

        // Add the appropriate listeners.
        GlobalScreen.addNativeMouseListener(example);
        GlobalScreen.addNativeMouseMotionListener(example);
        GlobalScreen.addNativeMouseWheelListener(example);
        GlobalScreen.addNativeKeyListener(example);
    }
}
