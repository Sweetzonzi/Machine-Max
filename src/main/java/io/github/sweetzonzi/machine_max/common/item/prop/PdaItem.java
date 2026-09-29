package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.PartType;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleAssemblyHelper;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyRequestPayload;
import io.github.sweetzonzi.machine_max.network.payload.pda.PdaSetDesignModePayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 蓝图终端（PDA）：收纳零件制造蓝图与通用制造蓝图，并作为零件来源参与装配。
 *
 * <p>全部状态存在 {@link PdaData} 数据组件（{@code machine_max:pda_data}）里，随物品走：放入容器、
 * 丢弃、交易、死亡掉落都自然带上。它同时是"谁能提供零件来源"这个问题的答案载体——覆写
 * {@link #getPartType} 后，服务端与客户端从同一份 {@code selected} 得到同一个结果。</p>
 *
 * <p>右键分派：潜行键切换设计模式；设计模式下右键按当前格位放置零件；常态下右键打开管理界面。
 * 管理界面是客户端独有物，由 {@link #setScreenOpener} 注入的钩子打开。</p>
 */
public class PdaItem extends Item implements PartAssemblyItem {

    /**
     * 打开管理界面的客户端实现。
     *
     * <p>本类属于共通代码，专用服务器同样会加载它，因此这里只持有 {@link Consumer}：直接引用客户端
     * 界面类会让类校验在专用服务器上加载 {@code net.minecraft.client} 下的类而失败。默认空实现，
     * 由客户端入口 {@code MachineMaxClient} 覆盖。</p>
     */
    private static Consumer<InteractionHand> screenOpener = hand -> {
    };

    public PdaItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 注入打开管理界面的客户端实现。只在客户端调用，专用服务器保持默认空实现。
     */
    public static void setScreenOpener(@NotNull Consumer<InteractionHand> opener) {
        screenOpener = Objects.requireNonNull(opener, "screenOpener");
    }

    /**
     * 右键使用：潜行键切换设计模式；设计模式下按当前格位放置零件；其余情形打开管理界面。
     *
     * <p>客户端的两个状态字段（{@code designMode} / {@code selected}）采用"先本地写、后发包"的
     * 预写方式，服务端回显同值；服务端不解释模式含义，只做权威写入。</p>
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (!level.isClientSide()) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            PdaData data = PdaHelper.getData(stack);
            boolean next = !data.designMode();
            PdaHelper.setData(stack, data.withDesignMode(next));
            PacketDistributor.sendToServer(new PdaSetDesignModePayload(usedHand, next));
            player.displayClientMessage(Component.translatable(next
                    ? "message.machine_max.pda.design_mode.enter"
                    : "message.machine_max.pda.design_mode.exit"), true);
            return InteractionResultHolder.success(stack);
        }
        if (PdaHelper.getData(stack).designMode()) {
            PartAssemblyRequestPayload request = VehicleAssemblyHelper.getInstance().buildRequest(player, usedHand, stack);
            if (request != null) PacketDistributor.sendToServer(request);
            return InteractionResultHolder.success(stack);
        }
        screenOpener.accept(usedHand);
        return InteractionResultHolder.success(stack);
    }

    /**
     * 覆写零件来源解析：设计模式开启且当前格位绑定的是零件配方时，返回该配方的零件类型。
     *
     * <p>设计模式关闭、格位为空、条目不是零件配方（通用配方或配方已失效）三种情形都返回
     * {@code null}，对外不可区分——消费方一律按"该物品此刻不提供零件来源"处理。</p>
     */
    @Override
    public @Nullable PartType getPartType(ItemStack stack, Level level) {
        PdaData data = PdaHelper.getData(stack);
        if (!data.designMode()) return null;
        ResourceLocation recipeId = data.shortcutAt(data.selected());
        if (recipeId == null) return null;
        RecipeHolder<PartFabricatingRecipe> holder = PdaHelper.partRecipeOf(level, recipeId);
        if (holder == null) return null;
        ResourceLocation partTypeId = holder.value().getPartType();
        return partTypeId == null ? null : PartType.get(level, partTypeId);
    }
}
