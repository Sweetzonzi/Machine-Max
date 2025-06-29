package io.github.sweetzonzi.machinemax.util;

import io.github.sweetzonzi.machinemax.external.js.hook.Hook;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
@EventBusSubscriber(bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class MMJoystickHandler {

    // 用于存储每个手柄的按钮状态
    public static final boolean[][] buttonStates = new boolean[GLFW.GLFW_JOYSTICK_LAST][];
    // 用于存储每个手柄的摇杆（轴）状态
    public static final float[][] axisStates = new float[GLFW.GLFW_JOYSTICK_LAST][];

    /**
     * 初始化 GLFW 和设置手柄回调函数
     */
    @SubscribeEvent
    public static void init(FMLCommonSetupEvent e) {
        if (!GLFW.glfwInit()) {
            System.out.println("Failed to initialize GLFW");
            return;
        }

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
     * 每帧调用，更新所有已连接的手柄的输入状态
     */
    public static void refreshState() {
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
