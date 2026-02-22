package io.github.sweetzonzi.machine_max.client.input;

import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 轴输入处理类，支持鼠标和手柄的轴输入
 * <p>
 * 该类用于处理各种轴类型的输入事件，包括鼠标移动、鼠标滚轮和游戏手柄轴输入。
 * 支持设置触发阈值，只有超过阈值的输入才会触发事件。
 * <p>
 * 使用示例：
 * <pre>
 * // 创建鼠标 X 轴输入
 * new NativeAxisInput(NativeAxisInput.AxisType.MOUSE, "X")
 *         .setThreshold(1.0)
 *         .setEvent(delta -> System.out.println("Mouse X axis moved " + delta));
 * </pre>
 * 
 * @author ArcherLee
 * @version 1.0.0
 */
public class NativeAxisInput {
    public enum AxisType {
        MOUSE,
        MOUSE_WHEEL,
        GAMEPAD
    }
    
    @FunctionalInterface
    public interface AxisEvent {
        void event(double delta);
    }
    
    @Getter
    private final AxisType axisType;
    
    @Getter
    private final String axisName;
    
    @Getter
    private AxisEvent event;
    
    @Getter
    private double threshold = 0.5;
    
    @Getter
    private double lastValue = 0;
    
    // 存储所有轴输入
    public static final Map<AxisType, Map<String, Set<NativeAxisInput>>> axisInputsMap = new HashMap<>();
    
    static {
        // 初始化轴输入映射
        for (AxisType type : AxisType.values()) {
            axisInputsMap.put(type, new HashMap<>());
        }
    }
    
    public NativeAxisInput(AxisType axisType, String axisName) {
        axisName = axisName.toLowerCase();
        this.axisType = axisType;
        this.axisName = axisName;
        axisInputsMap.get(axisType).computeIfAbsent(axisName, k -> new HashSet<>()).add(this);
    }
    
    public NativeAxisInput(AxisType axisType, String axisName, double threshold) {
        this(axisType, axisName);
        this.threshold = threshold;
    }
    
    public NativeAxisInput setEvent(AxisEvent event) {
        this.event = event;
        return this;
    }
    
    public NativeAxisInput setEventOnce(AxisEvent event) {
        if (this.event == null) this.event = event;
        return this;
    }
    
    public NativeAxisInput setThreshold(double threshold) {
        this.threshold = threshold;
        return this;
    }
    
    public void updateValue(double value) {
        this.lastValue = value;
    }
    
    public boolean shouldTrigger(double delta) {
        return Math.abs(delta) >= threshold;
    }
}
