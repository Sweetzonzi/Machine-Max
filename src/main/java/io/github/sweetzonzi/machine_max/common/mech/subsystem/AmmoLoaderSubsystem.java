package io.github.sweetzonzi.machine_max.common.mech.subsystem;

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
 * 管理物理弹药供给、装填时序和弹序循环，仅实现 {@link IAmmoSupplier} 接口。<br>
 * 不再实现 {@link IAmmoConsumer}（已移除多级弹药链支持）。<br>
 * <p>
 * 两种装填模式：
 * <ul>
 *   <li>{@code roundByRound=false} — 整体换弹匣（机炮），切换供给来源时触发 reloadTimeTicks 延迟</li>
 *   <li>{@code roundByRound=true} — 逐发压入（火炮），每发弹药需等待 reloadTimeTicks</li>
 * </ul>
 * <p>
 * 状态机：IDLE → RELOADING(consumer, remainingTicks) → READY → consume → IDLE
 */
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier {

    public final AmmoLoaderSubsystemAttr attr;

    /** 物理弹药容器（FIFO） */
    private final SimpleContainer container;

    /** 每个消费者的装填剩余 tick 计时器 */
    private final Map<IAmmoConsumer, Integer> reloadTimers = new HashMap<>();

    /** 每个消费者弹药是否已就绪 */
    private final Set<IAmmoConsumer> readyConsumers = new HashSet<>();

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
        return (int) (attr.staticAttribute.getReloadTime() * 20f); // 秒 → tick
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

    /**
     * 请求一发弹药，启动非阻塞装填计时器。<br>
     * 仅在本地有弹药时启动装填，无弹药直接返回 false（不再向上游请求补充）。
     * 幂等性：同一 consumer 多次调用不会创建重复计时器。
     *
     * @return true 表示请求已接受（首次请求或已在装填中）
     */
    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        // 幂等性：已在装填中则直接返回 true
        if (reloadTimers.containsKey(consumer)) return true;
        if (readyConsumers.contains(consumer)) return true;

        // 有弹药 → 启动装填计时器（秒 → tick）
        if (hasAmmo()) {
            reloadTimers.put(consumer, (int) (attr.staticAttribute.getReloadTime() * 20f));
            return true;
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

    @Override
    public int getCapacity() {
        return attr.staticAttribute.getMagazineCapacity();
    }

    @Override
    public SupplierStatus getStatus(IAmmoConsumer consumer) {
        if (reloadTimers.containsKey(consumer)) return SupplierStatus.RELOADING;
        if (getAmmoCount() <= 0) return SupplierStatus.EMPTY;
        return SupplierStatus.READY;
    }

    @Override
    public float getReloadProgress(IAmmoConsumer consumer) {
        Integer remaining = reloadTimers.get(consumer);
        if (remaining == null) return 0f;
        float total = attr.staticAttribute.getReloadTime() * 20f;
        return total > 0 ? 1f - (float) remaining / total : 1f;
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // ① 推进装填计时器（已移除向上游自动请求补充逻辑）
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
    }

    /**
     * 获取容器内弹药数量。
     */
    public int getAmmoCount() {
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
        handShake();
    }

    /**
     * 弹药链握手：向发现频道发送空信号，通过回调发现同一载具内的 IAmmoConsumer。<br>
     * 下游消费者（Launcher）收到回调后通过 addSupplier() 注册此供给者。
     * 回调携带频道名，供 Launcher 按 ammo_inputs 频道分组。
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
        // 弹药发现频道回调：供给者发现下游消费者，从回调值读取频道名后注册
        if (channelName.equals("callback") && sender instanceof IAmmoConsumer consumer) {
            if (consumer instanceof AbstractSubsystem sub) {
                if (sub.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
                    return SignalResult.PASS;
                }
            }
            // 从信号频道读取回调携带的频道名，若无法获取则使用 "unknown"
            Object callbackValue = getSignalChannel("callback").get(sender);
            String discoveryChannel = callbackValue instanceof String s ? s : "unknown";
            consumer.addSupplier(this, discoveryChannel);
            return SignalResult.CONSUME;
        }
        return super.onSignalUpdated(channelName, sender);
    }
}
