package io.github.sweetzonzi.machine_max.common.mech.subsystem;

import io.github.sweetzonzi.machine_max.common.mech.projectile.ProjectileType;
import org.jetbrains.annotations.Nullable;

/**
 * 弹药供给者接口。<br>
 * 供给者负责管理弹药储备、装填计时和弹药交付。
 * 消费者调用 requestRound → 等待 isRoundReady → consumeReadyRound 三步完成弹药获取。
 */
public interface IAmmoSupplier {

    /** 当前是否有可用弹药 */
    boolean hasAmmo();

    /** 剩余弹药计数。-1 表示无计数概念（如再生装弹机） */
    int getRemainingCount();

    /** 当前将提供的弹药类型，null = 未准备好 */
    @Nullable ProjectileType getSuppliedType();

    /** 是否为逐发装填模式 */
    boolean isRoundByRound();

    /** 装填耗时（tick） */
    int getReloadTimeTicks();

    /** 是否允许多个消费者同时等待装填 */
    boolean canSupplyMultiple();

    /** HUD 弹种标签 */
    String getLabel();

    /**
     * 请求一发弹药，启动非阻塞装填计时器。<br>
     * 幂等性：同一 consumer 多次调用不会创建重复计时器。
     *
     * @return true 表示请求已接受（首次请求或已在装填中）
     */
    boolean requestRound(IAmmoConsumer consumer);

    /** 指定消费者的弹药是否已就绪 */
    boolean isRoundReady(IAmmoConsumer consumer);

    /**
     * 消费已就绪的弹药。<br>
     * 仅在 {@link #isRoundReady(IAmmoConsumer)} 返回 true 时调用。
     *
     * @return 弹药类型，若未就绪则返回 null
     */
    @Nullable ProjectileType consumeReadyRound(IAmmoConsumer consumer);

    /** 是否支持退弹（接收弹药归还） */
    boolean canEject();

    /** 归还一发弹药给供给者（退弹用） */
    void returnRound(ProjectileType type);
}
