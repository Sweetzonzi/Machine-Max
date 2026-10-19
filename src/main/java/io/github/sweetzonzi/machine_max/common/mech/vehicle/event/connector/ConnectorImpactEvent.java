package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.connector;

import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.connector.AbstractConnector;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart.SubPartEvent;
import io.github.sweetzonzi.machine_max.common.mech.vehicle.interact.HitBox;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * 一次命中把冲击分配到连接点这一动作的事件。
 * <p>
 * 冲击与部件伤害是两条独立链路：冲击量取自命中上下文中的<b>穿甲前原始伤害</b>
 * （{@link BFDamageContext#baseDamage()}），因此未击穿、跳弹的命中同样会产生冲击；
 * 部件伤害被子系统拦截或被 {@code SubPartDamageEvent.Pre} 取消，也不会阻止冲击。
 * 要阻止某次命中扰动结构，应取消 {@link Pre}。
 * </p>
 * <p>
 * <b>调用线程：</b>命中发生线程（投射物命中为物理线程），
 * 与 {@code SubPartDamageEvent.Pre} 相同。监听方不得在此直接操作物理体。
 * </p>
 */
@Getter
public abstract class ConnectorImpactEvent extends SubPartEvent {
    private final BFDamageContext ctx;
    @Nullable
    private final HitBox hitBox;
    /** 本次命中产生的总冲击量（尚未按距离分配，也尚未经过连接点自身的减免与倍率） */
    private final float impact;

    protected ConnectorImpactEvent(
            @NotNull SubPart subPart,
            @NotNull BFDamageContext ctx,
            @Nullable HitBox hitBox,
            float impact
    ) {
        super(subPart);
        this.ctx = ctx;
        this.hitBox = hitBox;
        this.impact = impact;
    }

    /**
     * 冲击分配前触发。取消后本次命中不再向任何连接点施加冲击，{@link Post} 也不会触发。
     */
    public static class Pre extends ConnectorImpactEvent implements ICancellableEvent {
        public Pre(
                @NotNull SubPart subPart,
                @NotNull BFDamageContext ctx,
                @Nullable HitBox hitBox,
                float impact
        ) {
            super(subPart, ctx, hitBox, impact);
        }
    }

    /**
     * 冲击已分配并进入各连接点的待处理队列后触发。
     * <p>
     * 此时只完成了按距离分配；连接点自身的冲击减免、倍率、完整性扣减与击落判定
     * 仍会在主线程的 {@code mcTick} 中发生。
     * </p>
     */
    public static class Post extends ConnectorImpactEvent {
        /** 连接点 → 本次分配到的冲击量（只读视图，仅在事件回调期间有效） */
        @Getter
        private final Map<AbstractConnector, Float> distribution;

        public Post(
                @NotNull SubPart subPart,
                @NotNull BFDamageContext ctx,
                @Nullable HitBox hitBox,
                float impact,
                @NotNull Map<AbstractConnector, Float> distribution
        ) {
            super(subPart, ctx, hitBox, impact);
            this.distribution = Collections.unmodifiableMap(distribution);
        }
    }
}
