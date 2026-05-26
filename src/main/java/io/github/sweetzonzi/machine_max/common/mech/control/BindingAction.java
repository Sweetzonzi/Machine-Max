package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 离散按键动作类型，定义控制组中一个按键绑定如何产出信号值。<br>
 * 产出值为 0~1 的 float，通过 sendSignalToTarget 注入到指定频道。
 */
public enum BindingAction {
    /** 按下时发送 1.0f，松开后不再发送 */
    PRESS,
    /** 按下持续发送 1.0f，松开发送 EmptySignal */
    HOLD,
    /** 每按一次在 0.0f ↔ 1.0f 间翻转，服务端维护状态 */
    TOGGLE;

    public static final Codec<BindingAction> CODEC = Codec.STRING.xmap(
            name -> BindingAction.valueOf(name.toUpperCase()),
            action -> action.name().toLowerCase()
    );

    public static final StreamCodec<ByteBuf, BindingAction> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BindingAction decode(ByteBuf buf) {
            return values()[buf.readByte()];
        }

        @Override
        public void encode(ByteBuf buf, BindingAction value) {
            buf.writeByte(value.ordinal());
        }
    };
}
