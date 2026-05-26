package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * GUI 交互元素类型枚举，用于 dispatch 多态反序列化。
 */
public enum GuiActionType {
    TOGGLE,
    PULSE,
    SLIDER;

    public static final Codec<GuiActionType> CODEC = Codec.STRING.xmap(
            name -> GuiActionType.valueOf(name.toUpperCase()),
            action -> action.name().toLowerCase()
    );

    public static final StreamCodec<ByteBuf, GuiActionType> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GuiActionType decode(ByteBuf buf) {
            return values()[buf.readByte()];
        }

        @Override
        public void encode(ByteBuf buf, GuiActionType value) {
            buf.writeByte(value.ordinal());
        }
    };
}
