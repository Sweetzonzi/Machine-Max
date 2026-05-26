package io.github.sweetzonzi.machine_max.common.mech.control;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 控制组模式枚举，决定客户端按键语义分派方式。<br>
 * 原位于 VehicleCore，现移至控制组级别，使不同座椅（和子控制组）可独立设置模式。
 */
public enum ControlMode {
    /** 子控制组使用：不覆盖 baseGroup 的 mode */
    INHERIT,
    /** 地面载具（W/S=油门/刹车，A/D=转向） */
    GROUND,
    /** 飞行器（W/S=俯仰，A/D=横滚） */
    PLANE,
    /** 船舶 */
    SHIP,
    /** 机甲/步行机械 */
    MECH;

    public static final Codec<ControlMode> CODEC = Codec.STRING.xmap(
            name -> ControlMode.valueOf(name.toUpperCase()),
            mode -> mode.name().toLowerCase()
    );

    public static final StreamCodec<ByteBuf, ControlMode> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ControlMode decode(ByteBuf buf) {
            return values()[buf.readByte()];
        }

        @Override
        public void encode(ByteBuf buf, ControlMode value) {
            buf.writeByte(value.ordinal());
        }
    };
}
