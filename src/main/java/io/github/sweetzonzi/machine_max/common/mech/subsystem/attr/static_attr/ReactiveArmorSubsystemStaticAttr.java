package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;

/**
 * 爆反（反应装甲）砖的型号定义：在 {@link BasicSubsystemStaticAttr} 之上只多一个灵敏度阈值。
 *
 * <p>{@code basic_durability} 在这里是"存活位"而不是"耐打程度"：达到阈值的命中直接把它归零，
 * 数值本身只影响 HUD 与维修读数。{@code pass_damage} 决定砖拦下的命中是否继续把金额传给车体。</p>
 */
@Getter
public class ReactiveArmorSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /**
     * 灵敏度阈值：进砖之前的等效穿深（mm）达到该值即引爆。
     *
     * <p>比较用的是命中上下文里的原始穿深，不受本命中箱自己的 {@code penetration_modifiers} 影响，
     * 因此阈值与"砖能挡住多少"是两个独立的标定量。</p>
     */
    public final float triggerPenetration;

    public static final MapCodec<ReactiveArmorSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
                    Codec.FLOAT.optionalFieldOf("trigger_penetration", 50f)
                            .forGetter(ReactiveArmorSubsystemStaticAttr::getTriggerPenetration),
                    BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                            .forGetter(BasicSubsystemStaticAttr::getSoundAttr)
            ).apply(instance, ReactiveArmorSubsystemStaticAttr::new));

    public ReactiveArmorSubsystemStaticAttr(BasicAttr basicAttr, float triggerPenetration, BasicSoundAttr soundAttr) {
        super(basicAttr, soundAttr);
        // 负阈值等于"任何命中都引爆"，与"低于阈值不引爆"的语义冲突，构造期钳到 0
        this.triggerPenetration = Math.max(0f, triggerPenetration);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }
}
