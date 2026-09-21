package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SeatSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

@Getter
public class SeatSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final SeatSubsystemStaticAttr staticAttribute;
    public final String locator;
    public final ResourceLocation controlGroupPreset;
    public final Map<String, List<String>> passengerNumSignalTargets;
    /** 摄像机发现握手配置 */
    public final Map<String, List<String>> cameraDiscoveryTargets;
    /** 下车 locator 名称列表，顺序即优先级；空列表表示该座位未声明下车点 */
    public final List<String> dismountLocators;

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "").forGetter(SeatSubsystemAttr::getLocator),
            ResourceLocation.CODEC.optionalFieldOf("control_group_preset", NO_CONTROL_GROUP_PRESET).forGetter(SeatSubsystemAttr::getControlGroupPreset),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("passenger_num_outputs", Map.of()).forGetter(SeatSubsystemAttr::getPassengerNumSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("camera_discovery_targets", Map.of()).forGetter(SeatSubsystemAttr::getCameraDiscoveryTargets),
            Codec.STRING.listOf().optionalFieldOf("dismount_locators", List.of()).forGetter(SeatSubsystemAttr::getDismountLocators)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            ResourceLocation controlGroupPreset,
            Map<String, List<String>> passengerNumSignalTargets,
            Map<String, List<String>> cameraDiscoveryTargets,
            List<String> dismountLocators) {
        super(modelName);
        this.staticAttribute = (SeatSubsystemStaticAttr) getStaticAttr();
        if (locator == null || locator.isEmpty())
            throw new IllegalStateException("error.machine_max.seat_subsystem.no_locator");
        this.locator = locator;
        this.controlGroupPreset = controlGroupPreset;
        this.passengerNumSignalTargets = passengerNumSignalTargets;
        this.cameraDiscoveryTargets = cameraDiscoveryTargets;
        this.dismountLocators = checkDismountLocators(dismountLocators);
    }

    /**
     * 校验并规整下车 locator 列表。空名在下车流程中无法解析成候选点，属非法声明。
     *
     * @param dismountLocators JSON 中声明的下车 locator 列表，可为 null
     * @return 不可变的下车 locator 列表
     */
    private static List<String> checkDismountLocators(List<String> dismountLocators) {
        if (dismountLocators == null || dismountLocators.isEmpty()) return List.of();
        for (String name : dismountLocators) {
            if (name == null || name.isEmpty())
                throw new IllegalStateException("error.machine_max.seat_subsystem.empty_dismount_locator");
        }
        return List.copyOf(dismountLocators);
    }


    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.SEAT;
    }

    @Override
    public ResourceLocation getControlGroupPresetRl() {
        return controlGroupPreset;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new SeatSubsystem(owner, name, this);
    }

}
