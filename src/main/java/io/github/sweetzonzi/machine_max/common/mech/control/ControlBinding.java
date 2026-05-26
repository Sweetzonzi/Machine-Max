package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * 按键绑定，定义从玩家按键事件到信号频道输出的映射。<br>
 * TOGGLE 模式的运行时状态保存在自身。
 */
public class ControlBinding {

    /** 逻辑按键名，如 "mouse_left"、"key.r"、"gamepad.button_a" */
    public final String trigger;

    /** 动作类型，决定信号产出 */
    public final BindingAction action;

    /** 输出频道名，信号将被发送到此频道 */
    public final String channel;

    /** 信号目标名列表，默认 ["vehicle"] 走总线广播；可指定具体接收者走路由 */
    public final List<String> targets;

    /** TOGGLE 模式下的运行时状态，持久化保存 */
    private boolean toggleState = false;

    public ControlBinding(String trigger, BindingAction action, String channel, List<String> targets) {
        this.trigger = trigger;
        this.action = action != null ? action : BindingAction.PRESS;
        this.channel = channel != null ? channel : "";
        this.targets = (targets != null && !targets.isEmpty()) ? List.copyOf(targets) : List.of("vehicle");
    }

    /** 获取 TOGGLE 的当前状态 */
    public boolean getToggleState() {
        return toggleState;
    }

    /** 翻转 TOGGLE 状态并返回新值 */
    public boolean flipToggleState() {
        this.toggleState = !this.toggleState;
        return this.toggleState;
    }

    /** 强行设置 TOGGLE 状态 */
    public void setToggleState(boolean state) {
        this.toggleState = state;
    }

    public static final Codec<ControlBinding> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("trigger").forGetter(b -> b.trigger),
            BindingAction.CODEC.fieldOf("action").forGetter(b -> b.action),
            Codec.STRING.fieldOf("channel").forGetter(b -> b.channel),
            Codec.STRING.listOf().optionalFieldOf("targets", List.of("vehicle")).forGetter(b -> b.targets)
    ).apply(instance, ControlBinding::new));

    private static final StreamCodec<ByteBuf, List<String>> TARGETS_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public List<String> decode(ByteBuf buf) {
            int size = ByteBufCodecs.VAR_INT.decode(buf);
            List<String> list = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                list.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            }
            return list;
        }

        @Override
        public void encode(ByteBuf buf, List<String> list) {
            ByteBufCodecs.VAR_INT.encode(buf, list.size());
            for (String s : list) {
                ByteBufCodecs.STRING_UTF8.encode(buf, s);
            }
        }
    };

    public static final StreamCodec<ByteBuf, ControlBinding> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, b -> b.trigger,
            BindingAction.STREAM_CODEC, b -> b.action,
            ByteBufCodecs.STRING_UTF8, b -> b.channel,
            TARGETS_STREAM_CODEC, b -> b.targets,
            ByteBufCodecs.BOOL, b -> b.toggleState,
            (trigger, action, channel, targets, toggleState) -> {
                ControlBinding binding = new ControlBinding(trigger, action, channel, targets);
                binding.toggleState = toggleState;
                return binding;
            }
    );
}
