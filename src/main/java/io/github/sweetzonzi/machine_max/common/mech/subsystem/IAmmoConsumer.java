package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 弹药消费者接口。<br>
 * 发射器等需要从供给者获取弹药的子系统实现此接口。
 * 兼容性校验由消费者在运行时逐发执行。
 */
public interface IAmmoConsumer {

    /**
     * 供给者摘要，供 HUD 一次性获取全部弹药信息。<br>
     * 字段值来自 {@link IAmmoSupplier} 自身报告的方法，不做 instanceof 分支。
     *
     * @deprecated WeaponController 接管弹药管理后，HUD 应从 Controller 的 {@code getAmmoPool()} 读取，
     * 不再需要此 record。保留以兼容过渡期代码。
     */
    @Deprecated
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

    /** 设置当前选中的供给者（直接引用，null 表示无供给者） */
    void setCurrentSupplier(@Nullable IAmmoSupplier supplier);

    /** 获取所有已注册的供给者列表 */
    List<IAmmoSupplier> getSuppliers();

    /**
     * 获取按频道分组的供给者映射。<br>
     * 频道名对应 LauncherStaticAttr.ammo_inputs 中声明的发现频道。
     * LinkedHashMap 保证迭代顺序与 ammo_inputs 一致。
     *
     * @return 频道名 → 该频道下的供给者列表
     */
    Map<String, List<IAmmoSupplier>> getSupplierChannels();

    /**
     * 由供给者在握手中调用，将自身注册到消费者的可选供给者列表中。<br>
     * 若消费者此前没有供给者，自动选定此供给者。
     * 默认实现转发到 {@link #addSupplier(IAmmoSupplier, String)}，频道名为 "unknown"。
     */
    default void addSupplier(IAmmoSupplier supplier) {
        addSupplier(supplier, "unknown");
    }

    /**
     * 带频道名的供给者注册。<br>
     * 频道名由 Loader 在 handshake 回调中传递，对应 Launcher 的 ammo_inputs 声明顺序。
     *
     * @param supplier    供给者实例
     * @param channelName 发现频道名，用于按频道分组
     */
    void addSupplier(IAmmoSupplier supplier, String channelName);

    /**
     * 判断此消费者是否可以接受指定弹药类型。<br>
     * 兼容性校验由消费者端在运行时逐发执行。
     */
    boolean canAccept(ProjectileType type);

    /**
     * 所有供给者摘要的包装记录。<br>
     * 替代裸 {@code List&lt;SupplierSummary&gt;}，提供便捷的聚合查询方法。<br>
     * {@code totalByType} 在构造时一次性计算并缓存，避免高频查询重复迭代。
     *
     * @deprecated WeaponController 接管弹药管理后，HUD 应从 Controller 读取聚合视图。
     */
    @Deprecated
    record SupplierSummaries(
            List<SupplierSummary> summaries,
            Map<ResourceLocation, Integer> totalByType
    ) {

        /**
         * 便捷构造：仅传入摘要列表，自动计算按弹种汇总余量。
         */
        SupplierSummaries(List<SupplierSummary> summaries) {
            this(summaries, computeTotalByType(summaries));
        }

        /** 计算按弹种汇总余量 */
        private static Map<ResourceLocation, Integer> computeTotalByType(List<SupplierSummary> summaries) {
            if (summaries.isEmpty()) return Map.of();
            Map<ResourceLocation, Integer> map = new HashMap<>();
            for (SupplierSummary s : summaries) {
                if (s.type != null) {
                    map.merge(s.type, s.remaining, Integer::sum);
                }
            }
            return Map.copyOf(map);
        }

        /** 获取当前选中的供给者，无选中返回 null */
        @Nullable
        public SupplierSummary getSelected() {
            for (SupplierSummary s : summaries) {
                if (s.isSelected) return s;
            }
            return null;
        }

        /** 当前选中供给者的状态，无选中返回 EMPTY */
        public IAmmoSupplier.SupplierStatus getSelectedStatus() {
            SupplierSummary s = getSelected();
            return s != null ? s.status : IAmmoSupplier.SupplierStatus.EMPTY;
        }

        /** 某弹种的总余量（从缓存 map 直接查） */
        public int getTotalOf(ResourceLocation type) {
            return totalByType.getOrDefault(type, 0);
        }

        /** 是否有多于一个供给者（需要显示切换 UI） */
        public boolean hasMultiple() {
            return summaries.size() > 1;
        }
    }

    /**
     * 获取所有已连接供给者的摘要列表，供 HUD 直接使用。<br>
     * 数据全部来自 {@link IAmmoSupplier} 自身方法，不依赖具体实现类。
     *
     * @deprecated WeaponController 接管弹药管理后不再需要，保留以兼容过渡期代码。
     * @return 供给者摘要包装
     */
    @Deprecated
    default SupplierSummaries getSupplierSummaries() {
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
        return new SupplierSummaries(result);
    }
}
