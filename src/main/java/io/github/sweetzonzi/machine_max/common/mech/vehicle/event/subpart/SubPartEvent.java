package io.github.sweetzonzi.machine_max.common.mech.vehicle.event.subpart;

import io.github.sweetzonzi.machine_max.common.mech.vehicle.SubPart;
import lombok.Getter;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;

@Getter
public abstract class SubPartEvent extends Event {
    private final SubPart subPart;
    private final Level level;
    public SubPartEvent(SubPart subPart) {
        this.subPart = subPart;
        this.level = subPart.getLevel();
    }
}
