package io.github.sweetzonzi.machine_max.common.mech.energy;

/**
 * 电力储存者接口——同时是电力生产者和消费者，可充放电
 */
public interface IEnergyStorage extends IEnergyProducer, IEnergyConsumer {
    /**
     * 当前储存能量 (J)
     */
    float getStoredEnergy();

    /**
     * 最大储存容量 (J)
     */
    float getMaxStoredEnergy();

    /**
     * 最大充电功率 (W)
     */
    float getMaxChargeRate();

    /**
     * 最大放电功率 (W)
     */
    float getMaxDischargeRate();

    /**
     * 电网告知本 tick 储能变化量
     *
     * @param deltaEnergy 储存能量变化量 (J)，正为充电，负为放电
     */
    void onEnergyStored(float deltaEnergy);

    /**
     * 解决两个父接口的默认方法冲突
     */
    @Override
    default boolean isElectricActive() {
        return IEnergyProducer.super.isElectricActive();
    }
}
