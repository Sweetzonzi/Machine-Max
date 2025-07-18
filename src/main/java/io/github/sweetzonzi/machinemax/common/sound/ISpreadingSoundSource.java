package io.github.sweetzonzi.machinemax.common.sound;

import io.github.sweetzonzi.machinemax.client.sound.SpreadingSoundInstance;
import net.minecraft.world.phys.Vec3;

public interface ISpreadingSoundSource {
    Vec3 getPosition(SpreadingSoundInstance instance);

    Vec3 getSpeed(SpreadingSoundInstance instance);

    float getRange(SpreadingSoundInstance instance);

    float getPitch(SpreadingSoundInstance instance);

    float getVolume(SpreadingSoundInstance instance);
}
