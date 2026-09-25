package io.github.sweetzonzi.machine_max.common.attachment;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.item.prop.PartFabricatingBlueprintItem;
import io.github.sweetzonzi.machine_max.common.recipe.BlueprintResearchRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.recipe.ResearchRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMAttachments;
import io.github.sweetzonzi.machine_max.common.registry.MMDataComponents;
import io.github.sweetzonzi.machine_max.common.registry.MMItems;
import io.github.sweetzonzi.machine_max.common.registry.MMTags;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.Part;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartDamageEvent;
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

/**
 * 玩家科研状态：研发点余额、已完成研发集合与可用零件配方缓存。
 *
 * <p>研发项目只登记完成态、不产出物品：完成研发消耗研发材料与研发点，制造蓝图由抄录动作
 * 按 {@code unlock_recipe} 现场产出并直接进入背包，每次抄录消耗一张空白蓝图。</p>
 *
 * <p>可用零件配方缓存由背包中的零件制造蓝图构建，供装配门禁与装配候选查询使用。</p>
 */
@EventBusSubscriber(modid = MachineMax.MOD_ID)
public class BlueprintAttachment {
    @Getter
    public int freeResearchPoint;
    private final List<Pair<RpAddReason, Integer>> rpChangeRecords = new ArrayList<>();
    @Getter
    public final Set<ResourceLocation> completedResearches;
    private final Map<ResourceLocation, RecipeHolder<PartFabricatingRecipe>> availableRecipes = new HashMap<>();
    @Getter
    private boolean dirty = true;
    private int inventoryHash = Integer.MIN_VALUE;
    private static final int HIT_RP_COOLDOWN = 5;
    private int hitRpCooldown = 0;

    public static final Codec<Set<ResourceLocation>> COMPLETED_RESEARCHES_CODEC = ResourceLocation.CODEC.listOf().xmap(HashSet::new, ArrayList::new);

    public static final Codec<BlueprintAttachment> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.INT.optionalFieldOf("research_point", 0).forGetter(BlueprintAttachment::getFreeResearchPoint),
                    COMPLETED_RESEARCHES_CODEC.optionalFieldOf("completed_researches", Set.of()).forGetter(BlueprintAttachment::getCompletedResearches)
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

    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintAttachment> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, BlueprintAttachment::getFreeResearchPoint,
            COMPLETED_RESEARCHES_STREAM_CODEC, BlueprintAttachment::getCompletedResearches,
            BlueprintAttachment::new
    );

    public BlueprintAttachment(int freeResearchPoint, Set<ResourceLocation> completedResearches) {
        this.freeResearchPoint = freeResearchPoint;
        this.completedResearches = new HashSet<>(completedResearches);
    }

    public BlueprintAttachment(int freeResearchPoint) {
        this(freeResearchPoint, new HashSet<>());
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

    /**
     * 完成一次研发：消耗研发材料与研发点，登记完成态。
     *
     * <p>本方法不产出任何物品；制造蓝图由 {@link #transcribe(Player, ResourceLocation)} 产出。
     * 完成载荷只携带 {@code unlock_recipe} 的产物，供客户端弹窗告知玩家学会了什么。</p>
     *
     * @param player         玩家
     * @param researchRecipe 研发配方ID
     * @return 完成时返回 true
     */
    public boolean completeResearch(Player player, ResourceLocation researchRecipe) {
        RecipeHolder<ResearchRecipe> holder = getResearch(researchRecipe);
        if (holder == null || !canCompleteResearch(player, researchRecipe)) {
            return false;
        }
        ResearchRecipe recipe = holder.value();
        recipe.consumeIngredients(player);
        setRp(player, freeResearchPoint - recipe.getResearchCost());
        completedResearches.add(researchRecipe);
        markDirty(player);

        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new ResearchCompletePayload(researchRecipe, getUnlockedProduct(player, researchRecipe)));
            rpChangeRecords.clear();
        }
        return true;
    }

    /**
     * 取蓝图研发项目 {@code unlock_recipe} 所指配方的产物物品。
     *
     * <p>仅用于完成提示与界面预览，不进入玩家背包。</p>
     *
     * @param player         玩家，用于取得注册表访问
     * @param researchRecipe 研发配方ID
     * @return 产物物品；非蓝图研发项目或解锁配方缺失时返回空堆
     */
    public ItemStack getUnlockedProduct(Player player, ResourceLocation researchRecipe) {
        RecipeHolder<BlueprintResearchRecipe> blueprintResearch = getBlueprintResearch(researchRecipe);
        if (blueprintResearch == null) return ItemStack.EMPTY;
        RecipeHolder<FabricatingRecipe> holder =
                MMDynamicRes.SERVER_ALL_FABRICATING_RECIPES.get(blueprintResearch.value().getUnlockRecipe());
        if (holder == null) return ItemStack.EMPTY;
        return holder.value().getResultItem(player.level().registryAccess());
    }

    /**
     * 获取指定蓝图研发配方的制造蓝图物品。
     *
     * <p>按 {@code unlock_recipe} 指向的配方实例类型分流：零件配方产出零件制造蓝图（同时写入
     * {@code recipe_type} 与 {@code part_type}），通用制造配方产出通用制造蓝图（只写 {@code recipe_type}）。</p>
     *
     * @param researchRecipe 研发配方ID（researchId）
     * @return 制造蓝图物品；研发配方不存在或解锁的配方缺失时返回空堆
     */
    public ItemStack createBlueprintProduct(ResourceLocation researchRecipe) {
        RecipeHolder<BlueprintResearchRecipe> blueprintResearch = getBlueprintResearch(researchRecipe);
        if (blueprintResearch == null) return ItemStack.EMPTY;
        RecipeHolder<FabricatingRecipe> holder =
                MMDynamicRes.SERVER_ALL_FABRICATING_RECIPES.get(blueprintResearch.value().getUnlockRecipe());
        if (holder == null) return ItemStack.EMPTY;
        if (holder.value() instanceof PartFabricatingRecipe partRecipe) {
            ItemStack stack = new ItemStack(MMItems.getPART_FABRICATING_BLUEPRINT());
            stack.set(MMDataComponents.getRECIPE_TYPE(), holder.id());
            if (partRecipe.getPartType() != null) {
                stack.set(MMDataComponents.getPART_TYPE(), partRecipe.getPartType());
            }
            return stack;
        }
        ItemStack stack = new ItemStack(MMItems.getFABRICATING_BLUEPRINT());
        stack.set(MMDataComponents.getRECIPE_TYPE(), holder.id());
        return stack;
    }

    /**
     * 检查是否满足抄录条件：该条目是蓝图研发项目、已完成研发，且背包中有空白蓝图。
     *
     * <p>抄录的可重复性不受手中与背包中已有蓝图数量影响——每次抄录都单独消耗一张空白蓝图。</p>
     *
     * @param player         玩家
     * @param researchRecipe 研发配方ID
     * @return 可抄录时返回 true
     */
    public boolean canTranscribe(Player player, ResourceLocation researchRecipe) {
        if (getBlueprintResearch(researchRecipe) == null) {
            return false;
        }
        if (!isResearched(researchRecipe)) {
            return false;
        }
        return player.isCreative() || countEmptyBlueprints(player) > 0;
    }

    /**
     * 抄录：消耗一张空白蓝图，把该条目对应的制造蓝图直接放入玩家背包。
     *
     * @param player         玩家
     * @param researchRecipe 研发配方ID
     */
    public void transcribe(Player player, ResourceLocation researchRecipe) {
        if (!canTranscribe(player, researchRecipe)) return;
        ItemStack blueprint = createBlueprintProduct(researchRecipe);
        if (blueprint.isEmpty()) return;
        if (!player.isCreative() && !consumeEmptyBlueprint(player)) return;
        if (!player.getInventory().add(blueprint)) {
            player.drop(blueprint, false);
        }
        markDirty(player);
    }

    /** 统计背包中空白蓝图的总数量 */
    public static int countEmptyBlueprints(Player player) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(MMTags.EMPTY_BLUEPRINT)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 从背包中扣除一张空白蓝图；扣不到时返回 false */
    private static boolean consumeEmptyBlueprint(Player player) {
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(MMTags.EMPTY_BLUEPRINT)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
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
        this.freeResearchPoint = Math.max(0, freeResearchPoint);
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
     * 判断玩家是否有资格推进指定部件的装配进度（研发门禁）。
     *
     * <p>判定顺序：</p>
     * <ol>
     *   <li>创造模式直接放行；</li>
     *   <li>无零件配方（{@code getRecipe() == null}）放行——否则这类零件永远无法装配；</li>
     *   <li>该零件对应的研究条目已完成；</li>
     *   <li>背包中持有对应的零件制造蓝图（复用 {@code availableRecipes} 缓存，不每 tick 扫描背包）。</li>
     * </ol>
     *
     * <p>没有研究条目、或研究条目不可达的零件，第三条恒为假，判定落到"持有制造蓝图"上，
     * 因此不为这些情形开设放行分支。</p>
     *
     * @param player 玩家
     * @param part   目标部件
     * @return 允许推进时返回 true
     */
    public boolean canAdvanceAssembly(Player player, Part part) {
        if (player.isCreative()) return true;
        // 无零件配方零件走 assemble 的慢速兜底分支，必须放行
        if (part.getRecipe() == null) return true;
        ResourceLocation researchId = MMDynamicRes.SERVER_RESEARCH_BY_PART.get(part.getType().getRegistryKey());
        if (researchId != null && completedResearches.contains(researchId)) return true;
        // 持有对应零件制造蓝图即可装配；缓存由 EntityTickEvent 定时刷新
        if (isDirty()) rebuildAvailableRecipes(player);
        return availableRecipes.containsKey(part.getType().getRegistryKey());
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

    public void markDirty(Player player) {
        dirty = true;
        player.setData(MMAttachments.getBLUEPRINT(), this);
    }

    /**
     * 获取指定零件的装配候选配方：玩家持有该零件的零件制造蓝图即为可用，创造模式直接取本侧索引。
     *
     * @param player   玩家
     * @param partType 零件 id
     * @return 该零件的零件配方；玩家尚未持有对应蓝图（或该零件没有零件配方）时返回 {@code null}
     */
    @Nullable
    public RecipeHolder<PartFabricatingRecipe> getAvailablePartRecipeFor(Player player, ResourceLocation partType) {
        if (player.isCreative()) {
            return MMDynamicRes.getPartRecipe(player.level(), partType);
        }
        if (isDirty()) rebuildAvailableRecipes(player); // 刷新可用配方列表
        return availableRecipes.get(partType);
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
        // TODO: 蓝图库检查——计划中的蓝图收纳道具（统一存放玩家的制造蓝图，避免背包被蓝图塞满），
        //  实现后需在此扫描该道具内保存的附件信息并一并录入可用配方
    }

    /**
     * 检查是否为零件制造蓝图，并将其记录于可用配方列表中。
     *
     * <p>索引键取物品的 {@code machine_max:part_type} 组件，与配方实例的 {@code partType} 同值；
     * 通用制造蓝图不进入装配候选。</p>
     *
     * @param stack  物品
     * @param player 玩家，用于选定逻辑侧索引
     */
    private void checkAndRecord(ItemStack stack, Player player) {
        if (!(stack.getItem() instanceof PartFabricatingBlueprintItem)) return;
        ResourceLocation partType = stack.get(MMDataComponents.getPART_TYPE());
        if (partType == null) return;
        RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(player.level(), partType);
        if (holder != null) availableRecipes.put(partType, holder);
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
        if (event.getCtx().source().getEntity() instanceof ServerPlayer player) {
            var research = player.getData(MMAttachments.getBLUEPRINT());
            if (research.hitRpCooldown <= 0) {
                research.givRp(player, 1, RpAddReason.HIT);
                research.hitRpCooldown = BlueprintAttachment.HIT_RP_COOLDOWN;
            }
        }
    }

    @SubscribeEvent
    public static void onSubPartDamage(SubPartDamageEvent.Post event) {
        if (event.getCtx().source().getEntity() instanceof ServerPlayer player) {
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
