package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 滑动条 —— 连续值输出，运行时当前值持久化保存。<br>
 * 输出值为 currentValue（受 min/max/step 约束）。
 */
public class GuiSliderAction extends AbstractGuiAction {

    private float currentValue;

    /** 下限 */
    public final float min;

    /** 上限 */
    public final float max;

    /** 步进精度，0 表示连续 */
    public final float step;

    public GuiSliderAction(String label, String channel, List<String> targets,
                           float min, float max, float step, float currentValue) {
        super(label, channel, targets);
        this.min = min;
        this.max = max;
        this.step = step;
        this.currentValue = Math.clamp(currentValue, min, max);
    }

    @NotNull @Override public GuiActionType type() { return GuiActionType.SLIDER; }

    public float getValue() { return currentValue; }

    public void setValue(float value) {
        this.currentValue = Math.clamp(step > 0 ? Math.round(value / step) * step : value, min, max);
    }

    public static final MapCodec<GuiSliderAction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("label").forGetter(a -> a.label),
            Codec.STRING.fieldOf("channel").forGetter(a -> a.channel),
            Codec.STRING.listOf().optionalFieldOf("targets", List.of("vehicle")).forGetter(a -> a.targets),
            Codec.FLOAT.fieldOf("min").forGetter(a -> a.min),
            Codec.FLOAT.fieldOf("max").forGetter(a -> a.max),
            Codec.FLOAT.optionalFieldOf("step", 0f).forGetter(a -> a.step),
            Codec.FLOAT.optionalFieldOf("value", 0f).forGetter(a -> a.currentValue)
    ).apply(instance, GuiSliderAction::new));

    public static final StreamCodec<ByteBuf, GuiSliderAction> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull GuiSliderAction decode(ByteBuf buf) {
            String label = ByteBufCodecs.STRING_UTF8.decode(buf);
            String channel = ByteBufCodecs.STRING_UTF8.decode(buf);
            List<String> targets = TARGETS_STREAM_CODEC.decode(buf);
            float min = buf.readFloat();
            float max = buf.readFloat();
            float step = buf.readFloat();
            float value = buf.readFloat();
            return new GuiSliderAction(label, channel, targets, min, max, step, value);
        }

        @Override
        public void encode(ByteBuf buf, @NotNull GuiSliderAction a) {
            ByteBufCodecs.STRING_UTF8.encode(buf, a.label);
            ByteBufCodecs.STRING_UTF8.encode(buf, a.channel);
            TARGETS_STREAM_CODEC.encode(buf, a.targets);
            buf.writeFloat(a.min);
            buf.writeFloat(a.max);
            buf.writeFloat(a.step);
            buf.writeFloat(a.currentValue);
        }
    };
}
