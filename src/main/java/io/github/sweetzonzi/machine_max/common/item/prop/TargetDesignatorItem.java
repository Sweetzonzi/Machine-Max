package io.github.sweetzonzi.machine_max.common.item.prop;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * 目标指示器：手持时标注视野内可锁定的碰撞箱，右键建立或取消锁定。
 *
 * <p>锁定完全是客户端状态（见设计文档 §4.4）：物品没有服务端逻辑，服务端收到原版使用包后
 * 直接返回 {@link InteractionResultHolder#pass}，不写任何数据，也不产生本模组的载荷。</p>
 *
 * <p>本类属于共通代码，专用服务器同样会加载它，因此这里只持有 {@link Consumer}：直接引用客户端类会让
 * 类校验在专用服务器上加载 {@code net.minecraft.client} 下的类而失败。默认空实现，由客户端入口
 * {@code MachineMaxClient} 覆盖。</p>
 */
public class TargetDesignatorItem extends Item {

    private static Consumer<Player> useHandler = player -> {
    };

    public TargetDesignatorItem() {
        super(new Item.Properties().stacksTo(1));
    }

    /**
     * 注入右键的客户端实现。只在客户端调用，专用服务器保持默认空实现。
     */
    public static void setUseHandler(@NotNull Consumer<Player> handler) {
        useHandler = Objects.requireNonNull(handler, "useHandler");
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player,
                                                           @NotNull InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            useHandler.accept(player);
            return InteractionResultHolder.success(stack);
        }
        return InteractionResultHolder.pass(stack);
    }
}
