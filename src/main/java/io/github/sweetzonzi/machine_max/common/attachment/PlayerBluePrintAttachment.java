package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import lombok.Getter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

import java.util.HashMap;
import java.util.LinkedHashSet;


@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class PlayerBluePrintAttachment {
    @Getter
    private boolean dirty = true;
    @Getter
    private int researchPoint;
    private int inventoryHash = Integer.MIN_VALUE;
    private final HashMap<ResourceLocation, LinkedHashSet<RecipeHolder<FabricatingRecipe>>> availableRecipes = new HashMap<>();
    private final HashMap<ResourceLocation, LinkedHashSet<RecipeHolder<FabricatingRecipe>>> allRecipes = new HashMap<>();
    private static final LinkedHashSet<RecipeHolder<FabricatingRecipe>> EMPTY_RECIPE_LIST = new LinkedHashSet<>();

    public static final Codec<PlayerBluePrintAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.fieldOf("research_point").forGetter(PlayerBluePrintAttachment::getResearchPoint)
            ).apply(instance, PlayerBluePrintAttachment::new)
    );


    public PlayerBluePrintAttachment(int researchPoint) {
        this.researchPoint = researchPoint;
    }

    public void addRP(Player player, int amount) {
        researchPoint += Math.max(0, amount);
        this.onRPChange(player);
    }

    public boolean hasEnoughRP(int amount) {
        return researchPoint >= amount;
    }

    public void consumeRP(Player player, int amount) {
        this.researchPoint -= Math.min(amount, researchPoint);
        this.onRPChange(player);
    }

    private void onRPChange(Player player) {
        // TODO: 发包同步研发点数据

    }

    public void markDirty() {
        dirty = true;
    }

    /**
     * 统计玩家库存，获取所有可用于制造指定部件的配方，创造模式无视库存直接展示所有配方
     *
     * @param player   玩家
     * @param partType 部件类型
     * @return 可用配方集合
     */
    public LinkedHashSet<RecipeHolder<FabricatingRecipe>> getAvailableRecipeFor(Player player, ResourceLocation partType) {
        if (isDirty() && !player.isCreative()) {
            rebuildAvailableRecipes(player);
        }
        if (!player.isCreative())
            return availableRecipes.getOrDefault(partType, EMPTY_RECIPE_LIST);
        else return MMDynamicRes.PART_RECIPES.get(partType);
    }

    /**
     * 更新可用配方列表
     *
     * @param player 玩家
     */
    private void rebuildAvailableRecipes(Player player) {
        var research = player.getData(MMAttachments.getRESEARCH_AND_BLUEPRINT());
        research.inventoryHash = research.hashInventory(player);
        availableRecipes.clear();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            var stack = inventory.items.get(i);
            if (stack.getItem() instanceof FabricatingBlueprintItem) {
                RecipeHolder<?> recipeHolder = PartAssemblyItem.getRecipeHolder(stack, player.level());
                if (recipeHolder != null && recipeHolder.value() instanceof FabricatingRecipe fabricatingRecipe) {
                    RecipeHolder<FabricatingRecipe> fabRecipeHolder = (RecipeHolder<FabricatingRecipe>) recipeHolder;
                    ItemStack result = fabricatingRecipe.getResultItem(player.level().registryAccess());
                    if (result.getItem() instanceof PartItem) {
                        ResourceLocation partRegistryKey = result.get(MMDataComponents.getPART_TYPE());
                        if (partRegistryKey != null) {
                            availableRecipes.computeIfAbsent(partRegistryKey, k -> new LinkedHashSet<>()).add(fabRecipeHolder);
                        }
                    }
                }

            }
        }
    }

    private int hashInventory(Player player) {
        int hash = 1;
        for (ItemStack stack : player.getInventory().items) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(stack);
        }
        return hash;
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Player player) {
            if (!player.hasData(MMAttachments.getRESEARCH_AND_BLUEPRINT())) {
                player.setData(MMAttachments.getRESEARCH_AND_BLUEPRINT(), new PlayerBluePrintAttachment(0));
            }
            if (player.hasData(MMAttachments.getRESEARCH_AND_BLUEPRINT())) {
                var research = player.getData(MMAttachments.getRESEARCH_AND_BLUEPRINT());
                if (player.tickCount % 100 == 0) { // 定时更新可用配方列表
                    if (research.hashInventory(player) != research.inventoryHash) {
                        research.rebuildAvailableRecipes(player);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        if (!event.getEntity().level().isClientSide()) {
            Player player = event.getEntity();
            var research = player.getData(MMAttachments.getRESEARCH_AND_BLUEPRINT());
            research.addRP(player, event.getAmount());
        }
    }

    @SubscribeEvent
    public static void onContainerChanged(PlayerContainerEvent.Close event) {
        event.getEntity().getData(MMAttachments.getRESEARCH_AND_BLUEPRINT()).markDirty();
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        event.getEntity().getData(MMAttachments.getRESEARCH_AND_BLUEPRINT()).markDirty();
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        event.getPlayer().getData(MMAttachments.getRESEARCH_AND_BLUEPRINT()).markDirty();
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        event.getPlayer().getData(MMAttachments.getRESEARCH_AND_BLUEPRINT()).markDirty();
    }


}
