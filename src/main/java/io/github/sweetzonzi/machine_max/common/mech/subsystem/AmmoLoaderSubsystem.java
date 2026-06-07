package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.MachineMax;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.AmmoLoaderSubsystemAttr;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 装弹机子系统。<br>
 * 管理弹药供给、装填时序和弹序循环，同时实现 {@link IAmmoSupplier} 和 {@link IAmmoConsumer} 双接口。<br>
 * 作为 IAmmoSupplier 向下游（Launcher、其他 AmmoLoader）提供弹药；作为 IAmmoConsumer 从上游供给者补充弹药。<br>
 * <p>
 * 两种装填模式：
 * <ul>
 *   <li>{@code roundByRound=false} — 整体换弹匣（机炮），切换供给来源时触发 reloadTimeTicks 延迟</li>
 *   <li>{@code roundByRound=true} — 逐发压入（火炮），每发弹药需等待 reloadTimeTicks</li>
 * </ul>
 * <p>
 * 状态机：IDLE → RELOADING(consumer, remainingTicks) → READY → consume → IDLE
 */
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier, IAmmoConsumer {

    public final AmmoLoaderSubsystemAttr attr;

    /** 物理弹药容器（FIFO） */
    private final SimpleContainer container;

    /** 每个消费者的装填剩余 tick 计时器 */
    private final Map<IAmmoConsumer, Integer> reloadTimers = new HashMap<>();

    /** 每个消费者弹药是否已就绪 */
    private final Set<IAmmoConsumer> readyConsumers = new HashSet<>();

    // ——— IAmmoConsumer 字段 ———

    /** 当前选中的上游供给者索引 */
    private int selectedSupplierIndex = 0;

    /** 上游供给者列表（串联模式用） */
    private final List<IAmmoSupplier> upstreamSuppliers = new ArrayList<>();

    /** 当前是否正在等待上游装填 */
    private boolean waitingUpstream = false;

    public AmmoLoaderSubsystem(ISubsystemHost owner, String name, AmmoLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
        this.container = new SimpleContainer(attr.staticAttribute.getMagazineCapacity());
    }

    // ==================== IAmmoSupplier 实现 ====================

    @Override
    public boolean hasAmmo() {
        return getAmmoCount() > 0;
    }

    @Override
    public int getRemainingCount() {
        return getAmmoCount();
    }

    @Override
    @Nullable
    public ProjectileType getSuppliedType() {
        // 返回容器中第一发弹药的类型，null 表示空仓
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                return getProjectileTypeFromItem(stack);
            }
        }
        return null;
    }

    @Override
    public boolean isRoundByRound() {
        return attr.staticAttribute.isRoundByRound();
    }

    @Override
    public int getReloadTimeTicks() {
        return attr.staticAttribute.getReloadTimeTicks();
    }

    @Override
    public boolean canSupplyMultiple() {
        return attr.staticAttribute.isCanSupplyMultiple();
    }

    @Override
    public String getLabel() {
        ProjectileType type = getSuppliedType();
        if (type != null) {
            ResourceLocation key = type.getRegistryKey();
            return key != null ? key.toString() : "unknown";
        }
        return "empty";
    }

    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        // 幂等性：已在装填中则直接返回 true
        if (reloadTimers.containsKey(consumer)) return true;
        if (readyConsumers.contains(consumer)) return true;

        // 有弹药 → 启动装填计时器
        if (hasAmmo()) {
            reloadTimers.put(consumer, attr.staticAttribute.getReloadTimeTicks());
            return true;
        }

        // 无弹药且有上游供给者 → 向上游请求补充
        if (autoRequestUpstream() && !upstreamSuppliers.isEmpty()) {
            IAmmoSupplier upstream = getCurrentUpstream();
            if (upstream != null) {
                waitingUpstream = true;
                upstream.requestRound(this);
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean isRoundReady(IAmmoConsumer consumer) {
        return readyConsumers.contains(consumer);
    }

    @Override
    @Nullable
    public ProjectileType consumeReadyRound(IAmmoConsumer consumer) {
        if (!readyConsumers.contains(consumer)) return null;
        readyConsumers.remove(consumer);

        // 从容器 FIFO 取出第一发非空弹药
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                ProjectileType type = getProjectileTypeFromItem(stack);
                if (type == null) {
                    // 无法识别弹药类型，跳过此物品
                    stack.shrink(1);
                    if (stack.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                    continue;
                }
                stack.shrink(1);
                if (stack.isEmpty()) container.setItem(i, ItemStack.EMPTY);
                return type;
            }
        }
        return null;
    }

    @Override
    public boolean canEject() {
        // 弹仓有剩余空间即可退弹
        return getFreeCapacityInternal() > 0;
    }

    @Override
    public void returnRound(ProjectileType type) {
        if (getFreeCapacityInternal() <= 0) return;
        // 将退弹放入第一个空槽位
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                // 创建一个占位物品（弹药通过 NBT 标记类型）
                ItemStack stack = new ItemStack(getAmmoItem(type), 1);
                container.setItem(i, stack);
                return;
            }
        }
    }

    // ==================== IAmmoConsumer 实现 ====================

    @Override
    public boolean canAcceptAmmo() {
        return isActive() && !isDestroyed();
    }

    @Override
    public int getFreeCapacity() {
        return getFreeCapacityInternal();
    }

    @Override
    public boolean receiveAmmo(ProjectileType type) {
        if (getFreeCapacityInternal() <= 0) return false;
        // 存入第一个空槽位
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                ItemStack stack = new ItemStack(getAmmoItem(type), 1);
                container.setItem(i, stack);
                waitingUpstream = false;
                return true;
            }
        }
        return false;
    }

    @Override
    @Nullable
    public IAmmoSupplier getCurrentSupplier() {
        return getCurrentUpstream();
    }

    @Override
    public List<IAmmoSupplier> getSuppliers() {
        return upstreamSuppliers;
    }

    @Override
    public void setCurrentSupplier(int index) {
        if (index >= 0 && index < upstreamSuppliers.size()) {
            selectedSupplierIndex = index;
        }
    }

    @Override
    public void addSupplier(IAmmoSupplier supplier) {
        upstreamSuppliers.add(supplier);
        if (upstreamSuppliers.size() == 1) {
            selectedSupplierIndex = 0;
        }
    }

    @Override
    public boolean canAccept(ProjectileType type) {
        // 装弹机接受任何弹药（由下游 Launcher 最终校验兼容性）
        return true;
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // ① 推进装填计时器
        Iterator<Map.Entry<IAmmoConsumer, Integer>> iter = reloadTimers.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = iter.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                // 装填完成
                iter.remove();
                readyConsumers.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }

        // ② 弹仓未满时自动向上游请求补充
        if (autoRequestUpstream() && getFreeCapacityInternal() > 0 && !waitingUpstream) {
            IAmmoSupplier upstream = getCurrentUpstream();
            if (upstream != null && upstream.hasAmmo()) {
                waitingUpstream = upstream.requestRound(this);
            }
        }
    }

    /**
     * 获取容器内弹药数量。
     */
    private int getAmmoCount() {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (!container.getItem(i).isEmpty()) count++;
        }
        return count;
    }

    /**
     * 获取剩余容量。
     */
    private int getFreeCapacityInternal() {
        return container.getContainerSize() - getAmmoCount();
    }

    /**
     * 是否启用自动向上游请求补充。
     */
    private boolean autoRequestUpstream() {
        return attr.staticAttribute.isAutoRequestUpstream() && !upstreamSuppliers.isEmpty();
    }

    /**
     * 获取当前选中的上游供给者（IAmmoSupplier 模式的 downstream AmmoLoader 用此获取弹药）。
     */
    @Nullable
    private IAmmoSupplier getCurrentUpstream() {
        if (upstreamSuppliers.isEmpty()) return null;
        if (selectedSupplierIndex < 0 || selectedSupplierIndex >= upstreamSuppliers.size()) return null;
        return upstreamSuppliers.get(selectedSupplierIndex);
    }

    /**
     * 从 ItemStack 中解析对应的 ProjectileType。<br>
     * 使用物品注册名作为投射物类型 ID 进行查找。
     */
    @Nullable
    private ProjectileType getProjectileTypeFromItem(ItemStack stack) {
        if (stack.isEmpty()) return null;

        // 用物品注册名作为投射物类型 ID
        ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId != null) {
            ProjectileType type = ProjectileType.get(getLevel(), itemId);
            if (type != null) return type;
        }

        return null;
    }

    /**
     * 获取表示指定弹药类型的物品。
     */
    private net.minecraft.world.item.Item getAmmoItem(ProjectileType type) {
        // 尝试从弹药类型的注册名获取对应物品
        ResourceLocation typeId = type.getRegistryKey();
        if (typeId != null) {
            net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(typeId);
            if (item != null) return item;
        }
        // fallback
        return net.minecraft.world.item.Items.ARROW;
    }

    // ==================== 握手机制 ====================

    @Override
    public void onAttach() {
        super.onAttach();
        handShake();
    }

    @Override
    public void onVehicleStructureChanged() {
        super.onVehicleStructureChanged();
        upstreamSuppliers.clear();
        handShake();
    }

    /**
     * 弹药链握手：向发现频道发送空信号，通过回调发现同一载具内的 IAmmoConsumer。<br>
     * 下游消费者（Launcher、其他 AmmoLoader）收到回调后通过 addSupplier() 注册此供给者。
     */
    protected void handShake() {
        for (String signalChannel : attr.discoveryOutputs.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(CompoundTag data) {
        super.loadData(data);
        NonNullList<ItemStack> items = NonNullList.withSize(container.getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(data, items, getOwner().getLevel().registryAccess());
        for (int i = 0; i < items.size() && i < container.getContainerSize(); i++) {
            container.setItem(i, items.get(i));
        }
    }

    @Override
    public CompoundTag saveData(CompoundTag data) {
        super.saveData(data);
        ContainerHelper.saveAllItems(data, container.getItems(), true, getOwner().getLevel().registryAccess());
        return data;
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        popItems();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        popItems();
    }

    /**
     * 被摧毁或拆卸时爆出容器中所有物品。
     */
    private void popItems() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (!stack.isEmpty()) {
                var pos = getOwner().getSubPart().getPosition();
                var vec3 = new net.minecraft.world.phys.Vec3(pos.x, pos.y, pos.z);
                var item = new net.minecraft.world.entity.item.ItemEntity(getOwner().getLevel(), vec3.x, vec3.y, vec3.z, stack);
                getOwner().getLevel().addFreshEntity(item);
            }
        }
    }

    // ==================== 信号 ====================

    @Override
    public boolean acceptAllRoutingInput() {
        return true;
    }

    @Override
    public boolean acceptAllBroadcastInput() {
        return acceptAllRoutingInput();
    }

    @Override
    public Map<String, List<String>> getTargetNames() {
        return attr.discoveryOutputs;
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        // 弹药发现频道回调：供给者发现下游消费者
        if (channelName.equals("callback") && sender instanceof IAmmoConsumer consumer) {
            if (consumer instanceof AbstractSubsystem sub) {
                if (sub.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
                    return SignalResult.PASS;
                }
            }
            // 注册自身到消费者的供给者列表
            consumer.addSupplier(this);
            return SignalResult.CONSUME;
        }
        return super.onSignalUpdated(channelName, sender);
    }
}
