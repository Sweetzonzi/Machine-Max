package io.github.sweetzonzi.machine_max.client.input;

import org.lwjgl.glfw.GLFW;

public class GLFWKeyContext extends InputContext<Integer> {

    public GLFWKeyContext(Integer data) {
        super(data);
    }

    @Override
    public String getKeyText() {
        return GLFW.glfwGetKeyName(data, GLFW.glfwGetKeyScancode(data));
    }
}
