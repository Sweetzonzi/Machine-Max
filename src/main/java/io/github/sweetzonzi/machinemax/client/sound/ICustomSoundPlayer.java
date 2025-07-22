package io.github.sweetzonzi.machinemax.client.sound;

import cn.solarmoon.spark_core.sound.ISpreadingSoundSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public interface ICustomSoundPlayer {
    void playCustomSound(SoundSource soundType, ResourceLocation path, Vec3 position, Vec3 speed, float range, float pitch, float volume);

    void playCustomSound(SoundSource soundType, ResourceLocation path, ISpreadingSoundSource spreadingSoundSource);
}
