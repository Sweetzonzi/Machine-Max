package io.github.sweetzonzi.machinemax.mixin;

import io.github.sweetzonzi.machinemax.mixin_interface.IClientLevelMixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.UUID;

@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin implements IClientLevelMixin {
    @Shadow
    protected abstract LevelEntityGetter<Entity> getEntities();

    /**
     * 通过uuid获取实体
     * @param uuid 实体的uuid
     * @return 实体
     */
    @Override
    public Entity machine_Max$getEntity(UUID uuid){
        return getEntities().get(uuid);
    }
}
