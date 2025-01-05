package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.common.sloarphys.MMAbstractPhysLevel;
import io.github.tt432.machinemax.mixin_interface.IMixinLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public class LevelMixin implements IMixinLevel {
    @Unique
    public MMAbstractPhysLevel machine_max$physThread;

    @Override
    public MMAbstractPhysLevel machine_Max$getPhysLevel() {
        return machine_max$physThread;
    }

    @Override
    public void machine_Max$setPhysLevel(MMAbstractPhysLevel thread) {
        this.machine_max$physThread = thread;
    }
}
