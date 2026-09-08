package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 交互元素抽象基类。<br>
 * 子类通过 {@link GuiActionType} dispatch 实现多态序列化。
 */
public abstract class AbstractGuiAction {

    /** 按钮显示文字 */
    public final String label;

    /** 输出频道名 */
    public final String channel;

    /** 信号目标名列表，默认 ["global"] */
    public final List<String> targets;

    protected AbstractGuiAction(String label, String channel, List<String> targets) {
        this.label = label;
        this.channel = channel != null ? channel : "";
        this.targets = (targets != null && !targets.isEmpty()) ? List.copyOf(targets) : List.of("global");
    }

    /** 交互元素类型 */
    @NotNull public abstract GuiActionType type();

    public static final Codec<AbstractGuiAction> CODEC = GuiActionType.CODEC.dispatch(
            AbstractGuiAction::type,
            type -> switch (type) {
                case TOGGLE -> GuiToggleAction.MAP_CODEC;
                case PULSE  -> GuiPulseAction.MAP_CODEC;
                case SLIDER -> GuiSliderAction.MAP_CODEC;
            }
    );

    public static final StreamCodec<ByteBuf, AbstractGuiAction> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull AbstractGuiAction decode(ByteBuf buf) {
            GuiActionType type = GuiActionType.STREAM_CODEC.decode(buf);
            return switch (type) {
                case TOGGLE -> GuiToggleAction.STREAM_CODEC.decode(buf);
                case PULSE  -> GuiPulseAction.STREAM_CODEC.decode(buf);
                case SLIDER -> GuiSliderAction.STREAM_CODEC.decode(buf);
            };
        }

        @Override
        public void encode(ByteBuf buf, @NotNull AbstractGuiAction value) {
            GuiActionType.STREAM_CODEC.encode(buf, value.type());
            switch (value) {
                case GuiToggleAction a -> GuiToggleAction.STREAM_CODEC.encode(buf, a);
                case GuiPulseAction  a -> GuiPulseAction.STREAM_CODEC.encode(buf, a);
                case GuiSliderAction a -> GuiSliderAction.STREAM_CODEC.encode(buf, a);
                default -> throw new IllegalArgumentException("Unknown AbstractGuiAction type: " + value.getClass());
            }
        }
    };

    /** 网络传输用的 targets codec */
    static final StreamCodec<ByteBuf, List<String>> TARGETS_STREAM_CODEC = new StreamCodec<>() {
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
}
