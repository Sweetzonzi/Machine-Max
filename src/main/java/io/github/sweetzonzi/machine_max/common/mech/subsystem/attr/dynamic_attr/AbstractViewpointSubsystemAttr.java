package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr.AbstractViewpointSubsystemStaticAttr;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.attr.VariantAttr;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;

/**
 * 实体视点的公共安装属性。<br>
 * 所属 SubPart 由子系统配置位置确定；locator 提供相对该刚体的位置与旋转。
 */
@Getter
public abstract class AbstractViewpointSubsystemAttr extends BasicSubsystemDynamicAttr {
    public final AbstractViewpointSubsystemStaticAttr staticAttribute;
    /** 挂点名称；空字符串使用宿主刚体的原点与坐标轴。 */
    public final String locator;
    protected AbstractViewpointSubsystemAttr(ResourceLocation definition, String locator) {
        super(definition);
        this.staticAttribute = (AbstractViewpointSubsystemStaticAttr) getStaticAttr();
        this.locator = locator;
    }

    /** 空挂点表示使用宿主刚体原点，不参与区间检查；非空则必须落在宿主子部件内。 */
    @Override
    public void validate(VariantAttr variant, String subPartName, String subsystemName) {
        if (locator != null && !locator.isEmpty() && !hasLocator(variant, subPartName, locator)) {
            throw locatorNotFound(variant, subPartName, subsystemName, locator);
        }
    }
}
