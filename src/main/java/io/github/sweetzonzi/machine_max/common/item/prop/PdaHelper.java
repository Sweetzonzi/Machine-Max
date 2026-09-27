package io.github.sweetzonzi.machine_max.common.item.prop;

import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * PDA 蓝图终端的静态工具：数据组件读写、配方反查、蓝图归一与手持定位。
 *
 * <p>本类无内部状态，所有方法可在主线程调用。{@link #getData} 与 {@link #selectedRecipe} 只读；
 * {@link #setData} 是唯一的写入入口，内部先经 {@link PdaData#sanitized()} 收口。</p>
 *
 * <p>配方反查一律走 {@link MMDynamicRes} 的本侧索引，不查 {@code RecipeManager}，与装配侧既有约定一致。</p>
 */
public final class PdaHelper {
    private PdaHelper() {
    }

    /** 该物品栈是否为 PDA。 */
    public static boolean isPda(@Nullable ItemStack stack) {
        return stack != null && stack.getItem() instanceof PdaItem;
    }

    /** 读数据组件；栈为空、缺失组件时返回 {@link PdaData#EMPTY}，不返回 {@code null}。 */
    public static PdaData getData(@Nullable ItemStack stack) {
        if (stack == null) return PdaData.EMPTY;
        PdaData data = stack.get(MMDataComponents.getPDA_DATA());
        return data == null ? PdaData.EMPTY : data;
    }

    /** 写入数据组件；内部先 {@link PdaData#sanitized()} 收口。空栈不做任何事。 */
    public static void setData(@Nullable ItemStack stack, PdaData data) {
        if (stack == null || stack.isEmpty() || data == null) return;
        stack.set(MMDataComponents.getPDA_DATA(), data.sanitized());
    }

    /**
     * 按配方 id 取本侧配方。
     *
     * @return 索引未就绪或该 id 不存在时返回 {@code null}
     */
    @Nullable
    public static RecipeHolder<FabricatingRecipe> recipeOf(@Nullable Level level, @Nullable ResourceLocation recipeId) {
        if (level == null || recipeId == null) return null;
        return MMDynamicRes.getAllFabricating(level).get(recipeId);
    }

    /**
     * 按配方 id 取零件配方，即"该条目是不是零件配方"的判据。
     *
     * @return 不是零件配方、或该 id 不存在时返回 {@code null}
     */
    @Nullable
    public static RecipeHolder<PartFabricatingRecipe> partRecipeOf(@Nullable Level level, @Nullable ResourceLocation recipeId) {
        RecipeHolder<FabricatingRecipe> holder = recipeOf(level, recipeId);
        if (holder != null && holder.value() instanceof PartFabricatingRecipe partRecipe) {
            return new RecipeHolder<>(holder.id(), partRecipe);
        }
        return null;
    }

    /**
     * 把一张蓝图物品解析为配方 id（存入时的归一入口）。
     *
     * <p>归一顺序与 {@link PartAssemblyItem#getRecipeHolder} 一致：先经 {@code part_type} 组件反查本侧
     * 零件配方取其 id，反查不到时再读 {@code recipe_type} 组件。</p>
     *
     * @return 非蓝图物品、两个组件都缺失、或反查不到配方时返回 {@code null}
     */
    @Nullable
    public static ResourceLocation recipeIdOf(@Nullable ItemStack stack, @Nullable Level level) {
        if (stack == null || level == null) return null;
        if (!(stack.getItem() instanceof FabricatingBlueprintItem)) return null;
        ResourceLocation partTypeId = stack.get(MMDataComponents.getPART_TYPE());
        if (partTypeId != null) {
            RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(level, partTypeId);
            if (holder != null) return holder.id();
        }
        return stack.get(MMDataComponents.getRECIPE_TYPE());
    }

    /** 读某一格位绑定的配方 id；栈不是 PDA、格位越界或未绑定时返回 {@code null}。 */
    @Nullable
    public static ResourceLocation getShortcut(@Nullable ItemStack pdaStack, int shortcutIndex) {
        if (!isPda(pdaStack)) return null;
        return getData(pdaStack).shortcutAt(shortcutIndex);
    }

    /** 读当前格位（{@code selected}）绑定的配方 id；栈不是 PDA、或该格位未绑定时返回 {@code null}。 */
    @Nullable
    public static ResourceLocation selectedRecipe(@Nullable ItemStack pdaStack) {
        if (!isPda(pdaStack)) return null;
        PdaData data = getData(pdaStack);
        return data.shortcutAt(data.selected());
    }

    /**
     * 玩家主手优先、副手兜底地找出持有的 PDA 在哪只手。
     *
     * @return 两只手都没有 PDA 时返回 {@code null}
     */
    @Nullable
    public static InteractionHand heldPdaHand(@Nullable Player player) {
        if (player == null) return null;
        if (isPda(player.getMainHandItem())) return InteractionHand.MAIN_HAND;
        if (isPda(player.getOffhandItem())) return InteractionHand.OFF_HAND;
        return null;
    }
}
