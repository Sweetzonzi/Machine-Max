package io.github.sweetzonzi.machine_max.common.mech.signal;

/**
 * 单目标信号处理结果。<p>
 * 由 {@link ISignalSender#sendSignalToTarget(String, String, Object)} 返回，
 * 发送者据此控制迭代行为；是否继续遍历由调用处解释。<p>
 * 语义对齐 {@code net.minecraft.world.InteractionResult} 的 CONSUME / PASS / FAIL。
 */
public enum SignalResult {
    /** 信号已被目标消费/处理，发送方可终止遍历 */
    CONSUME,
    /** 目标不处理此信号，发送方可继续尝试下一个目标 */
    PASS,
    /** 目标应处理但当前无法处理（如座位已被占用）；交互区会继续尝试下一个目标，其他调用处自行决定 */
    FAIL
}
