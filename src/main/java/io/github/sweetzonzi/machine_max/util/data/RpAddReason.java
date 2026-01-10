package io.github.sweetzonzi.machine_max.util.data;

import io.netty.buffer.ByteBuf;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.NotNull;

@Getter
public enum RpAddReason {
    EXP(0), // 获得经验
    REPAIR(1), // 维修部件
    ASSEMBLY(2), // 组装部件
    PART_DAMAGE(3), // 伤害部件
    PART_DESTROY(4), // 摧毁部件

    UNKNOWN(-1); // 未知

    private final int value;

    RpAddReason(int value) {
        this.value = value;
    }

    /**
     * 根据int值获取对应的枚举实例
     */
    public static RpAddReason fromValue(int value) {
        for (RpAddReason key : RpAddReason.values()) {
            if (key.getValue() == value) {
                return key;
            }
        }
        return UNKNOWN;
    }

    public MutableComponent getTranslation() {
        return switch (this) {
            case EXP -> Component.translatable("hud.hint.machine_max.rp_add_reason.exp");
            case REPAIR -> Component.translatable("hud.hint.machine_max.rp_add_reason.repair");
            case ASSEMBLY -> Component.translatable("hud.hint.machine_max.rp_add_reason.assembly");
            case PART_DAMAGE -> Component.translatable("hud.hint.machine_max.rp_add_reason.part_damage");
            case PART_DESTROY -> Component.translatable("hud.hint.machine_max.rp_add_reason.part_destroy");
            default -> Component.translatable("hud.hint.machine_max.rp_add_reason.unknown");
        };
    }

    public static final StreamCodec<ByteBuf, RpAddReason> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull RpAddReason decode(ByteBuf buf) {
            return RpAddReason.fromValue(buf.readInt());
        }

        @Override
        public void encode(ByteBuf buf, RpAddReason value) {
            buf.writeInt(value.getValue());
        }
    };
}
