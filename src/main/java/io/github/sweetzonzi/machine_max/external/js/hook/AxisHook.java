package io.github.sweetzonzi.machine_max.external.js.hook;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 轴事件钩子类，用于监听和处理轴输入事件
 * <p>
 * 该类提供了一个统一的接口来处理各种轴输入事件，如鼠标移动、滚轮滚动等。
 * 它使用线程池异步处理事件，确保事件处理不会阻塞主线程。
 * <p>
 * 主要功能：
 * 1. 注册轴类型并监听其数据变化
 * 2. 统一的事件处理机制，支持异步事件触发
 * 3. 自动处理空值，将null转换为0D
 * 4. 保持轴类型的注册顺序，确保事件回调中数据顺序的一致性
 */
public class AxisHook {
    public static final String AxisDataHead = "axis."; //不会自增的数据 多用于轴输入
    private final static Set<AxisHook> axisHookList = new HashSet<>();
    /// 优化：添加线程池用于异步处理事件 暂时仅为轴输入支持
    private static final ExecutorService eventExecutor = Executors.newCachedThreadPool();

    private final Map<AxisType, Double> dataMap = new ConcurrentHashMap<>();
    private final List<AxisType> axisTypesList = new ArrayList<>(); // 保存轴类型的顺序

    private AxisEvent axisEvent;

    @FunctionalInterface
    public interface AxisEvent {
        void event(double... delta);
    }

    /**
     * @param types 怎么写入的类型顺序，拿到就是什么顺序的数据
     * */
    public static AxisHook createWith(AxisType... types) {
        AxisHook instance = new AxisHook();
        for (AxisType type : types) {
            instance.dataMap.put(type, 0D); //todo 默认值暂时为0，以后也许要为不同类型定义各自定义默认值
            instance.axisTypesList.add(type); // 保存轴类型的顺序
        }
        axisHookList.add(instance);
        return instance;
    }

    public AxisHook EVENT(AxisEvent event) {
        axisEvent = event;
        return this;
    }

    public enum AxisType {
        XScroll, // 鼠标水平滚轮
        YScroll, // 鼠标垂直滚轮
        XDelta, // 鼠标水平Delta移动
        YDelta, // 鼠标垂直Delta移动
        XPosition, // 鼠标水平绝对位置
        YPosition, // 鼠标垂直绝对位置
        ;
        @Override
        public String toString() {
            return AxisDataHead + name();
        }

        /// 用于取消 mixin行为 后面实现
        private boolean isCancel = false;

        public void startCancel() {
            isCancel = true;
        }

        public void stopCancel() {
            isCancel = false;
        }

        public boolean isCancel() {
            return isCancel;
        }
    }

    /**
     * 数据源注入方法，标明数据类型和值即可
     * */
    public static void putAxisData(AxisType type, Double data) {
        for (AxisHook ah : axisHookList) {
            ah.dataMap.put(type, data);
            ah.triggerEvent(); // 数据输入时触发事件
        }
    }

    /**
     * 触发事件的方法
     * */
    private void triggerEvent() {
        if (axisEvent != null) {
            double[] values = new double[axisTypesList.size()];
            int i = 0;
            for (AxisType axisType : axisTypesList) {
                Double v = dataMap.get(axisType);
                if (v == null) v = 0D;
                values[i] = v;
                i++;
            }
            eventExecutor.execute(() -> {axisEvent.event(values);});
        }
    }

/// 使用示范
    public static void main(String[] args) {
        AxisHook.createWith(AxisType.XDelta, AxisType.YDelta)
                .EVENT(bind -> {  // 统一使用 EVENT 方法，遇空自动覆盖为0
            double x = bind[0];
            double y = bind[1];
        });

        AxisHook.createWith(AxisType.XDelta, AxisType.YDelta, AxisType.XScroll, AxisType.XPosition)
                .EVENT(bind -> { // 统一使用 EVENT 方法，遇空自动覆盖为0
            double x = bind[0];
            double y = bind[1];
            double xScroll = bind[2];
            double xPosition = bind[3];
        });
    }
}
