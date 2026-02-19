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

/**
 * 来自 <a href="https://github.com/Liruochen1207/jnativehook">ArcherLee 魔改版 jnativehook</a>,
 * 支持了mc环境下查找dll文件
 * <p>
 * <a href="https://github.com/kwhat/jnativehook">jnativehook</a> 是一个基于C的原生输入库
 * <p>
 * 原作者: <a href="https://github.com/kwhat">kwhat</a>
 */

public class NativeKeyListener implements NativeMouseInputListener, NativeMouseWheelListener, com.github.kwhat.jnativehook.keyboard.NativeKeyListener {
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
//        System.out.println("Mosue Wheel Moved: " + e.getWheelRotation());
    }

    public void nativeKeyPressed(NativeKeyEvent e) {
//        System.out.println("Key Pressed: " + NativeKeyEvent.getKeyText(e.getKeyCode()));

        if (e.getKeyCode() == NativeKeyEvent.VC_ESCAPE) {
            try {
                GlobalScreen.unregisterNativeHook();
            } catch (NativeHookException nativeHookException) {
                nativeHookException.printStackTrace();
            }
        }
    }

    public void nativeKeyReleased(NativeKeyEvent e) {
//        System.out.println("Key Released: " + NativeKeyEvent.getKeyText(e.getKeyCode()));
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
