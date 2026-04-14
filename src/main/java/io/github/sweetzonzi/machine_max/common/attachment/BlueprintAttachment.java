package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.FabricatingBlueprintItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartAssemblyItem;
import io.github.sweetzonzi.machine_max.common.item.prop.PartItem;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.vehicle.event.subpart.SubPartDamageEvent;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.research.*;
import io.github.sweetzonzi.machine_max.util.data.RpAddReason;
import lombok.Getter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class BlueprintAttachment {
    @Getter
    public int freeResearchPoint;
    private final List<Pair<RpAddReason, Integer>> rpChangeRecords = new ArrayList<>();
    @Getter
    public final Set<ResourceLocation> completedResearches;
    private final Map<ResourceLocation, LinkedHashSet<RecipeHolder<FabricatingRecipe>>> availableRecipes = new HashMap<>();
    @Getter
    // 研发产物缓存：key 永远是 researchId，value 是可领取的制造蓝图物品（其内部 RECIPE_TYPE 才是制造配方ID）
    public final Map<ResourceLocation, ItemStack> products;
    @Getter
    private boolean dirty = true;
    private int inventoryHash = Integer.MIN_VALUE;
    private static final int HIT_RP_COOLDOWN = 5;
    private int hitRpCooldown = 0;

    public static final Codec<Set<ResourceLocation>> COMPLETED_RESEARCHES_CODEC = ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new);
    public static final Codec<Map<ResourceLocation, ItemStack>> PRODUCTS_CODEC = Codec.unboundedMap(ResourceLocation.CODEC, ItemStack.CODEC);

    public static final Codec<BlueprintAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("research_point", 0).forGetter(BlueprintAttachment::getFreeResearchPoint),
                    COMPLETED_RESEARCHES_CODEC.optionalFieldOf("completed_researches", Set.of()).forGetter(BlueprintAttachment::getCompletedResearches),
                    PRODUCTS_CODEC.fieldOf("products").forGetter(BlueprintAttachment::getProducts)
            ).apply(instance, BlueprintAttachment::new)
    );

    public static final StreamCodec<FriendlyByteBuf, Pair<RpAddReason, Integer>> RP_CHANGE_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull Pair<RpAddReason, Integer> decode(@NotNull FriendlyByteBuf buffer) {
            return new Pair<>(RpAddReason.STREAM_CODEC.decode(buffer), buffer.readInt());
        }

        @Override
        public void encode(@NotNull FriendlyByteBuf buffer, Pair<RpAddReason, Integer> pair) {
            RpAddReason.STREAM_CODEC.encode(buffer, pair.getFirst());
            buffer.writeInt(pair.getSecond());
        }
    };

    public static final StreamCodec<FriendlyByteBuf, List<Pair<RpAddReason, Integer>>> RP_CHANGE_LIST_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull List<Pair<RpAddReason, Integer>> decode(FriendlyByteBuf buffer) {
            int size = buffer.readInt();
            List<Pair<RpAddReason, Integer>> pairs = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                pairs.add(RP_CHANGE_STREAM_CODEC.decode(buffer));
            }
            return pairs;
        }

        @Override
        public void encode(FriendlyByteBuf buffer, List<Pair<RpAddReason, Integer>> pairs) {
            buffer.writeInt(pairs.size());
            for (Pair<RpAddReason, Integer> pair : pairs) {
                RP_CHANGE_STREAM_CODEC.encode(buffer, pair);
            }
        }
    };

    public static final StreamCodec<FriendlyByteBuf, Set<ResourceLocation>> COMPLETED_RESEARCHES_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull Set<ResourceLocation> decode(FriendlyByteBuf buffer) {
            int size = buffer.readInt();
            Set<ResourceLocation> completed = new HashSet<>(size);
            for (int i = 0; i < size; i++) {
                completed.add(ResourceLocation.STREAM_CODEC.decode(buffer));
            }
            return completed;
        }

        @Override
        public void encode(FriendlyByteBuf buffer, Set<ResourceLocation> completed) {
            buffer.writeInt(completed.size());
            for (ResourceLocation id : completed) {
                ResourceLocation.STREAM_CODEC.encode(buffer, id);
            }
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, Map<ResourceLocation, ItemStack>> PRODUCTS_STREAM_CODEC = new StreamCodec<>() {
        @Override
        public @NotNull Map<ResourceLocation, ItemStack> decode(RegistryFriendlyByteBuf buffer) {
            int size = buffer.readInt();
            Map<ResourceLocation, ItemStack> products = new LinkedHashMap<>(size);

            for (int i = 0; i < size; i++) {
                ResourceLocation key = ResourceLocation.STREAM_CODEC.decode(buffer);
                ItemStack value = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                products.put(key, value);
            }
            return products;
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, Map<ResourceLocation, ItemStack> products) {
            buffer.writeInt(products.size());
            products.forEach((key, value) -> {
                ResourceLocation.STREAM_CODEC.encode(buffer, key);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, value);
            });
        }
    };

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintAttachment> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, BlueprintAttachment::getFreeResearchPoint,
            COMPLETED_RESEARCHES_STREAM_CODEC, BlueprintAttachment::getCompletedResearches,
            PRODUCTS_STREAM_CODEC, BlueprintAttachment::getProducts,
            BlueprintAttachment::new
    );

    public BlueprintAttachment(int freeResearchPoint, Set<ResourceLocation> completedResearches, Map<ResourceLocation, ItemStack> products) {
        this.freeResearchPoint = freeResearchPoint;
        this.completedResearches = new HashSet<>(completedResearches);
        this.products = new HashMap<>(products);
    }

    public BlueprintAttachment(int freeResearchPoint) {
        this(freeResearchPoint, new HashSet<>(), new HashMap<>());
    }

    /**
     * 检查指定研发配方的前置研究是否已完成
     *
     * @param researchRecipe 研发配方ID
     * @return 是否已满足前置研究条件
     */
    public boolean hasSatisfiedPrerequisites(Player player, ResourceLocation researchRecipe) {
        if (player.isCreative()) return true;
        RecipeHolder<ResearchRecipe> holder = getResearch(researchRecipe);
        if (holder == null) {
            return false;
        }
        return holder.value().isCompletedBy(completedResearches);
    }

    public boolean canCompleteResearch(Player player, ResourceLocation researchRecipe) {
        RecipeHolder<ResearchRecipe> holder = getResearch(researchRecipe);
        if (holder == null) {
            return false;
        }
        if (isResearched(researchRecipe)) {
            return false;
        }
        if (player.isCreative()) return true;
        ResearchRecipe recipe = holder.value();
        return recipe.isCompletedBy(completedResearches)
                && recipe.hasRequiredIngredients(player)
                && freeResearchPoint >= recipe.getResearchCost();
    }

    public List<ResourceLocation> getMissingPrerequisites(ResourceLocation researchRecipe) {
        RecipeHolder<ResearchRecipe> holder = getResearch(researchRecipe);
        if (holder == null) {
            return List.of();
        }
        List<ResourceLocation> missing = new ArrayList<>();
        for (ResourceLocation prerequisite : holder.value().getPrerequisites()) {
            if (!completedResearches.contains(prerequisite)) {
                missing.add(prerequisite);
            }
        }
        return missing;
    }

    public boolean completeResearch(Player player, ResourceLocation researchRecipe) {
        RecipeHolder<ResearchRecipe> holder = getResearch(researchRecipe);
        if (holder == null || !canCompleteResearch(player, researchRecipe)) {
            return false;
        }
        ResourceLocation researchId = holder.id();
        ResearchRecipe recipe = holder.value();
        recipe.consumeIngredients(player);
        setRp(player, freeResearchPoint - recipe.getResearchCost());
        completedResearches.add(researchRecipe);
        markDirty(player);

        ItemStack product = ItemStack.EMPTY;
        if (holder instanceof RecipeHolder<?> rawHolder && rawHolder.value() instanceof BlueprintResearchRecipe blueprintResearch) {
            product = createBlueprintProduct(researchId);
            products.put(researchId, product);
        }

        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new ResearchCompletePayload(researchRecipe, product));
            rpChangeRecords.clear();
        }
        return true;
    }

    /**
     * 获取指定蓝图研发配方的产物蓝图
     *
     * @param researchRecipe 研发配方ID（researchId）
     * @return 制造蓝图（蓝图内部携带 unlockRecipe/fabricatingRecipeId）
     */
    public ItemStack createBlueprintProduct(ResourceLocation researchRecipe) {
        ItemStack stack = ItemStack.EMPTY;
        if (getBlueprintResearchResult(researchRecipe) instanceof RecipeHolder<FabricatingRecipe> fabricatingRecipe) {
            stack = new ItemStack(MMItems.getFABRICATING_BLUEPRINT());
            stack.set(MMDataComponents.getRECIPE_TYPE(), fabricatingRecipe.id());
            return stack;
        }
        return stack;
    }

    /**
     * 检查是否满足重新获取蓝图的条件
     *
     * @param researchRecipe 研发配方ID
     * @return 是否可获取
     */
    public boolean canReclaim(ResourceLocation researchRecipe) {
        RecipeHolder<BlueprintResearchRecipe> blueprintResearch = getBlueprintResearch(researchRecipe);
        if (blueprintResearch == null) {
            return false;
        }
        return isResearched(blueprintResearch.id()) && products.getOrDefault(researchRecipe, ItemStack.EMPTY) == ItemStack.EMPTY;
    }


    /**
     * 重新获取某个已经研发过的蓝图，存入产物缓存
     *
     * @param player         玩家
     * @param researchRecipe 研发配方ID
     */
    public void reclaim(Player player, ResourceLocation researchRecipe) {
        if (canReclaim(researchRecipe)) {
            products.put(researchRecipe, createBlueprintProduct(researchRecipe));
            this.markDirty(player);
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new ResearchProductSyncPayload(products));
            }
        }
    }

    /**
     * 获取指定研发配方的蓝图物品，需要先完成研发
     *
     * @param player         玩家
     * @param researchRecipe 研发配方ID
     */
    public void claim(Player player, ResourceLocation researchRecipe) {
        ItemStack product = getProducts().getOrDefault(researchRecipe, ItemStack.EMPTY);
        if (product != ItemStack.EMPTY) {
            boolean success = player.getInventory().add(product); // 首先尝试放入背包
            Entity itemEntity = product.getEntityRepresentation();
            if (!success && itemEntity != null) { // 未成功放入背包则掉落为物品
                itemEntity.setPos(player.getPosition(1));
                player.level().addFreshEntity(itemEntity);
            }
            getProducts().remove(researchRecipe); // 清空暂存
            this.markDirty(player);
            if (player instanceof ServerPlayer serverPlayer)
                PacketDistributor.sendToPlayer(serverPlayer, new ResearchProductSyncPayload(getProducts()));
        }
    }

    public void givRp(Player player, int rp, RpAddReason reason) {
        if (rp > 0) {
            this.rpChangeRecords.add(Pair.of(reason, rp));
            setRp(player, this.freeResearchPoint + rp);
        }
    }

    public static void giveRp(Player player, int rp, RpAddReason reason) {
        var research = player.getData(MMAttachments.getBLUEPRINT());
        research.givRp(player, rp, reason);
    }

    public void setRp(Player player, int freeResearchPoint) {
        this.freeResearchPoint = freeResearchPoint;
        this.markDirty(player);
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new FreeRpSyncPayload(freeResearchPoint, new ArrayList<>(this.rpChangeRecords)));
            this.rpChangeRecords.clear();
        }
    }

    /**
     * 获取所有可研发的配方
     *
     * @return 所有可研发的配方
     */
    public Map<ResourceLocation, RecipeHolder<ResearchRecipe>> getAllResearchable() {
        return MMDynamicRes.ALL_RESEARCH_RECIPES;
    }

    /**
     * 检查指定研发配方是否已研究
     *
     * @param researchRecipe 研发配方
     * @return 是否已研究
     */
    public boolean isResearched(ResourceLocation researchRecipe) {
        return completedResearches.contains(researchRecipe);
    }

    /**
     * 获取指定研发配方的ID与对象
     *
     * @param researchRecipe 研发配方
     * @return 研发配方ID与对象容器
     */
    @Nullable
    public RecipeHolder<ResearchRecipe> getResearch(ResourceLocation researchRecipe) {
        return MMDynamicRes.ALL_RESEARCH_RECIPES.get(researchRecipe);
    }

    /**
     * 获取指定研发配方的蓝图配方ID与对象
     *
     * @param researchRecipe 研发配方
     * @return 研发配方蓝图ID与对象容器
     */
    @Nullable
    public RecipeHolder<BlueprintResearchRecipe> getBlueprintResearch(ResourceLocation researchRecipe) {
        return MMDynamicRes.BLUEPRINT_RESEARCH_RECIPES.get(researchRecipe);
    }

    /**
     * 获取蓝图研发配方解锁的制造配方ID与对象
     *
     * @param researchRecipe 研发配方
     * @return 制造配方ID与对象容器
     */
    @Nullable
    public RecipeHolder<FabricatingRecipe> getBlueprintResearchResult(ResourceLocation researchRecipe) {
        RecipeHolder<BlueprintResearchRecipe> blueprintResearch = getBlueprintResearch(researchRecipe);
        if (blueprintResearch == null) {
            return null;
        }
        return MMDynamicRes.ALL_FABRICATING_RECIPES.get(blueprintResearch.value().getUnlockRecipe());
    }

    public void markDirty(Player player) {
        dirty = true;
        player.setData(MMAttachments.getBLUEPRINT(), this);
    }

    public boolean canAssemble(Player player, Part part) {
        if (player.isCreative()) return true; // 创造模式直接返回true
        var recipe = part.getRecipe();
        for (RecipeHolder<FabricatingRecipe> holder : getAvailablePartRecipeFor(player, part.type.getRegistryKey())) {
            if (holder.value().equals(recipe)) return true;
        }
        return false;
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

    private static final LinkedHashSet<RecipeHolder<FabricatingRecipe>> EMPTY_SET = new LinkedHashSet<>(1);

    /**
     * 统计玩家库存，获取所有可用于制造指定部件的配方，创造模式无视库存直接展示所有配方
     *
     * @param player         玩家
     * @param partType       部件类型
     * @param withResearched 是否包含已研发但未持有的配方
     * @return 可用配方集合
     */
    public LinkedHashSet<RecipeHolder<FabricatingRecipe>> getAvailablePartRecipeFor(Player player, ResourceLocation partType, boolean withResearched) {
        if (!player.isCreative()) { // 非创造模式检查背包
            if (isDirty()) rebuildAvailableRecipes(player); // 刷新可用配方列表
            LinkedHashSet<RecipeHolder<FabricatingRecipe>> result = new LinkedHashSet<>();
            if (availableRecipes.containsKey(partType)) result.addAll(availableRecipes.get(partType));
            if (withResearched) {
                // 检查已研发但未持有的配方
                for (RecipeHolder<FabricatingRecipe> holder : MMDynamicRes.PART_RECIPES.get(partType)) {
                    if (result.contains(holder)) continue; // 已在可用列表中则跳过
                    result.add(holder);
                }
            }
            return result;
        } else return MMDynamicRes.PART_RECIPES.getOrDefault(partType, EMPTY_SET); // 创造模式直接返回所有配方
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
        for (ItemStack product : research.products.values())
            checkAndRecord(product, player);
    }

    /**
     * 检查是否为部件蓝图，并将其记录于可用配方列表中
     *
     * @param stack  物品
     * @param player 玩家，用于查询注册表
     */
    @SuppressWarnings("unchecked")
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
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new ResearchAttachmentSyncPayload(player.getData(MMAttachments.getBLUEPRINT())));
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof Player player) {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            if (research.hitRpCooldown > 0) research.hitRpCooldown--;
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
        BlueprintAttachment.giveRp(event.getEntity(), event.getAmount() * 10, RpAddReason.EXP);
    }

    @SubscribeEvent
    public static void onSubPartHit(SubPartDamageEvent.Pre event) {
        if (event.getData().source().getEntity() instanceof ServerPlayer player) {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            if (research.hitRpCooldown <= 0) {
                research.givRp(player, 1, RpAddReason.HIT);
                research.hitRpCooldown = BlueprintAttachment.HIT_RP_COOLDOWN;
            }
        }
    }

    @SubscribeEvent
    public static void onSubPartDamage(SubPartDamageEvent.Post event) {
        if (event.getData().source().getEntity() instanceof ServerPlayer player) {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            research.givRp(player, (int) event.getDamageAmount(), RpAddReason.PART_DAMAGE);
        }
    }

    @SubscribeEvent
    public static void onContainerChanged(PlayerContainerEvent.Close event) {
        event.getEntity().getData(MMAttachments.getBLUEPRINT()).markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        event.getEntity().getData(MMAttachments.getBLUEPRINT()).markDirty(event.getEntity());
    }

    @SubscribeEvent
    public static void onItemPickup(ItemEntityPickupEvent.Post event) {
        event.getPlayer().getData(MMAttachments.getBLUEPRINT()).markDirty(event.getPlayer());
    }

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        event.getPlayer().getData(MMAttachments.getBLUEPRINT()).markDirty(event.getPlayer());
    }
}
