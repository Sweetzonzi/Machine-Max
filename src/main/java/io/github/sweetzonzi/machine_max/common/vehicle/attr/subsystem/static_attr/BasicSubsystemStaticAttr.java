package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Function;

/**
 * 无实际功能的基础子系统，可被实例化，作为无功能但可向载具传导伤害的子系统，例如载具的可破坏弱点。
 */
@Getter
public class BasicSubsystemStaticAttr extends AbstractSubsystemStaticAttr {

    protected final BasicAttr basicAttr;
    protected final BasicSoundAttr soundAttr;

    public static final MapCodec<BasicSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
                    BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT).forGetter(BasicSubsystemStaticAttr::getSoundAttr)
            ).apply(instance, BasicSubsystemStaticAttr::new));

    public BasicSubsystemStaticAttr(BasicAttr basicAttr, BasicSoundAttr soundAttr) {
        super(basicAttr.basicDurability());
        this.basicAttr = basicAttr;
        this.soundAttr = soundAttr;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.BASIC;
    }

    /**
     * 基础属性类，所有子系统共有的属性
     * @param basicDurability 基础耐久度
     * @param passDamage 是否传递伤害至子系统持有者
     * @param limitDamage 是否限制伤害传递值至子系统剩余耐久度，例如40伤害，子系统剩余耐久度为20，则只会传递20伤害给持有者
     */
    public record BasicAttr(
            float basicDurability,
            boolean passDamage,
            boolean limitDamage
    ) {
        public static final MapCodec<BasicAttr> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Codec.FLOAT.optionalFieldOf("basic_durability", 20f).forGetter(BasicAttr::basicDurability),
                        Codec.BOOL.optionalFieldOf("pass_damage", true).forGetter(BasicAttr::passDamage),
                        Codec.BOOL.optionalFieldOf("limit_damage", false).forGetter(BasicAttr::limitDamage)
                ).apply(instance, BasicAttr::new));
    }

    public record BasicSoundAttr(
            SoundEvent onDestroyed,
            SoundEvent onActivated
    ) {
        public static final BasicSoundAttr DEFAULT = new BasicSoundAttr(
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty_sound"), 16),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "empty_sound"), 16)
        );

        public static final MapCodec<BasicSoundAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                SoundEvent.DIRECT_CODEC.optionalFieldOf("on_destroyed", DEFAULT.onDestroyed).forGetter(BasicSoundAttr::onDestroyed),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("on_activate", DEFAULT.onActivated).forGetter(BasicSoundAttr::onActivated)
        ).apply(instance, BasicSoundAttr::new));

        public static <T> RecordCodecBuilder<T, BasicSoundAttr> basicSounds(Function<T, BasicSoundAttr> getter) {
            return RecordCodecBuilder.of(getter, CODEC);
        }
    }


    @Override
    public float getBasicDurability() {
        return basicAttr.basicDurability();
    }
}
