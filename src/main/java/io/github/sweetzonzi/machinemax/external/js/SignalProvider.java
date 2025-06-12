package io.github.sweetzonzi.machinemax.external.js;


import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import io.github.sweetzonzi.machinemax.util.MMJoystickHandler;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static io.github.sweetzonzi.machinemax.external.js.hook.Hook.SIGNAL_MAP;

public class SignalProvider {
    public static String key(String name) {
        return "key.keyboard."+ name;
    }

    public static boolean getKeyStatus(String name) {
        if (SIGNAL_MAP.get(SignalProvider.key(name)) instanceof Double d) {
            keyTick(name);
            return d != 0.0;
        }
        return false;
    }
    public static Double getKeyTicks(String name) {
        if (SIGNAL_MAP.get(SignalProvider.key(name)) instanceof Double d) {
            keyTick(name);
            return d;
        }
        return 0.0;
    }

    public static void keyTick(String name) { //按键按下记录+1
        signalTick(SignalProvider.key(name));
    }

    public static void signalTick(String name) { //信号获取后记录+1
        if (Hook.SIGNAL_MAP.get(name) != 0) Hook.SIGNAL_MAP.put(name,Hook.SIGNAL_MAP.get(name) + 1);
    }

    public static void gamepadInit() {
        MMJoystickHandler.init();//游戏手柄读取初始化
    }

    public static boolean containsGamepad(int id) { //通过id判断一个手柄是否存在
        return GLFW.glfwJoystickPresent(id);
    }
    public static List<Integer> connectedGamepadIdList(int id) { //所有当前连接的手柄id列表
        List<Integer> li = new ArrayList<>();
        for (int i = 0; i < GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (containsGamepad(i)) {
                li.add(i);
            }
        }
        return li;
    }

    public static void gamepadFlush() {
        MMJoystickHandler.refreshState();
    }

    public static boolean gamepadButton(int id, int button) {
        return MMJoystickHandler.factoryGamePadButtonEvent(id, button);
    }
    public static float gamepadAxis(int id, int axis) {
        return MMJoystickHandler.factoryGamePadAxisEvent(id, axis);
    }

    public static final int
            A            = 0,
            B            = 1,
            X            = 2,
            Y            = 3,
            LEFT_BUMPER  = 4,
            RIGHT_BUMPER = 5,
            BACK         = 6,
            START        = 7,
            GUIDE        = 8,
            LEFT_THUMB   = 9,
            RIGHT_THUMB  = 10,
            DPAD_UP      = 11,
            DPAD_RIGHT   = 12,
            DPAD_DOWN    = 13,
            DPAD_LEFT    = 14,
            CROSS        = A,
            CIRCLE       = B,
            SQUARE       = X,
            TRIANGLE     = Y;
    public static final int
            LEFT_X        = 0,
            LEFT_Y        = 1,
            RIGHT_X       = 2,
            RIGHT_Y       = 3,
            LEFT_TRIGGER  = 4,
            RIGHT_TRIGGER = 5;
}
