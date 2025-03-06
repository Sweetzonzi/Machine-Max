package io.github.tt432.machinemax.mixin;

import io.github.tt432.machinemax.common.entity.MMPartEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin(Player.class)
abstract public class PlayerMixin extends LivingEntity {


    protected PlayerMixin(EntityType<? extends LivingEntity> entityType, Level level) {
        super(entityType, level);
    }

    /**
     * @author 甜粽子 Sweetzonzi
     * @reason 由另外分配的按键控制离开载具
     */
    @Overwrite
    protected boolean wantsToStopRiding() {
        if (!(this.getVehicle() instanceof MMPartEntity)) return this.isShiftKeyDown();
        else return false;
    }
}
