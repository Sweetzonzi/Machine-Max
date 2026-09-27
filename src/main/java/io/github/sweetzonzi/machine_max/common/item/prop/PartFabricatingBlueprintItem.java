package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 零件制造蓝图：代表某个零件配方的蓝图物品。
 *
 * <p>可放置为线框零件、可参与装配，并在选中时提供连接点预览与瞄准提示。显示名、图标与零件标签
 * 都读 {@code machine_max:part_type}；{@code machine_max:recipe_type} 供装配门禁、JEI 子类型与研发界面使用。</p>
 */
public class PartFabricatingBlueprintItem extends FabricatingBlueprintItem implements PartAssemblyItem {

    public PartFabricatingBlueprintItem() {
        super();
    }

    /**
     * 右键点击物品，尝试将零件放置到世界中或尝试与选择的连接口连接。
     * <p>客户端基于本地装配选择状态构造请求上报，服务端由 {@code VehicleAssemblyServerHelper} 权威处理。</p>
     *
     * @param level    世界
     * @param player   玩家
     * @param usedHand 玩家使用的手
     * @return 互动结果
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            PartAssemblyRequestPayload request = VehicleAssemblyHelper.getInstance().buildRequest(player, usedHand, stack);
            if (request == null) return InteractionResultHolder.pass(stack);
            PacketDistributor.sendToServer(request);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
        appendPartTags(stack, context, tooltipComponents, tooltipFlag);
    }
}
