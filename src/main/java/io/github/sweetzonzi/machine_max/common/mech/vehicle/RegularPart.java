package io.github.sweetzonzi.machine_max.common.mech.vehicle;

import cn.solarmoon.spark_core.api.SparkLevel;
import cn.solarmoon.spark_core.util.PPhase;
import io.github.sweetzonzi.machine_max.common.recipe.PartFabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.data.RegularPartData;
import io.github.sweetzonzi.machine_max.external.MMDynamicRes;
import io.github.sweetzonzi.machine_max.network.payload.assembly.PartAssemblyProgressSyncPayload;
import lombok.Getter;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

import io.github.sweetzonzi.machine_max.common.mech.part.*;

@Getter
public final class RegularPart extends AbstractPart<RegularPartType> {
    public volatile float assemblingProgress = 1f;
    public int materialProgress = Integer.MAX_VALUE;
    public boolean renderWireframe = true;

    public RegularPart(RegularPartType type, String variant, Level level, PartCreationContext context) {
        super(type, variant, level, UUID.randomUUID(), type.getRegistryKey().toLanguageKey(), context);
        initializeStructure();
    }

    public RegularPart(RegularPartData data, RegularPartType type, Level level,
                       PartRestoreMode mode, PartCreationContext context) {
        super(type, data.variant, level, UUID.fromString(data.uuid), data.name, context);
        renderWireframe = mode == PartRestoreMode.STRUCTURE_ONLY || data.renderWireframe;
        setMaterialProgress(mode == PartRestoreMode.FULL_STATE ? data.materialAssemblingProgress : 0);
        assemblingProgress = mode == PartRestoreMode.FULL_STATE ? Math.clamp(data.assemblingProgress, 0f, 1f) : 0f;
        initializeStructure();
        try {
            restoreState(data, mode);
            if (assemblingProgress == 0f && renderWireframe) {
                for (SubPart child : subParts.values()) {
                    child.body.setGravity(SparkLevel.getPhysicsLevel(level).getWorld().getGravity(null).mult(0.1f));
                }
            }
        } catch (RuntimeException | Error failure) {
            abortConstruction();
            throw failure;
        }
    }

    @Override public RegularPartData save() { return new RegularPartData(this); }
    @Override public boolean permitsSubsystemWork() { return getAssemblingProgress() >= type.functionalThreshold; }
    @Override public float durabilityLimitRatio() { return Math.max(getAssemblingProgress(), 0.05f); }
    @Override public boolean permitsRepair() { return true; }
    @Override public boolean usesFullGravity() { return getAssemblingProgress() >= 1f; }
    @Override public float repairLimitRatio() {
        PartFabricatingRecipe recipe = getRecipe();
        int count = recipe == null ? 0 : recipe.getManualAssembleIngredientList().size();
        return count > 0 ? Math.clamp((float) materialProgress / count, 0f, 1f) : 1f;
    }
    @Override public float assemblyDamage(float amount, float remaining, boolean destroyed) {
        if (destroyed) return amount * type.vehicleDamageRateDestroyed;
        float absorbed = Math.min(amount, Math.max(remaining, 0f));
        return absorbed * type.vehicleDamageRate + (amount - absorbed) * type.vehicleDamageRateDestroyed;
    }
    @Override public void onDurabilityChanged() { recomputeAssemblyFromDurability(); }
    @Override public void onRepairAttempt() { setRenderWireframe(true); }
    @Override public void onTick() {
        if (renderWireframe && getAssemblingProgress() > type.functionalThreshold) setRenderWireframe(false);
        super.onTick();
    }

    /**
     * <p>每被调用一次，尝试增加组装进度，并消耗材料</p>
     * <p>Attempts to increase assembling progress, and consumes materials.</p>
     *
     * @param container 消耗材料的容器 Material container
     * @param progress  增加的进度 (tick) Progress to be increased
     * @return 是否成功改变进度 Whether the progress is successfully changed
     */
    public boolean assemble(Container container, float progress) {
        boolean ignoreMaterial = level.isClientSide();
        if (!level.isClientSide() && container instanceof Inventory inventory) {
            ignoreMaterial = inventory.player.hasInfiniteMaterials();
        }
        PartFabricatingRecipe recipe = getRecipe();
        // 未找到零件配方则每progress对应0.01组装进度
        if (recipe != null) {
            int totalTime = recipe.getProcessingTime();
            float step = progress / totalTime;
            float newProgress = Math.clamp(assemblingProgress + step, 0f, 1f);

            // 计算新的组装进度对应的材料需求
            List<Ingredient> manualAssembleList = recipe.getManualAssembleIngredientList();
            int totalMaterials = manualAssembleList.size(); // 手动组装总材料数量（ceil折算后）
            if (totalMaterials <= 0) {
                if (newProgress != assemblingProgress) {
                    setAssemblingProgress(newProgress);
                    return true;
                }
                return false;
            }
            materialProgress = Math.clamp(materialProgress, 0, totalMaterials);
            int targetMaterialProgress = (int) Math.ceil(newProgress * totalMaterials);

            // 尝试提升材料进度
            if (targetMaterialProgress > materialProgress) {
                if (ignoreMaterial) { // 创造模式无视材料需求
                    materialProgress = Math.clamp(targetMaterialProgress, 0, totalMaterials);
                } else { // 检查材料是否足够，如果不够则组装进度最多提升至材料供给进度的值
                    for (int i = materialProgress; i < targetMaterialProgress; i++) {
                        // 获取下一个需要消耗的材料
                        if (i < totalMaterials) {
                            Ingredient requiredIngredient = manualAssembleList.get(i);

                            // 在容器中查找匹配的物品
                            boolean found = false;
                            for (int slot = 0; slot < container.getContainerSize(); slot++) {
                                ItemStack stack = container.getItem(slot);
                                if (!stack.isEmpty() && requiredIngredient.test(stack)) {
                                    // 消耗一个物品
                                    stack.shrink(1);
                                    if (stack.isEmpty()) {
                                        container.setItem(slot, ItemStack.EMPTY);
                                    }
                                    materialProgress++;
                                    found = true;
                                    break;
                                }
                            }
                            // 如果某个材料不足，停止组装
                            if (!found) {
                                break;
                            }
                        }
                    }
                }
            }

            // Clamp进度并设置
            newProgress = Math.min(newProgress, (float) materialProgress / totalMaterials);
            if (newProgress != assemblingProgress) {
                setAssemblingProgress(newProgress);
                return true;
            }
        } else if (recipe == null && getAssemblingProgress() < 1f) {
            // 无制造配方（无材料需求）的零件直接涨进度
            setAssemblingProgress(getAssemblingProgress() + progress * 0.01f);
            return true;
        }
        return false;
    }

    /**
     * <p>每被调用一次，尝试降低组装进度，并返还材料</p>
     * <p>Attempts to decrease assembling progress, and returns materials.</p>
     *
     * @param container 返还材料的容器 Material container
     * @param progress  降低的进度 Progress to be decreased
     * @return 是否成功改变进度 Whether the progress is successfully changed
     */
    public boolean disassemble(Container container, float progress) {
        progress = Math.max(progress, 0f);
        boolean ignoreMaterial = level.isClientSide();
        if (!level.isClientSide() && container instanceof Inventory inventory) {
            ignoreMaterial = inventory.player.hasInfiniteMaterials();
        }
        PartFabricatingRecipe recipe = getRecipe();
        // 未找到零件配方则不改变组装进度
        if (recipe != null) {
            int totalTime = recipe.getProcessingTime();
            float step = progress / totalTime;
            float newProgress = Math.clamp(assemblingProgress - step, 0f, 1f);

            // 计算新的组装进度对应的材料需求
            List<Ingredient> manualDisassembleList = recipe.getManualDisassembleIngredientList();
            int totalMaterials = manualDisassembleList.size(); // 手动拆除总返还材料数量（floor折算后）
            if (totalMaterials <= 0) {
                materialProgress = 0;
                if (newProgress != assemblingProgress) {
                    setAssemblingProgress(newProgress);
                    return true;
                }
                return false;
            }
            materialProgress = Math.clamp(materialProgress, 0, totalMaterials);
            int targetMaterialProgress = (int) Math.floor(newProgress * totalMaterials);

            // 检查是否需要返还材料
            if (targetMaterialProgress < materialProgress) {
                if (ignoreMaterial) { // 创造模式不返还材料
                    materialProgress = Math.clamp(targetMaterialProgress, 0, totalMaterials);
                } else {
                    materialProgress = Math.clamp(materialProgress, 0, totalMaterials);
                    int materialsToReturn = materialProgress - targetMaterialProgress;
                    // 从后往前返还材料（后消耗的先返还）
                    for (int i = 0; i < materialsToReturn; i++) {
                        if (materialProgress > 0) {
                            materialProgress--;
                            Ingredient ingredientToReturn = manualDisassembleList.get(materialProgress);

                            // 创建要返还的物品（取第一个匹配项）
                            ItemStack[] matchingStacks = ingredientToReturn.getItems();
                            if (matchingStacks.length > 0) {
                                ItemStack returnStack = matchingStacks[0].copy();
                                returnStack.setCount(1);

                                // 尝试放入容器
                                boolean added = false;
                                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                                    ItemStack stack = container.getItem(slot);
                                    if (stack.isEmpty()) {
                                        container.setItem(slot, returnStack);
                                        added = true;
                                        break;
                                    } else if (ItemStack.isSameItemSameComponents(stack, returnStack) &&
                                            stack.getCount() < stack.getMaxStackSize()) {
                                        stack.grow(1);
                                        added = true;
                                        break;
                                    }
                                }

                                // 容器已满，掉落物品
                                if (!added && !level.isClientSide()) {
                                    // 在部件位置掉落物品
                                    net.minecraft.world.entity.item.ItemEntity itemEntity =
                                            new net.minecraft.world.entity.item.ItemEntity(
                                                    level,
                                                    rootSubPart.getPosition().x,
                                                    rootSubPart.getPosition().y,
                                                    rootSubPart.getPosition().z,
                                                    returnStack
                                            );
                                    level.addFreshEntity(itemEntity);
                                }
                            }
                        }
                    }
                }
            }
            // Clamp进度并设置
            newProgress = Math.max(newProgress, 0f);
            if (newProgress != assemblingProgress) {
                setAssemblingProgress(newProgress);
                return true;
            }
        }
        return false;
    }

    public PartFabricatingRecipe getRecipe() {
        RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(level, type.getRegistryKey());
        return holder == null ? null : holder.value();
    }

    /**
     * <p>设置部件的材料进度，用于计算组装状态，不存在配方时无效</p>
     *
     * @param progress 材料进度，0~材料总量
     */
    public void setMaterialProgress(int progress) {
        PartFabricatingRecipe recipe = getRecipe();
        if (recipe != null) {
            materialProgress = Math.clamp(progress, 0, recipe.getManualAssembleIngredientList().size());
        } else materialProgress = Math.max(0, progress);
    }

    /**
     * <p>获取部件组装进度</p>
     *
     * @return 组装进度，0~1f
     */
    public float getAssemblingProgress() {
        return Math.clamp(assemblingProgress, 0f, 1f);
    }

    /**
     * <p>根据子部件耐久度重新计算部件组装进度和材料进度上限。</p>
     * <p>有手动配方时，材料进度仅会被下调；无手动配方时，不限制材料进度。</p>
     *
     * @return 是否发生变化
     */
    public boolean recomputeAssemblyFromDurability() {
        if (level.isClientSide()) return false;
        float oldProgress = this.assemblingProgress;
        int oldMaterialProgress = this.materialProgress;

        float durabilityRatio = 0f;
        for (SubPart subPart : subParts.values()) {
            float maxDurability = subPart.getMaxDurability();
            if (maxDurability <= 0f) continue;
            float ratio = Math.clamp(subPart.getDurability() / maxDurability, 0f, 1f);
            durabilityRatio = Math.max(durabilityRatio, ratio);
        }

        PartFabricatingRecipe recipe = getRecipe();
        int totalMaterials = recipe != null ? recipe.getManualAssembleIngredientList().size() : 0;

        if (recipe == null || totalMaterials == 0) {
            setAssemblingProgress(durabilityRatio);
        } else {
            materialProgress = Math.clamp(materialProgress, 0, totalMaterials);
            int durabilityTier = getMaterialTierByDurabilityRatio(durabilityRatio, totalMaterials);
            materialProgress = Math.min(materialProgress, durabilityTier);
            float materialCap = (float) materialProgress / totalMaterials;
            setAssemblingProgress(Math.min(durabilityRatio, materialCap));
        }

        if (oldMaterialProgress != materialProgress && oldProgress == this.assemblingProgress) {
            syncAssemblyProgressToClient();
        }
        return oldProgress != this.assemblingProgress || oldMaterialProgress != this.materialProgress;
    }

    private static int getMaterialTierByDurabilityRatio(float ratio, int totalMaterials) {
        if (totalMaterials <= 0) return 0;
        if (ratio <= 0f) return 0;
        return Math.min(totalMaterials, (int) Math.floor(Math.clamp(ratio, 0f, 1f) * totalMaterials) + 1);
    }

    /**
     * <p>设置部件的组装进度，影响最大耐久和重力，不改变刚体质量</p>
     * <p>进度为0时重力降为正常的10%；进度>0时重力恢复正常并激活刚体。</p>
     * <p>进度达到{@link RegularPartType#functionalThreshold}时自动关闭线框渲染。</p>
     * <p>Sets the assembling progress of the part, which affects the maximum durability and gravity, but does not change the rigid body mass.</p>
     * <p>When progress is 0, gravity is reduced to 10% of normal; when progress > 0, gravity is restored and the body is activated.</p>
     *
     * @param progress 组装进度，0~1
     */
    public void setAssemblingProgress(float progress) {
        progress = Math.clamp(progress, 0f, 1f);
        if (progress != this.assemblingProgress) {
            float oldProgress = this.assemblingProgress;
            this.assemblingProgress = progress;
            float finalProgress = progress;
            SparkLevel.getPhysicsLevel(level).submitDeduplicatedTask("setAssemblingProgress_" + uuid, PPhase.PRE, () -> {
                for (SubPart subPart : subParts.values()) {
                    if (finalProgress == 0 && renderWireframe) {
                        subPart.body.setGravity(SparkLevel.getPhysicsLevel(level).getWorld().getGravity(null).mult(0.1f));
                    } else {
                        subPart.body.setGravity(SparkLevel.getPhysicsLevel(level).getWorld().getGravity(null));
                        subPart.body.activate();
                    }
                }
                updateMass();
                return null;
            });
            float functionalThreshold = type.getFunctionalThreshold();
            if (!level.isClientSide() && renderWireframe
                    && oldProgress < functionalThreshold
                    && progress >= functionalThreshold) {
                renderWireframe = false;
            }
            syncAssemblyProgressToClient();
        }
    }

    public boolean shouldRenderWireframe() {
        return renderWireframe;
    }

    public void setRenderWireframe(boolean renderWireframe) {
        if (this.renderWireframe != renderWireframe) {
            this.renderWireframe = renderWireframe;
            syncAssemblyProgressToClient();
        }
    }

    private void syncAssemblyProgressToClient() {
        if (!level.isClientSide() && assembly != null && assembly.isInLevel()) {
            PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new PartAssemblyProgressSyncPayload(
                    assembly.getAssemblyId(),
                    uuid,
                    assemblingProgress,
                    materialProgress,
                    renderWireframe
            ));
        }
    }

    public float getVehicleDurabilityContribution() {
        return getSharedMaxDurability() * Math.max(0f, this.type.vehicleDurabilityRate);
    }
}
