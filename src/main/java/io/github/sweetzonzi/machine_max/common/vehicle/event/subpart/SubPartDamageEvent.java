package io.github.sweetzonzi.machine_max.common.vehicle.event.subpart;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.ICancellableEvent;
@Getter
public abstract class SubPartDamageEvent extends SubPartEvent {
    protected DamageSource source;
    protected float damageAmount;

    public SubPartDamageEvent(SubPart subPart, DamageSource source, float damageAmount) {
        super(subPart);
        this.source = source;
        this.damageAmount = damageAmount;
    }

    public static class Pre extends SubPartDamageEvent implements ICancellableEvent {
        public Pre(SubPart subPart, DamageSource source, float amount) {
            super(subPart, source, amount);
        }

        public void setDamageSource(DamageSource source) {
            this.source = source;
        }

        public void setDamageAmount(float amount) {
            this.damageAmount = amount;
        }

    }

    public static class Post extends SubPartDamageEvent {
        public Post(SubPart subPart, DamageSource source, float amount) {
            super(subPart, source, amount);
        }
    }
}
