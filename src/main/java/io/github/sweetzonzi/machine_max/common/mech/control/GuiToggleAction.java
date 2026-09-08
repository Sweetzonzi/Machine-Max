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
 * 勾选框 —— TOGGLE 模式，运行时状态持久化保存。
 */
public class GuiToggleAction extends AbstractGuiAction {

    private boolean active = false;

    public GuiToggleAction(String label, String channel, List<String> targets) {
        super(label, channel, targets);
    }

    @NotNull @Override public GuiActionType type() { return GuiActionType.TOGGLE; }

    public boolean isActive() { return active; }
    public boolean flip() { this.active = !this.active; return this.active; }
    public void setActive(boolean active) { this.active = active; }

    public static final MapCodec<GuiToggleAction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("label").forGetter(a -> a.label),
            Codec.STRING.fieldOf("channel").forGetter(a -> a.channel),
            Codec.STRING.listOf().optionalFieldOf("targets", List.of("global")).forGetter(a -> a.targets),
            Codec.BOOL.optionalFieldOf("active", false).forGetter(a -> a.active)
    ).apply(instance, (label, channel, targets, active) -> {
        var a = new GuiToggleAction(label, channel, targets);
        a.active = active;
        return a;
    }));

    public static final StreamCodec<ByteBuf, GuiToggleAction> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, a -> a.label,
            ByteBufCodecs.STRING_UTF8, a -> a.channel,
            TARGETS_STREAM_CODEC, a -> a.targets,
            ByteBufCodecs.BOOL, a -> a.active,
            (label, channel, targets, active) -> {
                var a = new GuiToggleAction(label, channel, targets);
                a.active = active;
                return a;
            }
    );
}
