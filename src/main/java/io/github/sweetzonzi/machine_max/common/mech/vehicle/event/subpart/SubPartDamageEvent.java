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
        public Pre(SubPart subPart, BFDamageContext ctx, float amount) {
            super(subPart, ctx, amount);
        }

        public void setDamageCtx(BFDamageContext ctx) {
            this.ctx = ctx;
        }

        public void setDamageAmount(float amount) {
            this.damageAmount = amount;
        }

    }

    public static class Post extends SubPartDamageEvent {
        public Post(SubPart subPart, BFDamageContext ctx, float amount) {
            super(subPart, ctx, amount);
        }
    }
}
