package io.github.sweetzonzi.machine_max.util;

import io.github.sweetzonzi.machine_max.external.js.hook.Hook;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 手柄输入处理器。<br>
 * 注意：GLFW 的初始化由 Minecraft/LWJGL3 在启动时完成，此类的所有 GLFW 调用
 * 都应仅在主渲染线程（Client Tick）上执行，以避免 macOS 上的线程检查异常。
 */
public class MMJoystickHandler {

    // 用于存储每个手柄的按钮状态
    public static final boolean[][] buttonStates = new boolean[GLFW.GLFW_JOYSTICK_LAST][];
    // 用于存储每个手柄的摇杆（轴）状态
    public static final float[][] axisStates = new float[GLFW.GLFW_JOYSTICK_LAST][];

    // 标记手柄回调是否已注册（懒加载，确保在主线程上执行）
    private static final AtomicBoolean joystickCallbackRegistered = new AtomicBoolean(false);

    /**
     * 初始化手柄回调函数。<br>
     * 此方法必须在主渲染线程上调用。<br>
     * 由 {@link #refreshState()} 在首次调用时自动触发。
     */
    private static void init() {
        // 设置手柄连接/断开回调
        GLFW.glfwSetJoystickCallback((jid, event) -> {
            if (event == GLFW.GLFW_CONNECTED) {
                connected(jid);
                System.out.println("Joystick connected: " + jid);
            } else if (event == GLFW.GLFW_DISCONNECTED) {
                disconnected(jid);
                System.out.println("Joystick disconnected: " + jid);
            }
        });
    }

    private static void connected(int jid) {
        Hook.run(jid);
    }
    private static void disconnected(int jid) {
        Hook.run(jid);
    }

    /**
     * 每帧调用，更新所有已连接的手柄的输入状态。<br>
     * 首次调用时会自动在主线程上注册手柄回调（懒加载）。
     */
    public static void refreshState() {
        // 懒加载：首次在主线程调用 refreshState 时注册手柄回调
        if (joystickCallbackRegistered.compareAndSet(false, true)) {
            init();
        }
        for (int i = 0; i < GLFW.GLFW_JOYSTICK_LAST; i++) {
            if (GLFW.glfwJoystickPresent(i)) {
                updateJoystickInput(i);
            }
        }
    }


    /**
     * 更新指定手柄的按钮和摇杆状态
     * @param joystickID 手柄ID
     */
    private static void updateJoystickInput(int joystickID) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // 获取按钮输入
            ByteBuffer buttons = glfwGetJoystickButtons(joystickID);
            if (buttons != null) {
                buttonStates[joystickID] = new boolean[buttons.remaining()];
                for (int i = 0; i < buttons.remaining(); i++) {
                    buttonStates[joystickID][i] = (buttons.get(i) == GLFW.GLFW_PRESS);
                }
            }

            // 获取摇杆（轴）输入
            FloatBuffer axes = glfwGetJoystickAxes(joystickID);
            if (axes != null) {
                axisStates[joystickID] = new float[axes.remaining()];
                for (int i = 0; i < axes.remaining(); i++) {
                    axisStates[joystickID][i] = axes.get(i);
                }
            }
        }
    }

    /**
     * 获取指定手柄的按钮状态
     * @param joystickID 手柄ID
     * @param buttonID 按钮ID
     * @return 如果按钮按下则返回 true，未按下则返回 false
     */
    public static boolean isButtonPressed(int joystickID, int buttonID) {
        if (GLFW.glfwJoystickPresent(joystickID)) {
            if (buttonStates[joystickID] != null && buttonID >= 0 && buttonID < buttonStates[joystickID].length) {
                return buttonStates[joystickID][buttonID];
            }
        }

        return false;
    }

    /**
     * 获取指定手柄的摇杆（轴）状态
     * @param joystickID 手柄ID
     * @param axisID 摇杆轴ID
     * @return 摇杆轴的当前值（范围通常为 -1 到 1）
     */
    public static float getAxisState(int joystickID, int axisID) {
        if (GLFW.glfwJoystickPresent(joystickID)) {
            if (axisStates[joystickID] != null && axisID >= 0 && axisID < axisStates[joystickID].length) {
                return axisStates[joystickID][axisID];
            }
        }
        return 0.0f; // 默认值
    }

    /**
     * 获取指定手柄的按钮输入
     * @param jid 手柄ID
     * @return 按钮状态的缓冲区
     */
    private static ByteBuffer glfwGetJoystickButtons(int jid) {
        return GLFW.glfwGetJoystickButtons(jid);
    }

    /**
     * 获取指定手柄的摇杆（轴）输入
     * @param jid 手柄ID
     * @return 摇杆状态的缓冲区
     */
    private static FloatBuffer glfwGetJoystickAxes(int jid) {
        return GLFW.glfwGetJoystickAxes(jid);
    }


}

