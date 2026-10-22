package io.github.sweetzonzi.machine_max.common.item;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

/**
 * 配件种类 → 物品的派发点。
 *
 * <p>配件型号是内容包数据（{@link FittingType}），物品是注册表对象；两者不是一一对应：
 * 同一种类共用一个物品，型号靠 {@code machine_max:fitting_type} 组件区分。这层映射放在
 * 物品侧，使 {@code common/mech/vehicle/fitting/} 不反向依赖物品注册表。</p>
 *
 * <p>新增配件种类时，在 {@link #itemFor} 里加一条分支即可；创造栏、型号发放与
 * 交互分发都经这里取物品。</p>
 */
public final class FittingItems {
    private FittingItems() {
    }

    /**
     * 取某个型号对应的物品。
     *
     * @param type 配件型号
     * @return 该种类的物品；种类还没有对应物品时返回 {@code null} 并打 warn
     */
    @Nullable
    public static Item itemFor(FittingType type) {
        return switch (type.getSerializedName()) {
            case "track" -> MMItems.getTRACK().get();
            default -> {
                MachineMax.LOGGER.warn("载具配件种类 {} 没有对应的物品", type.getSerializedName());
                yield null;
            }
        };
    }
}
