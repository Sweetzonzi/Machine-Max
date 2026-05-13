package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.LauncherSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.LauncherSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 发射器子系统动态属性。<br>
 * 定义发射点locator、弹药反馈输出等运行时配置。<br>
 * 输入频道（开火信号）在静态属性中定义。
 */
@Getter
public class LauncherSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final LauncherSubsystemStaticAttr staticAttribute;
    public final String locator;                              // 发射点locator名称
    public final Map<String, List<String>> ammoCountOutputs;   // 剩余弹药反馈输出

    public static final MapCodec<LauncherSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("locator").forGetter(LauncherSubsystemAttr::getLocator),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("ammo_outputs", Map.of()).forGetter(LauncherSubsystemAttr::getAmmoCountOutputs)
    ).apply(instance, LauncherSubsystemAttr::new));

    public LauncherSubsystemAttr(
            ResourceLocation modelName,
            String locator,
            Map<String, List<String>> ammoCountOutputs) {
        super(modelName);
        this.staticAttribute = (LauncherSubsystemStaticAttr) getStaticAttr();
        this.locator = locator;
        this.ammoCountOutputs = ammoCountOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.LAUNCHER;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new LauncherSubsystem(owner, name, this);
    }
}
