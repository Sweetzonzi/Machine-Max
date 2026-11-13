package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * 弹药消费者接口。<br>
 * 发射器等需要从供给者获取弹药的子系统实现此接口。
 * 交付单位是一份 {@code AmmoBelt}，兼容性校验由消费者在拉到手之后按整份内容执行。
 */
public interface IAmmoConsumer {

    /** 获取当前选中的供给者 */
    @Nullable IAmmoSupplier getCurrentSupplier();

    /**
     * 设置当前选中的供给者（直接引用，null 表示无供给者）。<br>
     * 由服务端的弹药路由调用；客户端不调用，它的指认来自服务端同步过来的供给者身份。
     */
    void setCurrentSupplier(@Nullable IAmmoSupplier supplier);

    /** 获取所有已注册的供给者列表 */
    List<IAmmoSupplier> getSuppliers();

    /**
     * 获取按频道分组的供给者映射。<br>
     * 频道名是查询命中的到达频道，来自本消费者 {@code ammo_discovery_targets} 里的查询键。
     * LinkedHashMap 保证迭代顺序与查询顺序一致。
     *
     * @return 频道名 → 该频道下的供给者列表
     */
    Map<String, List<IAmmoSupplier>> getSupplierChannels();

    /**
     * 由消费者在按 {@code ammo_discovery_targets} 发现时调用，把命中的供给者登记进可选列表。<br>
     * 默认实现转发到 {@link #addSupplier(IAmmoSupplier, String)}，频道名为 "unknown"。
     */
    default void addSupplier(IAmmoSupplier supplier) {
        addSupplier(supplier, "unknown");
    }

    /**
     * 带频道名的供给者注册。<br>
     * 频道名是本次只读发现命中的到达频道（可经连接器转译），由发起查询的消费者传入。
     *
     * @param supplier    供给者实例
     * @param channelName 到达频道名，用于按频道分组
     */
    void addSupplier(IAmmoSupplier supplier, String channelName);

    /**
     * 判断此消费者是否可以接受指定弹药类型。<br>
     * 兼容性校验由消费者端在运行时逐发执行。
     */
    boolean canAccept(ProjectileType type);
}
