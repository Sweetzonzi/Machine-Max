package io.github.sweetzonzi.machine_max.common.mech.vehicle.energy;

import io.github.sweetzonzi.machine_max.common.mech.subsystem.AbstractSubsystem;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 全载具共享的电力能量网——总线模式
 *
 * <p>各子系统将自己挂载到电网上：生产者向电网供电，消费者从电网取电，储存者双向参与。
 * 整个电网的供需在一个 tick 内自动平衡，UGC 作者无需手动配置任何传播路径。</p>
 */
public class EnergyGrid {

    // ── 注册管理 ──
    private final Set<IEnergyProducer> producers = new CopyOnWriteArraySet<>();
    private final Set<IEnergyConsumer> consumers = new CopyOnWriteArraySet<>();

    // ── 电网当前状态 ──
    @Getter
    private float totalProduction = 0;       // 当前总发电功率 (W)
    @Getter
    private float totalConsumption = 0;      // 当前总耗电功率 (W)
    @Getter
    private float totalStoredEnergy = 0;     // 电网总储能 (J)
    @Getter
    private float maxStoredEnergy = 0;       // 电网总储能上限 (J)

    // ── 各消费者满足率 ──
    private final ConcurrentMap<IEnergyConsumer, Float> supplyRatios = new ConcurrentHashMap<>();

    // ── 注册与注销 ──

    public void registerProducer(IEnergyProducer producer) {
        producers.add(producer);
    }

    public void registerConsumer(IEnergyConsumer consumer) {
        consumers.add(consumer);
        if (consumer instanceof IEnergyStorage) {
            // 储存者同时是生产者，但它通过在 demand/capacity 中体现充放电来参与电网
            // 不另外注册 producer，避免重复
        }
    }

    public void unregisterProducer(IEnergyProducer producer) {
        producers.remove(producer);
    }

    public void unregisterConsumer(IEnergyConsumer consumer) {
        consumers.remove(consumer);
        supplyRatios.remove(consumer);
    }

    public void rebuildFrom(Collection<AbstractSubsystem> subsystems) {
        producers.clear();
        consumers.clear();
        supplyRatios.clear();

        for (AbstractSubsystem sub : subsystems) {
            if (sub instanceof IEnergyProducer p && p.isElectricActive()) {
                producers.add(p);
            }
            if (sub instanceof IEnergyConsumer c && c.isElectricActive()) {
                consumers.add(c);
            }
        }
    }

    // ── 电网状态查询 ──

    /** 电力是否充足（总产能 >= 总需求） */
    public boolean isPowerSufficient() {
        return totalProduction >= totalConsumption;
    }

    /**
     * 查询某个消费者的电力满足率
     *
     * @return 0.0~1.0，表示需求被满足的比例
     */
    public float getSupplyRatio(IEnergyConsumer consumer) {
        return supplyRatios.getOrDefault(consumer, 0f);
    }

    /**
     * 查询某个消费者的当前剩余储能比例
     *
     * @return 0.0~1.0
     */
    public float getStoredRatio(IEnergyStorage storage) {
        float max = storage.getMaxStoredEnergy();
        if (max <= 0) return 0;
        return storage.getStoredEnergy() / max;
    }

    /**
     * 查询当前立即可用的储能总量 (J)，用于瞬时用电（如武器开火）
     *
     * @return 所有储存者当前储能之和
     */
    public float getAvailableEnergy() {
        float total = 0;
        for (IEnergyConsumer c : consumers) {
            if (c instanceof IEnergyStorage s && s.isElectricActive()) {
                total += s.getStoredEnergy();
            }
        }
        return total;
    }

    /**
     * 立即从储能中扣减指定能量，用于瞬时用电（如武器开火）
     *
     * <p>按各储存者当前储能比例分摊扣减。此方法可在任意时候调用（如 {@code onTick}），
     * 不依赖每 tick 的电网结算。</p>
     *
     * @param requested 请求消耗的能量 (J)
     * @return 实际消耗的能量 (J)，可能少于请求值（储能不足时）
     */
    public float consumeEnergy(float requested) {
        if (requested <= 0) return 0;

        // 收集所有激活且有储能的储存者
        List<IEnergyStorage> storages = new ArrayList<>();
        float totalStored = 0;
        for (IEnergyConsumer c : consumers) {
            if (c instanceof IEnergyStorage s && s.isElectricActive()) {
                float stored = s.getStoredEnergy();
                if (stored > 0) {
                    storages.add(s);
                    totalStored += stored;
                }
            }
        }

        if (totalStored <= 0) return 0;

        float remaining = requested;
        for (int i = 0; i < storages.size(); i++) {
            IEnergyStorage s = storages.get(i);
            float stored = s.getStoredEnergy();
            if (stored <= 0) continue;

            // 按储能比例分摊，最后一个储存者承担所有余量
            float take;
            if (i == storages.size() - 1) {
                take = Math.min(stored, remaining);
            } else {
                float proportion = stored / totalStored;
                take = Math.min(stored, remaining * proportion);
            }
            s.onEnergyStored(-take);
            remaining -= take;
            if (remaining <= 0) break;
        }

        return requested - remaining;
    }

    /**
     * 为电网补充指定焦耳的能量，按各储存者剩余容量比例分配
     *
     * <p>用于外部能量源（如太阳能板、创造模式调试），不依赖每 tick 的电网结算。
     * 补充能量不受储存者充电功率限制，但受最大容量限制。</p>
     *
     * @param energy 要补充的能量 (J)
     * @return 实际补充的能量 (J)，可能少于请求值（所有储存者都满时）
     */
    public float addEnergy(float energy) {
        if (energy <= 0) return 0;

        // 收集所有激活的储存者，计算剩余可充容量
        List<IEnergyStorage> storages = new ArrayList<>();
        float totalRemainingCapacity = 0;
        for (IEnergyConsumer c : consumers) {
            if (c instanceof IEnergyStorage s && s.isElectricActive()) {
                float remaining = s.getMaxStoredEnergy() - s.getStoredEnergy();
                if (remaining > 0) {
                    storages.add(s);
                    totalRemainingCapacity += remaining;
                }
            }
        }

        if (totalRemainingCapacity <= 0) return 0;

        float remaining = Math.min(energy, totalRemainingCapacity);
        float added = 0;
        for (int i = 0; i < storages.size(); i++) {
            IEnergyStorage s = storages.get(i);
            float capacityRemaining = s.getMaxStoredEnergy() - s.getStoredEnergy();
            if (capacityRemaining <= 0) continue;

            float take;
            if (i == storages.size() - 1) {
                take = Math.min(capacityRemaining, remaining);
            } else {
                float proportion = capacityRemaining / totalRemainingCapacity;
                take = Math.min(capacityRemaining, remaining * proportion);
            }
            s.onEnergyStored(take);
            added += take;
            remaining -= take;
            if (remaining <= 0) break;
        }

        return added;
    }

    // ── 每 tick 结算 ──

    /**
     * 每物理 tick 结算一次电力供需
     *
     * @param tps 每秒物理 tick 数
     */
    public void prePhysicsTick(float tps) {
        if (tps <= 0) return;

        // 第一步：收集所有有效参与者的需求与产能
        List<IEnergyConsumer> activeConsumers = new ArrayList<>();
        float totalContinuousDemand = 0;

        List<IEnergyProducer> activeProducers = new ArrayList<>();
        float totalProdCapacity = 0;

        // 储存者单独分组：作为消费者（充电）和生产者（放电）
        List<IEnergyStorage> chargingStorages = new ArrayList<>();
        float totalChargeDemand = 0;
        List<IEnergyStorage> dischargingStorages = new ArrayList<>();
        float totalDischargeCapacity = 0;

        for (IEnergyConsumer c : consumers) {
            if (!c.isElectricActive()) continue;

            if (c instanceof IEnergyStorage storage) {
                // 储存者根据当前储能状态决定充放电
                float ratio = storage.getStoredEnergy() / Math.max(storage.getMaxStoredEnergy(), 1f);
                if (ratio < 0.99f) {
                    // 需要充电，作为消费者参与
                    float chargeCapacity = storage.getMaxChargeRate();
                    if (chargeCapacity > 0) {
                        chargingStorages.add(storage);
                        totalChargeDemand += chargeCapacity;
                    }
                }
                // 放电能力始终可用
                float dischargeRate = storage.getMaxDischargeRate();
                if (dischargeRate > 0 && storage.getStoredEnergy() > 0) {
                    dischargingStorages.add(storage);
                    totalDischargeCapacity += dischargeRate;
                }
            } else {
                float demand = c.getPowerDemand();
                if (demand > 0) {
                    activeConsumers.add(c);
                    totalContinuousDemand += demand;
                }
            }
        }

        for (IEnergyProducer p : producers) {
            if (!p.isElectricActive()) continue;
            if (p instanceof IEnergyStorage) {
                // 储存者作为生产者的容量已在 dischargingStorages 中计算
                continue;
            }
            float capacity = p.getProductionCapacity();
            if (capacity > 0) {
                activeProducers.add(p);
                totalProdCapacity += capacity;
            }
        }

        // 第二步：分配电力
        // 确定生产者需要产出多少——恰好满足消费者 + 尽可能充电，但不超过自身产能
        float productionNeeded = Math.min(totalProdCapacity, totalContinuousDemand + totalChargeDemand);
        float productionDelivered = 0;

        if (totalProdCapacity > 0) {
            // 按产能比例分摊生产量
            float remainingProduction = productionNeeded;
            for (int i = 0; i < activeProducers.size(); i++) {
                IEnergyProducer p = activeProducers.get(i);
                float capacity = p.getProductionCapacity();
                float actual;
                if (i == activeProducers.size() - 1) {
                    actual = Math.min(capacity, remainingProduction);
                } else {
                    float proportion = capacity / totalProdCapacity;
                    actual = Math.min(capacity, productionNeeded * proportion);
                }
                p.onPowerProduced(actual);
                productionDelivered += actual;
                remainingProduction -= actual;
            }
        }

        // 先满足消费者（持续需求），电力可能来自生产者，不足部分由储能补充
        float consumerFulfillable = productionDelivered + totalDischargeCapacity;
        float consumerDemand = totalContinuousDemand;

        if (consumerFulfillable >= consumerDemand) {
            // ── 消费者需求可完全满足 ──
            for (IEnergyConsumer c : activeConsumers) {
                float demand = c.getPowerDemand();
                c.onPowerSupplied(demand);
                supplyRatios.put(c, 1f);
            }
            // 不足部分从储能放电
            float shortfall = Math.max(0, consumerDemand - productionDelivered);
            distributeDischarge(shortfall, dischargingStorages, totalDischargeCapacity, tps);
            // 剩余产能给电池充电
            float chargeAvailable = Math.max(0, productionDelivered - consumerDemand);
            distributeCharge(chargeAvailable, chargingStorages, totalChargeDemand, tps);

            this.totalProduction = productionDelivered;
            this.totalConsumption = consumerDemand + Math.min(chargeAvailable, totalChargeDemand);
        } else {
            // ── 消费者需求无法完全满足 ──
            // 储能全力放电
            float dischargeUsed = distributeDischarge(totalDischargeCapacity, dischargingStorages, totalDischargeCapacity, tps);
            float totalAvailable = productionDelivered + dischargeUsed;

            // 按优先级削减
            activeConsumers.sort((a, b) -> Float.compare(b.getPriority(), a.getPriority()));
            float remaining = totalAvailable;
            for (int i = 0; i < activeConsumers.size(); i++) {
                IEnergyConsumer c = activeConsumers.get(i);
                float demand = c.getPowerDemand();
                if (remaining <= 0) {
                    c.onPowerSupplied(0);
                    supplyRatios.put(c, 0f);
                } else if (i == activeConsumers.size() - 1) {
                    float allocated = Math.min(demand, remaining);
                    c.onPowerSupplied(allocated);
                    supplyRatios.put(c, demand > 0 ? allocated / demand : 1f);
                    remaining -= allocated;
                } else {
                    float allocated = Math.min(demand, remaining * 0.5f);
                    c.onPowerSupplied(allocated);
                    supplyRatios.put(c, demand > 0 ? allocated / demand : 1f);
                    remaining -= allocated;
                }
            }

            // 电力不足，无法充电
            for (IEnergyStorage s : chargingStorages) {
                s.onEnergyStored(0);
            }

            this.totalProduction = productionDelivered + dischargeUsed;
            this.totalConsumption = this.totalProduction;
        }

        // 更新电网状态快照
        updateStoredSnapshot();
    }

    // ── 内部辅助 ──

    private void updateStoredSnapshot() {
        totalStoredEnergy = 0;
        maxStoredEnergy = 0;
        for (IEnergyConsumer c : consumers) {
            if (c instanceof IEnergyStorage s) {
                totalStoredEnergy += s.getStoredEnergy();
                maxStoredEnergy += s.getMaxStoredEnergy();
            }
        }
    }

    /**
     * 按比例从储存者放电
     *
     * @param requested  请求的放电功率 (W)
     * @param storages   放电候选储存者
     * @return 实际放电功率
     */
    private float distributeDischarge(float requested, List<IEnergyStorage> storages, float totalCapacity, float tps) {
        if (requested <= 0 || storages.isEmpty() || totalCapacity <= 0) return 0;
        float remaining = requested;
        for (int i = 0; i < storages.size(); i++) {
            IEnergyStorage s = storages.get(i);
            float rate = Math.min(s.getMaxDischargeRate(), s.getStoredEnergy() * tps);
            if (rate <= 0) continue;
            float ratio = totalCapacity > 0 ? rate / totalCapacity : 0;
            float actual = Math.min(rate, remaining * ratio);
            float deltaEnergy = -actual / tps;
            if (-deltaEnergy > s.getStoredEnergy()) {
                deltaEnergy = -s.getStoredEnergy();
                actual = s.getStoredEnergy() * tps;
            }
            s.onEnergyStored(deltaEnergy);
            remaining -= actual;
            if (remaining <= 0) break;
        }
        return requested - remaining;
    }

    /**
     * 按比例给储存者充电
     *
     * @param available 可用于充电的功率 (W)
     * @param storages  充电候选储存者
     */
    private void distributeCharge(float available, List<IEnergyStorage> storages, float totalDemand, float tps) {
        if (available <= 0 || storages.isEmpty() || totalDemand <= 0) return;
        float remaining = available;
        for (int i = 0; i < storages.size(); i++) {
            IEnergyStorage s = storages.get(i);
            float wanted = s.getMaxChargeRate();
            float ratio = totalDemand > 0 ? wanted / totalDemand : 0;
            float actual = Math.min(wanted, remaining * ratio);
            float deltaEnergy = actual / tps;
            float maxDelta = s.getMaxStoredEnergy() - s.getStoredEnergy();
            deltaEnergy = Math.min(deltaEnergy, maxDelta);
            if (deltaEnergy > 0) {
                s.onEnergyStored(deltaEnergy);
                remaining -= actual;
            }
            if (remaining <= 0) break;
        }
    }
}
