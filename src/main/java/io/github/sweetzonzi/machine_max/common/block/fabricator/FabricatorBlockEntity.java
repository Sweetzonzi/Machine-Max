package io.github.sweetzonzi.machine_max.common.block.fabricator;

import cn.solarmoon.spark_core.animation.IBlockEntityAnimatable;
import cn.solarmoon.spark_core.animation.anim.AnimController;
import cn.solarmoon.spark_core.animation.anim.AnimInstance;
import cn.solarmoon.spark_core.animation.model.ModelController;
import io.github.sweetzonzi.machine_max.common.menu.FabricatingMenu;
import io.github.sweetzonzi.machine_max.common.recipe.FabricatingRecipe;
import io.github.sweetzonzi.machine_max.common.registry.MMBlockEntities;
import io.github.sweetzonzi.machine_max.common.registry.MMResources;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Getter
public class FabricatorBlockEntity extends BaseContainerBlockEntity implements IBlockEntityAnimatable<FabricatorBlockEntity> {
    //动画相关
    public final AnimController animController = new AnimController(this);
    public final ModelController modelController = new ModelController(this);
    private final Map<String, Object> variables = HashMap.newHashMap(1);
    private ProductionTask renderingTask; // 当前渲染的任务
    // 动画实例
    public AnimInstance workAnim;
    public AnimInstance idleAnim;

    // 状态机
    public enum State {
        IDLE,       // 空闲状态
        WORKING,    // 工作中状态
        FINISHED    // 完成状态
    }

    @Getter
    private State currentState = State.IDLE;
    //属性机制相关
    public int maxTaskSize = 4; // 最大任务槽数
    public int maxWorkingTaskSize = 2; // 最大同时生产任务数
    public float efficiency = 1f; // 生产效率
    public int accuracy = 1; // TODO: 生产精度，或许控制可制造的配方或不同等级配方的制造速度加成
    // 任务槽管理 - 每个任务槽对应一个输出槽
    private final ProductionTask[] taskSlots; // 任务槽数组
    private final NonNullList<ItemStack> outputItems; // 输出物品（每个任务槽对应一个输出槽）
    // 状态跟踪
    private boolean needsUpdate = false;

    // 任务状态
    public enum TaskStatus {
        IDLE,        // 空闲
        QUEUED,      // 排队中
        PRODUCING,   // 生产中
        COMPLETED    // 已完成（待领取）
    }

    // 生产任务类
    public static class ProductionTask {
        public final int id;               // 任务ID
        public final String recipeId;      // 配方ID
        public final ItemStack result;     // 产出结果
        public final int totalTime;        // 总制造时间
        public float progress;             // 当前进度
        public TaskStatus status;          // 任务状态
        public final long createTime;      // 创建时间

        public ProductionTask(int id, String recipeId, ItemStack result, int totalTime) {
            this.id = id;
            this.recipeId = recipeId;
            this.result = result.copy();
            this.totalTime = totalTime;
            this.progress = 0f;
            this.status = TaskStatus.QUEUED;
            this.createTime = System.currentTimeMillis();
        }

        // 从NBT加载
        public ProductionTask(CompoundTag tag, HolderLookup.Provider levelRegistryAccess) {
            this.id = tag.getInt("Id");
            this.recipeId = tag.getString("RecipeId");
            this.result = ItemStack.parseOptional(levelRegistryAccess, tag.getCompound("Result"));
            this.totalTime = tag.getInt("TotalTime");
            this.progress = tag.getFloat("Progress");
            this.status = TaskStatus.values()[tag.getByte("Status")];
            this.createTime = tag.getLong("CreateTime");
        }

        // 保存到NBT
        public CompoundTag save(HolderLookup.Provider levelRegistryAccess) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Id", id);
            tag.putString("RecipeId", recipeId);
            tag.put("Result", result.save(levelRegistryAccess));
            tag.putInt("TotalTime", totalTime);
            tag.putFloat("Progress", progress);
            tag.putByte("Status", (byte) status.ordinal());
            tag.putLong("CreateTime", createTime);
            return tag;
        }

        public boolean isCompleted() {
            return progress >= totalTime;
        }

        public float getProgressPercent() {
            return totalTime > 0 ? progress / totalTime : 0f;
        }
    }

    public FabricatorBlockEntity(BlockPos pos, BlockState blockState) {
        super(MMBlockEntities.getFABRICATOR_BLOCK_ENTITY().get(), pos, blockState);
        this.taskSlots = new ProductionTask[maxTaskSize];
        this.outputItems = NonNullList.withSize(maxTaskSize, ItemStack.EMPTY);
        // 初始化状态机
        this.currentState = State.IDLE;
        this.renderingTask = null;
    }

    public void tick(Level level, BlockPos pos, BlockState blockState) {
        animController.physTick();
        animController.physTick();
        animController.physTick();
        animController.tick();
        updateState();// 更新状态机
        if (level.isClientSide) return;

        boolean changed = false;
        // 更新生产中的任务进度
        for (int i = 0; i < maxTaskSize; i++) {
            ProductionTask task = taskSlots[i];
            if (task != null && task.status == TaskStatus.PRODUCING) {
                task.progress += efficiency;

                // 检查任务是否完成
                if (task.isCompleted()) {
                    task.status = TaskStatus.COMPLETED;
                    // 将产物放入对应的输出槽
                    outputItems.set(i, task.result.copy());
                }
                changed = true;
            }
        }
        // 统计当前正在生产的任务数
        int producingCount = getProducingTaskCount();

        // 检查是否有排队任务可以开始生产
        if (producingCount < maxWorkingTaskSize) {
            for (int i = 0; i < maxTaskSize && producingCount < maxWorkingTaskSize; i++) {
                ProductionTask task = taskSlots[i];
                if (task != null && task.status == TaskStatus.QUEUED) {
                    task.status = TaskStatus.PRODUCING;
                    producingCount++;
                    changed = true;
                }
            }
        }

        // 如果有变化，标记更新
        if (changed || needsUpdate) {
            setChanged();
            level.sendBlockUpdated(pos, blockState, blockState, 3);
            needsUpdate = false;
        }
    }

    /**
     * 获取空闲的任务槽索引
     */
    private int getFreeTaskSlot() {
        for (int i = 0; i < maxTaskSize; i++) {
            if (taskSlots[i] == null) {
                return i;
            }
        }
        return -1;
    }

    private void updateState() {
        //更新状态
        if (getProducingTaskCount() > 0 && currentState != State.WORKING) {
            currentState = State.WORKING;
            renderingTask = findFirstProducingOrQueuedTask();
            if (workAnim != null) workAnim.enter();
        } else if (getCompletedTaskCount() > 0 && currentState != State.FINISHED && getProducingTaskCount() == 0) {
            currentState = State.FINISHED;
            renderingTask = findFirstCompletedTask();
            if (idleAnim != null) idleAnim.enter();
        } else if (currentState != State.IDLE && getProducingTaskCount() == 0 && getCompletedTaskCount() == 0) {
            currentState = State.IDLE;
            renderingTask = null;
            if (workAnim != null) workAnim.enter();
        }
        //更新动画进度与渲染
        if (currentState == State.WORKING) {
            renderingTask = findFirstProducingOrQueuedTask();
            if (renderingTask != null) {
                if (workAnim != null)
                    workAnim.setTime(renderingTask.progress / 20 / efficiency % workAnim.getMaxLength());
                //TODO:工作进度存为molang变量供动画使用
            }
        }
    }

    /**
     * 添加新生产任务
     */
    public boolean addFabricationTask(Player player, FabricatingRecipe recipe) {
        if (level == null) return false;

        // 检查是否有空闲任务槽
        int freeSlot = getFreeTaskSlot();
        if (freeSlot == -1) {
            return false;
        }

        // 检查玩家是否有足够原料
        if (!recipe.hasRequiredIngredients(player) && !player.isCreative()) {
            return false;
        }

        // 消耗原料
        if (!player.isCreative()) {
            recipe.consumeIngredients(player);
        }

        // 创建生产任务
        String recipeId = level.getRecipeManager()
                .getAllRecipesFor(MMResources.getFABRICATION_RECIPE_TYPE().get())
                .stream()
                .filter(holder -> holder.value() == recipe)
                .map(holder -> holder.id().toString())
                .findFirst()
                .orElse("unknown");

        ProductionTask task = new ProductionTask(
                freeSlot,
                recipeId,
                recipe.getResultItem(level.registryAccess()),
                recipe.getProcessingTime()
        );

        // 决定任务初始状态
        if (getProducingTaskCount() < maxWorkingTaskSize) {
            task.status = TaskStatus.PRODUCING;
        } else {
            task.status = TaskStatus.QUEUED;
        }

        // 将任务放入空闲槽位
        taskSlots[freeSlot] = task;

        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);

        return true;
    }

    /**
     * 取消任务
     */
    public boolean cancelTask(int taskId) {
        ProductionTask task = taskSlots[taskId];
        if (task != null && task.status != TaskStatus.COMPLETED) {
            taskSlots[taskId] = null;
            // 注意：不返还材料
            needsUpdate = true;
            setChanged();
            return true;
        }
        return false;
    }

    /**
     * 领取指定输出槽的物品
     */
    public void collectTask(int slot, Player player) {
        if (slot < 0 || slot >= maxTaskSize) return;

        ItemStack stack = outputItems.get(slot);
        if (!stack.isEmpty()) {
            needsUpdate = true;
            setChanged();
            // 尝试将物品添加到玩家背包
            if (player.getInventory().add(stack)) {
                outputItems.set(slot, ItemStack.EMPTY);
                // 如果对应的任务已完成，清空任务槽
                if (taskSlots[slot] != null && taskSlots[slot].status == TaskStatus.COMPLETED) {
                    taskSlots[slot] = null;
                }
            } else if (level != null) {
                // 背包已满，直接掉落
                level.addFreshEntity(Objects.requireNonNull(player.drop(stack, true)));
            }
        }
    }

    /**
     * 领取所有输出物品
     */
    public void collectAllTasks(Player player) {
        for (int i = 0; i < maxTaskSize; i++) {
            collectTask(i, player);
        }
    }

    // 获取各种任务数量
    public int getProducingTaskCount() {
        int count = 0;
        for (ProductionTask task : taskSlots) {
            if (task != null && task.status == TaskStatus.PRODUCING) {
                count++;
            }
        }
        return count;
    }

    public int getQueuedTaskCount() {
        int count = 0;
        for (ProductionTask task : taskSlots) {
            if (task != null && task.status == TaskStatus.QUEUED) {
                count++;
            }
        }
        return count;
    }

    public int getCompletedTaskCount() {
        int count = 0;
        for (ProductionTask task : taskSlots) {
            if (task != null && task.status == TaskStatus.COMPLETED) {
                count++;
            }
        }
        return count;
    }

    public int getIdleTaskCount() {
        int count = 0;
        for (ProductionTask task : taskSlots) {
            if (task == null) {
                count++;
            }
        }
        return count;
    }

    public int getTotalTaskCount() {
        return maxTaskSize - getIdleTaskCount();
    }

    // 获取任务列表（用于GUI显示）
    public List<ProductionTask> getAllTasks() {
        List<ProductionTask> allTasks = new ArrayList<>();
        for (ProductionTask task : taskSlots) {
            if (task != null) {
                allTasks.add(task);
            }
        }
        allTasks.sort(Comparator.comparingLong(task -> task.createTime));
        return allTasks;
    }

    /**
     * 查找第一个生产中的任务，如果没有则找第一个排队中的任务
     */
    private ProductionTask findFirstProducingOrQueuedTask() {
        // 优先查找生产中的任务
        for (int i = 0; i < maxTaskSize; i++) {
            ProductionTask task = taskSlots[i];
            if (task != null && task.status == TaskStatus.PRODUCING) {
                return task;
            }
        }

        // 如果没有生产中的任务，查找排队中的任务
        for (int i = 0; i < maxTaskSize; i++) {
            ProductionTask task = taskSlots[i];
            if (task != null && task.status == TaskStatus.QUEUED) {
                return task;
            }
        }

        return null;
    }

    /**
     * 查找第一个完成的任务
     */
    private ProductionTask findFirstCompletedTask() {
        for (int i = 0; i < maxTaskSize; i++) {
            ProductionTask task = taskSlots[i];
            if (task != null && task.status == TaskStatus.COMPLETED) {
                return task;
            }
        }
        return null;
    }

    /**
     * 获取指定槽位的任务
     */
    public ProductionTask getTaskAtSlot(int slot) {
        if (slot >= 0 && slot < maxTaskSize) {
            return taskSlots[slot];
        }
        return null;
    }

    /**
     * 获取指定槽位的任务状态
     */
    public TaskStatus getTaskStatusAtSlot(int slot) {
        ProductionTask task = getTaskAtSlot(slot);
        return task != null ? task.status : TaskStatus.IDLE;
    }

    // NBT持久化
    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);

        // 保存任务槽
        ListTag taskSlotsList = new ListTag();
        for (int i = 0; i < maxTaskSize; i++) {
            CompoundTag slotTag = new CompoundTag();
            if (taskSlots[i] != null) {
                slotTag.put("Task", taskSlots[i].save(registries));
            }
            taskSlotsList.add(slotTag);
        }
        tag.put("TaskSlots", taskSlotsList);

        // 保存配置
        tag.putInt("MaxTaskSize", maxTaskSize);
        tag.putInt("MaxWorkingTaskSize", maxWorkingTaskSize);
        tag.putFloat("Efficiency", efficiency);
        tag.putInt("Accuracy", accuracy);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);

        // 加载任务槽
        outputItems.clear();// 加载输出物品
        ListTag taskSlotsList = tag.getList("TaskSlots", Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(maxTaskSize, taskSlotsList.size()); i++) {
            CompoundTag slotTag = taskSlotsList.getCompound(i);
            if (slotTag.contains("Task")) {
                CompoundTag taskTag = slotTag.getCompound("Task");
                taskSlots[i] = new ProductionTask(taskTag, registries);
                // 从任务标签中获取结果物品
                if (taskSlots[i].isCompleted()) {
                    outputItems.set(i, taskSlots[i].result.copy());
                }
            } else {
                taskSlots[i] = null;
            }
        }

        // 加载配置
        if (tag.contains("MaxTaskSize")) {
            maxTaskSize = tag.getInt("MaxTaskSize");
        }
        if (tag.contains("MaxWorkingTaskSize")) {
            maxWorkingTaskSize = tag.getInt("MaxWorkingTaskSize");
        }
        if (tag.contains("Efficiency")) {
            efficiency = tag.getFloat("Efficiency");
        }
        if (tag.contains("Accuracy")) {
            accuracy = tag.getInt("Accuracy");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        // 保存任务槽
        ListTag taskSlotsList = new ListTag();
        for (int i = 0; i < maxTaskSize; i++) {
            CompoundTag slotTag = new CompoundTag();
            if (taskSlots[i] != null) {
                slotTag.put("Task", taskSlots[i].save(registries));
            }
            taskSlotsList.add(slotTag);
        }
        tag.put("TaskSlots", taskSlotsList);

        // 保存配置
        tag.putInt("MaxTaskSize", maxTaskSize);
        tag.putInt("MaxWorkingTaskSize", maxWorkingTaskSize);
        tag.putFloat("Efficiency", efficiency);
        tag.putInt("Accuracy", accuracy);
        return tag;
    }

    // 容器相关方法
    @Override
    public @NotNull Component getDisplayName() {
        return Component.empty();
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return getDisplayName();
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return outputItems;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        for (int i = 0; i < Math.min(outputItems.size(), items.size()); i++) {
            outputItems.set(i, items.get(i));
        }
    }

    @Override
    public int getContainerSize() {
        return maxTaskSize; // 容器大小等于任务槽数
    }

    @Override
    public boolean isEmpty() {
        return outputItems.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < outputItems.size() ? outputItems.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack result = stack.split(amount);
        if (stack.isEmpty()) {
            outputItems.set(slot, ItemStack.EMPTY);
            // 如果对应的任务已完成，清空任务槽
            if (taskSlots[slot] != null && taskSlots[slot].status == TaskStatus.COMPLETED) {
                taskSlots[slot] = null;
            }
        }
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        if (!stack.isEmpty()) {
            outputItems.set(slot, ItemStack.EMPTY);
            // 如果对应的任务已完成，清空任务槽
            if (taskSlots[slot] != null && taskSlots[slot].status == TaskStatus.COMPLETED) {
                taskSlots[slot] = null;
            }
        }
        return stack;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        if (slot >= 0 && slot < outputItems.size()) {
            outputItems.set(slot, stack);
            setChanged();
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this &&
                player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    public void clearContent() {
        Collections.fill(outputItems, ItemStack.EMPTY);
        // 注意：不清空任务槽，只清空输出物品
        setChanged();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory playerInventory) {
        return new FabricatingMenu(containerId, this);
    }

    @Override
    public FabricatorBlockEntity getAnimatable() {
        return this;
    }
}
