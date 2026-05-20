package io.github.sweetzonzi.machine_max.common.mech.energy;

/**
 * 电力消费者接口——实现此接口的子系统可从电网取电
 */
public interface IEnergyConsumer {
    /**
     * 当前需求功率 (W)
     */
    float getPowerDemand();

    /**
     * 电网实际供给的功率 (W)，由电网每 tick 结算后调用
     */
    void onPowerSupplied(float watts);

    /**
     * 优先级，0.0~1.0，越大越优先满足。默认 1.0
     */
    default float getPriority() { return 1.0f; }

    /** 是否在电网中处于激活状态 */
    default boolean isElectricActive() { return true; }
}
