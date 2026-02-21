package io.github.sweetzonzi.machine_max.client.input;

import lombok.Getter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 轴输入处理类，支持鼠标和手柄的轴输入
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
