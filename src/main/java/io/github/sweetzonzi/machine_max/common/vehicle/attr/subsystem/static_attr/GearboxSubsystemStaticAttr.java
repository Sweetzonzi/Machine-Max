package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.List;

@Getter
public class GearboxSubsystemStaticAttr extends BasicSubsystemStaticAttr {
    
    public record GearBoxSoundAttr(
            BasicSoundAttr basicSounds,
            SoundEvent clutchIn,
            SoundEvent clutchOut,
            SoundEvent gearUp,
            SoundEvent gearDown
    ) {
        public static final GearBoxSoundAttr DEFAULT = new GearBoxSoundAttr(
                BasicSoundAttr.DEFAULT,
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.gearbox.clutch_in"), 16),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.gearbox.clutch_out"), 16),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.gearbox.up.light"), 16),
                SoundEvent.createFixedRangeEvent(ResourceLocation.fromNamespaceAndPath(MachineMax.MOD_ID, "subsystem.gearbox.down.light"), 16)
        );

        public static final Codec<GearBoxSoundAttr> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BasicSoundAttr.basicSounds(GearBoxSoundAttr::basicSounds),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("clutch_in", DEFAULT.clutchIn).forGetter(GearBoxSoundAttr::clutchIn),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("clutch_out", DEFAULT.clutchOut).forGetter(GearBoxSoundAttr::clutchOut),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("gear_up", DEFAULT.gearUp).forGetter(GearBoxSoundAttr::gearUp),
                SoundEvent.DIRECT_CODEC.optionalFieldOf("gear_down", DEFAULT.gearDown).forGetter(GearBoxSoundAttr::gearDown)
        ).apply(instance, GearBoxSoundAttr::new));
    }

    public final float finalRatio;//最终减速比，用于整体缩放减速比
    public final List<Float> ratios;//减速比，升序排列
    public final float switchTime;//换挡时间(秒)
    public final List<String> ratioControlSignalKeys;//换挡控制信号，指定选用的减速比 TODO:似乎暂时无效，需要检查
    public final GearBoxSoundAttr sounds;//音效配置

    public GearboxSubsystemStaticAttr(
            BasicSubsystemStaticAttr.BasicAttr basicAttr,
            float finalRatio,
            List<Float> ratios,
            float switchTime,
            List<String> ratioControlSignalKeys,
            GearBoxSoundAttr sounds) {
        super(basicAttr, sounds.basicSounds());
        this.finalRatio = finalRatio;
        this.ratios = ratios;
        this.switchTime = switchTime;
        this.ratioControlSignalKeys = ratioControlSignalKeys;
        this.sounds = sounds;
    }

    public static final MapCodec<GearboxSubsystemStaticAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
            Codec.FLOAT.optionalFieldOf("final_ratio", 10f).forGetter(GearboxSubsystemStaticAttr::getFinalRatio),
            Codec.list(Codec.FLOAT).optionalFieldOf("ratios", List.of(-3.5f, 3.5f, 2.5f, 1.7f, 1.4f, 1.1f)).forGetter(GearboxSubsystemStaticAttr::getRatios),
            Codec.FLOAT.optionalFieldOf("switch_time", 0.3f).forGetter(GearboxSubsystemStaticAttr::getSwitchTime),
            Codec.STRING.listOf().optionalFieldOf("control_inputs", List.of("gearbox_control")).forGetter(GearboxSubsystemStaticAttr::getRatioControlSignalKeys),
            GearBoxSoundAttr.CODEC.optionalFieldOf("sounds", GearBoxSoundAttr.DEFAULT).forGetter(GearboxSubsystemStaticAttr::getSounds)
    ).apply(instance, GearboxSubsystemStaticAttr::new));

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.GEARBOX;
    }

    public SoundEvent getClutchInSound() {
        return sounds.clutchIn;
    }

    public SoundEvent getClutchOutSound() {
        return sounds.clutchOut;
    }

    public SoundEvent getGearUpSound() {
        return sounds.gearUp;
    }

    public SoundEvent getGearDownSound() {
        return sounds.gearDown;
    }

}
