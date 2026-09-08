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
 * 按钮 —— PULSE 模式，点击一次发送 1.0f，无运行时状态。
 */
public class GuiPulseAction extends AbstractGuiAction {

    public GuiPulseAction(String label, String channel, List<String> targets) {
        super(label, channel, targets);
    }

    @NotNull @Override public GuiActionType type() { return GuiActionType.PULSE; }

    public static final MapCodec<GuiPulseAction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("label").forGetter(a -> a.label),
            Codec.STRING.fieldOf("channel").forGetter(a -> a.channel),
            Codec.STRING.listOf().optionalFieldOf("targets", List.of("global")).forGetter(a -> a.targets)
    ).apply(instance, GuiPulseAction::new));

    public static final StreamCodec<ByteBuf, GuiPulseAction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, a -> a.label,
            ByteBufCodecs.STRING_UTF8, a -> a.channel,
            TARGETS_STREAM_CODEC, a -> a.targets,
            GuiPulseAction::new
    );
}
