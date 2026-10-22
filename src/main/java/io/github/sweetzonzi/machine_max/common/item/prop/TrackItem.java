package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 履带物品：右键主动轮开始铺设，依次右键各负重轮，最后再次右键主动轮闭合成一条履带。
 *
 * <p>型号由 {@code machine_max:fitting_type} 组件给出（见 {@link FittingItem}）；型号只
 * 影响属性与外观，铺设流程与左右分侧无关。履带是本类唯一的玩法，基类
 * {@link FittingItem} 不参与铺设。</p>
 *
 * <p>潜行右键：铺设中撤销最后一个负重轮；已闭合时移除目标轮，移除主动轮会解散整条履带。</p>
 *
 * <p>交互处理在 {@link io.github.sweetzonzi.machine_max.common.item.ItemInteractHandler}，
 * 实际成员变更在服务端完成。</p>
 */
public class TrackItem extends FittingItem {
    public TrackItem() {
        super();
    }

    /**
     * 解析物品携带的履带型号。
     *
     * <p>这是履带的交互入口，组件缺失、型号不存在、型号不是履带类型都只在这里告警；
     * 渲染路径走无声的 {@link FittingItem#typeOf}。</p>
     *
     * @param level 用于选择客户端 / 服务端型号注册表
     * @param stack 履带物品
     * @return 型号；型号缺失、不存在或不是履带类型时返回 {@code null}
     */
    @Nullable
    public static TrackType resolveType(Level level, ItemStack stack) {
        if (stack.get(MMDataComponents.getFITTING_TYPE()) == null) {
            MachineMax.LOGGER.warn("履带物品缺少 fitting_type 组件，使用默认型号 {}", FittingType.DEFAULT_ID);
        }
        FittingType type = typeOf(level, stack);
        if (type == null) {
            MachineMax.LOGGER.warn("履带型号 {} 不存在", idOf(stack));
            return null;
        }
        if (type instanceof TrackType trackType) return trackType;
        MachineMax.LOGGER.warn("载具配件型号 {} 不是履带类型，不能用于铺设", idOf(stack));
        return null;
    }
}
