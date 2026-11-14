package io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.static_attr;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import lombok.Getter;
import org.jetbrains.annotations.ApiStatus;

/**
 * 物品弹药架子系统静态属性。<br>
 * 只描述物品容器的容量：物件、堆叠与物品到交付规格的映射属于物品往返实现。
 * <p>
 * <b>WIP：</b>物品与 {@code AmmoBelt} 的往返尚未实现，本类型的容量与拒收语义成立，
 * 但第一方还没有实例使用它。
 */
@ApiStatus.Experimental
@Getter
public class AmmoRackSubsystemStaticAttr extends BasicSubsystemStaticAttr {

    /** 物品容器格数 */
    private final int magazineCapacity;

    public static final MapCodec<AmmoRackSubsystemStaticAttr> CODEC =
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    BasicAttr.CODEC.forGetter(BasicSubsystemStaticAttr::getBasicAttr),
                    Codec.INT.optionalFieldOf("magazine_capacity", 30)
                            .forGetter(AmmoRackSubsystemStaticAttr::getMagazineCapacity),
                    BasicSoundAttr.CODEC.codec().optionalFieldOf("sounds", BasicSoundAttr.DEFAULT)
                            .forGetter(BasicSubsystemStaticAttr::getSoundAttr)
            ).apply(instance, AmmoRackSubsystemStaticAttr::new));

    public AmmoRackSubsystemStaticAttr(
            BasicAttr basicAttr,
            int magazineCapacity,
            BasicSoundAttr sounds) {
        super(basicAttr, sounds);
        this.magazineCapacity = magazineCapacity;
    }

    @Override
    public MapCodec<? extends AbstractSubsystemStaticAttr> codec() {
        return CODEC;
    }

}
