package io.github.sweetzonzi.machine_max.common.mech.energy;

/**
 * 机械功率传递的载荷。
 * <p>
 * 约定 {@code power = τ × |speed|}：接收端用 {@code power / |speed|} 还原出该级应施加的扭矩。
 * {@code speed} 是该级的转速(rad/s，有符号)：动力源给出自身实际转速，
 * 传动链（变速箱、传动装置）给出折算到该输出端的目标轴转速。
 * 接收端在有限扭矩下追随此目标。末端牵引补偿以该目标为基点，只用于物理电机，不进入功率载荷。
 *
 * @param power 传递功率(W)，正代表沿 speed 方向驱动，负代表反向
 * @param speed 接收端应追随的轴转速(rad/s)，有符号
 */
public record MechPower(float power, float speed) {
    public static final MechPower ZERO = new MechPower(0, 0);
    public static final MechPower EMPTY = new MechPower(Float.NaN, Float.NaN);

    public boolean isEmpty() {
        return Float.isNaN(power) || Float.isNaN(speed);
    }
}
