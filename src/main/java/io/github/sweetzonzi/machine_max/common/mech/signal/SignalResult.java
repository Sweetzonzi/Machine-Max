package io.github.sweetzonzi.machine_max.common.mech.signal;

/**
 * 单目标信号处理结果。<p>
 * 由 {@link ISignalSender#sendSignalToTarget(String, String, Object)} 返回，
 * 发送者可据此控制迭代行为。<p>
 * 语义对齐 {@code net.minecraft.world.InteractionResult} 的 CONSUME / PASS / FAIL。
 */
public enum SignalResult {
    /** 信号已被目标消费/处理，发送方可终止遍历 */
    CONSUME,
    /** 目标不处理此信号，发送方可继续尝试下一个目标 */
    PASS,
    /** 目标应处理但失败（如状态不满足），发送方可终止遍历 */
    FAIL
}
