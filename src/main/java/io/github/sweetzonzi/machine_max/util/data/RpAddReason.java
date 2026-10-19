package io.github.sweetzonzi.machine_max.util.data;

import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
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
    HIT(3), // 命中部件
    PART_DAMAGE(4), // 伤害部件
    PART_DESTROY(5), // 摧毁部件
    SUBSYSTEM_DAMAGE(6), // 伤害子系统
    SUBSYSTEM_DESTROY(7), // 瘫痪子系统

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
            case EXP -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_EXP.translated();
            case REPAIR -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_REPAIR.translated();
            case ASSEMBLY -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_ASSEMBLY.translated();
            case HIT -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_HIT.translated();
            case PART_DAMAGE -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_PART_DAMAGE.translated();
            case PART_DESTROY -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_PART_DESTROY.translated();
            case SUBSYSTEM_DAMAGE -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_SUBSYSTEM_DAMAGE.translated();
            case SUBSYSTEM_DESTROY -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_SUBSYSTEM_DESTROY.translated();
            default -> MMLanguageRegistry.HUD_HINT_RP_ADD_REASON_UNKNOWN.translated();
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
