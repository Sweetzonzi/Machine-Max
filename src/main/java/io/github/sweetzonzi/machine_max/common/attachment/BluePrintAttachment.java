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
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.research.*;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;

//TODO:
// 加点，可自定义维修与组装速度加成，耐久度加成，根据加点数决定蓝图版本号和claim所需自由研发点
// 随身蓝图库，类似于末影箱，检查条件时同样检查这里
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class BluePrintAttachment {
    @Getter
    private int freeResearchPoint;
    @Getter
    private ResourceLocation researchingRecipe;
    @Getter
    private final Map<ResourceLocation, Float> researchedRecipes; // 所有研发过的配方及其研究层数
    private final Map<ResourceLocation, LinkedHashSet<RecipeHolder<FabricatingRecipe>>> availableRecipes = new HashMap<>();
    @Getter
    @Setter
    private ItemStack product;
    @Getter
    private boolean dirty = true;
    private int inventoryHash = Integer.MIN_VALUE;
    /**
     * 每级研究等级的组装速度加成
     */
    public static final float ASSEMBLY_BUFF_PER_LEVEL = 0.05f;
    /**
     * 持有蓝图时的组装速度加成
     */
    public static final float ASSEMBLY_BUFF_WITH_BLUEPRINT = 0.5f;
    /**
     * 每级研究等级的维修速度加成
     */
    public static final float REPAIR_BUFF_PER_LEVEL = 0.05f;
    /**
     * 持有蓝图时的维修速度加成
     */
    public static final float REPAIR_BUFF_WITH_BLUEPRINT = 0.3f;

    public static final Codec<Map<ResourceLocation, Float>> RESEARCHED_RECIPES_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, Codec.FLOAT);

    public static final Codec<BluePrintAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("research_point", 0).forGetter(BluePrintAttachment::getFreeResearchPoint),
                    ResourceLocation.CODEC.optionalFieldOf("researching_recipe", FabricatingRecipe.EMPTY).forGetter(BluePrintAttachment::getResearchingRecipe),
                    RESEARCHED_RECIPES_CODEC.optionalFieldOf("researched_recipes", new HashMap<>()).forGetter(BluePrintAttachment::getResearchedRecipes),
                    ItemStack.CODEC.optionalFieldOf("product", ItemStack.EMPTY).forGetter(BluePrintAttachment::getProduct)
            ).apply(instance, BluePrintAttachment::new)
    );

    public BluePrintAttachment(int freeResearchPoint, ResourceLocation researchingRecipe, Map<ResourceLocation, Float> researchedRecipes, ItemStack product) {
        this.freeResearchPoint = freeResearchPoint;
        this.researchingRecipe = researchingRecipe;
        this.researchedRecipes = researchedRecipes;
        this.product = product;
    }

    public BluePrintAttachment(int freeResearchPoint) {
        this.freeResearchPoint = freeResearchPoint;
        this.researchingRecipe = FabricatingRecipe.EMPTY;
        this.researchedRecipes = new HashMap<>();
        this.product = ItemStack.EMPTY;
    }

    /**
     * 检查是否满足重新获取蓝图的条件
     *
     * @param recipe 配方ID
     * @return 是否可获取
     */
    public boolean canReclaim(ResourceLocation recipe) {
        int level = getResearchLevel(recipe);
        if (level >= 1) {
            RecipeHolder<FabricatingRecipe> recipeHolder = getAllResearchable().get(recipe);
            if (recipeHolder != null) {
                int requiredResearchPoint = getReclaimRpCost(recipe);
                return freeResearchPoint >= requiredResearchPoint;
            } else return false;
        } else return false;
    }


    /**
     * 重新获取某个已经研发过的蓝图
     *
     * @param player 玩家
     * @param recipe 配方ID
     * @return 蓝图物品
     */
    public ItemStack reclaim(Player player, ResourceLocation recipe) {
        if (canReclaim(recipe)) {
            int rpCost = getReclaimRpCost(recipe);
            setFreeResearchPoint(player, freeResearchPoint - rpCost);
            ItemStack stack = new ItemStack(MMItems.getFABRICATING_BLUEPRINT());
            stack.set(MMDataComponents.getRECIPE_TYPE(), recipe);
            return stack;
        } else return ItemStack.EMPTY;
    }

    /**
     * 推进当前正在研究的配方的研发进度
     *
     * @param basicRp 基础研发点数，受到研究的配方的已研究等级影响
     */
    public void research(Player player, int basicRp) {
        int freeRp = 0;
        if (researchingRecipe != FabricatingRecipe.EMPTY) {
            if (!hasStartedResearching(researchingRecipe))
                throw new IllegalStateException("Cannot research recipe before starting researching it");
            RecipeHolder<FabricatingRecipe> recipeHolder = getResearching();
            // 当前配方已研发进度
            float currentResearchProgress = researchedRecipes.getOrDefault(researchingRecipe, 0f);
            if (recipeHolder != null && currentResearchProgress > 0f) { // 仅提交材料并开始了的研究可推进研究进度
                FabricatingRecipe recipe = recipeHolder.value();
                int requiredResearchPoint = recipe.getResearchPointCost();
                // 当前研发等级
                int currentResearchLevel = getResearchLevel(researchingRecipe);
                // 当前等级研发进度
                float currentLevelResearchProgress = currentResearchProgress - currentResearchLevel;
                // 计算当前轮次已有的研发点数
                int currentLevelResearchPoint = (int) (currentLevelResearchProgress * requiredResearchPoint);
                int rpToAdd = calculateRpToAdd(basicRp);
                if (currentLevelResearchPoint + rpToAdd < requiredResearchPoint) { // 未达到下一级研发点数则全部用于研发
                    researchedRecipes.put(researchingRecipe, currentLevelResearchProgress + (rpToAdd / (float) requiredResearchPoint));
                    if (player instanceof ServerPlayer serverPlayer) { // 发包同步
                        PacketDistributor.sendToPlayer(serverPlayer, new ResearchPushPayload(researchingRecipe, researchedRecipes.get(researchingRecipe)));
                    }
                } else { // 溢出部分作为自由研发点
                    if (product.isEmpty()) {
                        researchedRecipes.put(researchingRecipe, currentResearchLevel + 1f);
                        freeRp = rpToAdd - (requiredResearchPoint - currentLevelResearchPoint);
                        // 暂存蓝图物品
                        ItemStack stack = new ItemStack(MMItems.getFABRICATING_BLUEPRINT());
                        stack.set(MMDataComponents.getRECIPE_TYPE(), researchingRecipe); // 设置配方ID
                        this.product = stack; // 保存蓝图物品
                        markDirty(); // 标记可用配方列表需要更新
                        if (player instanceof ServerPlayer serverPlayer) { // 发包同步
                            PacketDistributor.sendToPlayer(serverPlayer, new ResearchCompletePayload(researchingRecipe, currentResearchLevel, stack));
                        }
                        researchingRecipe = FabricatingRecipe.EMPTY; // 研发完成，清空目标
                    } else freeRp = rpToAdd; // 蓝图物品已满，研发进度直接转化为自由研发点
                }
            } else clearResearching(player); // 清除非法研究目标
        } else { // 无研发目标则全部成为自由研发点
            freeRp = basicRp;
        }
        // 将溢出的研发点作为自由研发点储存
        setFreeResearchPoint(player, freeRp);
    }

    public void applyFreeRp(Player player) {
        if (researchingRecipe != FabricatingRecipe.EMPTY) {
            if (!hasStartedResearching(researchingRecipe))
                throw new IllegalStateException("Cannot research recipe before starting researching it");
            RecipeHolder<FabricatingRecipe> recipeHolder = getResearching();
            if (recipeHolder != null) {
                FabricatingRecipe recipe = recipeHolder.value();
                int requiredResearchPoint = recipe.getResearchPointCost();
                // 当前配方已研发进度
                float currentResearchProgress = researchedRecipes.computeIfAbsent(researchingRecipe, k -> Float.MIN_VALUE);
                // 当前研发等级
                int currentResearchLevel = getResearchLevel(researchingRecipe);
                // 当前等级研发进度
                float currentLevelResearchProgress = currentResearchProgress - currentResearchLevel;
                // 计算当前轮次已有的研发点数
                int currentLevelResearchPoint = (int) (currentLevelResearchProgress * requiredResearchPoint);
                if (currentLevelResearchPoint + freeResearchPoint < requiredResearchPoint) { // 未达到下一级研发点数则全部用于研发
                    researchedRecipes.put(researchingRecipe, currentLevelResearchProgress + (freeResearchPoint / (float) requiredResearchPoint));
                    setFreeResearchPoint(player, 0);
                } else { // 溢出部分作为自由研发点
                    markDirty(); // 标记可用配方列表需要更新
                    researchedRecipes.put(researchingRecipe, currentResearchLevel + 1f);
                    setFreeResearchPoint(player, freeResearchPoint - (requiredResearchPoint - currentLevelResearchPoint));
                    researchingRecipe = FabricatingRecipe.EMPTY; // 研发完成，清空目标
                }
            } else clearResearching(player); // 清除非法研究目标
        }
    }

    public int getResearchLevel(ResourceLocation recipe) {
        return (int) Math.floor(getResearchedRecipes().getOrDefault(recipe, 0f));
    }

    public int getRpCost(ResourceLocation recipe) {
        RecipeHolder<FabricatingRecipe> recipeHolder = getAllResearchable().get(recipe);
        if (recipeHolder != null) {
            int researchLevel = getResearchLevel(recipe);
            return recipeHolder.value().getResearchPointCost();
        } else return 0;
    }

    /**
     * 计算重新获取蓝图所需的研发点
     *
     * @param recipe 配方ID
     * @return 研发点消耗
     */
    public int getReclaimRpCost(ResourceLocation recipe) {
        return (int) (getRpCost(recipe) * 0.5f); // 暂定50%研发消耗
    }

    public void setFreeResearchPoint(Player player, int freeResearchPoint) {
        this.freeResearchPoint = freeResearchPoint;
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new FreeRpSyncPayload(freeResearchPoint));
        }
    }

    /**
     * 检查玩家背包，确认是否满足配方的研发条件
     *
     * @param player 玩家
     * @param recipe 配方ID
     * @return 是否可研发
     */
    public boolean canStartResearching(Player player, ResourceLocation recipe) {
        boolean result = false;
        var allRecipes = getAllResearchable();
        RecipeHolder<FabricatingRecipe> recipeHolder = allRecipes.get(recipe);
        if (recipeHolder != null) {
            result = recipeHolder.value().hasRequiredIngredients(player, true);
        }
        return result;
    }

    /**
     * 开始研究某个配方，消耗材料并设置当前正在研究的配方
     *
     * @param player 玩家
     * @param recipe 配方ID
     */
    public void startResearching(Player player, ResourceLocation recipe) {
        if (hasStartedResearching(recipe)) {
            setResearching(player, recipe);
        } else if (canStartResearching(player, recipe)) {
            var recipeHolder = getAllResearchable().get(recipe);
            if (recipeHolder != null) {
                recipeHolder.value().consumeIngredients(player, true);
                setResearching(player, recipe);
            }
        }
    }

    /**
     * 设置当前正在研究的配方，将会跳过材料检查，消耗材料的开始研究应使用{@link #startResearching(Player, ResourceLocation)}
     *
     * @param recipe 配方ID
     */
    public void setResearching(Player player, ResourceLocation recipe) {
        this.researchingRecipe = recipe;
        if (recipe != FabricatingRecipe.EMPTY) researchedRecipes.putIfAbsent(recipe, 0f);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ResearchSetPayload(recipe));
        }
    }

    public void clearResearching(Player player) {
        this.researchingRecipe = FabricatingRecipe.EMPTY;
        if (player instanceof ServerPlayer serverPlayer)
            PacketDistributor.sendToPlayer(serverPlayer, new ResearchCancelPayload());
    }

    public boolean hasStartedResearching(ResourceLocation recipe) {
        return getResearchedRecipes().getOrDefault(recipe, Float.MIN_VALUE) >= 0f;
    }

    /**
     * 获取当前正在研究的配方
     *
     * @return 当前正在研究的配方
     */
    @Nullable
    public RecipeHolder<FabricatingRecipe> getResearching() {
        if (researchingRecipe != FabricatingRecipe.EMPTY) {
            return MMDynamicRes.ALL_RECIPES.get(researchingRecipe);
        }
        return null;
    }

    /**
     * 获取所有可研发的配方
     *
     * @return 所有可研发的配方
     */
    public Map<ResourceLocation, RecipeHolder<FabricatingRecipe>> getAllResearchable() {
        return MMDynamicRes.ALL_RECIPES;
    }

    public int calculateRpToAdd(int basicRp) {
        if (researchingRecipe != FabricatingRecipe.EMPTY) {
            // 当前配方已研发进度
            float currentResearchProgress = researchedRecipes.computeIfAbsent(researchingRecipe, k -> Float.MIN_VALUE);
            // 当前研发等级
            int currentResearchLevel = (int) Math.floor(currentResearchProgress);
            return basicRp;
        } else return 0;
    }

    /**
     * 根据部件的研究进度和玩家的可用蓝图，计算装配速度增益
     *
     * @param part   要装配的部件
     * @param player 玩家
     * @return 装配速度增益
     */
    public float calculateAssemblyBuff(Part part, Player player) {
        float modifier = 0f;
        // 研发等级加成
        float researchProgress = getResearchedRecipes().get(part.getCustomRecipe());
        modifier += (float) (Math.floor(researchProgress) * ASSEMBLY_BUFF_PER_LEVEL);
        // 蓝图加成
        RecipeHolder<FabricatingRecipe> recipe = getAllResearchable().get(part.getCustomRecipe());
        if (recipe != null && getAvailablePartRecipeFor(player, part.type.getRegistryKey()).contains(recipe)) {
            modifier += ASSEMBLY_BUFF_WITH_BLUEPRINT;
        }
        return modifier;
    }

    /**
     * 根据部件的研究进度和玩家的可用蓝图，计算维修速度增益
     *
     * @param part   要装配的部件
     * @param player 玩家
     * @return 维修速度增益
     */
    public float calculateRepairBuff(Part part, Player player) {
        float modifier = 0f;
        // 研发等级加成
        float researchProgress = getResearchedRecipes().get(part.getCustomRecipe());
        modifier += (float) (Math.floor(researchProgress) * REPAIR_BUFF_PER_LEVEL);
        // 蓝图加成
        RecipeHolder<FabricatingRecipe> recipe = getAllResearchable().get(part.getCustomRecipe());
        if (recipe != null && getAvailablePartRecipeFor(player, part.type.getRegistryKey()).contains(recipe)) {
            modifier += REPAIR_BUFF_WITH_BLUEPRINT;
        }
        return modifier;
    }


    /**
     * 标记可用配方列表需要更新
     */
    public void markDirty() {
        dirty = true;
    }

    /**
     * 统计玩家库存，获取所有可用于制造指定部件的配方，不包括已研发但未持有的配方，创造模式无视库存直接展示所有配方
     *
     * @param player   玩家
     * @param partType 部件类型
     * @return 可用配方集合
     */
    public LinkedHashSet<RecipeHolder<FabricatingRecipe>> getAvailablePartRecipeFor(Player player, ResourceLocation partType) {
        return getAvailablePartRecipeFor(player, partType, false);
    }

    /**
     * 统计玩家库存，获取所有可用于制造指定部件的配方，创造模式无视库存直接展示所有配方
     *
     * @param player         玩家
     * @param partType       部件类型
     * @param withResearched 是否包含已研发但未持有的配方
     * @return 可用配方集合
     */
    public LinkedHashSet<RecipeHolder<FabricatingRecipe>> getAvailablePartRecipeFor(Player player, ResourceLocation partType, boolean withResearched) {
        if (isDirty() && !player.isCreative()) {
            rebuildAvailableRecipes(player);
        }
        if (!player.isCreative()) { // 非创造模式检查背包
            LinkedHashSet<RecipeHolder<FabricatingRecipe>> result = new LinkedHashSet<>(availableRecipes.get(partType));
            if (withResearched) {
                // 检查已研发但未持有的配方
                for (RecipeHolder<FabricatingRecipe> holder : MMDynamicRes.PART_RECIPES.get(partType)) {
                    if (result.contains(holder)) continue; // 已在可用列表中则跳过
                    ResourceLocation id = holder.id();
                    if (getResearchLevel(id) >= 1) {
                        result.add(holder);
                    }
                }
            }
            return result;
        } else return MMDynamicRes.PART_RECIPES.get(partType); // 创造模式直接返回所有配方
    }

    /**
     * 更新可用配方列表
     *
     * @param player 玩家
     */
    private void rebuildAvailableRecipes(Player player) {
        var research = player.getData(MMAttachments.getBLUEPRINT());
        research.inventoryHash = research.hashInventory(player);
        availableRecipes.clear();
        // 检查背包中的配方
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            checkAndRecord(inventory.items.get(i), player);
        }
        // 检查专用存储中的配方 TODO: 蓝图库检查
        checkAndRecord(product, player);
    }

    /**
     * 检查是否为部件蓝图，并将其记录于可用配方列表中
     * @param stack 物品
     * @param player 玩家，用于查询注册表
     */
    private void checkAndRecord(ItemStack stack, Player player) {
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
            var research = player.getData(MMAttachments.getBLUEPRINT());
            if (player.tickCount % 100 == 0) { // 定时更新可用配方列表
                if (research.hashInventory(player) != research.inventoryHash) {
                    research.rebuildAvailableRecipes(player);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        // 此事件仅在服务端被触发
        event.getEntity().getData(MMAttachments.getBLUEPRINT()).research(event.getEntity(), event.getAmount() * 10);
    }

    @SubscribeEvent
    public static void onContainerChanged(PlayerContainerEvent.Close event) {
        event.getEntity().getData(MMAttachments.getBLUEPRINT()).markDirty();
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        event.getEntity().getData(MMAttachments.getBLUEPRINT()).markDirty();
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        event.getPlayer().getData(MMAttachments.getBLUEPRINT()).markDirty();
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        event.getPlayer().getData(MMAttachments.getBLUEPRINT()).markDirty();
    }


}
