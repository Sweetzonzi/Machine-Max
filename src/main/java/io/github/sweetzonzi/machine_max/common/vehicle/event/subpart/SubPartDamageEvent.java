package io.github.sweetzonzi.machine_max.common.vehicle.event.subpart;

import io.github.sweetzonzi.machine_max.common.vehicle.SubPart;
import io.github.sweetzonzi.machine_max.common.vehicle.data.PartDamageData;
import lombok.Getter;
import net.minecraft.world.damagesource.DamageSource;
import net.neoforged.bus.api.ICancellableEvent;
@Getter
public abstract class SubPartDamageEvent extends SubPartEvent {
    protected PartDamageData data;
    protected float damageAmount;

    public SubPartDamageEvent(SubPart subPart, PartDamageData data, float damageAmount) {
        super(subPart);
        this.data = data;
        this.damageAmount = damageAmount;
    }

    public static class Pre extends SubPartDamageEvent implements ICancellableEvent {
        public Pre(SubPart subPart, PartDamageData data, float amount) {
            super(subPart, data, amount);
        }

        public void setDamageData(PartDamageData data) {
            this.data = data;
        }

        public void setDamageAmount(float amount) {
            this.damageAmount = amount;
        }

    }

    public static class Post extends SubPartDamageEvent {
        public Post(SubPart subPart, PartDamageData data, float amount) {
            super(subPart, data, amount);
        }
    }
}
