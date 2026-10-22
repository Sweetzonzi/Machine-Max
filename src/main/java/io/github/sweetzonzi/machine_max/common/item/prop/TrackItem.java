package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.track.TrackLinkData;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 履带物品：右键主动轮开始铺设，依次右键各负重轮，最后再次右键主动轮闭合成一条履带。
 *
 * <p>物品本身只表示「一条履带」，具体型号由 {@code machine_max:fitting_type} 组件的
 * 型号 id 决定；组件缺失时回落 {@link TrackLinkData#DEFAULT_TYPE} 并打 warn。型号只
 * 影响属性与外观，铺设流程与左右分侧无关。</p>
 *
 * <p>潜行右键：铺设中撤销最后一个负重轮；已闭合时移除目标轮，移除主动轮会解散整条履带。</p>
 *
 * <p>交互处理在 {@link io.github.sweetzonzi.machine_max.common.item.ItemInteractHandler}，
 * 实际成员变更在服务端完成。</p>
 */
public class TrackItem extends Item {
    public TrackItem() {
        super(new Properties().stacksTo(1));
    }

    /**
     * 解析物品携带的履带型号。
     *
     * @param level 用于选择客户端 / 服务端型号注册表
     * @param stack 履带物品
     * @return 型号；型号缺失或不是履带类型时返回 {@code null}
     */
    @Nullable
    public static TrackType resolveType(Level level, ItemStack stack) {
        ResourceLocation id = stack.get(MMDataComponents.getFITTING_TYPE());
        if (id == null) {
            id = TrackLinkData.DEFAULT_TYPE;
            MachineMax.LOGGER.warn("履带物品缺少 fitting_type 组件，使用默认型号 {}", id);
        }
        FittingType type = MMDynamicRes.getFittingType(level, id);
        if (type instanceof TrackType trackType) return trackType;
        MachineMax.LOGGER.warn("履带型号 {} 不存在或不是履带类型", id);
        return null;
    }
}
