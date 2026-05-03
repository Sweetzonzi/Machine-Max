package io.github.sweetzonzi.machine_max.common.vehicle.subsystem;

/**
 * 扭矩提供者接口，用于查询动力源在指定转速下能输出的最大扭矩。<p>
 * 引擎和电动机均实现此接口，供控制器（如变速箱自动换挡逻辑）统一查询。
 */
public interface ITorqueProvider {

    /**
     * 查询指定转速下该动力源能提供的最大扭矩
     *
     * @param rotSpeed 转速 (rad/s)
     * @return 最大扭矩 (N·m)
     */
    double getTorqueAtSpeed(double rotSpeed);
}
