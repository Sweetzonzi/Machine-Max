package io.github.sweetzonzi.machine_max.common.mech.vehicle.event;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.VehicleCore;
import lombok.Getter;

/**
 * 装配体拓扑发生变化（部件增删、连接建立/断开、合并、分裂）。
 *
 * <p>由 {@code VehicleCore.onStructureChanged(...)} 在质量、最大耐久、相机距离与子系统握手
 * 全部更新之后、并且只在双端都发布——客户端也订阅它，所以专用服务器上客户端同样能收到。
 * 订阅方应只置脏、在下一个 tick 再重算，避免在子系统握手与物理激活中途递归进来。</p>
 */
@Getter
public class VehicleStructureChangeEvent extends VehicleEvent {

    /** 触发拓扑变化的具体场景 */
    public enum Reason {
        /** 新部件装入同一装配体 */
        ADD,
        /** 已有部件之间新建连接 */
        ATTACH,
        /** 部件被移除 */
        REMOVE,
        /** 两台载具合并 */
        MERGE,
        /** 连接被断开 */
        DETACH,
        /** 分裂出的新载具（构造器内） */
        SPLIT
    }

    private final Reason reason;

    public VehicleStructureChangeEvent(VehicleCore vehicle, Reason reason) {
        super(vehicle);
        this.reason = reason;
    }
}
