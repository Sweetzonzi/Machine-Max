package io.github.sweetzonzi.machine_max.common.mech.subsystem.seat;

/**
 * 换座请求的结局。
 * <p>服务端在 {@link #OK} 之外给出第一个不满足的校验项，客户端据此提示玩家。
 * 除 {@link #OK} 与 {@link #MOUNT_FAILED} 之外的原因都表示没有任何状态被改动。</p>
 */
public enum SeatSwitchReason {
    /** 换座完成，座位与骑乘关系都已切换 */
    OK,
    /** 发起者当前不在任何座位的控制下 */
    NOT_SEATED,
    /** 目标就是发起者当前的座位 */
    SAME_SEAT,
    /** 目标座位已有乘客 */
    OCCUPIED,
    /** 目标座位或其所在子零件已毁坏，或子系统未激活 */
    UNAVAILABLE,
    /** 目标座位与当前座位不在同一个装配体内 */
    DIFFERENT_ASSEMBLY,
    /** 目标座位所在子零件没有可骑乘的实体 */
    NO_ENTITY,
    /** 跨实体换座时原版挂载被拒 */
    MOUNT_FAILED,
    /** 同一玩家在节流窗口内重复请求 */
    RATE_LIMITED
}
