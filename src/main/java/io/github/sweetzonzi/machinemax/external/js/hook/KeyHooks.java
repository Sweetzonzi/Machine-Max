package io.github.sweetzonzi.machinemax.external.js.hook;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sweetzonzi.machinemax.MachineMax;
import io.github.sweetzonzi.machinemax.external.js.InputSignalProvider;
import io.github.sweetzonzi.machinemax.external.js.JSUtils;
import io.github.sweetzonzi.machinemax.util.MMJoystickHandler;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.github.sweetzonzi.machinemax.external.js.hook.Hook.HOOK_SIGNAL_MAP;

@EventBusSubscriber(modid = MachineMax.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
@OnlyIn(Dist.CLIENT)
public class KeyHooks {
    public final static String INVERSE_NAME = "_inv";
    private static final HashMap<String, _Watcher> cachedWatchers = new HashMap<>(); // 按频道分配的所有观察器

    private static class _Watcher { //bool观察器，当面对输入连续的true时只返回一次true信号。除非输入被重置否则后续均返回false
        private boolean dead = false;
        public double lastTick = 0.0;
        public boolean run(boolean input) {
            if (!dead && input) { //首次触发
                dead = true;
                return input;
            }
            if (dead && !input) //输入重置
                dead = false;

            return false; //过滤后续的信号
        }

    }

    public static class GamePadSetting {
        public enum GType {
            Axis,
            Button
        }
        public final int jid;
        public final GType type;
        public final int GLFW_ID;

        public GamePadSetting(int jid, GType type, int GLFW_ID) {
            this.jid = jid;
            this.type = type;
            this.GLFW_ID = GLFW_ID;
        }

        public String getKeyName () {
            return "gamepad_"+jid+"."+type+"."+GLFW_ID;
        }
    }

    public static class EVENT {
        private final String keyName;
        private KeyMapping mapping = null;
        private GamePadSetting gamePadSetting = null;
        private List<EVENT> children = new ArrayList<>();

        public EVENT(String keyName) {
            this.keyName = InputSignalProvider.key(keyName);
        }
        public EVENT(KeyMapping mapping) {
            this.mapping = mapping;
            this.keyName = mapping.getKey().getName();
        }
        public EVENT(GamePadSetting gamePadSetting) {
            this.gamePadSetting = gamePadSetting;
            this.keyName = gamePadSetting.getKeyName();
        }

        private double getDownSignalTick() {
            return InputSignalProvider.getSignalTicks(keyName);
        }
        private double getUpSignalTick() {
            return InputSignalProvider.getSignalTicks(keyName+INVERSE_NAME);
        }

        private _Watcher fetchWatcher(RootEvent rootEvent) { //通过调用的名称生成频道，自动分配bool观察器
            var currentThread = Thread.currentThread();
            var stack = currentThread.getStackTrace()[2];
            String className = stack.getClassName();
            String methodName = stack.getMethodName();
            String channel = JSUtils.getSimpleName(className) + ":" + methodName + ":" + rootEvent.hashCode();
            if (!cachedWatchers.containsKey(channel)) cachedWatchers.put(channel, new _Watcher());
            return cachedWatchers.get(channel);
        }


        /**
         * 注册游戏手柄轴量、杆量的事件处理器
         * @param axisEvent 返回 -100 到 100 的杆量，持续调用
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnAxis(GamePadAxisEvent axisEvent) {
            children.forEach((child) -> child.OnAxis(axisEvent));
            if (gamePadSetting instanceof GamePadSetting setting && GLFW.glfwJoystickPresent(setting.jid))
                axisEvent.run(InputSignalProvider.getSignalTicks(keyName));
            return this;
        }


        // 如果编辑器提醒你函数类型改成Void，请忽略，因为它们是链式调用设计模式

        /**
         * 绑定多类型键盘事件的统一处理方法
         * @param downEvent 按下事件处理器（当按下信号刻度(getDownSignalTick())等于1.0时触发执行）
         * @param upEvent 抬起事件处理器（当抬起信号刻度(getUpSignalTick())等于1.0时触发执行）
         * @param keyLongPressEvent 单次长按事件处理器（按下超过8刻时则触发一次，然后进入休眠，直到按键抬起则停止休眠）
         * @param hoverEvent 连续长按事件处理器（执行时传入当前按下信号刻度值作为参数）
         * @param leaveEvent 抬起计时事件处理器（执行时传入当前抬起信号刻度值作为参数）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyAll(
                KeyDownEvent downEvent,
                KeyUpEvent upEvent,
                KeyLongPressEvent keyLongPressEvent,
                KeyHoverEvent hoverEvent,
                KeyLeaveEvent leaveEvent)
        {
            children.forEach((child) -> child.OnKeyAll(
                    downEvent,
                    upEvent,
                    keyLongPressEvent,
                    hoverEvent,
                    leaveEvent
            ));
            OnKeyDown(downEvent);
            OnKeyUp(upEvent);
            OnKeyLongPress(keyLongPressEvent);
            OnKeyHover(hoverEvent);
            OnKeyLeave(leaveEvent);
            return this;
        }

        /**
         * 注册按键按下瞬间的事件处理器
         * @param downEvent 按下事件处理器（当按下信号刻度(getDownSignalTick())等于1.0时触发执行）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyDown(KeyDownEvent downEvent) {
            children.forEach((child) -> child.OnKeyDown(downEvent));
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
            children.forEach((child) -> child.OnKeyDownAtTick(atTick, downEvent));
            if (getDownSignalTick() == atTick) downEvent.run();
            return this;
        }


        /**
         * 注册按键抬起瞬间的事件处理器
         * @param upEvent 抬起事件处理器（当抬起信号刻度(getUpSignalTick())等于1.0时触发执行）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyUp(KeyUpEvent upEvent) {
            children.forEach((child) -> child.OnKeyUp(upEvent));
            if (getUpSignalTick() == 1.0) upEvent.run();
            return this;
        }


        /**
         * 注册单次长按事件处理器
         * @param length 自定义长按刻时阈值
         * @param longPressEvent 长按事件处理器
         *（超过自定义刻时执行一次，然后进入休眠，直到按键抬起则停止休眠。没有任何参数传入）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyLongPress(double length, KeyLongPressEvent longPressEvent) {
            children.forEach((child) -> child.OnKeyLongPress(length, longPressEvent));
            if (fetchWatcher(longPressEvent).run(getDownSignalTick() >= length)) longPressEvent.run();
            return this;
        }


        /**
         * 注册单次长按事件处理器
         * @param longPressEvent 长按事件处理器
         *（超过8刻时执行一次，然后进入休眠，直到按键抬起则停止休眠。没有任何参数传入）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyLongPress(KeyLongPressEvent longPressEvent) {
            children.forEach((child) -> child.OnKeyLongPress(longPressEvent));
            return OnKeyLongPress(8, longPressEvent);
        }



        /**
         * 注册双击事件处理器
         * @param doublePressEvent 双击事件处理逻辑
         * @return 当前EVENT实例（链式调用）
         */
        public EVENT OnKeyDoublePress(KeyDoublePressEvent doublePressEvent) {
            children.forEach((child) -> child.OnKeyDoublePress(doublePressEvent));
            _Watcher wt = fetchWatcher(doublePressEvent);
            if (getUpSignalTick() == 1.0) wt.lastTick = 7;//阈值
            if (wt.lastTick > 0) wt.lastTick--;
            if (getDownSignalTick() == 1.0)
                if (wt.run(wt.lastTick > 0)) {
                    doublePressEvent.run();
                    wt.lastTick = 0;
                }

            return this;
        }


        /**
         * 注册三连击事件处理器
         * @param triplePressEvent 三连击事件处理逻辑
         * @return 当前EVENT实例（链式调用）
         */
        public EVENT OnKeyTriplePress(KeyTriplePressEvent triplePressEvent) {
            children.forEach((child) -> child.OnKeyTriplePress(triplePressEvent));
            _Watcher wt = fetchWatcher(triplePressEvent);
            int w = 12; //惰性阈值
            if (getUpSignalTick() == 1.0) wt.lastTick += w;
            if (wt.lastTick > 0) wt.lastTick -= 1;
            if (getDownSignalTick() == 1.0) {
                if (wt.run(wt.lastTick > w * 1.24)) { //防卫阈值
                    triplePressEvent.run();
                    wt.lastTick = 0;
                }
            }
            return this;
        }


        /**
         * 注册连续长按基础事件处理器（返回完整按下时长tick）
         * @param hoverEvent 长按事件处理器（执行时传入当前完整的按下信号刻度值作为参数）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyHover(KeyHoverEvent hoverEvent) {
            children.forEach((child) -> child.OnKeyHover(hoverEvent));
            if (getDownSignalTick() > 0)
                hoverEvent.run(getDownSignalTick() - 1);
            return this;
        }

        /**
         * 注册指定刻度区间内的连续长按事件处理器（返回区间内相对计时）
         * 规定了一个从from开始到to的区间, 当按键按下且tick在区间中时均会触发，并返回从区间开始的归零计数
         * @param from 区间起始刻度（包含）
         * @param to 区间结束刻度（包含，需大于from）
         * @param downEvent 长按事件处理器（满足区间条件时触发执行，参数为相对于区间起始的偏移刻度）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyHoverFromToDuration(double from, double to, KeyHoverEvent downEvent) {
            children.forEach((child) -> child.OnKeyHoverFromToDuration(from, to, downEvent));
            double kt = getDownSignalTick();
            if (from < to && kt >= from && kt <= to) downEvent.run(kt - from);
            return this;
        }

        /**
         * 注册以指定刻度为中心的连续区间长按事件处理器（返回中心相对计时）
         * 规定了一个duration区间, 区间的中心是atTick, 当按键按下且tick在区间中时均会触发，并返回从区间开始的归零计数
         * @param atTick 中心刻度（区间基准点）
         * @param duration 区间总长度（中心两侧各延伸duration/2）
         * @param downEvent 长按事件处理器（满足区间条件时触发执行，参数为相对于区间中心的偏移刻度）
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT OnKeyHoverAtDuration(double atTick, double duration, KeyHoverEvent downEvent) {
            children.forEach((child) -> child.OnKeyHoverAtDuration(atTick, duration, downEvent));
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
            children.forEach((child)-> child.OnKeyLeave(leaveEvent));
            if (getUpSignalTick() > 0)
                leaveEvent.run(getUpSignalTick() - 1);
            return this;
        }


        /**
         * 连接一个事件体作为孩子事件体，让自己绑定的接口传递给所有孩子
         * 用于让一个写好的功能应用在不同类型的触发条件，并且避免了重复的绑定代码
         * @param child 孩子事件体
         * @return {@link EVENT} 对象，用于链式调用
         */
        public EVENT addChild(EVENT child) {
            children.add(child);
            return this;
        }

        private interface RootEvent {
        }
        private interface KeyEvent extends RootEvent{
        }
        private interface GamePadEvent extends RootEvent{
        }
        public interface GamePadAxisEvent extends GamePadEvent{
            void run(double status);
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
        public interface KeyLongPressEvent extends KeyOnceEvent {

        }
        public interface KeyDoublePressEvent extends KeyOnceEvent {

        }
        public interface KeyTriplePressEvent extends KeyOnceEvent {

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
        if (!HOOK_SIGNAL_MAP.containsKey(keyName)) HOOK_SIGNAL_MAP.put(keyName, 0.0);
        if (value == 0) {
            HOOK_SIGNAL_MAP.put(keyName, value);
            HOOK_SIGNAL_MAP.put(keyName+INVERSE_NAME, 1.0);
        }
        if (value == 1) {
            HOOK_SIGNAL_MAP.put(keyName, value);
            HOOK_SIGNAL_MAP.put(keyName+INVERSE_NAME, 0.0);
        }

    }

    @SubscribeEvent
    public static void runKeyHook(ClientTickEvent.Post event) {
        for (String name : HOOK_SIGNAL_MAP.keySet()) {
            if (HOOK_SIGNAL_MAP.get(name) instanceof Double d) {
                if (d != 0) HOOK_SIGNAL_MAP.put(name, d+1);
            }

        }

        MMJoystickHandler.refreshState();

        for (int jid = 0; jid < MMJoystickHandler.buttonStates.length; jid++) {
            if (MMJoystickHandler.buttonStates[jid] == null) continue;
            for (int buttonId = 0; buttonId < MMJoystickHandler.buttonStates[jid].length; buttonId++) {
                boolean status = MMJoystickHandler.isButtonPressed(jid, buttonId);
                String keyName = new GamePadSetting(jid, GamePadSetting.GType.Button, buttonId).getKeyName();
                String invKeyName = keyName + INVERSE_NAME;
                if (! HOOK_SIGNAL_MAP.containsKey(keyName)) HOOK_SIGNAL_MAP.put(keyName, 0.0);
                if (! HOOK_SIGNAL_MAP.containsKey(invKeyName)) HOOK_SIGNAL_MAP.put(invKeyName, 0.0);
                HOOK_SIGNAL_MAP.put(keyName, status ? HOOK_SIGNAL_MAP.get(keyName) + 1.0 : 0.0);
                HOOK_SIGNAL_MAP.put(invKeyName, status ? 0.0 : HOOK_SIGNAL_MAP.get(invKeyName) + 1.0);
            }
        }
        for (int jid = 0; jid < MMJoystickHandler.axisStates.length; jid++) {
            if (MMJoystickHandler.axisStates[jid] == null) continue;
            for (int axisId = 0; axisId < MMJoystickHandler.axisStates[jid].length; axisId++) {
                float status = MMJoystickHandler.getAxisState(jid, axisId);
                String keyName = new GamePadSetting(jid, GamePadSetting.GType.Axis, axisId).getKeyName();
                String invKeyName = keyName + INVERSE_NAME;
                if (! HOOK_SIGNAL_MAP.containsKey(keyName)) HOOK_SIGNAL_MAP.put(keyName, 0.0);
                if (! HOOK_SIGNAL_MAP.containsKey(invKeyName)) HOOK_SIGNAL_MAP.put(invKeyName, 0.0);
                HOOK_SIGNAL_MAP.replace(keyName, Math.floor(status * 100));
                HOOK_SIGNAL_MAP.replace(invKeyName, -Math.floor(status * 100));
            }
        }
    }

}
