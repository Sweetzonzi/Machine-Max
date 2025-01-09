package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.mixin_interface.IMixinEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Entity.class)
public class EntityMixin implements IMixinEntity {
    @Unique
    private float machine_max$zRot = 0;
    @Override
    public float machine_Max$getZRot() {
        return machine_max$zRot;
    }

    @Override
    public void machine_Max$setZRot(float zRot) {
        this.machine_max$zRot = zRot;
    }
}
