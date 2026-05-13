package io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.vehicle.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.vehicle.attr.subsystem.static_attr.TurretDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.vehicle.subsystem.TurretDriverSubsystem;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/**
 * 炮塔驱动子系统动态属性。<br>
 * 定义受控连接器名、以及旋转角度反馈输出频道配置。
 * 使用统一的 RotationSignal（x=pitch, y=yaw, z=roll）进行输入输出。<br>
 * 输入频道在静态属性中定义。<br>
 * 硬件参数（最大力矩/速度）在静态属性中定义。
 */
@Getter
public class TurretDriverSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final TurretDriverSubsystemStaticAttr staticAttribute;
    public final String controlledConnector;
    /** 当前关节角度反馈输出频道 */
    public final Map<String, List<String>> rotationAngleOutputs;

    public static final MapCodec<TurretDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("connector").forGetter(TurretDriverSubsystemAttr::getControlledConnector),
            AbstractSubsystemAttr.SIGNAL_TARGETS_CODEC.optionalFieldOf("rotation_outputs", Map.of()).forGetter(TurretDriverSubsystemAttr::getRotationAngleOutputs)
    ).apply(instance, TurretDriverSubsystemAttr::new
    ));

    public TurretDriverSubsystemAttr(
            ResourceLocation modelName,
            String controlledConnector,
            Map<String, List<String>> rotationAngleOutputs) {
        super(modelName);
        this.staticAttribute = (TurretDriverSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
        this.rotationAngleOutputs = rotationAngleOutputs;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.TURRET;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new TurretDriverSubsystem(owner, name, this);
    }
}
