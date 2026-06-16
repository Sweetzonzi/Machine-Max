package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.energy.EnergyGrid;
import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import io.github.sweetzonzi.machine_max.common.mech.signal.EmptySignal;
import io.github.sweetzonzi.machine_max.common.mech.signal.ISignalSender;
import io.github.sweetzonzi.machine_max.common.mech.signal.SignalResult;
import io.github.sweetzonzi.machine_max.common.mech.subsystem.attr.dynamic_attr.RegenLoaderSubsystemAttr;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 再生装弹机子系统。<br>
 * 统一能量武器（激光、爆能束）和街机风格再生武器（导弹冷却再生）的弹药供给模型。<br>
 * 实现 {@link IAmmoSupplier} 接口，不实现 {@link IAmmoConsumer}（不消耗外部弹药）。<br>
 * <p>
 * 两种再生模式：
 * <ul>
 *   <li>{@code regenRoundByRound=true} — 随打随产：每消耗一发立即开始再生下一发</li>
 *   <li>{@code regenRoundByRound=false} — 批量产出：弹仓全空后触发再生，一次性回满</li>
 * </ul>
 * <p>
 * 能量消耗通过 {@link EnergyGrid#consumeEnergy(float)} 瞬时抽取，不实现 {@code IEnergyConsumer} 接口。
 */
public class RegenLoaderSubsystem extends BasicSubsystem implements IAmmoSupplier {

    public final RegenLoaderSubsystemAttr attr;

    /** 当前弹药计数 */
    @Getter
    private int ammoCount = 0;

    /** 再生进度（0.0 ~ 1.0+，累积超过 1.0 时产出一发） */
    private float regenProgress = 0f;

    /** 批量冷却剩余 tick（regenRoundByRound=false 时使用） */
    private int batchCooldownTicks = 0;

    /** 是否正在批量冷却中 */
    private boolean isBatchReloading = false;

    // ——— 多消费者支持 ———

    /** 每个消费者的输送计时器 */
    private final Map<IAmmoConsumer, Integer> deliveryTimers = new HashMap<>();

    /** 每个消费者的弹药是否已就绪 */
    private final Set<IAmmoConsumer> readyConsumers = new HashSet<>();

    public RegenLoaderSubsystem(ISubsystemHost owner, String name, RegenLoaderSubsystemAttr attr) {
        super(owner, name, attr);
        this.attr = attr;
    }

    // ==================== IAmmoSupplier 实现 ====================

    @Override
    public boolean hasAmmo() {
        return ammoCount > 0;
    }

    @Override
    public int getRemainingCount() {
        return ammoCount;
    }

    @Override
    @Nullable
    public ProjectileType getSuppliedType() {
        if (ammoCount <= 0) return null;
        return ProjectileType.get(getLevel(), attr.staticAttribute.getProjectileType());
    }

    @Override
    public boolean isRoundByRound() {
        return false; // RegenLoader 不区分逐发/弹链，所有交付均为瞬时+输送延迟
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
        return attr.staticAttribute.getProjectileType().toString();
    }

    @Override
    public boolean requestRound(IAmmoConsumer consumer) {
        if (deliveryTimers.containsKey(consumer) || readyConsumers.contains(consumer)) {
            return true; // 幂等
        }

        if (ammoCount <= 0) return false;

        // 启动输送计时器
        int tickTime = attr.staticAttribute.getReloadTimeTicks();
        if (tickTime > 0) {
            deliveryTimers.put(consumer, tickTime);
        } else {
            // 瞬时交付
            readyConsumers.add(consumer);
        }
        return true;
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

        if (ammoCount <= 0) return null;
        ammoCount--;

        // 随打随产模式：弹药消耗后立即开始再生
        if (attr.staticAttribute.isRegenRoundByRound() && attr.staticAttribute.getRegenPerMinute() > 0) {
            // regenProgress 在 onTick 中累积，触发自动再生
        }

        return ProjectileType.get(getLevel(), attr.staticAttribute.getProjectileType());
    }

    @Override
    public int getCapacity() {
        return attr.staticAttribute.getMagazineCapacity();
    }

    @Override
    public SupplierStatus getStatus(IAmmoConsumer consumer) {
        if (isBatchReloading) return SupplierStatus.RELOADING;
        if (ammoCount <= 0) return SupplierStatus.EMPTY;
        return SupplierStatus.READY;
    }

    @Override
    public float getReloadProgress(IAmmoConsumer consumer) {
        int cap = attr.staticAttribute.getMagazineCapacity();
        if (cap <= 0) return 0f;
        if (isBatchReloading) {
            // 批量冷却模式：计算总冷却 tick 数，返回剩余比例
            float rpm = attr.staticAttribute.getRegenPerMinute();
            int totalTicks = rpm > 0 ? (int) (cap / rpm * 1200) : 1;
            return 1f - (float) batchCooldownTicks / totalTicks;
        }
        // 非 batch 模式：当前余量 / 总容量
        return (float) ammoCount / cap;
    }

    @Override
    public boolean canEject() {
        return getFreeCapacity() > 0;
    }

    @Override
    public void returnRound(ProjectileType type) {
        if (ammoCount < attr.staticAttribute.getMagazineCapacity()) {
            ammoCount++;
        }
    }

    // ==================== 核心逻辑 ====================

    @Override
    public void onTick() {
        super.onTick();
        if (!isActive() || isDestroyed()) return;

        // ① 推进输送计时器
        Iterator<Map.Entry<IAmmoConsumer, Integer>> iter = deliveryTimers.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<IAmmoConsumer, Integer> entry = iter.next();
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                iter.remove();
                readyConsumers.add(entry.getKey());
            } else {
                entry.setValue(remaining);
            }
        }

        // ② 弹药再生
        if (attr.staticAttribute.isRegenRoundByRound()) {
            // 随打随产模式
            tickRegenRoundByRound();
        } else {
            // 批量产出模式
            tickBatchReload();
        }
    }

    /**
     * 随打随产模式：每 tick 累积再生进度，达到 1.0 时产出一发。
     */
    private void tickRegenRoundByRound() {
        float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
        if (regenPerMinute <= 0f) return;

        if (ammoCount < attr.staticAttribute.getMagazineCapacity()) {
            // 20 tick/s × 60 s = 1200 tick/min
            regenProgress += regenPerMinute / 1200f;

            while (regenProgress >= 1.0f && ammoCount < attr.staticAttribute.getMagazineCapacity()) {
                // 尝试消耗能量
                float energyCost = attr.staticAttribute.getEnergyCostPerRound();
                if (energyCost > 0f) {
                    if (!tryConsumeEnergy(energyCost)) {
                        break; // 能量不足，暂停再生
                    }
                }
                ammoCount++;
                regenProgress -= 1.0f;
            }
        }
    }

    /**
     * 批量产出模式：弹仓全空后触发冷却，冷却结束后一次性回满。
     */
    private void tickBatchReload() {
        if (ammoCount == 0 && !isBatchReloading) {
            // 触发批量再生
            float regenPerMinute = attr.staticAttribute.getRegenPerMinute();
            if (regenPerMinute <= 0f) return;

            // 扣除整批能量
            float totalEnergy = attr.staticAttribute.getMagazineCapacity() * attr.staticAttribute.getEnergyCostPerRound();
            if (totalEnergy > 0f) {
                if (!tryConsumeEnergy(totalEnergy)) {
                    return; // 能量不够整批，等下一 tick 再试
                }
            }

            // 计算冷却时间：弹仓容量 / 每分钟再生次数 × 1200 tick
            batchCooldownTicks = (int) (attr.staticAttribute.getMagazineCapacity() / regenPerMinute * 1200);
            if (batchCooldownTicks < 1) batchCooldownTicks = 1;
            isBatchReloading = true;
        }

        if (isBatchReloading) {
            batchCooldownTicks--;
            if (batchCooldownTicks <= 0) {
                isBatchReloading = false;
                ammoCount = attr.staticAttribute.getMagazineCapacity();
            }
        }
    }

    /**
     * 尝试从 EnergyGrid 消耗能量。
     *
     * @param amount 请求消耗的能量 (J)
     * @return true 表示实际消耗到了指定能量
     */
    private boolean tryConsumeEnergy(float amount) {
        EnergyGrid grid = getOwner().getEnergyGrid();
        if (grid == null) return false;
        float consumed = grid.consumeEnergy(amount);
        return consumed >= amount - 0.001f;
    }

    /**
     * 获取剩余容量。
     */
    private int getFreeCapacity() {
        return attr.staticAttribute.getMagazineCapacity() - ammoCount;
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
     * 下游消费者（Launcher 等）收到回调后通过 addSupplier() 注册此供给者。
     */
    protected void handShake() {
        for (String signalChannel : attr.discoveryOutputs.keySet()) {
            sendSignalToAllTargetsWithCallback(signalChannel, EmptySignal.INSTANCE, false);
        }
    }

    // ==================== 持久化 ====================

    @Override
    public void loadData(net.minecraft.nbt.CompoundTag data) {
        super.loadData(data);
        if (data.contains("ammo_count")) ammoCount = data.getInt("ammo_count");
        if (data.contains("regen_progress")) regenProgress = data.getFloat("regen_progress");
        if (data.contains("batch_cooldown")) batchCooldownTicks = data.getInt("batch_cooldown");
        if (data.contains("is_batch_reloading")) isBatchReloading = data.getBoolean("is_batch_reloading");
    }

    @Override
    public net.minecraft.nbt.CompoundTag saveData(net.minecraft.nbt.CompoundTag data) {
        super.saveData(data);
        data.putInt("ammo_count", ammoCount);
        data.putFloat("regen_progress", regenProgress);
        data.putInt("batch_cooldown", batchCooldownTicks);
        data.putBoolean("is_batch_reloading", isBatchReloading);
        return data;
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
        Map<String, List<String>> result = new HashMap<>(attr.discoveryOutputs);
        return result;
    }

    @Override
    public SignalResult onSignalUpdated(String channelName, ISignalSender sender) {
        // 弹药发现频道回调：供给者发现下游消费者并注册自身
        if (channelName.equals("callback") && sender instanceof IAmmoConsumer consumer) {
            if (consumer instanceof AbstractSubsystem sub) {
                if (sub.getOwner().getSubPart().getPart().assembly
                        != this.getOwner().getSubPart().getPart().assembly) {
                    return SignalResult.PASS;
                }
            }
            consumer.addSupplier(this);
            return SignalResult.CONSUME;
        }
        return super.onSignalUpdated(channelName, sender);
    }
}
