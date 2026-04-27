package io.github.sweetzonzi.machine_max.common.vehicle.energy;

/**
 * 电力生产者接口——实现此接口的子系统可向电网供电
 */
public interface IEnergyProducer {
    /**
     * 额定产能 (W)，电网可能实际调用低于此值的功率
     */
    float getProductionCapacity();

    /**
     * 电网告知本 tick 实际输出功率
     *
     * @param watts 实际输出的功率 (W)
     */
    void onPowerProduced(float watts);

    /** 是否在电网中处于激活状态 */
    default boolean isElectricActive() { return true; }
}
