package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 弹药消费者接口。<br>
 * 发射器、下游装弹机等需要从供给者获取弹药的子系统实现此接口。
 * 兼容性校验由消费者在运行时逐发执行。
 */
public interface IAmmoConsumer {

    /**
     * 供给者摘要，供 HUD 一次性获取全部弹药信息。<br>
     * 字段值来自 {@link IAmmoSupplier} 自身报告的方法，不做 instanceof 分支。
     */
    record SupplierSummary(
            /** 弹种注册名，如 "machine_max:apfsds" */
            @Nullable ResourceLocation type,
            /** 当前弹药数量 */
            int remaining,
            /** 最大容量，-1 表示无上限 */
            int capacity,
            /** 是否为当前选中的供给者 */
            boolean isSelected,
            /** 供给者当前状态（由供给者自行报告） */
            IAmmoSupplier.SupplierStatus status,
            /** 状态数值（装填进度 0~1、再生进度 0~1 等） */
            float statusProgress
    ) {}

    /** 消费者当前是否可以接收弹药 */
    boolean canAcceptAmmo();

    /** 剩余弹药容量。-1 表示无容量限制 */
    int getFreeCapacity();

    /**
     * 接收一发弹药。<br>
     * 由供给者在完成装填后调用，将弹药存入消费者。
     *
     * @return true 表示成功接收
     */
    boolean receiveAmmo(ProjectileType type);

    /** 获取当前选中的供给者 */
    @Nullable IAmmoSupplier getCurrentSupplier();

    /** 获取所有已注册的供给者列表 */
    List<IAmmoSupplier> getSuppliers();

    /** 设置当前选中的供给者索引 */
    void setCurrentSupplier(int index);

    /**
     * 由供给者在握手中调用，将自身注册到消费者的可选供给者列表中。<br>
     * 若消费者此前没有供给者，自动选定此供给者。
     */
    void addSupplier(IAmmoSupplier supplier);

    /**
     * 判断此消费者是否可以接受指定弹药类型。<br>
     * 兼容性校验由消费者端在运行时逐发执行。
     */
    boolean canAccept(ProjectileType type);

    /**
     * 获取所有已连接供给者的摘要列表，供 HUD 直接使用。<br>
     * 数据全部来自 {@link IAmmoSupplier} 自身方法，不依赖具体实现类。
     *
     * @return 供给者摘要列表
     */
    default List<SupplierSummary> getSupplierSummaries() {
        List<SupplierSummary> result = new ArrayList<>();
        IAmmoSupplier selected = getCurrentSupplier();
        for (IAmmoSupplier supplier : getSuppliers()) {
            ProjectileType suppliedType = supplier.getSuppliedType();
            ResourceLocation typeKey = suppliedType != null ? suppliedType.getRegistryKey() : null;
            result.add(new SupplierSummary(
                    typeKey,
                    supplier.getRemainingCount(),
                    supplier.getCapacity(),
                    supplier == selected,
                    supplier.getStatus(this),
                    supplier.getReloadProgress(this)
            ));
        }
        return result;
    }
}
