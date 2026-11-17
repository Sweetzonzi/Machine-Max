package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.FittingType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.fitting.TrackType;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.datagen.MMLanguageRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 履带物品：一件 = 一节链板。右键主动轮开始铺设，依次右键各负重轮，最后再次右键主动轮闭合成
 * 一条履带，并按推导出的节数 N 一次性扣 N 节。
 *
 * <p>型号由 {@code machine_max:fitting_type} 组件给出（见 {@link FittingItem}）；型号只
 * 影响属性与外观，铺设流程与左右分侧无关。履带是本类唯一的玩法，基类
 * {@link FittingItem} 不参与铺设。</p>
 *
 * <p>潜行右键：铺设中撤销最后一个负重轮。已闭合的履带由撬棍整条拆除，本物品不提供逐成员移除。</p>
 *
 * <p>交互处理在 {@link io.github.sweetzonzi.machine_max.common.item.ItemInteractHandler}，
 * 实际成员变更与扣料在服务端完成。</p>
 */
public class TrackItem extends FittingItem {
    public TrackItem() {
        // 1 件 = 1 节：堆叠数就是节数，铺设与维修都按件扣
        super(new Properties().stacksTo(64));
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

    /**
     * 悬浮提示补一行「1 件 = 1 节」：堆叠数就是节数，铺设与维修都按件扣。
     *
     * <p>物品名取型号翻译键（如「M1A2 履带节」），这里只说清单件的口径。</p>
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable(MMLanguageRegistry.TOOLTIP_TRACK_LINK_UNIT.tag)
                .withStyle(ChatFormatting.GRAY));
    }
}
