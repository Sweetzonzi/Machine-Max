package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import org.jetbrains.annotations.Nullable;

/**
 * 模块化子系统契约。
 * <p>
 * 实现此接口的子系统将其耐久度拆分为多个可独立受损的 SubModule。
 * ModularSubsystem 提供默认实现。
 */
public interface IModularSubsystem {
    /**
     * 将伤害路由到指定模块（或按比例分摊）。
     *
     * @param moduleName 目标模块名，null 或空表示按比例分摊到所有模块
     * @param amount     伤害量
     */
    void routeDamage(@Nullable String moduleName, float amount);

    /**
     * 将修复路由到指定模块（或按比例分摊）。
     *
     * @param moduleName 目标模块名，null 或空表示按受损比例分摊到所有模块
     * @param amount     修复量
     */
    void routeRepair(@Nullable String moduleName, float amount);

    /** 获取指定名称的模块，不存在返回 null。 */
    @Nullable SubModule getModule(String name);

    /** 所有模块的聚合当前耐久度。 */
    float getAggregatedDurability();

    /** 所有模块的聚合最大耐久度。 */
    float getAggregatedMaxDurability();

    /** 是否存在至少一个 critical 模块被摧毁。 */
    boolean isModularDestroyed();
}
