package io.github.sweetzonzi.machinemax.common.vehicle.subsystem;

import io.github.sweetzonzi.machinemax.common.vehicle.ISubsystemHost;

/**
 * 动力子系统接口，标识能够提供动力的子系统
 */
public interface IPowerSourceSubsystem {

    /**
     * 获取当前转速
     * @return 当前转速(rad/s)
     */
    double getRotSpeed();

    /**
     * 获取最大转速
     * @return 最大转速(rad/s)
     */
    double getMaxRotSpeed();

    /**
     * 获取当前输出功率
     * @return 当前输出功率(W)
     */
    double getOutputPower();

    /**
     * 获取最大输出功率
     * @return 最大输出功率(W)
     */
    double getMaxPower();

    /**
     * 获取当前油门输入
     * @return 当前油门输入(0~1)
     */
    double getThrottleInput();

    /**
     * 设置油门输入
     * @param throttle 油门输入(0~1)
     */
    void setThrottleInput(double throttle);

    /**
     * 获取子系统宿主
     * @return 子系统宿主
     */
    ISubsystemHost getHost();

    /**
     * 判断动力源是否激活
     * @return 是否激活
     */
    boolean isActive();
}
