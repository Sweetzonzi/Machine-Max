package io.github.sweetzonzi.machinemax.external.js.hook;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.external.js.InputSignalProvider;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;

import static io.github.sweetzonzi.machinemax.external.js.hook.Hook.SIGNAL_MAP;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
@OnlyIn(Dist.CLIENT)
public class KeyHooks {
    public final static String INVERSE_NAME = "_inv";

    public static class EVENT {
        private final String keyName;
        public EVENT(String keyName) {
            this.keyName = InputSignalProvider.key(keyName);
        }
        public EVENT(KeyMapping mapping) {
            this.keyName = mapping.getKey().getName();
        }

        private double getDownSignalTick() {
            return InputSignalProvider.getSignalTicks(keyName);
        }
        private double getUpSignalTick() {
            return InputSignalProvider.getSignalTicks(keyName+INVERSE_NAME);
        }

        // 如果编辑器提醒你函数类型改成Void，请忽略，因为它们是链式调用设计模式

        /**
         * 绑定多类型键盘事件的统一处理方法
         * @param downEvent 按下事件处理器（当按下信号刻度(getDownSignalTick())等于1.0时触发执行）
         * @param upEvent 抬起事件处理器（当抬起信号刻度(getUpSignalTick())等于1.0时触发执行）
         * @param hoverEvent 长按事件处理器（执行时传入当前按下信号刻度值作为参数）
         * @param leaveEvent 抬起计时事件处理器（执行时传入当前抬起信号刻度值作为参数）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnAll(KeyDownEvent downEvent, KeyUpEvent upEvent, KeyHoverEvent hoverEvent, KeyLeaveEvent leaveEvent) {
            if (getDownSignalTick() == 1.0) downEvent.run();
            if (getUpSignalTick() == 1.0) upEvent.run();
            hoverEvent.run(getDownSignalTick());
            leaveEvent.run(getUpSignalTick());
            return this;
        }

        /**
         * 注册按键按下瞬间的事件处理器
         * @param downEvent 按下事件处理器（当按下信号刻度(getDownSignalTick())等于1.0时触发执行）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyDown(KeyDownEvent downEvent) {
            if (getDownSignalTick() == 1.0) downEvent.run();
            return this;
        }


        /**
         * 注册指定刻度阈值触发的按下事件处理器
         * 当按键按下后tick超过atTick时触发一次
         * @param atTick 触发阈值刻度（当按下信号刻度(getDownSignalTick())等于该值时触发）
         * @param downEvent 按下事件处理器（满足刻度条件时触发执行）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyDownAtTick(double atTick, KeyDownEvent downEvent) {
            if (getDownSignalTick() == atTick) downEvent.run();
            return this;
        }


        /**
         * 注册按键抬起瞬间的事件处理器
         * @param upEvent 抬起事件处理器（当抬起信号刻度(getUpSignalTick())等于1.0时触发执行）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyUp(KeyUpEvent upEvent) {
            if (getUpSignalTick() == 1.0) upEvent.run();
            return this;
        }

        /**
         * 注册长按基础事件处理器（返回完整按下时长tick）
         * @param hoverEvent 长按事件处理器（执行时传入当前完整的按下信号刻度值作为参数）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyHover(KeyHoverEvent hoverEvent) {
            hoverEvent.run(getDownSignalTick());
            return this;
        }

        /**
         * 注册指定刻度区间内的长按事件处理器（返回区间内相对计时）
         * 规定了一个从from开始到to的区间, 当按键按下且tick在区间中时均会触发，并返回从区间开始的归零计数
         * @param from 区间起始刻度（包含）
         * @param to 区间结束刻度（包含，需大于from）
         * @param downEvent 长按事件处理器（满足区间条件时触发执行，参数为相对于区间起始的偏移刻度）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyHoverFromToDuration(double from, double to, KeyHoverEvent downEvent) {
            double kt = getDownSignalTick();
            if (from < to && kt >= from && kt <= to) downEvent.run(kt - from);
            return this;
        }

        /**
         * 注册以指定刻度为中心的区间长按事件处理器（返回中心相对计时）
         * 规定了一个duration区间, 区间的中心是atTick, 当按键按下且tick在区间中时均会触发，并返回从区间开始的归零计数
         * @param atTick 中心刻度（区间基准点）
         * @param duration 区间总长度（中心两侧各延伸duration/2）
         * @param downEvent 长按事件处理器（满足区间条件时触发执行，参数为相对于区间中心的偏移刻度）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyHoverAtDuration(double atTick, double duration, KeyHoverEvent downEvent) {
            double kt = getDownSignalTick();
            if (kt >= atTick - (duration/2) && kt <= atTick + (duration/2)) downEvent.run(kt - (duration/2));
            return this;
        }

        /**
         * 注册按键抬起计时事件处理器（持续返回抬起时长刻度）
         * 当检测到按键抬起，会开始记录抬起了多久，并且持续返回记录的时间
         * @param leaveEvent 抬起计时事件处理器（执行时传入当前累计的抬起信号刻度值作为参数）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyLeave(KeyLeaveEvent leaveEvent) {
            leaveEvent.run(getUpSignalTick());
            return this;
        }

        private interface KeyEvent {
        }
        private interface KeyOnceEvent extends KeyEvent { //返回一次的事件
            void run();
        }
        private interface KeyStreamEvent extends KeyEvent { //一直返回的事件，会返回一个tick数
            void run(double tick);
        }
        public interface KeyDownEvent extends KeyOnceEvent {

        }
        public interface KeyUpEvent extends KeyOnceEvent {

        }
        public interface KeyHoverEvent extends KeyStreamEvent {

        }
        public interface KeyLeaveEvent extends KeyStreamEvent {

        }
    }


    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        InputConstants.Key inputKey = InputConstants.getKey(event.getKey(), event.getScanCode());
        String keyName = inputKey.getName();
        double value = event.getAction();
        if (!SIGNAL_MAP.containsKey(keyName)) SIGNAL_MAP.put(keyName, 0.0);
        if (value == 0) {
            SIGNAL_MAP.put(keyName, value);
            SIGNAL_MAP.put(keyName+INVERSE_NAME, 1.0);
        }
        if (value == 1) {
            SIGNAL_MAP.put(keyName, value);
            SIGNAL_MAP.put(keyName+INVERSE_NAME, 0.0);
        }

    }

    @SubscribeEvent
    public static void runKeyHook(ClientTickEvent.Post event) {
        for (String name : Hook.SIGNAL_MAP.keySet()) {
            if (Hook.SIGNAL_MAP.get(name) instanceof Double d) {
                if (d != 0) Hook.SIGNAL_MAP.put(name, d+1);
            }

        }
    }

}
