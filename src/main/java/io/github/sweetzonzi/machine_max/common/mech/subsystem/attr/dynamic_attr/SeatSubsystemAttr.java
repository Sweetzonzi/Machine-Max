package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.SeatSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.SeatSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    /**
     * 第一人称 HUD 布局的安装级覆盖；空表示沿用子系统型号定义里的默认值。
     * <p>显式写 {@code machine_max:empty} 表示这个安装位在该视角不显示 HUD，
     * 与"没写"（沿用定义）区分开。</p>
     */
    public final Optional<ResourceLocation> firstHudLayoutOverride;
    /** 第三人称 HUD 布局的安装级覆盖；语义同 {@link #firstHudLayoutOverride} */
    public final Optional<ResourceLocation> thirdHudLayoutOverride;
    /** 是否允许乘员使用物品的安装级覆盖；空表示沿用型号定义里的默认值 */
    public final Optional<Boolean> allowUseItemsOverride;

    public static final MapCodec<SeatSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.optionalFieldOf("locator", "").forGetter(SeatSubsystemAttr::getLocator),
            ResourceLocation.CODEC.optionalFieldOf("control_group_preset", NO_CONTROL_GROUP_PRESET).forGetter(SeatSubsystemAttr::getControlGroupPreset),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("passenger_num_outputs", Map.of()).forGetter(SeatSubsystemAttr::getPassengerNumSignalTargets),
            SIGNAL_TARGETS_CODEC.optionalFieldOf("camera_discovery_targets", Map.of()).forGetter(SeatSubsystemAttr::getCameraDiscoveryTargets),
            Codec.STRING.listOf().optionalFieldOf("dismount_locators", List.of()).forGetter(SeatSubsystemAttr::getDismountLocators),
            ResourceLocation.CODEC.optionalFieldOf("first_hud_layout").forGetter(SeatSubsystemAttr::getFirstHudLayoutOverride),
            ResourceLocation.CODEC.optionalFieldOf("third_hud_layout").forGetter(SeatSubsystemAttr::getThirdHudLayoutOverride),
            Codec.BOOL.optionalFieldOf("allow_use_items").forGetter(SeatSubsystemAttr::getAllowUseItemsOverride)
    ).apply(instance, SeatSubsystemAttr::new));

    public SeatSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            ResourceLocation controlGroupPreset,
            Map<String, List<String>> passengerNumSignalTargets,
            Map<String, List<String>> cameraDiscoveryTargets,
            List<String> dismountLocators,
            Optional<ResourceLocation> firstHudLayoutOverride,
            Optional<ResourceLocation> thirdHudLayoutOverride,
            Optional<Boolean> allowUseItemsOverride) {
        super(modelName);
        this.staticAttribute = (SeatSubsystemStaticAttr) getStaticAttr();
        if (locator == null || locator.isEmpty())
            throw new IllegalStateException(MMLanguageRegistry.ERROR_SEAT_SUBSYSTEM_NO_LOCATOR.tag);
        this.locator = locator;
        this.controlGroupPreset = controlGroupPreset;
        this.passengerNumSignalTargets = passengerNumSignalTargets;
        this.cameraDiscoveryTargets = cameraDiscoveryTargets;
        this.dismountLocators = checkDismountLocators(dismountLocators);
        this.firstHudLayoutOverride = firstHudLayoutOverride;
        this.thirdHudLayoutOverride = thirdHudLayoutOverride;
        this.allowUseItemsOverride = allowUseItemsOverride;
    }

    /** 该视角实际使用的 HUD 布局键：安装级覆盖优先，缺省取型号定义。 */
    public ResourceLocation getFirstHudLayout() {
        return firstHudLayoutOverride.orElse(staticAttribute.getViews().firstHudLayout());
    }

    /** 该视角实际使用的 HUD 布局键：安装级覆盖优先，缺省取型号定义。 */
    public ResourceLocation getThirdHudLayout() {
        return thirdHudLayoutOverride.orElse(staticAttribute.getViews().thirdHudLayout());
    }

    /** 乘员能否使用物品：安装级覆盖优先，缺省取型号定义。 */
    public boolean isAllowUseItems() {
        return allowUseItemsOverride.orElse(staticAttribute.isAllowUseItems());
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
                throw new IllegalStateException(MMLanguageRegistry.ERROR_SEAT_SUBSYSTEM_EMPTY_DISMOUNT_LOCATOR.tag);
        }
        return List.copyOf(dismountLocators);
    }


    /**
     * 座位安装点必须落在宿主子部件区间内；下车点缺失不阻断装载——下车流程本身会跳过该候选点并告警。
     *
     * <p>{@code locator} 在构造器里已拒绝空值，因此这里只需处理名字解析不到的情况。</p>
     */
    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        if (!hasLocator(variant, subPartName, locator)) {
            throw locatorNotFound(variant, subPartName, subsystemName, locator);
        }
        for (String dismountLocator : dismountLocators) {
            if (hasLocator(variant, subPartName, dismountLocator)) continue;
            MachineMax.LOGGER.warn(
                    "座位子系统 {} 声明了下车定位器 {}，但它不在子部件 {}（模型 {}）的区间内；下车流程会跳过该候选点",
                    subsystemName, dismountLocator, subPartName, variant.getModel());
        }
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
