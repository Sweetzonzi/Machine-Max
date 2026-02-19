package io.github.sweetzonzi.machine_max.util.control;

import io.github.sweetzonzi.machine_max.MachineMax;
import lombok.Getter;
import lombok.Setter;

/**
 * 一个简单的PD控制器，用于实现对各个可动部件的控制
 * <p>
 * 相比于PID控制器，PD控制器省略了积分项，适用于不需要消除稳态误差的场景
 *
 * @author 甜粽子
 */
@Getter
@Setter
public class PDController {
    private double P;               // 比例系数
    private double D;               // 微分系数
    private double error;           // 实时误差
    private double errorLastFrame;  // 前一次迭代的误差
    private double errorSpeed;      // 误差变化率
    private double STEP;           // 控制器的运行步长

    /**
     * 创建一个PD控制器
     *
     * @param p    比例系数：误差越大，控制量越大
     * @param d    微分系数：误差变化速度越大，控制量越大（提供阻尼效果）
     * @param step 控制器运行步长，通常与物理计算步长相同
     */
    public PDController(double p, double d, double step) {
        this.adjust(p, d, step);
        this.resetError();
    }

    /**
     * 调整PD控制器的参数
     *
     * @param p    比例系数：误差越大，控制量越大
     * @param d    微分系数：误差变化速度越大，控制量越大
     * @param step 控制器运行步长，通常与物理计算步长相同
     */
    public void adjust(double p, double d, double step) {
        this.P = p;
        this.D = d;
        if (step <= 0) {
            this.STEP = 0.1;
            MachineMax.LOGGER.error("PD controller's time step must be greater than 0!");
        } else {
            this.STEP = step;
        }
    }

    /**
     * 重设此PD控制器的记录误差
     */
    public void resetError() {
        this.error = 0;
        this.errorSpeed = 0;
        this.errorLastFrame = 0;
    }

    /**
     * 对于给定的被控量，控制器会根据和目标的差距给出一个控制量以尝试修正被控量的值
     * <p>
     * 此方法应随时间推进反复被调用
     *
     * @param target 被控量目标值
     * @param actual 被控量实际值
     * @return 控制量输出
     */
    public double step(double target, double actual) {
        this.error = actual - target;  // 计算当前误差

        // 计算误差变化率（微分项）
        if (this.STEP > 0) {
            this.errorSpeed = (this.error - this.errorLastFrame) / this.STEP;
        } else {
            this.errorSpeed = 0;
        }

        // P项 + D项
        double output = this.P * error + this.D * errorSpeed;

        // 更新上一帧误差
        this.errorLastFrame = this.error;

        return output;
    }

    /**
     * 设置时间步长
     */
    public void setStep(double step) {
        if (step <= 0) {
            this.STEP = 0.1;
            MachineMax.LOGGER.error("PD controller's time step must be greater than 0!");
        } else {
            this.STEP = step;
        }
    }

    /**
     * 计算控制量但不更新内部状态（可用于预测或特殊情况）
     */
    public double calculate(double target, double actual) {
        double currentError = actual - target;
        double currentErrorSpeed = (currentError - this.errorLastFrame) / this.STEP;
        return (this.P * currentError + this.D * currentErrorSpeed) * this.STEP;
    }
}