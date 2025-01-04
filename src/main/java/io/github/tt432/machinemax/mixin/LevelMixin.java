package io.github.tt432.machinemax.mixin;

import cn.solarmoon.spark_core.phys.thread.PhysLevel;
import io.github.tt432.machinemax.mixin_interface.IMixinLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Level.class)
public class LevelMixin implements IMixinLevel {
    @Unique
    public PhysLevel machine_max$physThread;

    @Override
    public PhysLevel machine_Max$getPhysLevel() {
        return machine_max$physThread;
    }

    @Override
    public void machine_Max$setPhysLevel(PhysLevel thread) {
        this.machine_max$physThread = thread;
    }
}
