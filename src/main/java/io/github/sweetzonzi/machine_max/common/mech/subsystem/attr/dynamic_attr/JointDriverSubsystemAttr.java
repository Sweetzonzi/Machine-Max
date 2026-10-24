package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.ISubsystemHost;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.MotorAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.SubsystemTypes;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.JointDriverSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.JointDriverSubsystem;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import io.github.sweetzonzi.machine_max.util.data.Axis;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.ApiStatus;

import java.util.Map;

/**
 * 关节驱动子系统动态属性：指定被控连接点、旋转顺序与各轴驱动参数。
 * <p>
 * <b>WIP：</b>{@link SubsystemTypes#JOINT} 的 JSON 契约尚未定稿，平动轴控制仍在实现中。
 */
@ApiStatus.Experimental
@Getter
public class JointDriverSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final JointDriverSubsystemStaticAttr staticAttribute;
    public final String controlledConnector;
    public final String rotationOrder;
    public final Map<Axis, MotorAttr> axisParams;

    public static final MapCodec<JointDriverSubsystemAttr> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("definition").forGetter(AbstractSubsystemAttr::getModelName),
            Codec.STRING.fieldOf("locator").forGetter(JointDriverSubsystemAttr::getControlledConnector),
            Codec.STRING.fieldOf("rotation_order").forGetter(JointDriverSubsystemAttr::getRotationOrder),
            MotorAttr.MAP_CODEC.fieldOf("axes").forGetter(JointDriverSubsystemAttr::getAxisParams)
    ).apply(instance, JointDriverSubsystemAttr::new
    ));

    public JointDriverSubsystemAttr(
            ResourceLocation modelName,
            String controlledConnector,
            String rotationOrder,
            Map<Axis, MotorAttr> axisParams) {
        super(modelName);
        this.staticAttribute = (JointDriverSubsystemStaticAttr) getStaticAttr();
        this.controlledConnector = controlledConnector;
        this.rotationOrder = rotationOrder;
        this.axisParams = axisParams;
    }

    /** JSON 字段名沿用 {@code locator}，值是被控连接点名；必须存在且为高级连接点。 */
    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        requireAdvancedConnector(variant, subPartName, subsystemName, controlledConnector);
    }

    @Override
    public MapCodec<? extends AbstractSubsystemAttr> codec() {
        return CODEC;
    }

    @Override
    public SubsystemTypes getType() {
        return SubsystemTypes.JOINT  ;
    }

    @Override
    public AbstractSubsystem createSubsystem(ISubsystemHost owner, String name) {
        return new JointDriverSubsystem(owner, name, this);
    }
}