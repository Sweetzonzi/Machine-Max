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
import io.github.sweetzonzi.machine_max.common.mech.ObjectManager;

/**
 * MM 普通部件实例，在公共机械基础上实现材料组装、维修与载具伤害规则。
 *
 * <p>材料供给进度与组装进度分别保存：材料决定能组装和修复到哪里，组装进度影响工作许可和
 * 可写入耐久上限。二者在恢复构造器中先初始化，再创建子零件，避免虚方法读取到默认零值。</p>
 * <p>材料消耗、返还、伤害结算及网络同步由主线程路径驱动；运行中的重力更新提交到 PRE 物理阶段。
 * 普通部件是否允许撬棍拆卸由构造上下文决定，不由本类名称自动推断。</p>
 */
@Getter
public final class RegularPart extends AbstractPart<RegularPartType> {
    /**
     * 组装完成比例，合法读取通过 getAssemblingProgress() 钳制到 [0, 1]；材料与耐久变化会调整它。
     */
    public volatile float assemblingProgress = 1f;
    /**
     * 按手动组装材料清单计数的已供给项数；新建完整部件以大值表示材料齐备。
     */
    public int materialProgress = Integer.MAX_VALUE;
    /**
     * 是否显示组装线框；与组装进度共同决定零进度部件的低重力处理。
     */
    public boolean renderWireframe = true;

    /**
     * 新建完整普通部件，生成随机 UUID 并按变体创建结构。
     * <p>构造阶段尚未入世；外部入口应优先经 {@link PartFactory#createRegular} 完成预检。</p>
     */
    public RegularPart(RegularPartType type, String variant, Level level, PartCreationContext context) {
        super(type, variant, level, UUID.randomUUID(), type.getRegistryKey().toLanguageKey(), context);
        initializeStructure();
    }

    /**
     * 从存档恢复普通部件，先确定制造状态，再构造结构和加载公共动态状态。
     *
     * <p>完整模式采用保存的材料、组装进度和线框状态；骨架模式将两项进度置零并开启线框。
     * 结构构造后由 {@link #restoreState} 应用模式允许的状态。失败时清理构造资源，不触发正常拆离掉落。</p>
     *
     * @param data 工厂已预检的普通部件存档；不会修改输入
     * @param type 与存档型号一致的已发布定义
     * @param level 恢复目标世界
     * @param mode 完整恢复或骨架恢复
     * @param context 本次路径授予的拆卸权限
     */
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

    /**
     * 采集普通部件的公共状态、材料供给、组装进度和线框标志。
     */
    @Override
    public RegularPartData save() {
        return new RegularPartData(this);
    }

    /**
     * 组装进度达到定义的工作阈值时允许子系统工作。
     */
    @Override
    public boolean permitsSubsystemWork() {
        return getAssemblingProgress() >= type.functionalThreshold;
    }

    /**
     * 耐久写入上限随组装进度变化；最低保留设计上限的 5%，使骨架可承受初始损伤。
     */
    @Override
    public float durabilityLimitRatio() {
        return Math.max(getAssemblingProgress(), 0.05f);
    }

    /**
     * 允许 MM 子零件维修流程继续处理；实际可修复到的比例另由材料限制。
     */
    @Override
    public boolean permitsRepair() {
        return true;
    }

    /**
     * 完全组装后允许子零件的延迟重力初始化恢复正常重力。
     */
    @Override
    public boolean usesFullGravity() {
        return getAssemblingProgress() >= 1f;
    }

    /**
     * 有手动材料清单时，以已供给项数占比限制维修目标；无配方或清单为空时返回 1。
     */
    @Override
    public float repairLimitRatio() {
        PartFabricatingRecipe recipe = getRecipe();
        int count = recipe == null ? 0 : recipe.getManualAssembleIngredientList().size();
        return count > 0 ? Math.clamp((float) materialProgress / count, 0f, 1f) : 1f;
    }

    /**
     * 按普通部件的两档系数折算装配体伤害。
     * <p>子零件已摧毁时全部使用摧毁系数；否则可被剩余耐久吸收的部分使用正常系数，溢出部分使用摧毁系数。</p>
     */
    @Override
    public float assemblyDamage(float amount, float remaining, boolean destroyed) {
        if (destroyed) return amount * type.vehicleDamageRateDestroyed;
        float absorbed = Math.min(amount, Math.max(remaining, 0f));
        return absorbed * type.vehicleDamageRate + (amount - absorbed) * type.vehicleDamageRateDestroyed;
    }

    /**
     * 在服务端伤害或维修结算后，根据子零件耐久重算组装进度与材料上限。
     */
    @Override
    public void onDurabilityChanged() {
        recomputeAssemblyFromDurability();
    }

    /**
     * 维修尝试进入时开启线框，并按需要向客户端同步；不表示此次一定恢复了耐久。
     */
    @Override
    public void onRepairAttempt() {
        setRenderWireframe(true);
    }

    /**
     * 在主线程更新线框自动隐藏条件，再推进公共动画发布与存活检查。
     */
    @Override
    public void onTick() {
        if (renderWireframe && getAssemblingProgress() > type.functionalThreshold) setRenderWireframe(false);
        super.onTick();
    }

    /**
     * 主线程推进组装，并按手动材料清单顺序消耗物品。
     *
     * <p>有配方时以加工时间换算进度，并按目标进度向上取整计算所需材料档位；材料不足时在已供给上限停止。
     * 客户端与无限材料玩家只推进记录、不扣物品。无配方时每单位输入增加 0.01 进度。</p>
     *
     * @param container 用于取得材料的容器
     * @param progress 本次投入的非负加工时间，单位为 tick
     * @return 组装进度是否改变；材料记录可能在返回假时发生调整
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
     * 主线程降低组装进度，并按手动拆解清单逆序返还材料。
     *
     * <p>有配方时以加工时间换算进度，并向下取整计算保留材料档位。容器无法容纳返还物品时，
     * 服务端在根子零件位置掉落。客户端与无限材料玩家不返还物品；没有配方则不处理拆解。</p>
     *
     * @param container 接收返还材料的容器
     * @param progress 本次拆解时间，单位为 tick；负数按零处理
     * @return 组装进度是否改变；返回假不代表材料记录一定未变
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

    /**
     * 按世界与型号查询制造配方。
     * @return 当前配方；该型号无配方或索引尚未就绪时返回 {@code null}
     */
    public PartFabricatingRecipe getRecipe() {
        RecipeHolder<PartFabricatingRecipe> holder = MMDynamicRes.getPartRecipe(level, type.getRegistryKey());
        return holder == null ? null : holder.value();
    }

    /**
     * 设置已供给的材料项数，不扣物品也不自动修改组装进度。
     *
     * <p>有配方时钳制到手动组装清单的项数范围；没有配方时只限制为非负数。</p>
     *
     * @param progress 材料清单中已经供给的项数
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

    /**
     * 将耐久比例换算为可保留的材料档位。
     * <p>零耐久归零；正耐久取向下取整后再加一项并限制到材料总数，避免恰在材料边界时过早扣去下一档。</p>
     */
    private static int getMaterialTierByDurabilityRatio(float ratio, int totalMaterials) {
        if (totalMaterials <= 0) return 0;
        if (ratio <= 0f) return 0;
        return Math.min(totalMaterials, (int) Math.floor(Math.clamp(ratio, 0f, 1f) * totalMaterials) + 1);
    }

    /**
     * 主线程设置 [0, 1] 组装进度，并安排重力与客户端同步更新。
     *
     * <p>进度影响后续耐久写入的上限，本方法不直接重设现有耐久。重力任务按部件 UUID 去重并在 PRE
     * 物理阶段执行：进度为零且开启线框时使用世界重力的 10%，其他情况恢复正常重力并激活刚体。
     * 设计质量不按进度缩放。</p>
     * <p>服务端进度从阈值下方达到工作阈值时关闭线框；状态变化按需向所在维度广播。</p>
     *
     * @param progress 目标组装完成比例
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

    /**
     * 返回当前组装线框开关，供子零件渲染入口查询。
     */
    public boolean shouldRenderWireframe() {
        return renderWireframe;
    }

    /**
     * 设置组装线框标志；值变化时由服务端向所在维度同步制造状态。
     */
    public void setRenderWireframe(boolean renderWireframe) {
        if (this.renderWireframe != renderWireframe) {
            this.renderWireframe = renderWireframe;
            syncAssemblyProgressToClient();
        }
    }

    /**
     * 向本维度玩家广播组装、材料和线框状态。
     * <p>仅服务端且部件已归属入世装配体时发送。选择一个仍在对象表内的子零件作为地址，
     * 客户端通过该子零件取得部件；不要求宿主进入载具注册表。没有有效子零件时不发送。</p>
     */
    private void syncAssemblyProgressToClient() {
        if (!level.isClientSide() && assembly != null && assembly.isInLevel()) {
            // 根子零件可能已销毁；任一仍注册的子零件都可以定位整个部件。
            for (SubPart child : subParts.values()) {
                if (ObjectManager.getDestroyableObject(level, child.getId()) != child) continue;
                PacketDistributor.sendToPlayersInDimension((ServerLevel) level, new PartAssemblyProgressSyncPayload(
                        child.getId(), assemblingProgress, materialProgress, renderWireframe));
                break;
            }
        }
    }

    /**
     * 返回共享耐久上限乘以非负贡献系数的结果，供载具汇总 HP 上限使用。
     */
    public float getVehicleDurabilityContribution() {
        return getSharedMaxDurability() * Math.max(0f, this.type.vehicleDurabilityRate);
    }
}
