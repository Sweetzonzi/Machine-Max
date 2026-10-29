package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import io.github.sweetzonzi.ballistics_framework.api.BFDamageContext;
import lombok.Getter;
import net.neoforged.bus.api.ICancellableEvent;
@Getter
public abstract class SubPartDamageEvent extends SubPartEvent {
    protected BFDamageContext ctx;
    protected float damageAmount;

    public SubPartDamageEvent(SubPart subPart, BFDamageContext ctx, float damageAmount) {
        super(subPart);
        this.ctx = ctx;
        this.damageAmount = damageAmount;
    }

    public static class Pre extends SubPartDamageEvent implements ICancellableEvent {
        /**
         * 入口履带吸收标记：本次耐久伤害已由履带装甲层吸收，结算时不再走零件耐久与子系统伤害。
         * <p>只用于命中记录的跨线程传递，不表示事件取消；结构冲击与物理冲量仍按各自通道处理。</p>
         */
        private boolean trackAbsorbed;

        public Pre(SubPart subPart, BFDamageContext ctx, float amount) {
            super(subPart, ctx, amount);
        }

        public void setDamageCtx(BFDamageContext ctx) {
            this.ctx = ctx;
        }

        public void setDamageAmount(float amount) {
            this.damageAmount = amount;
        }

        public void setTrackAbsorbed(boolean trackAbsorbed) {
            this.trackAbsorbed = trackAbsorbed;
        }

        public boolean isTrackAbsorbed() {
            return trackAbsorbed;
        }

    }

    public static class Post extends SubPartDamageEvent {
        public Post(SubPart subPart, BFDamageContext ctx, float amount) {
            super(subPart, ctx, amount);
        }
    }
}
