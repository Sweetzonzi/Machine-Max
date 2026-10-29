package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import cn.solarmoon.spark_core.physics.PenetrationKey;
import com.jme3.math.Vector3f;
import io.github.sweetzonzi.machine_max.common.mech.explosion.CookoffComposition;
import io.github.sweetzonzi.machine_max.common.mech.explosion.ExplosionManager;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 装弹机子系统。<br>
 * 管理物理弹药供给、装填时序和弹序循环，仅实现 {@link IAmmoSupplier} 接口。<br>
 * 不实现 {@link IAmmoConsumer}（无多级弹药链支持）。
 * <p>
 * 两种装填模式：
 * <ul>
 *   <li>{@code roundByRound=false} — 整体换弹匣（机炮），切换供给来源时触发 reloadTimeTicks 延迟</li>
 *   <li>{@code roundByRound=true} — 逐发压入（火炮），每发弹药需等待 reloadTimeTicks</li>
 * </ul>
 * <p>
 * 状态机：IDLE → RELOADING(consumer, remainingTicks) → READY → consume → IDLE
 * <p>
 * 多消费者：{@code can_supply_multiple=false} 时整台装弹机同一时刻只推进一个消费者的计时器，
 * 没排上的消费者在 {@link #requestRound(IAmmoConsumer)} 中只得到"已受理"，不起计时器，
 * 下个 tick 重发请求再试，直到前一个消费者取走弹药腾出输弹线——一条输弹链一次只能移动一发。
 * {@code can_supply_multiple=true} 时所有消费者的计时器并行推进。<br>
 * 因此不存在等待队列：{@link #reloadTimers} 里有没有本消费者，就是"轮到没轮到"。
 * <p>
 * 计时器与就绪位都用插入有序的普通集合：本子系统的读写全在主线程（自身 tick、武器控制器的路由、
 * HUD 刷新都在主线程），不需要并发容器。
 * <p>
 * <b>WIP：</b>弹仓以物品容器实现，退弹 {@link #returnRound(ProjectileType)} 的物品映射尚未做完
 * （投射物类型的注册名与物品注册名不通用，取不到时落成 {@code Items.ARROW}，
 * 再取弹时因解析不出投射物类型而被丢弃）。弹药链与路由决策暂由再生装弹机承担。
 */
@ApiStatus.Experimental
public class AmmoLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier {

    public final AmmoLoaderSubsystemAttr attr;

    /** 物理弹药容器（FIFO） */
    private final SimpleContainer container;

    /** 每个消费者的装填剩余 tick 计时器，插入有序 */
    private final Map<IAmmoConsumer, Integer> reloadTimers = new LinkedHashMap<>();

    /** 每个消费者弹药是否已就绪（交付已完成、等消费者取走），插入有序 */
    private final Set<IAmmoConsumer> readyConsumers = new LinkedHashSet<>();

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
     * 仅在本地有弹药时启动装填，无弹药直接返回 false（不从上游请求补充）。
     * 幂等性：同一 consumer 多次调用不会创建重复计时器。
     * <p>
     * {@code can_supply_multiple=false} 且已有别人在装填时，本次请求返回 true 但不启动计时器
     * ——它没有被排上，下个 tick 重发请求即可再试。整个流程只需 {@link #reloadTimers}
     * 一张表表达"谁的交付在进行中"，因此不记录等待队列。
     *
     * @return true 表示请求已受理（已轮到它，或本次未被排上）；false 表示本地无弹
     */
    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        // 已在交付中（计时中或待取）→ 幂等返回
        if (isRoundScheduled(consumer)) return true;

        // 无弹药：不受理，等弹药到位后由消费者再请求
        if (!hasAmmo()) return false;

        // 独占装弹机：已有别人占着这条输弹线，本次不排上
        if (!canSupplyMultiple() && hasScheduledRound()) return true;

        reloadTimers.put(consumer, (int) (attr.staticAttribute.getReloadTime() * 20f));
        return true;
    }

    @Override
    public boolean isRoundScheduled(IAmmoConsumer consumer) {
        return reloadTimers.containsKey(consumer) || readyConsumers.contains(consumer);
    }

    /**
     * 这次装填已经走了多少 tick：总耗时减剩余。<br>
     * 计时器是倒计时，所以这里换算一次。
     */
    @Override
    public int getDeliveryElapsedTicks(IAmmoConsumer consumer) {
        Integer remaining = reloadTimers.get(consumer);
        return remaining == null ? -1 : Math.max(0, getReloadTimeTicks() - remaining);
    }

    /**
     * 是否已有消费者被开始交付。<br>
     * 交付计时或已就绪待取都算"占着"：待取的那发还在弹仓里没被取走，同样不能被别人认领。
     */
    @Override
    public boolean hasScheduledRound() {
        return !reloadTimers.isEmpty() || !readyConsumers.isEmpty();
    }

    /**
     * 清掉已经不能再来取弹（被摧毁或失活）的消费者的交付状态。<br>
     * 消费者退场时不会通知供给者，而它的计时器/就绪位会一直占着输弹线，
     * 使这台装弹机对后来者永久不可用；因此在 tick 里自行清理。
     */
    private void pruneGoneConsumers() {
        reloadTimers.keySet().removeIf(AmmoLoaderSubsystem::isConsumerGone);
        readyConsumers.removeIf(AmmoLoaderSubsystem::isConsumerGone);
    }

    /**
     * 该消费者是否已经不能再来取弹（被摧毁或失活）。
     */
    private static boolean isConsumerGone(IAmmoConsumer consumer) {
        return consumer instanceof AbstractSubsystem sub && (sub.isDestroyed() || !sub.isActive());
    }

    /**
     * 取消该消费者尚未取走的交付。<br>
     * 计时器与就绪位都只表示"已承诺交付"，弹仓计数要等
     * {@link #consumeReadyRound(IAmmoConsumer)} 取出时才扣减，因此这里没有已扣出的弹药需要归还，
     * 清掉承诺即可；取消后输弹线立刻空出，等待中的消费者下次请求即可被排上。
     */
    @Override
    public void cancelRound(IAmmoConsumer consumer) {
        reloadTimers.remove(consumer);
        readyConsumers.remove(consumer);
    }

    @Override
    public boolean isRoundReady(IAmmoConsumer consumer) {
        return readyConsumers.contains(consumer);
    }

    /**
     * 取走已就绪的弹药。
     *
     * @return 弹药类型；消费者未就绪或弹仓已空时返回 null
     */
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

    /**
     * 接收一发退还的弹药：有空槽位就把它放进去。<br>
     * 弹仓是异构物品容器（弹种由每格物品决定），因此没有"弹链匹配"这一层判断，
     * 只需确认放得下；{@link #getAmmoItem(ProjectileType)} 取不到对应物品时按 WIP 说明
     * 退回占位物品，再取弹时会因解析不出投射物类型而被丢弃。
     *
     * @param type 退还的弹药类型
     * @return true 表示已放入弹仓
     */
    @Override
    public boolean returnRound(ProjectileType type) {
        if (getFreeCapacityInternal() <= 0) return false;
        // 将退弹放入第一个空槽位
        for (int i = 0; i < container.getContainerSize(); i++) {
            if (container.getItem(i).isEmpty()) {
                // 创建一个占位物品（弹药通过 NBT 标记类型）
                ItemStack stack = new ItemStack(getAmmoItem(type), 1);
                container.setItem(i, stack);
                return true;
            }
        }
        return false;
    }

    @Override
    public int getCapacity() {
        return attr.staticAttribute.getMagazineCapacity();
    }

    /**
     * 装弹机当前工作状态（per-consumer）。<br>
     * 注意这里不表达"有没有被排上"——那由 {@link #isRoundScheduled(IAmmoConsumer)} 回答。
     */
    @Override
    public SupplierStatus getStatus(IAmmoConsumer consumer) {
        if (readyConsumers.contains(consumer)) return SupplierStatus.READY;
        if (reloadTimers.containsKey(consumer)) return SupplierStatus.RELOADING;
        if (getAmmoCount() <= 0) return SupplierStatus.EMPTY;
        return SupplierStatus.READY;
    }

    /**
     * 装填进度（0~1）。<br>
     * 未被排上的消费者没有计时器，进度为 0。
     *
     * @param consumer 请求进度的消费者
     * @return 进度值；无计时器时返回 0
     */
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

        // 清掉已退场的消费者，别让它们的交付占着输弹线
        pruneGoneConsumers();

        // 推进装填计时器（本子系统不从上游补充弹药：弹仓空了就拒绝请求）
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
        if (getLevel().isClientSide() || getSubPart().isDestroyed()) return;
        detonateStoredAmmo();
    }

    /**
     * 库存殉爆：按物品容器里的实际弹药合成一次普通爆炸，并实际清空容器。
     * <p>
     * 每格按 {@code stack.getCount()} 计入该弹种的发射药贡献与各战斗部的被诱爆贡献；
     * 解析不出投射物类型的物品不产生贡献，但一旦判定需要起爆，容器整体清空（含这些物品），
     * 不产生掉落物。{@code P、D、J} 全为零时不消耗库存、不起爆。
     * <p>
     * <b>调用线程：</b>主线程（子系统摧毁边沿）。
     */
    private void detonateStoredAmmo() {
        if (getAmmoCount() <= 0) return;

        CookoffComposition composition = new CookoffComposition();
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) continue;
            ProjectileType type = getProjectileTypeFromItem(stack);
            composition.addStoredRound(type, stack.getCount());
        }
        if (composition.isEmpty()) return;

        // 起爆位置：最近有效受击指认的 HitBox 局部形心；无有效来源回退宿主质心
        Vector3f origin = getLastDamageOrigin();
        // 爆点落在自身命中箱内部，该命中箱必须豁免，否则近距离满值穿深会吃掉整发爆炸
        Set<PenetrationKey> ignoredKeys = getCookoffIgnoredKeys();
        // 先消耗库存再交给普通爆炸服务：本次起爆后该单元不再有弹药可供给
        consumeAllStoredAmmo();
        ExplosionManager.get(getLevel()).detonate(
                origin, composition.toExplosionParams(), getLevel().random.nextLong(), ignoredKeys);
    }

    /**
     * 起爆消耗：实际清空物品容器并清掉所有在途与待取的输送承诺。
     */
    private void consumeAllStoredAmmo() {
        for (int i = 0; i < container.getContainerSize(); i++) {
            container.setItem(i, ItemStack.EMPTY);
        }
        reloadTimers.clear();
        readyConsumers.clear();
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
