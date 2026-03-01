package io.github.sweetzonzi.machine_max.external.js.hook;

import lombok.Getter;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AxisHook {
    public static final String AxisDataHead = "axis."; //不会自增的数据 多用于轴输入
    private final static List<AxisHook> axisHookList = new ArrayList<>();
    private NotNullAxisEvent notNullAxisEvent;
    private NullableAxisEvent nullableAxisEvent;
    /// 优化：添加线程池用于异步处理事件 暂时仅为轴输入支持
    private static final ExecutorService eventExecutor = Executors.newCachedThreadPool();

    @Getter
    private final Map<AxisType, Double> dataMap = new ConcurrentHashMap<>();

    private boolean nullable = false;

    static {
        new Thread(()->{
            while (true) {
                try {
                    Thread.sleep(Duration.of(40, ChronoUnit.MILLIS));

                    for (AxisHook ah : axisHookList) {
                        Double[] nullableData = new Double[ah.dataMap.size()];
                        double[] values = new double[ah.dataMap.size()];

                        int i = 0;
                        for (AxisType axisType : ah.dataMap.keySet()) {
                            Double v = ah.dataMap.get(axisType);
                            if (!ah.nullable && v == null) v = 0D;
                            nullableData[i] = v;
                            values[i] = v;
                            i ++;
                        }
                        if (ah.nullable) {
                            eventExecutor.execute(() -> {ah.nullableAxisEvent.event(nullableData);});
                        } else {
                            eventExecutor.execute(() -> {ah.notNullAxisEvent.event(values);});

                        }
                    }
                } catch (InterruptedException ignore) {}
            }
        }).start();
    }

    @FunctionalInterface
    public interface NotNullAxisEvent {
        void event(double... delta);
    }
    @FunctionalInterface
    public interface NullableAxisEvent {
        void event(Double... delta);
    }

    /**
     * @param types 怎么写入的类型顺序，拿到就是什么顺序的数据
     * */
    public static AxisHook createWith(AxisType... types) {
        AxisHook instance = new AxisHook();
        for (AxisType type : types) {
            instance.dataMap.put(type, null);
        }
        axisHookList.add(instance);
        return instance;
    }

    public void NotNullBind(NotNullAxisEvent event) {
        notNullAxisEvent = event;
    }
    public void NullableBind(NullableAxisEvent event) {
        nullable = true;
        nullableAxisEvent = event;
    }

    public enum AxisType {
        XScroll, // 鼠标水平滚轮
        YScroll, // 鼠标垂直滚轮
        XMove, // 鼠标水平Delta移动
        YMove, // 鼠标垂直Delta移动
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
            boolean b = isCancel;
            return b;
        }
    }

    /**
     * 数据源注入方法，标明数据类型和值即可
     * */
    public static void putAxisData(AxisType type, Double data) {
        for (AxisHook ah : axisHookList) {
            ah.dataMap.put(type, data);
        }
    }

/// 使用示范
    public static void main(String[] args) {
        AxisHook.createWith(AxisType.XMove, AxisType.YMove)
                .NotNullBind(bind -> {  //不可空数据，遇空自动覆盖为0
            double x = bind[0];
            double y = bind[1];
        });

        AxisHook.createWith(AxisType.XMove, AxisType.YMove, AxisType.XScroll, AxisType.XPosition)
                .NullableBind(bind -> { //可空数据，所以用 Double接收
            Double x = bind[0];
            Double y = bind[1];
            Double xScroll = bind[2];
            Double xPosition = bind[3];
        });
    }
}
